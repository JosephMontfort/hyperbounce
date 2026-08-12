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

/**
 * Xposed integration layer for the upstream AndroidLiquidGlass LiquidToggle.
 *
 * IMPORTANT ARCHITECTURE
 * ---------------------
 * The native HyperOS SlidingButton remains the real state holder and click
 * target. Its old Canvas/RenderNode renderer remains in PhysicsHook.java and
 * can be restored by setting USE_LIQUID_GLASS_TOGGLE=false.
 *
 * In Liquid mode this class adds a transparent-window-level Compose overlay,
 * centered over the native toggle. LiquidToggle owns all interaction and
 * visual animation; onSelect() delegates back to native CompoundButton.performClick()
 * so HyperOS listeners/preferences continue to receive their normal callback.
 *
 * The injected ComposeView is kept outside the source View subtree captured by
 * LiquidToggleViewBackdrop. Therefore the live backdrop never recursively captures
 * the liquid surface itself.
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

    private class Host(
        private val target: View,
        private val decor: ViewGroup,
    ) {
        private val captureSource: View = findCaptureSource(target, decor)
        private val backdrop = LiquidToggleViewBackdrop(captureSource)

        private var composeView: ComposeView? = null
        private var checked by mutableStateOf(
            (target as? CompoundButton)?.isChecked == true
        )
        private var visible by mutableStateOf(true)
        // Geometry is host-view bookkeeping, not Compose state. Keeping these as
        // plain fields avoids unnecessary Compose invalidations on scroll.
        private var overlayWidthPx = 0
        private var overlayHeightPx = 0
        private var lastOverlayX = Float.NaN
        private var lastOverlayY = Float.NaN
        private var geometryRefreshPosted = false

        private val layoutListener =
            View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                refreshGeometry()
            }

        /*
         * Scrolling changes the target's window position without changing its
         * layout bounds. The old renderer never had this problem because it drew
         * inside the native toggle itself, so the whole rendered view naturally
         * scrolled with its row.
         *
         * We still keep the Compose surface at the decor level because that is
         * what prevents ancestor clipping and keeps the live backdrop source
         * outside the injected glass surface. The efficient fix is therefore to
         * observe the hierarchy for scroll changes but coalesce all callbacks into
         * ONE geometry update per display frame. We also move the already-laid-out
         * ComposeView with translationX/Y instead of changing LayoutParams, so
         * scrolling never triggers a parent relayout.
         */
        private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
            scheduleGeometryRefresh()
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
            target.addOnLayoutChangeListener(layoutListener)
            target.addOnAttachStateChangeListener(attachListener)
            decor.viewTreeObserver.addOnScrollChangedListener(scrollListener)

            installOwnersIfNeeded()

            val compose = ComposeView(target.context).apply {
                tag = "HyperBounceLiquidToggle:${System.identityHashCode(target)}"
                setViewCompositionStrategy(
                    ViewCompositionStrategy.DisposeOnDetachedFromWindow
                )
                setContent {
                    LiquidToggleOverlay(
                        backdrop = backdrop,
                        visible = visible,
                        selected = { checked },
                        onSelect = { value ->
                            val button = target as? CompoundButton
                            if (button != null) {
                                if (button.isChecked != value) {
                                    // Delegate through the native button so existing
                                    // Preference/Settings listeners and app logic run.
                                    button.performClick()
                                }
                                syncChecked()
                            }
                        },
                    )
                }
            }

            composeView = compose
            decor.addView(
                compose,
                ViewGroup.LayoutParams(1, 1),
            )

            // Layout the overflow envelope once. Subsequent scrolling only changes
            // translation, which avoids repeated ViewRoot/FrameLayout relayouts.
            refreshGeometry()
            syncVisibility()
            decor.post { backdrop.start() }
        }

        private fun scheduleGeometryRefresh() {
            val compose = composeView ?: return
            if (geometryRefreshPosted) return
            geometryRefreshPosted = true

            // Coalesce bursts of scroll callbacks into a single UI-thread update.
            compose.postOnAnimation {
                geometryRefreshPosted = false
                if (composeView === compose && compose.parent === decor) {
                    refreshGeometry()
                }
            }
        }

        fun refreshGeometry() {
            val compose = composeView ?: return
            val location = IntArray(2)
            val decorLocation = IntArray(2)
            target.getLocationInWindow(location)
            decor.getLocationInWindow(decorLocation)

            val density = target.resources.displayMetrics.density

            // Keep the actual library toggle at 64dp x 28dp (scaled to 97.5% below)
            // while giving the injected ComposeView a small touch/overflow envelope
            // so the library's 1.5x bloom never gets clipped.
            val newWidthPx = (80f * density).roundToInt()
            val newHeightPx = (48f * density).roundToInt()

            if (overlayWidthPx != newWidthPx || overlayHeightPx != newHeightPx) {
                overlayWidthPx = newWidthPx
                overlayHeightPx = newHeightPx
                val lp = compose.layoutParams
                    ?: ViewGroup.LayoutParams(overlayWidthPx, overlayHeightPx)
                lp.width = overlayWidthPx
                lp.height = overlayHeightPx
                compose.layoutParams = lp
            }

            val overlayX =
                location[0] - decorLocation[0] +
                    (target.width - overlayWidthPx) / 2f
            val overlayY =
                location[1] - decorLocation[1] +
                    (target.height - overlayHeightPx) / 2f

            // Translation moves the already-laid-out overlay without requesting
            // another FrameLayout measurement/layout pass.
            if (overlayX != lastOverlayX) {
                compose.translationX = overlayX
                lastOverlayX = overlayX
            }
            if (overlayY != lastOverlayY) {
                compose.translationY = overlayY
                lastOverlayY = overlayY
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
        }

        fun dispose() {
            target.removeOnLayoutChangeListener(layoutListener)
            target.removeOnAttachStateChangeListener(attachListener)
            decor.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
            backdrop.release()

            val compose = composeView
            composeView = null

            /*
             * A target can detach during FrameLayout.onLayout(). Removing our
             * sibling synchronously from that callback can corrupt the parent's
             * child array on some HyperOS builds and produce:
             *   FrameLayout.layoutChildren() -> NPE in View.getVisibility()
             *
             * Post the removal until after the current traversal has completed.
             */
            if (compose != null) {
                compose.visibility = View.GONE
                decor.post {
                    try {
                        compose.setContent { }
                        (compose.parent as? ViewGroup)?.removeView(compose)
                    } catch (_: Throwable) {
                        // Never let cleanup crash a hooked system app.
                    }
                }
            }
        }

        private fun installOwnersIfNeeded() {
            // The floating-nav branch already proved this owner strategy works
            // for injected ComposeView hierarchies in these Xposed targets.
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
                            // The upstream component remains structurally and
                            // behaviorally unchanged; only its outer geometry is
                            // scaled slightly below the library's full 64x28dp size.
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

        // Select the highest application-content ancestor immediately below the
        // decor/window. The Liquid Compose overlay is a sibling of this subtree.
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
