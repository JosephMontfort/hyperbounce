package dev.joseph.hyperbounce.notification.lockscreen

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.view.View
import android.view.ViewGroup
import dev.joseph.hyperbounce.notification.NotificationSheetConfigHolder
import java.lang.ref.WeakReference
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Isolated controller for the "Lock Screen as Notification Center" feature.
 * Coordinates:
 * 1. Suppressing stock Xiaomi notification shade header & clock during pulldown.
 * 2. Retrieving and rendering authentic lockscreen clock views directly onto NotificationGlassSheetView.
 * 3. Bottom quick affordance widgets (Torch & Camera) injection and expansion tracking.
 * 4. Notification stack scroll layout clearance and state reset.
 */
object LockScreenCoverSheetController {

    private const val TAG = "HyperBounce_LSCover"
    private const val TAG_BOTTOM_AFFORDANCES = "hyperbounce_notif_bottom_affordances"

    enum class ClockLayer {
        BACKGROUND, // Behind depth cutout (e.g. OversizeBMinuteClock or full clock for Classic/Rhombus/Magazine)
        FOREGROUND  // In front of depth cutout (e.g. OversizeBHourClock)
    }

    private var cachedClockContainerRef: WeakReference<ViewGroup>? = null
    private var cachedFgClockContainerRef: WeakReference<ViewGroup>? = null
    private var cachedHeaderViewRef: WeakReference<View>? = null
    private var isHeaderSuppressedByUs = false

    // Cached reflection fields for zero-overhead live clock sync (option 2)
    private var cachedCanScaleField: Field? = null
    private var cachedHasNotifField: Field? = null
    private var cachedAnimHelperField: Field? = null
    private var cachedDoAnimMethod: Method? = null
    private var cachedControllerField: Field? = null
    private var cachedClockViewField: Field? = null
    private var cachedSetShowFullTimeMethod: Method? = null
    private var cachedNsslId: Int = 0
    private var cachedNsslRef: WeakReference<ViewGroup>? = null

    @Volatile
    var currentMorphFraction: Float = 0f
        private set

    private var targetMorphFraction: Float = 0f
    private var morphAnimator: android.animation.ValueAnimator? = null

