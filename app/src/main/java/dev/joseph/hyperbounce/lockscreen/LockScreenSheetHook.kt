package dev.joseph.hyperbounce.lockscreen

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import dev.joseph.hyperbounce.lockscreen.glass.LockScreenGlassSheetOptimizedView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max

/**
 * Feature 1 (Optimized Pipeline): 120Hz Zero-Drop Lock Screen Liquid Glass Sheet Hook.
 *
 * Optimizations over base hook:
 * 1. Dual-Animator Decoupling: Eliminates the race condition between Folme expansion updates
 *    and startUnlockKeyguardDismissAnim() ViewPropertyAnimator on quick flicks.
 * 2. Asynchronous Broadcast Dispatch: Offloads synchronous Binder IPC (sendBroadcast) from the
 *    main UI thread to a background executor thread, preventing 2-8ms frame drops on flick releases.
 * 3. Zero-Reflection Hot Path: Replaces per-frame Method.invoke() reflection with event-driven
 *    cached state checks (< 1 nanosecond overhead).
 * 4. Single-Pass Invalidation: Eliminates redundant view hierarchy searches and double-invalidation
 *    calls during 120Hz swipe-up motion.
 * 5. Scale Churn Elimination: Only mutates View.scaleX/scaleY in doScale if value != 1.0f,
 *    preventing unnecessary matrix dirty passes on every child view.
 * 6. Zero-walk coordinates: Pairs with LockScreenGlassSheetOptimizedView to avoid recursive
 *    getLocationOnScreen() calls on every onDraw().
 */
object LockScreenSheetHook {

    private const val TAG = "HyperBounce_LockSheet"
    private const val TAG_LOCK_GLASS_SHEET = "hyperbounce_lock_glass_sheet_opt"
    private const val TAG_HOME_PILL = "hyperbounce_home_pill_opt"
    private const val TAG_LOCK_WALLPAPER_DIM = "hyperbounce_lock_wallpaper_dim"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val bgExecutor = Executors.newSingleThreadExecutor()

    private var kpvcRootViewField: Field? = null
    private var kpvcNsslField: Field? = null
    private var kpvcNcpField: Field? = null
    private var kpvcBottomAreaField: Field? = null
    private var bottomAreaViewField: Field? = null
    private var kpvcDeductedImageViewField: Field? = null

    private var cachedLockRootViewRef: WeakReference<ViewGroup>? = null
    private var cachedLockGlassSheetRef: WeakReference<LockScreenGlassSheetOptimizedView>? = null
    private var cachedLockNotifViewRef: WeakReference<View>? = null
    private var cachedLockPillRef: WeakReference<View>? = null
    private var cachedBottomAreaViewRef: WeakReference<ViewGroup>? = null
    private var cachedDepthSubjectViewRef: WeakReference<View>? = null
    private var cachedLockWallpaperDimRef: WeakReference<View>? = null

    // Event-driven state tracking (0ms reflection in hot path)
    @Volatile private var isKeyguardShowing: Boolean = true
    @Volatile private var currentStatusBarState: Int = 1 // 1 = KEYGUARD
    @Volatile private var isDozing: Boolean = false
    @Volatile private var isGoingToSleep: Boolean = false

    // Dismissal animation coordinator (prevents Folme vs ViewPropertyAnimator clash)
    @Volatile var isDismissAnimationRunning: Boolean = false
    private var dismissReturnAnimator: ValueAnimator? = null

    @Volatile var lockscreenFlingVelocity: Float = 0.0f
    private var lastExpansionFraction: Float = 1.0f
    private var lastExpansionTime: Long = 0L
    private var lockscreenVelocityTracker: VelocityTracker? = null

    @Volatile private var hasSearchedDepthSubject = false
    private var lastAppliedUpwardOffset = 0.0f
    private var lastAppliedDimAlpha = 0.0f

