package dev.joseph.hyperbounce

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.CompoundButton
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.joseph.hyperbounce.liquidglass.LiquidToggle
import kotlin.math.roundToInt
import java.util.WeakHashMap

internal object LiquidToggleHost {
    private val hosts = WeakHashMap<View, Host>()

    @JvmStatic
    fun ensure(target: View) {
        if (target.width <= 0 || target.height <= 0) return
        if (!target.isAttachedToWindow) return

        val existing = hosts[target]
        if (existing != null) {
            existing.refreshGeometry()
            return
        }

        val decor = target.rootView as? ViewGroup ?: return
        val host = Host(target, decor)
        hosts[target] = host
        host.attach()
    }

    @JvmStatic
    fun syncChecked(target: View) {
        hosts[target]?.syncChecked()
    }

    @JvmStatic
    fun syncVisibility(target: View) {
        hosts[target]?.syncVisibility()
    }

    @JvmStatic
    fun remove(target: View) {
        hosts.remove(target)?.dispose()
    }

    @JvmStatic
    fun forwardTouchEvent(target: View, event: android.view.MotionEvent) {
        val host = hosts[target] ?: return
        val compose = host.composeView ?: return
        val offsetEvent = android.view.MotionEvent.obtain(event)
        
        // Touch events are relative to the target toggle. 
        // We must offset them to the ComposeView's local coordinate space.
        offsetEvent.offsetLocation(-host.composeLeftInTarget.toFloat(), -host.composeTopInTarget.toFloat())
        compose.dispatchTouchEvent(offsetEvent)
        offsetEvent.recycle()
    }

    private class Host(
        private val target: View,
        private val decor: ViewGroup,
    ) {
        private val captureSource: View = findCaptureSource(target, decor)
        private val backdrop = LiquidToggleViewBackdrop(captureSource)

        var composeView: ComposeView? = null
        private var checked by mutableStateOf((target as? CompoundButton)?.isChecked == true)
        private var visible by mutableStateOf(true)
        
        var composeLeftInTarget by mutableIntStateOf(0)
        var composeTopInTarget by mutableIntStateOf(0)

        private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshGeometry()
        }

        // Ensures the ComposeView tracks pixel-perfectly as the RecyclerView scrolls
        private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
            refreshGeometry()
        }

        private val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                refreshGeometry()
                syncVisibility()
            }

            override fun onViewDetachedFromWindow(v: View) {
                LiquidToggleHost.remove(v)
            }
        }

        fun attach() {
            target.addOnLayoutChangeListener(layoutListener)
            target.viewTreeObserver.addOnScrollChangedListener(scrollListener)
            target.addOnAttachStateChangeListener(attachListener)

            installOwnersIfNeeded()

            val compose = ComposeView(target.context).apply {
                tag = "HyperBounceLiquidToggle:${System.identityHashCode(target)}"
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    LiquidToggleOverlay(
                        backdrop = backdrop,
                        visible = visible,
                        selected = { checked },
                        onSelect = { value ->
                            val button = target as? CompoundButton
                            if (button != null) {
                                if (button.isChecked != value) button.performClick()
                                syncChecked()
                            }
                        },
                    )
                }
            }

            composeView = compose
            
            // SAFE ATTACHMENT: DecorView extends FrameLayout. Use FrameLayout.LayoutParams
            // This prevents the original NullPointerException during the internal layout phase.
            decor.addView(
                compose,
                FrameLayout.LayoutParams(1, 1)
            )

            refreshGeometry()
            syncVisibility()
            decor.post { backdrop.start() }
        }

        fun refreshGeometry() {
            val compose = composeView ?: return
            val location = IntArray(2)
            val decorLocation = IntArray(2)
            target.getLocationInWindow(location)
            decor.getLocationInWindow(decorLocation)

            val density = target.resources.displayMetrics.density
            val overlayWidthPx = (80f * density).roundToInt()
            val overlayHeightPx = (48f * density).roundToInt()

            composeLeftInTarget = (target.width - overlayWidthPx) / 2
            composeTopInTarget = (target.height - overlayHeightPx) / 2

            val overlayLeftPx = location[0] - decorLocation[0] + composeLeftInTarget
            val overlayTopPx = location[1] - decorLocation[1] + composeTopInTarget

            val lp = (compose.layoutParams as? FrameLayout.LayoutParams)
                ?: FrameLayout.LayoutParams(overlayWidthPx, overlayHeightPx)
            lp.width = overlayWidthPx
            lp.height = overlayHeightPx
            lp.leftMargin = overlayLeftPx
            lp.topMargin = overlayTopPx
            compose.layoutParams = lp
        }

        fun syncChecked() {
            checked = (target as? CompoundButton)?.isChecked == true
            refreshGeometry()
        }

        fun syncVisibility() {
            visible = target.isAttachedToWindow &&
                    target.visibility == View.VISIBLE &&
                    target.windowVisibility == View.VISIBLE &&
                    target.isShown
        }

        fun dispose() {
            target.removeOnLayoutChangeListener(layoutListener)
            target.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
            target.removeOnAttachStateChangeListener(attachListener)
            backdrop.release()

            val compose = composeView
            composeView = null
            compose?.setContent { }
            (compose?.parent as? ViewGroup)?.removeView(compose)
        }

        private fun installOwnersIfNeeded() {
            val owner = ownerFor(decor)
            decor.setViewTreeLifecycleOwner(owner)
            decor.setViewTreeSavedStateRegistryOwner(owner)
            decor.setViewTreeViewModelStoreOwner(owner)
        }
    }

    @Composable
    private fun LiquidToggleOverlay(
        backdrop: LiquidToggleViewBackdrop,
        visible: Boolean,
        selected: () -> Boolean,
        onSelect: (Boolean) -> Unit,
    ) {
        val density = LocalDensity.current
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (visible) {
                LiquidToggle(
                    selected = selected,
                    onSelect = onSelect,
                    backdrop = backdrop,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = LIQUID_TOGGLE_SCALE
                            scaleY = LIQUID_TOGGLE_SCALE
                        }
                        .onGloballyPositioned { coordinates ->
                            val padding = with(density) { 24.dp.toPx().roundToInt() }
                            backdrop.setTargetBounds(
                                left = coordinates.positionInWindow().x,
                                top = coordinates.positionInWindow().y,
                                width = coordinates.size.width,
                                height = coordinates.size.height,
                                paddingPx = padding,
                            )
                        },
                )
            }
        }
    }

    private fun findCaptureSource(target: View, decor: ViewGroup): View {
        var candidate: View = target
        var parent = candidate.parent as? ViewGroup
        while (parent != null && parent !== decor) {
            candidate = parent
            parent = parent.parent as? ViewGroup
        }
        return candidate
    }

    private val ownerMap = WeakHashMap<ViewGroup, FakeLifecycleOwner>()

    private fun ownerFor(decor: ViewGroup): FakeLifecycleOwner {
        return ownerMap[decor] ?: FakeLifecycleOwner().also {
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            ownerMap[decor] = it
        }
    }

    private const val LIQUID_TOGGLE_SCALE = 0.975f
}
