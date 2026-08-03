package dev.joseph.hyperbounce

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
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

                    // VISIBILITY FIX: PreDrawListener runs every frame to track coordinates natively
                    decorView.viewTreeObserver.addOnPreDrawListener {
                        val containerId = res.getIdentifier("bottom_bar_container", "id", "com.google.android.youtube")
                        val pivotId     = res.getIdentifier("pivot_bar", "id", "com.google.android.youtube")

                        // 1. MAKE STOCK BAR DISAPPEAR: Shrink it to 0 height to reclaim the space safely
                        val container = if (containerId != 0) activity.findViewById<ViewGroup>(containerId) else null
                        if (container != null) {
                            val params = container.layoutParams
                            if (params != null && params.height != 0) {
                                params.height = 0
                                container.layoutParams = params
                            }
                        }

                        // 2. TRACK VISIBILITY & HIDE ICONS
                        val pivot = if (pivotId != 0) activity.findViewById<ViewGroup>(pivotId) else null
                        if (pivot != null) {
                            pivot.alpha = 0f // Keep stock icons invisible

                            val loc = IntArray(2)
                            pivot.getLocationOnScreen(loc)
                            // If Y-coordinate is <= screen height, YouTube intends for it to be visible
                            val isVisible = loc[1] <= decorView.height

                            // Toggle our floating pill visibility directly
                            val composeView = decorView.findViewWithTag<View>("HyperFloatingBar")
                            if (composeView != null) {
                                composeView.visibility = if (isVisible) View.VISIBLE else View.GONE
                            } else {
                                // Inject if not present
                                val wrapper = pivot.getChildAt(0) as? ViewGroup
                                if (wrapper != null && wrapper.childCount >= 3) {
                                    executeUIHeist(activity, pivot)
                                }
                            }
                        }
                        true
                    }
                }
            }
        )
    }

    private fun executeUIHeist(activity: Activity, stockBar: ViewGroup) {
        try {
            val decorView = activity.window.decorView as ViewGroup
            if (decorView.findViewWithTag<View>("HyperFloatingBar") != null) return

            XposedBridge.log("[HyperBounce] Injecting Liquid Glass overlay...")

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
                    decorView.rootWindowInsets
                        ?.getInsets(WindowInsets.Type.systemBars())
                        ?.bottom ?: 0
                } catch (e: Exception) { 0 }

                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity     = Gravity.BOTTOM
                    bottomMargin = navInsets
                }

                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)

                setContent {
                    LiquidNavBarHost(
                        stockBar        = stockBar,
                        externalTabState = tabState,
                    )
                }
            }

            decorView.addView(composeView)
            XposedBridge.log("[HyperBounce] Liquid Glass injected successfully!")

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

    override val lifecycle: Lifecycle          get() = registry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store
}
