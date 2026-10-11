package dev.joseph.hyperbounce.notification

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.animation.DecelerateInterpolator
import android.hardware.camera2.CameraManager
import dev.joseph.hyperbounce.notification.glass.CustomWallpaperHelper
import dev.joseph.hyperbounce.notification.glass.DepthWallpaperHelper
import dev.joseph.hyperbounce.notification.glass.NotificationGlassSheetView
import dev.joseph.hyperbounce.notification.glass.RealtimeBackdropHelper
import dev.joseph.hyperbounce.notification.lockscreen.LockScreenBottomAreaView
import dev.joseph.hyperbounce.notification.lockscreen.LockScreenCoverSheetController
import dev.joseph.hyperbounce.notification.stack.NotificationStackScrollerHelper
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlin.math.abs

/**
 * Feature 7: iOS-Style Notification Center Container Sheet Hook.
 * Converts the HyperOS Notification Center into a single cohesive iOS-style paper sheet:
 * - Continuous 120Hz native slide down from top (translationY: -height -> 0) as one integrated paper sheet.
 * - Bottommost notifications enter sight first as the sheet is pulled down.
 * - Static notification stack: eliminates horizontal rolling and staggered row drops.
 * - Leaves Keyguard and LockScreenSheetHook completely unhindered and independent.
 * - Frames the sheet with 3-edge AGSL Liquid Glass 2.0 lens and an iOS home pill at the bottom.
 * - Single-pipeline dismissal coordinator: guarantees exactly one slide-up animation and one unlock trigger.
 */
object NotificationSheetHook {

    private const val TAG = "HyperBounce_NotifSheet"
    private const val TAG_GLASS_SHEET = "hyperbounce_notif_glass_sheet"
    private const val TAG_HOME_PILL = "hyperbounce_notif_home_pill"
    private const val TAG_BOTTOM_AFFORDANCES = "hyperbounce_notif_bottom_affordances"
    private const val TAG_ORIG_MARGIN_START = 0x7E010001
    private const val TAG_ORIG_MARGIN_END = 0x7E010002
    private const val TAG_ORIG_PADDING_TOP = 0x7E010003
    private const val TAG_ORIG_MARGIN_TOP = 0x7E010004
    private const val TAG_ORIG_MARGIN_BOTTOM = 0x7E010005
    private const val TAG_ORIG_PADDING_BOTTOM = 0x7E010006
    private const val TAG_NSSL_LISTENERS_ATTACHED = 0x7E010007

    @Volatile
    var currentStatusBarState: Int = 1 // 0 = SHADE, 1 = KEYGUARD, 2 = SHADE_LOCKED

    @Volatile
    var latestFlingVelocity: Float = 0.0f

    private var shadeVelocityTracker: VelocityTracker? = null

    @Volatile
    private var isShadeFullyOpen = false

    @Volatile
    private var hasFullyOpenedForCurrentSession = false

    @Volatile
    private var isClosingMode = false

    @Volatile
    private var isUserTouchingShade = false

    @Volatile
    private var touchDownX = 0f

    @Volatile
    private var touchDownY = 0f

    @Volatile
    private var hasTriggeredUnlockForCurrentOpen = false

    val hasFullyOpened: Boolean
        get() = isShadeFullyOpen

    @Volatile
    private var isShadeOpen = false

    @Volatile
    var isShadeSwitching: Boolean = false

    @Volatile
    var isShadeSwitchingToControlCenter: Boolean = false

    @Volatile
    var isShadeSwitchingToNotification: Boolean = false

    @Volatile
    var isControlCenterExpanded: Boolean = false

    private var controlCenterRef: WeakReference<Any>? = null
    private var containerRef: WeakReference<ViewGroup>? = null
    private var themeBackgroundViewRef: WeakReference<View>? = null
    private var blurAnimatorRef: WeakReference<Any>? = null
    private var expandControllerRef: WeakReference<Any>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isKeyguardShowing: Boolean = false
    @Volatile
    private var lastTouchY: Float = 0f
    @Volatile
    private var lastTouchDy: Float = 0f
    @Volatile
    private var latestTouchYVelocity: Float = 0f
    @Volatile
    private var isOpeningGesture: Boolean = false

    private var cachedGlassSheetRef: WeakReference<NotificationGlassSheetView>? = null
    private var cachedHomePillRef: WeakReference<View>? = null
    private var cachedNsslRef: WeakReference<View>? = null
    private var cachedNsslId: Int = 0
    private var cachedShadeBgId: Int = 0
    private var cachedThemeBgId: Int = 0

    private fun getNsslView(container: ViewGroup): View? {
        val cached = cachedNsslRef?.get()
        if (cached != null && cached.parent != null) return cached
        if (cachedNsslId == 0) {
            cachedNsslId = container.resources.getIdentifier("notification_stack_scroller", "id", "com.android.systemui")
        }
        if (cachedNsslId != 0) {
            val v = container.findViewById<View>(cachedNsslId)
            if (v != null) {
                cachedNsslRef = WeakReference(v)
                return v
            }
        }
        return null
    }

    @Volatile
    private var miuiFlashlightController: Any? = null
    @Volatile
    private var fallbackTorchEnabled: Boolean = false

    fun getContainer(): ViewGroup? = containerRef?.get()

