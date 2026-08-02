package dev.joseph.hyperbounce

import android.app.Activity
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.compose.runtime.MutableState
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

    private var lastUiMode = -1
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
                    val res = activity.resources

                    val containerId = res.getIdentifier("bottom_bar_container", "id", "com.google.android.youtube")
                    val pivotId = res.getIdentifier("pivot_bar", "id", "com.google.android.youtube")

                    if (containerId == 0 && pivotId == 0) return

                    val decorView = activity.window.decorView as? ViewGroup ?: return

                    decorView.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            val container = if (containerId != 0) activity.findViewById<ViewGroup>(containerId) else null
                            val pivot = if (pivotId != 0) activity.findViewById<ViewGroup>(pivotId) else null

                            val stockBar = container ?: pivot ?: return

                            // Nuke stock bottom views to prevent gaps
                            if (container != null) {
                                container.visibility = View.GONE
                                val params = container.layoutParams
                                if (params != null && params.height != 0) {
                                    params.height = 0
                                    container.layoutParams = params
                                }
                            }

                            if (pivot != null) {
                                pivot.visibility = View.GONE
                                val params = pivot.layoutParams
                                if (params != null && params.height != 0) {
                                    params.height = 0
                                    pivot.layoutParams = params
                                }
                            }

                            executeUIHeist(activity, stockBar)
                        }
                    })
                }
            }
        )
    }

    private fun executeUIHeist(activity: Activity, stockBar: ViewGroup) {
        try {
            val rootView = activity.window.decorView as ViewGroup
            if (rootView.findViewWithTag<View>("HyperFloatingBar") != null) return

            XposedBridge.log("[HyperBounce] Injecting overlay into YouTube...")

            val lifecycleOwner = FakeLifecycleOwner().apply {
                registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            }

            if (rootView.findViewTreeLifecycleOwner() == null)
                rootView.setViewTreeLifecycleOwner(lifecycleOwner)
            if (rootView.findViewTreeSavedStateRegistryOwner() == null)
                rootView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            if (rootView.findViewTreeViewModelStoreOwner() == null)
                rootView.setViewTreeViewModelStoreOwner(lifecycleOwner)

            val tabState = mutableStateOf(0)
            externalTabState = tabState

            val composeView = ComposeView(activity).apply {
                tag = "HyperFloatingBar"
                clipChildren = false
                clipToPadding = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setContent {
                    LiquidNavBarHost(
                        stockBar = stockBar,
                        externalTabState = tabState,
                    )
                }
            }

            rootView.addView(composeView)
            XposedBridge.log("[HyperBounce] YouTube overlay attached.")

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

    override val lifecycle: Lifecycle
        get() = registry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore
        get() = store
}
