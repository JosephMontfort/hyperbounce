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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kyant.backdrop.backdrops.emptyBackdrop
import dev.joseph.hyperbounce.liquidglass.LiquidToggle
import java.util.WeakHashMap

internal object LiquidToggleHost {
    private val hosts = WeakHashMap<View, Host>()

    @JvmStatic
    fun ensure(target: View) {
        if (target.width <= 0 || target.height <= 0) return
        if (!target.isAttachedToWindow) return
        if (hosts.containsKey(target)) {
            hosts[target]?.refreshGeometry()
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
    fun syncVisibility(target: View, isVisible: Boolean) {
        hosts[target]?.updateVisibility(isVisible)
    }

    @JvmStatic
    fun forwardTouchEvent(target: View, event: android.view.MotionEvent): Boolean {
        val host = hosts[target] ?: return false
        val compose = host.composeView ?: return false
        val offsetEvent = android.view.MotionEvent.obtain(event)
        offsetEvent.offsetLocation(-host.composeLeftInTarget.toFloat(), -host.composeTopInTarget.toFloat())
        val handled = compose.dispatchTouchEvent(offsetEvent)
        offsetEvent.recycle()
        return handled
    }

    @JvmStatic
    fun remove(target: View) {
        hosts.remove(target)?.dispose()
    }

    private class Host(private val target: View, private val parent: ViewGroup) {
        var composeView: ComposeView? = null
        private var checked by mutableStateOf((target as? CompoundButton)?.isChecked == true)
        private var isVisibleState by mutableStateOf(target.visibility == View.VISIBLE)
        
        var composeLeftInTarget by mutableIntStateOf(0)
        var composeTopInTarget by mutableIntStateOf(0)
        private val backdrop = LiquidToggleViewBackdrop(parent)

        private val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                syncChecked()
                refreshGeometry()
            }
            override fun onViewDetachedFromWindow(v: View) {
                LiquidToggleHost.remove(v)
            }
        }

        private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            refreshGeometry()
        }

        fun attach() {
            target.addOnAttachStateChangeListener(attachListener)
            target.addOnLayoutChangeListener(layoutListener)
            disableClipping(target)

            val safeContext = target.context
            val compose = ComposeView(safeContext).apply {
                tag = "HyperBounceLiquidToggle:${System.identityHashCode(target)}"
                isClickable = true
                isFocusable = true
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    LiquidToggleOverlay(
                        backdrop = backdrop,
                        visible = isVisibleState,
                        selected = { checked },
                        onSelect = { value ->
                            val button = target as? CompoundButton
                            if (button != null && button.isChecked != value) {
                                button.performClick()
                            }
                        }
                    )
                }
            }

            target.alpha = 0.0f
            
            val overlay = parent.overlay
            if (overlay != null) {
                val dummy = View(target.context)
                overlay.add(dummy)
                val overlayViewGroup = dummy.parent as? View
                if (overlayViewGroup != null) installOwnersIfNeeded(overlayViewGroup)
                overlay.remove(dummy)

                installOwnersIfNeeded(compose)
                overlay.add(compose)
            }

            composeView = compose
            backdrop.overlayViewToHide = compose
            backdrop.start()
            refreshGeometry()
        }

        private fun disableClipping(view: View) {
            var p: View? = view
            while (p != null) {
                p.clipToOutline = false
                if (p is ViewGroup) {
                    p.clipChildren = false
                    p.clipToPadding = false
                }
                p = p.parent as? View
            }
        }

        fun refreshGeometry() {
            val compose = composeView ?: return
            val density = target.resources.displayMetrics.density
            val overlayWidthPx = (80f * density).toInt()
            val overlayHeightPx = (48f * density).toInt()

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
            backdrop.setTargetBounds(left = target.left.toFloat(), top = target.top.toFloat(), width = target.width, height = target.height, paddingPx = (24f * density).toInt())
        }

        fun syncChecked() {
            checked = (target as? CompoundButton)?.isChecked == true
        }

        fun updateVisibility(isVisible: Boolean) {
            isVisibleState = isVisible
            composeView?.visibility = if (isVisible) View.VISIBLE else View.GONE
        }

        fun dispose() {
            target.removeOnAttachStateChangeListener(attachListener)
            target.removeOnLayoutChangeListener(layoutListener)
            target.alpha = 1f 

            val compose = composeView
            composeView = null
            backdrop.release()
            compose?.setContent { }
            compose?.let { parent.overlay?.remove(it) }
        }
    }

    @Composable
    private fun LiquidToggleOverlay(
        backdrop: com.kyant.backdrop.Backdrop,
        visible: Boolean,
        selected: () -> Boolean,
        onSelect: (Boolean) -> Unit,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (visible) {
                LiquidToggle(
                    selected = selected,
                    onSelect = onSelect,
                    backdrop = backdrop,
                    modifier = Modifier.graphicsLayer {
                        scaleX = 0.975f
                        scaleY = 0.975f
                    }
                )
            }
        }
    }

    private val ownerMap = WeakHashMap<View, FakeLifecycleOwner>()

    private fun installOwnersIfNeeded(attachTarget: View) {
        val owner = ownerMap[attachTarget] ?: FakeLifecycleOwner().also {
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            it.registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            ownerMap[attachTarget] = it
        }
        attachTarget.setViewTreeLifecycleOwner(owner)
        attachTarget.setViewTreeSavedStateRegistryOwner(owner)
        attachTarget.setViewTreeViewModelStoreOwner(owner)
    }
}
