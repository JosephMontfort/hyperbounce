package dev.joseph.hyperbounce.notification

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.animation.DecelerateInterpolator
import android.hardware.camera2.CameraManager
import dev.joseph.hyperbounce.notification.glass.DepthWallpaperHelper
import dev.joseph.hyperbounce.notification.glass.NotificationGlassSheetView
import dev.joseph.hyperbounce.notification.glass.RealtimeBackdropHelper
import dev.joseph.hyperbounce.notification.lockscreen.LockScreenBottomAreaView
import dev.joseph.hyperbounce.notification.lockscreen.LockScreenCoverSheetController
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
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

    @Volatile
    var currentStatusBarState: Int = 1 // 0 = SHADE, 1 = KEYGUARD, 2 = SHADE_LOCKED

    @Volatile
    private var isShadeFullyOpen = false

    @Volatile
    private var hasFullyOpenedForCurrentSession = false

    @Volatile
    private var isUserTouchingShade = false

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
    private val mainHandler = Handler(Looper.getMainLooper())

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

    fun toggleFlashlight(context: Context) {
        val ctrl = getMiuiFlashlightControllerInstance()
        if (ctrl != null) {
            try {
                val isEnabledMethod = ctrl.javaClass.getMethod("isEnabled")
                val isEnabled = isEnabledMethod.invoke(ctrl) as? Boolean ?: false
                val setFlashlightMethod = ctrl.javaClass.getMethod("setFlashlight", Boolean::class.javaPrimitiveType)
                setFlashlightMethod.invoke(ctrl, !isEnabled)
                return
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
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Fallback CameraManager toggle torch failed", t)
        }
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

    fun updateHomeScreenCache(context: Context?) {
        if (context == null) return
        try {
            isHomeScreenCached = isHomeScreenTop(context)
        } catch (_: Throwable) {}
    }

    fun isKeyguard(context: Context?): Boolean {
        if (currentStatusBarState != 0) return true
        if (context != null) {
            try {
                val km = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
                if (km?.isKeyguardLocked == true) return true
            } catch (_: Throwable) {}
        }
        return false
    }

    fun isControlCenterActive(): Boolean {
        if (isShadeSwitchingToNotification) return false
        if (isShadeSwitchingToControlCenter) return true
        val cc = controlCenterRef?.get()
        if (cc != null) {
            try {
                val method = cc.javaClass.getMethod("getExpanded")
                val expanded = method.invoke(cc) as? Boolean
                if (expanded != null) {
                    isControlCenterExpanded = expanded
                    return expanded
                }
            } catch (_: Throwable) {}
        }
        return isControlCenterExpanded
    }

    private val isUnlockedShadeActive: Boolean
        get() {
            val cfg = NotificationSheetConfigHolder.sheet
            return cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()
        }

    fun init(module: XposedModule, classLoader: ClassLoader) {
        // 0. Crash prevention: Hook NotificationWakeUpCoordinator to prevent early NPE before stackScrollerController is injected
        hookWakeUpCoordinator(module, classLoader)

        // 1. Hook StatusBarStateControllerImpl to track active shade / lockscreen state
        hookStatusBarState(module, classLoader)

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

        // 15. Hook NotificationPanelViewController.setExpandedHeight for 1:1 real-time drag expansion
        hookPanelExpandedHeight(module, classLoader)
    }

    private fun hookWakeUpCoordinator(module: XposedModule, classLoader: ClassLoader) {
        try {
            val nwcClass = Class.forName("com.android.systemui.statusbar.notification.NotificationWakeUpCoordinator", false, classLoader)
            var stackField: Field? = null
            try {
                stackField = nwcClass.getDeclaredField("stackScrollerController")
                stackField.isAccessible = true
            } catch (_: Throwable) {}

            val guardedMethods = setOf("updateDozeAmount", "updateHideAmount", "notifyAnimationStart", "setWakingUp", "setHardDozeAmountOverride")
            for (m in nwcClass.declaredMethods) {
                if (m.name in guardedMethods) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val inst = chain.thisObject
                            if (inst != null && stackField != null) {
                                val controller = stackField.get(inst)
                                if (controller == null) {
                                    // stackScrollerController has not yet been injected by NotificationPanelViewController.
                                    // Return early to prevent NPE during early SystemUI startup.
                                    return null
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
            container.findViewWithTag<View>(TAG_GLASS_SHEET)?.visibility = View.GONE
            container.findViewWithTag<View>(TAG_HOME_PILL)?.visibility = View.GONE
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
            if (!cfg.enabled || currentStatusBarState != 0 || isKeyguard(container.context) || isControlCenterActive()) {
                return@post
            }
            ensureGlassSheetInjected(container)
            ensureHomePillInjected(container)
            val sheet = container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)
            val pill = container.findViewWithTag<View>(TAG_HOME_PILL)
            sheet?.visibility = View.VISIBLE
            sheet?.expansionFraction = 1.0f
            sheet?.translationY = 0.0f
            sheet?.isClosingFromFullOpen = false
            sheet?.postInvalidateOnAnimation()
            pill?.visibility = View.VISIBLE
            pill?.translationY = 0.0f
            if (cfg.lockScreenAsNotifCenterEnabled) {
                LockScreenCoverSheetController.onShadeFullyOpened(container)
            } else {
                LockScreenCoverSheetController.onShadeReset(container)
            }
            if (cfg.depthWallpaperEnabled && sheet != null) {
                RealtimeBackdropHelper.startLiveRefresh(container.context, sheet)
            }
        }
    }

    private fun suppressNotificationBlur() {
        val cfg = NotificationSheetConfigHolder.sheet
        if (cfg.enabled && cfg.depthWallpaperEnabled && currentStatusBarState == 0 && !isControlCenterActive()) {
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

                                if (newState != 0) {
                                    isShadeOpen = false
                                    isShadeFullyOpen = false
                                    hasFullyOpenedForCurrentSession = false
                                    hasTriggeredUnlockForCurrentOpen = true
                                    isControlCenterExpanded = false
                                    isShadeSwitching = false
                                    isShadeSwitchingToControlCenter = false
                                    isShadeSwitchingToNotification = false
                                    lastAppliedFraction = 0f
                                    currentStretchOffset = 0f
                                    mainHandler.post {
                                        val container = containerRef?.get()
                                        if (container != null) {
                                            container.findViewWithTag<View>(TAG_GLASS_SHEET)?.visibility = View.GONE
                                            container.findViewWithTag<View>(TAG_HOME_PILL)?.visibility = View.GONE
                                            // Reset container translation so lockscreen/keyguard starts at 0.0f
                                            container.translationY = 0.0f
                                            container.scaleX = 1.0f
                                            container.scaleY = 1.0f
                                            resetNotificationMargins(container)
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
                    "switchToControlCenter" -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
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
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in NotificationPanelViewController.onFinishInflate hook", t)
                            }
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationPanelViewController.onFinishInflate", t)
        }
    }

    private fun onContainerAcquired(container: ViewGroup) {
        containerRef = WeakReference(container)
        mainHandler.post {
            registerSettingsReceiver(container.context)
            // Keep container clip settings stock
            container.clipChildren = false
            container.clipToPadding = false

            val nsslId = container.resources.getIdentifier("notification_stack_scroller", "id", "com.android.systemui")
            val nssl = if (nsslId != 0) container.findViewById<ViewGroup>(nsslId) else null
            if (nssl != null) {
                nssl.visibility = View.VISIBLE
            }

            if (currentStatusBarState != 0 || isKeyguard(container.context) || isControlCenterActive()) {
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

    private fun ensureGlassSheetInjected(container: ViewGroup) {
        try {
            val cfg = NotificationSheetConfigHolder.sheet
            var sheet = container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)

            if (!cfg.enabled || currentStatusBarState != 0 || isKeyguard(container.context) || isControlCenterActive()) {
                sheet?.visibility = View.GONE
                return
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

            // Bind the real-time blurred shade background layer (ShadeBackgroundView)
            if (sheet.shadeBackgroundView == null) {
                val shadeBgId = container.resources.getIdentifier("shade_background", "id", "com.android.systemui")
                if (shadeBgId != 0) {
                    var p: ViewParent? = container.parent
                    while (p != null) {
                        if (p is ViewGroup) {
                            val bg = p.findViewById<View>(shadeBgId)
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
                val themeBgId = container.resources.getIdentifier("theme_background", "id", "com.android.systemui")
                if (themeBgId != 0) {
                    var p: ViewParent? = container.parent
                    while (p != null) {
                        if (p is ViewGroup) {
                            val bg = p.findViewById<View>(themeBgId)
                            if (bg != null) {
                                themeBackgroundViewRef = WeakReference(bg)
                                break
                            }
                        }
                        p = p.parent
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to inject NotificationGlassSheetView", t)
        }
    }

    private fun ensureHomePillInjected(container: ViewGroup) {
        try {
            val cfg = NotificationSheetConfigHolder.sheet
            var pillWrapper = container.findViewWithTag<ViewGroup>(TAG_HOME_PILL)

            if (!cfg.enabled || currentStatusBarState != 0 || isKeyguard(container.context) || isControlCenterActive()) {
                pillWrapper?.visibility = View.GONE
                return
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
                Log.d(TAG, "Injected home indicator pill successfully")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to inject home pill", t)
        }
    }

    private var isSettingsReceiverRegistered = false

    private fun registerSettingsReceiver(context: Context) {
        if (isSettingsReceiverRegistered) return
        try {
            val filter = android.content.IntentFilter("dev.joseph.hyperbounce.ACTION_SETTINGS_CHANGED").apply {
                addAction("dev.joseph.hyperbounce.ACTION_REFRESH_DEPTH_WALLPAPER")
            }
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: android.content.Intent?) {
                    if (intent?.action == "dev.joseph.hyperbounce.ACTION_REFRESH_DEPTH_WALLPAPER") {
                        DepthWallpaperHelper.clearCache(c ?: context)
                        DepthWallpaperHelper.triggerAsyncSubjectGeneration(c ?: context, forceRefresh = true) {
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
                        "notif_sheet_enabled" -> cfg.enabled = intent.getBooleanExtra("value_boolean", cfg.enabled)
                        "notif_sheet_lock_wallpaper_enabled" -> cfg.lockWallpaperEnabled = intent.getBooleanExtra("value_boolean", cfg.lockWallpaperEnabled)
                        "notif_sheet_lock_wallpaper_homescreen_only" -> cfg.lockWallpaperHomescreenOnly = intent.getBooleanExtra("value_boolean", cfg.lockWallpaperHomescreenOnly)
                        "notif_sheet_depth_wallpaper_enabled" -> {
                            cfg.depthWallpaperEnabled = intent.getBooleanExtra("value_boolean", cfg.depthWallpaperEnabled)
                            if (cfg.depthWallpaperEnabled) {
                                if (cfg.depthCustomSubject || DepthWallpaperHelper.hasCustomSubject(c ?: context)) {
                                    Log.d(TAG, "Preserving existing custom depth subject")
                                } else {
                                    DepthWallpaperHelper.triggerAsyncSubjectGeneration(c ?: context)
                                }
                            }
                        }
                        "notif_sheet_depth_custom_subject" -> {
                            cfg.depthCustomSubject = intent.getBooleanExtra("value_boolean", cfg.depthCustomSubject)
                            DepthWallpaperHelper.clearCache(c ?: context)
                            DepthWallpaperHelper.triggerAsyncSubjectGeneration(c ?: context, forceRefresh = true) {
                                mainHandler.post {
                                    containerRef?.get()?.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)?.postInvalidateOnAnimation()
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
                        else -> handled = false
                    }
                    if (handled) {
                        mainHandler.post {
                            val container = containerRef?.get() ?: return@post
                            adjustNotificationMargins(container)
                            if (!cfg.glassEffectsEnabled) {
                                RealtimeBackdropHelper.stopLiveRefresh()
                            }
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
        var homePkg: String? = null
        try {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = context.packageManager?.resolveActivity(homeIntent, 0)
            homePkg = resolveInfo?.activityInfo?.packageName
        } catch (_: Throwable) {}

        fun isLauncher(pkg: String?): Boolean {
            if (pkg.isNullOrEmpty()) return false
            if (pkg == homePkg) return true
            if (pkg == "com.miui.home" || pkg == "com.mi.android.globallauncher" || pkg == "com.miui.fliphome" || pkg.contains("launcher")) return true
            return false
        }

        // 1. Check via Miui CommonUtil.isTopActivityLauncher(context)
        try {
            val commonUtilClass = Class.forName("com.miui.systemui.util.CommonUtil", false, context.classLoader)
            val method = commonUtilClass.getMethod("isTopActivityLauncher", Context::class.java)
            val result = method.invoke(null, context) as? Boolean
            if (result == true) {
                Log.d(TAG, "isHomeScreenTop: true via CommonUtil")
                return true
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

    fun notifyCloseOverHomescreen(context: Context) {
        isShadeOpen = false

        if (currentStatusBarState != 0 || isKeyguard(context)) {
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
                // Send ONLY ONE targeted broadcast to the active launcher package to prevent double-firing
                val intent = Intent("dev.joseph.hyperbounce.ACTION_TRIGGER_UNLOCK_ANIMATION")
                val pm = context.packageManager
                val isMiuiHome = try {
                    pm.getPackageInfo("com.miui.home", 0) != null
                } catch (_: Throwable) { false }
                val isGlobalLauncher = !isMiuiHome && try {
                    pm.getPackageInfo("com.mi.android.globallauncher", 0) != null
                } catch (_: Throwable) { false }

                if (isMiuiHome) {
                    intent.setPackage("com.miui.home")
                } else if (isGlobalLauncher) {
                    intent.setPackage("com.mi.android.globallauncher")
                }
                context.sendBroadcast(intent)
                Log.d(TAG, "Dispatched single ACTION_TRIGGER_UNLOCK_ANIMATION to launcher (pkg=${intent.`package`})")
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
            val cfg = NotificationSheetConfigHolder.sheet
            val nsslId = container.resources.getIdentifier("notification_stack_scroller", "id", "com.android.systemui")
            val nssl = if (nsslId != 0) container.findViewById<View>(nsslId) else null ?: return

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

            val isActive = cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()
            val extraMargin = if (isActive) (cfg.sideMargin * density).toInt() else 0
            val targetStart = origStart + extraMargin
            val targetEnd = origEnd + extraMargin
            val bottomClearance = if (isActive) ((cfg.pillBottomMargin + cfg.pillHeight + 24f) * density).toInt() else 0

            var changed = false
            if (lp.marginStart != targetStart || lp.marginEnd != targetEnd) {
                lp.marginStart = targetStart
                lp.marginEnd = targetEnd
                lp.leftMargin = targetStart
                lp.rightMargin = targetEnd
                changed = true
            }
            if (lp.bottomMargin != bottomClearance) {
                lp.bottomMargin = bottomClearance
                changed = true
            }
            if (changed) {
                nssl.layoutParams = lp
                nssl.requestLayout()
            }
            if (nssl is ViewGroup) {
                val origPaddingTop = (nssl.getTag(TAG_ORIG_PADDING_TOP) as? Int) ?: run {
                    val pt = nssl.paddingTop
                    nssl.setTag(TAG_ORIG_PADDING_TOP, pt)
                    pt
                }
                val targetPaddingTop = if (isActive && cfg.lockScreenAsNotifCenterEnabled) {
                    kotlin.math.max(origPaddingTop, (260f * density).toInt())
                } else {
                    origPaddingTop
                }
                if (nssl.paddingTop != targetPaddingTop || nssl.paddingBottom != bottomClearance) {
                    nssl.setPadding(nssl.paddingLeft, targetPaddingTop, nssl.paddingRight, bottomClearance)
                }
                if (isActive && cfg.lockScreenAsNotifCenterEnabled) {
                    nssl.clipToPadding = false
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to adjust notification margins", t)
        }
    }

    private fun resetNotificationMargins(container: ViewGroup) {
        try {
            val nsslId = container.resources.getIdentifier("notification_stack_scroller", "id", "com.android.systemui")
            val nssl = if (nsslId != 0) container.findViewById<View>(nsslId) else null ?: return

            val lp = nssl.layoutParams as? ViewGroup.MarginLayoutParams ?: return
            val origStart = (nssl.getTag(TAG_ORIG_MARGIN_START) as? Int) ?: lp.marginStart
            val origEnd = (nssl.getTag(TAG_ORIG_MARGIN_END) as? Int) ?: lp.marginEnd

            var changed = false
            if (lp.marginStart != origStart || lp.marginEnd != origEnd) {
                lp.marginStart = origStart
                lp.marginEnd = origEnd
                lp.leftMargin = origStart
                lp.rightMargin = origEnd
                changed = true
            }
            if (lp.bottomMargin != 0) {
                lp.bottomMargin = 0
                changed = true
            }
            if (changed) {
                nssl.layoutParams = lp
                nssl.requestLayout()
            }
            if (nssl is ViewGroup) {
                val origPaddingTop = (nssl.getTag(TAG_ORIG_PADDING_TOP) as? Int) ?: nssl.paddingTop
                if (nssl.paddingTop != origPaddingTop || nssl.paddingBottom != 0) {
                    nssl.setPadding(nssl.paddingLeft, origPaddingTop, nssl.paddingRight, 0)
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
            for (m: Method in expandControllerClass.declaredMethods) {
                if (m.name == "onExpandFinish" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val ctrl = chain.thisObject
                                if (ctrl != null) {
                                    val appF = ctrl.javaClass.getDeclaredField("appearance")
                                    appF.isAccessible = true
                                    val appearance = appF.getBoolean(ctrl)
                                    if (appearance) {
                                        if (isUnlockedShadeActive && lastAppliedFraction >= 0.80f) {
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
                                    val container = containerRef?.get()
                                    if (container != null && hasFullyOpenedForCurrentSession && !hasTriggeredUnlockForCurrentOpen && !isLaunchingNotificationActivity) {
                                        hasTriggeredUnlockForCurrentOpen = true
                                        hasFullyOpenedForCurrentSession = false
                                        if (isHomeScreenCached) {
                                            notifyCloseOverHomescreen(container.context)
                                        }
                                    }
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
                val sheet = container.findViewWithTag<View>(TAG_GLASS_SHEET)
                val pill = container.findViewWithTag<View>(TAG_HOME_PILL)
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
            val sheet = container.findViewWithTag<View>(TAG_GLASS_SHEET)
            val pill = container.findViewWithTag<View>(TAG_HOME_PILL)
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
        val prev = lastAppliedFraction
        val f = fraction.coerceIn(0.0f, 1.0f)
        if (abs(f - lastAppliedFraction) < 0.001f) return
        lastAppliedFraction = f

        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyExpansionFractionInternal(f, prev)
        } else {
            mainHandler.post {
                applyExpansionFractionInternal(f, prev)
            }
        }
    }

    private fun applyExpansionFractionInternal(fraction: Float, prevFraction: Float = -1f) {
        try {
            val container = containerRef?.get() ?: return
            val cfg = NotificationSheetConfigHolder.sheet
            if (!cfg.enabled || currentStatusBarState != 0 || isKeyguard(container.context) || isControlCenterActive()) {
                container.findViewWithTag<View>(TAG_GLASS_SHEET)?.visibility = View.GONE
                container.findViewWithTag<View>(TAG_HOME_PILL)?.visibility = View.GONE
                LockScreenCoverSheetController.onShadeReset(container)
                isShadeFullyOpen = false
                hasTriggeredUnlockForCurrentOpen = true
                isLaunchingNotificationActivity = false
                RealtimeBackdropHelper.stopLiveRefresh()
                return
            }

            // Prime home screen cache at gesture opening start
            if (!isShadeOpen && fraction > 0.015f) {
                isShadeOpen = true
                updateHomeScreenCache(container.context)
            }

            var viewHeight = container.height.toFloat()
            if (viewHeight <= 0f) {
                viewHeight = container.resources.displayMetrics.heightPixels.toFloat()
            }
            if (viewHeight <= 0f) viewHeight = 2400f

            val sheet = container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)
            val pill = container.findViewWithTag<View>(TAG_HOME_PILL)

            // Arming: Shade has reached full expansion (only when expanding or at rest, never while collapsing)
            if (fraction >= 0.95f && (prevFraction < 0f || fraction >= prevFraction)) {
                isShadeFullyOpen = true
                hasFullyOpenedForCurrentSession = true
                hasTriggeredUnlockForCurrentOpen = false
                isShadeOpen = true
            }

            if (fraction <= 0.015f) {
                sheet?.visibility = View.GONE
                sheet?.translationY = -viewHeight
                sheet?.isClosingFromFullOpen = false
                pill?.visibility = View.GONE
                pill?.translationY = -viewHeight
                LockScreenCoverSheetController.onShadeReset(container)
                RealtimeBackdropHelper.stopLiveRefresh()
                isShadeFullyOpen = false
                hasFullyOpenedForCurrentSession = false
                hasTriggeredUnlockForCurrentOpen = true
                isShadeOpen = false
                isLaunchingNotificationActivity = false
                currentStretchOffset = 0f
                return
            }

            ensureGlassSheetInjected(container)
            ensureHomePillInjected(container)

            // Keep container translation at 0.0f; translate only the glass card and pill!
            container.translationY = 0.0f

            val upwardOffset = (fraction - 1.0f) * viewHeight
            // Cap sheet translation at 0f: during pulldown, slides from -viewHeight to 0f.
            // Once fully open (or during over-pull / spring rebound), remains pinned at 0f so top is never cut off!
            val finalSheetY = if (isShadeFullyOpen) {
                if (fraction >= 0.85f) {
                    0f
                } else {
                    ((fraction / 0.85f) - 1.0f) * viewHeight
                }
            } else {
                kotlin.math.min(0f, upwardOffset)
            }

            val curSheet = sheet ?: container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)
            if (curSheet != null) {
                curSheet.visibility = View.VISIBLE
                curSheet.isClosingFromFullOpen = hasFullyOpenedForCurrentSession && fraction < 0.95f
                curSheet.expansionFraction = fraction
                val prevY = curSheet.translationY
                curSheet.translationY = finalSheetY
                if (abs(prevY - finalSheetY) > 0.5f) {
                    curSheet.postInvalidateOnAnimation()
                }
                // Refresh backdrop only if glass effects are actively required
                val isLandscape = container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || container.width > container.height
                val shouldDrawWallpaper = cfg.lockWallpaperEnabled && !isLandscape &&
                        (!cfg.lockWallpaperHomescreenOnly || isHomeScreenCached)
                val shouldRenderGlass = cfg.glassEffectsEnabled && (!cfg.glassOnlyWithoutWallpaper || !shouldDrawWallpaper)
                if (shouldRenderGlass) {
                    RealtimeBackdropHelper.startLiveRefresh(container.context, curSheet)
                } else {
                    RealtimeBackdropHelper.stopLiveRefresh()
                }

                // Suppress background blur and dimming when iOS 27 Depth Wallpaper is active
                if (cfg.depthWallpaperEnabled && currentStatusBarState == 0) {
                    val bgAlpha = if (curSheet.isClosingFromFullOpen) 1f else if (fraction >= 0.90f) ((fraction - 0.90f) / 0.10f).coerceIn(0f, 1f) else 0f
                    curSheet.shadeBackgroundView?.alpha = bgAlpha
                    themeBackgroundViewRef?.get()?.alpha = bgAlpha
                }
            }

            val curPill = pill ?: container.findViewWithTag<View>(TAG_HOME_PILL)
            if (curPill != null) {
                curPill.visibility = View.VISIBLE
                // Pill is stably pinned to the sheet's bottom edge with stretch offset when sheet is fully open
                curPill.translationY = finalSheetY + (if (finalSheetY == 0f) currentStretchOffset else 0f)
                curPill.alpha = (fraction - 0.2f).coerceIn(0f, 1f)
            }

            if (cfg.lockScreenAsNotifCenterEnabled) {
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
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                args[1] = 0.0f
                                return chain.proceed(args)
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
        if (!isUnlockedShadeActive) return
        val container = containerRef?.get() ?: return
        val density = container.resources.displayMetrics.density

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isUserTouchingShade = true
                touchDownY = event.rawY
            }
            MotionEvent.ACTION_MOVE -> {
                isUserTouchingShade = true
                var viewHeight = container.height.toFloat()
                if (viewHeight <= 0f) {
                    viewHeight = container.resources.displayMetrics.heightPixels.toFloat()
                }
                if (viewHeight <= 0f) viewHeight = 2400f

                // Instantaneous 1:1 finger tracking during shade pulldown
                if (!isShadeFullyOpen && touchDownY < 250f * density) {
                    val frac = (event.rawY / viewHeight).coerceIn(0.0f, 1.0f)
                    applyExpansionFraction(frac)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasTouching = isUserTouchingShade
                isUserTouchingShade = false
                val dy = event.rawY - touchDownY

                if (event.actionMasked == MotionEvent.ACTION_UP && wasTouching) {
                    val isClosingGesture = (dy < -20f * density) || (lastAppliedFraction < 0.92f && dy < 0f)
                    if (hasFullyOpenedForCurrentSession && !hasTriggeredUnlockForCurrentOpen &&
                        !isLaunchingNotificationActivity && isClosingGesture && lastAppliedFraction > 0.05f
                    ) {
                        hasTriggeredUnlockForCurrentOpen = true
                        isShadeFullyOpen = false
                        hasFullyOpenedForCurrentSession = false
                        Log.d(TAG, "ACTION_UP closing release detected (dy=$dy, frac=$lastAppliedFraction) -> triggering simultaneous unlock")
                        notifyCloseOverHomescreen(container.context)
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
                            if (cfg.enabled && cfg.depthWallpaperEnabled && currentStatusBarState == 0 && !isControlCenterActive()) {
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
                            if (cfg.enabled && cfg.lockScreenAsNotifCenterEnabled && isUnlockedShadeActive) {
                                val container = containerRef?.get()
                                if (container != null) {
                                    val clockContainer = LockScreenCoverSheetController.getAuthenticClockContainer(container)
                                    if (clockContainer != null) {
                                        val clockBottom = LockScreenCoverSheetController.getEffectiveClockBottom(clockContainer)
                                        if (clockBottom > 0) {
                                            val density = container.resources.displayMetrics.density
                                            return clockBottom.toFloat() + (16f * density)
                                        }
                                    }
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
                                if (cfg.enabled && cfg.lockScreenAsNotifCenterEnabled && isUnlockedShadeActive) {
                                    val container = containerRef?.get()
                                    if (container != null) {
                                        mainHandler.post {
                                            LockScreenCoverSheetController.syncClockWithNotifications(container)
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

    private fun hookPanelExpandedHeight(module: XposedModule, classLoader: ClassLoader) {
        val classes = listOf(
            "com.android.systemui.shade.NotificationPanelViewController",
            "com.android.keyguard.tinyPanel.TinyKeyguardPanelViewControllerImpl"
        )
        for (clsName in classes) {
            try {
                val clazz = Class.forName(clsName, false, classLoader)
                for (m in clazz.declaredMethods) {
                    if (m.name == "setExpandedHeight" && m.parameterTypes.size == 1 &&
                        m.parameterTypes[0] == Float::class.javaPrimitiveType
                    ) {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val result = chain.proceed()
                                if (isUnlockedShadeActive) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                    val height = (args[0] as Number).toFloat()
                                    val container = containerRef?.get()
                                    val totalDist = if (container != null && container.height > 0) {
                                        container.height.toFloat()
                                    } else {
                                        val dm = container?.resources?.displayMetrics
                                        dm?.heightPixels?.toFloat() ?: 2400f
                                    }
                                    if (totalDist > 0f) {
                                        val frac = (height / totalDist).coerceIn(0.0f, 1.0f)
                                        applyExpansionFraction(frac)
                                    }
                                }
                                return result
                            }
                        })
                        Log.d(TAG, "Hooked $clsName.setExpandedHeight successfully")
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to hook $clsName.setExpandedHeight: ${t.message}")
            }
        }
    }
}
