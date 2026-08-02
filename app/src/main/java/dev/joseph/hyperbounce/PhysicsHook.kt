package dev.joseph.hyperbounce

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
                            val containerId = res.getIdentifier("bottom_bar_container", "id", "com.google.android.youtube")
                            val pivotId = res.getIdentifier("pivot_bar", "id", "com.google.android.youtube")
                            val dividerId = res.getIdentifier("navigation_bar_divider_frame", "id", "com.google.android.youtube")

                            if (dividerId != 0) {
                                activity.findViewById<View>(dividerId)?.alpha = 0f
                            }

                            // Inject directly into YouTube's container to avoid full-screen culling
                            if (containerId != 0 && pivotId != 0) {
                                val container = activity.findViewById<ViewGroup>(containerId)
                                val pivot = activity.findViewById<ViewGroup>(pivotId)
                                
                                if (container != null && pivot != null) {
                                    pivot.alpha = 0f
                                    
                                    val wrapper = pivot.getChildAt(0) as? ViewGroup
                                    if (wrapper != null && wrapper.childCount >= 3) {
                                        executeUIHeist(activity, container, pivot)
                                    }
                                }
                            }
                        }
                    })
                }
            }
        )
    }

    private fun executeUIHeist(activity: Activity, container: ViewGroup, stockBar: ViewGroup) {
        try {
            if (container.findViewWithTag<View>("HyperFloatingBar") != null) return

            XposedBridge.log("[HyperBounce] Injecting Liquid Glass into bottom_bar_container...")

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
                setBackgroundColor(Color.TRANSPARENT)
                
                // Use generic ViewGroup params since we are inside YouTube's container
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
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

            // CRITICAL FIX: Add directly to YouTube's bottom bar container instead of the Activity!
            container.addView(composeView)
            XposedBridge.log("[HyperBounce] Liquid Glass injected successfully into native container!")

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
