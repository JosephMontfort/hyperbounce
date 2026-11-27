package dev.hyperios.app.nav

import android.app.Activity
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Region
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hyperios.app.FakeLifecycleOwner
import dev.hyperios.app.prefs.HyperBouncePrefs
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import kotlin.math.roundToInt

/**
 * Production-Grade Xposed Hook for X (formerly Twitter) (com.twitter.android).
 *
 * Implements:
 * 1. Multi-layer universal stock navigation bar suppression (Root k0.j, Pipeline A glass.c, Pipeline B p3.a).
 * 2. Permanent scroll immunity via DecorView injection.
 * 3. IME keyboard dodging.
 * 4. Zero-latency tab switching with dynamic reflection and ClassLoader-safe Function0 Proxy.
 * 5. Real-time badge and unread status synchronization.
 */
object TwitterHook {
    private const val TAG = "TwitterHook"
    private const val TAG_OVERLAY = "HyperFloatingBar_Twitter"
    private const val OVERLAY_TAG_ID = 0x7F0B9920

    lateinit var module: XposedModule
    lateinit var prefs: HyperBouncePrefs
    var classLoader: ClassLoader? = null

    val currentTabIndexState = mutableIntStateOf(0)
    val badgeCountsState = mutableStateOf<List<String?>>(listOf(null, null, null, null, null))
    val badgeDotsState = mutableStateOf<List<Boolean>>(listOf(false, false, false, false, false))

    @Volatile
    var activeTabController: Any? = null

    @Volatile
    var rawTabSwitchFunction: Any? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var cachedFunction0Proxy: Any? = null

    @Volatile
    private var isAttaching = false

    @Volatile
    private var activeFloatingBar: View? = null

    private var insetsListenerProxy: Any? = null

    @JvmStatic
    fun init(
        module: XposedModule,
        param: XposedModuleInterface.PackageReadyParam,
        prefs: HyperBouncePrefs
    ) {
        this.module = module
        this.prefs = prefs
        this.classLoader = param.classLoader

        val enabled = prefs.appEnabled("twitter")
        Log.i(TAG, "TwitterHook.init: enabled=$enabled for ${param.packageName}")
        if (!enabled) {
            module.log(Log.INFO, TAG, "Twitter hook disabled in preferences, skipping injection")
            return
        }

        module.log(Log.INFO, TAG, "Initializing Redesigned Twitter Hook (Dual-Pipeline Neutralization & Proxy Tab Engine)")

        hookMainActivity(param.classLoader)
        hookMultiLayerSuppression(param.classLoader)
        hookNavigationAndBadges(param.classLoader)
    }

