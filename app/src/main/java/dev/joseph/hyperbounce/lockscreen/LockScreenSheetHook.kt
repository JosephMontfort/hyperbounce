package dev.joseph.hyperbounce.lockscreen

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.max
import android.widget.FrameLayout
import dev.joseph.hyperbounce.lockscreen.glass.LockScreenGlassSheetView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Feature 1: Lock Screen Liquid Glass Sheet Hook.
 * Injects a real-time AGSL Liquid Glass Sheet with 3-edge true refraction & chromatic aberration
 * strictly on the Lock Screen, hidden in Unlocked Notification Shade and Control Center.
 */
object LockScreenSheetHook {

    private const val TAG = "HyperBounce_LockSheet"

    private val mainHandler = Handler(Looper.getMainLooper())

    private var kpvcRootViewField: Field? = null
    private var kpvcNcField: Field? = null
    private var kpvcNsslField: Field? = null
    private var kpvcNcpField: Field? = null
    private var kpvcBottomAreaField: Field? = null
    private var bottomAreaViewField: Field? = null
    private var kpvcStatusBarStateField: Field? = null
    private var kpvcKeyguardStateControllerField: Field? = null
    private var kscIsShowingMethod: Method? = null

    private val bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
    @Volatile var isKeyguardShowing: Boolean = true

    private var cachedLockRootViewRef: WeakReference<ViewGroup>? = null
    private var cachedLockGlassSheetRef: WeakReference<LockScreenGlassSheetView>? = null
    private var cachedLockNotifViewRef: WeakReference<View>? = null
    private var cachedLockPillRef: WeakReference<View>? = null
    private var cachedBottomAreaViewRef: WeakReference<ViewGroup>? = null

    private fun getRootView(kpvc: Any): ViewGroup? {
        val cached = cachedLockRootViewRef?.get()
        if (cached != null) return cached
        return try {
            if (kpvcRootViewField == null) {
                kpvcRootViewField = kpvc.javaClass.getDeclaredField("keyguardRootView").apply { isAccessible = true }
            }
            val v = kpvcRootViewField?.get(kpvc) as? ViewGroup
            if (v != null) {
                cachedLockRootViewRef = WeakReference(v)
            }
            v
        } catch (_: Throwable) { null }
    }

    private fun getNotifView(kpvc: Any): View? {
        val cached = cachedLockNotifViewRef?.get()
        if (cached != null) return cached
        var notifView: View? = null
        try {
            if (kpvcNcField == null) {
                kpvcNcField = kpvc.javaClass.getDeclaredField("notificationContainer").apply { isAccessible = true }
            }
            notifView = kpvcNcField?.get(kpvc) as? View
        } catch (_: Throwable) {}

        if (notifView == null) {
            try {
                if (kpvcNsslField == null) {
                    kpvcNsslField = kpvc.javaClass.getDeclaredField("notificationStackScrollLayout").apply { isAccessible = true }
                }
                notifView = kpvcNsslField?.get(kpvc) as? View
            } catch (_: Throwable) {}
        }

        if (notifView == null) {
            try {
                if (kpvcNcpField == null) {
                    kpvcNcpField = kpvc.javaClass.getDeclaredField("notificationContainerParent").apply { isAccessible = true }
                }
                notifView = kpvcNcpField?.get(kpvc) as? View
            } catch (_: Throwable) {}
        }
        if (notifView != null) {
            cachedLockNotifViewRef = WeakReference(notifView)
        }
        return notifView
    }

    private fun getBottomAreaView(kpvc: Any): ViewGroup? {
        val cached = cachedBottomAreaViewRef?.get()
        if (cached != null) return cached
        return try {
            if (kpvcBottomAreaField == null) {
                kpvcBottomAreaField = kpvc.javaClass.getDeclaredField("keyguardBottomAreaInjector").apply { isAccessible = true }
            }
            val injector = kpvcBottomAreaField?.get(kpvc) ?: return null
            if (bottomAreaViewField == null) {
                bottomAreaViewField = injector.javaClass.getDeclaredField("mKeyguardBottomAreaView").apply { isAccessible = true }
            }
            val bv = bottomAreaViewField?.get(injector) as? ViewGroup
            if (bv != null) {
                cachedBottomAreaViewRef = WeakReference(bv)
            }
            bv
        } catch (_: Throwable) { null }
    }

    @Volatile
    var lockscreenFlingVelocity: Float = 0.0f
    private var lastExpansionFraction: Float = 1.0f
    private var lastExpansionTime: Long = 0L
    private var lockscreenVelocityTracker: VelocityTracker? = null

