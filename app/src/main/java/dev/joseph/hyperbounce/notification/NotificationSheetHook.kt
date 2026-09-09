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
 * Converts the HyperOS Notification Center into a single cohesive iOS-style glass container sheet:
 * - Continuous 120Hz native slide down from top as an integrated unit.
 * - Eliminates horizontal rolling and staggered row animations inside notifications so they sit fully static.
 * - Frames the container with a 3-edge AGSL Liquid Glass 2.0 lens (left, right, bottom edges).
 * - Refracts the live blurred shade background layer (ShadeBackgroundView) without touching wallpaper.
 * - Leaves the stock status bar clock animation from the left edge completely untouched.
 * - Provides an iOS home indicator pill at the bottom of the container sheet.
 * - Pulling the pill up dismisses the notification sheet.
 * - Closing over the homescreen (via pill swipe-up or gesture swipe-up) triggers the homescreen unlock bounce animation.
 * - Insets notification items inward slightly so glass bevels frame them cleanly.
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
    var isShadeSwitching: Boolean = false

    @Volatile
    var isControlCenterExpanded: Boolean = false

    private var containerRef: WeakReference<ViewGroup>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var lastAppliedFraction = -1.0f

    @Volatile
    private var lastUnlockTriggerTime = 0L

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

    fun init(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook StatusBarStateControllerImpl to track active shade / lockscreen state
        hookStatusBarState(module, classLoader)

        // 1b. Hook ShadeSwitchControllerImpl and ControlCenterImpl to prevent unlock bounce on switch
        hookShadeSwitch(module, classLoader)

        // 2. Hook MiuiNQSContainer (notification_container_parent) for lifecycle, glass & pill injection
        hookContainerParent(module, classLoader)

        // 3. Hook FloatFlowProperty to capture 120Hz continuous Folme expansion
        hookFloatFlowProperty(module, classLoader)

        // 4. Hook NotificationHeaderExpandController (clock expansion decoupling)
        hookHeaderCallback(module, classLoader)

        // 5. Hook ShadeExpansionStateManager for drag gesture tracking
        hookShadeExpansionState(module, classLoader)

        // 6. Hook NotificationPanelExpandController for show/hide and callbacks
        hookExpandController(module, classLoader)

        // 7. Hook NotificationPanelViewController for fling close detection
        hookNpvc(module, classLoader)

        // 8. Hook NotificationStackScrollLayout for full height layout and visibility/alpha pinning
        hookNssl(module, classLoader)

        // 9. Hook NotificationStackScrollLayoutInjector to always keep layout visible
        hookNsslInjector(module, classLoader)

        // 10. Hook AmbientState to prevent isFullyHidden from hiding stack
        hookAmbientState(module, classLoader)

        // 11. Hook MiuiPanelAnimValueObjectImpl to reset anim fields
        hookMiuiPanelAnim(module, classLoader)

        // 12. Hook ViewStateInjectorImpl to eliminate stock rolling and row scaling animations
        hookViewStateInjector(module, classLoader)

        // 13. Hook ViewState.applyToView to prevent missing/invisible notifications
        hookViewStateApply(module, classLoader)

        // 14. Hook PhoneStatusBarView for 1:1 pull down tracking decoupled from stock clock
        hookStatusBarTouch(module, classLoader)

        // 15. Hook NotificationPanelView to continue tracking pulldown touch
        hookPanelViewTouch(module, classLoader)

        // 16. Hook MiuiNotificationPanelAnimControllerImpl to prevent row hiding/staggering
        hookMiuiPanelAnimController(module, classLoader)
    }

    private fun hideSheetForControlCenter() {
        mainHandler.post {
            val container = containerRef?.get() ?: return@post
            container.findViewWithTag<View>(TAG_GLASS_SHEET)?.visibility = View.GONE
            container.findViewWithTag<View>(TAG_HOME_PILL)?.visibility = View.GONE
            if (!isLockScreenManaging(container)) {
                container.translationY = 0.0f
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
                                    // Not in unlocked shade (e.g. keyguard active): hide glass sheet and home pill
                                    mainHandler.post {
                                        val container = containerRef?.get()
                                        if (container != null) {
                                            val sheet = container.findViewWithTag<View>(TAG_GLASS_SHEET)
                                            sheet?.visibility = View.GONE
                                            val pill = container.findViewWithTag<View>(TAG_HOME_PILL)
                                            pill?.visibility = View.GONE
                                            if (!isLockScreenManaging(container)) {
                                                container.translationY = 0.0f
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

    private fun isLockScreenManaging(container: ViewGroup): Boolean {
        // On lockscreen, LockScreenSheetHook manages translationY if active
        return currentStatusBarState == 1
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
            container.clipChildren = true
            container.clipToPadding = true
            val nsslId = container.resources.getIdentifier("notification_stack_scroller", "id", "com.android.systemui")
            val nssl = if (nsslId != 0) container.findViewById<ViewGroup>(nsslId) else null
            if (nssl != null) {
                nssl.clipChildren = true
                nssl.clipToPadding = true
                nssl.visibility = View.VISIBLE
            }
            if (isKeyguard(container.context) || isControlCenterActive()) {
                container.findViewWithTag<View>(TAG_GLASS_SHEET)?.visibility = View.GONE
                container.findViewWithTag<View>(TAG_HOME_PILL)?.visibility = View.GONE
                if (!isLockScreenManaging(container)) {
                    container.translationY = 0.0f
                }
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

            if (!cfg.enabled || isKeyguard(container.context) || isControlCenterActive()) {
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
                    // 0, 0 in ConstraintLayout = MATCH_CONSTRAINT
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

            if (!cfg.enabled || isKeyguard(container.context) || isControlCenterActive()) {
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
                val touchTargetH = (44f * density).toInt()

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

    @Volatile
    var isPillDragging = false
    private var pillTouchDownY = 0f
    private var pillInitialTransY = 0f

    @Volatile
    var isPulldownDragging = false
    private var pulldownStartY = 0f
    private var pulldownStartX = 0f

    private fun handleStatusBarTouch(event: MotionEvent): Boolean {
        val cfg = NotificationSheetConfigHolder.sheet
        if (!cfg.enabled || currentStatusBarState != 0 || isControlCenterActive()) {
            return false
        }
        val container = containerRef?.get() ?: return false
        val screenW = container.resources.displayMetrics.widthPixels.toFloat()
        var viewHeight = container.height.toFloat()
        if (viewHeight <= 0f) viewHeight = container.resources.displayMetrics.heightPixels.toFloat()
        if (viewHeight <= 0f) viewHeight = 2400f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Dragging down from top left/center status bar (Notification side)
                if (event.rawX <= screenW * 0.55f) {
                    isPulldownDragging = true
                    pulldownStartY = event.rawY
                    pulldownStartX = event.rawX
                    container.visibility = View.VISIBLE
                    container.translationY = -viewHeight
                    ensureGlassSheetInjected(container)
                    ensureHomePillInjected(container)
                    ensureAllRowsVisible(container)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isPulldownDragging) {
                    val dy = event.rawY - pulldownStartY
                    if (dy > 0f) {
                        // Requires ~70% screen height drag distance for full shade open
                        val maxDragDistance = viewHeight * 0.70f
                        val fraction = (dy / maxDragDistance).coerceIn(0.0f, 1.0f)
                        applyExpansionFraction(fraction)
                        ensureAllRowsVisible(container)
                    } else {
                        applyExpansionFraction(0.0f)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isPulldownDragging) {
                    isPulldownDragging = false
                    val dy = event.rawY - pulldownStartY
                    val maxDragDistance = viewHeight * 0.70f
                    val fraction = (dy / maxDragDistance).coerceIn(0.0f, 1.0f)
                    if (fraction > 0.35f) {
                        // Dragged past threshold: smoothly animate container to fully open
                        container.animate()
                            .translationY(0f)
                            .setDuration(220)
                            .setInterpolator(DecelerateInterpolator())
                            .withEndAction {
                                applyExpansionFraction(1.0f)
                                ensureAllRowsVisible(container)
                            }
                            .start()
                    } else {
                        // Released before threshold: smoothly animate back up
                        container.animate()
                            .translationY(-viewHeight)
                            .setDuration(220)
                            .setInterpolator(DecelerateInterpolator())
                            .withEndAction {
                                applyExpansionFraction(0.0f)
                                triggerShadeClose(container.context)
                            }
                            .start()
                    }
                }
            }
        }
        return false // Return false to allow stock clock animation to continue untouched
    }

    private fun setupPillTouchListener(pill: View, container: ViewGroup) {
        pill.setOnTouchListener { _, event ->
            val cfg = NotificationSheetConfigHolder.sheet
            if (!cfg.enabled || currentStatusBarState != 0) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isPillDragging = true
                    pillTouchDownY = event.rawY
                    pillInitialTransY = container.translationY
                    container.requestDisallowInterceptTouchEvent(true)
                    container.parent?.requestDisallowInterceptTouchEvent(true)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isPillDragging) {
                        val dy = event.rawY - pillTouchDownY
                        if (dy < 0f) { // Dragging upward to close
                            val newTransY = (pillInitialTransY + dy).coerceAtMost(0f)
                            var viewHeight = container.height.toFloat()
                            if (viewHeight <= 0f) viewHeight = container.resources.displayMetrics.heightPixels.toFloat()
                            if (viewHeight <= 0f) viewHeight = 2400f
                            val fraction = (1.0f + (newTransY / viewHeight)).coerceIn(0.0f, 1.0f)
                            applyExpansionFraction(fraction)
                            ensureAllRowsVisible(container)
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

                        var viewHeight = container.height.toFloat()
                        if (viewHeight <= 0f) viewHeight = container.resources.displayMetrics.heightPixels.toFloat()
                        if (viewHeight <= 0f) viewHeight = 2400f

                        if (dy < -threshold) {
                            // Swiped up enough to close:
                            // 1. Immediately trigger launcher unlock bounce on flick/release without waiting for animation!
                            notifyCloseOverHomescreen(pill.context)

                            // 2. Animate container smoothly off-screen, then trigger shade collapse
                            container.animate()
                                .translationY(-viewHeight)
                                .setDuration(220)
                                .setInterpolator(DecelerateInterpolator())
                                .withEndAction {
                                    applyExpansionFraction(0.0f)
                                    triggerShadeClose(pill.context)
                                }
                                .start()
                        } else {
                            // Rebound back to open
                            container.animate()
                                .translationY(0f)
                                .setDuration(220)
                                .setInterpolator(DecelerateInterpolator())
                                .withEndAction {
                                    applyExpansionFraction(1.0f)
                                    ensureAllRowsVisible(container)
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

    fun notifyCloseOverHomescreen(context: Context) {
        if (!isShadeOpen) {
            Log.d(TAG, "notifyCloseOverHomescreen: shade was not open, skipping")
            return
        }
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
        if (now - lastUnlockTriggerTime < 500L) return
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

            val extraMargin = if (cfg.enabled) (cfg.sideMargin * density).toInt() else 0
            val targetStart = origStart + extraMargin
            val targetEnd = origEnd + extraMargin
            val bottomClearance = if (cfg.enabled) ((cfg.pillBottomMargin + cfg.pillHeight + 24f) * density).toInt() else 0

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
                nssl.clipChildren = true
                nssl.clipToPadding = true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to adjust notification margins", t)
        }
    }

    private fun hookFloatFlowProperty(module: XposedModule, classLoader: ClassLoader) {
        // Decoupled from stock clock animator. Does not drive the paper sheet.
    }

    private fun hookHeaderCallback(module: XposedModule, classLoader: ClassLoader) {
        // Left untouched so the stock clock curves in freely from the left edge.
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
                                if (!isPulldownDragging && !isPillDragging) {
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
                                    if (currentStatusBarState == 0 && !isControlCenterActive()) {
                                        isShadeOpen = true
                                    }
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                    val anim = args[0] as Boolean
                                    if (!isPulldownDragging && !isPillDragging) {
                                        val container = containerRef?.get()
                                        if (container != null) {
                                            if (anim) {
                                                container.animate()
                                                    .translationY(0f)
                                                    .setDuration(220)
                                                    .setInterpolator(DecelerateInterpolator())
                                                    .withEndAction {
                                                        applyExpansionFraction(1.0f)
                                                        ensureAllRowsVisible(container)
                                                    }
                                                    .start()
                                            } else {
                                                applyExpansionFraction(1.0f)
                                                ensureAllRowsVisible(container)
                                            }
                                        } else {
                                            applyExpansionFraction(1.0f)
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
                                val result = chain.proceed()
                                try {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                    val anim = args[0] as Boolean
                                    val container = containerRef?.get()
                                    if (container != null) {
                                        notifyCloseOverHomescreen(container.context)
                                    }
                                    RealtimeBackdropHelper.stopLiveRefresh()
                                    if (!isPulldownDragging && !isPillDragging) {
                                        if (container != null) {
                                            var viewHeight = container.height.toFloat()
                                            if (viewHeight <= 0f) viewHeight = container.resources.displayMetrics.heightPixels.toFloat()
                                            if (viewHeight <= 0f) viewHeight = 2400f
                                            if (anim) {
                                                container.animate()
                                                    .translationY(-viewHeight)
                                                    .setDuration(220)
                                                    .setInterpolator(DecelerateInterpolator())
                                                    .withEndAction {
                                                        applyExpansionFraction(0.0f)
                                                    }
                                                    .start()
                                            } else {
                                                applyExpansionFraction(0.0f)
                                            }
                                        } else {
                                            applyExpansionFraction(0.0f)
                                        }
                                    }
                                } catch (_: Throwable) {}
                                return result
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
                                            }
                                        } else {
                                            RealtimeBackdropHelper.stopLiveRefresh()
                                            val container = containerRef?.get()
                                            if (container != null) {
                                                notifyCloseOverHomescreen(container.context)
                                            }
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

    private fun hookNpvc(module: XposedModule, classLoader: ClassLoader) {
        // NPVC fling hook is no longer used for early unlock triggering;
        // unlock animation is cleanly triggered when shade collapse finishes or pill is flicked.
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
            if (!isLockScreenManaging(container)) {
                container.translationY = 0.0f
            }
            return
        }

        var viewHeight = container.height.toFloat()
        if (viewHeight <= 0f) {
            viewHeight = container.resources.displayMetrics.heightPixels.toFloat()
        }
        if (viewHeight <= 0f) viewHeight = 2400f

        if (fraction <= 0.001f) {
            if (!isLockScreenManaging(container)) {
                container.translationY = -viewHeight
            }
            container.findViewWithTag<View>(TAG_GLASS_SHEET)?.visibility = View.GONE
            container.findViewWithTag<View>(TAG_HOME_PILL)?.visibility = View.GONE
            RealtimeBackdropHelper.stopLiveRefresh()
            if (prevFraction > 0.05f) {
                notifyCloseOverHomescreen(container.context)
            }
            return
        }

        val upwardOffset = (1.0f - fraction) * -viewHeight
        container.translationY = upwardOffset
        container.scaleX = 1.0f
        container.scaleY = 1.0f
        container.visibility = View.VISIBLE

        ensureGlassSheetInjected(container)
        ensureHomePillInjected(container)
        ensureAllRowsVisible(container)

        val sheet = container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)
        if (sheet != null) {
            sheet.visibility = View.VISIBLE
            sheet.invalidate()
        }

        val pill = container.findViewWithTag<View>(TAG_HOME_PILL)
        if (pill != null) {
            pill.visibility = View.VISIBLE
        }

        // Trigger backdrop capture when drag starts
        if (fraction in 0.05f..0.35f && prevFraction < 0.05f) {
            RealtimeBackdropHelper.triggerCapture(container.context) {
                sheet?.invalidate()
            }
        }

        // Mark shade as opened once pulled down past 40%
        if (fraction >= 0.40f && !isControlCenterActive()) {
            isShadeOpen = true
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
                                            try {
                                                val stField = ambientState.javaClass.getField("mStackTranslation")
                                                stField.setFloat(ambientState, 0.0f)
                                            } catch (_: Throwable) {}
                                        }
                                    } catch (_: Throwable) {}

                                    val res = chain.proceed(args)
                                    nssl.visibility = View.VISIBLE
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
                            if (cfg.enabled && currentStatusBarState == 0) {
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
                            if (cfg.enabled && currentStatusBarState == 0) {
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
                    "changeVisible" -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                if (cfg.enabled && currentStatusBarState == 0) {
                                    val result = chain.proceed()
                                    neutralizeAnimObjectFields(chain.thisObject)
                                    return result
                                }
                                return chain.proceed()
                            }
                        })
                    }
                    "changeExpand" -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                if (cfg.enabled && currentStatusBarState == 0) {
                                    val result = chain.proceed()
                                    neutralizeAnimObjectFields(chain.thisObject)
                                    return result
                                }
                                return chain.proceed()
                            }
                        })
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
            val alphaF = obj.javaClass.getDeclaredField("expandAlpha")
            alphaF.isAccessible = true
            alphaF.setFloat(obj, 1.0f)
        } catch (_: Throwable) {}

        try {
            val scaleF = obj.javaClass.getDeclaredField("expandScale")
            scaleF.isAccessible = true
            scaleF.setFloat(obj, 1.0f)
        } catch (_: Throwable) {}

        try {
            val transXF = obj.javaClass.getDeclaredField("expandTransX")
            transXF.isAccessible = true
            transXF.setFloat(obj, 0.0f)
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
                                if (cfg.enabled && currentStatusBarState == 0) {
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
                                if (cfg.enabled && currentStatusBarState == 0) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                    val type = (args[2] as? Number)?.toInt() ?: -1
                                    if (type == 11018) {
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
                                if (cfg.enabled && currentStatusBarState == 0) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                    val type = (args[2] as? Number)?.toInt() ?: -1
                                    if (type == 11018) {
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
                                if (cfg.enabled && currentStatusBarState == 0) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                    val type = (args[2] as? Number)?.toInt() ?: -1
                                    if (type == 11018) {
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

    private fun hookViewStateApply(module: XposedModule, classLoader: ClassLoader) {
        try {
            val vsClass = Class.forName("com.android.systemui.statusbar.notification.stack.ViewState", false, classLoader)
            for (m in vsClass.declaredMethods) {
                if (m.name == "applyToView" && m.parameterTypes.size == 1 && View::class.java.isAssignableFrom(m.parameterTypes[0])) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = NotificationSheetConfigHolder.sheet
                            if (cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()) {
                                val vs = chain.thisObject
                                if (vs != null) {
                                    try {
                                        val hiddenF = vs.javaClass.getField("hidden")
                                        hiddenF.setBoolean(vs, false)
                                        val alphaF = vs.javaClass.getField("mAlpha")
                                        alphaF.setFloat(vs, 1.0f)
                                        val scaleXF = vs.javaClass.getField("mScaleX")
                                        scaleXF.setFloat(vs, 1.0f)
                                        val scaleYF = vs.javaClass.getField("mScaleY")
                                        scaleYF.setFloat(vs, 1.0f)
                                        val transXF = vs.javaClass.getField("mXTranslation")
                                        transXF.setFloat(vs, 0.0f)
                                    } catch (_: Throwable) {}
                                }
                                val result = chain.proceed()
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val view = args[0] as? View
                                if (view != null) {
                                    view.visibility = View.VISIBLE
                                    view.alpha = 1.0f
                                    view.scaleX = 1.0f
                                    view.scaleY = 1.0f
                                    view.translationX = 0.0f
                                }
                                return result
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ViewState.applyToView", t)
        }
    }

    private fun hookStatusBarTouch(module: XposedModule, classLoader: ClassLoader) {
        try {
            val psbvClass = Class.forName("com.android.systemui.statusbar.phone.PhoneStatusBarView", false, classLoader)
            for (m in psbvClass.declaredMethods) {
                if ((m.name == "dispatchTouchEvent" || m.name == "onTouchEvent") &&
                    m.parameterTypes.size == 1 && m.parameterTypes[0] == MotionEvent::class.java
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val rawArgs = chain.args
                            val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                            val ev = args[0] as? MotionEvent
                            if (ev != null) {
                                handleStatusBarTouch(ev)
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook PhoneStatusBarView touch", t)
        }
    }

    private fun hookPanelViewTouch(module: XposedModule, classLoader: ClassLoader) {
        try {
            val npvClass = Class.forName("com.android.systemui.shade.NotificationPanelView", false, classLoader)
            for (m in npvClass.declaredMethods) {
                if (m.name == "dispatchTouchEvent" && m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == MotionEvent::class.java
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val rawArgs = chain.args
                            val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                            val ev = args[0] as? MotionEvent
                            if (ev != null) {
                                handleStatusBarTouch(ev)
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationPanelView.dispatchTouchEvent", t)
        }
    }

    private fun hookMiuiPanelAnimController(module: XposedModule, classLoader: ClassLoader) {
        try {
            val animClass = Class.forName("com.android.systemui.shade.MiuiNotificationPanelAnimControllerImpl", false, classLoader)
            for (m in animClass.declaredMethods) {
                if (m.name == "startPanelVisibleAnimation" || m.name == "startPanelExpandAnimation" || m.name.startsWith("startPanelExpandAnimation")) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val cfg = NotificationSheetConfigHolder.sheet
                            if (cfg.enabled && currentStatusBarState == 0 && !isControlCenterActive()) {
                                val container = containerRef?.get()
                                if (container != null) {
                                    ensureAllRowsVisible(container)
                                }
                                return null
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook MiuiNotificationPanelAnimControllerImpl", t)
        }
    }

    fun ensureAllRowsVisible(container: ViewGroup) {
        try {
            val nsslId = container.resources.getIdentifier("notification_stack_scroller", "id", "com.android.systemui")
            val nssl = if (nsslId != 0) container.findViewById<ViewGroup>(nsslId) else null ?: return
            val count = nssl.childCount
            for (i in 0 until count) {
                val child = nssl.getChildAt(i) ?: continue
                if (child.visibility != View.VISIBLE) {
                    child.visibility = View.VISIBLE
                }
                if (child.alpha < 1.0f) {
                    child.alpha = 1.0f
                }
                if (child.scaleX != 1.0f) {
                    child.scaleX = 1.0f
                }
                if (child.scaleY != 1.0f) {
                    child.scaleY = 1.0f
                }
                if (child.translationX != 0.0f) {
                    child.translationX = 0.0f
                }
                try {
                    val folmeClass = Class.forName("miuix.animation.Folme", false, container.context.classLoader)
                    val cleanMethod = folmeClass.getMethod("clean", Any::class.java)
                    cleanMethod.invoke(null, child)
                } catch (_: Throwable) {}

                try {
                    val getInjectorMethod = child.javaClass.getMethod("getInjector")
                    val injector = getInjectorMethod.invoke(child)
                    if (injector != null) {
                        try {
                            injector.javaClass.getMethod("setExtAlpha", Float::class.javaPrimitiveType).invoke(injector, 1.0f)
                            injector.javaClass.getMethod("setExtScaleX", Float::class.javaPrimitiveType).invoke(injector, 1.0f)
                            injector.javaClass.getMethod("setExtScaleY", Float::class.javaPrimitiveType).invoke(injector, 1.0f)
                            injector.javaClass.getMethod("setExtTranslationX", Float::class.javaPrimitiveType).invoke(injector, 0.0f)
                        } catch (_: Throwable) {}
                    }
                } catch (_: Throwable) {}

                try {
                    val getViewStateMethod = child.javaClass.getMethod("getViewState")
                    val vs = getViewStateMethod.invoke(child)
                    if (vs != null) {
                        val alphaF = vs.javaClass.getField("mAlpha")
                        alphaF.setFloat(vs, 1.0f)
                        val hiddenF = vs.javaClass.getField("hidden")
                        hiddenF.setBoolean(vs, false)
                        val scaleXF = vs.javaClass.getField("mScaleX")
                        scaleXF.setFloat(vs, 1.0f)
                        val scaleYF = vs.javaClass.getField("mScaleY")
                        scaleYF.setFloat(vs, 1.0f)
                        val transXF = vs.javaClass.getField("mXTranslation")
                        transXF.setFloat(vs, 0.0f)
                    }
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in ensureAllRowsVisible", t)
        }
    }
}