    private fun getMiuiFlashlightControllerInstance(): Any? {
        if (miuiFlashlightController != null) return miuiFlashlightController
        try {
            val cl = containerRef?.get()?.context?.classLoader ?: NotificationSheetHook::class.java.classLoader
            if (cl != null) {
                val stubClass = Class.forName("miui.stub.CommonStub\$registerFlashlightController\$1", false, cl)
                val mgrClass = Class.forName("com.miui.interfaces.InterfacesImplManager", false, cl)
                val getImpl = mgrClass.getMethod("getImpl", Class::class.java)
                val stub = getImpl.invoke(null, stubClass)
                if (stub != null) {
                    val sysUiProviderField = stub.javaClass.getDeclaredField("\$sysUIProvider")
                    sysUiProviderField.isAccessible = true
                    val provider = sysUiProviderField.get(stub)
                    if (provider != null) {
                        val fcField = provider.javaClass.getDeclaredField("mFlashlightController")
                        fcField.isAccessible = true
                        val lazy = fcField.get(provider)
                        if (lazy != null) {
                            val getMethod = lazy.javaClass.getMethod("get")
                            val fc = getMethod.invoke(lazy)
                            if (fc != null) {
                                miuiFlashlightController = fc
                                registerFlashlightListener(fc, cl)
                                return fc
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    fun isFlashlightEnabled(): Boolean {
        val ctrl = getMiuiFlashlightControllerInstance()
        if (ctrl != null) {
            try {
                val isEnabledMethod = ctrl.javaClass.getMethod("isEnabled")
                return isEnabledMethod.invoke(ctrl) as? Boolean ?: false
            } catch (_: Throwable) {}
        }
        return fallbackTorchEnabled
    }

    fun toggleFlashlight(context: Context): Boolean {
        val ctrl = getMiuiFlashlightControllerInstance()
        if (ctrl != null) {
            try {
                val isEnabledMethod = ctrl.javaClass.getMethod("isEnabled")
                val isEnabled = isEnabledMethod.invoke(ctrl) as? Boolean ?: false
                val targetState = !isEnabled
                val setFlashlightMethod = ctrl.javaClass.getMethod("setFlashlight", Boolean::class.javaPrimitiveType)
                setFlashlightMethod.invoke(ctrl, targetState)
                LockScreenCoverSheetController.onTorchStateChanged(targetState)
                return targetState
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to toggle flashlight via MiuiFlashlightController", t)
            }
        }
        // Fallback: CameraManager
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cameraManager != null) {
                val cameraId = cameraManager.cameraIdList.firstOrNull()
                if (cameraId != null) {
                    fallbackTorchEnabled = !fallbackTorchEnabled
                    cameraManager.setTorchMode(cameraId, fallbackTorchEnabled)
                    LockScreenCoverSheetController.onTorchStateChanged(fallbackTorchEnabled)
                    return fallbackTorchEnabled
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Fallback CameraManager toggle torch failed", t)
        }
        return false
    }

    @Volatile
    private var lastAppliedFraction = -1.0f

    @Volatile
    private var lastUnlockTriggerTime = 0L

    // Real-time over-pull stretch bounce offset
    @Volatile
    private var currentStretchOffset = 0f

    @Volatile
    var isHomeScreenCached: Boolean = false

    @Volatile
    var isLaunchingNotificationActivity: Boolean = false

    @Volatile
    private var cachedHomePkg: String? = null
    private var cachedCommonUtilMethod: Method? = null
    private var isCommonUtilMethodLookupDone = false
    private val bgCheckExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun updateHomeScreenCache(context: Context?) {
        if (context == null) return
        val appContext = context.applicationContext ?: context
        bgCheckExecutor.execute {
            try {
                isHomeScreenCached = isHomeScreenTop(appContext)
            } catch (_: Throwable) {}
        }
    }

    fun isKeyguard(context: Context? = null): Boolean {
        return currentStatusBarState != 0 || isKeyguardShowing
    }

    fun isControlCenterActive(): Boolean {
        if (isShadeSwitchingToNotification) return false
        if (isShadeSwitchingToControlCenter) return true
        return isControlCenterExpanded
    }

    val isUnlockedShadeActive: Boolean
        get() {
            val cfg = NotificationSheetConfigHolder.sheet
            return cfg.isSheetActive && currentStatusBarState == 0 && !isKeyguardShowing && !isControlCenterActive()
        }

    fun isLandscapeMode(): Boolean {
        val container = containerRef?.get() ?: return false
        return container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ||
                (container.width > 0 && container.height > 0 && container.width > container.height)
    }

    fun isOneToOneActive(): Boolean {
        val cfg = NotificationSheetConfigHolder.sheet
        return cfg.isOneToOneTrackingActive && !isLandscapeMode() && isUnlockedShadeActive
    }

    fun getTargetExpandThreshold(): Float {
        val container = containerRef?.get() ?: return 200.0f
        val h = container.resources.displayMetrics.heightPixels.toFloat()
        // Natural thumb sweep travel threshold: ~76% of screen height (e.g., ~1824px on a 2400px screen)
        // This ensures the sheet reaches 100% full expansion and locks down comfortably
        // well before the user's thumb reaches the physical bottom bezel of the phone.
        return if (h > 0f) (h * 0.76f).coerceAtLeast(200f) else 200.0f
    }

    fun updateExpandThreshold(targetCtrl: Any? = null) {
        val ctrl = targetCtrl ?: expandControllerRef?.get() ?: return
        try {
            val targetThresh = if (isOneToOneActive()) getTargetExpandThreshold() else 200.0f
            val threshField = ctrl.javaClass.getDeclaredField("expandThresh")
            threshField.isAccessible = true
            val currentThresh = threshField.getFloat(ctrl)
            if (abs(currentThresh - targetThresh) > 1f) {
                threshField.setFloat(ctrl, targetThresh)
                Log.d(TAG, "Updated NotificationPanelExpandController.expandThresh from $currentThresh to $targetThresh")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to update expandThresh", t)
        }
    }

    fun init(module: XposedModule, classLoader: ClassLoader) {
        sModule = module
        // 0. Crash prevention: Hook NotificationWakeUpCoordinator to prevent early NPE before stackScrollerController is injected
        hookWakeUpCoordinator(module, classLoader)

        // 1. Hook StatusBarStateControllerImpl and KeyguardStateControllerImpl to track active shade / lockscreen state
        hookStatusBarState(module, classLoader)
        hookKeyguardState(module, classLoader)

        // 2. Hook ShadeSwitchControllerImpl and ControlCenterImpl to prevent unlock bounce on switch
        hookShadeSwitch(module, classLoader)

        // 3. Hook MiuiNQSContainer (notification_container_parent) for lifecycle, glass & pill injection
        hookContainerParent(module, classLoader)

        // 4. Hook FloatFlowProperty to capture 120Hz continuous Folme expansion & over-pull stretch
        hookFloatFlowProperty(module, classLoader)

        // 5. Hook NotificationHeaderExpandController (expansion tracking)
        hookHeaderCallback(module, classLoader)

        // 6. Hook ShadeExpansionStateManager for drag gesture tracking
        hookShadeExpansionState(module, classLoader)

        // 7. Hook NotificationPanelExpandController for expand completion and dismissal
        hookExpandController(module, classLoader)

        // 8. Hook ViewStateInjectorImpl to center the notification drop (no horizontal blanket rollout)
        hookViewStateInjector(module, classLoader)

        // 9. Hook StatusBarTransitionAnimatorController to track activity launch from notification clicks
        hookTransitionAnimatorController(module, classLoader)

        // 10. Hook NotificationShadeWindowView & NotificationPanelView for touch tracking and touch-release unlock trigger
        hookNotificationWindowTouch(module, classLoader)

        // 11. Hook NotificationPanelBlurAnimator & BlurUtils for depth wallpaper background blur suppression
        hookBlurSuppression(module, classLoader)

        // 12. Hook MiuiFlashlightController for quick affordances
        hookFlashlightController(module, classLoader)

        // 13. Hook NotificationTopPaddingControllerImpl for dynamic clock clearance
        hookTopPaddingController(module, classLoader)

        // 14. Hook KeyguardPanelViewController & NSSL for notification state & clock morphing
        hookKeyguardNotificationState(module, classLoader)

        // 15. Hook empty space clicks on NSSL & Panel to prevent premature collapse when image background is active
        hookEmptySpaceClick(module, classLoader)

        // 16. Hook NotificationPanelExpansionAnimator to bypass Folme spring during active finger dragging for 1:1 tracking
        hookNotificationPanelExpansionAnimator(module, classLoader)

        // 17. Hook MiuiPanelAnimValueObjectImpl to scale notification translation 1:1 during 1:1 tracking
        hookMiuiPanelAnimValueObject(module, classLoader)

        // 18. Hook FakeFocusNotifControllerImpl to scale focus notification translation 1:1 during 1:1 tracking
        hookFakeFocusNotifController(module, classLoader)

        // 19. Hook notification row injectors to prevent transparent cards when HyperOS Advanced Textures (background_blur_enable) is active
        hookNotificationCardBlurSuppression(module, classLoader)

        // 20. Hook AnimationHelper to enforce big clock in Notification Center without affecting lockscreen
        hookAnimationHelper(module, classLoader)

        // 21. Hook StackScrollAlgorithm for static top clearance margin and iOS stacked notifications
        hookStackScrollAlgorithm(module, classLoader)

        // 22. Hook NotificationStackScrollLayout.getScrollRange() for stacked headroom
        hookStackScrollLayoutRange(module, classLoader)
    }

    private fun hookWakeUpCoordinator(module: XposedModule, classLoader: ClassLoader) {
        try {
            val nwcClass = Class.forName("com.android.systemui.statusbar.notification.NotificationWakeUpCoordinator", false, classLoader)
            var stackField: Field? = null
            try {
                stackField = nwcClass.getDeclaredField("stackScrollerController")
                stackField.isAccessible = true
            } catch (_: Throwable) {}

            var isStackScrollerControllerInjected = false
            val guardedMethods = setOf("updateDozeAmount", "updateHideAmount", "notifyAnimationStart", "setWakingUp", "setHardDozeAmountOverride")
            for (m in nwcClass.declaredMethods) {
                if (m.name in guardedMethods) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (!isStackScrollerControllerInjected) {
                                val inst = chain.thisObject
                                if (inst != null && stackField != null) {
                                    val controller = stackField.get(inst)
                                    if (controller == null) {
                                        // stackScrollerController has not yet been injected by NotificationPanelViewController.
                                        // Return early to prevent NPE during early SystemUI startup.
                                        return null
                                    }
                                    isStackScrollerControllerInjected = true
                                }
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
            Log.d(TAG, "Hooked NotificationWakeUpCoordinator successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationWakeUpCoordinator", t)
        }
    }

    private fun hideSheetForControlCenter() {
        mainHandler.post {
            val container = containerRef?.get() ?: return@post
            val sheet = cachedGlassSheetRef?.get() ?: container.findViewWithTag<View>(TAG_GLASS_SHEET)
            val pill = cachedHomePillRef?.get() ?: container.findViewWithTag<View>(TAG_HOME_PILL)
            sheet?.visibility = View.GONE
            pill?.visibility = View.GONE
            LockScreenCoverSheetController.onShadeReset(container)
            container.translationY = 0.0f
            container.scaleX = 1.0f
            container.scaleY = 1.0f
            resetNotificationMargins(container)
            RealtimeBackdropHelper.stopLiveRefresh()
        }
    }

    private fun showSheetForNotificationCenter() {
        mainHandler.post {
            val container = containerRef?.get() ?: return@post
            val cfg = NotificationSheetConfigHolder.sheet
            if (!cfg.isSheetActive || currentStatusBarState != 0 || isKeyguard() || isControlCenterActive()) {
                return@post
            }
            val isLandscape = container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || container.width > container.height
            val sheet = cachedGlassSheetRef?.get() ?: ensureGlassSheetInjected(container)
            val pill = cachedHomePillRef?.get() ?: ensureHomePillInjected(container)
            adjustNotificationMargins(container)
            sheet?.visibility = View.VISIBLE
            sheet?.expansionFraction = 1.0f
            sheet?.translationY = 0.0f
            sheet?.isClosingFromFullOpen = false
            sheet?.postInvalidateOnAnimation()
            pill?.visibility = View.VISIBLE
            pill?.translationY = 0.0f
            val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
            if (!isLandscape && cfg.isLockScreenActive && isHomescreenAllowed) {
                LockScreenCoverSheetController.onShadeFullyOpened(container)
            } else {
                LockScreenCoverSheetController.onShadeReset(container)
            }
            if (!isLandscape && cfg.isGlassEffectsActive) {
                sheet?.let { RealtimeBackdropHelper.startLiveRefresh(container.context, it) }
            } else {
                RealtimeBackdropHelper.stopLiveRefresh()
            }
            val isWallpaperOrDepth = (cfg.isDepthActive || cfg.isWallpaperActive) && isHomescreenAllowed
            if (isWallpaperOrDepth) {
                sheet?.shadeBackgroundView?.alpha = 0.0f
                themeBackgroundViewRef?.get()?.alpha = 0.0f
            } else {
                sheet?.shadeBackgroundView?.alpha = 1.0f
                themeBackgroundViewRef?.get()?.alpha = 1.0f
            }
        }
    }

    private fun suppressNotificationBlur() {
        val cfg = NotificationSheetConfigHolder.sheet
        val container = containerRef?.get()
        val isLandscape = container != null && (container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || container.width > container.height)
        if (isLandscape) return
        val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
        val isWallpaperOrDepth = (cfg.isDepthActive || cfg.isWallpaperActive) && isHomescreenAllowed
        if (isWallpaperOrDepth && currentStatusBarState == 0 && !isControlCenterActive()) {
            val anim = blurAnimatorRef?.get() ?: return
            try {
                for (m in anim.javaClass.declaredMethods) {
                    if (m.name.startsWith("updateBlurRatio")) {
                        m.isAccessible = true
                        m.invoke(anim, 0.0f, false)
                        break
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to suppress blur ratio", t)
            }
        }
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
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val newState = (args[0] as Number).toInt()
                                currentStatusBarState = newState
                                updateExpandThreshold()

                                if (newState != 0) {
                                    isKeyguardShowing = true
                                    isShadeOpen = false
                                    isShadeFullyOpen = false
                                    hasFullyOpenedForCurrentSession = false
                                    hasTriggeredUnlockForCurrentOpen = true
                                    isControlCenterExpanded = false
                                    isShadeSwitching = false
                                    isShadeSwitchingToControlCenter = false
                                    isShadeSwitchingToNotification = false
                                    isClosingMode = false
                                    lastAppliedFraction = 0f
                                    currentStretchOffset = 0f
                                    mainHandler.post {
                                        val container = containerRef?.get()
                                        if (container != null) {
                                            val curSheet = container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)
                                            curSheet?.isClosingMode = false
                                            curSheet?.isClosingFromFullOpen = false
                                            curSheet?.visibility = View.GONE
                                            curSheet?.translationY = 0.0f
                                            val curPill = container.findViewWithTag<View>(TAG_HOME_PILL)
                                            curPill?.visibility = View.GONE
                                            curPill?.translationY = 0.0f
                                            // Reset container translation so lockscreen/keyguard starts at 0.0f
                                            container.translationY = 0.0f
                                            container.scaleX = 1.0f
                                            container.scaleY = 1.0f
                                            resetNotificationMargins(container)
                                            LockScreenCoverSheetController.onShadeReset(container)
                                            val headerId = container.resources.getIdentifier("normal_notification_header_view", "id", "com.android.systemui")
                                            if (headerId != 0) {
                                                container.rootView?.findViewById<View>(headerId)?.let { hView ->
                                                    hView.alpha = 0.0f
                                                    hView.visibility = View.GONE
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in setState hook", t)
                            }
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook StatusBarStateControllerImpl", t)
        }
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
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val showing = args[0] as Boolean
                                val occluded = args[1] as Boolean
                                isKeyguardShowing = showing && !occluded
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                    Log.d(TAG, "Hooked KeyguardStateControllerImpl.notifyKeyguardState successfully")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook KeyguardStateControllerImpl", t)
        }
    }

    private fun hookShadeSwitch(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook ShadeSwitchControllerImpl
        try {
            val sscClass = Class.forName("com.miui.systemui.shade.ShadeSwitchControllerImpl", false, classLoader)
            for (m in sscClass.declaredMethods) {
                when (m.name) {
                    "setSwitching" -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                try {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                    val switching = args[0] as Boolean
                                    isShadeSwitching = switching

                                    val toCtrlField = chain.thisObject?.javaClass?.getDeclaredField("toCtrl")
                                    toCtrlField?.isAccessible = true
                                    val toCtrl = toCtrlField?.getBoolean(chain.thisObject) ?: false

                                    if (switching) {
                                        isShadeSwitchingToControlCenter = toCtrl
                                        isShadeSwitchingToNotification = !toCtrl
                                        if (toCtrl) {
                                            isControlCenterExpanded = true
                                            isShadeOpen = false
                                            isShadeFullyOpen = false
                                            hasFullyOpenedForCurrentSession = false
                                            lastAppliedFraction = 0f
                                            hasTriggeredUnlockForCurrentOpen = true
                                            currentStretchOffset = 0f
                                            hideSheetForControlCenter()
                                        } else {
                                            isControlCenterExpanded = false
                                            hasTriggeredUnlockForCurrentOpen = true
                                            currentStretchOffset = 0f
                                            showSheetForNotificationCenter()
                                            suppressNotificationBlur()
                                        }
                                    } else {
                                        isShadeSwitchingToControlCenter = false
                                        isShadeSwitchingToNotification = false
                                        if (toCtrl) {
                                            isControlCenterExpanded = true
                                            isShadeOpen = false
                                            isShadeFullyOpen = false
                                            hasFullyOpenedForCurrentSession = false
                                            lastAppliedFraction = 0f
                                            hideSheetForControlCenter()
                                        } else {
                                            isControlCenterExpanded = false
                                            isShadeOpen = true
                                            isShadeFullyOpen = true
                                            hasFullyOpenedForCurrentSession = true
                                            hasTriggeredUnlockForCurrentOpen = false
                                            lastAppliedFraction = 1.0f
                                            showSheetForNotificationCenter()
                                            suppressNotificationBlur()
                                        }
                                    }
                                } catch (_: Throwable) {}
                                return chain.proceed()
                            }
                        })
                    }
                    "getSwitchable" -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
                                if (cfg.isLockScreenActive && isHomescreenAllowed && isUnlockedShadeActive) {
                                    return false
                                }
                                return chain.proceed()
                            }
                        })
                    }
                    "switchToControlCenter" -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
                                if (cfg.isLockScreenActive && isHomescreenAllowed && isUnlockedShadeActive) {
                                    return null
                                }
                                isControlCenterExpanded = true
                                isShadeSwitching = true
                                isShadeSwitchingToControlCenter = true
                                isShadeSwitchingToNotification = false
                                isShadeOpen = false
                                isShadeFullyOpen = false
                                hasFullyOpenedForCurrentSession = false
                                lastAppliedFraction = 0f
                                hasTriggeredUnlockForCurrentOpen = true
                                currentStretchOffset = 0f
                                hideSheetForControlCenter()
                                return chain.proceed()
                            }
                        })
                    }
                    "switchToNotification" -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                isControlCenterExpanded = false
                                isShadeSwitching = true
                                isShadeSwitchingToControlCenter = false
                                isShadeSwitchingToNotification = true
                                isShadeFullyOpen = true
                                isShadeOpen = true
                                hasFullyOpenedForCurrentSession = true
                                hasTriggeredUnlockForCurrentOpen = false
                                lastAppliedFraction = 1.0f
                                showSheetForNotificationCenter()
                                suppressNotificationBlur()
                                return chain.proceed()
                            }
                        })
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ShadeSwitchControllerImpl", t)
        }

        // 1b. Hook NotificationShadeWrapper.getSwitchable
        try {
            val nswClass = Class.forName("com.miui.systemui.shade.NotificationShadeWrapper", false, classLoader)
            for (m in nswClass.declaredMethods) {
                if (m.name == "getSwitchable") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = NotificationSheetConfigHolder.sheet
                            val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
                            if (cfg.isLockScreenActive && isHomescreenAllowed && isUnlockedShadeActive) {
                                return false
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationShadeWrapper.getSwitchable", t)
        }

        // 2. Hook ControlCenterImpl
        try {
            val ccClass = Class.forName("com.miui.systemui.controlcenter.ControlCenterImpl", false, classLoader)
            for (m in ccClass.declaredMethods) {
                when (m.name) {
                    "expand", "switchShow" -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                if (chain.thisObject != null) {
                                    controlCenterRef = WeakReference(chain.thisObject)
                                }
                                isControlCenterExpanded = true
                                isShadeOpen = false
                                isShadeFullyOpen = false
                                hasFullyOpenedForCurrentSession = false
                                lastAppliedFraction = 0f
                                hasTriggeredUnlockForCurrentOpen = true
                                currentStretchOffset = 0f
                                hideSheetForControlCenter()
                                return chain.proceed()
                            }
                        })
                    }
                    "collapse", "switchHide" -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                if (chain.thisObject != null) {
                                    controlCenterRef = WeakReference(chain.thisObject)
                                }
                                isControlCenterExpanded = false
                                if (isShadeSwitchingToNotification) {
                                    isShadeSwitching = false
                                    isShadeSwitchingToNotification = false
                                    isShadeOpen = true
                                    isShadeFullyOpen = true
                                    hasFullyOpenedForCurrentSession = true
                                    hasTriggeredUnlockForCurrentOpen = false
                                    lastAppliedFraction = 1.0f
                                    showSheetForNotificationCenter()
                                    suppressNotificationBlur()
                                } else {
                                    isShadeSwitching = false
                                    isShadeFullyOpen = false
                                    hasTriggeredUnlockForCurrentOpen = true
                                }
                                return chain.proceed()
                            }
                        })
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ControlCenterImpl", t)
        }

        // 3. Hook ControlCenterWindowViewController to ensure isControlCenterExpanded is never stuck
        try {
            val ccwvcClass = Class.forName("com.miui.systemui.controlcenter.panel.ControlCenterWindowViewController", false, classLoader)
            for (m in ccwvcClass.declaredMethods) {
                if (m.name == "hidePanel" || m.name == "collapse") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            isControlCenterExpanded = false
                            if (isShadeSwitchingToNotification) {
                                isShadeSwitching = false
                                isShadeSwitchingToNotification = false
                                isShadeOpen = true
                                isShadeFullyOpen = true
                                hasFullyOpenedForCurrentSession = true
                                hasTriggeredUnlockForCurrentOpen = false
                                lastAppliedFraction = 1.0f
                                showSheetForNotificationCenter()
                                suppressNotificationBlur()
                            } else {
                                isShadeSwitching = false
                                isShadeFullyOpen = false
                                hasTriggeredUnlockForCurrentOpen = true
                            }
                            return chain.proceed()
                        }
                    })
                } else if (m.name == "showPanel" || m.name == "expand") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            isControlCenterExpanded = true
                            isShadeOpen = false
                            isShadeFullyOpen = false
                            hasFullyOpenedForCurrentSession = false
                            lastAppliedFraction = 0f
                            hasTriggeredUnlockForCurrentOpen = true
                            hideSheetForControlCenter()
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (_: Throwable) {}
    }

    private fun hookContainerParent(module: XposedModule, classLoader: ClassLoader) {
        // Point 1: NotificationsQuickSettingsContainer.onFinishInflate()
        try {
            val nqscClass = Class.forName("com.android.systemui.shade.NotificationsQuickSettingsContainer", false, classLoader)
            for (m: Method in nqscClass.declaredMethods) {
                if (m.name == "onFinishInflate" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val container = chain.thisObject as? ViewGroup
                                if (container != null) {
                                    onContainerAcquired(container)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in NotificationsQuickSettingsContainer.onFinishInflate hook", t)
                            }
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationsQuickSettingsContainer", t)
        }

        // Point 2: MiuiNQSContainer.onMeasure(int, int)
        try {
            val miuiNqsClass = Class.forName("com.android.systemui.shade.MiuiNQSContainer", false, classLoader)
            for (m: Method in miuiNqsClass.declaredMethods) {
                if (m.name == "onMeasure" && m.parameterTypes.size == 2) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val container = chain.thisObject as? ViewGroup
                                if (container != null && (containerRef?.get() != container || container.findViewWithTag<View>(TAG_GLASS_SHEET) == null)) {
                                    onContainerAcquired(container)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in MiuiNQSContainer.onMeasure hook", t)
                            }
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook MiuiNQSContainer.onMeasure", t)
        }

        // Point 3: NotificationPanelViewController.onFinishInflate()
        try {
            val npvcClass = Class.forName("com.android.systemui.shade.NotificationPanelViewController", false, classLoader)
            for (m: Method in npvcClass.declaredMethods) {
                if (m.name == "onFinishInflate" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val npvc = chain.thisObject
                                if (npvc != null) {
                                    val field = npvc.javaClass.getDeclaredField("mNotificationContainerParent")
                                    field.isAccessible = true
                                    val container = field.get(npvc) as? ViewGroup
                                    if (container != null) {
                                        onContainerAcquired(container)
                                    }
                                    if (expandControllerRef?.get() == null) {
                                        try {
                                            val notifInjField = npvc.javaClass.getDeclaredField("mNotifInjector")
                                            notifInjField.isAccessible = true
                                            val inj = notifInjField.get(npvc)
                                            if (inj != null) {
                                                val helperField = inj.javaClass.getDeclaredField("expandHelper")
                                                helperField.isAccessible = true
                                                val helper = helperField.get(inj)
                                                if (helper != null) {
                                                    expandControllerRef = WeakReference(helper)
                                                    updateExpandThreshold(helper)
                                                }
                                            }
                                        } catch (_: Throwable) {}
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in NotificationPanelViewController.onFinishInflate hook", t)
                            }
                            return result
                        }
                    })
                }
                if (m.name.startsWith("fling") && m.parameterTypes.isNotEmpty() &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val velocity = (args[0] as? Number)?.toFloat() ?: 0.0f
                                var isExpanding = false
                                for (i in 1 until args.size) {
                                    if (args[i] is Boolean) {
                                        isExpanding = args[i] as Boolean
                                        break
                                    }
                                }
                                val absVel = kotlin.math.abs(velocity)
                                if (!isExpanding) {
                                    val effectiveVel = if (absVel > 0.0f) absVel else latestFlingVelocity
                                    if (effectiveVel > 0.0f) {
                                        latestFlingVelocity = effectiveVel
                                        Log.d(TAG, "Captured shade/unlock fling velocity (${m.name}): $latestFlingVelocity")
                                    }
                                }
                            } catch (_: Throwable) {}
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationPanelViewController", t)
        }
    }

    private fun onContainerAcquired(container: ViewGroup) {
        containerRef = WeakReference(container)
        container.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                updateExpandThreshold()
            }
        }
        mainHandler.post {
            registerSettingsReceiver(container.context)
            // Keep container clip settings stock
            container.clipChildren = false
            container.clipToPadding = false

            val nssl = getNsslView(container) as? ViewGroup
            if (nssl != null) {
                nssl.visibility = View.VISIBLE
                if (nssl.getTag(TAG_NSSL_LISTENERS_ATTACHED) == null) {
                    nssl.setTag(TAG_NSSL_LISTENERS_ATTACHED, true)
                    nssl.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                        val cfg = NotificationSheetConfigHolder.sheet
                        if (cfg.isLockScreenActive) {
                            LockScreenCoverSheetController.syncClockWithNotifications(container)
                        }
                    }
                    nssl.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                        override fun onChildViewAdded(parent: View?, child: View?) {
                            val cfg = NotificationSheetConfigHolder.sheet
                            if (cfg.isLockScreenActive) {
                                LockScreenCoverSheetController.syncClockWithNotifications(container)
                            }
                        }
                        override fun onChildViewRemoved(parent: View?, child: View?) {
                            val cfg = NotificationSheetConfigHolder.sheet
                            if (cfg.isLockScreenActive) {
                                LockScreenCoverSheetController.syncClockWithNotifications(container)
                            }
                        }
                    })
                }
            }

            val cfg = NotificationSheetConfigHolder.sheet
            val isImageBgActive = cfg.isWallpaperActive
            if (isImageBgActive) {
                container.isClickable = false
            }

            if (currentStatusBarState != 0 || isKeyguard() || isControlCenterActive()) {
                container.findViewWithTag<View>(TAG_GLASS_SHEET)?.visibility = View.GONE
                container.findViewWithTag<View>(TAG_HOME_PILL)?.visibility = View.GONE
                container.translationY = 0.0f
                resetNotificationMargins(container)
            } else {
                ensureGlassSheetInjected(container)
                ensureHomePillInjected(container)
                adjustNotificationMargins(container)
                val frac = if (lastAppliedFraction >= 0f) lastAppliedFraction else 0f
                applyExpansionFractionInternal(frac)
            }
        }
    }

    private fun ensureGlassSheetInjected(container: ViewGroup): NotificationGlassSheetView? {
        try {
            val existing = cachedGlassSheetRef?.get()
            if (existing != null && existing.parent == container) {
                return existing
            }
            val cfg = NotificationSheetConfigHolder.sheet
            var sheet = container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)

            if (!cfg.isSheetActive || currentStatusBarState != 0 || isKeyguard() || isControlCenterActive()) {
                sheet?.visibility = View.GONE
                return null
            }

            if (sheet == null) {
                sheet = NotificationGlassSheetView(container.context).apply {
                    tag = TAG_GLASS_SHEET
                    visibility = View.GONE
                }
                val lp = try {
                    val lpClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams", false, container.context.classLoader)
                    val constructor = lpClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    val inst = constructor.newInstance(0, 0) as ViewGroup.MarginLayoutParams
                    applyConstraintParentParams(inst, container)
                    inst
                } catch (_: Throwable) {
                    android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                container.addView(sheet, 0, lp)
                Log.d(TAG, "Injected NotificationGlassSheetView successfully")
            }

            // Never consume touch events so swipe gestures between shade & control center (RTL/LTR) work seamlessly
            sheet.isClickable = false
            sheet.isFocusable = false
            sheet.setOnClickListener(null)

            // Bind the real-time blurred shade background layer (ShadeBackgroundView)
            if (sheet.shadeBackgroundView == null) {
                if (cachedShadeBgId == 0) {
                    cachedShadeBgId = container.resources.getIdentifier("shade_background", "id", "com.android.systemui")
                }
                if (cachedShadeBgId != 0) {
                    var p: ViewParent? = container.parent
                    while (p != null) {
                        if (p is ViewGroup) {
                            val bg = p.findViewById<View>(cachedShadeBgId)
                            if (bg != null) {
                                sheet.shadeBackgroundView = bg
                                break
                            }
                        }
                        p = p.parent
                    }
                }
            }

            // Bind the Xiaomi theme_background view for depth blur suppression
            if (themeBackgroundViewRef?.get() == null) {
                if (cachedThemeBgId == 0) {
                    cachedThemeBgId = container.resources.getIdentifier("theme_background", "id", "com.android.systemui")
                }
                if (cachedThemeBgId != 0) {
                    var p: ViewParent? = container.parent
                    while (p != null) {
                        if (p is ViewGroup) {
                            val bg = p.findViewById<View>(cachedThemeBgId)
                            if (bg != null) {
                                themeBackgroundViewRef = WeakReference(bg)
                                break
                            }
                        }
                        p = p.parent
                    }
                }
            }
            cachedGlassSheetRef = WeakReference(sheet)
            return sheet
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to inject NotificationGlassSheetView", t)
            return null
        }
    }

    private fun ensureHomePillInjected(container: ViewGroup): View? {
        try {
            val existing = cachedHomePillRef?.get()
            if (existing != null && existing.parent == container) {
                return existing
            }
            val cfg = NotificationSheetConfigHolder.sheet
            var pillWrapper = container.findViewWithTag<ViewGroup>(TAG_HOME_PILL)

            if (!cfg.isSheetActive || currentStatusBarState != 0 || isKeyguard() || isControlCenterActive()) {
                pillWrapper?.visibility = View.GONE
                return null
            }

            if (pillWrapper == null) {
                val context = container.context
                val density = context.resources.displayMetrics.density
                val pillW = (cfg.pillWidth.coerceAtLeast(40f) * density).toInt()
                val pillH = (cfg.pillHeight.coerceAtLeast(2f) * density).toInt()
                val radius = pillH / 2f
                val bMargin = (cfg.pillBottomMargin * density).toInt()

                val wrapper = android.widget.FrameLayout(context).apply {
                    tag = TAG_HOME_PILL
                    visibility = View.GONE
                    clipChildren = false
                    clipToPadding = false
                    isClickable = false
                    isFocusable = false
                    isEnabled = false
                    setOnTouchListener { _, _ -> false }
                }

                val pillBar = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(0xF0FFFFFF.toInt())
                        cornerRadius = radius
                    }
                    elevation = 16f * density
                    isClickable = false
                    isFocusable = false
                    isEnabled = false
                    setOnTouchListener { _, _ -> false }
                }
                val barLp = android.widget.FrameLayout.LayoutParams(
                    pillW,
                    pillH,
                    android.view.Gravity.CENTER
                )
                wrapper.addView(pillBar, barLp)

                val lp = try {
                    val lpClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams", false, container.context.classLoader)
                    val constructor = lpClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    val inst = constructor.newInstance(pillW, pillH) as ViewGroup.MarginLayoutParams
                    inst.bottomMargin = bMargin
                    lpClass.getField("bottomToBottom").setInt(inst, 0)
                    lpClass.getField("startToStart").setInt(inst, 0)
                    lpClass.getField("endToEnd").setInt(inst, 0)
                    inst
                } catch (_: Throwable) {
                    android.widget.FrameLayout.LayoutParams(
                        pillW,
                        pillH,
                        android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    ).apply {
                        bottomMargin = bMargin
                    }
                }

                container.addView(wrapper, lp)
                pillWrapper = wrapper
                Log.d(TAG, "Injected home indicator pill successfully")
            }
            cachedHomePillRef = WeakReference(pillWrapper)
            return pillWrapper
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to inject home pill", t)
            return null
        }
    }

    private var sModule: XposedModule? = null
    private var isSettingsReceiverRegistered = false

    private fun registerSettingsReceiver(context: Context) {
        if (isSettingsReceiverRegistered) return
        try {
            val prefs = try {
                sModule?.getRemotePreferences(dev.joseph.hyperbounce.prefs.PREFS_GROUP)
            } catch (_: Throwable) {
                null
            }
            val isHotReload = prefs?.getBoolean("hot_reload_enabled", false) ?: false

            val filter = android.content.IntentFilter().apply {
                if (isHotReload) {
                    addAction("dev.joseph.hyperbounce.ACTION_SETTINGS_CHANGED")
                }
                addAction("dev.joseph.hyperbounce.ACTION_REFRESH_DEPTH_WALLPAPER")
                addAction(android.content.Intent.ACTION_WALLPAPER_CHANGED)
            }
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: android.content.Intent?) {
                    val targetCtx = c ?: context
                    if (intent?.action == "dev.joseph.hyperbounce.ACTION_REFRESH_DEPTH_WALLPAPER" ||
                        intent?.action == android.content.Intent.ACTION_WALLPAPER_CHANGED
                    ) {
                        dev.joseph.hyperbounce.lockscreen.glass.LockScreenWallpaperHelper.invalidateCache()
                        CustomWallpaperHelper.clearCache(targetCtx)
                        DepthWallpaperHelper.clearCache(targetCtx, forceClearAll = true)
                        DepthWallpaperHelper.triggerAsyncSubjectGeneration(targetCtx, forceRefresh = true) {
                            mainHandler.post {
                                containerRef?.get()?.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)?.postInvalidateOnAnimation()
                            }
                        }
                        return
                    }
                    val key = intent?.getStringExtra("key") ?: return
                    val cfg = NotificationSheetConfigHolder.sheet
                    var handled = true
                    when (key) {
                        "notif_master_enabled" -> {
                            cfg.masterEnabled = intent.getBooleanExtra("value_boolean", cfg.masterEnabled)
                            updateExpandThreshold()
                            if (!cfg.masterEnabled) {
                                val container = containerRef?.get()
                                if (container != null) {
                                    mainHandler.post {
                                        val curSheet = cachedGlassSheetRef?.get() ?: container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)
                                        curSheet?.visibility = View.GONE
                                        val curPill = cachedHomePillRef?.get() ?: container.findViewWithTag<View>(TAG_HOME_PILL)
                                        curPill?.visibility = View.GONE
                                        container.translationY = 0.0f
                                        LockScreenCoverSheetController.onShadeReset(container)
                                        RealtimeBackdropHelper.stopLiveRefresh()
                                    }
                                }
                            }
                        }
                        "notif_sheet_enabled" -> {
                            cfg.enabled = intent.getBooleanExtra("value_boolean", cfg.enabled)
                            updateExpandThreshold()
                        }
                        "notif_sheet_lock_wallpaper_enabled" -> {
                            cfg.lockWallpaperEnabled = intent.getBooleanExtra("value_boolean", cfg.lockWallpaperEnabled)
                            updateExpandThreshold()
                        }
                        "notif_sheet_one_to_one_tracking" -> {
                            cfg.oneToOneTrackingEnabled = intent.getBooleanExtra("value_boolean", cfg.oneToOneTrackingEnabled)
                            updateExpandThreshold()
                        }
                        "notif_sheet_custom_wallpaper_enabled" -> {
                            cfg.customWallpaperEnabled = intent.getBooleanExtra("value_boolean", cfg.customWallpaperEnabled)
                            CustomWallpaperHelper.clearCache(targetCtx)
                            containerRef?.get()?.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)?.postInvalidateOnAnimation()
                        }
                        "notif_sheet_lock_wallpaper_homescreen_only" -> cfg.lockWallpaperHomescreenOnly = intent.getBooleanExtra("value_boolean", cfg.lockWallpaperHomescreenOnly)
                        "notif_sheet_depth_wallpaper_enabled" -> {
                            cfg.depthWallpaperEnabled = intent.getBooleanExtra("value_boolean", cfg.depthWallpaperEnabled)
                            if (cfg.isDepthActive) {
                                DepthWallpaperHelper.triggerAsyncSubjectGeneration(targetCtx)
                            }
                        }
                        "notif_sheet_depth_custom_subject" -> {
                            cfg.depthCustomSubject = intent.getBooleanExtra("value_boolean", cfg.depthCustomSubject)
                            DepthWallpaperHelper.clearCache(targetCtx, forceClearAll = !cfg.depthCustomSubject)
                            if (cfg.isDepthActive) {
                                DepthWallpaperHelper.triggerAsyncSubjectGeneration(targetCtx, forceRefresh = true) {
                                    mainHandler.post {
                                        containerRef?.get()?.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)?.postInvalidateOnAnimation()
                                    }
                                }
                            }
                        }
                        "notif_sheet_lockscreen_enabled" -> cfg.lockScreenAsNotifCenterEnabled = intent.getBooleanExtra("value_boolean", cfg.lockScreenAsNotifCenterEnabled)
                        "notif_sheet_show_bottom_widgets" -> cfg.showBottomWidgets = intent.getBooleanExtra("value_boolean", cfg.showBottomWidgets)
                        "notif_sheet_clock_morph" -> cfg.clockMorphOnNotifications = intent.getBooleanExtra("value_boolean", cfg.clockMorphOnNotifications)
                        "notif_sheet_glass_effects_enabled" -> cfg.glassEffectsEnabled = intent.getBooleanExtra("value_boolean", cfg.glassEffectsEnabled)
                        "notif_sheet_glass_only_without_wallpaper" -> cfg.glassOnlyWithoutWallpaper = intent.getBooleanExtra("value_boolean", cfg.glassOnlyWithoutWallpaper)
                        "notif_card_refraction_height" -> cfg.refractionHeight = intent.getFloatExtra("value_float", cfg.refractionHeight)
                        "notif_card_side_refraction" -> cfg.sideRefraction = intent.getFloatExtra("value_float", cfg.sideRefraction)
                        "notif_card_bottom_refraction" -> cfg.bottomRefraction = intent.getFloatExtra("value_float", cfg.bottomRefraction)
                        "notif_card_bevel_width" -> cfg.bevelWidth = intent.getFloatExtra("value_float", cfg.bevelWidth)
                        "notif_card_left_bevel" -> cfg.leftBevelWidth = intent.getFloatExtra("value_float", cfg.leftBevelWidth)
                        "notif_card_right_bevel" -> cfg.rightBevelWidth = intent.getFloatExtra("value_float", cfg.rightBevelWidth)
                        "notif_card_bottom_bevel" -> cfg.bottomBevelWidth = intent.getFloatExtra("value_float", cfg.bottomBevelWidth)
                        "notif_card_dispersion" -> cfg.dispersionStrength = intent.getFloatExtra("value_float", cfg.dispersionStrength)
                        "notif_card_specular" -> cfg.specularStrength = intent.getFloatExtra("value_float", cfg.specularStrength)
                        "notif_card_hairline" -> cfg.hairlineStrength = intent.getFloatExtra("value_float", cfg.hairlineStrength)
                        "notif_card_inner_glow" -> cfg.innerGlowStrength = intent.getFloatExtra("value_float", cfg.innerGlowStrength)
                        "notif_card_rim_softness" -> cfg.rimSoftness = intent.getFloatExtra("value_float", cfg.rimSoftness)
                        "notif_card_inner_shadow" -> cfg.innerShadow = intent.getFloatExtra("value_float", cfg.innerShadow)
                        "notif_card_saturation" -> cfg.saturationFactor = intent.getFloatExtra("value_float", cfg.saturationFactor)
                        "notif_card_blur_radius" -> cfg.blurRadius = intent.getFloatExtra("value_float", cfg.blurRadius)
                        "notif_card_corner_radius" -> cfg.cornerRadius = intent.getFloatExtra("value_float", cfg.cornerRadius)
                        "notif_side_margin" -> cfg.sideMargin = intent.getFloatExtra("value_float", cfg.sideMargin)
                        "notif_pill_width" -> cfg.pillWidth = intent.getFloatExtra("value_float", cfg.pillWidth)
                        "notif_pill_height" -> cfg.pillHeight = intent.getFloatExtra("value_float", cfg.pillHeight)
                        "notif_pill_bottom_margin" -> cfg.pillBottomMargin = intent.getFloatExtra("value_float", cfg.pillBottomMargin)
                        "notif_start_margin_top" -> cfg.notifStartMarginTop = intent.getFloatExtra("value_float", cfg.notifStartMarginTop)
                        "notif_clock_scale" -> cfg.notifClockScale = intent.getFloatExtra("value_float", cfg.notifClockScale)
                        "notif_stack_enabled" -> cfg.notifStackEnabled = intent.getBooleanExtra("value_boolean", cfg.notifStackEnabled)
                        "notif_launcher_unlock_on_dismiss" -> cfg.launcherUnlockOnDismiss = intent.getBooleanExtra("value_boolean", cfg.launcherUnlockOnDismiss)
                        else -> handled = false
                    }
                    if (handled) {
                        mainHandler.post {
                            val container = containerRef?.get() ?: return@post
                            adjustNotificationMargins(container)
                            container.findViewWithTag<View>(TAG_GLASS_SHEET)?.invalidate()
                        }
                    }
                }
            }
            val appCtx = context.applicationContext ?: context
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                appCtx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                appCtx.registerReceiver(receiver, filter)
            }
            isSettingsReceiverRegistered = true
            Log.d(TAG, "Registered ACTION_SETTINGS_CHANGED receiver in SystemUI")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register settings receiver in SystemUI", t)
        }
    }



    fun triggerShadeClose(context: Context) {
        try {
            val sbService = context.getSystemService("statusbar")
            if (sbService != null) {
                val collapseMethod = sbService.javaClass.getMethod("collapsePanels")
                collapseMethod.invoke(sbService)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error triggering shade close", t)
        }
    }

    fun isHomeScreenTop(context: Context): Boolean {
        var homePkg = cachedHomePkg
        if (homePkg == null) {
            try {
                val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val resolveInfo = context.packageManager?.resolveActivity(homeIntent, 0)
                homePkg = resolveInfo?.activityInfo?.packageName
                if (homePkg != null) {
                    cachedHomePkg = homePkg
                }
            } catch (_: Throwable) {}
        }

        fun isLauncher(pkg: String?): Boolean {
            if (pkg.isNullOrEmpty()) return false
            if (pkg == homePkg) return true
            if (pkg == "com.miui.home" || pkg == "com.mi.android.globallauncher" || pkg == "com.miui.fliphome" || pkg.contains("launcher")) return true
            return false
        }

        // 1. Check via Miui CommonUtil.isTopActivityLauncher(context) with cached reflection
        try {
            if (!isCommonUtilMethodLookupDone) {
                isCommonUtilMethodLookupDone = true
                try {
                    val commonUtilClass = Class.forName("com.miui.systemui.util.CommonUtil", false, context.classLoader)
                    cachedCommonUtilMethod = commonUtilClass.getMethod("isTopActivityLauncher", Context::class.java)
                } catch (_: Throwable) {}
            }
            val method = cachedCommonUtilMethod
            if (method != null) {
                val result = method.invoke(null, context) as? Boolean
                if (result != null) {
                    return result
                }
            }
        } catch (_: Throwable) {}

        // 2. Check via ActivityTaskManager.getService().getFocusedRootTaskInfo() and getAllRootTaskInfos()
        try {
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val getServiceMethod = atmClass.getMethod("getService")
            val atm = getServiceMethod.invoke(null)
            if (atm != null) {
                try {
                    val getFocusedMethod = atm.javaClass.getMethod("getFocusedRootTaskInfo")
                    val focusedTask = getFocusedMethod.invoke(atm)
                    if (focusedTask != null) {
                        val topActField = focusedTask.javaClass.getField("topActivity")
                        val topAct = topActField.get(focusedTask) as? ComponentName
                        if (isLauncher(topAct?.packageName)) {
                            Log.d(TAG, "isHomeScreenTop: true via getFocusedRootTaskInfo (${topAct?.packageName})")
                            return true
                        }
                    }
                } catch (_: Throwable) {}

                try {
                    val getAllMethod = atm.javaClass.getMethod("getAllRootTaskInfos")
                    val taskInfosObj = getAllMethod.invoke(atm)
                    val taskList = when (taskInfosObj) {
                        is List<*> -> taskInfosObj
                        is Array<*> -> taskInfosObj.toList()
                        else -> null
                    }
                    if (taskList != null) {
                        for (taskInfo in taskList) {
                            if (taskInfo == null) continue
                            val isVisibleField = taskInfo.javaClass.getField("isVisible")
                            val isVisible = isVisibleField.getBoolean(taskInfo)
                            val topActivityField = taskInfo.javaClass.getField("topActivity")
                            val topActivity = topActivityField.get(taskInfo) as? ComponentName
                            if (isVisible && isLauncher(topActivity?.packageName)) {
                                Log.d(TAG, "isHomeScreenTop: true via getAllRootTaskInfos (${topActivity?.packageName})")
                                return true
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}

        // 3. Check via ActivityManagerWrapper
        try {
            val amwClass = Class.forName("com.android.systemui.shared.system.ActivityManagerWrapper", false, context.classLoader)
            val getInstanceMethod = try {
                amwClass.getMethod("getInstance")
            } catch (_: Throwable) { null }
            val sInstance = getInstanceMethod?.invoke(null) ?: run {
                val f = amwClass.getDeclaredField("sInstance")
                f.isAccessible = true
                f.get(null)
            }
            if (sInstance != null) {
                val getRunningTaskMethod = amwClass.getMethod("getRunningTask")
                val task = getRunningTaskMethod.invoke(sInstance)
                if (task != null) {
                    val isHomeMethod = try {
                        amwClass.getMethod("isHomeTask", task.javaClass)
                    } catch (_: Throwable) { null }
                    val isHome = isHomeMethod?.invoke(null, task) as? Boolean
                    if (isHome == true) {
                        Log.d(TAG, "isHomeScreenTop: true via ActivityManagerWrapper.isHomeTask")
                        return true
                    }
                    val topActivityField = task.javaClass.getField("topActivity")
                    val comp = topActivityField.get(task) as? ComponentName
                    if (isLauncher(comp?.packageName)) {
                        Log.d(TAG, "isHomeScreenTop: true via ActivityManagerWrapper topActivity pkg=${comp?.packageName}")
                        return true
                    }
                }
            }
        } catch (_: Throwable) {}

        // 4. Fallback: ActivityManager.getRunningTasks(1)
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val tasks = am?.getRunningTasks(1)
            val topPkg = tasks?.firstOrNull()?.topActivity?.packageName
            if (isLauncher(topPkg)) {
                Log.d(TAG, "isHomeScreenTop: true via ActivityManager fallback pkg=$topPkg")
                return true
            }
        } catch (_: Throwable) {}

        return false
    }

    fun notifyCloseOverHomescreen(context: Context, velocity: Float = latestFlingVelocity) {
        if (isOpeningGesture) {
            Log.d(TAG, "notifyCloseOverHomescreen: opening gesture active, skipping unlock trigger")
            return
        }
        isShadeOpen = false

        val cfg = NotificationSheetConfigHolder.sheet
        if (!cfg.launcherUnlockOnDismiss) {
            Log.d(TAG, "notifyCloseOverHomescreen: launcherUnlockOnDismiss disabled, skipping")
            return
        }

        if (currentStatusBarState != 0 || isKeyguard()) {
            Log.d(TAG, "notifyCloseOverHomescreen: keyguard active, skipping")
            return
        }

        if (isControlCenterActive()) {
            Log.d(TAG, "notifyCloseOverHomescreen: control center active or switching, skipping")
            return
        }

        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastUnlockTriggerTime < 600L) {
            Log.d(TAG, "notifyCloseOverHomescreen: debounced (within 600ms)")
            return
        }
        lastUnlockTriggerTime = now

        if (isHomeScreenCached || isHomeScreenTop(context)) {
            try {
                listOf("com.miui.home", "com.mi.android.globallauncher").forEach { pkg ->
                    try {
                        val intent = Intent("dev.joseph.hyperbounce.ACTION_TRIGGER_UNLOCK_ANIMATION").apply {
                            putExtra("EXTRA_FORCE", true)
                            putExtra("EXTRA_SOURCE", "NOTIFICATION_CENTER")
                            if (velocity > 0.0f) {
                                putExtra("EXTRA_VELOCITY", velocity)
                            }
                            setPackage(pkg)
                            addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                        }
                        context.sendBroadcast(intent)
                    } catch (_: Throwable) {}
                }
                Log.d(TAG, "Dispatched ACTION_TRIGGER_UNLOCK_ANIMATION to launcher (velocity=$velocity, forced=true)")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to send unlock broadcast", t)
            }
        } else {
            Log.d(TAG, "notifyCloseOverHomescreen: not over homescreen, skipping unlock animation")
        }
    }

    private fun applyConstraintParentParams(lp: ViewGroup.LayoutParams, container: ViewGroup) {
        try {
            val lpClass = lp.javaClass
            val fields = arrayOf("leftToLeft", "rightToRight", "topToTop", "bottomToBottom", "startToStart", "endToEnd")
            for (fieldName in fields) {
                try {
                    val f: Field = lpClass.getField(fieldName)
                    f.setInt(lp, 0) // ConstraintLayout.LayoutParams.PARENT_ID = 0
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    private fun adjustNotificationMargins(container: ViewGroup) {
        try {
            val isLandscape = container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                resetNotificationMargins(container)
                return
            }

            val cfg = NotificationSheetConfigHolder.sheet
            val nssl = getNsslView(container) ?: return

            val lp = nssl.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            val density = container.resources.displayMetrics.density

            val origStart = (nssl.getTag(TAG_ORIG_MARGIN_START) as? Int) ?: run {
                val s = lp.marginStart
                nssl.setTag(TAG_ORIG_MARGIN_START, s)
                s
            }
            val origEnd = (nssl.getTag(TAG_ORIG_MARGIN_END) as? Int) ?: run {
                val e = lp.marginEnd
                nssl.setTag(TAG_ORIG_MARGIN_END, e)
                e
            }
            val origTop = (nssl.getTag(TAG_ORIG_MARGIN_TOP) as? Int) ?: run {
                val t = lp.topMargin
                nssl.setTag(TAG_ORIG_MARGIN_TOP, t)
                t
            }
            val origBottom = (nssl.getTag(TAG_ORIG_MARGIN_BOTTOM) as? Int) ?: run {
                val b = lp.bottomMargin
                nssl.setTag(TAG_ORIG_MARGIN_BOTTOM, b)
                b
            }

            val isActive = cfg.isSheetActive && currentStatusBarState == 0 && !isControlCenterActive()
            val extraMargin = if (isActive) (cfg.sideMargin * density).toInt() else 0
            val targetStart = origStart + extraMargin
            val targetEnd = origEnd + extraMargin

            val targetTop = origTop
            val targetBottom = origBottom

            var changed = false
            if (lp.marginStart != targetStart || lp.marginEnd != targetEnd) {
                lp.marginStart = targetStart
                lp.marginEnd = targetEnd
                lp.leftMargin = targetStart
                lp.rightMargin = targetEnd
                changed = true
            }
            if (lp.topMargin != targetTop) {
                lp.topMargin = targetTop
                changed = true
            }
            if (lp.bottomMargin != targetBottom) {
                lp.bottomMargin = targetBottom
                changed = true
            }
            if (changed) {
                nssl.layoutParams = lp
            }
            if (nssl is ViewGroup) {
                val origPaddingTop = (nssl.getTag(TAG_ORIG_PADDING_TOP) as? Int) ?: run {
                    val pt = nssl.paddingTop
                    nssl.setTag(TAG_ORIG_PADDING_TOP, pt)
                    pt
                }
                val origPaddingBottom = (nssl.getTag(TAG_ORIG_PADDING_BOTTOM) as? Int) ?: run {
                    val pb = nssl.paddingBottom
                    nssl.setTag(TAG_ORIG_PADDING_BOTTOM, pb)
                    pb
                }

                if (nssl.clipBounds != null) nssl.clipBounds = null
                nssl.clipToPadding = false

                val targetPaddingTop = origPaddingTop
                val pillClearance = if (isActive) ((cfg.pillBottomMargin + cfg.pillHeight + 12f) * density).toInt() else origPaddingBottom
                val targetPaddingBottom = kotlin.math.max(origPaddingBottom, pillClearance)

                if (nssl.paddingTop != targetPaddingTop || nssl.paddingBottom != targetPaddingBottom) {
                    nssl.setPadding(nssl.paddingLeft, targetPaddingTop, nssl.paddingRight, targetPaddingBottom)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to adjust notification margins", t)
        }
    }

    private fun resetNotificationMargins(container: ViewGroup) {
        try {
            val nssl = getNsslView(container) ?: return

            if (nssl.clipBounds != null) nssl.clipBounds = null
            val lp = nssl.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            val origStart = (nssl.getTag(TAG_ORIG_MARGIN_START) as? Int) ?: lp.marginStart
            val origEnd = (nssl.getTag(TAG_ORIG_MARGIN_END) as? Int) ?: lp.marginEnd
            val origTop = (nssl.getTag(TAG_ORIG_MARGIN_TOP) as? Int) ?: lp.topMargin
            val origBottom = (nssl.getTag(TAG_ORIG_MARGIN_BOTTOM) as? Int) ?: lp.bottomMargin

            var changed = false
            if (lp.marginStart != origStart || lp.marginEnd != origEnd) {
                lp.marginStart = origStart
                lp.marginEnd = origEnd
                lp.leftMargin = origStart
                lp.rightMargin = origEnd
                changed = true
            }
            if (lp.topMargin != origTop) {
                lp.topMargin = origTop
                changed = true
            }
            if (lp.bottomMargin != origBottom) {
                lp.bottomMargin = origBottom
                changed = true
            }
            if (changed) {
                nssl.layoutParams = lp
            }
            if (nssl is ViewGroup) {
                val origPaddingTop = (nssl.getTag(TAG_ORIG_PADDING_TOP) as? Int) ?: nssl.paddingTop
                val origPaddingBottom = (nssl.getTag(TAG_ORIG_PADDING_BOTTOM) as? Int) ?: nssl.paddingBottom
                if (nssl.paddingTop != origPaddingTop || nssl.paddingBottom != origPaddingBottom) {
                    nssl.setPadding(nssl.paddingLeft, origPaddingTop, nssl.paddingRight, origPaddingBottom)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to reset notification margins", t)
        }
    }

    private fun hookFloatFlowProperty(module: XposedModule, classLoader: ClassLoader) {
        try {
            val ffpClass = Class.forName("com.miui.systemui.util.FloatFlowProperty", false, classLoader)
            for (m: Method in ffpClass.declaredMethods) {
                if (m.name == "setValue" && m.parameterTypes.size == 2 &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val callerName = chain.thisObject?.javaClass?.name ?: ""
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val fVal = (args[1] as Number).toFloat()

                                if (callerName.contains("NotificationPanelExpansionAnimator")) {
                                    if (isUnlockedShadeActive) {
                                        applyExpansionFraction(fVal)
                                    }
                                } else if (callerName.contains("NotificationPanelStretchAnimator")) {
                                    if (isUnlockedShadeActive) {
                                        applyStretchOffset(fVal)
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in FloatFlowProperty.setValue hook", t)
                            }
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook FloatFlowProperty", t)
        }
    }

    private fun hookHeaderCallback(module: XposedModule, classLoader: ClassLoader) {
        try {
            val cbClass = Class.forName(
                "com.android.systemui.controlcenter.shade.NotificationHeaderExpandController\$notificationCallback\$1",
                false,
                classLoader
            )
            for (m: Method in cbClass.declaredMethods) {
                if (m.name == "onExpansionChanged" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                if (isUnlockedShadeActive) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                    val frac = (args[0] as Number).toFloat()
                                    applyExpansionFraction(frac)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in onExpansionChanged hook", t)
                            }
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationHeaderExpandController callback", t)
        }
    }

    private fun hookShadeExpansionState(module: XposedModule, classLoader: ClassLoader) {
        try {
            val sesClass = Class.forName("com.android.systemui.shade.ShadeExpansionStateManager", false, classLoader)
            for (m: Method in sesClass.declaredMethods) {
                if (m.name == "updateStateInternal" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Int::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val state = (args[0] as Number).toInt()
                                when (state) {
                                    0 -> { // CLOSED
                                        RealtimeBackdropHelper.stopLiveRefresh()
                                        isShadeOpen = false
                                        isShadeFullyOpen = false
                                        hasFullyOpenedForCurrentSession = false
                                        hasTriggeredUnlockForCurrentOpen = true
                                        isLaunchingNotificationActivity = false
                                    }
                                    1 -> { // OPENING
                                        if (isUnlockedShadeActive) {
                                            val container = containerRef?.get()
                                            if (container != null) {
                                                updateHomeScreenCache(container.context)
                                            }
                                        }
                                    }
                                    2 -> { // OPEN (genuinely fully expanded)
                                        if (isUnlockedShadeActive) {
                                            isShadeOpen = true
                                            isShadeFullyOpen = true
                                            hasFullyOpenedForCurrentSession = true
                                            hasTriggeredUnlockForCurrentOpen = false
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in updateStateInternal hook", t)
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ShadeExpansionStateManager", t)
        }
    }

    private fun hookExpandController(module: XposedModule, classLoader: ClassLoader) {
        try {
            val expandControllerClass = Class.forName("com.android.systemui.shade.NotificationPanelExpandController", false, classLoader)
            for (c in expandControllerClass.declaredConstructors) {
                module.hook(c).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        try {
                            val ctrl = chain.thisObject
                            if (ctrl != null) {
                                expandControllerRef = WeakReference(ctrl)
                                updateExpandThreshold(ctrl)
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error in expandController constructor hook", t)
                        }
                        return result
                    }
                })
            }
            for (m: Method in expandControllerClass.declaredMethods) {
                if (m.name == "getExpandThresh" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (isOneToOneActive()) {
                                return getTargetExpandThreshold()
                            }
                            return chain.proceed()
                        }
                    })
                } else if (m.name == "onExpandFinish" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            try {
                                val ctrl = chain.thisObject
                                if (ctrl != null) {
                                    if (expandControllerRef?.get() == null) {
                                        expandControllerRef = WeakReference(ctrl)
                                    }
                                    if (isOneToOneActive()) {
                                        val thresh = getTargetExpandThreshold()
                                        val appF = ctrl.javaClass.getDeclaredField("appearance")
                                        appF.isAccessible = true
                                        val oldApp = appF.getBoolean(ctrl)

                                        val container = containerRef?.get()
                                        val density = container?.resources?.displayMetrics?.density ?: 2.75f
                                        val dy = lastTouchY - touchDownY
                                        val isPulldown = isOpeningGesture || dy > 10f * density

                                        if (isPulldown) {
                                            val isFlingDown = (latestTouchYVelocity > 120f * density || (latestFlingVelocity > 120f * density && (lastTouchDy >= 0f || dy > 15f * density)))
                                            val isSettledOpen = (lastAppliedFraction >= 0.50f || hasFullyOpenedForCurrentSession || isShadeFullyOpen)
                                            val shouldAppear = isFlingDown || isSettledOpen || (lastAppliedFraction >= 0.35f && lastTouchDy >= 0f)

                                            appF.setBoolean(ctrl, shouldAppear)

                                            if (shouldAppear) {
                                                try {
                                                    val setHMethod = ctrl.javaClass.getDeclaredMethod("setExpandHeight", Float::class.javaPrimitiveType)
                                                    setHMethod.isAccessible = true
                                                    setHMethod.invoke(ctrl, thresh)
                                                } catch (_: Throwable) {
                                                    try {
                                                        val hField = ctrl.javaClass.getDeclaredField("expandHeight")
                                                        hField.isAccessible = true
                                                        hField.setFloat(ctrl, thresh)
                                                    } catch (_: Throwable) {}
                                                }

                                                if (!oldApp) {
                                                    try {
                                                        val panelAnimF = ctrl.javaClass.getDeclaredField("panelAnimator")
                                                        panelAnimF.isAccessible = true
                                                        val panelAnim = panelAnimF.get(ctrl)
                                                        if (panelAnim != null) {
                                                            val animM = panelAnim.javaClass.getDeclaredMethod("startPanelVisibleAnimation", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                                                            animM.isAccessible = true
                                                            animM.invoke(panelAnim, true, true, true)
                                                        }
                                                    } catch (_: Throwable) {}

                                                    try {
                                                        val fakeFocusF = ctrl.javaClass.getDeclaredField("fakeFocusNotifController")
                                                        fakeFocusF.isAccessible = true
                                                        val fakeFocus = fakeFocusF.get(ctrl)
                                                        if (fakeFocus != null) {
                                                            val focusM = fakeFocus.javaClass.getDeclaredMethod("startVisibleAnimation", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                                                            focusM.isAccessible = true
                                                            focusM.invoke(fakeFocus, true, true)
                                                        }
                                                    } catch (_: Throwable) {}

                                                    try {
                                                        val notifyM = ctrl.javaClass.getDeclaredMethod("notifyAppearanceChanged", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                                                        notifyM.isAccessible = true
                                                        notifyM.invoke(ctrl, true, true)
                                                    } catch (_: Throwable) {}
                                                }
                                            } else {
                                                try {
                                                    val setHMethod = ctrl.javaClass.getDeclaredMethod("setExpandHeight", Float::class.javaPrimitiveType)
                                                    setHMethod.isAccessible = true
                                                    setHMethod.invoke(ctrl, 0.0f)
                                                } catch (_: Throwable) {
                                                    try {
                                                        val hField = ctrl.javaClass.getDeclaredField("expandHeight")
                                                        hField.isAccessible = true
                                                        hField.setFloat(ctrl, 0.0f)
                                                    } catch (_: Throwable) {}
                                                }

                                                if (oldApp) {
                                                    try {
                                                        val panelAnimF = ctrl.javaClass.getDeclaredField("panelAnimator")
                                                        panelAnimF.isAccessible = true
                                                        val panelAnim = panelAnimF.get(ctrl)
                                                        if (panelAnim != null) {
                                                            val animM = panelAnim.javaClass.getDeclaredMethod("startPanelVisibleAnimation", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                                                            animM.isAccessible = true
                                                            animM.invoke(panelAnim, false, true, true)
                                                        }
                                                    } catch (_: Throwable) {}

                                                    try {
                                                        val fakeFocusF = ctrl.javaClass.getDeclaredField("fakeFocusNotifController")
                                                        fakeFocusF.isAccessible = true
                                                        val fakeFocus = fakeFocusF.get(ctrl)
                                                        if (fakeFocus != null) {
                                                            val focusM = fakeFocus.javaClass.getDeclaredMethod("startVisibleAnimation", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                                                            focusM.isAccessible = true
                                                            focusM.invoke(fakeFocus, false, true)
                                                        }
                                                    } catch (_: Throwable) {}

                                                    try {
                                                        val notifyM = ctrl.javaClass.getDeclaredMethod("notifyAppearanceChanged", Boolean::class.javaPrimitiveType, Boolean::class.javaPrimitiveType)
                                                        notifyM.isAccessible = true
                                                        notifyM.invoke(ctrl, false, true)
                                                    } catch (_: Throwable) {}
                                                }
                                            }
                                        } else {
                                            // Closing gesture (swiping up from open shade): Let stock Xiaomi algorithm fully handle closing!
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in onExpandFinish pre-intercept", t)
                            }

                            val result = chain.proceed()
                            try {
                                val ctrl = chain.thisObject
                                if (ctrl != null) {
                                    val appF = ctrl.javaClass.getDeclaredField("appearance")
                                    appF.isAccessible = true
                                    val appearance = appF.getBoolean(ctrl)
                                    if (appearance) {
                                        if (isUnlockedShadeActive && (lastAppliedFraction >= 0.50f || hasFullyOpenedForCurrentSession || isShadeFullyOpen)) {
                                            isShadeOpen = true
                                            isShadeFullyOpen = true
                                            hasFullyOpenedForCurrentSession = true
                                            hasTriggeredUnlockForCurrentOpen = false
                                        }
                                    } else {
                                        RealtimeBackdropHelper.stopLiveRefresh()
                                        isShadeOpen = false
                                        isShadeFullyOpen = false
                                        hasFullyOpenedForCurrentSession = false
                                        hasTriggeredUnlockForCurrentOpen = true
                                    }
                                }
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                } else if (m.name == "hide" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val rawArgs = chain.args
                            val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                            val animated = args[0] as Boolean
                            try {
                                if (isUnlockedShadeActive) {
                                    isShadeFullyOpen = false
                                    if (!animated) {
                                        applyExpansionFraction(0.0f)
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in NotificationPanelExpandController.hide hook", t)
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationPanelExpandController", t)
        }
    }

    fun applyStretchOffset(stretchPx: Float) {
        val offset = stretchPx.coerceAtLeast(0f)
        if (abs(offset - currentStretchOffset) < 0.5f) return
        currentStretchOffset = offset
        val container = containerRef?.get() ?: return
        if (currentStatusBarState != 0 || isControlCenterActive()) return

        if (lastAppliedFraction >= 0.85f || isShadeOpen || hasFullyOpened) {
            val updateAction = Runnable {
                val sheet = cachedGlassSheetRef?.get() ?: container.findViewWithTag<View>(TAG_GLASS_SHEET)
                val pill = cachedHomePillRef?.get() ?: container.findViewWithTag<View>(TAG_HOME_PILL)
                // Over-pull bounce: wallpaper sheet remains pinned at 0f so top is never cut off!
                sheet?.translationY = 0f
                pill?.translationY = offset
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                updateAction.run()
            } else {
                mainHandler.post(updateAction)
            }
        }
    }

    fun animateStretchRelease() {
        val container = containerRef?.get() ?: return
        if (currentStretchOffset <= 0.01f) return
        mainHandler.post {
            val sheet = cachedGlassSheetRef?.get() ?: container.findViewWithTag<View>(TAG_GLASS_SHEET)
            val pill = cachedHomePillRef?.get() ?: container.findViewWithTag<View>(TAG_HOME_PILL)
            sheet?.translationY = 0f
            pill?.animate()
                ?.translationY(0f)
                ?.setDuration(260)
                ?.setInterpolator(DecelerateInterpolator(1.8f))
                ?.start()
            currentStretchOffset = 0f
        }
    }

    fun applyExpansionFraction(fraction: Float) {
        val f = fraction.coerceIn(0.0f, 1.0f)
        val updateBlock = Runnable {
            val prev = lastAppliedFraction
            if (abs(f - prev) < 0.001f) return@Runnable
            lastAppliedFraction = f
            applyExpansionFractionInternal(f, prev)
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            updateBlock.run()
        } else {
            mainHandler.post(updateBlock)
        }
    }

    private fun applyExpansionFractionInternal(fraction: Float, prevFraction: Float = -1f) {
        try {
            val container = containerRef?.get() ?: return
            val cfg = NotificationSheetConfigHolder.sheet
            if (!cfg.isSheetActive || currentStatusBarState != 0 || isKeyguard() || isControlCenterActive()) {
                val curSheet = cachedGlassSheetRef?.get() ?: container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)
                curSheet?.isClosingMode = false
                curSheet?.isClosingFromFullOpen = false
                curSheet?.visibility = View.GONE
                curSheet?.translationY = 0.0f
                val curPill = cachedHomePillRef?.get() ?: container.findViewWithTag<View>(TAG_HOME_PILL)
                curPill?.visibility = View.GONE
                curPill?.translationY = 0.0f
                container.translationY = 0.0f
                LockScreenCoverSheetController.onShadeReset(container)
                isShadeFullyOpen = false
                hasTriggeredUnlockForCurrentOpen = true
                isLaunchingNotificationActivity = false
                isClosingMode = false
                RealtimeBackdropHelper.stopLiveRefresh()
                return
            }

            // Prime home screen cache and adjust notification margins at gesture opening start
            if (!isShadeOpen && fraction > 0.015f) {
                isShadeOpen = true
                updateHomeScreenCache(container.context)
                adjustNotificationMargins(container)
            }

            val screenHeight = container.resources.displayMetrics.heightPixels.toFloat()
            val viewHeight = if (container.height > 0 && container.height.toFloat() >= screenHeight * 0.8f) {
                container.height.toFloat()
            } else {
                screenHeight
            }

            val sheet = cachedGlassSheetRef?.get() ?: ensureGlassSheetInjected(container)
            val pill = cachedHomePillRef?.get() ?: ensureHomePillInjected(container)

            // Stabilized closing mode: Only enter closing mode upon intentional upward swipe from full open
            val isIntentionalClosingSwipe = prevFraction > 0f && (prevFraction - fraction > 0.015f) && (isClosingMode || fraction < 0.96f)
            if ((isShadeFullyOpen || hasFullyOpenedForCurrentSession) && isIntentionalClosingSwipe) {
                isClosingMode = true
            }

            // Arming: Shade has reached full expansion (only when expanding or at rest, never while collapsing)
            if (fraction >= 0.95f && (prevFraction < 0f || fraction >= prevFraction)) {
                isClosingMode = false
                isShadeFullyOpen = true
                hasFullyOpenedForCurrentSession = true
                hasTriggeredUnlockForCurrentOpen = false
                isShadeOpen = true
            }

            if (fraction <= 0.015f) {
                val wasOpen = (isShadeFullyOpen || hasFullyOpenedForCurrentSession) && isClosingMode && !isOpeningGesture
                if (wasOpen && !hasTriggeredUnlockForCurrentOpen) {
                    hasTriggeredUnlockForCurrentOpen = true
                    val effectiveVel = if (latestTouchYVelocity < -50f) -latestTouchYVelocity else latestFlingVelocity
                    notifyCloseOverHomescreen(container.context, effectiveVel)
                }
                isClosingMode = false
                sheet?.visibility = View.GONE
                sheet?.translationY = -viewHeight
                sheet?.isClosingMode = false
                sheet?.isClosingFromFullOpen = false
                pill?.visibility = View.GONE
                pill?.translationY = -viewHeight
                LockScreenCoverSheetController.onShadeReset(container)
                isShadeFullyOpen = false
                hasFullyOpenedForCurrentSession = false
                hasTriggeredUnlockForCurrentOpen = true
                isShadeOpen = false
                isLaunchingNotificationActivity = false
                currentStretchOffset = 0f
                resetNotificationMargins(container)
                return
            }

            // Keep container translation at 0.0f; translate only the glass card and pill!
            container.translationY = 0.0f

            val upwardOffset = (fraction - 1.0f) * viewHeight
            // Smooth 120fps continuous translation: slides from -viewHeight to 0f without artificial snaps
            val finalSheetY = kotlin.math.min(0f, upwardOffset)

            val curSheet = sheet
            if (curSheet != null) {
                curSheet.visibility = View.VISIBLE
                curSheet.isClosingMode = isClosingMode
                curSheet.isClosingFromFullOpen = isClosingMode && hasFullyOpenedForCurrentSession
                curSheet.expansionFraction = fraction
                val prevY = curSheet.translationY
                curSheet.translationY = finalSheetY
                if (abs(prevY - finalSheetY) > 0.5f) {
                    curSheet.postInvalidateOnAnimation()
                }
                // Refresh backdrop continuously if glass effects are actively required
                val isLandscape = container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || container.width > container.height
                val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
                val isDepthActive = cfg.isDepthActive && !isLandscape && isHomescreenAllowed
                val isLockScreenActive = cfg.isLockScreenActive && !isLandscape && isHomescreenAllowed
                val shouldDrawWallpaper = cfg.isWallpaperActive && !isLandscape && isHomescreenAllowed
                val shouldRenderGlass = !isLandscape && cfg.isGlassEffectsActive && (!cfg.glassOnlyWithoutWallpaper || !shouldDrawWallpaper)
                if (shouldRenderGlass) {
                    RealtimeBackdropHelper.startLiveRefresh(container.context, curSheet)
                } else {
                    RealtimeBackdropHelper.stopLiveRefresh()
                }

                // Suppress background blur and dimming when wallpaper background or iOS 27 Depth Wallpaper is active without flickering
                if ((isDepthActive || shouldDrawWallpaper) && currentStatusBarState == 0) {
                    // Keep stock shade background invisible during pulldown and resting state so wallpaper displays cleanly
                    curSheet.shadeBackgroundView?.alpha = 0.0f
                    themeBackgroundViewRef?.get()?.alpha = 0.0f
                } else {
                    curSheet.shadeBackgroundView?.alpha = 1.0f
                    themeBackgroundViewRef?.get()?.alpha = 1.0f
                    if (isLandscape) {
                        val anim = blurAnimatorRef?.get()
                        if (anim != null) {
                            try {
                                for (m in anim.javaClass.declaredMethods) {
                                    if (m.name.startsWith("updateBlurRatio")) {
                                        m.isAccessible = true
                                        m.invoke(anim, fraction, false)
                                        break
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }

            val curPill = pill
            if (curPill != null) {
                curPill.visibility = View.VISIBLE
                // Pill is stably pinned to the sheet's bottom edge with stretch offset when sheet is fully open
                curPill.translationY = finalSheetY + (if (finalSheetY == 0f) currentStretchOffset else 0f)
                curPill.alpha = (fraction - 0.2f).coerceIn(0f, 1f)
            }

            val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
            val isLandscape = container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || container.width > container.height
            if (!isLandscape && cfg.isLockScreenActive && isHomescreenAllowed) {
                LockScreenCoverSheetController.onExpansionFractionChanged(container, fraction, finalSheetY, currentStretchOffset)
            } else {
                LockScreenCoverSheetController.onShadeReset(container)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in applyExpansionFractionInternal", t)
        }
    }




    private fun hookViewStateInjector(module: XposedModule, classLoader: ClassLoader) {
        try {
            val vsiClass = Class.forName("com.android.systemui.statusbar.notification.stack.ViewStateInjectorImpl", false, classLoader)

            for (m: Method in vsiClass.declaredMethods) {
                if (m.name == "setFolmeTranslationXForType" && m.parameterTypes.size == 2 &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (isUnlockedShadeActive) {
                                val args = chain.args.toTypedArray()
                                if (args.size > 1) {
                                    args[1] = 0.0f
                                    return chain.proceed(args)
                                }
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked ViewStateInjectorImpl.setFolmeTranslationXForType")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ViewStateInjectorImpl", t)
        }
    }

    private fun hookTransitionAnimatorController(module: XposedModule, classLoader: ClassLoader) {
        try {
            val controllerClass = Class.forName("com.android.systemui.statusbar.phone.StatusBarTransitionAnimatorController", false, classLoader)
            for (m: Method in controllerClass.declaredMethods) {
                if (m.name == "onTransitionAnimationStart" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (currentStatusBarState == 0) {
                                isLaunchingNotificationActivity = true
                            }
                            return chain.proceed()
                        }
                    })
                } else if (m.name == "onTransitionAnimationEnd" || m.name == "onTransitionAnimationCancelled") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            isLaunchingNotificationActivity = false
                            return chain.proceed()
                        }
                    })
                }
            }
            Log.d(TAG, "Hooked StatusBarTransitionAnimatorController successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook StatusBarTransitionAnimatorController", t)
        }
    }

    private fun hookNotificationWindowTouch(module: XposedModule, classLoader: ClassLoader) {
        val classNames = arrayOf(
            "com.android.systemui.statusbar.phone.PhoneStatusBarView",
            "com.android.systemui.shade.NotificationShadeWindowView",
            "com.android.systemui.shade.NotificationPanelView"
        )
        for (className in classNames) {
            try {
                val clazz = Class.forName(className, false, classLoader)
                for (m: Method in clazz.declaredMethods) {
                    if (m.name == "dispatchTouchEvent" && m.parameterTypes.size == 1 &&
                        m.parameterTypes[0] == MotionEvent::class.java
                    ) {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val rawArgs = chain.args
                                val event = (if (rawArgs is List<*>) rawArgs[0] else (rawArgs as Array<*>)[0]) as? MotionEvent
                                if (event != null) {
                                    handlePanelTouchEvent(event)
                                }
                                return chain.proceed()
                            }
                        })
                        Log.d(TAG, "Hooked $className.dispatchTouchEvent successfully")
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook $className: ${t.message}")
            }
        }
    }

    private fun handlePanelTouchEvent(event: MotionEvent) {
        if (!isUnlockedShadeActive || isLandscapeMode()) return
        val container = containerRef?.get() ?: return
        val density = container.resources.displayMetrics.density
        val screenW = container.resources.displayMetrics.widthPixels.toFloat()

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isUserTouchingShade = true
                touchDownX = event.rawX
                touchDownY = event.rawY
                lastTouchY = event.rawY
                lastTouchDy = 0f
                latestFlingVelocity = 0f
                latestTouchYVelocity = 0f
                isOpeningGesture = !isShadeFullyOpen || lastAppliedFraction < 0.20f || event.rawY < 150f * density
                if (isOpeningGesture) {
                    isClosingMode = false
                    hasTriggeredUnlockForCurrentOpen = true
                    hasFullyOpenedForCurrentSession = false
                    isShadeFullyOpen = false
                }
                updateExpandThreshold()

                shadeVelocityTracker?.recycle()
                shadeVelocityTracker = VelocityTracker.obtain()
                shadeVelocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> {
                isUserTouchingShade = true
                val dyFromDown = event.rawY - touchDownY
                if (dyFromDown > 15f * density) {
                    isOpeningGesture = true
                    isClosingMode = false
                    hasTriggeredUnlockForCurrentOpen = true
                } else if (dyFromDown < -20f * density && isShadeFullyOpen) {
                    isOpeningGesture = false
                    isClosingMode = true
                }
                lastTouchDy = event.rawY - lastTouchY
                lastTouchY = event.rawY
                shadeVelocityTracker?.addMovement(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasTouching = isUserTouchingShade
                isUserTouchingShade = false
                val dy = event.rawY - touchDownY
                val upDy = event.rawY - lastTouchY
                if (kotlin.math.abs(upDy) > 0.1f) {
                    lastTouchDy = upDy
                }

                var touchVelocity = 0.0f
                try {
                    shadeVelocityTracker?.addMovement(event)
                    shadeVelocityTracker?.computeCurrentVelocity(1000)
                    val yVel = shadeVelocityTracker?.yVelocity ?: 0.0f
                    latestTouchYVelocity = yVel
                    touchVelocity = kotlin.math.abs(yVel)
                    if (touchVelocity > 0.0f) {
                        latestFlingVelocity = touchVelocity
                        Log.d(TAG, "Captured touch fling velocity from VelocityTracker: $latestFlingVelocity, yVel=$yVel, dy=$lastTouchDy")
                    }
                } catch (_: Throwable) {} finally {
                    shadeVelocityTracker?.recycle()
                    shadeVelocityTracker = null
                }

                if (event.actionMasked == MotionEvent.ACTION_UP && wasTouching) {
                    val dm = container.resources.displayMetrics
                    val density = dm.density
                    val isUpwardSwipe = (dy < -15f * density) && (lastTouchDy <= 0f || latestTouchYVelocity < -50f)

                    if (!isOpeningGesture && (lastAppliedFraction >= 0.50f || hasFullyOpenedForCurrentSession)) {
                        isShadeOpen = true
                        isShadeFullyOpen = true
                        hasFullyOpenedForCurrentSession = true
                        hasTriggeredUnlockForCurrentOpen = false
                    }

                    if (!isOpeningGesture && hasFullyOpenedForCurrentSession && isShadeFullyOpen && !hasTriggeredUnlockForCurrentOpen &&
                        !isLaunchingNotificationActivity && isUpwardSwipe
                    ) {
                        isClosingMode = true
                        val curSheet = container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)
                        curSheet?.isClosingMode = true
                        curSheet?.isClosingFromFullOpen = true
                        hasTriggeredUnlockForCurrentOpen = true
                        isShadeFullyOpen = false
                        hasFullyOpenedForCurrentSession = false
                        val effectiveVel = if (touchVelocity > 0f) touchVelocity else latestFlingVelocity
                        Log.d(TAG, "ACTION_UP closing release detected across notification center (dy=$dy, touchY=$touchDownY, vel=$effectiveVel) -> triggering simultaneous unlock")
                        notifyCloseOverHomescreen(container.context, effectiveVel)
                    }
                }
            }
        }
    }

    private fun hookBlurSuppression(module: XposedModule, classLoader: ClassLoader) {
        try {
            val blurAnimClass = Class.forName("com.android.systemui.shade.NotificationPanelBlurAnimator", false, classLoader)
            for (m in blurAnimClass.declaredMethods) {
                if (m.name.startsWith("updateBlurRatio")) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            chain.thisObject?.let { blurAnimatorRef = WeakReference(it) }
                            val cfg = NotificationSheetConfigHolder.sheet
                            val container = containerRef?.get()
                            val isLandscape = container != null && (container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || container.width > container.height)
                            val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
                            val isWallpaperOrDepth = (cfg.isDepthActive || cfg.isWallpaperActive) && isHomescreenAllowed
                            if (isWallpaperOrDepth && !isLandscape && currentStatusBarState == 0 && !isControlCenterActive()) {
                                val args = chain.args.toTypedArray()
                                if (args.isNotEmpty()) {
                                    args[0] = 0.0f
                                    return chain.proceed(args)
                                }
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked NotificationPanelBlurAnimator.${m.name} successfully")
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "NotificationPanelBlurAnimator not found: ${t.message}")
        }
    }

    private fun hookFlashlightController(module: XposedModule, classLoader: ClassLoader) {
        val classes = listOf(
            "com.android.systemui.controlcenter.policy.MiuiFlashlightControllerImpl",
            "com.android.systemui.statusbar.policy.FlashlightControllerImpl"
        )
        for (clsName in classes) {
            try {
                val clazz = Class.forName(clsName, false, classLoader)
                for (ctor in clazz.declaredConstructors) {
                    module.hook(ctor).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            val inst = chain.thisObject
                            if (inst != null) {
                                miuiFlashlightController = inst
                                registerFlashlightListener(inst, classLoader)
                            }
                            return result
                        }
                    })
                }
                Log.d(TAG, "Hooked $clsName constructors successfully")
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook $clsName constructor: ${t.message}")
            }
        }
    }

    private fun registerFlashlightListener(controller: Any, classLoader: ClassLoader) {
        try {
            val listenerClass = Class.forName("com.android.systemui.statusbar.policy.FlashlightController\$FlashlightListener", false, classLoader)
            val proxy = Proxy.newProxyInstance(
                classLoader,
                arrayOf(listenerClass)
            ) { _, method, args ->
                if (method.name == "onFlashlightChanged" && args != null && args.isNotEmpty()) {
                    val enabled = args[0] as? Boolean ?: false
                    mainHandler.post {
                        LockScreenCoverSheetController.onTorchStateChanged(enabled)
                    }
                }
                null
            }
            val addCallbackMethod = controller.javaClass.getMethod("addCallback", Any::class.java)
            addCallbackMethod.invoke(controller, proxy)
            Log.d(TAG, "Registered FlashlightListener proxy on $controller")
        } catch (t: Throwable) {
            Log.d(TAG, "Failed to register FlashlightListener proxy: ${t.message}")
        }
    }

    private fun hookTopPaddingController(module: XposedModule, classLoader: ClassLoader) {
        try {
            val clazz = Class.forName("com.android.systemui.shade.NotificationTopPaddingControllerImpl", false, classLoader)
            for (m in clazz.declaredMethods) {
                if (m.name == "getTopPadding" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = NotificationSheetConfigHolder.sheet
                            val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
                            if (cfg.isLockScreenActive && isHomescreenAllowed && isUnlockedShadeActive) {
                                val container = containerRef?.get()
                                if (container != null) {
                                    val density = container.resources.displayMetrics.density
                                    val rawProceed = (chain.proceed() as? Number)?.toFloat() ?: 0f
                                    val stockTopPadding = if (rawProceed > 50f * density) rawProceed else (110f * density)
                                    if (cfg.notifStartMarginTop <= 0.01f) {
                                        return stockTopPadding
                                    }
                                    return stockTopPadding + (cfg.notifStartMarginTop * density)
                                }
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked NotificationTopPaddingControllerImpl.getTopPadding successfully")
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "NotificationTopPaddingControllerImpl not found: ${t.message}")
        }
    }

    private fun hookKeyguardNotificationState(module: XposedModule, classLoader: ClassLoader) {
        val classes = listOf(
            "com.android.keyguard.panel.KeyguardPanelViewController",
            "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutControllerInjectorImpl"
        )
        for (clsName in classes) {
            try {
                val clazz = Class.forName(clsName, false, classLoader)
                for (m in clazz.declaredMethods) {
                    if (m.name == "onUpdateNotificationState" && m.parameterTypes.isEmpty()) {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val result = chain.proceed()
                                val cfg = NotificationSheetConfigHolder.sheet
                                val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
                                if (cfg.isLockScreenActive && isHomescreenAllowed && isUnlockedShadeActive) {
                                    if (!LockScreenCoverSheetController.isBigClockSynced) {
                                        val container = containerRef?.get()
                                        if (container != null) {
                                            mainHandler.post {
                                                LockScreenCoverSheetController.syncClockWithNotifications(container)
                                                container.findViewWithTag<View>(TAG_GLASS_SHEET)?.invalidate()
                                            }
                                        }
                                    }
                                }
                                return result
                            }
                        })
                        Log.d(TAG, "Hooked $clsName.onUpdateNotificationState successfully")
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook $clsName.onUpdateNotificationState: ${t.message}")
            }
        }
    }

    private fun hookEmptySpaceClick(module: XposedModule, classLoader: ClassLoader) {
        val targetClasses = listOf(
            "com.android.systemui.shade.NotificationPanelViewController",
            "com.android.systemui.shade.MiuiNotificationPanelViewController",
            "com.android.systemui.statusbar.phone.NotificationPanelViewController",
            "com.android.systemui.statusbar.phone.MiuiNotificationPanelViewController",
            "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout",
            "com.android.systemui.statusbar.notification.stack.MiuiNotificationStackScrollLayout"
        )
        for (clsName in targetClasses) {
            try {
                val clazz = Class.forName(clsName, false, classLoader)
                for (m: Method in clazz.declaredMethods) {
                    when {
                        m.name == "onEmptySpaceClick" && m.parameterTypes.isEmpty() -> {
                            module.hook(m).intercept(object : XposedInterface.Hooker {
                                override fun intercept(chain: XposedInterface.Chain): Any? {
                                    val cfg = NotificationSheetConfigHolder.sheet
                                    val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
                                    val isImageBgActive = cfg.isWallpaperActive && isHomescreenAllowed
                                    if (isImageBgActive && isUnlockedShadeActive) {
                                        Log.d(TAG, "Suppressed ${clazz.simpleName}.onEmptySpaceClick because image background is active")
                                        return null
                                    }
                                    return chain.proceed()
                                }
                            })
                            Log.d(TAG, "Hooked $clsName.onEmptySpaceClick successfully")
                        }
                        m.name == "handleEmptySpaceClick" && m.parameterTypes.size == 1 &&
                        MotionEvent::class.java.isAssignableFrom(m.parameterTypes[0]) -> {
                            module.hook(m).intercept(object : XposedInterface.Hooker {
                                override fun intercept(chain: XposedInterface.Chain): Any? {
                                    val cfg = NotificationSheetConfigHolder.sheet
                                    val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
                                    val isImageBgActive = cfg.isWallpaperActive && isHomescreenAllowed
                                    if (isImageBgActive && isUnlockedShadeActive) {
                                        Log.d(TAG, "Suppressed ${clazz.simpleName}.handleEmptySpaceClick because image background is active")
                                        return null
                                    }
                                    return chain.proceed()
                                }
                            })
                            Log.d(TAG, "Hooked $clsName.handleEmptySpaceClick successfully")
                        }
                        (m.name == "setOnEmptySpaceClickListener" || m.name == "setEmptySpaceClickListener") && m.parameterTypes.size == 1 -> {
                            module.hook(m).intercept(object : XposedInterface.Hooker {
                                override fun intercept(chain: XposedInterface.Chain): Any? {
                                    val rawArgs = chain.args
                                    val listener = if (rawArgs is List<*>) rawArgs[0] else (rawArgs as Array<*>)[0]
                                    if (listener != null) {
                                        try {
                                            for (lm in listener.javaClass.declaredMethods) {
                                                if (lm.name == "onEmptySpaceClick") {
                                                    module.hook(lm).intercept(object : XposedInterface.Hooker {
                                                        override fun intercept(lChain: XposedInterface.Chain): Any? {
                                                            val cfg = NotificationSheetConfigHolder.sheet
                                                            val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
                                                            val isImageBgActive = cfg.isWallpaperActive && isHomescreenAllowed
                                                            if (isImageBgActive && isUnlockedShadeActive) {
                                                                Log.d(TAG, "Suppressed dynamic empty space click listener ${listener.javaClass.name}.${lm.name}")
                                                                return if (lm.returnType == Boolean::class.javaPrimitiveType) false else null
                                                            }
                                                            return lChain.proceed()
                                                        }
                                                    })
                                                }
                                            }
                                        } catch (_: Throwable) {}
                                    }
                                    return chain.proceed()
                                }
                            })
                            Log.d(TAG, "Hooked $clsName.${m.name} successfully")
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook $clsName for empty space clicks: ${t.message}")
            }
        }
    }

    private fun hookNotificationPanelExpansionAnimator(module: XposedModule, classLoader: ClassLoader) {
        try {
            val animClass = Class.forName("com.android.systemui.shade.NotificationPanelExpansionAnimator", false, classLoader)
            for (m: Method in animClass.declaredMethods) {
                if (m.name.startsWith("updateExpansion") && m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType &&
                    m.parameterTypes[1] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (isOneToOneActive() && isUserTouchingShade) {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else (rawArgs as Array<Any?>).clone()
                                args[1] = false // setTo instead of to: zero spring oscillation while tracking finger
                                return chain.proceed(args)
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked NotificationPanelExpansionAnimator.${m.name}")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationPanelExpansionAnimator", t)
        }
    }

    private fun hookMiuiPanelAnimValueObject(module: XposedModule, classLoader: ClassLoader) {
        try {
            val animVoClass = Class.forName("com.android.systemui.shade.MiuiPanelAnimValueObjectImpl", false, classLoader)
            for (m: Method in animVoClass.declaredMethods) {
                if (m.name == "changeExpand" && m.parameterTypes.size >= 5 &&
                    m.parameterTypes[4] == Float::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (isOneToOneActive()) {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else (rawArgs as Array<Any?>).clone()
                                val f = (args[4] as? Number)?.toFloat() ?: 0f
                                val screenH = getTargetExpandThreshold()
                                if (screenH > 0f) {
                                    val remapped = if (f <= screenH) {
                                        (f / screenH) * 200.0f
                                    } else {
                                        200.0f + (f - screenH)
                                    }
                                    args[4] = remapped
                                    return chain.proceed(args)
                                }
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked MiuiPanelAnimValueObjectImpl.changeExpand")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook MiuiPanelAnimValueObjectImpl", t)
        }
    }

    private fun hookFakeFocusNotifController(module: XposedModule, classLoader: ClassLoader) {
        try {
            val fakeFocusClass = Class.forName("com.android.systemui.statusbar.notification.policy.FakeFocusNotifControllerImpl", false, classLoader)
            for (m: Method in fakeFocusClass.declaredMethods) {
                if (m.name == "startExpandAnimation" && m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType &&
                    m.parameterTypes[1] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (isOneToOneActive()) {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else (rawArgs as Array<Any?>).clone()
                                val f = (args[0] as? Number)?.toFloat() ?: 0f
                                val screenH = getTargetExpandThreshold()
                                if (screenH > 0f) {
                                    val remapped = if (f <= screenH) {
                                        (f / screenH) * 200.0f
                                    } else {
                                        200.0f + (f - screenH)
                                    }
                                    args[0] = remapped
                                    return chain.proceed(args)
                                }
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked FakeFocusNotifControllerImpl.startExpandAnimation")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook FakeFocusNotifControllerImpl", t)
        }
    }

    private var cachedInjectorContextField: Field? = null
    private var cachedInjectorViewField: Field? = null
    private var cachedExpandableBgNormalField: Field? = null
    private var cachedSetCustomBgMethod: Method? = null
    private var cachedMatBgResId: Int = 0

    private fun getInjectorContext(injector: Any?): Context? {
        if (injector == null) return null
        try {
            val fCtx = cachedInjectorContextField ?: run {
                injector.javaClass.getField("context").also { cachedInjectorContextField = it }
            }
            val ctx = fCtx.get(injector) as? Context
            if (ctx != null) return ctx
        } catch (_: Throwable) {}
        try {
            val fView = cachedInjectorViewField ?: run {
                injector.javaClass.getField("view").also { cachedInjectorViewField = it }
            }
            val v = fView.get(injector) as? View
            if (v != null) return v.context
        } catch (_: Throwable) {}
        return null
    }

    @Volatile
    private var cachedAdvancedTexturesOn: Boolean? = null
    @Volatile
    private var isObserverRegistered = false

    private fun registerAdvancedTexturesObserver(context: Context) {
        if (isObserverRegistered) return
        synchronized(this) {
            if (isObserverRegistered) return
            val appContext = context.applicationContext ?: context
            try {
                val uri = Settings.Secure.getUriFor("background_blur_enable")
                val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        try {
                            cachedAdvancedTexturesOn = Settings.Secure.getInt(appContext.contentResolver, "background_blur_enable", 0) == 1
                        } catch (_: Throwable) {}
                    }
                }
                appContext.contentResolver.registerContentObserver(uri, false, observer)
                isObserverRegistered = true
                cachedAdvancedTexturesOn = Settings.Secure.getInt(appContext.contentResolver, "background_blur_enable", 0) == 1
            } catch (_: Throwable) {}
        }
    }

    private fun isAdvancedTexturesOn(context: Context?): Boolean {
        cachedAdvancedTexturesOn?.let { return it }

        val ctx = context ?: containerRef?.get()?.context ?: return false
        registerAdvancedTexturesObserver(ctx)
        return cachedAdvancedTexturesOn ?: try {
            Settings.Secure.getInt(ctx.contentResolver, "background_blur_enable", 0) == 1
        } catch (_: Throwable) {
            false
        }
    }

    private fun hookNotificationCardBlurSuppression(module: XposedModule, classLoader: ClassLoader) {
        // Point 1: ExpandableNotificationRowInjector.updateBlurBg(int r13, int r14, boolean r15)
        // Strictly conditioned on HyperOS Advanced Textures (background_blur_enable == 1)
        try {
            val enriClass = Class.forName("com.android.systemui.statusbar.notification.row.ExpandableNotificationRowInjector", false, classLoader)
            for (m in enriClass.declaredMethods) {
                if (m.name == "updateBlurBg" && m.parameterTypes.size == 3 &&
                    m.parameterTypes[0] == Int::class.javaPrimitiveType &&
                    m.parameterTypes[1] == Int::class.javaPrimitiveType &&
                    m.parameterTypes[2] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = NotificationSheetConfigHolder.sheet
                            if (cfg.isSheetActive) {
                                val ctx = getInjectorContext(chain.thisObject)
                                if (isAdvancedTexturesOn(ctx)) {
                                    val args = chain.args.toTypedArray()
                                    if (args.size > 2) {
                                        args[2] = false
                                        return chain.proceed(args)
                                    }
                                }
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked ExpandableNotificationRowInjector.updateBlurBg successfully")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ExpandableNotificationRowInjector.updateBlurBg: ${t.message}")
        }

        // Point 2: ActivatableNotificationViewInjector.initBackground()
        // Strictly conditioned on HyperOS Advanced Textures (background_blur_enable == 1)
        try {
            val anviClass = Class.forName("com.android.systemui.statusbar.notification.row.ActivatableNotificationViewInjector", false, classLoader)
            for (m in anviClass.declaredMethods) {
                if (m.name == "initBackground" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            val cfg = NotificationSheetConfigHolder.sheet
                            if (cfg.isSheetActive) {
                                try {
                                    val injector = chain.thisObject ?: return result
                                    val ctx = getInjectorContext(injector) ?: containerRef?.get()?.context
                                    if (isAdvancedTexturesOn(ctx)) {
                                        val viewField = cachedInjectorViewField ?: run {
                                            injector.javaClass.getField("view").also { cachedInjectorViewField = it }
                                        }
                                        val expandableView = viewField.get(injector)
                                        if (expandableView != null) {
                                            val bgNormalField = cachedExpandableBgNormalField ?: run {
                                                expandableView.javaClass.getField("mBackgroundNormal").also { cachedExpandableBgNormalField = it }
                                            }
                                            val bgNormal = bgNormalField.get(expandableView)
                                            if (bgNormal != null && ctx != null) {
                                                if (cachedMatBgResId == 0) {
                                                    cachedMatBgResId = ctx.resources.getIdentifier("notification_material_bg", "drawable", "com.android.systemui")
                                                }
                                                if (cachedMatBgResId != 0) {
                                                    val setCustomBgMethod = cachedSetCustomBgMethod ?: run {
                                                        bgNormal.javaClass.getMethod("setCustomBackground", Int::class.javaPrimitiveType).also { cachedSetCustomBgMethod = it }
                                                    }
                                                    setCustomBgMethod.invoke(bgNormal, cachedMatBgResId)
                                                }
                                            }
                                        }
                                    }
                                } catch (_: Throwable) {}
                            }
                            return result
                        }
                    })
                    Log.d(TAG, "Hooked ActivatableNotificationViewInjector.initBackground successfully")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ActivatableNotificationViewInjector.initBackground: ${t.message}")
        }

        // Point 3: MiuiMediaViewControllerImpl.updateMediaBackground()
        // Prevents transparent media player card when HyperOS Advanced Textures (background_blur_enable) is active
        try {
            val mmvcClass = Class.forName("com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl", false, classLoader)
            for (m in mmvcClass.declaredMethods) {
                if (m.name == "updateMediaBackground" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            val cfg = NotificationSheetConfigHolder.sheet
                            if (cfg.isSheetActive) {
                                try {
                                    val controller = chain.thisObject ?: return result
                                    var ctx: Context? = null
                                    try {
                                        val fContext = controller.javaClass.getDeclaredField("context").apply { isAccessible = true }
                                        ctx = fContext.get(controller) as? Context
                                    } catch (_: Throwable) {}
                                    if (ctx == null) ctx = containerRef?.get()?.context

                                    if (isAdvancedTexturesOn(ctx) && ctx != null) {
                                        val fHolder = controller.javaClass.getDeclaredField("holder").apply { isAccessible = true }
                                        val holder = fHolder.get(controller)
                                        if (holder != null) {
                                            val fMediaBg = holder.javaClass.getDeclaredField("mediaBg").apply { isAccessible = true }
                                            val mediaBg = fMediaBg.get(holder) as? View
                                            if (mediaBg != null) {
                                                // Clear blend color compat
                                                try {
                                                    val miBlurCompatClass = Class.forName("com.miui.systemui.util.MiBlurCompat", false, classLoader)
                                                    val clearMethod = miBlurCompatClass.getMethod("clearMiBackgroundBlendColorCompat", View::class.java)
                                                    clearMethod.invoke(null, mediaBg)
                                                } catch (_: Throwable) {
                                                    try {
                                                        val clearMethod = mediaBg.javaClass.getMethod("clearMiBackgroundBlendColor")
                                                        clearMethod.invoke(mediaBg)
                                                    } catch (_: Throwable) {}
                                                }

                                                // Check if in Full AOD
                                                var isFullAod = false
                                                try {
                                                    val aodClass = Class.forName("com.android.systemui.statusbar.notification.fullaod.NotifiFullAodController", false, classLoader)
                                                    val enableFullAodField = aodClass.getDeclaredField("mEnableFullAod").apply { isAccessible = true }
                                                    isFullAod = enableFullAodField.getBoolean(null)
                                                } catch (_: Throwable) {}

                                                // Set solid media background
                                                val bgResName = if (isFullAod) "notification_media_fullaod_item_bg" else "notification_media_item_bg"
                                                val bgResId = ctx.resources.getIdentifier(bgResName, "drawable", "com.android.systemui")
                                                if (bgResId != 0) {
                                                    mediaBg.setBackground(ctx.getDrawable(bgResId))
                                                } else {
                                                    val fallbackId = ctx.resources.getIdentifier("notification_material_bg", "drawable", "com.android.systemui")
                                                    if (fallbackId != 0) {
                                                        mediaBg.setBackground(ctx.getDrawable(fallbackId))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Failed to apply solid background to media player: ${t.message}")
                                }
                            }
                            return result
                        }
                    })
                    Log.d(TAG, "Hooked MiuiMediaViewControllerImpl.updateMediaBackground successfully")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook MiuiMediaViewControllerImpl.updateMediaBackground: ${t.message}")
        }
    }

    private fun hookAnimationHelper(module: XposedModule, classLoader: ClassLoader) {
        try {
            val clazz = Class.forName("com.android.keyguard.clock.animation.AnimationHelper", false, classLoader)
            for (m in clazz.declaredMethods) {
                if (m.name == "doAnimationToNotification" && m.parameterTypes.size == 2) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = NotificationSheetConfigHolder.sheet
                            val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
                            // Enforce big clock ONLY when our NC feature is active AND shade is pulled down while unlocked!
                            // On real lock screen (isUnlockedShadeActive is false), let stock HyperOS run completely untouched!
                            if (cfg.isLockScreenActive && isHomescreenAllowed && isUnlockedShadeActive) {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                args[0] = false
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked AnimationHelper.doAnimationToNotification successfully")
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "AnimationHelper not found or failed to hook: ${t.message}")
        }
    }

    private var cachedScrimPaddingField: Field? = null
    private fun setScrimPadding(algorithm: Any, value: Float) {
        try {
            if (cachedScrimPaddingField == null) {
                var curr: Class<*>? = algorithm.javaClass
                while (curr != null) {
                    try {
                        cachedScrimPaddingField = curr.getDeclaredField("mNotificationScrimPadding").apply { isAccessible = true }
                        break
                    } catch (_: Throwable) {
                        curr = curr.superclass
                    }
                }
            }
            cachedScrimPaddingField?.setFloat(algorithm, value)
        } catch (_: Throwable) {}
    }

    private fun hookStackScrollAlgorithm(module: XposedModule, classLoader: ClassLoader) {
        val targetClasses = listOf(
            "com.android.systemui.statusbar.notification.stack.StackScrollAlgorithm",
            "com.android.systemui.statusbar.notification.stack.MiuiStackScrollAlgorithm"
        )
        for (clsName in targetClasses) {
            try {
                val clazz = Class.forName(clsName, false, classLoader)
                for (m in clazz.declaredMethods) {
                    if (m.name == "resetViewStates" && m.parameterTypes.size == 2) {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val thisObject = chain.thisObject ?: return chain.proceed()
                                val args = chain.args
                                val rawAmbientState = if (args is List<*>) args[0] else (args as Array<*>)[0]

                                val cfg = NotificationSheetConfigHolder.sheet
                                val isHomescreenAllowed = !cfg.lockWallpaperHomescreenOnly || isHomeScreenCached
                                val isLockScreenNcActive = cfg.isLockScreenActive && isHomescreenAllowed && isUnlockedShadeActive

                                // Run stock algorithm pass (mTopPadding already set cleanly by hookTopPaddingController)
                                val result = chain.proceed()

                                // 2. iOS-Style Stacked Notifications Transformation (Post-resetViewStates)
                                if (cfg.isSheetActive && cfg.notifStackEnabled && isUnlockedShadeActive) {
                                    try {
                                        NotificationStackScrollerHelper.applyStackAlgorithm(thisObject, rawAmbientState)
                                    } catch (t: Throwable) {
                                        Log.e(TAG, "Error applying stacked notifications", t)
                                    }
                                } else {
                                    NotificationStackScrollerHelper.extraStackScrollRange = 0
                                }

                                return result
                            }
                        })
                        Log.d(TAG, "Hooked $clsName.resetViewStates successfully")
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook $clsName.resetViewStates: ${t.message}")
            }
        }
    }

    private fun hookStackScrollLayoutRange(module: XposedModule, classLoader: ClassLoader) {
        val targetClasses = listOf(
            "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout",
            "com.android.systemui.statusbar.notification.stack.MiuiNotificationStackScrollLayout"
        )
        for (clsName in targetClasses) {
            try {
                val clazz = Class.forName(clsName, false, classLoader)
                for (m in clazz.declaredMethods) {
                    if (m.name == "getScrollRange" && m.parameterTypes.isEmpty()) {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val origRange = chain.proceed() as? Int ?: 0
                                val cfg = NotificationSheetConfigHolder.sheet
                                val extra = NotificationStackScrollerHelper.extraStackScrollRange
                                if (cfg.isSheetActive && cfg.notifStackEnabled && isUnlockedShadeActive && extra > 0) {
                                    return origRange + extra
                                }
                                return origRange
                            }
                        })
                        Log.d(TAG, "Hooked $clsName.getScrollRange successfully")
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook $clsName.getScrollRange: ${t.message}")
            }
        }
    }
}