    private fun handleLockscreenTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lockscreenVelocityTracker?.recycle()
                lockscreenVelocityTracker = VelocityTracker.obtain()
                lockscreenVelocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> {
                lockscreenVelocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                try {
                    lockscreenVelocityTracker?.addMovement(event)
                    lockscreenVelocityTracker?.computeCurrentVelocity(1000)
                    val yVel = abs(lockscreenVelocityTracker?.yVelocity ?: 0.0f)
                    if (yVel > 50.0f) {
                        lockscreenFlingVelocity = yVel
                        Log.d(TAG, "Captured lockscreen touch fling velocity: $lockscreenFlingVelocity")
                    }
                } catch (_: Throwable) {} finally {
                    lockscreenVelocityTracker?.recycle()
                    lockscreenVelocityTracker = null
                }
            }
        }
    }

    fun init(module: XposedModule, classLoader: ClassLoader) {
        // 0. Hook KeyguardStateControllerImpl to track keyguard showing state with 0 reflection overhead
        try {
            val kscClass = Class.forName("com.android.systemui.statusbar.policy.KeyguardStateControllerImpl", false, classLoader)
            for (m: Method in kscClass.declaredMethods) {
                if (m.name == "notifyKeyguardState" && m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Boolean::class.javaPrimitiveType &&
                    m.parameterTypes[1] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val showing = args[0] as Boolean
                                val occluded = args[1] as Boolean
                                isKeyguardShowing = showing && !occluded
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                    break
                }
            }
        } catch (_: Throwable) {}

        // 1. Hook KeyguardPanelViewController (HyperOS Lockscreen Controller)
        try {
            val kpvcClass = Class.forName("com.android.keyguard.panel.KeyguardPanelViewController", false, classLoader)
            for (m: Method in kpvcClass.declaredMethods) {
                // Hook updateKeyguardElementsExpansionInternal(float)
                if (m.name.startsWith("updateKeyguardElementsExpansionInternal") &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val f = (args[0] as Number).toFloat()
                                val kpvc = chain.thisObject ?: return result

                                val rootView = getRootView(kpvc)
                                val finalNotifView = getNotifView(kpvc)

                                val now = SystemClock.uptimeMillis()
                                val dt = now - lastExpansionTime
                                if (lastExpansionTime > 0L && dt in 2..250) {
                                    val df = abs(f - lastExpansionFraction)
                                    var viewH = rootView?.height?.toFloat() ?: 2400f
                                    if (viewH <= 0f) viewH = 2400f
                                    val instVel = (df * viewH) / (dt / 1000.0f)
                                    if (instVel > 50.0f) {
                                        lockscreenFlingVelocity = lockscreenFlingVelocity * 0.3f + instVel * 0.7f
                                    }
                                }
                                lastExpansionFraction = f
                                lastExpansionTime = now

                                if (rootView != null) {
                                    val fraction = f.coerceIn(0.0f, 1.0f)
                                    val cfg = LockScreenConfigHolder.sheet
                                    val isActive = cfg.enabled && (isLockScreenActive(kpvc) || (fraction > 0.01f && fraction < 1.0f))
                                    if (!isActive) {
                                        val existingSheet = cachedLockGlassSheetRef?.get() ?: rootView.findViewWithTag<View>("hyperbounce_lock_glass_sheet")
                                        existingSheet?.visibility = View.GONE
                                        return result
                                    }

                                    var viewHeight = rootView.height.toFloat()
                                    if (viewHeight == 0f) viewHeight = 2400f
                                    val upwardOffset = (1.0f - fraction) * -viewHeight

                                    // 1. Move root lockscreen layer upward smoothly (120Hz native performance)
                                    rootView.translationY = upwardOffset
                                    rootView.scaleX = 1.0f
                                    rootView.scaleY = 1.0f

                                    // 2. Move notification container upward in sync
                                    if (finalNotifView != null) {
                                        finalNotifView.translationY = upwardOffset
                                        finalNotifView.scaleX = 1.0f
                                        finalNotifView.scaleY = 1.0f
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "KPVC expansion hook error", t)
                            }
                            return result
                        }
                    })
                }

                // Hook onViewAttachedToWindow, updateKeyguardElementsVisibility, and updateVisibility to ensure state synchronization
                if (m.name == "onViewAttachedToWindow" ||
                    m.name == "updateKeyguardElementsVisibility" ||
                    m.name == "updateVisibility"
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val kpvc = chain.thisObject
                                if (kpvc != null) {
                                    val cfg = LockScreenConfigHolder.sheet
                                    if (Looper.myLooper() == Looper.getMainLooper()) {
                                        ensureInjected(kpvc)
                                        ensurePillInjected(kpvc, cfg)
                                    } else {
                                        mainHandler.post {
                                            ensureInjected(kpvc)
                                            ensurePillInjected(kpvc, cfg)
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                }

                // Hook doScale / doScale$1 to disable shrinking towards center
                if (m.name.startsWith("doScale") &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType &&
                    View::class.java.isAssignableFrom(m.parameterTypes[1])
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val v = args[1] as? View
                                if (v != null) {
                                    v.scaleX = 1.0f
                                    v.scaleY = 1.0f
                                }
                            } catch (_: Throwable) {}
                            return null
                        }
                    })
                }

                // Hook startUnlockKeyguardDismissAnim for smooth unlock dismissal upward
                if (m.name == "startUnlockKeyguardDismissAnim") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val kpvc = chain.thisObject
                                val rootView = if (kpvc != null) getRootView(kpvc) else null
                                val finalNotifView = if (kpvc != null) getNotifView(kpvc) else null

                                if (rootView != null) {
                                    val animUI = Runnable {
                                        var viewHeight = rootView.height.toFloat()
                                        if (viewHeight == 0f) viewHeight = 2400f
                                        rootView.animate()
                                            .translationY(-viewHeight)
                                            .alpha(0.0f)
                                            .setDuration(220)
                                            .setInterpolator(DecelerateInterpolator(1.5f))
                                            .start()
                                        finalNotifView?.animate()
                                            ?.translationY(-viewHeight)
                                            ?.alpha(0.0f)
                                            ?.setDuration(220)
                                            ?.setInterpolator(DecelerateInterpolator(1.5f))
                                            ?.start()
                                    }
                                    if (Looper.myLooper() == Looper.getMainLooper()) {
                                        animUI.run()
                                    } else {
                                        mainHandler.post(animUI)
                                    }

                                    val vel = max(
                                        lockscreenFlingVelocity,
                                        dev.joseph.hyperbounce.notification.NotificationSheetHook.latestFlingVelocity
                                    )
                                    val context = rootView.context
                                    bgExecutor.execute {
                                        try {
                                            listOf("com.miui.home", "com.mi.android.globallauncher").forEach { pkg ->
                                                try {
                                                    val intent = Intent("dev.joseph.hyperbounce.ACTION_TRIGGER_UNLOCK_ANIMATION").apply {
                                                        if (vel > 0.0f) {
                                                            putExtra("EXTRA_VELOCITY", vel)
                                                        }
                                                        setPackage(pkg)
                                                        addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                                                    }
                                                    context.sendBroadcast(intent)
                                                } catch (_: Throwable) {}
                                            }
                                            Log.d(TAG, "Dispatched ACTION_TRIGGER_UNLOCK_ANIMATION on keyguard dismiss (vel=$vel)")
                                        } catch (t: Throwable) {
                                            Log.e(TAG, "Failed to broadcast unlock on keyguard dismiss", t)
                                        }
                                    }
                                }
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "KPVC class hook error", t)
        }

        // 2. Hook KeyguardRootView & HyperOSKeyguardRootView onLayout & dispatchTouchEvent
        val rootViewClasses = arrayOf(
            "com.android.keyguard.widget.HyperOSKeyguardRootView",
            "com.android.systemui.keyguard.ui.view.KeyguardRootView"
        )
        for (className in rootViewClasses) {
            try {
                val clazz = Class.forName(className, false, classLoader)
                for (m: Method in clazz.declaredMethods) {
                    if (m.name == "onLayout") {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val result = chain.proceed()
                                try {
                                    val v = chain.thisObject as? ViewGroup ?: return result
                                    val sheet = v.findViewWithTag<View>("hyperbounce_lock_glass_sheet")
                                    if (sheet != null && v.width > 0 && v.height > 0) {
                                        sheet.layout(0, 0, v.width, v.height)
                                    }
                                } catch (_: Throwable) {}
                                return result
                            }
                        })
                    } else if (m.name == "dispatchTouchEvent" && m.parameterTypes.size == 1 &&
                        m.parameterTypes[0] == MotionEvent::class.java
                    ) {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val rawArgs = chain.args
                                val event = (if (rawArgs is List<*>) rawArgs[0] else (rawArgs as Array<*>)[0]) as? MotionEvent
                                if (event != null) {
                                    handleLockscreenTouchEvent(event)
                                }
                                return chain.proceed()
                            }
                        })
                    }
                }
            } catch (_: Throwable) {}
        }

        // 3. Disable clock scaling flag in KeyguardClockContainer
        try {
            val clockContainerClass = Class.forName("com.android.keyguard.clock.KeyguardClockContainer", false, classLoader)
            for (m: Method in clockContainerClass.declaredMethods) {
                if (m.name == "updateMagazineView" || m.name == "onFinishInflate") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val lockCfg = LockScreenConfigHolder.sheet
                                if (!lockCfg.enabled) {
                                    return result
                                }
                                val container = chain.thisObject
                                val canScaleField: Field = container.javaClass.getDeclaredField("mClockCanScale")
                                canScaleField.isAccessible = true
                                canScaleField.setBoolean(container, false)
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                }
            }
        } catch (_: Throwable) {}
    }

    private fun isLockScreenActive(kpvc: Any): Boolean {
        if (!isKeyguardShowing) return false
        return try {
            if (kpvcStatusBarStateField == null) {
                kpvcStatusBarStateField = kpvc.javaClass.getDeclaredField("statusBarState").apply { isAccessible = true }
            }
            val state = kpvcStatusBarStateField?.getInt(kpvc) ?: 1
            // 1 is StatusBarState.KEYGUARD
            state == 1
        } catch (_: Throwable) {
            true
        }
    }

    private fun ensurePillInjected(kpvc: Any, cfg: LockScreenSheetConfig) {
        if (cachedLockPillRef?.get() != null) return
        try {
            val bottomAreaView = getBottomAreaView(kpvc) ?: return
            var pill = bottomAreaView.findViewWithTag<View>("hyperbounce_home_pill")
            if (pill == null) {
                val context = bottomAreaView.context
                val density = context.resources.displayMetrics.density
                val pillW = (cfg.pillWidth.coerceAtLeast(40f) * density).toInt()
                val pillH = (cfg.pillHeight.coerceAtLeast(2f) * density).toInt()
                val radius = pillH / 2
                val bMargin = (cfg.pillBottomMargin * density).toInt()

                pill = View(context).apply {
                    id = View.generateViewId()
                    tag = "hyperbounce_home_pill"

                    val pillDrawable = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(0xF0FFFFFF.toInt())
                        cornerRadius = radius.toFloat()
                    }
                    background = pillDrawable
                    elevation = 8 * density
                }

                val flp = FrameLayout.LayoutParams(
                    pillW,
                    pillH,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply {
                    bottomMargin = bMargin
                }
                bottomAreaView.addView(pill, flp)
            }
            cachedLockPillRef = WeakReference(pill)
        } catch (t: Throwable) {
            Log.e(TAG, "Pill injection error", t)
        }
    }

    private fun ensureInjected(kpvc: Any, cachedRoot: ViewGroup? = null) {
        try {
            val rootView = cachedRoot ?: getRootView(kpvc) ?: return
            val cfg = LockScreenConfigHolder.sheet
            val active = cfg.enabled && isLockScreenActive(kpvc)

            var glassSheet = cachedLockGlassSheetRef?.get()
            if (glassSheet == null || glassSheet.parent != rootView) {
                glassSheet = rootView.findViewWithTag<LockScreenGlassSheetView>("hyperbounce_lock_glass_sheet")
            }

            if (!active) {
                glassSheet?.visibility = View.GONE
                return
            }

            if (glassSheet == null) {
                val context = rootView.context
                glassSheet = LockScreenGlassSheetView(context).apply {
                    id = View.generateViewId()
                    tag = "hyperbounce_lock_glass_sheet"
                }

                var added = false
                try {
                    val clpClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams", false, rootView.context.classLoader)
                    val clpCons: Constructor<*> = clpClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    val clp = clpCons.newInstance(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) as ViewGroup.MarginLayoutParams
                    clpClass.getField("topToTop").set(clp, 0)
                    clpClass.getField("bottomToBottom").set(clp, 0)
                    clpClass.getField("startToStart").set(clp, 0)
                    clpClass.getField("endToEnd").set(clp, 0)
                    rootView.addView(glassSheet, 0, clp)
                    added = true
                } catch (_: Throwable) {}

                if (!added) {
                    try {
                        val flp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        rootView.addView(glassSheet, 0, flp)
                    } catch (_: Throwable) {}
                }
            }

            cachedLockGlassSheetRef = WeakReference(glassSheet)
            if (glassSheet.visibility != View.VISIBLE) {
                glassSheet.visibility = View.VISIBLE
            }
        } catch (_: Throwable) {}
    }
}
