package dev.hyperios.app.nav

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
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
            } catch (_: ClassNotFoundException) {
            } catch (t: Throwable) {
                Log.e(TAG, "Error hooking activity $activityName", t)
            }
        }
    }

    private fun attachNavBar(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            try {
                val decorView = activity.window?.decorView as? ViewGroup ?: return@runOnUiThread
                if (decorView.findViewWithTag<View>(TAG_OVERLAY) != null ||
                    decorView.findViewById<View>(OVERLAY_TAG_ID) != null
                ) {
                    return@runOnUiThread
                }

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

                val composeView = ComposeView(activity).apply {
                    tag = TAG_OVERLAY
                    id = OVERLAY_TAG_ID
                    clipChildren = false
                    clipToPadding = false
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
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

                decorView.addView(composeView)
                Log.i(TAG, "TwitterNavBarHost successfully attached to DecorView (Scroll Immune + IME Dodging)")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to attach TwitterNavBarHost to DecorView", t)
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
