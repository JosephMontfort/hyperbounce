package dev.hyperios.app.notification.lockscreen

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import dev.hyperios.app.notification.NotificationSheetConfigHolder
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
    private var cachedBottomAreaRef: WeakReference<LockScreenBottomAreaView>? = null
    private var isHeaderSuppressedByUs = false

    private var cachedClockId: Int = 0
    private var cachedFgClockId: Int = 0
    private var cachedHeaderId: Int = 0

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

    private var triedResolvingFgClock: Boolean = false
    private var lastClockResolveAttempt: Long = 0L

    init {
        // Immediate style update: when user changes lockscreen clock theme/style, invalidate references
        LockScreenInfoReader.addListener { _ ->
            cachedClockContainerRef = null
            cachedFgClockContainerRef = null
            triedResolvingFgClock = false
            isClockSyncedBig = false
            Log.d(TAG, "Lockscreen clock style updated, refreshed clock view references immediately")
        }
    }

    @Volatile
    var currentMorphFraction: Float = 0f
        private set

    private var targetMorphFraction: Float = 0f
    private var morphAnimator: android.animation.ValueAnimator? = null
    private var isClockSyncedBig: Boolean = false
    val isBigClockSynced: Boolean get() = isClockSyncedBig
    val isCompactClockSynced: Boolean get() = isClockSyncedBig

    fun updateMorphFraction(target: Float) {
        currentMorphFraction = 0f
        targetMorphFraction = 0f
        morphAnimator?.cancel()
        morphAnimator = null
    }

    fun getBottomAreaView(container: ViewGroup): LockScreenBottomAreaView? {
        val cached = cachedBottomAreaRef?.get()
        if (cached != null && cached.parent == container) {
            return cached
        }
        val view = container.findViewWithTag<LockScreenBottomAreaView>(TAG_BOTTOM_AFFORDANCES)
        if (view != null) {
            cachedBottomAreaRef = WeakReference(view)
        }
        return view
    }

    /**
     * Resolves the authentic lockscreen clock views from the SystemUI view hierarchy.
     */
    private fun resolveClockViews(rootView: View?) {
        if (rootView == null) return
        val now = SystemClock.uptimeMillis()
        if (cachedClockContainerRef?.get() != null && (triedResolvingFgClock || cachedFgClockContainerRef?.get() != null)) {
            return
        }
        if (now - lastClockResolveAttempt < 1000L) {
            return
        }
        lastClockResolveAttempt = now
        try {
            if (cachedClockContainerRef?.get() == null) {
                if (cachedClockId == 0) {
                    cachedClockId = rootView.resources.getIdentifier("miui_keyguard_clock_container", "id", "com.android.systemui")
                }
                if (cachedClockId != 0) {
                    val clockView = rootView.findViewById<ViewGroup>(cachedClockId)
                    if (clockView != null) {
                        cachedClockContainerRef = WeakReference(clockView)
                    }
                }
            }
            if (!triedResolvingFgClock && cachedFgClockContainerRef?.get() == null) {
                if (cachedFgClockId == 0) {
                    cachedFgClockId = rootView.resources.getIdentifier("miui_keyguard_foreground_clock_container", "id", "com.android.systemui")
                }
                if (cachedFgClockId != 0) {
                    val fgView = rootView.findViewById<ViewGroup>(cachedFgClockId)
                    if (fgView != null) {
                        cachedFgClockContainerRef = WeakReference(fgView)
                    }
                }
                triedResolvingFgClock = true
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

        // User requirement: Notification Center clock ALWAYS stays prominent and BIG.
        val morph = 0.0f

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

                val effectiveAlpha = (alpha.coerceIn(0f, 1f) * 255f).toInt()
                val intermediateScale = NotificationSheetConfigHolder.sheet.notifClockScale.coerceIn(0.40f, 1.20f)
                val saveCount = if (effectiveAlpha < 255) {
                    val clockBottom = (targetView.height.toFloat() * intermediateScale).coerceAtLeast(320f * density)
                    canvas.saveLayerAlpha(0f, 0f, viewW.toFloat(), (clockBottom + kotlin.math.abs(translationY)).coerceAtMost(viewH.toFloat()), effectiveAlpha)
                } else {
                    canvas.save()
                }

                try {
                    canvas.translate(0f, translationY)
                    // Apply intermediate scale configured by user centered horizontally and pinned to top
                    val pivotX = viewW / 2.0f
                    val pivotY = 0.0f
                    canvas.scale(intermediateScale, intermediateScale, pivotX, pivotY)
                    targetView.draw(canvas)
                } finally {
                    canvas.restoreToCount(saveCount)
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
                val intermediateScale = NotificationSheetConfigHolder.sheet.notifClockScale.coerceIn(0.40f, 1.20f)
                val pivotX = w / 2.0f
                val pivotY = 0.0f
                val saveCount = canvas.save()
                try {
                    canvas.scale(intermediateScale, intermediateScale, pivotX, pivotY)
                    LockScreenClockHelper.drawClock(
                        canvas = canvas,
                        context = context,
                        w = w,
                        h = h,
                        density = density,
                        config = clockConfig,
                        morphFraction = 0.0f,
                        alpha = alpha,
                        translationY = translationY
                    )
                } finally {
                    canvas.restoreToCount(saveCount)
                }
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
        if (isSuppress == isHeaderSuppressedByUs && cachedHeaderViewRef?.get() != null) return
        try {
            val cfg = NotificationSheetConfigHolder.sheet
            val isUnlocked = dev.hyperios.app.notification.NotificationSheetHook.currentStatusBarState == 0
            val isLandscape = rootView.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

            var headerView = cachedHeaderViewRef?.get()
            if (headerView == null) {
                if (cachedHeaderId == 0) {
                    cachedHeaderId = rootView.resources.getIdentifier("normal_notification_header_view", "id", "com.android.systemui")
                }
                if (cachedHeaderId != 0) {
                    headerView = rootView.findViewById(cachedHeaderId)
                    if (headerView != null) {
                        cachedHeaderViewRef = WeakReference(headerView)
                    }
                }
            }

            if (!isUnlocked) {
                // On lockscreen, normal_notification_header_view MUST ALWAYS be hidden!
                if (headerView != null && headerView.visibility != View.GONE) {
                    headerView.alpha = 0.0f
                    headerView.visibility = View.GONE
                }
                isHeaderSuppressedByUs = false
                return
            }

            if (!cfg.isLockScreenActive || isLandscape) {
                // When feature is disabled or in landscape, only restore if we previously suppressed it!
                if (isHeaderSuppressedByUs && headerView != null) {
                    headerView.visibility = View.VISIBLE
                    headerView.alpha = 1.0f
                    isHeaderSuppressedByUs = false
                }
                return
            }

            if (headerView != null) {
                if (isSuppress) {
                    if (!isHeaderSuppressedByUs) {
                        headerView.alpha = 0.0f
                        headerView.visibility = View.GONE
                        isHeaderSuppressedByUs = true
                    }
                } else if (isHeaderSuppressedByUs) {
                    headerView.visibility = View.VISIBLE
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
            var bottomArea = getBottomAreaView(container)
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
                cachedBottomAreaRef = WeakReference(bottomArea)
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

        val rawBottom = try {
            val clockContainer = getAuthenticClockContainer(container.rootView)
            var detectedH = 0
            if (clockContainer != null) {
                val h = clockContainer.height
                if (h > (120f * density).toInt()) {
                    detectedH = h
                } else {
                    for (i in 0 until clockContainer.childCount) {
                        val child = clockContainer.getChildAt(i)
                        if (child != null && child.height > (120f * density).toInt()) {
                            detectedH = child.height
                            break
                        }
                    }
                }
            }
            if (detectedH > 0) detectedH else {
                val templateId = try {
                    LockScreenInfoReader.getConfig(container.context).templateId.lowercase()
                } catch (_: Throwable) { "classic" }
                when {
                    templateId.startsWith("magazine") || templateId.startsWith("signature") -> (340f * density).toInt()
                    templateId.startsWith("oversize") -> (310f * density).toInt()
                    templateId.startsWith("rhombus") -> (285f * density).toInt()
                    else -> (220f * density).toInt()
                }
            }
        } catch (_: Throwable) {
            (220f * density).toInt()
        }

        // Match configured clock scale
        val clockScale = NotificationSheetConfigHolder.sheet.notifClockScale.coerceIn(0.40f, 1.20f)
        return (rawBottom * clockScale).toInt()
    }

    /**
     * Synchronizes the authentic SystemUI keyguard clock with live notifications:
     * 1. Inspects notification_stack_scroller visible count.
     * 2. Sets KeyguardClockContainer.mHasNotification = false and enables mClockCanScale.
     * 3. Calls AnimationHelper.doAnimationToNotification(false, true) to keep the prominent
     *    Big Lock Screen Clock style in Notification Center.
     * 4. Strictly executed ONLY during unlocked shade pulldown (never affects lockscreen).
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
     * 1. Sets KeyguardClockContainer.mHasNotification = false and enables mClockCanScale.
     * 2. Calls AnimationHelper.doAnimationToNotification(false, true) to keep the prominent
     *    Big Lock Screen Clock style in Notification Center.
     * 3. Uses cached reflection fields for zero-overhead performance during swipe pull-down.
     */
    fun syncClockWithNotifications(container: ViewGroup?) {
        if (container == null) return
        val isLandscape = container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        if (isLandscape) return

        val cfg = NotificationSheetConfigHolder.sheet
        // Strictly only execute when lockScreenAsNotifCenterEnabled is on AND shade is active while unlocked!
        if (!cfg.isLockScreenActive || !dev.hyperios.app.notification.NotificationSheetHook.isUnlockedShadeActive) return

        if (isClockSyncedBig) return

        try {
            val clockContainer = getAuthenticClockContainer(container.rootView)
            if (clockContainer != null) {
                try {
                    val clockClass = clockContainer.javaClass
                    getCanScaleField(clockClass)?.setBoolean(clockContainer, true)

                    // Always enforce Big Clock: mHasNotification = false
                    val hasNotifField = getHasNotifField(clockClass)
                    hasNotifField?.setBoolean(clockContainer, false)

                    val animHelper = getAnimHelperField(clockClass)?.get(clockContainer)
                    if (animHelper != null) {
                        getDoAnimMethod(animHelper.javaClass)?.invoke(animHelper, false, true)
                        Log.d(TAG, "Enforced authentic clock to big style via doAnimationToNotification(false, true)")
                    }

                    // Direct call to clock view: setShowFullTime(true) forces big / full mode!
                    try {
                        val controller = getControllerField(clockClass)?.get(clockContainer)
                        if (controller != null) {
                            val clockView = getClockViewField(controller.javaClass)?.get(controller)
                            if (clockView != null) {
                                getSetShowFullTimeMethod(clockView.javaClass)?.invoke(clockView, true)
                            }
                        }
                    } catch (_: Throwable) {}

                    isClockSyncedBig = true
                } catch (t: Throwable) {
                    Log.e(TAG, "Error configuring authentic clock to big state", t)
                }
            }

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
            val container = dev.hyperios.app.notification.NotificationSheetHook.getContainer() ?: return
            val bottomArea = getBottomAreaView(container)
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
                getBottomAreaView(container)?.visibility = View.GONE
                return
            }
            val cfg = NotificationSheetConfigHolder.sheet
            if (!cfg.isLockScreenActive) return

            // 1. Suppress stock shade header when pulling down
            suppressStockShadeHeader(container.rootView, fraction > 0.015f)

            // 2. Synchronize authentic lockscreen clock with notifications (once until shade resets)
            if (!isClockSyncedBig) {
                syncClockWithNotifications(container)
            }

            // 3. Ensure bottom affordances injected and tracked
            ensureBottomAffordancesInjected(container)
            val bottomArea = getBottomAreaView(container)
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
            val isLandscape = container.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                getBottomAreaView(container)?.visibility = View.GONE
                return
            }
            val cfg = NotificationSheetConfigHolder.sheet
            if (!cfg.isLockScreenActive) return

            suppressStockShadeHeader(container.rootView, true)
            syncClockWithNotifications(container)
            ensureBottomAffordancesInjected(container)
            val bottomArea = getBottomAreaView(container)
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
            isClockSyncedBig = false
            currentMorphFraction = 0f
            targetMorphFraction = 0f
            morphAnimator?.cancel()
            morphAnimator = null

            // Restore stock shade header
            suppressStockShadeHeader(container.rootView, false)

            // Hide bottom affordances
            getBottomAreaView(container)?.visibility = View.GONE

            if (cachedNsslId == 0) {
                cachedNsslId = container.resources.getIdentifier("notification_stack_scroller", "id", "com.android.systemui")
            }
            val nssl = if (cachedNsslId != 0) cachedNsslRef?.get() ?: container.findViewById<ViewGroup>(cachedNsslId)?.also { cachedNsslRef = WeakReference(it) } else null
            nssl?.clipBounds = null

            lastKnownHasNotification = null
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onShadeReset", t)
        }
    }
}
