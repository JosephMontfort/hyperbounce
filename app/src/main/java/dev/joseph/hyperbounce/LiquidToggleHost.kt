package dev.joseph.hyperbounce

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.CompoundButton
import android.view.ViewGroupOverlay
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import dev.joseph.hyperbounce.liquidglass.LiquidToggle
import java.util.WeakHashMap
import kotlin.math.roundToInt

/**
 * Xposed integration layer for the upstream AndroidLiquidGlass LiquidToggle.
 *
 * IMPORTANT ARCHITECTURE
 * ---------------------
 * The native HyperOS CompoundButton remains the real state holder and callback
 * source. PhysicsHook.java still contains the complete legacy Canvas/RenderNode/
 * AGSL renderer and remains selectable with USE_LIQUID_GLASS_TOGGLE=false.
 *
 * LIQUID MODE
 * -----------
 * The Compose LiquidToggle is attached to the NATIVE TOGGLE'S IMMEDIATE PARENT
 * USING ViewGroupOverlay rather than to the window/decor. This is deliberate:
 *
 *   native row/container
 *        ├── original HyperOS toggle (onDraw suppressed)
 *        └── ViewGroupOverlay
 *              └── LiquidToggle ComposeView
 *
 * Because the overlay belongs to the same parent that contains the real toggle,
 * scrolling the settings page moves BOTH together automatically. There is no
 * global scroll listener, no per-scroll window-coordinate repositioning, and no
 * LayoutParams churn during scrolling.
 *
 * The overlay does not participate in normal parent layout, so it cannot change
 * row measurement or spacing. Ancestor clipping is disabled so the upstream
 * LiquidToggle's 1.5x bloom can extend outside the native toggle bounds.
 *
 * BACKDROP
 * --------
 * LiquidToggleViewBackdrop captures only the toggle's immediate parent/container,
 * not the entire app. The native toggle's onDraw is already suppressed by
 * PhysicsHook, and the Compose overlay is temporarily hidden while the source
 * container is copied. This gives the glass the local container content directly
 * underneath the toggle while avoiding self-recursion.
 */
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
        val parent = target.parent as? ViewGroup ?: decor
        val host = Host(target, parent, decor)
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

    private class Host(
        private val target: View,
        private val container: ViewGroup,
        private val decor: ViewGroup,
    ) {
        private val overlay: ViewGroupOverlay = container.overlay
        private val backdrop = LiquidToggleViewBackdrop(
            sourceView = container,
            overlayViewProvider = { composeView }
        )

        private var composeView: ComposeView? = null
        private var checked by mutableStateOf(
            (target as? CompoundButton)?.isChecked == true
        )
        private var visible by mutableStateOf(true)

        private var overlayWidthPx = 0
        private var overlayHeightPx = 0
        private var lastLeft = Int.MIN_VALUE
        private var lastTop = Int.MIN_VALUE
        private var lastTargetWidth = Int.MIN_VALUE
        private var lastTargetHeight = Int.MIN_VALUE
        private var disposed = false

        private val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                refreshGeometry()
            }

        private val attachListener =
            object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    refreshGeometry()
                    syncVisibility()
                }

                override fun onViewDetachedFromWindow(v: View) {
                    LiquidToggleHost.remove(v)
                }
            }

        fun attach() {
            prepareClipping(container)
            target.addOnLayoutChangeListener(layoutListener)
            target.addOnAttachStateChangeListener(attachListener)

            installOwnersIfNeeded()

            val compose = ComposeView(target.context).apply {
                tag = "HyperBounceLiquidToggle:${System.identityHashCode(target)}"
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnDetachedFromWindow
                )
                isClickable = false
                isFocusable = false
                setViewTreeLifecycleOwner(ownerFor(decor))
                setViewTreeSavedStateRegistryOwner(savedStateOwnerFor(decor))
                setViewTreeViewModelStoreOwner(viewModelOwnerFor(decor))

                setContent {
                    LiquidToggleOverlay(
                        backdrop = backdrop,
                        visible = visible,
                        selected = { checked },
                        onSelect = { value ->
                            val button = target as? CompoundButton
                            if (button != null) {
                                if (button.isChecked != value) {
                                    // Preserve the native HyperOS callback/state path.
                                    button.performClick()
                                }
                                syncChecked()
                            }
                        },
                    )
                }
            }

            composeView = compose
            // Overlay children do NOT participate in the parent's measurement/layout.
            // This is the key difference from adding the ComposeView as a real sibling
            // child; list rows therefore keep their original dimensions and scroll
            // naturally with the native toggle.
            overlay.add(compose)

            refreshGeometry()
            syncVisibility()
            decor.post { backdrop.start() }
        }

        fun refreshGeometry() {
            val compose = composeView ?: return
            if (disposed || !target.isAttachedToWindow) return

            val density = target.resources.displayMetrics.density
            val newWidthPx = (80f * density).roundToInt()
            val newHeightPx = (48f * density).roundToInt()

            if (overlayWidthPx != newWidthPx || overlayHeightPx != newHeightPx) {
                overlayWidthPx = newWidthPx
                overlayHeightPx = newHeightPx
            }

            /*
             * The ComposeView is hosted by the same parent as the native toggle.
             * Therefore these are PARENT-LOCAL coordinates and remain valid while
             * the whole row/container scrolls. We do not use getLocationInWindow()
             * or any scroll callback here.
             */
            val left = target.left +
                target.translationX.roundToInt() +
                (target.width - overlayWidthPx) / 2
            val top = target.top +
                target.translationY.roundToInt() +
                (target.height - overlayHeightPx) / 2

            if (
                left != lastLeft ||
                top != lastTop ||
                target.width != lastTargetWidth ||
                target.height != lastTargetHeight
            ) {
                /*
                 * ViewGroupOverlay children are manually laid out. They remain
                 * attached to the parent overlay and are automatically transformed
                 * with that parent when a RecyclerView/ListView/ScrollView moves.
                 */
                compose.layout(
                    left,
                    top,
                    left + overlayWidthPx,
                    top + overlayHeightPx
                )

                backdrop.setTargetBounds(
                    left = target.left.toFloat(),
                    top = target.top.toFloat(),
                    width = target.width,
                    height = target.height,
                    paddingPx = (24f * density).roundToInt(),
                )

                lastLeft = left
                lastTop = top
                lastTargetWidth = target.width
                lastTargetHeight = target.height
            }
        }

        fun syncChecked() {
            checked = (target as? CompoundButton)?.isChecked == true
            refreshGeometry()
        }

        fun syncVisibility() {
            visible =
                target.isAttachedToWindow &&
                    target.visibility == View.VISIBLE &&
                    target.windowVisibility == View.VISIBLE &&
                    target.isShown

            composeView?.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        }

        fun dispose() {
            if (disposed) return
            disposed = true

            target.removeOnLayoutChangeListener(layoutListener)
            target.removeOnAttachStateChangeListener(attachListener)
            backdrop.release()

            val compose = composeView
            composeView = null

            if (compose != null) {
                compose.visibility = View.INVISIBLE

                /*
                 * ViewGroupOverlay removal never mutates the FrameLayout child
                 * array, so it avoids the Security Center FrameLayout.layoutChildren()
                 * NPE seen with synchronous real-child removal.
                 */
                try {
                    overlay.remove(compose)
                } catch (_: Throwable) {
                    decor.post {
                        try {
                            overlay.remove(compose)
                        } catch (_: Throwable) {
                            // Never crash the hooked application during cleanup.
                        }
                    }
                }
            }
        }

        private fun prepareClipping(start: ViewGroup) {
            var current: View? = start
            var depth = 0
            while (current is ViewGroup) {
                current.setClipChildren(false)
                current.setClipToPadding(false)
                current.setClipToOutline(false)
                current = current.parent as? View
                depth++
                if (depth > 8) break
            }
        }

        private fun installOwnersIfNeeded() {
            val owner = ownerFor(decor)
            val saved = savedStateOwnerFor(decor)
            val vm = viewModelOwnerFor(decor)
            decor.setViewTreeLifecycleOwner(owner)
            decor.setViewTreeSavedStateRegistryOwner(saved)
            decor.setViewTreeViewModelStoreOwner(vm)
        }
    }

    @Composable
    private fun LiquidToggleOverlay(
        backdrop: LiquidToggleViewBackdrop,
        visible: Boolean,
        selected: () -> Boolean,
        onSelect: (Boolean) -> Unit,
    ) {
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
                            // Slightly below the upstream 64x28dp library geometry.
                            scaleX = LIQUID_TOGGLE_SCALE
                            scaleY = LIQUID_TOGGLE_SCALE
                        },
                )
            }
        }
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

    private fun savedStateOwnerFor(decor: ViewGroup) =
        decor.findViewTreeSavedStateRegistryOwner()

    private fun viewModelOwnerFor(decor: ViewGroup) =
        decor.findViewTreeViewModelStoreOwner()

    private const val LIQUID_TOGGLE_SCALE = 0.975f
}
