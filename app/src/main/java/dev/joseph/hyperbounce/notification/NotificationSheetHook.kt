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
import dev.joseph.hyperbounce.notification.glass.NotificationGlassSheetView
import dev.joseph.hyperbounce.notification.glass.RealtimeBackdropHelper
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method
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
    private const val TAG_ORIG_MARGIN_START = 0x7E010001
    private const val TAG_ORIG_MARGIN_END = 0x7E010002

    @Volatile
    var currentStatusBarState: Int = 1 // 0 = SHADE, 1 = KEYGUARD, 2 = SHADE_LOCKED

    @Volatile
    private var isShadeOpen = false

    @Volatile
    var hasFullyOpened: Boolean = false

    @Volatile
    var isShadeSwitching: Boolean = false

    @Volatile
    var isControlCenterExpanded: Boolean = false

    private var containerRef: WeakReference<ViewGroup>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var lastAppliedFraction = -1.0f

    @Volatile
    private var lastUnlockTriggerTime = 0L

    @Volatile
    var isPillDragging = false
    private var pillTouchDownY = 0f
    private var pillInitialTransY = 0f

    @Volatile
    private var isDismissingCoordinator = false

    // Real-time continuous touch drag tracking
    @Volatile
    private var isTrackingDrag = false
    private var dragTouchDownY = 0f
    private var dragTouchDownX = 0f
    private var dragStartFraction = 0f
    private var lastProcessedEventTime = 0L
    private var lastProcessedAction = -1

    // Real-time over-pull stretch bounce offset
    @Volatile
    private var currentStretchOffset = 0f

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
        return isShadeSwitching || isControlCenterExpanded
    }

    private val isUnlockedShadeActive: Boolean
        get() {
            val cfg = NotificationSheetConfigHolder.sheet
            return cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()
        }

    fun init(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook StatusBarStateControllerImpl to track active shade / lockscreen state
        hookStatusBarState(module, classLoader)

        // 1b. Hook ShadeSwitchControllerImpl and ControlCenterImpl to prevent unlock bounce on switch
        hookShadeSwitch(module, classLoader)

        // 2. Hook MiuiNQSContainer (notification_container_parent) for lifecycle, glass & pill injection
        hookContainerParent(module, classLoader)

        // 2b. Hook touch stream on NotificationPanelViewControllerInjector & NotificationPanelView for 120Hz continuous drag
        hookPanelTouch(module, classLoader)

        // 3. Hook FloatFlowProperty to capture 120Hz continuous Folme expansion & over-pull stretch
        hookFloatFlowProperty(module, classLoader)

        // 4. Hook NotificationHeaderExpandController (expansion tracking)
        hookHeaderCallback(module, classLoader)

        // 5. Hook ShadeExpansionStateManager for drag gesture tracking
        hookShadeExpansionState(module, classLoader)

        // 6. Hook NotificationPanelExpandController for show/hide and unified dismissal
        hookExpandController(module, classLoader)

        // 7. Hook NotificationStackScrollLayout for full height layout and visibility
        hookNssl(module, classLoader)

        // 8. Hook NotificationStackScrollLayoutInjector to keep layout visible
        hookNsslInjector(module, classLoader)

        // 9. Hook AmbientState to prevent isFullyHidden from hiding stack
        hookAmbientState(module, classLoader)

        // 10. Hook MiuiPanelAnimValueObjectImpl to neutralize individual row Folme animation values
        hookMiuiPanelAnim(module, classLoader)

        // 11. Hook ViewStateInjectorImpl to neutralize individual row Folme translation/scale/alpha
        hookViewStateInjector(module, classLoader)

        // 12. Hook ExpandableViewBase to ensure static row visibility & alpha during pull down
        hookExpandableViewBase(module, classLoader)
    }

    private fun hideSheetForControlCenter() {
        mainHandler.post {
            val container = containerRef?.get() ?: return@post
            container.findViewWithTag<View>(TAG_GLASS_SHEET)?.visibility = View.GONE
            container.findViewWithTag<View>(TAG_HOME_PILL)?.visibility = View.GONE
            container.translationY = 0.0f
            container.scaleX = 1.0f
            container.scaleY = 1.0f
            resetNotificationMargins(container)
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
                                    hasFullyOpened = false
                                    isDismissingCoordinator = false
                                    isPillDragging = false
                                    isTrackingDrag = false
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
                                    if (switching) {
                                        isShadeOpen = false
                                        hasFullyOpened = false
                                        isTrackingDrag = false
                                        currentStretchOffset = 0f
                                        hideSheetForControlCenter()
                                    }
                                } catch (_: Throwable) {}
                                return chain.proceed()
                            }
                        })
                    }
                    "switchToControlCenter" -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                isShadeSwitching = true
                                isShadeOpen = false
                                hasFullyOpened = false
                                isTrackingDrag = false
                                currentStretchOffset = 0f
                                hideSheetForControlCenter()
                                return chain.proceed()
                            }
                        })
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ShadeSwitchControllerImpl", t)
        }

        try {
            val ccClass = Class.forName("com.miui.systemui.controlcenter.ControlCenterImpl", false, classLoader)
            for (m in ccClass.declaredMethods) {
                when (m.name) {
                    "expand" -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                isControlCenterExpanded = true
                                isShadeOpen = false
                                hasFullyOpened = false
                                isTrackingDrag = false
                                currentStretchOffset = 0f
                                hideSheetForControlCenter()
                                return chain.proceed()
                            }
                        })
                    }
                    "collapse" -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                isControlCenterExpanded = false
                                return chain.proceed()
                            }
                        })
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ControlCenterImpl", t)
        }
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
                val touchTargetH = (48f * density).toInt()

                val wrapper = android.widget.FrameLayout(context).apply {
                    tag = TAG_HOME_PILL
                    visibility = View.GONE
                    clipChildren = false
                    clipToPadding = false
                }

                val pillBar = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(0xF0FFFFFF.toInt())
                        cornerRadius = radius
                    }
                    elevation = 16f * density
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
                    val inst = constructor.newInstance(pillW, touchTargetH) as ViewGroup.MarginLayoutParams
                    inst.bottomMargin = (bMargin - ((touchTargetH - pillH) / 2)).coerceAtLeast(0)
                    lpClass.getField("bottomToBottom").setInt(inst, 0)
                    lpClass.getField("startToStart").setInt(inst, 0)
                    lpClass.getField("endToEnd").setInt(inst, 0)
                    inst
                } catch (_: Throwable) {
                    android.widget.FrameLayout.LayoutParams(
                        pillW,
                        touchTargetH,
                        android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                    ).apply {
                        bottomMargin = (bMargin - ((touchTargetH - pillH) / 2)).coerceAtLeast(0)
                    }
                }

                setupPillTouchListener(wrapper, container)
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
            val filter = android.content.IntentFilter("dev.joseph.hyperbounce.ACTION_SETTINGS_CHANGED")
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: android.content.Intent?) {
                    val key = intent?.getStringExtra("key") ?: return
                    val cfg = NotificationSheetConfigHolder.sheet
                    var handled = true
                    when (key) {
                        "notif_sheet_enabled" -> cfg.enabled = intent.getBooleanExtra("value_boolean", cfg.enabled)
                        "notif_card_refraction_height" -> cfg.refractionHeight = intent.getFloatExtra("value_float", cfg.refractionHeight)
                        "notif_card_bevel_width" -> cfg.bevelWidth = intent.getFloatExtra("value_float", cfg.bevelWidth)
                        "notif_card_left_bevel" -> cfg.leftBevelWidth = intent.getFloatExtra("value_float", cfg.leftBevelWidth)
                        "notif_card_right_bevel" -> cfg.rightBevelWidth = intent.getFloatExtra("value_float", cfg.rightBevelWidth)
                        "notif_card_bottom_bevel" -> cfg.bottomBevelWidth = intent.getFloatExtra("value_float", cfg.bottomBevelWidth)
                        "notif_card_dispersion" -> cfg.dispersionStrength = intent.getFloatExtra("value_float", cfg.dispersionStrength)
                        "notif_card_specular" -> cfg.specularStrength = intent.getFloatExtra("value_float", cfg.specularStrength)
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

    private fun setupPillTouchListener(pill: View, container: ViewGroup) {
        pill.setOnTouchListener { _, event ->
            val cfg = NotificationSheetConfigHolder.sheet
            if (!cfg.enabled || currentStatusBarState != 0 || isControlCenterActive()) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (isDismissingCoordinator) return@setOnTouchListener false
                    isPillDragging = true
                    pillTouchDownY = event.rawY
                    pillInitialTransY = container.translationY
                    container.requestDisallowInterceptTouchEvent(true)
                    container.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isPillDragging && !isDismissingCoordinator) {
                        val dy = event.rawY - pillTouchDownY
                        if (dy < 0f) { // Dragging upward to close
                            val newTransY = (pillInitialTransY + dy).coerceAtMost(0f)
                            var viewHeight = container.height.toFloat()
                            if (viewHeight <= 0f) viewHeight = container.resources.displayMetrics.heightPixels.toFloat()
                            if (viewHeight <= 0f) viewHeight = 2400f
                            val fraction = (1.0f + (newTransY / viewHeight)).coerceIn(0.0f, 1.0f)
                            applyExpansionFraction(fraction)
                        }
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    container.requestDisallowInterceptTouchEvent(false)
                    container.parent?.requestDisallowInterceptTouchEvent(false)
                    if (isPillDragging) {
                        isPillDragging = false
                        val dy = event.rawY - pillTouchDownY
                        val density = pill.resources.displayMetrics.density
                        val threshold = 40f * density

                        if (dy < -threshold) {
                            // Dismiss sheet cleanly via single dismissal coordinator
                            requestShadeDismiss(pill.context, "pill_swipe")
                        } else {
                            // Rebound back to open
                            container.animate()
                                .translationY(0f)
                                .setDuration(220)
                                .setInterpolator(DecelerateInterpolator(1.5f))
                                .withEndAction {
                                    applyExpansionFraction(1.0f)
                                }
                                .start()
                        }
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    /**
     * Unified Dismissal Coordinator:
     * Ensures exactly ONE slide-up animation and ONE homescreen unlock broadcast
     * whether the dismissal is initiated by the Home Pill or the notification shade gesture.
     */
    fun requestShadeDismiss(context: Context, source: String) {
        val container = containerRef?.get() ?: return
        if (currentStatusBarState != 0 || isControlCenterActive()) return

        synchronized(this) {
            if (isDismissingCoordinator) return
            isDismissingCoordinator = true
        }

        Log.d(TAG, "requestShadeDismiss triggered by $source, hasFullyOpened=$hasFullyOpened")

        val dismissAction = Runnable {
            // 1. Dispatch unlock broadcast to launcher ONCE only if shade was fully opened
            if (hasFullyOpened) {
                notifyCloseOverHomescreen(context)
            } else {
                Log.d(TAG, "requestShadeDismiss: incomplete pull dismissal, skipping unlock broadcast")
            }

            // 2. Animate container smoothly off-screen ONCE
            var viewHeight = container.height.toFloat()
            if (viewHeight <= 0f) viewHeight = container.resources.displayMetrics.heightPixels.toFloat()
            if (viewHeight <= 0f) viewHeight = 2400f

            container.animate()
                .translationY(-viewHeight)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator(1.5f))
                .withEndAction {
                    applyExpansionFraction(0.0f)
                    hasFullyOpened = false
                    isShadeOpen = false
                    isDismissingCoordinator = false
                    if (source != "system_hide") {
                        triggerShadeClose(context)
                    }
                }
                .start()
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissAction.run()
        } else {
            mainHandler.post(dismissAction)
        }
    }

    private fun triggerShadeClose(context: Context) {
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

        // 4. Fallback: ActivityManager.getRunningTasks(10)
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val tasks = am?.getRunningTasks(10)
            if (!tasks.isNullOrEmpty()) {
                for (task in tasks) {
                    val topPkg = task.topActivity?.packageName
                    if (isLauncher(topPkg)) {
                        Log.d(TAG, "isHomeScreenTop: true via ActivityManager fallback pkg=$topPkg")
                        return true
                    }
                }
            }
        } catch (_: Throwable) {}

        return false
    }

    private var hasTriggeredUnlockForCurrentOpen = false

    fun notifyCloseOverHomescreen(context: Context) {
        if (!hasFullyOpened || hasTriggeredUnlockForCurrentOpen) {
            Log.d(TAG, "notifyCloseOverHomescreen: shade was not fully opened or already triggered, skipping")
            return
        }
        hasTriggeredUnlockForCurrentOpen = true
        hasFullyOpened = false
        isShadeOpen = false

        if (currentStatusBarState != 0 || isKeyguard(context)) {
            Log.d(TAG, "notifyCloseOverHomescreen: keyguard active, skipping")
            return
        }

        if (isControlCenterActive()) {
            Log.d(TAG, "notifyCloseOverHomescreen: control center active or switching, skipping")
            return
        }

        val now = System.currentTimeMillis()
        if (now - lastUnlockTriggerTime < 800L) {
            Log.d(TAG, "notifyCloseOverHomescreen: debounced (within 800ms)")
            return
        }
        lastUnlockTriggerTime = now

        if (isHomeScreenTop(context)) {
            try {
                val pkgs = arrayOf("com.miui.home", "com.mi.android.globallauncher")
                for (pkg in pkgs) {
                    val intent = Intent("dev.joseph.hyperbounce.ACTION_TRIGGER_UNLOCK_ANIMATION").apply {
                        setPackage(pkg)
                    }
                    context.sendBroadcast(intent)
                }
                val generalIntent = Intent("dev.joseph.hyperbounce.ACTION_TRIGGER_UNLOCK_ANIMATION")
                context.sendBroadcast(generalIntent)
                Log.d(TAG, "Dispatched ACTION_TRIGGER_UNLOCK_ANIMATION to launcher successfully")
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
                if (nssl.paddingBottom != bottomClearance) {
                    nssl.setPadding(nssl.paddingLeft, nssl.paddingTop, nssl.paddingRight, bottomClearance)
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
            if (nssl is ViewGroup && nssl.paddingBottom != 0) {
                nssl.setPadding(nssl.paddingLeft, nssl.paddingTop, nssl.paddingRight, 0)
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
                                    if (!isPillDragging && !isDismissingCoordinator && isUnlockedShadeActive && !isTrackingDrag) {
                                        applyExpansionFraction(fVal)
                                    }
                                } else if (callerName.contains("NotificationPanelStretchAnimator")) {
                                    if (!isPillDragging && !isDismissingCoordinator && isUnlockedShadeActive) {
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
                                if (!isPillDragging && !isDismissingCoordinator && isUnlockedShadeActive && !isTrackingDrag) {
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
                if (m.name == "onPanelExpansionChanged" && m.parameterTypes.size == 3 &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                if (!isPillDragging && !isDismissingCoordinator && isUnlockedShadeActive && !isTrackingDrag) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                    val frac = (args[0] as Number).toFloat()
                                    applyExpansionFraction(frac)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in onPanelExpansionChanged hook", t)
                            }
                            return result
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
                when {
                    m.name == "show" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Boolean::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val result = chain.proceed()
                                try {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                    val anim = args[0] as Boolean
                                    if (!isPillDragging && !isDismissingCoordinator && isUnlockedShadeActive) {
                                        val container = containerRef?.get()
                                        if (container != null) {
                                            if (anim) {
                                                container.animate()
                                                    .translationY(0f)
                                                    .setDuration(220)
                                                    .setInterpolator(DecelerateInterpolator(1.5f))
                                                    .withEndAction {
                                                        applyExpansionFraction(1.0f)
                                                        hasFullyOpened = true
                                                        isShadeOpen = true
                                                        hasTriggeredUnlockForCurrentOpen = false
                                                    }
                                                    .start()
                                            } else {
                                                applyExpansionFraction(1.0f)
                                                hasFullyOpened = true
                                                isShadeOpen = true
                                                hasTriggeredUnlockForCurrentOpen = false
                                            }
                                        } else {
                                            applyExpansionFraction(1.0f)
                                            hasFullyOpened = true
                                            isShadeOpen = true
                                            hasTriggeredUnlockForCurrentOpen = false
                                        }
                                    }
                                } catch (_: Throwable) {}
                                return result
                            }
                        })
                    }
                    m.name == "hide" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Boolean::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                if (isDismissingCoordinator || isPillDragging) {
                                    // Unified coordinator is already animating and triggering unlock broadcast
                                    return chain.proceed()
                                }
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val anim = args[0] as Boolean
                                val container = containerRef?.get()
                                if (container != null && currentStatusBarState == 0 && !isControlCenterActive()) {
                                    if (anim) {
                                        requestShadeDismiss(container.context, "system_hide")
                                    } else {
                                        applyExpansionFraction(0.0f)
                                        if (hasFullyOpened) {
                                            notifyCloseOverHomescreen(container.context)
                                        }
                                        hasFullyOpened = false
                                        isShadeOpen = false
                                    }
                                }
                                return chain.proceed()
                            }
                        })
                    }
                    m.name == "onExpandFinish" && m.parameterTypes.isEmpty() -> {
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
                                            if (currentStatusBarState == 0 && !isControlCenterActive()) {
                                                isShadeOpen = true
                                                hasFullyOpened = true
                                                hasTriggeredUnlockForCurrentOpen = false
                                            }
                                        } else {
                                            RealtimeBackdropHelper.stopLiveRefresh()
                                            if (!isDismissingCoordinator && hasFullyOpened) {
                                                val container = containerRef?.get()
                                                if (container != null && currentStatusBarState == 0 && !isControlCenterActive()) {
                                                    notifyCloseOverHomescreen(container.context)
                                                }
                                            }
                                            hasFullyOpened = false
                                            isShadeOpen = false
                                        }
                                    }
                                } catch (_: Throwable) {}
                                return result
                            }
                        })
                    }
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

        if (lastAppliedFraction >= 0.99f || isShadeOpen || hasFullyOpened) {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                container.translationY = offset
                container.findViewWithTag<View>(TAG_GLASS_SHEET)?.invalidate()
            } else {
                mainHandler.post {
                    container.translationY = offset
                    container.findViewWithTag<View>(TAG_GLASS_SHEET)?.invalidate()
                }
            }
        }
    }

    fun animateStretchRelease() {
        val container = containerRef?.get() ?: return
        if (currentStretchOffset <= 0.01f) return
        mainHandler.post {
            container.animate()
                .translationY(0f)
                .setDuration(260)
                .setInterpolator(DecelerateInterpolator(1.8f))
                .withEndAction {
                    currentStretchOffset = 0f
                }
                .start()
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
        val container = containerRef?.get() ?: return
        val cfg = NotificationSheetConfigHolder.sheet
        if (!cfg.enabled || currentStatusBarState != 0 || isKeyguard(container.context) || isControlCenterActive()) {
            container.findViewWithTag<View>(TAG_GLASS_SHEET)?.visibility = View.GONE
            container.findViewWithTag<View>(TAG_HOME_PILL)?.visibility = View.GONE
            // On lockscreen, container translation is managed strictly by LockScreenSheetHook or stock.
            return
        }

        var viewHeight = container.height.toFloat()
        if (viewHeight <= 0f) {
            viewHeight = container.resources.displayMetrics.heightPixels.toFloat()
        }
        if (viewHeight <= 0f) viewHeight = 2400f

        if (fraction <= 0.001f) {
            container.translationY = -viewHeight
            container.findViewWithTag<View>(TAG_GLASS_SHEET)?.visibility = View.GONE
            container.findViewWithTag<View>(TAG_HOME_PILL)?.visibility = View.GONE
            RealtimeBackdropHelper.stopLiveRefresh()
            if (hasFullyOpened) {
                notifyCloseOverHomescreen(container.context)
            }
            hasFullyOpened = false
            isShadeOpen = false
            currentStretchOffset = 0f
            return
        }

        // True Paper Sheet Motion: translates the parent container downward as one solid card.
        // Bottommost notifications are naturally at the bottom of the container and enter view first!
        val upwardOffset = (fraction - 1.0f) * viewHeight
        val finalY = if (fraction >= 0.99f && currentStretchOffset > 0.01f) currentStretchOffset else upwardOffset
        container.translationY = finalY
        container.scaleX = 1.0f
        container.scaleY = 1.0f
        container.visibility = View.VISIBLE

        // Keep notification stack scroller visible and fully opaque
        val nsslId = container.resources.getIdentifier("notification_stack_scroller", "id", "com.android.systemui")
        val nssl = if (nsslId != 0) container.findViewById<View>(nsslId) else null
        if (nssl != null) {
            nssl.visibility = View.VISIBLE
            nssl.alpha = 1.0f
        }

        ensureGlassSheetInjected(container)
        ensureHomePillInjected(container)
        adjustNotificationMargins(container)

        val sheet = container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)
        if (sheet != null) {
            sheet.visibility = View.VISIBLE
            sheet.invalidate()
        }

        val pill = container.findViewWithTag<View>(TAG_HOME_PILL)
        if (pill != null) {
            pill.visibility = View.VISIBLE
            pill.alpha = (fraction - 0.2f).coerceIn(0f, 1f)
        }

        // Trigger backdrop capture when drag starts
        if (fraction in 0.05f..0.35f && prevFraction < 0.05f) {
            RealtimeBackdropHelper.triggerCapture(container.context) {
                sheet?.invalidate()
            }
        }

        // Shade is confirmed fully opened ONLY when fraction reaches completion
        if (fraction >= 0.98f && !isControlCenterActive()) {
            isShadeOpen = true
            hasFullyOpened = true
            hasTriggeredUnlockForCurrentOpen = false
        }

        // Start live refresh when fully open
        if (fraction >= 0.90f && sheet != null) {
            RealtimeBackdropHelper.startLiveRefresh(container.context, sheet)
        }
    }

    private fun hookNssl(module: XposedModule, classLoader: ClassLoader) {
        try {
            val nsslClass = Class.forName("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayout", false, classLoader)
            for (m in nsslClass.declaredMethods) {
                if (m.name == "setExpandedHeight" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = NotificationSheetConfigHolder.sheet
                            if (cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()) {
                                val nssl = chain.thisObject as? ViewGroup
                                if (nssl != null) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                    val viewH = nssl.height.toFloat().coerceAtLeast(2000f)
                                    args[0] = viewH

                                    try {
                                        val ambientStateField = nssl.javaClass.getDeclaredField("mAmbientState")
                                        ambientStateField.isAccessible = true
                                        val ambientState = ambientStateField.get(nssl)
                                        if (ambientState != null) {
                                            val pvField = ambientState.javaClass.getField("panelVisible")
                                            pvField.setBoolean(ambientState, true)
                                            try {
                                                val afField = ambientState.javaClass.getDeclaredField("mAppearFraction")
                                                afField.isAccessible = true
                                                afField.setFloat(ambientState, 1.0f)
                                            } catch (_: Throwable) {}
                                        }
                                    } catch (_: Throwable) {}

                                    val res = chain.proceed(args)
                                    nssl.visibility = View.VISIBLE
                                    nssl.alpha = 1.0f
                                    return res
                                }
                            }
                            return chain.proceed()
                        }
                    })
                } else if (m.name == "setAlpha" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Float::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = NotificationSheetConfigHolder.sheet
                            if (cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()) {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                args[0] = 1.0f
                                return chain.proceed(args)
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationStackScrollLayout", t)
        }
    }

    private fun hookNsslInjector(module: XposedModule, classLoader: ClassLoader) {
        try {
            val injectorClass = Class.forName("com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutInjector", false, classLoader)
            for (m in injectorClass.declaredMethods) {
                if (m.name == "getShouldBeVisible" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = NotificationSheetConfigHolder.sheet
                            if (cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()) {
                                return true
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationStackScrollLayoutInjector", t)
        }
    }

    private fun hookAmbientState(module: XposedModule, classLoader: ClassLoader) {
        try {
            val asClass = Class.forName("com.android.systemui.statusbar.notification.stack.AmbientState", false, classLoader)
            for (m in asClass.declaredMethods) {
                if (m.name == "isFullyHidden" && m.parameterTypes.isEmpty()) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = NotificationSheetConfigHolder.sheet
                            if (cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()) {
                                return false
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook AmbientState", t)
        }
    }

    private fun hookMiuiPanelAnim(module: XposedModule, classLoader: ClassLoader) {
        try {
            val animClass = Class.forName("com.android.systemui.shade.MiuiPanelAnimValueObjectImpl", false, classLoader)
            for (m in animClass.declaredMethods) {
                when (m.name) {
                    "changeVisible", "changeExpand" -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                val isUnlocked = cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()
                                if (isUnlocked) {
                                    neutralizeAnimObjectFields(chain.thisObject)
                                }
                                val result = chain.proceed()
                                if (isUnlocked) {
                                    neutralizeAnimObjectFields(chain.thisObject)
                                }
                                return result
                            }
                        })
                    }
                    "scheduleUpdate" -> {
                        if (m.parameterTypes.isEmpty()) {
                            module.hook(m).intercept(object : XposedInterface.Hooker {
                                override fun intercept(chain: XposedInterface.Chain): Any? {
                                    val cfg = NotificationSheetConfigHolder.sheet
                                    val isUnlocked = cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()
                                    if (isUnlocked) {
                                        neutralizeAnimObjectFields(chain.thisObject)
                                    }
                                    val result = chain.proceed()
                                    if (isUnlocked) {
                                        neutralizeAnimObjectFields(chain.thisObject)
                                    }
                                    return result
                                }
                            })
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook MiuiPanelAnimValueObjectImpl", t)
        }
    }

    private fun neutralizeAnimObjectFields(obj: Any?) {
        if (obj == null) return
        try {
            val clazz = obj.javaClass
            try {
                val alphaF = clazz.getDeclaredField("expandAlpha")
                alphaF.isAccessible = true
                alphaF.setFloat(obj, 1.0f)
            } catch (_: Throwable) {}

            try {
                val scaleF = clazz.getDeclaredField("expandScale")
                scaleF.isAccessible = true
                scaleF.setFloat(obj, 1.0f)
            } catch (_: Throwable) {}

            try {
                val transXF = clazz.getDeclaredField("expandTransX")
                transXF.isAccessible = true
                transXF.setFloat(obj, 1.0f)
            } catch (_: Throwable) {}

            try {
                val slideXF = clazz.getDeclaredField("slideTransX")
                slideXF.isAccessible = true
                slideXF.setFloat(obj, 0.0f)
            } catch (_: Throwable) {}

            try {
                val transYF = clazz.getDeclaredField("expandTransY")
                transYF.isAccessible = true
                transYF.setFloat(obj, 1.0f)
            } catch (_: Throwable) {}

            try {
                val bigYF = clazz.getDeclaredField("bigTransY")
                bigYF.isAccessible = true
                bigYF.setFloat(obj, 0.0f)
            } catch (_: Throwable) {}

            try {
                val smallYF = clazz.getDeclaredField("smallTransY")
                smallYF.isAccessible = true
                smallYF.setFloat(obj, 0.0f)
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }

    private fun hookViewStateInjector(module: XposedModule, classLoader: ClassLoader) {
        try {
            val vsiClass = Class.forName("com.android.systemui.statusbar.notification.stack.ViewStateInjectorImpl", false, classLoader)

            for (m: Method in vsiClass.declaredMethods) {
                when {
                    // Stop horizontal rolling from the left edge: force translationX = 0.0f
                    m.name == "setFolmeTranslationXForType" && m.parameterTypes.size == 2 &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                if (cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                    args[1] = 0.0f
                                    return chain.proceed(args)
                                }
                                return chain.proceed()
                            }
                        })
                    }

                    // Stop individual row scale animations for TYPE_PANEL_OBJECT (11018): keep scale = 1.0f
                    (m.name == "setFolmeScaleXForType" || m.name == "setFolmeScaleYForType") &&
                    m.parameterTypes.size == 3 &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType &&
                    m.parameterTypes[2] == Int::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                if (cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                    val type = (args[2] as? Number)?.toInt() ?: -1
                                    if (type == 11018 || type in 11031..11034) {
                                        args[1] = 1.0f
                                        return chain.proceed(args)
                                    }
                                }
                                return chain.proceed()
                            }
                        })
                    }

                    // Stop individual row alpha animations for TYPE_PANEL_OBJECT (11018): keep alpha = 1.0f
                    m.name == "setFolmeAlphaForType" &&
                    m.parameterTypes.size == 3 &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType &&
                    m.parameterTypes[2] == Int::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                if (cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                    val type = (args[2] as? Number)?.toInt() ?: -1
                                    if (type == 11018 || type in 11031..11034) {
                                        args[1] = 1.0f
                                        return chain.proceed(args)
                                    }
                                }
                                return chain.proceed()
                            }
                        })
                    }

                    // Stop staggered vertical drop for TYPE_PANEL_OBJECT (11018): keep translationY = 0.0f
                    m.name == "setFolmeTranslationYForType" &&
                    m.parameterTypes.size == 3 &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType &&
                    m.parameterTypes[2] == Int::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                if (cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                    val type = (args[2] as? Number)?.toInt() ?: -1
                                    if (type == 11018 || type in 11031..11034) {
                                        args[1] = 0.0f
                                        return chain.proceed(args)
                                    }
                                }
                                return chain.proceed()
                            }
                        })
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ViewStateInjectorImpl", t)
        }
    }

    private fun handlePanelTouch(event: MotionEvent, source: String): Boolean {
        val cfg = NotificationSheetConfigHolder.sheet
        if (!cfg.enabled || currentStatusBarState != 0 || isControlCenterActive() || isPillDragging || isDismissingCoordinator) {
            isTrackingDrag = false
            return false
        }

        val eventTime = event.eventTime
        val action = event.actionMasked

        // Deduplicate if received from both handleExternalTouch and dispatchTouchEvent
        if (eventTime == lastProcessedEventTime && action == lastProcessedAction && action != MotionEvent.ACTION_MOVE) {
            return false
        }
        lastProcessedEventTime = eventTime
        lastProcessedAction = action

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                dragTouchDownY = event.rawY
                dragTouchDownX = event.rawX
                dragStartFraction = if (hasFullyOpened || isShadeOpen) 1.0f else (if (lastAppliedFraction >= 0f) lastAppliedFraction else 0.0f)
                isTrackingDrag = false
                currentStretchOffset = 0f
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.rawY - dragTouchDownY
                val deltaX = event.rawX - dragTouchDownX

                if (!isTrackingDrag) {
                    val density = containerRef?.get()?.resources?.displayMetrics?.density ?: 2.5f
                    val slop = 8f * density
                    if (abs(deltaY) > slop && abs(deltaY) > abs(deltaX) * 1.1f) {
                        isTrackingDrag = true
                    }
                }

                if (isTrackingDrag) {
                    val container = containerRef?.get()
                    var viewHeight = container?.height?.toFloat() ?: 0f
                    if (viewHeight <= 0f) viewHeight = container?.resources?.displayMetrics?.heightPixels?.toFloat() ?: 2400f
                    if (viewHeight <= 0f) viewHeight = 2400f

                    if (dragStartFraction < 0.5f) {
                        // Pulling down from closed state (opening)
                        val rawFrac = (deltaY / viewHeight).coerceAtLeast(0.0f)
                        if (rawFrac <= 1.0f) {
                            currentStretchOffset = 0f
                            applyExpansionFraction(rawFrac)
                        } else {
                            // Over-pull past full expansion: rubber-band bounce
                            applyExpansionFraction(1.0f)
                            val overPull = (deltaY - viewHeight) * 0.35f
                            applyStretchOffset(overPull)
                        }
                    } else {
                        // Dragging when already fully opened
                        if (deltaY > 0f) {
                            // Over-pull downward bounce
                            val stretch = deltaY * 0.35f
                            applyStretchOffset(stretch)
                        } else {
                            // Swiping upward to close
                            currentStretchOffset = 0f
                            val rawFrac = (1.0f + deltaY / (viewHeight * 0.85f)).coerceIn(0.0f, 1.0f)
                            applyExpansionFraction(rawFrac)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (currentStretchOffset > 0.5f) {
                    animateStretchRelease()
                }
                if (isTrackingDrag) {
                    val container = containerRef?.get()
                    val currentFrac = lastAppliedFraction
                    val wasFullyOpened = hasFullyOpened
                    isTrackingDrag = false

                    if (container != null && currentStatusBarState == 0 && !isControlCenterActive()) {
                        if (!wasFullyOpened && currentFrac in 0.01f..0.45f) {
                            // Incomplete pull: snap back off-screen without unlock animation
                            mainHandler.postDelayed({
                                if (!isShadeOpen && !hasFullyOpened && lastAppliedFraction in 0.01f..0.45f) {
                                    var vh = container.height.toFloat()
                                    if (vh <= 0f) vh = container.resources.displayMetrics.heightPixels.toFloat()
                                    if (vh <= 0f) vh = 2400f
                                    container.animate()
                                        .translationY(-vh)
                                        .setDuration(200)
                                        .setInterpolator(DecelerateInterpolator(1.5f))
                                        .withEndAction {
                                            applyExpansionFraction(0.0f)
                                            hasFullyOpened = false
                                            isShadeOpen = false
                                        }
                                        .start()
                                }
                            }, 80)
                        } else if (!wasFullyOpened && currentFrac >= 0.75f) {
                            // Near full pull: settle open
                            mainHandler.postDelayed({
                                if (!hasFullyOpened && lastAppliedFraction >= 0.75f) {
                                    container.animate()
                                        .translationY(0f)
                                        .setDuration(200)
                                        .setInterpolator(DecelerateInterpolator(1.5f))
                                        .withEndAction {
                                            applyExpansionFraction(1.0f)
                                            hasFullyOpened = true
                                            isShadeOpen = true
                                        }
                                        .start()
                                }
                            }, 80)
                        } else if (wasFullyOpened && currentFrac < 0.65f) {
                            // Fully opened previously, dragged up significantly to dismiss
                            requestShadeDismiss(container.context, "drag_collapse")
                        } else if (wasFullyOpened && currentFrac in 0.65f..0.98f) {
                            // Fully opened previously, dragged up slightly then released: rebound open
                            container.animate()
                                .translationY(0f)
                                .setDuration(200)
                                .setInterpolator(DecelerateInterpolator(1.5f))
                                .withEndAction {
                                    applyExpansionFraction(1.0f)
                                }
                                .start()
                        }
                    }
                }
            }
        }
        return false
    }

    private fun hookPanelTouch(module: XposedModule, classLoader: ClassLoader) {
        try {
            val npvcInjClass = Class.forName("com.android.systemui.shade.NotificationPanelViewControllerInjector", false, classLoader)
            for (m: Method in npvcInjClass.declaredMethods) {
                if (m.name == "handleExternalTouch" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == MotionEvent::class.java
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val event = args[0] as? MotionEvent
                                if (event != null) {
                                    handlePanelTouch(event, "npvc_external")
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error handling external touch", t)
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked NotificationPanelViewControllerInjector.handleExternalTouch")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationPanelViewControllerInjector.handleExternalTouch", t)
        }

        try {
            val npvClass = Class.forName("com.android.systemui.shade.NotificationPanelView", false, classLoader)
            for (m: Method in npvClass.declaredMethods) {
                if (m.name == "dispatchTouchEvent" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == MotionEvent::class.java
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val event = args[0] as? MotionEvent
                                if (event != null) {
                                    handlePanelTouch(event, "npv_dispatch")
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error handling npv dispatch touch", t)
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked NotificationPanelView.dispatchTouchEvent")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationPanelView.dispatchTouchEvent", t)
        }
    }

    private fun isRowPinned(view: Any?): Boolean {
        if (view == null) return false
        return try {
            val m = view.javaClass.getMethod("isPinned")
            m.invoke(view) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }

    private fun hookExpandableViewBase(module: XposedModule, classLoader: ClassLoader) {
        try {
            val ebClass = Class.forName("com.miui.systemui.notification.row.ExpandableViewBase", false, classLoader)

            for (m: Method in ebClass.declaredMethods) {
                when {
                    m.name == "getExtAlpha" && m.parameterTypes.isEmpty() -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                if (isUnlockedShadeActive && !isRowPinned(chain.thisObject)) {
                                    return 1.0f
                                }
                                return chain.proceed()
                            }
                        })
                    }
                    m.name == "setExtAlpha" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Float::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                if (isUnlockedShadeActive && !isRowPinned(chain.thisObject)) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                    args[0] = 1.0f
                                    return chain.proceed(args)
                                }
                                return chain.proceed()
                            }
                        })
                    }
                    m.name == "getExtTransitionAlpha" && m.parameterTypes.isEmpty() -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                if (isUnlockedShadeActive && !isRowPinned(chain.thisObject)) {
                                    return 1.0f
                                }
                                return chain.proceed()
                            }
                        })
                    }
                    m.name == "setExtTransitionAlpha" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Float::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                if (isUnlockedShadeActive && !isRowPinned(chain.thisObject)) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                    args[0] = 1.0f
                                    return chain.proceed(args)
                                }
                                return chain.proceed()
                            }
                        })
                    }
                    (m.name == "getExtScaleX" || m.name == "getExtScaleY") && m.parameterTypes.isEmpty() -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                if (isUnlockedShadeActive && !isRowPinned(chain.thisObject)) {
                                    return 1.0f
                                }
                                return chain.proceed()
                            }
                        })
                    }
                    (m.name == "setExtScaleX" || m.name == "setExtScaleY") && m.parameterTypes.size == 1 && m.parameterTypes[0] == Float::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                if (isUnlockedShadeActive && !isRowPinned(chain.thisObject)) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                    args[0] = 1.0f
                                    return chain.proceed(args)
                                }
                                return chain.proceed()
                            }
                        })
                    }
                    (m.name == "getExtTranslationX" || m.name == "getExtTranslationY") && m.parameterTypes.isEmpty() -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                if (isUnlockedShadeActive && !isRowPinned(chain.thisObject)) {
                                    return 0.0f
                                }
                                return chain.proceed()
                            }
                        })
                    }
                    (m.name == "setExtTranslationX" || m.name == "setExtTranslationY") && m.parameterTypes.size == 1 && m.parameterTypes[0] == Float::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                if (isUnlockedShadeActive && !isRowPinned(chain.thisObject)) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                    args[0] = 0.0f
                                    return chain.proceed(args)
                                }
                                return chain.proceed()
                            }
                        })
                    }
                }
            }
            Log.d(TAG, "Hooked ExpandableViewBase ext properties successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ExpandableViewBase", t)
        }
    }
}
