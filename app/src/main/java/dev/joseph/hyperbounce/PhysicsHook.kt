package dev.joseph.hyperbounce

import android.app.Activity
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
    private var isBarVisible: MutableState<Boolean> = mutableStateOf(false)
    private val LOG = "HyperBounce"   // adb logcat -s HyperBounce

    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.google.android.youtube") return

        // ══════════════════════════════════════════════════════════════════
        // DIAGNOSTIC HOOKS — five independent layers so nothing escapes
        // ══════════════════════════════════════════════════════════════════
        installTouchLogger(lpparam)

        XposedHelpers.findAndHookMethod(
            "android.app.Activity",
            lpparam.classLoader,
            "onResume",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    if (activity.javaClass.simpleName != "MainActivity") return

                    val res        = activity.resources
                    val decorView  = activity.window.decorView as? ViewGroup ?: return
                    if (decorView.findViewWithTag<View>("HyperFloatingBar") != null) return

                    val containerId = res.getIdentifier("bottom_bar_container", "id", "com.google.android.youtube")
                    val pivotId     = res.getIdentifier("pivot_bar",            "id", "com.google.android.youtube")

                    decorView.viewTreeObserver.addOnPreDrawListener {
                        val container = if (containerId != 0) activity.findViewById<ViewGroup>(containerId) else null
                        val pivot     = if (pivotId     != 0) activity.findViewById<ViewGroup>(pivotId)     else null

                        // ── DIAGNOSTIC: stock bar stays fully visible ──────────────────
                        // We do NOT collapse container height and do NOT set pivot.alpha=0f.
                        // The user physically taps the real Home tab while our hooks below
                        // capture exactly what YouTube class handles the navigation.
                        container?.setBackgroundColor(Color.TRANSPARENT)   // only clear bg colour

                        // Visibility tracking (still needed for our floating bar)
                        if (pivot != null) {
                            val loc = IntArray(2)
                            pivot.getLocationOnScreen(loc)
                            val nowVisible = loc[1] <= decorView.height
                            if (isBarVisible.value != nowVisible) isBarVisible.value = nowVisible

                            val wrapper = pivot.getChildAt(0) as? ViewGroup
                            if (wrapper != null && wrapper.childCount >= 3) {
                                executeUIHeist(activity, pivot)
                            }
                        }
                        true
                    }
                }
            }
        )
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Five-layer touch logger
    // ──────────────────────────────────────────────────────────────────────────
    private fun installTouchLogger(lpparam: LoadPackageParam) {

        // ── Layer 1: Activity.dispatchTouchEvent ─────────────────────────────
        // The absolute first point inside the app where any touch is visible.
        // Logs every ACTION_DOWN with screen coordinates.
        hookSafe("android.app.Activity", "dispatchTouchEvent", lpparam,
                 MotionEvent::class.java) { param ->
            val event = param.args[0] as? MotionEvent ?: return@hookSafe
            if (event.action != MotionEvent.ACTION_DOWN) return@hookSafe
            val act = param.thisObject as? Activity ?: return@hookSafe
            if (act.javaClass.simpleName != "MainActivity") return@hookSafe
            log("TOUCH_DOWN x=${event.x.toInt()} y=${event.y.toInt()}")
        }

        // ── Layer 2: View.setOnClickListener ────────────────────────────────
        // Fires once at startup whenever YouTube attaches a click handler to a view.
        // Filters to IDs that look navigation-related.  Tells us WHICH YouTube class
        // is the registered handler for each tab button — even if the name is obfuscated.
        hookSafe("android.view.View", "setOnClickListener", lpparam,
                 View.OnClickListener::class.java) { param ->
            val view     = param.thisObject as? View ?: return@hookSafe
            val listener = param.args[0]             ?: return@hookSafe
            val idName   = safeResName(view) ?: return@hookSafe
            if (!idName.containsAny("tab", "nav", "pivot", "home", "bar", "bottom")) return@hookSafe
            log("SET_LISTENER  id=$idName  listener=${listener.javaClass.name}")
        }

        // ── Layer 3: View.performClick ───────────────────────────────────────
        // Fires for every programmatic AND gesture-driven click anywhere in YouTube.
        // Filtered to bottom 22 % of screen (nav bar zone) to avoid noise.
        // Logs: view class, resource ID, OnClickListener class, full 30-frame stack.
        hookSafe("android.view.View", "performClick", lpparam) { param ->
            val view = param.thisObject as? View ?: return@hookSafe
            val metrics = try { view.resources.displayMetrics } catch (_: Exception) { return@hookSafe }
            val loc = IntArray(2)
            view.getLocationOnScreen(loc)
            if (loc[1] < metrics.heightPixels * 0.78f) return@hookSafe   // skip non-nav views

            val sb = StringBuilder("PERF_CLICK")
            sb.append("  cls=").append(view.javaClass.name)
            sb.append("  id=").append(safeResName(view) ?: view.id)
            sb.append("  loc=").append(loc[0]).append(",").append(loc[1])
            sb.append("  alpha=").append(view.alpha)
            sb.append("  listener=").append(reflectListener(view) ?: "?")
            log(sb.toString())

            val stack = Thread.currentThread().stackTrace
            for (i in 2 until minOf(stack.size, 32)) log("  STACK ${stack[i]}")
        }

        // ── Layer 4: View.callOnClick ────────────────────────────────────────
        // The accessibility path: performAccessibilityAction → callOnClick.
        // If our forceClick goes here but home tab still doesn't navigate, this tells us.
        hookSafe("android.view.View", "callOnClick", lpparam) { param ->
            val view = param.thisObject as? View ?: return@hookSafe
            val loc = IntArray(2); view.getLocationOnScreen(loc)
            val h = try { view.resources.displayMetrics.heightPixels } catch (_: Exception) { 0 }
            if (loc[1] < h * 0.78f) return@hookSafe
            log("CALL_CLICK  cls=${view.javaClass.name}  id=${safeResName(view) ?: view.id}")
            val stack = Thread.currentThread().stackTrace
            for (i in 2 until minOf(stack.size, 20)) log("  STACK ${stack[i]}")
        }

        // ── Layer 5: ViewGroup.onInterceptTouchEvent ─────────────────────────
        // If a parent ViewGroup intercepts the touch BEFORE it reaches the tab button,
        // that parent's class will appear here.  Explains "home tap does nothing" —
        // something might be eating the event above the tab.
        hookSafe("android.view.ViewGroup", "onInterceptTouchEvent", lpparam,
                 MotionEvent::class.java) { param ->
            val event = param.args[0] as? MotionEvent ?: return@hookSafe
            if (event.action != MotionEvent.ACTION_DOWN) return@hookSafe
            val vg = param.thisObject as? ViewGroup ?: return@hookSafe
            val loc = IntArray(2); vg.getLocationOnScreen(loc)
            val h = try { vg.resources.displayMetrics.heightPixels } catch (_: Exception) { 0 }
            if (loc[1] < h * 0.75f) return@hookSafe   // bottom 25 % only
            val returning = param.result as? Boolean
            log("INTERCEPT  cls=${vg.javaClass.name}  id=${safeResName(vg) ?: vg.id}" +
                "  loc=${loc[0]},${loc[1]}  intercepts=${returning}")
        }

        log("==== HyperBounce diagnostic hooks installed ====")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun log(msg: String) {
        Log.d(LOG, msg)
        XposedBridge.log("[$LOG] $msg")
    }

    /** Hook a method safely; swallow if the method / class doesn't exist. */
    private fun hookSafe(
        className: String,
        methodName: String,
        lpparam: LoadPackageParam,
        vararg parameterTypes: Class<*>,
        before: (XC_MethodHook.MethodHookParam) -> Unit,
    ) {
        try {
            val types: Array<Any> = if (parameterTypes.isEmpty()) {
                arrayOf(object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) = before(param)
                })
            } else {
                arrayOf(*parameterTypes, object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) = before(param)
                })
            }
            XposedHelpers.findAndHookMethod(className, lpparam.classLoader, methodName, *types)
        } catch (e: Exception) {
            XposedBridge.log("[$LOG] hook $className#$methodName failed: ${e.message}")
        }
    }

    private fun safeResName(view: View): String? = try {
        view.resources.getResourceEntryName(view.id)
    } catch (_: Exception) { null }

    private fun reflectListener(view: View): String? {
        return try {
            var cls: Class<*>? = View::class.java
            while (cls != null) {
                try {
                    val f = cls.getDeclaredField("mListenerInfo")
                    f.isAccessible = true
                    val info = f.get(view) ?: return null
                    val f2 = info.javaClass.getDeclaredField("mOnClickListener")
                    f2.isAccessible = true
                    return f2.get(info)?.javaClass?.name
                } catch (_: NoSuchFieldException) { cls = cls?.superclass }
            }
            null
        } catch (_: Exception) { null }
    }

    private fun String.containsAny(vararg parts: String) =
        parts.any { this.contains(it, ignoreCase = true) }

    // ── Nav bar injection (unchanged from stable build) ───────────────────────

    private fun executeUIHeist(activity: Activity, stockBar: ViewGroup) {
        try {
            val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
            if (contentView.findViewWithTag<View>("HyperFloatingBar") != null) return

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
                } catch (_: Exception) { 0 }
                val extraPadding = (56 * activity.resources.displayMetrics.density).toInt()

                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity      = Gravity.BOTTOM
                    bottomMargin = navInsets + extraPadding
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
                        stockBar         = stockBar,
                        externalTabState = tabState,
                        isVisible        = isBarVisible,
                    )
                }
            }
            contentView.addView(composeView)
            log("Floating bar injected")

        } catch (e: Exception) {
            log("executeUIHeist failed: ${e.message}")
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
