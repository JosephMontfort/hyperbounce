package dev.joseph.hyperbounce

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.MotionDurationScale
import androidx.compose.runtime.Recomposer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.joseph.hyperbounce.liquidglass.JellyPinButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.WeakHashMap

internal object LiquidKeypadHost {
    private val hosts = WeakHashMap<View, Host>()

    @JvmStatic
    fun ensure(target: View, digitStr: String) {
        if (target.width <= 0 || target.height <= 0) return
        if (!target.isAttachedToWindow) return
        if (hosts.containsKey(target)) {
            hosts[target]?.refreshGeometry()
            return
        }

        val parent = target.parent as? ViewGroup ?: return
        val host = Host(target, parent, digitStr)
        hosts[target] = host
        host.attach()
    }

    @JvmStatic
    fun forwardTouchEvent(target: View, event: android.view.MotionEvent): Boolean {
        val host = hosts[target] ?: return false
        val compose = host.composeView ?: return false
        // Offset the touch coordinates to match the ComposeView's local space
        val offsetEvent = android.view.MotionEvent.obtain(event)
        offsetEvent.offsetLocation(-host.composeLeftInTarget.toFloat(), -host.composeTopInTarget.toFloat())
        val handled = compose.dispatchTouchEvent(offsetEvent)
        offsetEvent.recycle()
        return handled
    }

    private class Host(private val target: View, private val parent: ViewGroup, private val digitStr: String) {
        var composeView: ComposeView? = null
        private var recomposer: Recomposer? = null
        private var recomposerScope: CoroutineScope? = null
        
        var composeLeftInTarget = 0
        var composeTopInTarget = 0
        private val backdrop = LiquidToggleViewBackdrop(parent)

        private val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { refreshGeometry() }
            override fun onViewDetachedFromWindow(v: View) { dispose() }
        }

        private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshGeometry() }

        fun attach() {
            target.addOnAttachStateChangeListener(attachListener)
            target.addOnLayoutChangeListener(layoutListener)

            val baseCtx = target.context
            val safeContext = object : android.view.ContextThemeWrapper(baseCtx, 0) {
                override fun getApplicationContext(): Context = baseCtx.applicationContext ?: baseCtx
            }

            // Isolate the Compose Recomposer from SystemUI
            val isolatedMotionScale = object : MotionDurationScale { override val scaleFactor: Float = 1f }
            val isolatedRecomposer = Recomposer(AndroidUiDispatcher.Main + isolatedMotionScale)
            val isolatedScope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)
            recomposer = isolatedRecomposer
            recomposerScope = isolatedScope
            isolatedScope.launch {
                try { isolatedRecomposer.runRecomposeAndApplyChanges() } catch (_: Throwable) {}
            }

            val compose = ComposeView(safeContext).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setParentCompositionContext(isolatedRecomposer)
                setContent {
                    JellyPinButton(digitStr = digitStr, backdrop = backdrop)
                }
            }

            // Inject the ComposeView into the native view's overlay
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

        fun refreshGeometry() {
            val compose = composeView ?: return
            
            // Map the ComposeView to perfectly cover the native key
            composeLeftInTarget = 0
            composeTopInTarget = 0
            val overlayLeftPx = target.left
            val overlayTopPx = target.top

            compose.measure(
                View.MeasureSpec.makeMeasureSpec(target.width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(target.height, View.MeasureSpec.EXACTLY)
            )
            compose.layout(
                overlayLeftPx,
                overlayTopPx,
                overlayLeftPx + target.width,
                overlayTopPx + target.height
            )
            backdrop.setTargetBounds(left = target.left.toFloat(), top = target.top.toFloat(), width = target.width, height = target.height, paddingPx = 0)
        }

        fun dispose() {
            target.removeOnAttachStateChangeListener(attachListener)
            target.removeOnLayoutChangeListener(layoutListener)

            val compose = composeView
            composeView = null
            backdrop.release()
            compose?.setContent { }
            compose?.let { parent.overlay?.remove(it) }

            recomposer?.cancel()
            recomposer = null
            recomposerScope?.cancel()
            recomposerScope = null
            hosts.remove(target)
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
