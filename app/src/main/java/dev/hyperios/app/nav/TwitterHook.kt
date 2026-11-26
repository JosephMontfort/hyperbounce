package dev.hyperios.app.nav

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import dev.hyperios.app.FakeLifecycleOwner
import dev.hyperios.app.prefs.HyperBouncePrefs
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

/**
 * Xposed Hook for X (formerly Twitter) (com.twitter.android).
 *
 * Responsibilities:
 * 1. Inject HyperBounce's floating Compose LiquidNavigationBar (TwitterNavBarHost)
 *    into MainActivity's android.R.id.content, guaranteeing permanent scroll immunity.
 * 2. Suppress stock bottom navigation bar via `com.x.ui.common.p3.a` (forcing parameter 6 z2 = true)
 *    and `com.x.main.k0.b` (canceling stock item composition).
 * 3. Intercept tab controller (`com.x.main.k`), current tab enum (`com.x.navigation.vd`),
 *    and tab switcher lambda in `com.x.main.k0.i`.
 * 4. Extract badge counts and unread dots from `LandingBadgesState` (`w0.d`).
 * 5. Dispatch zero-latency tab switches and trigger native scroll-to-top / pull-to-refresh on re-clicks.
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
    var tabSwitchFunction: ((Any) -> Any?)? = null

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

        module.log(Log.INFO, TAG, "Initializing X (Twitter) Floating Liquid Navigation Bar Hook")

        hookMainActivity(param.classLoader)
        hookStockBarSuppression(param.classLoader)
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

                // Hook onPostCreate(Bundle)
                try {
                    val onPostCreateMethod = actClass.declaredMethods.find {
                        it.name == "onPostCreate" &&
                                it.parameterTypes.size == 1 &&
                                Bundle::class.java.isAssignableFrom(it.parameterTypes[0])
                    } ?: actClass.getMethod("onPostCreate", Bundle::class.java)

                    module.hook(onPostCreateMethod).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? Activity)?.let { act ->
                            attachNavBar(act)
                        }
                        result
                    }
                    Log.i(TAG, "Hooked onPostCreate on $activityName")
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to hook onPostCreate on $activityName: ${t.message}")
                }

                // Hook onResume()
                try {
                    val onResumeMethod = actClass.getMethod("onResume")
                    module.hook(onResumeMethod).intercept { chain ->
                        val result = chain.proceed()
                        (chain.thisObject as? Activity)?.let { act ->
                            attachNavBar(act)
                        }
                        result
                    }
                    Log.i(TAG, "Hooked onResume on $activityName")
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to hook onResume on $activityName: ${t.message}")
                }
            } catch (_: ClassNotFoundException) {
                // Ignore alternative activity names if absent
            } catch (t: Throwable) {
                Log.e(TAG, "Error hooking activity $activityName", t)
            }
        }
    }

    private fun attachNavBar(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        activity.runOnUiThread {
            try {
                val content = activity.findViewById<ViewGroup>(android.R.id.content) ?: return@runOnUiThread
                if (content.findViewWithTag<View>(TAG_OVERLAY) != null ||
                    content.findViewById<View>(OVERLAY_TAG_ID) != null
                ) {
                    return@runOnUiThread
                }

                val decor = activity.window?.decorView
                val fallbackOwner by lazy {
                    FakeLifecycleOwner().apply {
                        registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_CREATE)
                        registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_START)
                        registry.handleLifecycleEvent(androidx.lifecycle.Lifecycle.Event.ON_RESUME)
                    }
                }
                val lifecycleOwner = (activity as? LifecycleOwner) ?: fallbackOwner

                if (content.findViewTreeLifecycleOwner() == null) {
                    decor?.findViewTreeLifecycleOwner()?.let { content.setViewTreeLifecycleOwner(it) }
                        ?: content.setViewTreeLifecycleOwner(lifecycleOwner)
                }
                if (content.findViewTreeSavedStateRegistryOwner() == null) {
                    decor?.findViewTreeSavedStateRegistryOwner()?.let { content.setViewTreeSavedStateRegistryOwner(it) }
                        ?: content.setViewTreeSavedStateRegistryOwner((activity as? SavedStateRegistryOwner) ?: (fallbackOwner as SavedStateRegistryOwner))
                }
                if (content.findViewTreeViewModelStoreOwner() == null) {
                    decor?.findViewTreeViewModelStoreOwner()?.let { content.setViewTreeViewModelStoreOwner(it) }
                        ?: content.setViewTreeViewModelStoreOwner((activity as? ViewModelStoreOwner) ?: (fallbackOwner as ViewModelStoreOwner))
                }

                val host = TwitterNavBarHost(
                    context = activity,
                    prefs = prefs,
                    backdropSource = content
                ).apply {
                    tag = TAG_OVERLAY
                    id = OVERLAY_TAG_ID
                    clipChildren = false
                    clipToPadding = false
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                }

                content.addView(host)
                Log.i(TAG, "TwitterNavBarHost successfully attached to android.R.id.content")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to attach TwitterNavBarHost", t)
            }
        }
    }

    private fun hookStockBarSuppression(cl: ClassLoader) {
        // 1. Primary hook: com.x.ui.common.p3.a(...) -> force param 6 (z2) to true
        try {
            val p3Class = cl.loadClass("com.x.ui.common.p3")
            val p3Methods = p3Class.declaredMethods.filter { it.name == "a" }
            for (m in p3Methods) {
                module.hook(m).intercept { chain ->
                    if (chain.args.size >= 7 && chain.args[6] is Boolean) {
                        chain.args[6] = true
                    }
                    chain.proceed()
                }
                Log.i(TAG, "Hooked com.x.ui.common.p3.a for stock bottom bar collapse")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.ui.common.p3.a", t)
        }

        // 2. Secondary hook: com.x.main.k0.b(...) -> extract state and suppress item composition
        try {
            val k0Class = cl.loadClass("com.x.main.k0")
            val bMethods = k0Class.declaredMethods.filter { it.name == "b" && it.parameterTypes.size >= 8 }
            for (m in bMethods) {
                module.hook(m).intercept { chain ->
                    try {
                        if (chain.args.size >= 5) {
                            extractBadges(chain.args[1])
                            val currentVd = chain.args[3]
                            if (currentVd is Enum<*>) {
                                currentTabIndexState.intValue = currentVd.ordinal.coerceIn(0, 4)
                            }
                            val fn1 = chain.args[4] as? ((Any) -> Any?)
                            if (fn1 != null) {
                                tabSwitchFunction = fn1
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in k0.b hook", t)
                    }
                    // Suppress stock navigation bar item emission
                    null
                }
                Log.i(TAG, "Hooked com.x.main.k0.b for item suppression & state capture")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.main.k0.b", t)
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
                            val fn1 = chain.args[4] as? ((Any) -> Any?)
                            val currentVd = chain.args[7]
                            val controller = chain.args[8]

                            if (fn1 != null) tabSwitchFunction = fn1
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
                Log.i(TAG, "Hooked com.x.main.k0.i for navigation controller interception")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook com.x.main.k0.i", t)
        }
    }

    private fun pollBadgesFromController(controller: Any) {
        try {
            val uMethod = controller.javaClass.methods.find { it.name == "u" && it.parameterTypes.isEmpty() }
            if (uMethod != null) {
                val stateFlow = uMethod.invoke(controller)
                if (stateFlow != null) {
                    val getValueMethod = stateFlow.javaClass.methods.find { it.name == "getValue" && it.parameterTypes.isEmpty() }
                    if (getValueMethod != null) {
                        val w0 = getValueMethod.invoke(stateFlow)
                        extractBadges(w0)
                    }
                }
            }
        } catch (_: Throwable) {
            // Ignore reflection issues during fast recompositions
        }
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

    fun dispatchTabClick(index: Int) {
        val cl = classLoader ?: return
        try {
            val vdClass = cl.loadClass("com.x.navigation.vd")
            val enums = vdClass.enumConstants as? Array<Enum<*>> ?: return
            val targetVd = enums.getOrNull(index.coerceIn(0, 4)) ?: return

            // Method 1: Lambda invocation (zero-latency, preserves re-click scroll-to-top)
            val fn = tabSwitchFunction
            if (fn != null) {
                fn.invoke(targetVd)
            } else {
                // Method 2: Controller invocation fallback
                val controller = activeTabController
                if (controller != null) {
                    val nMethod = controller.javaClass.methods.find {
                        it.name == "n" && it.parameterTypes.size == 2 && it.parameterTypes[0] == vdClass
                    }
                    nMethod?.invoke(controller, targetVd, null)
                }
            }
            currentTabIndexState.intValue = index
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to dispatch tab click to $index", t)
        }
    }
}
