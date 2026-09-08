package dev.joseph.hyperbounce.notification

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import dev.joseph.hyperbounce.notification.glass.NotificationGlassSheetView
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
    var currentStatusBarState: Int = 0 // 0 = SHADE, 1 = KEYGUARD, 2 = SHADE_LOCKED

    private var containerRef: WeakReference<ViewGroup>? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var lastAppliedFraction = -1.0f

    @Volatile
    private var lastUnlockTriggerTime = 0L

    fun init(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook StatusBarStateControllerImpl to track active shade / lockscreen state
        hookStatusBarState(module, classLoader)

        // 2. Hook MiuiNQSContainer (notification_container_parent) for lifecycle, glass & pill injection
        hookContainerParent(module, classLoader)

        // 3. Hook FloatFlowProperty to capture 120Hz continuous Folme expansion
        hookFloatFlowProperty(module, classLoader)

        // 4. Hook NotificationHeaderExpandController$notificationCallback$1 (clock expansion listener)
        hookHeaderCallback(module, classLoader)

        // 5. Hook ShadeExpansionStateManager for drag gesture tracking
        hookShadeExpansionState(module, classLoader)

        // 6. Hook NotificationPanelExpandController for show/hide and callbacks
        hookExpandController(module, classLoader)

        // 7. Hook NotificationPanelViewController for fling close detection
        hookNpvc(module, classLoader)

        // 8. Hook ViewStateInjectorImpl to eliminate stock rolling and row scaling animations
        hookViewStateInjector(module, classLoader)
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
                                    // Not in unlocked shade (e.g. keyguard active): restore container translation
                                    mainHandler.post {
                                        val container = containerRef?.get()
                                        if (container != null && !isLockScreenManaging(container)) {
                                            container.translationY = 0.0f
                                            val sheet = container.findViewWithTag<View>(TAG_GLASS_SHEET)
                                            sheet?.visibility = View.GONE
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

    private fun isLockScreenManaging(container: ViewGroup): Boolean {
        // On lockscreen, LockScreenSheetHook manages translationY if active
        return currentStatusBarState == 1
    }

    private fun hookContainerParent(module: XposedModule, classLoader: ClassLoader) {
        try {
            val containerClass = Class.forName("com.android.systemui.shade.MiuiNQSContainer", false, classLoader)
            for (m: Method in containerClass.declaredMethods) {
                if (m.name == "onAttachedToWindow") {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val container = chain.thisObject as? ViewGroup ?: return result
                                containerRef = WeakReference(container)
                                mainHandler.post {
                                    ensureGlassSheetInjected(container)
                                    ensureHomePillInjected(container)
                                    adjustNotificationMargins(container)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in onAttachedToWindow hook", t)
                            }
                            return result
                        }
                    })
                } else if (m.name == "onLayout" && m.parameterTypes.size == 5) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val container = chain.thisObject as? ViewGroup ?: return result
                                containerRef = WeakReference(container)
                                ensureGlassSheetInjected(container)
                                ensureHomePillInjected(container)
                                adjustNotificationMargins(container)
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in onLayout hook", t)
                            }
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook MiuiNQSContainer", t)
        }
    }

    private fun ensureGlassSheetInjected(container: ViewGroup) {
        try {
            val cfg = NotificationSheetConfigHolder.sheet
            var sheet = container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)

            if (!cfg.enabled) {
                sheet?.visibility = View.GONE
                return
            }

            if (sheet == null) {
                sheet = NotificationGlassSheetView(container.context).apply {
                    tag = TAG_GLASS_SHEET
                    visibility = View.INVISIBLE
                }
                val lp = try {
                    val lpClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams", false, container.context.classLoader)
                    val constructor = lpClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    val inst = constructor.newInstance(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    ) as ViewGroup.MarginLayoutParams
                    applyConstraintParentParams(inst, container)
                    inst
                } catch (_: Throwable) {
                    ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                container.addView(sheet, 0, lp)
            }

            // Bind the real-time blurred shade background layer (ShadeBackgroundView)
            val parent = container.parent as? ViewGroup
            if (parent != null && sheet.shadeBackgroundView == null) {
                val shadeBgId = container.resources.getIdentifier("shade_background", "id", "com.android.systemui")
                val shadeBg = if (shadeBgId != 0) parent.findViewById<View>(shadeBgId) else null
                sheet.shadeBackgroundView = shadeBg
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to inject NotificationGlassSheetView", t)
        }
    }

    private fun ensureHomePillInjected(container: ViewGroup) {
        try {
            val cfg = NotificationSheetConfigHolder.sheet
            var pill = container.findViewWithTag<View>(TAG_HOME_PILL)

            if (!cfg.enabled) {
                pill?.visibility = View.GONE
                return
            }

            if (pill == null) {
                val context = container.context
                val density = context.resources.displayMetrics.density
                val pillW = (cfg.pillWidth.coerceAtLeast(40f) * density).toInt()
                val pillH = (cfg.pillHeight.coerceAtLeast(2f) * density).toInt()
                val radius = pillH / 2f
                val bMargin = (cfg.pillBottomMargin * density).toInt()

                pill = View(context).apply {
                    tag = TAG_HOME_PILL
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(0xF0FFFFFF.toInt())
                        cornerRadius = radius
                    }
                    elevation = 16f * density
                    visibility = if (lastAppliedFraction > 0.005f) View.VISIBLE else View.INVISIBLE
                }

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
                    android.widget.FrameLayout.LayoutParams(pillW, pillH, android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL).apply {
                        bottomMargin = bMargin
                    }
                }

                setupPillTouchListener(pill, container)
                container.addView(pill, lp)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to inject home pill", t)
        }
    }

    private var isPillDragging = false
    private var pillTouchDownY = 0f
    private var pillInitialTransY = 0f

    private fun setupPillTouchListener(pill: View, container: ViewGroup) {
        pill.setOnTouchListener { _, event ->
            val cfg = NotificationSheetConfigHolder.sheet
            if (!cfg.enabled || currentStatusBarState != 0) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isPillDragging = true
                    pillTouchDownY = event.rawY
                    pillInitialTransY = container.translationY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isPillDragging) {
                        val dy = event.rawY - pillTouchDownY
                        if (dy < 0f) { // Dragging upward to close
                            val newTransY = (pillInitialTransY + dy).coerceAtMost(0f)
                            container.translationY = newTransY
                            var viewHeight = container.height.toFloat()
                            if (viewHeight <= 0f) viewHeight = container.resources.displayMetrics.heightPixels.toFloat()
                            if (viewHeight <= 0f) viewHeight = 2400f
                            val fraction = (1.0f + (newTransY / viewHeight)).coerceIn(0.0f, 1.0f)
                            val sheet = container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)
                            sheet?.visibility = if (fraction > 0.005f) View.VISIBLE else View.INVISIBLE
                            sheet?.invalidate()
                        }
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isPillDragging) {
                        isPillDragging = false
                        val dy = event.rawY - pillTouchDownY
                        val density = pill.resources.displayMetrics.density
                        val threshold = 50f * density

                        if (dy < -threshold) {
                            // Swiped up enough to close: collapse shade and trigger homescreen unlock anim if on launcher
                            triggerShadeClose(pill.context)
                        } else {
                            // Rebound back to open
                            container.animate()
                                .translationY(0f)
                                .setDuration(220)
                                .setInterpolator(DecelerateInterpolator())
                                .withEndAction {
                                    container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)?.invalidate()
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
            notifyCloseOverHomescreen(context)
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
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
            val tasks = am.getRunningTasks(1)
            if (!tasks.isNullOrEmpty()) {
                val topPkg = tasks[0].topActivity?.packageName
                return topPkg == "com.miui.home"
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error checking top activity", e)
        }
        return false
    }

    fun notifyCloseOverHomescreen(context: Context) {
        val now = System.currentTimeMillis()
        if (now - lastUnlockTriggerTime < 800L) return
        lastUnlockTriggerTime = now

        if (isHomeScreenTop(context)) {
            try {
                val intent = Intent("dev.joseph.hyperbounce.ACTION_TRIGGER_UNLOCK_ANIMATION").apply {
                    setPackage("com.miui.home")
                }
                context.sendBroadcast(intent)
                Log.d(TAG, "Dispatched ACTION_TRIGGER_UNLOCK_ANIMATION to com.miui.home")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to send unlock broadcast", t)
            }
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

            if (lp.marginStart != targetStart || lp.marginEnd != targetEnd) {
                lp.marginStart = targetStart
                lp.marginEnd = targetEnd
                lp.leftMargin = targetStart
                lp.rightMargin = targetEnd
                nssl.layoutParams = lp
                nssl.requestLayout()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to adjust notification margins", t)
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
                                if (callerName.contains("NotificationPanelExpansionAnimator")) {
                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                    val frac = (args[1] as Number).toFloat()
                                    applyExpansionFraction(frac)
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
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val frac = (args[0] as Number).toFloat()
                                applyExpansionFraction(frac)
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
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val frac = (args[0] as Number).toFloat()
                                applyExpansionFraction(frac)
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
                                    if (!anim) {
                                        applyExpansionFraction(1.0f)
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
                                    if (!anim) {
                                        applyExpansionFraction(0.0f)
                                    }
                                    val container = containerRef?.get()
                                    if (container != null) {
                                        notifyCloseOverHomescreen(container.context)
                                    }
                                } catch (_: Throwable) {}
                                return result
                            }
                        })
                    }
                    m.name.startsWith("notifyExpandHeightChanged") && m.parameterTypes.size == 4 -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val result = chain.proceed()
                                try {
                                    val cfg = NotificationSheetConfigHolder.sheet
                                    if (!cfg.enabled || currentStatusBarState != 0) return result

                                    val rawArgs = chain.args
                                    val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                    val expandHeight = (args[0] as Number).toFloat()
                                    val expandThresh = (args[1] as Number).toFloat()

                                    val fraction = if (expandThresh > 0f) (expandHeight / expandThresh).coerceIn(0.0f, 1.0f) else 0.0f
                                    applyExpansionFraction(fraction)
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
        try {
            val npvcClass = Class.forName("com.android.systemui.shade.NotificationPanelViewController", false, classLoader)
            for (m: Method in npvcClass.declaredMethods) {
                if (m.name == "fling\$2" && m.parameterTypes.size == 4 &&
                    m.parameterTypes[2] == Boolean::class.javaPrimitiveType
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val result = chain.proceed()
                            try {
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<*>
                                val expanding = args[2] as Boolean
                                if (!expanding && currentStatusBarState == 0) {
                                    val npvc = chain.thisObject
                                    val viewField = npvc.javaClass.getDeclaredField("mView")
                                    viewField.isAccessible = true
                                    val view = viewField.get(npvc) as? View
                                    if (view != null) {
                                        notifyCloseOverHomescreen(view.context)
                                    }
                                }
                            } catch (_: Throwable) {}
                            return result
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook NotificationPanelViewController", t)
        }
    }

    fun applyExpansionFraction(fraction: Float) {
        val f = fraction.coerceIn(0.0f, 1.0f)
        if (abs(f - lastAppliedFraction) < 0.001f) return
        lastAppliedFraction = f

        if (Looper.myLooper() == Looper.getMainLooper()) {
            applyExpansionFractionInternal(f)
        } else {
            mainHandler.post {
                applyExpansionFractionInternal(f)
            }
        }
    }

    private fun applyExpansionFractionInternal(fraction: Float) {
        val container = containerRef?.get() ?: return
        val cfg = NotificationSheetConfigHolder.sheet
        if (!cfg.enabled || currentStatusBarState != 0) return

        var viewHeight = container.height.toFloat()
        if (viewHeight <= 0f) {
            viewHeight = container.resources.displayMetrics.heightPixels.toFloat()
        }
        if (viewHeight <= 0f) viewHeight = 2400f

        val upwardOffset = (1.0f - fraction) * -viewHeight
        container.translationY = upwardOffset
        container.scaleX = 1.0f
        container.scaleY = 1.0f

        val sheet = container.findViewWithTag<NotificationGlassSheetView>(TAG_GLASS_SHEET)
        if (sheet != null) {
            sheet.visibility = if (fraction > 0.005f) View.VISIBLE else View.INVISIBLE
            if (sheet.visibility == View.VISIBLE) {
                sheet.invalidate()
            }
        }

        val pill = container.findViewWithTag<View>(TAG_HOME_PILL)
        if (pill != null) {
            pill.visibility = if (fraction > 0.005f) View.VISIBLE else View.INVISIBLE
        }
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

                    // Stop individual row scale animations: keep scale = 1.0f for type 11018
                    (m.name == "setFolmeScaleXForType" || m.name == "setFolmeScaleYForType") &&
                    m.parameterTypes.size == 3 &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType &&
                    m.parameterTypes[2] == Int::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                val animType = (args[2] as Number).toInt()

                                if (cfg.enabled && currentStatusBarState == 0 && animType == 11018) {
                                    args[1] = 1.0f
                                    return chain.proceed(args)
                                }
                                return chain.proceed()
                            }
                        })
                    }

                    // Stop staggered row vertical translation: keep translationY = 0.0f for type 11018
                    m.name == "setFolmeTranslationYForType" &&
                    m.parameterTypes.size == 3 &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType &&
                    m.parameterTypes[2] == Int::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                val animType = (args[2] as Number).toInt()

                                if (cfg.enabled && currentStatusBarState == 0 && animType == 11018) {
                                    args[1] = 0.0f
                                    return chain.proceed(args)
                                }
                                return chain.proceed()
                            }
                        })
                    }

                    // Keep full alpha for static rows in sheet container: keep alpha = 1.0f for type 11018
                    m.name == "setFolmeAlphaForType" &&
                    m.parameterTypes.size == 3 &&
                    m.parameterTypes[1] == Float::class.javaPrimitiveType &&
                    m.parameterTypes[2] == Int::class.javaPrimitiveType -> {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val cfg = NotificationSheetConfigHolder.sheet
                                val rawArgs = chain.args
                                val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as Array<Any?>
                                val animType = (args[2] as Number).toInt()

                                if (cfg.enabled && currentStatusBarState == 0 && animType == 11018) {
                                    args[1] = 1.0f
                                    return chain.proceed(args)
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
}
