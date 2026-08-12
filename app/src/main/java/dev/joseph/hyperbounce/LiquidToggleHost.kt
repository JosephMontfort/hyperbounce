package dev.joseph.hyperbounce

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
        
        // Touch events are relative to the target toggle. 
        // We must offset them to the ComposeView's coordinate space.
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

            installOwnersIfNeeded(target, compose)
            composeView = compose
            (parent.overlay as? ViewGroupOverlay)?.add(compose)

            refreshGeometry()
            syncVisibility()
            target.post { backdrop.start() }
        }

        fun refreshGeometry() {
            val compose = composeView ?: return
            val density = target.resources.displayMetrics.density
            val overlayWidthPx = (80f * density).roundToInt()
            val overlayHeightPx = (48f * density).roundToInt()

            composeLeftInTarget = (target.width - overlayWidthPx) / 2
            composeTopInTarget = (target.height - overlayHeightPx) / 2

            // Overlay layout bounds are relative to the parent ViewGroup
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
            // Fix: Safe call using 'it' as ViewGroupOverlay expects a non-null View
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

    private fun installOwnersIfNeeded(target: View, compose: ComposeView) {
        val owner = ownerMap[target] ?: FakeLifecycleOwner().also {
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            ownerMap[target] = it
        }
        compose.setViewTreeLifecycleOwner(owner)
        compose.setViewTreeSavedStateRegistryOwner(owner)
        compose.setViewTreeViewModelStoreOwner(owner)
    }

    private const val LIQUID_TOGGLE_SCALE = 0.975f
}
