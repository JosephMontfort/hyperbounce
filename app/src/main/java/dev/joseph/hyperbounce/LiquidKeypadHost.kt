package dev.joseph.hyperbounce

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.joseph.hyperbounce.liquidglass.LiquidKeypadOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.WeakHashMap

internal object LiquidKeypadHost {
    private val hosts = WeakHashMap<View, Host>()
    private val mainHandler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun ensure(target: View) {
        // Enforce Main UI Thread strictly to prevent Lifecycle wmshell.main crashes
        mainHandler.post {
            if (hosts.containsKey(target)) return@post
            val parent = target as? ViewGroup ?: return@post
            val host = Host(parent)
            hosts[target] = host
            host.attach()
        }
    }

    @JvmStatic
    fun forwardTouchEvent(target: View, event: android.view.MotionEvent): Boolean {
        val host = hosts[target] ?: return false
        val compose = host.composeView ?: return false
        return compose.dispatchTouchEvent(event)
    }

    private class Host(private val rootContainer: ViewGroup) {
        var composeView: ComposeView? = null
        private var recomposer: Recomposer? = null
        private var recomposerScope: CoroutineScope? = null
        
        private val nativeKeyMap = mutableMapOf<String, View>()
        private val keyBoundsState = mutableStateMapOf<String, Rect>()

        fun attach() {
            fun mapNativeKeys(view: View) {
                if (view.javaClass.name.contains("MiuiNumPadKey")) {
                    var digit = ""
                    var cls: Class<*>? = view.javaClass
                    while (cls != null) {
                        try {
                            val f = cls.getDeclaredField("mDigitText")
                            f.isAccessible = true
                            (f.get(view) as? TextView)?.let { digit = it.text.toString() }
                            break
                        } catch (e: Exception) { cls = cls.superclass }
                    }
                    if (digit.isNotEmpty()) {
                        nativeKeyMap[digit] = view
                        view.alpha = 0f // Make the native key perfectly invisible
                        
                        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                            val location = IntArray(2)
                            v.getLocationInWindow(location)
                            keyBoundsState[digit] = Rect(location[0], location[1], location[0] + v.width, location[1] + v.height)
                        }
                    }
                } else if (view is ViewGroup) {
                    for (i in 0 until view.childCount) mapNativeKeys(view.getChildAt(i))
                }
            }
            mapNativeKeys(rootContainer)

            val baseCtx = rootContainer.context
            val safeContext = object : android.view.ContextThemeWrapper(baseCtx, 0) {
                override fun getApplicationContext(): Context = baseCtx.applicationContext ?: baseCtx
            }

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
                    LiquidKeypadOverlay(nativeKeys = nativeKeyMap, keyBounds = keyBoundsState)
                }
            }

            val overlay = rootContainer.overlay
            if (overlay != null) {
                val dummy = View(rootContainer.context)
                overlay.add(dummy)
                (dummy.parent as? View)?.let { installOwnersIfNeeded(it) }
                overlay.remove(dummy)

                installOwnersIfNeeded(compose)
                overlay.add(compose)
            }
            composeView = compose
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
