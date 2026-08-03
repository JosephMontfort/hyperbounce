package dev.joseph.hyperbounce

import android.app.Activity
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
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
    // Set to true so the pill is visible in our sandbox
    private var isBarVisible: MutableState<Boolean> = mutableStateOf(true)

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.google.android.youtube") return

        // --- INJECTED RAW TOUCH TRACER ---
        try {
            XposedHelpers.findAndHookMethod(
                "android.view.View",
                lpparam.classLoader,
                "dispatchTouchEvent",
                MotionEvent::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val view = param.thisObject as View
                        val event = param.args[0] as MotionEvent
                        
                        // Only log the initial finger-down press to avoid spamming the log with drag/swipe events
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            var viewName = "NO_ID"
                            if (view.id > 0) {
                                try {
                                    viewName = view.context.resources.getResourceEntryName(view.id)
                                } catch (e: Exception) {}
                            }
                            
                            // Filter specifically for the bottom navigation bar elements
                            if (viewName.contains("pivot") || viewName.contains("tab") || viewName.contains("icon")) {
                                XposedBridge.log("\n=======================================================")
                                XposedBridge.log("[+] [TouchTracer] Raw Touch Detected on: ${view.javaClass.name}")
                                XposedBridge.log("[+] [TouchTracer] View ID Name: $viewName")
                                XposedBridge.log("[+] [TouchTracer] MotionEvent: X=${event.rawX}, Y=${event.rawY}")
                                XposedBridge.log("[+] [TouchTracer] Stack Trace:")
                                XposedBridge.log(android.util.Log.getStackTraceString(java.lang.Exception("TouchTrace")))
                                XposedBridge.log("=======================================================\n")
                            }
                        }
                    }
                }
            )
        } catch (e: Exception) {
            XposedBridge.log("[HyperBounce] Failed to inject Touch Tracer: ${e.message}")
        }
        // ---------------------------------

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
                            val pivotId = res.getIdentifier("pivot_bar", "id", "com.google.android.youtube")
                            val dividerId = res.getIdentifier("navigation_bar_divider_frame", "id", "com.google.android.youtube")

                            if (dividerId != 0) {
                                // This causes the blank screen, exactly as you wanted for the sandbox
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

                val coroutineContext = AndroidUiDispatcher.CurrentThread
                val recomposer = Recomposer(coroutineContext)
                setParentCompositionContext(recomposer)
                CoroutineScope(coroutineContext).launch {
                    recomposer.runRecomposeAndApplyChanges()
                }

                setContent {
                    LiquidNavBarHost(
                        stockBar        = stockBar,
                        externalTabState = tabState,
                        isVisible       = isBarVisible // THE MISSING PARAMETER ADDED HERE
                    )
                }
            }

            decorView.addView(composeView)

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
