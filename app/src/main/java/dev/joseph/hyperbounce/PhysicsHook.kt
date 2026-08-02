package dev.joseph.hyperbounce

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
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

                    if (decorView.findViewWithTag<View>("HyperFloatingBar") != null) return

                    decorView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            val containerId = res.getIdentifier("bottom_bar_container", "id", "com.google.android.youtube")
                            val pivotId     = res.getIdentifier("pivot_bar",            "id", "com.google.android.youtube")

                            // CRITICAL FIX: NEVER touch 'navigation_bar_divider_frame' - it wraps the entire app!
                            
                            val container = if (containerId != 0) activity.findViewById<ViewGroup>(containerId) else null
                            val pivot     = if (pivotId != 0) activity.findViewById<ViewGroup>(pivotId) else null

                            if (container != null) {
                                // Remove the opaque black background behind the stock bar without breaking layout
                                container.setBackgroundColor(Color.TRANSPARENT)
                            }

                            if (pivot != null && pivot.alpha != 0f) {
                                // Hide the stock icons safely
                                pivot.alpha = 0f
                                
                                val wrapper = pivot.getChildAt(0) as? ViewGroup
                                if (wrapper != null && wrapper.childCount >= 3) {
                                    executeUIHeist(activity, pivot)
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
            val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
            if (contentView.findViewWithTag<View>("HyperFloatingBar") != null) return

            XposedBridge.log("[HyperBounce] Injecting True Liquid Glass UI...")

            val lifecycleOwner = FakeLifecycleOwner().apply {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            val tabState = mutableStateOf(0)
            externalTabState = tabState

            val composeView = ComposeView(activity).apply {
                tag = "HyperFloatingBar"
                clipChildren  = false
                clipToPadding = false
                setBackgroundColor(Color.TRANSPARENT)

                val navInsets = try {
                    contentView.rootWindowInsets?.getInsets(WindowInsets.Type.systemBars())?.bottom ?: 0
                } catch (e: Exception) { 0 }

                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity      = Gravity.BOTTOM
                    bottomMargin = navInsets
                }

                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                
                // Safely attach to the ComposeView ONLY
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

            // Inject into R.id.content to avoid DecorView z-order issues entirely
            contentView.addView(composeView)

        } catch (e: Exception) {
            XposedBridge.log("[HyperBounce] Exception in UI Heist: ${e.message}")
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
