package dev.joseph.hyperbounce

import android.app.Activity
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

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.google.android.youtube") return

        // 1. THE ZERO-LAG STRETCHER
        // Natively kills the padding on the feed container to fill the white void
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

        // 2. GHOST BAR & FAB SHIFTER
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

                        // Shift YouTube's floating action buttons up to clear our 40.dp bar
                        if (resName == "fab_container") {
                            view.translationY = -180f
                        }

                        // Trigger Compose Injection when the master layout attaches
                        if (resName == "watch_while_layout_coordinator_layout") {
                            val activity = view.context as? Activity
                            if (activity != null) {
                                view.post {
                                    val pivotId = activity.resources.getIdentifier("pivot_bar", "id", activity.packageName)
                                    val pivotBar = activity.window.decorView.findViewById<ViewGroup>(pivotId)
                                    if (pivotBar != null) {
                                        executeUIHeist(activity, view as ViewGroup, pivotBar)
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {}
                }
            })
        } catch (t: Throwable) {}
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

            // FIX: Attach owners to the absolute top of the view tree (DecorView) 
            // so Compose's WindowRecomposer can always find them!
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
                        externalTabState = tabState
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