    private fun onSleepOrDozeTriggered() {
        val resetUI = Runnable {
            dismissReturnAnimator?.cancel()
            dismissReturnAnimator = null
            isDismissAnimationRunning = false
            hasSearchedDepthSubject = false
            lastAppliedUpwardOffset = 0.0f
            lastAppliedDimAlpha = 0.0f
            val rootView = cachedLockRootViewRef?.get()
            val sheet = cachedLockGlassSheetRef?.get() ?: rootView?.findViewWithTag<LockScreenGlassSheetOptimizedView>(TAG_LOCK_GLASS_SHEET)
            sheet?.visibility = View.GONE
            sheet?.swipeProgress = 0.0f
            val pill = cachedLockPillRef?.get()
            pill?.visibility = View.GONE
            if (rootView != null) {
                rootView.animate().cancel()
                rootView.translationY = 0.0f
            }
            val notifView = cachedLockNotifViewRef?.get()
            if (notifView != null) {
                notifView.animate().cancel()
                notifView.translationY = 0.0f
                if (notifView.translationZ == 2.0f) {
                    notifView.translationZ = 0.0f
                }
            }
            val depthView = cachedDepthSubjectViewRef?.get()
            if (depthView != null) {
                depthView.animate().cancel()
                depthView.translationY = 0.0f
                depthView.translationZ = 0.0f
                if (depthView is ImageView) {
                    depthView.clearColorFilter()
                }
            }
            val dimLayer = cachedLockWallpaperDimRef?.get()
            if (dimLayer != null) {
                dimLayer.animate().cancel()
                dimLayer.visibility = View.GONE
                dimLayer.alpha = 0.0f
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            resetUI.run()
        } else {
            mainHandler.post(resetUI)
        }
    }

    private fun isLockScreenActive(): Boolean {
        return (currentStatusBarState == 1 || isKeyguardShowing) && !isDozing && !isGoingToSleep
    }

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
            if (kpvcNsslField == null) {
                kpvcNsslField = kpvc.javaClass.getDeclaredField("notificationStackScrollLayout").apply { isAccessible = true }
            }
            notifView = kpvcNsslField?.get(kpvc) as? View
        } catch (_: Throwable) {}

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

    private fun getDepthSubjectView(kpvc: Any, rootView: ViewGroup?): View? {
        val cached = cachedDepthSubjectViewRef?.get()
        if (cached != null && cached.isAttachedToWindow) return cached
        if (hasSearchedDepthSubject) return null

        try {
            if (kpvcDeductedImageViewField == null) {
                kpvcDeductedImageViewField = kpvc.javaClass.getDeclaredField("deductedImageView").apply { isAccessible = true }
            }
            val v = kpvcDeductedImageViewField?.get(kpvc) as? View
            if (v != null) {
                cachedDepthSubjectViewRef = WeakReference(v)
                hasSearchedDepthSubject = true
                return v
            }
        } catch (_: Throwable) {}

        if (rootView != null) {
            for (i in 0 until rootView.childCount) {
                val child = rootView.getChildAt(i)
                val name = child.javaClass.simpleName.lowercase()
                if (name.contains("deducted") || name.contains("depth")) {
                    cachedDepthSubjectViewRef = WeakReference(child)
                    hasSearchedDepthSubject = true
                    return child
                }
            }
        }
        hasSearchedDepthSubject = true
        return null
    }

