package dev.joseph.hyperbounce.notification

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * Feature: iOS 26 Incoming Pop-Up Notification Animation Hook.
 *
 * Exclusively intercepts incoming Heads-Up (pop-up) notifications AFTER the screen is unlocked:
 * - Originates shrunken from the center top notch of the screen.
 * - Emerges smoothly into its resting position with an authentic iOS single spring overshoot and settle.
 * - Manages multiple simultaneous notifications with a layered cascading deck presentation.
 * - Does NOT affect lockscreen notifications, notification shade opening, or notifications in open shade.
 * - Fully crash-resilient and thread-safe.
 */
object IosHunAnimationHook {

    private const val TAG = "HyperBounce_IosHun"
    private const val TAG_IOS_HUN_ACTIVE = 0x7E030001
    private const val TAG_ACTIVE_ANIMATOR = 0x7E030002

    @Volatile
    private var isHooked = false

    private var currentFrontRow: WeakReference<View>? = null
    private var currentDeckRow: WeakReference<View>? = null

    /**
     * Authentically models the iOS 26 critically underdamped spring curve.
     * With dampingRatio ≈ 0.74 and response ≈ 0.58, it produces a single, elegant
     * overshoot of ~3.2% at 58% duration and settles smoothly to 1.000 by completion.
     */
    class Ios26SpringInterpolator(
        private val dampingRatio: Float = 0.74f,
        private val response: Float = 0.58f
    ) : Interpolator {
        override fun getInterpolation(input: Float): Float {
            if (input <= 0.0f) return 0.0f
            if (input >= 1.0f) return 1.0f

            val omega = (Math.PI / response).toFloat()
            val beta = omega * (dampingRatio / Math.sqrt(1.0 - dampingRatio * dampingRatio).toFloat())
            val decay = Math.exp((-beta * input).toDouble()).toFloat()
            val osc = Math.cos((omega * input).toDouble()).toFloat() +
                    (beta / omega) * Math.sin((omega * input).toDouble()).toFloat()

            return 1.0f - decay * osc
        }
    }

    fun init(module: XposedModule, classLoader: ClassLoader) {
        if (isHooked) return
        try {
            hookGenerateHeadsUpAnimation(module, classLoader)
            hookExpandableViewStateAnimateTo(module, classLoader)
            isHooked = true
            Log.d(TAG, "IosHunAnimationHook initialized successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize IosHunAnimationHook", t)
        }
    }

