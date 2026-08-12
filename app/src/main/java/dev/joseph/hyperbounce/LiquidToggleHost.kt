package dev.joseph.hyperbounce

import android.content.ComponentCallbacks
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroupOverlay
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

// CRITICAL FIX #1: Swallows SystemUI's broken ContextWrapper NullPointerExceptions 
// when Compose attempts to register global configuration and memory callbacks.
internal class SafeContext(base: Context) : ContextWrapper(base) {
    override fun registerComponentCallbacks(callback: ComponentCallbacks?) {
        try { super.registerComponentCallbacks(callback) } catch (e: Throwable) {}
    }
    override fun unregisterComponentCallbacks(callback: ComponentCallbacks?) {
        try { super.unregisterComponentCallbacks(callback) } catch (e: Throwable) {}
    }
}

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

        val parent = target.parent as? ViewGroup ?: return
        val host = Host(target, parent)
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
        
        offsetEvent.offsetLocation(-host.composeLeftInTarget.toFloat(), -host.composeTopInTarget.toFloat())
        compose.dispatchTouchEvent(offsetEvent)
        offsetEvent.recycle()
    }

    private class Host(private val target: View, private val parent: ViewGroup) {
        private val captureSource: View = target.rootView
        private val backdrop = LiquidToggleViewBackdrop(captureSource)

        var composeView: ComposeView? = null
        private var checked by mutableStateOf((target as? CompoundButton)?.isChecked == true)
        private var visible by mutableStateOf(true)
        
        var composeLeftInTarget by mutableIntStateOf(0)
        var composeTopInTarget by mutableIntStateOf(0)

        private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
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
            target.addOnAttachStateChangeListener(attachListener)

            // CRITICAL FIX #2: Dynamically strip clipping bounds from the parent tree
            // so the overlay can bloom outside the toggle's physical dimensions freely.
            disableClipping(target)

            val safeContext = SafeContext(target.context)
            val compose = ComposeView(safeContext).apply {
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
            
            val overlay = parent.overlay as? ViewGroupOverlay
            if (overlay != null) {
                // CRITICAL FIX #3: The Dummy View Trick
                // Force Android to instantiate the internal OverlayViewGroup so we can safely 
                // attach Lifecycle tags to it BEFORE Compose initialization executes.
                val dummy = View(safeContext)
                overlay.add(dummy)
                
                val overlayViewGroup = dummy.parent as? View
                if (overlayViewGroup != null) {
                    installOwnersIfNeeded(target, overlayViewGroup)
                }
                
                overlay.remove(dummy)

                // The environment is now completely safe. Proceed with Compose injection.
                installOwnersIfNeeded(target, compose)
                overlay.add(compose)
            }

            refreshGeometry()
            syncVisibility()
            target.post { backdrop.start() }
        }

        private fun disableClipping(view: View) {
            view.clipToOutline = false
            var p = view.parent
            var depth = 0
            while (p is View) {
                p.clipToOutline = false
                if (p is ViewGroup) {
                    p.clipChildren = false
                    p.clipToPadding = false
                }
                p = p.parent
                depth++
                if (depth > 4) break
            }
        }

        fun refreshGeometry() {
            val compose = composeView ?: return
            val density = target.resources.displayMetrics.density
            val overlayWidthPx = (80f * density).roundToInt()
            val overlayHeightPx = (48f * density).roundToInt()

            composeLeftInTarget = (target.width - overlayWidthPx) / 2
            composeTopInTarget = (target.height - overlayHeightPx) / 2

            val overlayLeftPx = target.left + composeLeftInTarget
            val overlayTopPx = target.top + composeTopInTarget

            compose.measure(
                View.MeasureSpec.makeMeasureSpec(overlayWidthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(overlayHeightPx, View.MeasureSpec.EXACTLY)
            )
            compose.layout(
                overlayLeftPx,
                overlayTopPx,
                overlayLeftPx + overlayWidthPx,
                overlayTopPx + overlayHeightPx
            )
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
            target.removeOnAttachStateChangeListener(attachListener)
            backdrop.release()

            val compose = composeView
            composeView = null
            compose?.setContent { }
            compose?.let { (parent.overlay as? ViewGroupOverlay)?.remove(it) }
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

    private val ownerMap = WeakHashMap<View, FakeLifecycleOwner>()

    private fun installOwnersIfNeeded(target: View, attachTarget: View) {
        val owner = ownerMap[target] ?: FakeLifecycleOwner().also {
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            ownerMap[target] = it
        }
        attachTarget.setViewTreeLifecycleOwner(owner)
        attachTarget.setViewTreeSavedStateRegistryOwner(owner)
        attachTarget.setViewTreeViewModelStoreOwner(owner)
    }

    private const val LIQUID_TOGGLE_SCALE = 0.975f
}