    fun updateMorphFraction(target: Float) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                updateMorphFraction(target)
            }
            return
        }
        if (kotlin.math.abs(target - targetMorphFraction) < 0.01f && morphAnimator?.isRunning == true) return
        targetMorphFraction = target
        morphAnimator?.cancel()
        val anim = android.animation.ValueAnimator.ofFloat(currentMorphFraction, target).apply {
            duration = 320L
            interpolator = android.view.animation.PathInterpolator(0.2f, 0f, 0.2f, 1f)
            addUpdateListener { va ->
                currentMorphFraction = va.animatedValue as Float
                val container = dev.joseph.hyperbounce.notification.NotificationSheetHook.getContainer()
                val sheet = container?.findViewWithTag<View>("hyperbounce_notif_glass_sheet")
                sheet?.postInvalidateOnAnimation()
            }
        }
        morphAnimator = anim
        anim.start()
    }

    /**
     * Resolves the authentic lockscreen clock views from the SystemUI view hierarchy.
     */
    private fun resolveClockViews(rootView: View?) {
        if (rootView == null) return
        try {
            if (cachedClockContainerRef?.get() == null) {
                val clockId = rootView.resources.getIdentifier("miui_keyguard_clock_container", "id", "com.android.systemui")
                if (clockId != 0) {
                    val clockView = rootView.findViewById<ViewGroup>(clockId)
                    if (clockView != null) {
                        cachedClockContainerRef = WeakReference(clockView)
                    }
                }
            }
            if (cachedFgClockContainerRef?.get() == null) {
                val fgId = rootView.resources.getIdentifier("miui_keyguard_foreground_clock_container", "id", "com.android.systemui")
                if (fgId != 0) {
                    val fgView = rootView.findViewById<ViewGroup>(fgId)
                    if (fgView != null) {
                        cachedFgClockContainerRef = WeakReference(fgView)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error resolving clock views", t)
        }
    }

    /**
     * Draws the authentic lockscreen clock or falls back to vector text rendering.
     */
    fun drawClock(
        canvas: Canvas,
        rootView: View?,
        context: Context,
        w: Int,
        h: Int,
        density: Float,
        layer: ClockLayer,
        alpha: Float,
        translationY: Float
    ) {
        val isLandscape = context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE || w > h
        if (isLandscape) return

        val cfg = NotificationSheetConfigHolder.sheet
        if (!cfg.isLockScreenActive || alpha <= 0.01f) return

        resolveClockViews(rootView)

        val container = dev.joseph.hyperbounce.notification.NotificationSheetHook.getContainer()
        val nsslId = container?.resources?.getIdentifier("notification_stack_scroller", "id", "com.android.systemui") ?: 0
        val nssl = if (nsslId != 0) container?.findViewById<ViewGroup>(nsslId) else null
        val hasNotif = if (nssl != null) hasActiveNotifications(nssl) else (lastKnownHasNotification == true)
        lastKnownHasNotification = hasNotif

        val targetFraction = if (hasNotif) 1.0f else 0.0f
        if (kotlin.math.abs(targetFraction - targetMorphFraction) > 0.01f) {
            updateMorphFraction(targetFraction)
        }
        val morph = currentMorphFraction

        val targetView: ViewGroup? = when (layer) {
            ClockLayer.BACKGROUND -> cachedClockContainerRef?.get()
            ClockLayer.FOREGROUND -> cachedFgClockContainerRef?.get()
        }

        // If targetView is found and has children, draw the real SystemUI clock view!
        if (targetView != null && targetView.childCount > 0) {
            try {
                val viewW = if (targetView.width > 0) targetView.width else w
                val viewH = if (targetView.height > 0) targetView.height else h

                if (targetView.width <= 0 || targetView.height <= 0) {
                    val wSpec = View.MeasureSpec.makeMeasureSpec(viewW, View.MeasureSpec.EXACTLY)
                    val hSpec = View.MeasureSpec.makeMeasureSpec(viewH, View.MeasureSpec.EXACTLY)
                    targetView.measure(wSpec, hSpec)
                    targetView.layout(0, 0, viewW, viewH)
                }

                val prevVis = targetView.visibility
                val prevAlpha = targetView.alpha
                val prevTransX = targetView.translationX
                val prevTransY = targetView.translationY
                val prevScaleX = targetView.scaleX
                val prevScaleY = targetView.scaleY

                try {
                    targetView.visibility = View.VISIBLE
                    targetView.alpha = 1.0f

                    try {
                        val canScaleField = targetView.javaClass.getDeclaredField("mClockCanScale")
                        canScaleField.isAccessible = true
                        canScaleField.setBoolean(targetView, true)
                    } catch (_: Throwable) {}

                    val clockScale = 1.0f - (0.32f * morph)
                    val clockShiftY = -18f * density * morph

                    val saveCount = canvas.save()
                    canvas.translate(0f, translationY + clockShiftY)
                    if (morph > 0.001f) {
                        canvas.scale(clockScale, clockScale, viewW / 2f, 50f * density)
                    }
                    if (alpha < 0.999f) {
                        canvas.saveLayerAlpha(
                            0f, 0f, viewW.toFloat(), viewH.toFloat(),
                            (alpha * 255f).toInt().coerceIn(0, 255)
                        )
                    }
                    targetView.draw(canvas)
                    canvas.restoreToCount(saveCount)
                } finally {
                    targetView.visibility = prevVis
                    targetView.alpha = prevAlpha
                    targetView.translationX = prevTransX
                    targetView.translationY = prevTransY
                    targetView.scaleX = prevScaleX
                    targetView.scaleY = prevScaleY
                }
                return
            } catch (t: Throwable) {
                Log.e(TAG, "Error drawing authentic keyguard clock view", t)
            }
        }

        // Fallback: If authentic clock view not found or empty on BACKGROUND layer, use LockScreenClockHelper
        if (layer == ClockLayer.BACKGROUND) {
            try {
                val clockConfig = LockScreenInfoReader.getConfig(context)
                LockScreenClockHelper.drawClock(
                    canvas = canvas,
                    context = context,
                    w = w,
                    h = h,
                    density = density,
                    config = clockConfig,
                    morphFraction = morph,
                    alpha = alpha,
                    translationY = translationY
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Error drawing fallback clock", t)
            }
        }
    }

    /**
     * Suppresses the stock Xiaomi shade header clock so it does not animate from status bar to panel.
     */
    fun suppressStockShadeHeader(rootView: View?, isSuppress: Boolean) {
        if (rootView == null) return
        try {
            val cfg = NotificationSheetConfigHolder.sheet
            val isUnlocked = dev.joseph.hyperbounce.notification.NotificationSheetHook.currentStatusBarState == 0
            val isLandscape = rootView.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

            var headerView = cachedHeaderViewRef?.get()
            if (headerView == null) {
                val headerId = rootView.resources.getIdentifier("normal_notification_header_view", "id", "com.android.systemui")
                if (headerId != 0) {
                    headerView = rootView.findViewById(headerId)
                    if (headerView != null) {
                        cachedHeaderViewRef = WeakReference(headerView)
                    }
                }
            }

            if (!isUnlocked) {
                // On lockscreen, normal_notification_header_view MUST ALWAYS be hidden!
                headerView?.alpha = 0.0f
                headerView?.visibility = View.GONE
                isHeaderSuppressedByUs = false
                return
            }

            if (!cfg.isLockScreenActive || isLandscape) {
                // When feature is disabled or in landscape, only restore if we previously suppressed it!
                if (isHeaderSuppressedByUs) {
                    headerView?.alpha = 1.0f
                    isHeaderSuppressedByUs = false
                }
                return
            }

            if (headerView != null) {
                if (isSuppress) {
                    headerView.alpha = 0.0f
                    headerView.visibility = View.GONE
                    isHeaderSuppressedByUs = true
                } else if (isHeaderSuppressedByUs) {
                    headerView.alpha = 1.0f
                    isHeaderSuppressedByUs = false
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error suppressing stock shade header", t)
        }
    }

    /**
     * Ensures bottom affordances (Torch & Camera) are injected into the container.
     */
    fun ensureBottomAffordancesInjected(container: ViewGroup) {
        try {
            val isLandscape = container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            var bottomArea = container.findViewWithTag<LockScreenBottomAreaView>(TAG_BOTTOM_AFFORDANCES)
            if (isLandscape) {
                bottomArea?.visibility = View.GONE
                return
            }

            val cfg = NotificationSheetConfigHolder.sheet
            if (!cfg.isLockScreenActive || !cfg.showBottomWidgets) {
                bottomArea?.visibility = View.GONE
                return
            }

            if (bottomArea == null) {
                val density = container.resources.displayMetrics.density
                bottomArea = LockScreenBottomAreaView(container.context).apply {
                    tag = TAG_BOTTOM_AFFORDANCES
                    visibility = View.GONE
                    elevation = 64f * density
                    translationZ = 64f * density
                }
                val lp = try {
                    val lpClass = Class.forName("androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams", false, container.context.classLoader)
                    val constructor = lpClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    val inst = constructor.newInstance(0, 0) as ViewGroup.MarginLayoutParams
                    val fields = arrayOf("leftToLeft", "rightToRight", "topToTop", "bottomToBottom", "startToStart", "endToEnd")
                    for (fieldName in fields) {
                        try {
                            val f = lpClass.getField(fieldName)
                            f.setInt(inst, 0)
                        } catch (_: Throwable) {}
                    }
                    inst
                } catch (_: Throwable) {
                    android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                container.addView(bottomArea, lp)
                bottomArea.bringToFront()
                Log.d(TAG, "Injected LockScreenBottomAreaView successfully")
            } else {
                bottomArea.bringToFront()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to inject LockScreenBottomAreaView", t)
        }
    }

    private var lastKnownHasNotification: Boolean? = null

    /**
     * Resolves the authentic lockscreen clock views from the SystemUI view hierarchy.
     */
    fun getAuthenticClockContainer(rootView: View?): ViewGroup? {
        resolveClockViews(rootView)
        return cachedClockContainerRef?.get()
    }

    /**
     * Accurately determines if active notifications exist inside notification_stack_scroller.
     */
    fun hasActiveNotifications(nssl: ViewGroup?): Boolean {
        if (nssl == null) return false

        // 1. If EmptyShadeView is visible, there are definitely 0 notifications
        try {
            for (i in 0 until nssl.childCount) {
                val child = nssl.getChildAt(i) ?: continue
                val name = child.javaClass.simpleName
                if (name.contains("Empty", ignoreCase = true) && child.visibility == View.VISIBLE) {
                    return false
                }
            }
        } catch (_: Throwable) {}

        // 2. Query getNotGoneChildCount if available
        try {
            val getNotGone = nssl.javaClass.methods.firstOrNull { it.name == "getNotGoneChildCount" && it.parameterTypes.isEmpty() }
            if (getNotGone != null) {
                val notGone = (getNotGone.invoke(nssl) as? Number)?.toInt() ?: 0
                if (notGone > 0) {
                    for (i in 0 until nssl.childCount) {
                        val child = nssl.getChildAt(i) ?: continue
                        if (child.visibility == View.GONE) continue
                        val name = child.javaClass.simpleName
                        if (!name.contains("Footer", ignoreCase = true) &&
                            !name.contains("Shelf", ignoreCase = true) &&
                            !name.contains("SectionHeader", ignoreCase = true) &&
                            !name.contains("Empty", ignoreCase = true)
                        ) {
                            return true
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        // 3. Fallback child scan
        try {
            for (i in 0 until nssl.childCount) {
                val child = nssl.getChildAt(i) ?: continue
                if (child.visibility == View.GONE) continue
                val name = child.javaClass.simpleName
                if (name.contains("Notification", ignoreCase = true) ||
                    name.contains("Row", ignoreCase = true) ||
                    name.contains("Media", ignoreCase = true) ||
                    name.contains("Zen", ignoreCase = true)
                ) {
                    return true
                }
            }
        } catch (_: Throwable) {}

        return false
    }

    /**
     * Counts visible notification rows inside notification_stack_scroller.
     */
    fun getVisibleNotificationCount(nssl: ViewGroup?): Int {
        return if (hasActiveNotifications(nssl)) 1 else 0
    }

    /**
     * Calculates the effective bottom coordinate of the clock container.
     */
    fun getEffectiveClockBottom(container: ViewGroup?): Int {
        if (container == null) return 0
        val density = container.resources.displayMetrics.density
        val nsslId = container.resources.getIdentifier("notification_stack_scroller", "id", "com.android.systemui")
        val nssl = if (nsslId != 0) container.findViewById<ViewGroup>(nsslId) else null
        val hasNotif = if (nssl != null) hasActiveNotifications(nssl) else (lastKnownHasNotification == true)

        if (hasNotif) {
            return (135f * density).toInt()
        }

        try {
            val clockContainer = getAuthenticClockContainer(container.rootView)
            if (clockContainer != null) {
                val getClockBottomMethod = clockContainer.javaClass.getMethod("getClockBottom")
                val bottom = (getClockBottomMethod.invoke(clockContainer) as? Number)?.toInt() ?: 0
                if (bottom in (180f * density).toInt()..(450f * density).toInt()) {
                    return bottom
                }
            }
        } catch (_: Throwable) {}

        return (310f * density).toInt()
    }

    /**
     * Synchronizes the authentic SystemUI keyguard clock with live notifications:
     * 1. Inspects notification_stack_scroller visible count.
     * 2. Sets KeyguardClockContainer.mHasNotification = hasNotif and enables mClockCanScale.
     * 3. Calls AnimationHelper.doAnimationToNotification(hasNotif, false) to trigger
     *    the authentic Xiaomi lockscreen Folme morph animation (shrink when notifications exist,
     *    expand to full size when empty).
     * 4. Applies non-destructive hardware clipBounds and padding to NSSL.
     */
    private fun getCanScaleField(clazz: Class<*>): Field? {
        if (cachedCanScaleField == null) {
            try {
                cachedCanScaleField = clazz.getDeclaredField("mClockCanScale").apply { isAccessible = true }
            } catch (_: Throwable) {}
        }
        return cachedCanScaleField
    }

    private fun getHasNotifField(clazz: Class<*>): Field? {
        if (cachedHasNotifField == null) {
            try {
                cachedHasNotifField = clazz.getDeclaredField("mHasNotification").apply { isAccessible = true }
            } catch (_: Throwable) {}
        }
        return cachedHasNotifField
    }

    private fun getAnimHelperField(clazz: Class<*>): Field? {
        if (cachedAnimHelperField == null) {
            try {
                cachedAnimHelperField = clazz.getDeclaredField("mAnimationHelper").apply { isAccessible = true }
            } catch (_: Throwable) {}
        }
        return cachedAnimHelperField
    }

    private fun getDoAnimMethod(clazz: Class<*>): Method? {
        if (cachedDoAnimMethod == null) {
            try {
                cachedDoAnimMethod = clazz.getMethod(
                    "doAnimationToNotification",
                    Boolean::class.javaPrimitiveType,
                    Boolean::class.javaPrimitiveType
                )
            } catch (_: Throwable) {}
        }
        return cachedDoAnimMethod
    }

    private fun getControllerField(clazz: Class<*>): Field? {
        if (cachedControllerField == null) {
            try {
                cachedControllerField = clazz.getDeclaredField("mMiuiClockController").apply { isAccessible = true }
            } catch (_: Throwable) {}
        }
        return cachedControllerField
    }

    private fun getClockViewField(clazz: Class<*>): Field? {
        if (cachedClockViewField == null) {
            try {
                cachedClockViewField = clazz.getDeclaredField("mClockView").apply { isAccessible = true }
            } catch (_: Throwable) {}
        }
        return cachedClockViewField
    }

    private fun getSetShowFullTimeMethod(clazz: Class<*>): Method? {
        if (cachedSetShowFullTimeMethod == null) {
            try {
                cachedSetShowFullTimeMethod = clazz.getMethod("setShowFullTime", Boolean::class.javaPrimitiveType)
            } catch (_: Throwable) {}
        }
        return cachedSetShowFullTimeMethod
    }

    /**
     * Synchronizes the authentic SystemUI keyguard clock with live notifications:
     * 1. Inspects notification_stack_scroller visible count.
     * 2. Sets KeyguardClockContainer.mHasNotification = hasNotif and enables mClockCanScale.
     * 3. Calls AnimationHelper.doAnimationToNotification(hasNotif, false) to trigger
     *    the authentic Xiaomi lockscreen Folme morph animation (shrink when notifications exist,
     *    expand to full size when empty).
     * 4. Uses cached reflection fields for zero-overhead performance during swipe pull-down.
     */
    fun syncClockWithNotifications(container: ViewGroup?) {
        if (container == null) return
        val isLandscape = container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) return

        val cfg = NotificationSheetConfigHolder.sheet
        if (!cfg.isLockScreenActive) return

        try {
            var nssl = cachedNsslRef?.get()
            if (nssl == null) {
                if (cachedNsslId == 0) {
                    cachedNsslId = container.resources.getIdentifier("notification_stack_scroller", "id", "com.android.systemui")
                }
                if (cachedNsslId != 0) {
                    nssl = container.findViewById<ViewGroup>(cachedNsslId)
                    if (nssl != null) {
                        cachedNsslRef = WeakReference(nssl)
                    }
                }
            }

            val hasNotif = hasActiveNotifications(nssl)
            val notifChanged = (lastKnownHasNotification != hasNotif)
            lastKnownHasNotification = hasNotif

            val targetFraction = if (hasNotif) 1.0f else 0.0f
            if (kotlin.math.abs(targetFraction - targetMorphFraction) > 0.01f) {
                updateMorphFraction(targetFraction)
            }

            val clockContainer = getAuthenticClockContainer(container.rootView)
            if (clockContainer != null) {
                try {
                    val clockClass = clockContainer.javaClass
                    getCanScaleField(clockClass)?.setBoolean(clockContainer, true)

                    val hasNotifField = getHasNotifField(clockClass)
                    val currentInContainer = hasNotifField?.getBoolean(clockContainer) ?: false

                    if (notifChanged || currentInContainer != hasNotif) {
                        hasNotifField?.setBoolean(clockContainer, hasNotif)

                        val animHelper = getAnimHelperField(clockClass)?.get(clockContainer)
                        if (animHelper != null) {
                            getDoAnimMethod(animHelper.javaClass)?.invoke(animHelper, hasNotif, false)
                            Log.d(TAG, "Triggered authentic doAnimationToNotification(hasNotif=$hasNotif)")
                        }

                        // Also update clock view full time state directly
                        try {
                            val controller = getControllerField(clockClass)?.get(clockContainer)
                            if (controller != null) {
                                val clockView = getClockViewField(controller.javaClass)?.get(controller)
                                if (clockView != null) {
                                    getSetShowFullTimeMethod(clockView.javaClass)?.invoke(clockView, hasNotif)
                                }
                            }
                        } catch (_: Throwable) {}
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error animating authentic clock view to notification state", t)
                }
            }

            // Ensure no artificial clip bounds or padding restrictions are placed on NSSL
            if (nssl != null && nssl.clipBounds != null) {
                nssl.clipBounds = null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in syncClockWithNotifications", t)
        }
    }

    /**
     * Propagates hardware torch changes directly to the bottom affordance button.
     */
    fun onTorchStateChanged(enabled: Boolean) {
        try {
            val container = dev.joseph.hyperbounce.notification.NotificationSheetHook.getContainer() ?: return
            val bottomArea = container.findViewWithTag<LockScreenBottomAreaView>(TAG_BOTTOM_AFFORDANCES)
            bottomArea?.setTorchActive(enabled)
        } catch (_: Throwable) {}
    }

    /**
     * Called on each frame of shade expansion when lockScreenAsNotifCenterEnabled is true.
     */
    fun onExpansionFractionChanged(
        container: ViewGroup,
        fraction: Float,
        finalSheetY: Float,
        currentStretchOffset: Float
    ) {
        try {
            val isLandscape = container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                suppressStockShadeHeader(container.rootView, false)
                container.findViewWithTag<View>(TAG_BOTTOM_AFFORDANCES)?.visibility = View.GONE
                return
            }
            val cfg = NotificationSheetConfigHolder.sheet
            if (!cfg.isLockScreenActive) return

            // 1. Suppress stock shade header when pulling down
            suppressStockShadeHeader(container.rootView, fraction > 0.015f)

            // 2. Synchronize authentic lockscreen clock with notifications (cached reflection, zero overhead)
            syncClockWithNotifications(container)

            // 3. Ensure bottom affordances injected and tracked
            ensureBottomAffordancesInjected(container)
            val bottomArea = container.findViewWithTag<LockScreenBottomAreaView>(TAG_BOTTOM_AFFORDANCES)
            if (bottomArea != null) {
                if (cfg.showBottomWidgets) {
                    bottomArea.bringToFront()
                    bottomArea.translationY = finalSheetY + (if (finalSheetY == 0f) currentStretchOffset else 0f)
                    bottomArea.setExpansionFraction(fraction)
                } else {
                    bottomArea.visibility = View.GONE
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onExpansionFractionChanged", t)
        }
    }

    /**
     * Called when the notification shade is fully opened.
     */
    fun onShadeFullyOpened(container: ViewGroup) {
        try {
            val isLandscape = container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                container.findViewWithTag<View>(TAG_BOTTOM_AFFORDANCES)?.visibility = View.GONE
                return
            }
            val cfg = NotificationSheetConfigHolder.sheet
            if (!cfg.isLockScreenActive) return

            suppressStockShadeHeader(container.rootView, true)
            syncClockWithNotifications(container)
            ensureBottomAffordancesInjected(container)
            val bottomArea = container.findViewWithTag<LockScreenBottomAreaView>(TAG_BOTTOM_AFFORDANCES)
            if (bottomArea != null && cfg.showBottomWidgets) {
                bottomArea.bringToFront()
                bottomArea.visibility = View.VISIBLE
                bottomArea.translationY = 0.0f
                bottomArea.setExpansionFraction(1.0f)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onShadeFullyOpened", t)
        }
    }

    /**
     * Resets cover sheet state when shade is closed or feature is disabled.
     */
    fun onShadeReset(container: ViewGroup) {
        try {
            currentMorphFraction = 0f
            targetMorphFraction = 0f
            morphAnimator?.cancel()

            // Restore stock shade header
            suppressStockShadeHeader(container.rootView, false)

            // Hide bottom affordances
            container.findViewWithTag<View>(TAG_BOTTOM_AFFORDANCES)?.visibility = View.GONE

            val nsslId = container.resources.getIdentifier("notification_stack_scroller", "id", "com.android.systemui")
            val nssl = if (nsslId != 0) container.findViewById<ViewGroup>(nsslId) else null
            nssl?.clipBounds = null

            lastKnownHasNotification = null
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onShadeReset", t)
        }
    }
}