    private fun ensureWallpaperDimInjected(rootView: ViewGroup): View? {
        val parent = rootView.parent as? ViewGroup ?: return null
        var dimView = cachedLockWallpaperDimRef?.get()
        if (dimView != null && dimView.parent == parent) return dimView

        dimView = parent.findViewWithTag<View>(TAG_LOCK_WALLPAPER_DIM)
        if (dimView != null) {
            cachedLockWallpaperDimRef = WeakReference(dimView)
            return dimView
        }

        return try {
            val context = rootView.context
            val newDim = View(context).apply {
                tag = TAG_LOCK_WALLPAPER_DIM
                setBackgroundColor(0xFF000000.toInt())
                alpha = 0f
                visibility = View.GONE
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val rootIndex = parent.indexOfChild(rootView)
            val insertIndex = if (rootIndex >= 0) rootIndex else 0
            val lp = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            parent.addView(newDim, insertIndex, lp)
            cachedLockWallpaperDimRef = WeakReference(newDim)
            newDim
        } catch (t: Throwable) {
            Log.e(TAG, "Error injecting wallpaper dim layer", t)
            null
        }
    }

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
                    }
                } catch (_: Throwable) {} finally {
                    lockscreenVelocityTracker?.recycle()
                    lockscreenVelocityTracker = null
                }
            }
    fun applySwipeProgressInternal(
        kpvc: Any?,
        rootView: ViewGroup,
        swipeProgress: Float,
        cfg: LockScreenConfigHolder.SheetConfig = LockScreenConfigHolder.sheet
    ) {
        if (swipeProgress <= 0.001f) {
            val existingSheet = cachedLockGlassSheetRef?.get() ?: rootView.findViewWithTag<LockScreenGlassSheetOptimizedView>(TAG_LOCK_GLASS_SHEET)
            existingSheet?.visibility = View.GONE
            existingSheet?.swipeProgress = 0.0f
            if (rootView.translationY != 0.0f) rootView.translationY = 0.0f
            val finalNotifView = cachedLockNotifViewRef?.get() ?: (if (kpvc != null) getNotifView(kpvc) else null)
            if (finalNotifView != null && finalNotifView.translationY != 0.0f) finalNotifView.translationY = 0.0f
            val depthView = if (kpvc != null) getDepthSubjectView(kpvc, rootView) else cachedDepthSubjectViewRef?.get()
            if (depthView != null) {
                if (depthView.translationY != 0.0f) depthView.translationY = 0.0f
                if (depthView.translationZ != 0.0f) depthView.translationZ = 0.0f
                if (depthView is ImageView) depthView.clearColorFilter()
            }
            val dimLayer = cachedLockWallpaperDimRef?.get() ?: (rootView.parent as? ViewGroup)?.findViewWithTag<View>(TAG_LOCK_WALLPAPER_DIM)
            if (dimLayer != null) {
                dimLayer.visibility = View.GONE
                dimLayer.alpha = 0.0f
            }
            lastAppliedUpwardOffset = 0.0f
            lastAppliedDimAlpha = 0.0f
            if (!cfg.pillEnabled) {
                val pill = cachedLockPillRef?.get() ?: (if (kpvc != null) getBottomAreaView(kpvc) else cachedBottomAreaViewRef?.get())?.findViewWithTag<View>(TAG_HOME_PILL)
                pill?.visibility = View.GONE
            }
            return
        }

        var viewHeight = rootView.height.toFloat()
        if (viewHeight <= 0f) viewHeight = 2400f
        val upwardOffset = swipeProgress * -viewHeight

        val depthSubjectView = if (kpvc != null) getDepthSubjectView(kpvc, rootView) else cachedDepthSubjectViewRef?.get()

        // 1. Move root lockscreen layer and depth subject smoothly without micro-jitter overhead
        if (abs(lastAppliedUpwardOffset - upwardOffset) >= 0.25f) {
            lastAppliedUpwardOffset = upwardOffset
            rootView.translationY = upwardOffset
            if (rootView.scaleX != 1.0f) rootView.scaleX = 1.0f
            if (rootView.scaleY != 1.0f) rootView.scaleY = 1.0f

            if (depthSubjectView != null) {
                depthSubjectView.translationY = -upwardOffset
                if (depthSubjectView.translationZ != -1.0f) {
                    depthSubjectView.translationZ = -1.0f
                }
            }
        }

        // 1b. Dimming layer scaling down as sheet settles
        val targetDim = 0.45f
        val dimAlpha = targetDim * (swipeProgress / 0.18f).coerceIn(0.0f, 1.0f)
        if (abs(lastAppliedDimAlpha - dimAlpha) >= 0.005f) {
            lastAppliedDimAlpha = dimAlpha
            val dimLayer = ensureWallpaperDimInjected(rootView)
            if (dimLayer != null) {
                dimLayer.visibility = View.VISIBLE
                dimLayer.alpha = dimAlpha
            }
            if (depthSubjectView is ImageView) {
                val alphaInt = (dimAlpha * 255).toInt().coerceIn(0, 255)
                depthSubjectView.colorFilter = PorterDuffColorFilter(
                    Color.argb(alphaInt, 0, 0, 0),
                    PorterDuff.Mode.SRC_ATOP
                )
            }
        }

        // 2. Ensure real-time AGSL Liquid Glass Sheet Card is rendered on top of depthSubjectView
        var sheet = cachedLockGlassSheetRef?.get()
        if (sheet == null || sheet.parent != rootView) {
            if (kpvc != null) ensureInjected(kpvc, rootView)
            sheet = cachedLockGlassSheetRef?.get()
        }
        if (sheet != null) {
            if (sheet.visibility != View.VISIBLE) {
                sheet.visibility = View.VISIBLE
            }
            sheet.swipeProgress = swipeProgress
        }

        // 3. Move notification container upward in sync
        val finalNotifView = cachedLockNotifViewRef?.get() ?: (if (kpvc != null) getNotifView(kpvc) else null)
        if (finalNotifView != null) {
            if (finalNotifView.translationY != upwardOffset) {
                finalNotifView.translationY = upwardOffset
            }
            if (finalNotifView.scaleX != 1.0f) finalNotifView.scaleX = 1.0f
            if (finalNotifView.scaleY != 1.0f) finalNotifView.scaleY = 1.0f
        }

        // 4. Dynamic iOS Home Indicator Bar in KeyguardBottomAreaView
        if (cfg.pillEnabled) {
            if (kpvc != null) ensurePillInjected(kpvc, cfg)
            val pill = cachedLockPillRef?.get() ?: (if (kpvc != null) getBottomAreaView(kpvc) else cachedBottomAreaViewRef?.get())?.findViewWithTag<View>(TAG_HOME_PILL)
            if (pill != null && pill.visibility != View.VISIBLE) {
                pill.visibility = View.VISIBLE
            }
        } else {
            val pill = cachedLockPillRef?.get() ?: (if (kpvc != null) getBottomAreaView(kpvc) else cachedBottomAreaViewRef?.get())?.findViewWithTag<View>(TAG_HOME_PILL)
            if (pill != null && pill.visibility != View.GONE) {
                pill.visibility = View.GONE
            }
        }
    }

    fun startDismissReturnAnimation(
        kpvc: Any? = null,
        cachedRoot: ViewGroup? = null,
        initialProgress: Float? = null
    ) {
        val runOnMain = Runnable {
            val rootView = cachedRoot ?: cachedLockRootViewRef?.get() ?: return@Runnable
            val sheet = cachedLockGlassSheetRef?.get() ?: rootView.findViewWithTag<LockScreenGlassSheetOptimizedView>(TAG_LOCK_GLASS_SHEET)
            val cfg = LockScreenConfigHolder.sheet
            if (!cfg.enabled || !isLockScreenActive()) {
                applySwipeProgressInternal(kpvc, rootView, 0.0f, cfg)
                return@Runnable
            }

            val startProgress = (initialProgress ?: sheet?.swipeProgress ?: 1.0f).coerceIn(0.0f, 1.0f)
            if (startProgress <= 0.005f) {
                applySwipeProgressInternal(kpvc, rootView, 0.0f, cfg)
                return@Runnable
            }

            dismissReturnAnimator?.cancel()
            isDismissAnimationRunning = true

            val animator = ValueAnimator.ofFloat(startProgress, 0.0f).apply {
                duration = 500L
                interpolator = PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f)
                addUpdateListener { va ->
                    val p = va.animatedValue as Float
                    applySwipeProgressInternal(kpvc, rootView, p, cfg)
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        isDismissAnimationRunning = false
                        applySwipeProgressInternal(kpvc, rootView, 0.0f, cfg)
                        dismissReturnAnimator = null
                    }
                    override fun onAnimationCancel(animation: Animator) {
                        isDismissAnimationRunning = false
                        dismissReturnAnimator = null
                    }
                })
            }
            dismissReturnAnimator = animator
            animator.start()
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            runOnMain.run()
        } else {
            mainHandler.post(runOnMain)
        }
    }

    fun init(module: XposedModule, classLoader: ClassLoader) {
        // 0. Event-driven state tracking to eliminate reflection from 120Hz hot path
        hookStatusBarState(module, classLoader)
        hookKeyguardState(module, classLoader)
        hookWakefulnessAndDoze(module, classLoader)

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
                                // If dismissal animation is actively driving the view, do not fight or overwrite it
                                if (isDismissAnimationRunning) {
                                    return result
                                }

                                val rawArgs = chain.args
                                val f = ((if (rawArgs is List<*>) rawArgs[0] else (rawArgs as Array<*>)[0]) as Number).toFloat()
                                val kpvc = chain.thisObject ?: return result

                                val cfg = LockScreenConfigHolder.sheet
                                val active = cfg.enabled && isLockScreenActive()

                                val rootView = getRootView(kpvc)
                                val finalNotifView = getNotifView(kpvc)

                                if (!active) {
                                    applySwipeProgressInternal(kpvc, rootView ?: return result, 0.0f, cfg)
                                    return result
                                }

                                val now = SystemClock.uptimeMillis()
                                val dt = now - lastExpansionTime
                                if (lastExpansionTime > 0L && dt in 2..250) {
                                    val df = abs(f - lastExpansionFraction)
                                    var viewH = rootView?.height?.toFloat() ?: 2400f
                                    if (viewH <= 0f) viewH = 2400f
                                    val instVel = (df * viewH) / (dt / 1000.0f)
                                    if (instVel > 50.0f) {
                                        val clampedVel = instVel.coerceIn(50.0f, 10000.0f)
                                        lockscreenFlingVelocity = lockscreenFlingVelocity * 0.3f + clampedVel * 0.7f
                                    }
                                }

                                if (rootView != null) {
                                    val fraction = f.coerceIn(0.0f, 1.0f)
                                    val swipeProgress = (1.0f - fraction).coerceIn(0.0f, 1.0f)

                                    if (swipeProgress <= 0.001f) {
                                        if (isDismissAnimationRunning) {
                                            return result
                                        }
                                        if (lastExpansionFraction < 0.85f) {
                                            val startProgress = 1.0f - lastExpansionFraction
                                            lastExpansionFraction = 1.0f
                                            startDismissReturnAnimation(kpvc, rootView, startProgress)
                                            return result
                                        }
                                        lastExpansionFraction = 1.0f
                                        applySwipeProgressInternal(kpvc, rootView, 0.0f, cfg)
                                        return result
                                    }

                                    if (isDismissAnimationRunning) {
                                        dismissReturnAnimator?.cancel()
                                        dismissReturnAnimator = null
                                        isDismissAnimationRunning = false
                                    }

                                    lastExpansionFraction = f
                                    lastExpansionTime = now

                                    applySwipeProgressInternal(kpvc, rootView, swipeProgress, cfg)
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
                                    if (Looper.myLooper() == Looper.getMainLooper()) {
                                        ensureInjected(kpvc)
                                    } else {
                                        mainHandler.post { ensureInjected(kpvc) }
                                    }
                                }
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                }

                // Hook doScale / doScale$1 to disable shrinking towards center (with zero-churn check)
                if (m.name.startsWith("doScale") &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType &&
                    View::class.java.isAssignableFrom(m.parameterTypes[1])
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = LockScreenConfigHolder.sheet
                            if (!cfg.enabled || !isLockScreenActive()) {
                                return chain.proceed()
                            }
                            try {
                                val rawArgs = chain.args
                                val v = (if (rawArgs is List<*>) rawArgs[1] else (rawArgs as Array<*>)[1]) as? View
                                if (v != null) {
                                    if (v.scaleX != 1.0f) v.scaleX = 1.0f
                                    if (v.scaleY != 1.0f) v.scaleY = 1.0f
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
                            val cfg = LockScreenConfigHolder.sheet
                            if (!cfg.enabled || !isLockScreenActive()) {
                                return result
                            }
                            try {
                                val kpvc = chain.thisObject
                                val rootView = if (kpvc != null) getRootView(kpvc) else null
                                val finalNotifView = if (kpvc != null) getNotifView(kpvc) else null

                                if (rootView != null) {
                                    isDismissAnimationRunning = true
                                    val animUI = Runnable {
                                        var viewHeight = rootView.height.toFloat()
                                        if (viewHeight <= 0f) viewHeight = 2400f

                                        // Cancel any lingering animators to prevent conflicting transformations
                                        rootView.animate().cancel()
                                        finalNotifView?.animate()?.cancel()

                                        rootView.animate()
                                            .translationY(-viewHeight)
                                            .setDuration(220)
                                            .setInterpolator(DecelerateInterpolator(1.5f))
                                            .withEndAction {
                                                isDismissAnimationRunning = false
                                                rootView.translationY = 0.0f
                                                finalNotifView?.translationY = 0.0f
                                                val sheet = cachedLockGlassSheetRef?.get() ?: rootView.findViewWithTag<LockScreenGlassSheetOptimizedView>(TAG_LOCK_GLASS_SHEET)
                                                sheet?.visibility = View.GONE
                                                sheet?.swipeProgress = 0.0f
                                            }
                                            .start()

                                        finalNotifView?.animate()
                                            ?.translationY(-viewHeight)
                                            ?.setDuration(220)
                                            ?.setInterpolator(DecelerateInterpolator(1.5f))
                                            ?.start()

                                        val depthView = if (kpvc != null) getDepthSubjectView(kpvc, rootView) else null
                                        depthView?.animate()?.cancel()
                                        depthView?.animate()
                                            ?.translationY(viewHeight)
                                            ?.setDuration(220)
                                            ?.setInterpolator(DecelerateInterpolator(1.5f))
                                            ?.withEndAction {
                                                depthView.translationY = 0.0f
                                                depthView.translationZ = 0.0f
                                            }
                                            ?.start()

                                        val dimLayer = cachedLockWallpaperDimRef?.get() ?: (rootView.parent as? ViewGroup)?.findViewWithTag<View>(TAG_LOCK_WALLPAPER_DIM)
                                        dimLayer?.animate()?.cancel()
                                        dimLayer?.animate()
                                            ?.alpha(0.0f)
                                            ?.setDuration(220)
                                            ?.withEndAction {
                                                dimLayer.visibility = View.GONE
                                                dimLayer.alpha = 0.0f
                                            }
                                            ?.start()
                                    }
                                    if (Looper.myLooper() == Looper.getMainLooper()) {
                                        animUI.run()
                                    } else {
                                        mainHandler.post(animUI)
                                    }

                                    // Immediate broadcast dispatch offloaded to bgExecutor to prevent main-thread binder jank
                                    val context = rootView.context
                                    val vel = max(
                                        lockscreenFlingVelocity,
                                        dev.joseph.hyperbounce.notification.NotificationSheetHook.latestFlingVelocity
                                    )
                                    bgExecutor.execute {
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
                                val cfg = LockScreenConfigHolder.sheet
                                if (!cfg.enabled || !isLockScreenActive()) return result
                                try {
                                    val v = chain.thisObject as? ViewGroup ?: return result
                                    val sheet = v.findViewWithTag<View>(TAG_LOCK_GLASS_SHEET)
                                    if (sheet != null && v.width > 0 && v.height > 0) {
                                        if (sheet.width != v.width || sheet.height != v.height) {
                                            sheet.layout(0, 0, v.width, v.height)
                                        }
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
                                val cfg = LockScreenConfigHolder.sheet
                                if (cfg.enabled && isLockScreenActive()) {
                                    val rawArgs = chain.args
                                    val event = (if (rawArgs is List<*>) rawArgs[0] else (rawArgs as Array<*>)[0]) as? MotionEvent
                                    if (event != null) {
                                        handleLockscreenTouchEvent(event)
                                    }
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
            var canScaleFieldCached: Field? = null
            for (m: Method in clockContainerClass.declaredMethods) {
                if (m.name == "updateMagazineView" || m.name == "onFinishInflate") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val notifCfg = dev.joseph.hyperbounce.notification.NotificationSheetConfigHolder.sheet
                                if (notifCfg.enabled && notifCfg.lockScreenAsNotifCenterEnabled) {
                                    return result
                                }
                                val lockCfg = LockScreenConfigHolder.sheet
                                if (!lockCfg.enabled) {
                                    return result
                                }
                                val container = chain.thisObject ?: return result
                                if (canScaleFieldCached == null) {
                                    canScaleFieldCached = container.javaClass.getDeclaredField("mClockCanScale").apply { isAccessible = true }
                                }
                                canScaleFieldCached?.setBoolean(container, false)
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                }
            }
        } catch (_: Throwable) {}
    }

    private fun hookStatusBarState(module: XposedModule, classLoader: ClassLoader) {
        try {
            val sbClass = Class.forName("com.android.systemui.statusbar.StatusBarStateControllerImpl", false, classLoader)
            for (m: Method in sbClass.declaredMethods) {
                if (m.name == "setState" && m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    m.parameterTypes[1] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val rawArgs = chain.args
                                val state = (if (rawArgs is List<*>) rawArgs[0] else (rawArgs as Array<*>)[0]) as Number
                                currentStatusBarState = state.toInt()
                                if (currentStatusBarState != 1) {
                                    isDismissAnimationRunning = false
                                    onSleepOrDozeTriggered()
                                }
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                } else if (m.name == "setIsDozing" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val rawArgs = chain.args
                            val dozing = (if (rawArgs is List<*>) rawArgs[0] else (rawArgs as Array<*>)[0]) as Boolean
                            isDozing = dozing
                            if (dozing) {
                                onSleepOrDozeTriggered()
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (_: Throwable) {}
    }

    private fun hookKeyguardState(module: XposedModule, classLoader: ClassLoader) {
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
                                val showing = (if (rawArgs is List<*>) rawArgs[0] else (rawArgs as Array<*>)[0]) as Boolean
                                val occluded = (if (rawArgs is List<*>) rawArgs[1] else (rawArgs as Array<*>)[1]) as Boolean
                                isKeyguardShowing = showing && !occluded
                                if (!isKeyguardShowing) {
                                    isDismissAnimationRunning = false
                                    onSleepOrDozeTriggered()
                                }
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                    break
                }
            }
        } catch (_: Throwable) {}
    }

    private fun hookWakefulnessAndDoze(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook KeyguardLifecyclesDispatcher & KeyguardLifecycleHandler
        try {
            val dispatcherClass = Class.forName("com.android.systemui.keyguard.KeyguardLifecyclesDispatcher", false, classLoader)
            for (m: Method in dispatcherClass.declaredMethods) {
                if (m.name == "dispatch" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Int::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            try {
                                val rawArgs = chain.args
                                val what = ((if (rawArgs is List<*>) rawArgs[0] else (rawArgs as Array<*>)[0]) as Number).toInt()
                                if (what == 2 || what == 3 || what == 6 || what == 7) {
                                    isGoingToSleep = true
                                    onSleepOrDozeTriggered()
                                } else if (what == 0 || what == 1 || what == 4 || what == 5) {
                                    isGoingToSleep = false
                                }
                            } catch (_: Throwable) {}
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (_: Throwable) {}

        try {
            val handlerClass = Class.forName("com.android.systemui.keyguard.KeyguardLifecyclesDispatcher\$KeyguardLifecycleHandler", false, classLoader)
            for (m: Method in handlerClass.declaredMethods) {
                if (m.name == "handleMessage" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Message::class.java
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            try {
                                val rawArgs = chain.args
                                val msg = (if (rawArgs is List<*>) rawArgs[0] else (rawArgs as Array<*>)[0]) as? Message
                                if (msg != null) {
                                    val what = msg.what
                                    if (what == 2 || what == 3 || what == 6 || what == 7) {
                                        isGoingToSleep = true
                                        onSleepOrDozeTriggered()
                                    } else if (what == 0 || what == 1 || what == 4 || what == 5) {
                                        isGoingToSleep = false
                                    }
                                }
                            } catch (_: Throwable) {}
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (_: Throwable) {}

        // 2. Hook AnimationHelper.doAnimationToAod (HyperOS clock-to-AOD animation)
        try {
            val animHelperClass = Class.forName("com.android.keyguard.clock.animation.AnimationHelper", false, classLoader)
            for (m: Method in animHelperClass.declaredMethods) {
                if (m.name == "doAnimationToAod") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            try {
                                val rawArgs = chain.args
                                val toAod = (if (rawArgs is List<*>) rawArgs[0] else (rawArgs as Array<*>)[0]) as? Boolean ?: false
                                if (toAod) {
                                    isGoingToSleep = true
                                    onSleepOrDozeTriggered()
                                }
                            } catch (_: Throwable) {}
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (_: Throwable) {}

        // 3. Hook ClockBaseAnimation.doAnimationToAod
        try {
            val clockAnimClass = Class.forName("com.android.keyguard.clock.animation.ClockBaseAnimation", false, classLoader)
            for (m: Method in clockAnimClass.declaredMethods) {
                if (m.name == "doAnimationToAod") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            try {
                                val rawArgs = chain.args
                                val toAod = (if (rawArgs is List<*>) rawArgs[0] else (rawArgs as Array<*>)[0]) as? Boolean ?: false
                                if (toAod) {
                                    isGoingToSleep = true
                                    onSleepOrDozeTriggered()
                                }
                            } catch (_: Throwable) {}
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (_: Throwable) {}
    }

    private fun ensurePillInjected(kpvc: Any, cfg: LockScreenSheetConfig) {
        try {
            val bottomAreaView = getBottomAreaView(kpvc) ?: return
            val pill = cachedLockPillRef?.get() ?: bottomAreaView.findViewWithTag<View>(TAG_HOME_PILL)
            if (!cfg.enabled || !cfg.pillEnabled || !isLockScreenActive()) {
                pill?.visibility = View.GONE
                return
            }
            if (pill == null) {
                val context = bottomAreaView.context
                val density = context.resources.displayMetrics.density
                val pillW = (cfg.pillWidth.coerceAtLeast(40f) * density).toInt()
                val pillH = (cfg.pillHeight.coerceAtLeast(2f) * density).toInt()
                val radius = pillH / 2
                val bMargin = (cfg.pillBottomMargin * density).toInt()

                val newPill = View(context).apply {
                    id = View.generateViewId()
                    tag = TAG_HOME_PILL

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
                bottomAreaView.addView(newPill, flp)
                cachedLockPillRef = WeakReference(newPill)
            } else {
                pill.visibility = View.VISIBLE
                cachedLockPillRef = WeakReference(pill)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Pill injection error", t)
        }
    }

    private fun ensureInjected(kpvc: Any, cachedRoot: ViewGroup? = null) {
        try {
            val rootView = cachedRoot ?: getRootView(kpvc) ?: return
            val cfg = LockScreenConfigHolder.sheet
            val active = cfg.enabled && isLockScreenActive()

            var glassSheet = cachedLockGlassSheetRef?.get()
            if (glassSheet == null || glassSheet.parent != rootView) {
                glassSheet = rootView.findViewWithTag<LockScreenGlassSheetOptimizedView>(TAG_LOCK_GLASS_SHEET)
            }

            if (!active) {
                glassSheet?.visibility = View.GONE
                glassSheet?.swipeProgress = 0.0f
                val pill = cachedLockPillRef?.get() ?: getBottomAreaView(kpvc)?.findViewWithTag<View>(TAG_HOME_PILL)
                pill?.visibility = View.GONE
                val dimLayer = cachedLockWallpaperDimRef?.get() ?: (rootView.parent as? ViewGroup)?.findViewWithTag<View>(TAG_LOCK_WALLPAPER_DIM)
                dimLayer?.visibility = View.GONE
                dimLayer?.alpha = 0.0f
                val finalNotif = getNotifView(kpvc)
                if (finalNotif != null && finalNotif.translationZ != 0.0f) {
                    finalNotif.translationZ = 0.0f
                }
                val depthView = getDepthSubjectView(kpvc, rootView)
                if (depthView != null && depthView.translationZ != 0.0f) {
                    depthView.translationZ = 0.0f
                }
                return
            }

            val finalNotifView = getNotifView(kpvc)
            val depthSubjectView = getDepthSubjectView(kpvc, rootView)

            if (glassSheet == null) {
                val context = rootView.context
                LockScreenWallpaperHelper.triggerAsyncUnblurredWallpaperLoad(context)
                glassSheet = LockScreenGlassSheetOptimizedView(context).apply {
                    id = View.generateViewId()
                    tag = TAG_LOCK_GLASS_SHEET
                    translationZ = 0.0f
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
            } else {
                if (glassSheet.translationZ != 0.0f) {
                    glassSheet.translationZ = 0.0f
                }
                if (rootView.indexOfChild(glassSheet) != 0) {
                    rootView.removeView(glassSheet)
                    rootView.addView(glassSheet, 0)
                }
            }

            if (finalNotifView != null && finalNotifView.translationZ != 0.0f) {
                finalNotifView.translationZ = 0.0f
            }

            cachedLockGlassSheetRef = WeakReference(glassSheet)
            if (glassSheet.swipeProgress > 0.001f) {
                if (glassSheet.visibility != View.VISIBLE) {
                    glassSheet.visibility = View.VISIBLE
                }
            } else {
                glassSheet.visibility = View.GONE
            }
            if (rootView.width > 0 && rootView.height > 0 && (glassSheet.width != rootView.width || glassSheet.height != rootView.height)) {
                glassSheet.layout(0, 0, rootView.width, rootView.height)
            }
            if (glassSheet.visibility == View.VISIBLE) {
                glassSheet.invalidate()
            }
        } catch (_: Throwable) {}
    }
}
