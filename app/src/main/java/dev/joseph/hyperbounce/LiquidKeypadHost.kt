package dev.joseph.hyperbounce

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.joseph.hyperbounce.liquidglass.LiquidKeypadOverlay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.WeakHashMap

internal object LiquidKeypadHost {
    private val hosts = WeakHashMap<View, Host>()

    @JvmStatic
    fun ensure(target: View) {
        if (hosts.containsKey(target)) return
        val parent = target as? ViewGroup ?: return
        val host = Host(parent)
        hosts[target] = host
        host.attach()
    }

    @JvmStatic
    fun forwardTouchEvent(target: View, event: android.view.MotionEvent): Boolean {
        val host = hosts[target] ?: return false
        val compose = host.composeView ?: return false

        // Safely offset native relative touches to the Compose Absolute Coordinates
        val locationInRoot = IntArray(2)
        target.getLocationInWindow(locationInRoot)
        val rootLocation = IntArray(2)
        compose.getLocationInWindow(rootLocation)
        
        val dx = locationInRoot[0] - rootLocation[0]
        val dy = locationInRoot[1] - rootLocation[1]

        val clonedEvent = android.view.MotionEvent.obtain(event)
        clonedEvent.offsetLocation(dx.toFloat(), dy.toFloat())
        val handled = compose.dispatchTouchEvent(clonedEvent)
        clonedEvent.recycle()
        return handled
    }

    private class Host(private val rootContainer: ViewGroup) {
        var composeView: ComposeView? = null
        private var recomposer: Recomposer? = null
        private var recomposerScope: CoroutineScope? = null
        
        private val nativeKeyMap = mutableMapOf<String, View>()
        private val keyBoundsState = mutableStateMapOf<String, Rect>()

        fun attach() {
            try {
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
                            view.alpha = 0f // Make perfectly invisible
                            
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

                // Dispatchers.Unconfined guarantees it runs on the calling thread (wmshell.main) safely
                val currentDispatcher = Dispatchers.Unconfined 
                val isolatedMotionScale = object : MotionDurationScale { override val scaleFactor: Float = 1f }
                val isolatedRecomposer = Recomposer(currentDispatcher + isolatedMotionScale)
                val isolatedScope = CoroutineScope(SupervisorJob() + currentDispatcher)
                
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

            } catch (e: Throwable) {
                Log.e("HyperBounce", "FATAL ERROR IN COMPOSE INJECTION, FALLING BACK TO NATIVE KEYPAD", e)
                // Failsafe: Restore native keypad visibility to prevent device lockout
                nativeKeyMap.values.forEach { it.alpha = 1f }
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
