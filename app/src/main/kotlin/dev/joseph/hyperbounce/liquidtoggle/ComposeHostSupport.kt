/*
 * Shared plumbing for LiquidToggleHost and LiquidSliderHost: swapping a native
 * widget for a same-size ComposeView overlay at its exact layout slot, a fallback
 * Lifecycle/ViewModelStore/SavedStateRegistry owner for windows that don't already
 * have one, touch pass-through so RecyclerView doesn't steal drags, the row-tap
 * "row spy", and a periodic backdrop refresh ticker. See LiquidToggleHost's file
 * comment for the attachment-strategy rationale -- it applies equally to both.
 */
package dev.joseph.hyperbounce.liquidtoggle

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.MutableState
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

internal class OverlayWrapper(
    val wrapper: FrameLayout,
    val composeView: ComposeView,
    val hostOwner: HostOwner?
)

/**
 * Moves [model] into a new FrameLayout inserted at its original index/LayoutParams,
 * hides [model], and adds a same-size [TouchPassthroughComposeView] on top of it.
 *
 * The native widget is never removed from the tree -- other app code may hold a
 * cached reference to it from RecyclerView.ViewHolder construction and call methods
 * on it later without re-querying findViewById. Instead it is moved one level
 * deeper (into this new wrapper) and hidden; findViewById(id) for its original id
 * still resolves since that search is recursive, and the outer parent's layout is
 * completely unaffected since it still sees exactly one child in that slot.
 *
 * Callers are expected to cache the result themselves (see each host's
 * WeakHashMap) and only call this once per model.
 */
internal fun createOverlayWrapper(model: View): OverlayWrapper? {
    val parent = model.parent as? ViewGroup ?: return null
    val index = parent.indexOfChild(model)
    if (index < 0) return null

    val context = model.context
    val originalParams = model.layoutParams
    val originalId = model.id

    // Any ConstraintLayout (or similar) siblings referencing the widget's id should
    // resolve to the wrapper, since only *direct* children participate in those
    // constraints -- the widget itself is about to move one level deeper.
    val wrapper = FrameLayout(context)
    wrapper.id = originalId
    model.id = View.generateViewId()

    parent.removeViewAt(index)
    parent.addView(wrapper, index, originalParams)

    val modelParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    )
    wrapper.addView(model, modelParams)
    model.visibility = View.INVISIBLE
    model.isClickable = false
    model.isFocusable = false

    val hostOwner = ensureLifecycleOwner(parent, wrapper)

    val composeView = TouchPassthroughComposeView(context)
    val composeParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
    )
    wrapper.addView(composeView, composeParams)

    return OverlayWrapper(wrapper, composeView, hostOwner)
}

/**
 * Fallback Lifecycle/ViewModelStore/SavedStateRegistry owner for windows that don't
 * already provide one -- e.g. a SystemUI Control Center view added straight onto
 * WindowManager from a Service rather than hosted by an Activity. Settings/Dialer
 * already have a real owner via their Activity, so this path is only exercised there.
 */
internal fun ensureLifecycleOwner(parent: ViewGroup, wrapper: View): HostOwner? {
    if (parent.findViewTreeLifecycleOwner() != null) return null
    val owner = HostOwner().also { it.start() }
    wrapper.setViewTreeLifecycleOwner(owner)
    wrapper.setViewTreeViewModelStoreOwner(owner)
    wrapper.setViewTreeSavedStateRegistryOwner(owner)
    return owner
}

/**
 * Walks up from [content] to the nearest ancestor below the RecyclerView/ListView
 * and mirrors its down/up state into [pressedState] -- the same "row spy" the
 * Canvas engine used so a tap anywhere on the settings row (not just the thumb)
 * also plays the bloom.
 */
internal fun attachRowSpy(content: View, pressedState: MutableState<Boolean>) {
    var rowView: View? = null
    var p = content.parent
    while (p is View) {
        val name = p.javaClass.simpleName
        if (name == "RecyclerView" || name.contains("ListView")) break
        rowView = p
        p = p.parent
    }
    rowView?.setOnTouchListener { _, event ->
        val down = event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_MOVE
        if (pressedState.value != down) pressedState.value = down
        false
    }
}

internal class TouchPassthroughComposeView(context: Context) : ComposeView(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return super.dispatchTouchEvent(ev)
    }
}

internal class HostOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun start() {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }
}

/**
 * Periodically re-rasterizes the row behind [wrapper] into [backdrop] so the glass
 * effects have real content to refract (see ViewSnapshotBackdrop). Pausable via
 * [stop] (e.g. onDetachedFromWindow) and safely restartable via [start].
 */
internal class BackdropRefresher(
    private val wrapper: View,
    private val backdrop: ViewSnapshotBackdrop,
    private val intervalMs: Long = 400L
) {
    private var scheduled = false

    fun start() {
        if (scheduled) return
        scheduled = true
        val tick = object : Runnable {
            override fun run() {
                if (!scheduled || !wrapper.isAttachedToWindow) return
                val rowRoot = wrapper.parent as? View ?: wrapper
                backdrop.refresh(rowRoot, wrapper)
                wrapper.postDelayed(this, intervalMs)
            }
        }
        wrapper.post(tick)
    }

    fun stop() {
        scheduled = false
    }
}
