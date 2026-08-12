package dev.joseph.hyperbounce

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.CompoundButton
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
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
 * The Compose LiquidToggle is hosted by a ZERO-SIZE NORMAL child of the
 * NATIVE TOGGLE'S IMMEDIATE PARENT:
 *
 *   native row/container
 *        ├── original HyperOS toggle (onDraw suppressed)
 *        └── zero-size LiquidToggleHostView
 *              └── ComposeView (manually laid out at the toggle position)
 *
 * This is intentional. A ViewGroupOverlay moves with the row, but its private
 * OverlayViewGroup does not reliably expose the lifecycle owners required by
 * Compose in several HyperOS/system-app windows. That caused the crash:
 *
 *   ViewTreeLifecycleOwner not found from ViewOverlay$OverlayViewGroup
 *
 * The normal zero-size host keeps the ComposeView inside a real ViewParent tree,
 * while its measured size remains 0x0 so it does not alter row measurement.
 * The target and host share the same scrolling parent, so the whole subtree
 * moves together automatically. There is no global scroll listener, no per-
 * scroll window-coordinate repositioning, and no LayoutParams churn during
 * scrolling.
 *
 * Ancestor clipping is disabled so the upstream LiquidToggle's 1.5x bloom can
 * extend outside the native toggle bounds.
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
        /*
         * IMPORTANT:
         * Do NOT use ViewGroupOverlay for ComposeView.
         *
         * AndroidX Compose resolves WindowRecomposer lifecycle owners through the
         * normal ViewParent tree. ViewOverlay's private OverlayViewGroup does not
         * reliably expose those owners in several HyperOS/system-app windows, which
         * causes:
         *
         *   IllegalStateException: ViewTreeLifecycleOwner not found from
         *   android.view.ViewOverlay$OverlayViewGroup
         *
         * The host below is a normal zero-size child of the toggle's immediate
         * parent. It therefore participates in the same Android parent transform
         * and scroll lifecycle as the native toggle, while its zero measured size
         * does not change the parent's row geometry.
         *
         * The actual ComposeView is manually laid out inside this zero-size host.
         */
        private val hostView = LiquidToggleHostView(target.context)

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

            hostView.apply {
                tag = "HyperBounceLiquidToggleHost:${System.identityHashCode(target)}"
                // A zero-size normal child: it does not alter row measurement.
                layoutParams = ViewGroup.LayoutParams(0, 0)
                setViewTreeLifecycleOwner(ownerFor(decor))
            }

            container.addView(hostView)

            val compose = ComposeView(target.context).apply {
                tag = "HyperBounceLiquidToggle:${System.identityHashCode(target)}"
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool
                )
                isClickable = false
                isFocusable = false

                // Install the synthetic owner set directly on the ComposeView.
                // Compose's WindowRecomposer resolves lifecycle ownership from
                // the ComposeView's own ViewTree lookup; relying only on the
                // injected host's parent tag was unreliable in HyperOS system
                // app hierarchies and could fall through to content_parent.
                val composeOwner = ownerFor(decor)
                setViewTreeLifecycleOwner(composeOwner)
                setViewTreeSavedStateRegistryOwner(composeOwner)
                setViewTreeViewModelStoreOwner(composeOwner)

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
            hostView.addView(compose)

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
             * hostView is a zero-size child whose own position may be chosen by
             * the parent layout. The ComposeView is therefore laid out relative
             * to the host using the delta between the target and host positions.
             *
             * During scrolling both hostView and target receive the same parent
             * transform automatically; no scroll listener or window-coordinate
             * chasing is required.
             */
            val hostLeft = hostView.left
            val hostTop = hostView.top

            val desiredLeft = target.left +
                target.translationX.roundToInt() +
                (target.width - overlayWidthPx) / 2

            val desiredTop = target.top +
                target.translationY.roundToInt() +
                (target.height - overlayHeightPx) / 2

            val composeLeft = desiredLeft - hostLeft
            val composeTop = desiredTop - hostTop

            if (
                desiredLeft != lastLeft ||
                desiredTop != lastTop ||
                target.width != lastTargetWidth ||
                target.height != lastTargetHeight
            ) {
                compose.layout(
                    composeLeft,
                    composeTop,
                    composeLeft + overlayWidthPx,
                    composeTop + overlayHeightPx
                )

                backdrop.setTargetBounds(
                    left = target.left.toFloat(),
                    top = target.top.toFloat(),
                    width = target.width,
                    height = target.height,
                    paddingPx = (24f * density).roundToInt(),
                )

                lastLeft = desiredLeft
                lastTop = desiredTop
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

            hostView.visibility = if (visible) View.VISIBLE else View.INVISIBLE
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

            try {
                if (compose != null) {
                    compose.visibility = View.INVISIBLE
                    hostView.removeView(compose)
                }
            } catch (_: Throwable) {
                // Never crash the hooked application during cleanup.
            }

            /*
             * Always defer removal of the normal child. System-app FrameLayouts
             * can be in the middle of a layout traversal when a hooked target
             * disappears; synchronous child-array mutation can produce the exact
             * FrameLayout.layoutChildren() NPE seen in Security Center.
             */
            decor.post {
                try {
                    if (hostView.parent === container) {
                        container.removeView(hostView)
                    }
                } catch (_: Throwable) {
                    // Never crash the hooked application during cleanup.
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
            hostView.clipChildren = false
            hostView.clipToPadding = false
        }

        private fun installOwnersIfNeeded() {
            /*
             * Give the NORMAL host View its own complete synthetic owner set.
             * ComposeView sits directly under this host, so its WindowRecomposer
             * resolves these owners through the ordinary ViewParent chain.
             *
             * We deliberately do NOT overwrite lifecycle/saved-state owners on
             * the target app's decor or container. That could interfere with the
             * host application's own lifecycle.
             */
            val owner = ownerFor(decor)
            hostView.setViewTreeLifecycleOwner(owner)
            hostView.setViewTreeSavedStateRegistryOwner(owner)
            hostView.setViewTreeViewModelStoreOwner(owner)
        }
    }

    private class LiquidToggleHostView(context: android.content.Context) : ViewGroup(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            // Deliberately zero-size so this injected host never changes the native
            // row's measured dimensions.
            setMeasuredDimension(0, 0)
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                child.measure(
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                )
            }
        }

        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            // Children are positioned explicitly by Host.refreshGeometry().
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


    private const val LIQUID_TOGGLE_SCALE = 0.975f
}
