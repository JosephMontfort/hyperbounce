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

internal class FakeApplication(base: Context) : android.app.Application() {
    init { attachBaseContext(base) }
    override fun getApplicationContext(): Context = this
    override fun registerComponentCallbacks(callback: ComponentCallbacks?) {}
    override fun unregisterComponentCallbacks(callback: ComponentCallbacks?) {}
}

internal class SafeContext(base: Context) : ContextWrapper(base) {
    private val fakeApp by lazy { FakeApplication(base) }
    override fun getApplicationContext(): Context = fakeApp
    override fun registerComponentCallbacks(callback: ComponentCallbacks?) {}
    override fun unregisterComponentCallbacks(callback: ComponentCallbacks?) {}
}

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
    fun remove(target: View) {
        hosts.remove(target)?.dispose()
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

    private class Host(private val target: View, private val parent: ViewGroup) {
        private val captureSource: View = findCaptureSource(target)
        private val backdrop = LiquidToggleViewBackdrop(captureSource)

        var composeView: ComposeView? = null
        private var checked by mutableStateOf((target as? CompoundButton)?.isChecked == true)
        private var isVisibleState by mutableStateOf(target.visibility == View.VISIBLE)
        
        var composeLeftInTarget by mutableIntStateOf(0)
        var composeTopInTarget by mutableIntStateOf(0)

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

            val safeContext = SafeContext(target.context)
            val compose = ComposeView(safeContext).apply {
                tag = "HyperBounceLiquidToggle:${System.identityHashCode(target)}"
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (isVisibleState) {
                            LiquidToggle(
                                selected = { checked },
                                onSelect = { value ->
                                    val button = target as? CompoundButton
                                    if (button != null && button.isChecked != value) {
                                        button.performClick()
                                    }
                                },
                                // Link the backdrop object back to Compose so it can refract the captured frame!
                                backdrop = backdrop,
                                modifier = Modifier.graphicsLayer {
                                    scaleX = 0.975f
                                    scaleY = 0.975f
                                }.onGloballyPositioned { coordinates ->
                                    val padding = with(LocalDensity.current) { 24.dp.toPx().roundToInt() }
                                    backdrop.setTargetBounds(
                                        left = coordinates.positionInWindow().x,
                                        top = coordinates.positionInWindow().y,
                                        width = coordinates.size.width,
                                        height = coordinates.size.height,
                                        paddingPx = padding,
                                    )
                                }
                            )
                        }
                    }
                }
            }
            
            val overlay = parent.overlay as? ViewGroupOverlay
            if (overlay != null) {
                val dummy = View(safeContext)
                overlay.add(dummy)
                val overlayViewGroup = dummy.parent as? View
                if (overlayViewGroup != null) installOwnersIfNeeded(overlayViewGroup)
                overlay.remove(dummy)

                installOwnersIfNeeded(compose)
                overlay.add(compose)
            }

            composeView = compose
            backdrop.overlayViewToHide = compose
            refreshGeometry()
            target.post { backdrop.start() }
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

            isVisibleState = target.visibility == View.VISIBLE
        }

        fun syncChecked() {
            checked = (target as? CompoundButton)?.isChecked == true
        }

        fun dispose() {
            target.removeOnAttachStateChangeListener(attachListener)
            target.removeOnLayoutChangeListener(layoutListener)
            backdrop.release()
            val compose = composeView
            composeView = null
            compose?.setContent { }
            compose?.let { (parent.overlay as? ViewGroupOverlay)?.remove(it) }
        }
    }

    private fun findCaptureSource(target: View): View {
        var p: View? = target.parent as? View
        var candidate: View = target
        while (p != null) {
            val name = p.javaClass.simpleName.lowercase()
            if (name.contains("recycler") || name.contains("list") || name.contains("scroll")) {
                return p
            }
            if (p.background != null && name != "decorview") {
                candidate = p
            }
            if (p.parent !is View) break
            p = p.parent as? View
        }
        return candidate
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