    private fun hookMainActivity(cl: ClassLoader) {
        val targetActivities = listOf(
            "com.x.android.main.MainActivity",
            "com.twitter.app.main.MainActivity"
        )

        for (activityName in targetActivities) {
            try {
                val actClass = cl.loadClass(activityName)

                // Hook onStart()
                try {
                    val onStartMethod = actClass.declaredMethods.find {
                        it.name == "onStart" && it.parameterTypes.isEmpty()
                    } ?: actClass.getMethod("onStart")

                    module.hook(onStartMethod).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? Activity)?.let { attachNavBar(it) }
                        result
                    }
                    Log.i(TAG, "Hooked onStart on $activityName")
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to hook onStart on $activityName: ${t.message}")
                }

                // Hook onResume()
                try {
                    val onResumeMethod = actClass.getMethod("onResume")
                    module.hook(onResumeMethod).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? Activity)?.let { attachNavBar(it) }
                        result
                    }
                    Log.i(TAG, "Hooked onResume on $activityName")
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to hook onResume on $activityName: ${t.message}")
                }

                // Hook onDestroy()
                try {
                    val onDestroyMethod = actClass.declaredMethods.find {
                        it.name == "onDestroy" && it.parameterTypes.isEmpty()
                    } ?: actClass.getMethod("onDestroy")

                    module.hook(onDestroyMethod).intercept { chain ->
                        (chain.thisObject as? Activity)?.let { detachNavBar(it) }
                        chain.proceed()
                    }
                    Log.i(TAG, "Hooked onDestroy on $activityName")
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to hook onDestroy on $activityName: ${t.message}")
                }
            } catch (_: ClassNotFoundException) {
            } catch (t: Throwable) {
                Log.e(TAG, "Error hooking activity $activityName", t)
            }
        }
    }

    /**
     * Uses reflection to register an OnComputeInternalInsetsListener on the window's ViewTreeObserver.
     * Sets mTouchableInsets = TOUCHABLE_INSETS_REGION (3) and confines touch dispatch to the floating pill.
     */
    private fun applyTouchableInsets(view: View, pillBoundsSupplier: () -> Rect?) {
        try {
            val vto = view.viewTreeObserver
            val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
            val infoClass = Class.forName("android.view.ViewTreeObserver\$InternalInsetsInfo")
            val setTouchableInsetsMethod = infoClass.getMethod("setTouchableInsets", Int::class.javaPrimitiveType)
            val touchableRegionField = infoClass.getField("touchableRegion")
            val TOUCHABLE_INSETS_REGION = 3 // InternalInsetsInfo.TOUCHABLE_INSETS_REGION

            val proxy = Proxy.newProxyInstance(
                listenerClass.classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onComputeInternalInsets" && args != null && args.isNotEmpty()) {
                    val insetsInfo = args[0]
                    val pillRect = pillBoundsSupplier()
                    if (pillRect != null && !pillRect.isEmpty) {
                        setTouchableInsetsMethod.invoke(insetsInfo, TOUCHABLE_INSETS_REGION)
                        val region = touchableRegionField.get(insetsInfo) as Region
                        region.set(pillRect)
                    }
                }
                null
            }

            val addMethod = ViewTreeObserver::class.java.getMethod("addOnComputeInternalInsetsListener", listenerClass)
            addMethod.invoke(vto, proxy)
            insetsListenerProxy = proxy
            Log.i(TAG, "Touch passthrough: OnComputeInternalInsetsListener successfully registered (TOUCHABLE_INSETS_REGION)")
        } catch (t: Throwable) {
            Log.w(TAG, "OnComputeInternalInsetsListener reflection setup: ${t.message}")
        }
    }

    private fun removeTouchableInsets(view: View) {
        val proxy = insetsListenerProxy ?: return
        try {
            val vto = view.viewTreeObserver
            if (vto.isAlive) {
                val listenerClass = Class.forName("android.view.ViewTreeObserver\$OnComputeInternalInsetsListener")
                val removeMethod = ViewTreeObserver::class.java.getMethod("removeOnComputeInternalInsetsListener", listenerClass)
                removeMethod.invoke(vto, proxy)
                Log.i(TAG, "OnComputeInternalInsetsListener removed cleanly")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to remove OnComputeInternalInsetsListener: ${t.message}")
        } finally {
            insetsListenerProxy = null
        }
    }

    private fun detachNavBar(activity: Activity) {
        activity.runOnUiThread {
            try {
                val bar = activeFloatingBar
                if (bar != null) {
                    removeTouchableInsets(bar)
                    if (bar.isAttachedToWindow) {
                        activity.windowManager?.removeViewImmediate(bar)
                        Log.i(TAG, "TwitterNavBarHost cleanly removed from WindowManager")
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error removing bar from WindowManager: ${t.message}")
            } finally {
                activeFloatingBar = null
                isAttaching = false
            }
        }
    }

    private fun attachNavBar(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            if (isAttaching || (activeFloatingBar != null && activeFloatingBar?.isAttachedToWindow == true)) {
                return@runOnUiThread
            }
            isAttaching = true

            try {
                val window = activity.window
                if (window == null) {
                    isAttaching = false
                    return@runOnUiThread
                }
                val decorView = window.decorView as? ViewGroup
                if (decorView == null) {
                    isAttaching = false
                    return@runOnUiThread
                }
                val windowToken = decorView.windowToken

                if (windowToken == null) {
                    // Defer until decorView is attached to the window
                    isAttaching = false
                    decorView.post { attachNavBar(activity) }
                    return@runOnUiThread
                }

                // Clean up any legacy overlay view on DecorView if present
                decorView.findViewWithTag<View>(TAG_OVERLAY)?.let { decorView.removeView(it) }
                decorView.findViewById<View>(OVERLAY_TAG_ID)?.let { decorView.removeView(it) }

                val backdropSource = activity.findViewById<View>(android.R.id.content) ?: decorView

                val fallbackOwner by lazy {
                    FakeLifecycleOwner().apply {
                        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
                        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
                        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
                    }
                }
                val lifecycleOwner = (activity as? androidx.lifecycle.LifecycleOwner) ?: fallbackOwner

                if (decorView.findViewTreeLifecycleOwner() == null) {
                    decorView.setViewTreeLifecycleOwner(lifecycleOwner)
                }
                if (decorView.findViewTreeSavedStateRegistryOwner() == null) {
                    decorView.setViewTreeSavedStateRegistryOwner(
                        activity as? androidx.savedstate.SavedStateRegistryOwner
                            ?: (fallbackOwner as androidx.savedstate.SavedStateRegistryOwner)
                    )
                }
                if (decorView.findViewTreeViewModelStoreOwner() == null) {
                    decorView.setViewTreeViewModelStoreOwner(
                        activity as? androidx.lifecycle.ViewModelStoreOwner
                            ?: (fallbackOwner as androidx.lifecycle.ViewModelStoreOwner)
                    )
                }

                decorView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {}
                    override fun onViewDetachedFromWindow(v: View) {
                        detachNavBar(activity)
                    }
                })

                val density = activity.resources.displayMetrics.density
                val insets = decorView.rootWindowInsets
                val bottomInset = try {
                    insets?.getInsets(WindowInsets.Type.navigationBars())?.bottom
                        ?: insets?.getInsets(WindowInsets.Type.systemBars())?.bottom
                        ?: 0
                } catch (_: Throwable) { 0 }

                val barHeightPx = (110f * density).roundToInt() + bottomInset

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    barHeightPx,
                    WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    token = windowToken
                    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                }

                val composeView = ComposeView(activity).apply {
                    tag = TAG_OVERLAY
                    id = OVERLAY_TAG_ID
                    clipChildren = false
                    clipToPadding = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        barHeightPx
                    )
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
                    setViewTreeLifecycleOwner(lifecycleOwner)
                    setViewTreeSavedStateRegistryOwner(
                        activity as? androidx.savedstate.SavedStateRegistryOwner
                            ?: (fallbackOwner as androidx.savedstate.SavedStateRegistryOwner)
                    )
                    setViewTreeViewModelStoreOwner(
                        activity as? androidx.lifecycle.ViewModelStoreOwner
                            ?: (fallbackOwner as androidx.lifecycle.ViewModelStoreOwner)
                    )

                    setContent {
                        TwitterNavBarHost(
                            backdropSource = backdropSource,
                            hyperBouncePrefs = prefs,
                            window = activity.window
                        )
                    }
                }

                // Pill bounds calculation supplier for OnComputeInternalInsetsListener
                val pillBoundsSupplier: () -> Rect? = {
                    val w = composeView.width
                    val h = composeView.height
                    if (w > 0 && h > 0) {
                        val livePrefs = dev.hyperios.app.prefs.LivePrefs(prefs)
                        val barHeightDp = livePrefs.getFloat("nav_bar_height_twitter", 64f)
                        val bottomMarginDp = livePrefs.getFloat("nav_bottom_margin_twitter", 16f)
                        val tabWidthDp = livePrefs.getFloat("nav_tab_width_twitter", 64f)

                        val screenWidthDp = w / density
                        val tabCount = 5
                        val maxSafeTabWidth = ((screenWidthDp - 16f) / tabCount).coerceAtLeast(40f)
                        val effectiveTabWidth = tabWidthDp.coerceAtMost(maxSafeTabWidth)

                        val pillWidthPx = (effectiveTabWidth * tabCount * density).roundToInt()
                        val pillHeightPx = (barHeightDp * density).roundToInt()
                        val bottomMarginPx = (bottomMarginDp * density).roundToInt()

                        val left = ((w - pillWidthPx) / 2).coerceAtLeast(0)
                        val right = ((w + pillWidthPx) / 2).coerceAtMost(w)
                        val bottom = (h - bottomMarginPx).coerceAtMost(h)
                        val top = (bottom - pillHeightPx).coerceAtLeast(0)

                        // 8dp touch tolerance padding around the pill
                        val pad = (8f * density).roundToInt()
                        Rect(
                            (left - pad).coerceAtLeast(0),
                            (top - pad).coerceAtLeast(0),
                            (right + pad).coerceAtMost(w),
                            (bottom + pad).coerceAtMost(h)
                        )
                    } else null
                }

                // Register dynamic internal insets listener
                applyTouchableInsets(composeView, pillBoundsSupplier)

                // Automatic keyboard (IME) dodging: hide bar when soft keyboard is shown
                composeView.setOnApplyWindowInsetsListener { v, insets ->
                    val imeBottom = try {
                        insets.getInsets(WindowInsets.Type.ime()).bottom
                    } catch (_: Throwable) { 0 }

                    val targetVisibility = if (imeBottom > 200) View.GONE else View.VISIBLE
                    if (v.visibility != targetVisibility) {
                        v.visibility = targetVisibility
                    }
                    insets
                }

                val wm = activity.windowManager ?: return@runOnUiThread
                wm.addView(composeView, params)
                activeFloatingBar = composeView
                Log.i(TAG, "TwitterNavBarHost attached via WindowManager (height=${barHeightPx}px, gravity=BOTTOM) - Touch Passthrough Configured")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to attach TwitterNavBarHost via WindowManager", t)
            } finally {
                isAttaching = false
            }
        }
    }

    private fun hookMultiLayerSuppression(cl: ClassLoader) {
        // LAYER 1: Root Navigation Shell Hook: com.x.main.k0.j(...)
        // Forces param 6 (z4 / isBottomBarHidden) to true
        try {
            val k0Class = cl.loadClass("com.x.main.k0")
            val jMethods = k0Class.declaredMethods.filter { it.name == "j" && it.parameterTypes.size >= 30 }
            for (m in jMethods) {
                module.hook(m).intercept { chain ->
                    val args = chain.args.toTypedArray()
                    if (args.size >= 7 && args[6] is Boolean) {
                        args[6] = true
                        chain.proceed(args)
                    } else {
                        chain.proceed()
                    }
                }
                Log.i(TAG, "Layer 1 Hooked: com.x.main.k0.j (Root Shell Suppression)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.main.k0.j", t)
        }

        // LAYER 2: Pipeline A Hooks: com.x.ui.common.glass.c
        try {
            val glassClass = cl.loadClass("com.x.ui.common.glass.c")

            // Method e: Container - force param 3 (z2 / isBottomBarHidden) = true
            for (m in glassClass.declaredMethods.filter { it.name == "e" && it.parameterTypes.size >= 16 }) {
                module.hook(m).intercept { chain ->
                    val args = chain.args.toTypedArray()
                    if (args.size >= 4 && args[3] is Boolean) {
                        args[3] = true
                        chain.proceed(args)
                    } else {
                        chain.proceed()
                    }
                }
                Log.i(TAG, "Layer 2 Hooked: com.x.ui.common.glass.c.e (Glass Container Suppression)")
            }

            // Method c: Glass Bar Rendering - direct parameter neutralization with group balance
            for (m in glassClass.declaredMethods.filter { it.name == "c" && it.parameterTypes.size >= 16 }) {
                module.hook(m).intercept { chain ->
                    val args = chain.args.toTypedArray()
                    if (args.size >= 5 && args[4] is Boolean) {
                        args[4] = true
                        chain.proceed(args)
                    } else {
                        chain.proceed()
                    }
                }
                Log.i(TAG, "Layer 2 Hooked: com.x.ui.common.glass.c.c (Glass Bar Neutralization)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.ui.common.glass.c", t)
        }

        // LAYER 3: Pipeline B Hook: com.x.ui.common.p3.a
        // Forces param 6 (isBottomBarHidden) = true
        try {
            val p3Class = cl.loadClass("com.x.ui.common.p3")
            for (m in p3Class.declaredMethods.filter { it.name == "a" && it.parameterTypes.size >= 10 }) {
                module.hook(m).intercept { chain ->
                    val args = chain.args.toTypedArray()
                    if (args.size >= 7 && args[6] is Boolean) {
                        args[6] = true
                        chain.proceed(args)
                    } else {
                        chain.proceed()
                    }
                }
                Log.i(TAG, "Layer 3 Hooked: com.x.ui.common.p3.a (Legacy Bottom Bar Suppression)")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.ui.common.p3.a", t)
        }
    }

    private fun hookNavigationAndBadges(cl: ClassLoader) {
        try {
            val k0Class = cl.loadClass("com.x.main.k0")
            val iMethods = k0Class.declaredMethods.filter { it.name == "i" && it.parameterTypes.size >= 10 }
            for (m in iMethods) {
                module.hook(m).intercept { chain ->
                    try {
                        if (chain.args.size >= 9) {
                            val fn1 = chain.args[4]
                            val currentVd = chain.args[7]
                            val controller = chain.args[8]

                            if (fn1 != null) {
                                rawTabSwitchFunction = fn1
                            }
                            if (controller != null) {
                                activeTabController = controller
                                pollBadgesFromController(controller)
                            }
                            if (currentVd is Enum<*>) {
                                currentTabIndexState.intValue = currentVd.ordinal.coerceIn(0, 4)
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in k0.i hook", t)
                    }
                    chain.proceed()
                }
                Log.i(TAG, "Hooked com.x.main.k0.i for Navigation & Badges")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.main.k0.i", t)
        }
    }

    private fun getOrCreateFunction0Proxy(cl: ClassLoader): Any? {
        cachedFunction0Proxy?.let { return it }
        synchronized(this) {
            cachedFunction0Proxy?.let { return it }
            return try {
                val fn0Class = cl.loadClass("kotlin.jvm.functions.Function0")
                val proxy = Proxy.newProxyInstance(
                    cl,
                    arrayOf(fn0Class),
                    InvocationHandler { _, _, _ -> null /* returns kotlin.Unit */ }
                )
                cachedFunction0Proxy = proxy
                proxy
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to create Function0 Proxy", t)
                null
            }
        }
    }

    fun pollBadgesFromController(controller: Any) {
        try {
            val uMethod = controller.javaClass.methods.find { it.name == "u" && it.parameterTypes.isEmpty() } ?: return
            val stateFlow = uMethod.invoke(controller) ?: return
            val getValueMethod = stateFlow.javaClass.methods.find { it.name == "getValue" && it.parameterTypes.isEmpty() } ?: return
            val w0 = getValueMethod.invoke(stateFlow) ?: return
            extractBadges(w0)
        } catch (_: Throwable) {}
    }

    fun extractBadges(w0Var: Any?) {
        if (w0Var == null) return
        try {
            val dField = w0Var.javaClass.declaredFields.find { it.name == "d" }?.apply { isAccessible = true } ?: return
            val map = dField.get(w0Var) as? Map<*, *> ?: return
            val cl = classLoader ?: w0Var.javaClass.classLoader
            val vdClass = cl.loadClass("com.x.navigation.vd")
            val enums = vdClass.enumConstants as? Array<Enum<*>> ?: return

            val counts = mutableListOf<String?>(null, null, null, null, null)
            val dots = mutableListOf(false, false, false, false, false)

            for (i in 0 until 5) {
                val targetEnum = enums.getOrNull(i) ?: continue
                val m1Obj = map[targetEnum] ?: continue
                val aField = m1Obj.javaClass.declaredFields.find { it.name == "a" }?.apply { isAccessible = true }
                val bField = m1Obj.javaClass.declaredFields.find { it.name == "b" }?.apply { isAccessible = true }
                val count = aField?.getInt(m1Obj) ?: 0
                val dot = bField?.getBoolean(m1Obj) ?: false
                counts[i] = if (count > 0) count.toString() else null
                dots[i] = dot && count == 0
            }

            badgeCountsState.value = counts
            badgeDotsState.value = dots
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to extract badges from LandingBadgesState", t)
        }
    }

    /**
     * Dispatches zero-latency tab switches to Twitter's Decompose Router.
     * Guaranteed crash-proof across rapid scrubbing and tab re-clicks.
     */
    fun dispatchTabClick(index: Int) {
        val cl = classLoader ?: return
        val targetIndex = index.coerceIn(0, 4)

        // Optimistic UI state update
        currentTabIndexState.intValue = targetIndex

        mainHandler.post {
            try {
                val vdClass = cl.loadClass("com.x.navigation.vd")
                val enums = vdClass.enumConstants as? Array<Enum<*>> ?: return@post
                val targetVd = enums.getOrNull(targetIndex) ?: return@post

                var dispatched = false

                // Method 1: Lambda invocation via reflective method call (ClassLoader safe)
                val rawFn = rawTabSwitchFunction
                if (rawFn != null) {
                    try {
                        val invokeMethod = rawFn.javaClass.methods.find {
                            it.name == "invoke" && it.parameterTypes.size == 1 &&
                                    (it.parameterTypes[0] == vdClass || it.parameterTypes[0].isAssignableFrom(targetVd.javaClass))
                        } ?: rawFn.javaClass.methods.find {
                            it.name == "invoke" && it.parameterTypes.size == 1
                        }

                        if (invokeMethod != null) {
                            invokeMethod.invoke(rawFn, targetVd)
                            dispatched = true
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Method 1 lambda dispatch failed: ${t.message}")
                    }
                }

                // Method 2: Controller invocation fallback with Proxy Function0
                if (!dispatched) {
                    val controller = activeTabController
                    if (controller != null) {
                        val fn0Class = cl.loadClass("kotlin.jvm.functions.Function0")
                        val nMethod = controller.javaClass.methods.find {
                            it.name == "n" && it.parameterTypes.size == 2 &&
                                    (it.parameterTypes[0] == vdClass || it.parameterTypes[0].isAssignableFrom(targetVd.javaClass)) &&
                                    (it.parameterTypes[1] == fn0Class || it.parameterTypes[1].name == "kotlin.jvm.functions.Function0")
                        }

                        val nonNullCallback = getOrCreateFunction0Proxy(cl)
                        if (nMethod != null && nonNullCallback != null) {
                            nMethod.invoke(controller, targetVd, nonNullCallback)
                            dispatched = true
                        }
                    }
                }

                // Verify active tab in Decompose stack
                val controller = activeTabController
                if (controller != null) {
                    try {
                        val qMethod = controller.javaClass.methods.find { it.name == "q" && it.parameterTypes.isEmpty() }
                        val activeVd = qMethod?.invoke(controller) as? Enum<*>
                        if (activeVd != null) {
                            currentTabIndexState.intValue = activeVd.ordinal.coerceIn(0, 4)
                        }
                    } catch (_: Throwable) {}
                }

            } catch (t: Throwable) {
                Log.e(TAG, "Failed to dispatch tab click to $targetIndex", t)
            }
        }
    }
}
