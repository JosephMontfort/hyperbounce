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

                    // Guard: don't add another listener if we already injected.
                    // Without this, every onResume (back from another app, screen-on, etc.)
                    // stacks a new OnGlobalLayoutListener onto the DecorView indefinitely.
                    if (decorView.findViewWithTag<View>("HyperFloatingBar") != null) return

                    decorView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            val pivotId   = res.getIdentifier("pivot_bar",                  "id", "com.google.android.youtube")
                            val dividerId = res.getIdentifier("navigation_bar_divider_frame","id", "com.google.android.youtube")

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

                // Use the modern non-deprecated insets API (minSdk 33, so WindowInsets.Type is safe).
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

                // *** KEY FIX ***
                // Do NOT call setParentCompositionContext() here.
                //
                // The old code created its own Recomposer running
                // runRecomposeAndApplyChanges() on AndroidUiDispatcher.CurrentThread.
                // That spawned a second Choreographer-driven frame loop alongside
                // YouTube's own Recomposer.  Modern YouTube is almost entirely Jetpack
                // Compose; two Recomposers racing on the same Choreographer caused
                // YouTube's compositions to miss their frame slots → blank screen.
                //
                // Without setParentCompositionContext, ComposeView calls
                // findViewTreeCompositionContext() which walks up to the DecorView.
                // YouTube's ComponentActivity stores its Recomposer there via
                // setViewTreeCompositionContext.  Our content is therefore composed
                // as a sibling under YouTube's properly-configured Recomposer —
                // no second frame loop, no conflict, videos render normally.

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
