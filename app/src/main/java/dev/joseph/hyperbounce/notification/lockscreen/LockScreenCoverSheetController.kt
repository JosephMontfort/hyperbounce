package dev.joseph.hyperbounce.notification.lockscreen

import android.content.Context
import android.graphics.Canvas
import android.util.Log
import android.view.View
import android.view.ViewGroup
import dev.joseph.hyperbounce.notification.NotificationSheetConfigHolder
import java.lang.ref.WeakReference

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
        val cfg = NotificationSheetConfigHolder.sheet
        if (!cfg.enabled || !cfg.lockScreenAsNotifCenterEnabled || alpha <= 0.01f) return

        resolveClockViews(rootView)

        val hasNotif = (lastKnownHasNotification == true)
        val morphFraction = if (hasNotif) 1.0f else 0.0f

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

                    val clockScale = if (hasNotif) 0.72f else 1.0f
                    val clockShiftY = if (hasNotif) -24f * density else 0f

                    val saveCount = canvas.save()
                    canvas.translate(0f, translationY + clockShiftY)
                    if (hasNotif) {
                        canvas.scale(clockScale, clockScale, viewW / 2f, 40f * density)
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
                    morphFraction = morphFraction,
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
            if (headerView != null) {
                val cfg = NotificationSheetConfigHolder.sheet
                if (cfg.enabled && cfg.lockScreenAsNotifCenterEnabled) {
                    // When lock screen as notification center is active, never show stock shade header clock!
                    headerView.alpha = 0.0f
                    headerView.visibility = View.GONE
                } else {
                    if (isSuppress) {
                        headerView.alpha = 0.0f
                        headerView.visibility = View.INVISIBLE
                    } else {
                        headerView.alpha = 1.0f
                        headerView.visibility = View.VISIBLE
                    }
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
            val cfg = NotificationSheetConfigHolder.sheet
            var bottomArea = container.findViewWithTag<LockScreenBottomAreaView>(TAG_BOTTOM_AFFORDANCES)

            if (!cfg.enabled || !cfg.lockScreenAsNotifCenterEnabled || !cfg.showBottomWidgets) {
                bottomArea?.visibility = View.GONE
                return
            }

            if (bottomArea == null) {
                bottomArea = LockScreenBottomAreaView(container.context).apply {
                    tag = TAG_BOTTOM_AFFORDANCES
                    visibility = View.GONE
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
                Log.d(TAG, "Injected LockScreenBottomAreaView successfully")
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
     * Counts visible notification rows inside notification_stack_scroller using the exact
     * HyperOS Keyguard / NSSL criteria (excluding gone views and non-notification rows).
     */
    fun getVisibleNotificationCount(nssl: ViewGroup?): Int {
        if (nssl == null) return 0
        var count = 0
        try {
            val childCount = nssl.childCount
            for (i in 0 until childCount) {
                val child = nssl.getChildAt(i) ?: continue
                if (child.visibility != View.GONE) {
                    val name = child.javaClass.simpleName
                    if (name.contains("Empty") || name.contains("Footer") || name.contains("Shelf") || name.contains("SectionHeader")) {
                        continue
                    }
                    if (name.contains("NotificationRow") || name.contains("MediaHeader") || name.contains("ZenMode")) {
                        count++
                    }
                }
            }
        } catch (_: Throwable) {}
        return count
    }

    /**
     * Calculates the effective bottom coordinate of the clock container.
     */
    fun getEffectiveClockBottom(container: ViewGroup?): Int {
        if (container == null) return 0
        val density = container.resources.displayMetrics.density
        val hasNotif = (lastKnownHasNotification == true)
        try {
            val clockContainer = getAuthenticClockContainer(container.rootView)
            if (clockContainer != null) {
                val getClockBottomMethod = clockContainer.javaClass.getMethod("getClockBottom")
                val bottom = (getClockBottomMethod.invoke(clockContainer) as? Number)?.toInt() ?: 0
                if (bottom in (80f * density).toInt()..(450f * density).toInt()) {
                    return if (hasNotif) kotlin.math.min(bottom, (170f * density).toInt()) else bottom
                }
            }
        } catch (_: Throwable) {}

        return if (hasNotif) (145f * density).toInt() else (320f * density).toInt()
    }

    /**
     * Synchronizes the authentic SystemUI keyguard clock with live notifications:
     * 1. Inspects notification_stack_scroller visible count.
     * 2. Sets KeyguardClockContainer.mHasNotification = hasNotif.
     * 3. Calls AnimationHelper.doAnimationToNotification(hasNotif, true) to trigger
     *    the authentic Xiaomi lockscreen Folme morph animation (shrink when notifications exist,
     *    expand to full size when empty).
     * 4. Dynamically adjusts NSSL topMargin and bottomMargin so notifications flow
     *    strictly beneath the clock and above the bottom affordances with proper clipping.
     */
    fun syncClockWithNotifications(container: ViewGroup?) {
        if (container == null) return
        val cfg = NotificationSheetConfigHolder.sheet
        if (!cfg.enabled || !cfg.lockScreenAsNotifCenterEnabled) return

        try {
            val nsslId = container.resources.getIdentifier("notification_stack_scroller", "id", "com.android.systemui")
            val nssl = if (nsslId != 0) container.findViewById<ViewGroup>(nsslId) else null

            val visibleCount = getVisibleNotificationCount(nssl)
            val hasNotif = visibleCount > 0

            val notifChanged = (lastKnownHasNotification != hasNotif)
            lastKnownHasNotification = hasNotif

            val clockContainer = getAuthenticClockContainer(container.rootView)
            if (clockContainer != null) {
                try {
                    val hasNotifField = clockContainer.javaClass.getDeclaredField("mHasNotification")
                    hasNotifField.isAccessible = true
                    val currentInContainer = hasNotifField.getBoolean(clockContainer)

                    if (notifChanged || currentInContainer != hasNotif) {
                        hasNotifField.setBoolean(clockContainer, hasNotif)

                        val animHelperField = clockContainer.javaClass.getDeclaredField("mAnimationHelper")
                        animHelperField.isAccessible = true
                        val animHelper = animHelperField.get(clockContainer)
                        if (animHelper != null) {
                            val doAnimMethod = animHelper.javaClass.getMethod(
                                "doAnimationToNotification",
                                Boolean::class.javaPrimitiveType,
                                Boolean::class.javaPrimitiveType
                            )
                            doAnimMethod.invoke(animHelper, hasNotif, true)
                            Log.d(TAG, "Triggered authentic doAnimationToNotification(hasNotif=$hasNotif)")
                        }

                        // Also update clock view full time state directly
                        try {
                            val controllerField = clockContainer.javaClass.getDeclaredField("mMiuiClockController")
                            controllerField.isAccessible = true
                            val controller = controllerField.get(clockContainer)
                            if (controller != null) {
                                val clockViewField = controller.javaClass.getDeclaredField("mClockView")
                                clockViewField.isAccessible = true
                                val clockView = clockViewField.get(controller)
                                if (clockView != null) {
                                    val setShowFullTimeMethod = clockView.javaClass.getMethod("setShowFullTime", Boolean::class.javaPrimitiveType)
                                    setShowFullTimeMethod.invoke(clockView, hasNotif)
                                }
                            }
                        } catch (_: Throwable) {}

                        clockContainer.requestLayout()
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error animating authentic clock view to notification state", t)
                }
            }

            // Adjust NSSL layout & margins so notification cards do not collide with clock
            if (nssl != null) {
                val lp = nssl.layoutParams as? ViewGroup.MarginLayoutParams
                if (lp != null) {
                    val density = container.resources.displayMetrics.density
                    val clockBottom = getEffectiveClockBottom(container)
                    val targetTop = clockBottom + (12f * density).toInt()
                    val targetBottom = (120f * density).toInt()

                    var changed = false
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
                        nssl.requestLayout()
                    }
                    nssl.clipToPadding = true
                    nssl.clipChildren = true
                    (nssl.parent as? ViewGroup)?.clipChildren = true
                    if (nssl.paddingTop != 0 || nssl.paddingBottom != 0) {
                        nssl.setPadding(nssl.paddingLeft, 0, nssl.paddingRight, 0)
                    }
                }
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
            val cfg = NotificationSheetConfigHolder.sheet
            if (!cfg.enabled || !cfg.lockScreenAsNotifCenterEnabled) return

            // 1. Suppress stock shade header when pulling down
            suppressStockShadeHeader(container.rootView, fraction > 0.015f)

            // 2. Synchronize authentic lockscreen clock with notifications
            syncClockWithNotifications(container)

            // 3. Ensure bottom affordances injected and tracked
            ensureBottomAffordancesInjected(container)
            val bottomArea = container.findViewWithTag<LockScreenBottomAreaView>(TAG_BOTTOM_AFFORDANCES)
            if (bottomArea != null) {
                if (cfg.showBottomWidgets) {
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
            val cfg = NotificationSheetConfigHolder.sheet
            if (!cfg.enabled || !cfg.lockScreenAsNotifCenterEnabled) return

            suppressStockShadeHeader(container.rootView, true)
            syncClockWithNotifications(container)
            ensureBottomAffordancesInjected(container)
            val bottomArea = container.findViewWithTag<LockScreenBottomAreaView>(TAG_BOTTOM_AFFORDANCES)
            if (bottomArea != null && cfg.showBottomWidgets) {
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
            // Restore stock shade header
            suppressStockShadeHeader(container.rootView, false)

            // Hide bottom affordances
            container.findViewWithTag<View>(TAG_BOTTOM_AFFORDANCES)?.visibility = View.GONE

            lastKnownHasNotification = null
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onShadeReset", t)
        }
    }
}
