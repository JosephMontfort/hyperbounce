package dev.joseph.hyperbounce

import android.app.Activity
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PhysicsHook : IXposedHookLoadPackage {

    private var externalTabState: MutableState<Int>? = null

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.google.android.youtube") return

        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            lpparam.classLoader,
            "onResume",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    
                    if (activity.javaClass.simpleName != "MainActivity") return

                    val res = activity.resources
                    val decorView = activity.window.decorView as? ViewGroup ?: return

                    decorView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            val pivotId = res.getIdentifier("pivot_bar", "id", "com.google.android.youtube")
                            val dividerId = res.getIdentifier("navigation_bar_divider_frame", "id", "com.google.android.youtube")

                            if (dividerId != 0) {
                                activity.findViewById<View>(dividerId)?.alpha = 0f
                            }

                            if (pivotId != 0) {
                                val pivot = activity.findViewById<ViewGroup>(pivotId)
                                if (pivot != null) {
                                    pivot.alpha = 0f
                                    
                                    val wrapper = pivot.getChildAt(0) as? ViewGroup
                                    if (wrapper != null && wrapper.childCount >= 3) {
                                        executeUIHeist(activity, pivot)
                                    }
                                }
                            }
                        }
                    })
                }
            }
        )
    }

    private fun executeUIHeist(activity: Activity, stockBar: ViewGroup) {
        try {
            val decorView = activity.window.decorView as ViewGroup
            if (decorView.findViewWithTag<View>("HyperFloatingBar") != null) return

            XposedBridge.log("[HyperBounce] Injecting Liquid Glass overlay into YouTube...")

            val lifecycleOwner = FakeLifecycleOwner().apply {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            val tabState = mutableStateOf(0)
            externalTabState = tabState

            val composeView = ComposeView(activity).apply {
                tag = "HyperFloatingBar"
                clipChildren = false
                clipToPadding = false
                
                // CRITICAL FIX: Tell Android this view is see-through. 
                // Prevents the GPU from culling (blacking out) the YouTube app underneath!
                setBackgroundColor(Color.TRANSPARENT)
                
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)

                val coroutineContext = AndroidUiDispatcher.CurrentThread
                val recomposer = Recomposer(coroutineContext)
                setParentCompositionContext(recomposer)
                CoroutineScope(coroutineContext).launch {
                    recomposer.runRecomposeAndApplyChanges()
                }

                setContent {
                    LiquidNavBarHost(
                        stockBar = stockBar,
                        externalTabState = tabState,
                    )
                }
            }

            // Using addContentView is strictly safer for overlays than touching DecorView directly
            activity.addContentView(composeView, composeView.layoutParams)
            XposedBridge.log("[HyperBounce] Liquid Glass injected successfully!")

            // DIAGNOSTIC PROBE: Logs YouTube's content dimensions 2 seconds after injection
            Handler(Looper.getMainLooper()).postDelayed({
                XposedBridge.log("[HyperBounce] --- DIAGNOSTIC PROBE ---")
                val content = activity.findViewById<ViewGroup>(android.R.id.content)
                if (content != null) {
                    for (i in 0 until content.childCount) {
                        val child = content.getChildAt(i)
                        XposedBridge.log("[HyperBounce] Content Child $i: ${child.javaClass.simpleName} (w:${child.width}, h:${child.height}, visibility:${child.visibility})")
                    }
                }
            }, 2000)

        } catch (e: Exception) {
            XposedBridge.log("[HyperBounce] Exception in YouTube UI Heist: ${e.message}")
        }
    }
}

class FakeLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {
    val registry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    init { savedStateRegistryController.performRestore(null) }
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store
}
