package dev.joseph.hyperbounce

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam

class PhysicsHook : IXposedHookLoadPackage {

    private val isDarkState = mutableStateOf(false)

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.google.android.youtube") return

        // 1. THE ZERO-LAG STRETCHER
        try {
            XposedHelpers.findAndHookMethod(View::class.java, "setPadding", 
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, 
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        if (view.id != View.NO_ID && view.resources != null) {
                            try {
                                if (view.resources.getResourceEntryName(view.id) == "pane_fragment_container") {
                                    param.args[3] = 0 // Force bottom padding to 0 natively
                                }
                            } catch (e: Exception) {}
                        }
                    }
                })
        } catch (t: Throwable) {}

        // 2. GHOST BAR & COMPONENT SHIFTER
        try {
            XposedHelpers.findAndHookMethod(View::class.java, "onAttachedToWindow", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val view = param.thisObject as View
                    if (view.id == View.NO_ID || view.resources == null) return

                    try {
                        val resName = view.resources.getResourceEntryName(view.id)

                        // Ghost the stock bars completely
                        if (resName == "bottom_bar_container" || resName == "pivot_bar") {
                            view.alpha = 0f
                            view.translationY = 9999f
                            val lp = view.layoutParams
                            if (lp != null && lp.height != 0) {
                                lp.height = 0
                                view.layoutParams = lp
                            }
                        }

                        // Shift regular YouTube FABs
                        if (resName == "fab_container") {
                            view.translationY = -180f
                        }

                        // Shift YouTube Shorts Master Footer (Captions, Account, Action Buttons)
                        if (resName == "reel_player_footer_container") {
                            view.translationY = -200f
                        }

                        // Trigger Compose Injection
                        if (resName == "watch_while_layout_coordinator_layout") {
                            val activity = view.context as? Activity
                            if (activity != null) {
                                view.post {
                                    val pivotId = activity.resources.getIdentifier("pivot_bar", "id", activity.packageName)
                                    val pivotBar = activity.window.decorView.findViewById<ViewGroup>(pivotId)
                                    if (pivotBar != null) {
                                        executeUIHeist(activity, view as ViewGroup, pivotBar)
                                        
                                        // Continuous Theme Checker
                                        activity.window.decorView.viewTreeObserver.addOnPreDrawListener {
                                            val currentDark = isYouTubeInDarkMode(activity)
                                            if (isDarkState.value != currentDark) {
                                                isDarkState.value = currentDark
                                            }
                                            true
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }
            })
        } catch (t: Throwable) {}
    }

    // Mathematical Luminance Scanner to beat YouTube's custom theme engine
    private fun isYouTubeInDarkMode(activity: Activity): Boolean {
        val typedValue = TypedValue()
        activity.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true)
        if (typedValue.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) {
            val color = typedValue.data
            val r = Color.red(color)
            val g = Color.green(color)
            val b = Color.blue(color)
            // Standard relative luminance formula
            val luminance = 0.299 * r + 0.587 * g + 0.114 * b
            return luminance < 128 // Less than 128 means the background is dark
        }
        // Fallback to system uiMode if extraction fails
        val uiMode = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    private fun executeUIHeist(activity: Activity, rootGroup: ViewGroup, pivotBar: ViewGroup) {
        try {
            if (rootGroup.findViewWithTag<View>("HyperFloatingBar") != null) return

            XposedBridge.log("[HyperBounce] Injecting YouTube Liquid Bar...")

            val decorView = activity.window.decorView as ViewGroup

            val lifecycleOwner = FakeLifecycleOwner().apply {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            if (decorView.findViewTreeLifecycleOwner() == null) decorView.setViewTreeLifecycleOwner(lifecycleOwner)
            if (decorView.findViewTreeSavedStateRegistryOwner() == null) decorView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            if (decorView.findViewTreeViewModelStoreOwner() == null) decorView.setViewTreeViewModelStoreOwner(lifecycleOwner)

            val tabState = mutableStateOf(0)

            val composeView = ComposeView(activity).apply {
                tag = "HyperFloatingBar"
                clipChildren = false
                clipToPadding = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                
                setContent {
                    LiquidNavBarHost(
                        pivotBar = pivotBar,
                        externalTabState = tabState,
                        isDarkState = isDarkState
                    )
                }
            }

            rootGroup.addView(composeView)
            XposedBridge.log("[HyperBounce] Overlay attached.")
        } catch (e: Exception) {
            XposedBridge.log("[HyperBounce] Exception: ${e.message}")
        }
    }
}

class FakeLifecycleOwner :
    LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    val registry = LifecycleRegistry(this)
    private val ctrl  = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()
    init { ctrl.performRestore(null) }
    override val lifecycle: Lifecycle                get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = ctrl.savedStateRegistry
    override val viewModelStore: ViewModelStore      get() = store
}