    /**
     * Intercepts heads-up appearance in NotificationStackScrollLayoutController.
     */
    private fun hookGenerateHeadsUpAnimation(module: XposedModule, classLoader: ClassLoader) {
        val controllerClasses = listOf(
            "com.android.systemui.statusbar.notification.stack.NotificationStackScrollLayoutController",
            "com.android.systemui.statusbar.notification.stack.MiuiNotificationStackScrollLayoutController"
        )

        for (clsName in controllerClasses) {
            try {
                val clazz = Class.forName(clsName, false, classLoader)
                for (m: Method in clazz.declaredMethods) {
                    if (m.name == "generateHeadsUpAnimation" && m.parameterTypes.size == 2 &&
                        m.parameterTypes[1] == Boolean::class.javaPrimitiveType
                    ) {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val entry = chain.args[0]
                                val isAppear = chain.args[1] as? Boolean ?: false
                                val row = getRowFromEntry(entry)

                                if (row != null) {
                                    val cfg = NotificationSheetConfigHolder.sheet
                                    val isKeyguard = NotificationSheetHook.currentStatusBarState == 1 || isKeyguardActive(chain.thisObject)
                                    val isShadeOpen = NotificationSheetHook.isShadeSwitching || isShadeExpanded(chain.thisObject)

                                    if (cfg.enabled && cfg.iosHunAnimationEnabled && !isKeyguard && !isShadeOpen) {
                                        if (isAppear) {
                                            handleHeadsUpAppear(row, chain.thisObject)
                                        } else {
                                            handleHeadsUpDisappear(row)
                                        }
                                    }
                                }

                                return chain.proceed()
                            }
                        })
                        Log.d(TAG, "Hooked $clsName.generateHeadsUpAnimation successfully")
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Notice: could not hook $clsName.generateHeadsUpAnimation: ${t.message}")
            }
        }
    }

    /**
     * Intercepts ExpandableViewState.animateTo to prevent stock linear animators
     * from overriding the active iOS spring animation while it is running.
     */
    private fun hookExpandableViewStateAnimateTo(module: XposedModule, classLoader: ClassLoader) {
        val stateClasses = listOf(
            "com.android.systemui.statusbar.notification.stack.ExpandableViewState",
            "com.android.systemui.statusbar.notification.stack.ViewState"
        )

        for (clsName in stateClasses) {
            try {
                val clazz = Class.forName(clsName, false, classLoader)
                for (m: Method in clazz.declaredMethods) {
                    if (m.name == "animateTo" && m.parameterTypes.size == 2 &&
                        View::class.java.isAssignableFrom(m.parameterTypes[0])
                    ) {
                        module.hook(m).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any? {
                                val view = chain.args[0] as? View
                                if (view != null && view.getTag(TAG_IOS_HUN_ACTIVE) == true) {
                                    // Bypass stock linear translation/scale animator while iOS spring runs
                                    return null
                                }
                                return chain.proceed()
                            }
                        })
                        Log.d(TAG, "Hooked $clsName.animateTo successfully")
                        break
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Notice: could not hook $clsName.animateTo: ${t.message}")
            }
        }
    }

    /**
     * Triggers the iOS 26 pop-up emergence animation from the top center notch.
     */
    private fun handleHeadsUpAppear(row: View, controller: Any) {
        row.post {
            try {
                if (!row.isAttachedToWindow) return@post

                // Cancel any previous animator on this row
                (row.getTag(TAG_ACTIVE_ANIMATOR) as? Animator)?.cancel()

                val cfg = NotificationSheetConfigHolder.sheet
                row.setTag(TAG_IOS_HUN_ACTIVE, true)

                // Layout measurement guarantee
                if (row.width == 0 || row.height == 0) {
                    row.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            row.viewTreeObserver.removeOnPreDrawListener(this)
                            executeSpringEmergence(row, controller, cfg)
                            return true
                        }
                    })
                } else {
                    executeSpringEmergence(row, controller, cfg)
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error handling heads-up appear", t)
                row.setTag(TAG_IOS_HUN_ACTIVE, null)
            }
        }
    }

    private fun executeSpringEmergence(row: View, controller: Any, cfg: NotificationSheetConfig) {
        val targetY = getTargetRestingY(row, controller)
        val notchY = 0.0f // Top notch / status bar top edge origin

        // Pivot firmly anchored to horizontal center of notch
        row.pivotX = row.width / 2.0f
        row.pivotY = 0.0f

        // Initial shrunken state at notch
        val startScale = cfg.iosHunStartScale.coerceIn(0.20f, 0.85f)
        row.scaleX = startScale
        row.scaleY = startScale
        row.translationY = notchY
        row.alpha = 0.0f

        // Handle multiple incoming notifications (cascading deck)
        val prevRow = currentFrontRow?.get()
        if (prevRow != null && prevRow != row && prevRow.isAttachedToWindow) {
            val deckOffset = 12f * row.resources.displayMetrics.density
            prevRow.animate()
                .scaleX(0.93f)
                .scaleY(0.93f)
                .translationY(prevRow.translationY + deckOffset)
                .alpha(0.82f)
                .setDuration(240L)
                .setInterpolator(DecelerateInterpolator())
                .start()
            currentDeckRow = WeakReference(prevRow)
        }
        currentFrontRow = WeakReference(row)

        val durationMs = cfg.iosHunDurationMs.toLong().coerceIn(200L, 800L)
        val spring = Ios26SpringInterpolator(dampingRatio = 0.74f, response = 0.58f)

        val animator = ValueAnimator.ofFloat(0.0f, 1.0f).apply {
            duration = durationMs
            interpolator = spring
            addUpdateListener { va ->
                val fraction = va.animatedFraction
                val springVal = va.animatedValue as Float

                // Pop outward with single overshoot and settling
                val curScale = startScale + (1.0f - startScale) * springVal
                row.scaleX = curScale
                row.scaleY = curScale

                // Translation emerges from notch to target resting Y
                val curY = notchY + (targetY - notchY) * springVal
                row.translationY = curY

                // Alpha swiftly reaches full opacity within first 30% of movement
                val alphaProg = (fraction / 0.30f).coerceIn(0.0f, 1.0f)
                row.alpha = alphaProg
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    finalizeRow(row, targetY)
                }

                override fun onAnimationCancel(animation: Animator) {
                    finalizeRow(row, targetY)
                }
            })
        }

        row.setTag(TAG_ACTIVE_ANIMATOR, animator)
        animator.start()
    }

    private fun finalizeRow(row: View, targetY: Float) {
        row.scaleX = 1.0f
        row.scaleY = 1.0f
        row.translationY = targetY
        row.alpha = 1.0f
        row.setTag(TAG_IOS_HUN_ACTIVE, null)
        row.setTag(TAG_ACTIVE_ANIMATOR, null)
    }

    /**
     * When a heads-up notification disappears, restore any deck notification to the foreground.
     */
    private fun handleHeadsUpDisappear(row: View) {
        (row.getTag(TAG_ACTIVE_ANIMATOR) as? Animator)?.cancel()
        row.setTag(TAG_IOS_HUN_ACTIVE, null)
        row.setTag(TAG_ACTIVE_ANIMATOR, null)

        if (currentFrontRow?.get() == row) {
            currentFrontRow = null
            val deck = currentDeckRow?.get()
            if (deck != null && deck.isAttachedToWindow) {
                val restY = getTargetRestingY(deck, null)
                val cfg = NotificationSheetConfigHolder.sheet
                val durationMs = cfg.iosHunDurationMs.toLong().coerceIn(200L, 800L)
                deck.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .translationY(restY)
                    .alpha(1.0f)
                    .setDuration(durationMs)
                    .setInterpolator(Ios26SpringInterpolator(0.74f, 0.58f))
                    .start()
                currentFrontRow = currentDeckRow
                currentDeckRow = null
            }
        } else if (currentDeckRow?.get() == row) {
            currentDeckRow = null
        }
    }

    private fun getTargetRestingY(row: View, controller: Any?): Float {
        try {
            // Attempt 1: Query row's ExpandableViewState mYTranslation
            val vsMethod = row.javaClass.getMethod("getViewState")
            val vs = vsMethod.invoke(row)
            if (vs != null) {
                val yField = vs.javaClass.getField("mYTranslation")
                val yVal = (yField.get(vs) as? Number)?.toFloat() ?: 0f
                if (yVal > 0f) return yVal
            }
        } catch (_: Throwable) {}

        try {
            // Attempt 2: Query NSSL getTopHeadsUpPinnedHeight or ambient top margin
            if (controller != null) {
                val viewField = controller.javaClass.getDeclaredField("mView")
                viewField.isAccessible = true
                val nssl = viewField.get(controller)
                if (nssl != null) {
                    val ambField = nssl.javaClass.getDeclaredField("mAmbientState")
                    ambField.isAccessible = true
                    val amb = ambField.get(nssl)
                    if (amb != null) {
                        val topMarginField = amb.javaClass.getDeclaredField("mStackTopMargin")
                        topMarginField.isAccessible = true
                        val topMargin = (topMarginField.get(amb) as? Number)?.toFloat() ?: 0f
                        if (topMargin > 0f) return topMargin
                    }
                }
            }
        } catch (_: Throwable) {}

        // Fallback default: standard status bar padding (~48dp)
        val density = row.resources.displayMetrics.density
        return 48f * density
    }

    private fun getRowFromEntry(entry: Any?): View? {
        if (entry == null) return null
        return try {
            val rowField = entry.javaClass.getDeclaredField("row")
            rowField.isAccessible = true
            rowField.get(entry) as? View
        } catch (_: Throwable) {
            null
        }
    }

    private fun isKeyguardActive(controller: Any?): Boolean {
        if (controller == null) return false
        return try {
            val m = controller.javaClass.getMethod("isKeyguardShowing")
            m.invoke(controller) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }

    private fun isShadeExpanded(controller: Any?): Boolean {
        if (controller == null) return false
        return try {
            val viewField = controller.javaClass.getDeclaredField("mView")
            viewField.isAccessible = true
            val nssl = viewField.get(controller) ?: return false
            val expField = nssl.javaClass.getDeclaredField("mIsExpanded")
            expField.isAccessible = true
            expField.get(nssl) as? Boolean ?: false
        } catch (_: Throwable) {
            false
        }
    }
}
