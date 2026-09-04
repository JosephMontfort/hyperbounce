package dev.joseph.hyperbounce.launcher

import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.View
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.math.abs

/**
 * iOS-Style App Open & Close Animation Engine for HyperOS Launcher (com.miui.home)
 *
 * Implements pure iOS physical dynamics and weightless trajectory equations:
 *
 * 1. Zero Drag Resistance & Continuous Elevation (Zero Downward Dip):
 *    - Replaces HyperOS's heavy friction curves with fluid, direct-tracking physics.
 *    - Upward elevation from frame 0: strictly moves UPWARDS as soon as touched,
 *      eliminating any downward shift when swiping up for Recents.
 *
 * 2. Rapid Scaling & Complete On-Screen Floating Window Engulfing:
 *    - Quickened non-linear scaling curve: the card begins scaling down immediately,
 *      reaching floating window dimensions (0.24x) by p = 0.45 - 0.50.
 *    - Synchronized docking: by the time the floating window engulfer appears and wraps
 *      around the card, the app is already scaled down and neatly docked inside the
 *      upper viewport (below status bar), completely visible on-screen without spilling over.
 *
 * 3. Horizontal Card-Crossing Haptics for Recents (Stack Style):
 *    - Hooks TaskStackView.onStackScrollChanged.
 *    - As each app card crosses the center reference line during horizontal swipes,
 *      triggers a crisp, light tactile clock tick (MIUI_GEAR_LIGHT / EFFECT_TICK).
 *
 * 4. Recents Current Task Centering:
 *    - Overrides TaskStackViewsAlgorithmHorizontal, TaskStackViewsAlgorithmStack, and TaskViewsElement.
 *    - Eliminates the intentional HyperOS offset that shifted the current app to the right.
 *    - Keeps the active running app perfectly centered on screen when opening Recents.
 *
 * 5. Velocity-Dependent Flight & Thumb-Coupled Minimizing:
 *    - Captures live gesture flick velocity and thumb release position.
 *    - Boosted launch impulses: app launches effortlessly into a soaring parabolic arc before
 *      landing smoothly into its icon squircle.
 *
 * 6. Refined Gesture Haptics:
 *    - Back Gesture: Distinct, crisp, modern elastic pop (MIUI_BUTTON_SMALL / MIUI_MESH_LIGHT).
 *    - App-to-Recents Gesture: Clean mechanical latch tick (MIUI_SWITCH / MIUI_KEYBOARD).
 *
 * 7. Feather-Light Expansive App Opening:
 *    - Organic, critically damped (zeta=1.00, T0=0.410s) expansive flight from squircle to full screen.
 */
object AppTransitionHook {
    private const val TAG = "HyperBounce_iOS"

    // Live Gesture Velocity & Thumb Position Tracking
    @Volatile
    private var sLastVelocityX: Float = 0f
    @Volatile
    private var sLastVelocityY: Float = 0f
    @Volatile
    private var sLastThumbY: Float = 0f
    @Volatile
    private var sLastVelocityTime: Long = 0L

    // Recents Stack Scroll Tracking
    @Volatile
    private var sLastScrolledTaskIndex: Int = -999

    private var sKeyField: Field? = null
    private var sImpField: Field? = null
    private var sDField: Field? = null
    private var sRField: Field? = null

    fun init(module: XposedInterface, classLoader: ClassLoader?) {
        if (classLoader == null) return

        hookGestureVelocityTracking(module, classLoader)
        hookDragResistanceAndScaling(module, classLoader)
        hookLandscapeRecentsOptimization(module, classLoader)
        hookParallelAppTransitions(module, classLoader)
        hookSmallWindowThreshold(module, classLoader)
        hookRecentsCurrentTaskCentering(module, classLoader)
        hookRecentsStackScrollHaptics(module, classLoader)
        hookRefinedHaptics(module, classLoader)
        hookExtraInteractionsFriction(module, classLoader)
        hookSpringParamsProvider(module, classLoader)
        hookSfAnimImplementor(module, classLoader)
        hookHomeLayerScaling(module, classLoader)
        hookWallpaperDynamics(module, classLoader)
        hookDeviceLevelUtils(module, classLoader)
    }

    /**
     * Hook 1: Tracks live gesture flick velocity and thumb position from GestureHomeCalculator.
     * updateVelocity(float f, float f2) receives vx and vy in px/ms.
     */
    private fun hookGestureVelocityTracking(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val calcClass = Class.forName("com.miui.home.recents.GestureHomeCalculator", false, classLoader)
            val updateVelocityMethod = calcClass.getDeclaredMethod(
                "updateVelocity",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )

            val currYField = runCatching {
                calcClass.getDeclaredField("mCurrY").apply { isAccessible = true }
            }.getOrNull()

            module.hook(updateVelocityMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    try {
                        val vx = (chain.args[0] as? Number)?.toFloat() ?: 0f
                        val vy = (chain.args[1] as? Number)?.toFloat() ?: 0f
                        sLastVelocityX = vx
                        sLastVelocityY = vy
                        sLastVelocityTime = SystemClock.uptimeMillis()

                        if (currYField != null) {
                            val currY = (currYField.get(chain.thisObject) as? Number)?.toFloat() ?: 0f
                            if (currY > 0f) {
                                sLastThumbY = currY
                            }
                        }
                    } catch (_: Throwable) {}
                    return result
                }
            })
            Log.i(TAG, "Hooked GestureHomeCalculator.updateVelocity for live flick & thumb tracking")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook GestureHomeCalculator.updateVelocity", t)
        }
    }

    /**
     * Hook 2: Eliminates drag resistance, ensures zero downward dip, and accelerates the scaling-down
     * curve so the app is fully compact and docked inside the viewport when the floating window engulfer appears.
     */
    private fun hookDragResistanceAndScaling(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val algoClass = Class.forName("com.miui.home.recents.views.TaskStackLayoutAlgorithm", false, classLoader)

            // 1. Hook calculateTaskWidth(float f, float f2)
            // Accelerates the scaling curve so the card reaches compact floating window scale (0.24x) by p = 0.45 - 0.50
            val calcWidthMethod = algoClass.getDeclaredMethod(
                "calculateTaskWidth",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )

            module.hook(calcWidthMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled) {
                        return chain.proceed()
                    }

                    val baseWidth = (chain.args[0] as? Number)?.toFloat() ?: return chain.proceed()
                    val p = (chain.args[1] as? Number)?.toFloat()?.coerceAtLeast(0f) ?: 0f

                    // Fast, natural responsive scaling curve:
                    // p = 0.00 -> 1.00x
                    // p = 0.18 -> 0.76x
                    // p = 0.35 -> 0.48x
                    // p = 0.48 -> 0.26x (ready before floating window engulfs!)
                    // p >= 0.60 -> 0.22x - 0.24x
                    val scale = when {
                        p <= 0.02f -> 1.0f
                        p <= 0.25f -> 1.0f - (p - 0.02f) * (0.22f / 0.23f) // 1.00 -> 0.78
                        p <= 0.50f -> 0.78f - (p - 0.25f) * (0.30f / 0.25f) // 0.78 -> 0.48
                        p <= 0.75f -> 0.48f - (p - 0.50f) * (0.24f / 0.25f) // 0.48 -> 0.24
                        else -> (0.24f / (1.0f + (p - 0.75f) * 1.20f)).coerceAtLeast(0.18f)
                    }
                    return baseWidth * scale
                }
            })

            // 2. Hook calculateTaskY(int i, float f, float f2, float f3)
            // 1:1 finger tracking: bottom edge follows finger directly (no 2x overshoot bug!)
            val calcYMethod = algoClass.getDeclaredMethod(
                "calculateTaskY",
                Int::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )

            module.hook(calcYMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled) {
                        return chain.proceed()
                    }

                    val i = (chain.args[0] as? Number)?.toInt() ?: 1
                    val screenH = (chain.args[1] as? Number)?.toFloat() ?: 0f
                    val taskH = (chain.args[2] as? Number)?.toFloat() ?: 0f
                    val progress = (chain.args[3] as? Number)?.toFloat()?.coerceAtLeast(0f) ?: 0f

                    if (screenH > 0f) {
                        val p = progress

                        if (i == 1 || i == 3) {
                            // 1:1 Finger Tracking: bottom edge is directly positioned at finger height
                            // i == 1: Portrait gesture swipe up
                            // i == 3: Landscape gesture swipe up (right-side nav bar)
                            val bottomY = screenH * (1.0f - p)
                            val desiredTop = bottomY - taskH

                            // Never shift down at start (0.0f at p=0), and stay cleanly inside screen
                            val finalY = when {
                                p <= 0.02f -> 0.0f
                                desiredTop < -0.040f * screenH -> -0.040f * screenH
                                else -> desiredTop
                            }
                            return finalY
                        } else if (i == 2) {
                            // Landscape gesture swipe up (left-side nav bar)
                            val desiredTop = (screenH * p).coerceAtMost(screenH * 1.040f)
                            return desiredTop
                        }
                    }

                    return chain.proceed()
                }
            })

            // 3. Hook calculateTaskX(float f, float f2, float f3, float f4)
            // Ensures horizontal bounds stay completely inside the screen when pulled to corners
            val calcXMethod = algoClass.getDeclaredMethod(
                "calculateTaskX",
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            )

            module.hook(calcXMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = (chain.proceed() as? Number)?.toFloat() ?: return chain.proceed()
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled) {
                        return result
                    }
                    val screenW = (chain.args[2] as? Number)?.toFloat() ?: return result
                    val taskW = (chain.args[3] as? Number)?.toFloat() ?: return result

                    // Keep the card cleanly inside display boundaries [12dp, screenW - taskW - 12dp]
                    val margin = screenW * 0.025f
                    val maxX = (screenW - taskW - margin).coerceAtLeast(margin)
                    return result.coerceIn(margin, maxX)
                }
            })

            Log.i(TAG, "Hooked TaskStackLayoutAlgorithm for quick scaling and 100% on-screen engulfing")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook TaskStackLayoutAlgorithm drag calculations", t)
        }

        // 4. Hook afterFrictionValue on algorithm subclasses to eliminate artificial rubber-band wall
        val algoSubclasses = listOf(
            "com.miui.home.recents.views.TaskStackViewsAlgorithmVertical",
            "com.miui.home.recents.views.TaskStackViewsAlgorithmHorizontal",
            "com.miui.home.recents.views.TaskStackViewsAlgorithmStack"
        )
        for (subclass in algoSubclasses) {
            try {
                val clazz = Class.forName(subclass, false, classLoader)
                val frictionMethod = clazz.getDeclaredMethod(
                    "afterFrictionValue",
                    Float::class.javaPrimitiveType,
                    Float::class.javaPrimitiveType
                )
                module.hook(frictionMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (!cfg.enabled) {
                            return chain.proceed()
                        }
                        val f = (chain.args[0] as? Number)?.toFloat() ?: 0f
                        return f * 1.0f // 100% linear, zero friction resistance
                    }
                })
            } catch (_: Throwable) {}
        }
    }

    /**
     * Hook 3: Reduces the vertical swipe distance required to activate the Floating Window Engulfer.
     * Hooks SmallWindowCrop.getActiveCoef and getHoldCoef so the engulfer activates ~250px earlier.
     */
    private fun hookSmallWindowThreshold(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val swcClass = Class.forName("com.miui.home.recents.views.SmallWindowCrop", false, classLoader)

            val getHoldMethod = swcClass.getDeclaredMethod("getHoldCoef").apply { isAccessible = true }
            module.hook(getHoldMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (!LauncherConfigHolder.iosAnimConfig.enabled) {
                        return chain.proceed()
                    }
                    return 0.92f // Appears earlier when moving towards top-right
                }
            })

            val getActiveMethod = swcClass.getDeclaredMethod("getActiveCoef").apply { isAccessible = true }
            module.hook(getActiveMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (!LauncherConfigHolder.iosAnimConfig.enabled) {
                        return chain.proceed()
                    }
                    // Engulfs earlier (reduced vertical distance threshold from stock 0.363 to 0.48)
                    return 0.48f
                }
            })

            Log.i(TAG, "Hooked SmallWindowCrop thresholds to reduce vertical swipe activation distance")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook SmallWindowCrop thresholds", t)
        }
    }

    /**
     * Hook 4: Centers the current running task in Recents Overview instead of pushing it to the right.
     * Overrides getDefaultCenterTaskViewIndex, getTargetTaskViewIndex, and getCenterTaskViewIndex.
     */
    private fun hookRecentsCurrentTaskCentering(module: XposedInterface, classLoader: ClassLoader) {
        val targetAlgorithms = listOf(
            "com.miui.home.recents.views.TaskStackViewsAlgorithmHorizontal",
            "com.miui.home.recents.views.TaskStackViewsAlgorithmStack"
        )

        for (algoName in targetAlgorithms) {
            try {
                val clazz = Class.forName(algoName, false, classLoader)

                // 1. getDefaultCenterTaskViewIndex() -> return 0 (instead of stock 1)
                val defaultCenterMethod = clazz.getDeclaredMethod("getDefaultCenterTaskViewIndex")
                module.hook(defaultCenterMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (!cfg.enabled) {
                            return chain.proceed()
                        }
                        return 0 // Current task stays in the center!
                    }
                })

                // 2. getTargetTaskViewIndex(boolean z, int i, int i2)
                val targetIndexMethod = clazz.getDeclaredMethod(
                    "getTargetTaskViewIndex",
                    Boolean::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                module.hook(targetIndexMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (!cfg.enabled) {
                            return chain.proceed()
                        }

                        val isQuickSwitch = chain.args[0] as? Boolean ?: false
                        if (isQuickSwitch) {
                            return chain.proceed()
                        }

                        return 0
                    }
                })
                Log.i(TAG, "Hooked $algoName for Recents current task centering")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to hook Recents task index on $algoName", t)
            }
        }

        // 3. Hook TaskViewsElement.getCenterTaskViewIndex()
        try {
            val elemClass = Class.forName("com.miui.home.recents.anim.TaskViewsElement", false, classLoader)
            val centerMethod = elemClass.getDeclaredMethod("getCenterTaskViewIndex").apply { isAccessible = true }

            module.hook(centerMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled) {
                        return chain.proceed()
                    }

                    try {
                        val currentParams = chain.thisObject.javaClass.getDeclaredField("currentTaskViewParams").apply { isAccessible = true }.get(chain.thisObject)
                        val isQuickSwitch = (currentParams?.javaClass?.getMethod("isQuickSwitchMode")?.invoke(currentParams) as? Boolean) ?: false
                        if (!isQuickSwitch) {
                            return 0
                        }
                    } catch (_: Throwable) {}

                    return chain.proceed()
                }
            })
            Log.i(TAG, "Hooked TaskViewsElement.getCenterTaskViewIndex for direct center leash binding")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook TaskViewsElement.getCenterTaskViewIndex", t)
        }
    }

    /**
     * Hook 4: Horizontal card-crossing clock tick haptics for Stack-style Recents.
     * As each app crosses the middle reference line during horizontal swipes, triggers a crisp light clock tick.
     */
    private fun hookRecentsStackScrollHaptics(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val tsvClass = Class.forName("com.miui.home.recents.views.TaskStackView", false, classLoader)

            // Reset tracking index on onScrollStart
            val onScrollStartMethod = tsvClass.getDeclaredMethod("onScrollStart")
            module.hook(onScrollStartMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    try {
                        val scrollerField = chain.thisObject.javaClass.getDeclaredField("mStackScroller").apply { isAccessible = true }
                        val scroller = scrollerField.get(chain.thisObject)
                        val stackScroll = (scroller?.javaClass?.getMethod("getStackScroll")?.invoke(scroller) as? Number)?.toFloat() ?: 0f
                        sLastScrolledTaskIndex = kotlin.math.round(stackScroll).toInt()
                    } catch (_: Throwable) {
                        sLastScrolledTaskIndex = -999
                    }
                    return result
                }
            })

            // Hook onStackScrollChanged(float f, AnimationProps animationProps)
            val animPropsClass = Class.forName("com.miui.home.recents.views.AnimationProps", false, classLoader)
            val onScrollChangedMethod = tsvClass.getDeclaredMethod(
                "onStackScrollChanged",
                Float::class.javaPrimitiveType,
                animPropsClass
            )

            module.hook(onScrollChangedMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled) {
                        return result
                    }

                    try {
                        val scrollP = (chain.args[0] as? Number)?.toFloat() ?: return result
                        val nearestIndex = kotlin.math.round(scrollP).toInt()

                        // Trigger light clock tick when crossing each app's center threshold
                        if (nearestIndex != sLastScrolledTaskIndex && kotlin.math.abs(scrollP - nearestIndex) < 0.18f) {
                            sLastScrolledTaskIndex = nearestIndex
                            val isScrollingField = chain.thisObject.javaClass.getDeclaredField("mIsScrolling").apply { isAccessible = true }
                            val isScrolling = isScrollingField.get(chain.thisObject) as? Boolean ?: true
                            if (isScrolling) {
                                triggerLightClockTickHaptic(chain.thisObject as? View)
                            }
                        }
                    } catch (_: Throwable) {}

                    return result
                }
            })

            Log.i(TAG, "Hooked TaskStackView.onStackScrollChanged for horizontal card crossing clock tick haptics")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook TaskStackView for scroll haptics", t)
        }
    }

    /**
     * Triggers a crisp, light clock tick (MIUI_GEAR_LIGHT / EFFECT_TICK) as an app crosses the center.
     */
    private fun triggerLightClockTickHaptic(view: View?) {
        try {
            if (view != null) {
                // miuix.view.HapticFeedbackConstants.MIUI_GEAR_LIGHT = 268435477 (Crisp clock/gear tick)
                val hapticCompatClass = Class.forName("miuix.view.HapticCompat")
                val performMethod = hapticCompatClass.getMethod(
                    "performHapticFeedbackAsync",
                    View::class.java,
                    Int::class.javaPrimitiveType
                )
                performMethod.invoke(null, view, 268435477)
                return
            }
        } catch (_: Throwable) {}

        // Fallback: Android system linear motor light tick
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApp = activityThreadClass.getMethod("currentApplication").invoke(null) as? android.content.Context
            val vibrator = currentApp?.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(8L)
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Hook 5: Refined, satisfying linear motor haptics:
     * - Back Gesture: Crisp elastic tactile pop (MIUI_BUTTON_SMALL / MIUI_MESH_LIGHT)
     * - App-to-Recents Gesture: Clean mechanical latch tick (MIUI_SWITCH / MIUI_KEYBOARD) instead of heavy thud
     */
    private fun hookRefinedHaptics(module: XposedInterface, classLoader: ClassLoader) {
        val hapticClasses = listOf(
            "com.miui.home.common.hapticfeedback.HapticFeedbackCompatLinear",
            "com.miui.home.common.hapticfeedback.HapticFeedbackCompatV2",
            "com.miui.home.common.hapticfeedback.HapticFeedbackCompatNormal"
        )

        for (className in hapticClasses) {
            try {
                val clazz = Class.forName(className, false, classLoader)

                // 1. Hook performGestureReadyBack() -> Crisp elastic pop
                val readyBackMethod = clazz.getDeclaredMethod("performGestureReadyBack")
                module.hook(readyBackMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (!cfg.enabled) {
                            return chain.proceed()
                        }
                        triggerBackPopHaptic(chain.thisObject)
                        return null // Suppress heavy thud
                    }
                })

                // 2. Hook performGestureBackHandUp() -> Crisp elastic pop on release
                val handUpMethod = clazz.getDeclaredMethod("performGestureBackHandUp")
                module.hook(handUpMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (!cfg.enabled) {
                            return chain.proceed()
                        }
                        triggerBackPopHaptic(chain.thisObject)
                        return null // Suppress heavy thud
                    }
                })

                // 3. Hook performEnterRecent(View view) -> Satisfying latch tick instead of heavy thud
                val enterRecentMethod = clazz.getDeclaredMethod("performEnterRecent", View::class.java)
                module.hook(enterRecentMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (!cfg.enabled) {
                            return chain.proceed()
                        }
                        val view = chain.args[0] as? View
                        triggerRecentsTickHaptic(chain.thisObject, view)
                        return null // Suppress stock heavy MIUI_HOLD thud!
                    }
                })

                Log.i(TAG, "Hooked $className for refined gesture haptics")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to hook haptics on $className", t)
            }
        }
    }

    /**
     * Triggers a crisp standard linear motor keyboard tick for Back Gesture (MIUI_KEYBOARD = 268435479).
     */
    private fun triggerBackPopHaptic(instance: Any) {
        try {
            val helperField = instance.javaClass.getDeclaredField("mHapticHelper").apply { isAccessible = true }
            val helper = helperField.get(instance)
            if (helper != null) {
                // miuix.view.HapticFeedbackConstants.MIUI_KEYBOARD = 268435479 (Standard keyboard tick)
                // miuix.view.HapticFeedbackConstants.MIUI_GEAR_LIGHT = 268435477
                val performMethod = helper.javaClass.getMethod(
                    "performHapticFeedback",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val success = performMethod.invoke(helper, 268435479, 268435477) as? Boolean ?: false
                if (success) return
            }
        } catch (_: Throwable) {}

        // Fallback: Android system linear motor tick
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApp = activityThreadClass.getMethod("currentApplication").invoke(null) as? android.content.Context
            val vibrator = currentApp?.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(10L)
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Triggers a distinct, satisfying mechanical latch tick for Recents (MIUI_SWITCH = 268435460 / MIUI_KEYBOARD = 268435479).
     */
    private fun triggerRecentsTickHaptic(instance: Any, view: View?) {
        try {
            val helperField = instance.javaClass.getDeclaredField("mHapticHelper").apply { isAccessible = true }
            val helper = helperField.get(instance)
            if (helper != null) {
                // miuix.view.HapticFeedbackConstants.MIUI_SWITCH = 268435460
                // miuix.view.HapticFeedbackConstants.MIUI_KEYBOARD = 268435479
                val performMethod = helper.javaClass.getMethod(
                    "performHapticFeedback",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val success = performMethod.invoke(helper, 268435460, 268435479) as? Boolean ?: false
                if (success) return
            }
        } catch (_: Throwable) {}

        // Fallback on view or system vibrator
        try {
            view?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        } catch (_: Throwable) {}
    }

    /**
     * Hook 6: Eliminates artificial resistance in other core navigation interactions:
     * - Fluid back gesture indicator tracking in BackGestureUtils
     * - Lightweight card dismiss gesture in VerticalSwipe
     */
    private fun hookExtraInteractionsFriction(module: XposedInterface, classLoader: ClassLoader) {
        // Back gesture indicator friction
        try {
            val backClass = Class.forName("com.android.systemui.fsgesture.BackGestureUtils", false, classLoader)
            val convertOffsetMethod = backClass.getDeclaredMethod("convertOffset", Float::class.javaPrimitiveType)

            module.hook(convertOffsetMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled) {
                        return chain.proceed()
                    }
                    val f = (chain.args[0] as? Number)?.toFloat() ?: 0f
                    if (f <= 0f) return 0f
                    // Responsive, fluid back indicator motion:
                    return (f / 12.0f).coerceAtMost(36.0f)
                }
            })
            Log.i(TAG, "Hooked BackGestureUtils.convertOffset for feather-light edge back gestures")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook BackGestureUtils", t)
        }

        // Recents card dismiss swipe friction
        try {
            val swipeClass = Class.forName("com.miui.home.recents.views.VerticalSwipe", false, classLoader)
            val frictionMethod = swipeClass.getDeclaredMethod("afterFrictionValue", Float::class.javaPrimitiveType, Float::class.javaPrimitiveType).apply {
                isAccessible = true
            }

            module.hook(frictionMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled) {
                        return chain.proceed()
                    }
                    val f = (chain.args[0] as? Number)?.toFloat() ?: 0f
                    return f * 1.0f // 100% responsive card dismiss in recents
                }
            })
            Log.i(TAG, "Hooked VerticalSwipe.afterFrictionValue for feather-light recents dismissal")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook VerticalSwipe", t)
        }
    }

    // Reflection & Device Metric Caching for Zero Frame Jitter
    private var sCachedMagicSpeedMethod: Method? = null
    private var sMagicSpeedMethodChecked = false
    private var sCachedSysPropGetMethod: Method? = null
    private var sSysPropGetMethodChecked = false
    private var sCachedSpeedRatio = 1.0f
    private var sLastSpeedRatioCheck = 0L

    private var sCachedDeviceWidth = 0f
    private var sCachedDeviceHeight = 0f
    private var sLastDeviceConfigCheck = 0L

    /**
     * Obtains the cached device dimensions (width, height) without per-frame reflection overhead.
     */
    private fun getCachedDeviceDimensions(classLoader: ClassLoader): Pair<Float, Float> {
        val now = SystemClock.uptimeMillis()
        if (sCachedDeviceWidth > 0f && sCachedDeviceHeight > 0f && (now - sLastDeviceConfigCheck) < 10000L) {
            return Pair(sCachedDeviceWidth, sCachedDeviceHeight)
        }
        return try {
            val dcClass = Class.forName("com.miui.home.common.device.DeviceConfigs", false, classLoader)
            val h = (dcClass.getMethod("getDeviceHeight").invoke(null) as? Number)?.toFloat() ?: 2400f
            val w = (dcClass.getMethod("getDeviceWidth").invoke(null) as? Number)?.toFloat() ?: 1080f
            sCachedDeviceWidth = w.coerceAtLeast(720f)
            sCachedDeviceHeight = h.coerceAtLeast(1000f)
            sLastDeviceConfigCheck = now
            Pair(sCachedDeviceWidth, sCachedDeviceHeight)
        } catch (_: Throwable) {
            Pair(1080f, 2400f)
        }
    }

    /**
     * Obtains the dynamic animation speed scale factor from HyperOS system settings with reflection caching.
     * Uses Utilities.getGestureAnimMagicSpeed() and persist.miui.home_ms.
     */
    private fun getAnimationSpeedRatio(classLoader: ClassLoader): Float {
        val now = SystemClock.uptimeMillis()
        if (sLastSpeedRatioCheck > 0L && (now - sLastSpeedRatioCheck) < 2000L) {
            return sCachedSpeedRatio
        }
        return try {
            if (!sMagicSpeedMethodChecked) {
                sMagicSpeedMethodChecked = true
                val utilClass = Class.forName("com.miui.home.recents.util.Utilities", false, classLoader)
                sCachedMagicSpeedMethod = utilClass.getDeclaredMethod("getGestureAnimMagicSpeed").apply { isAccessible = true }
            }
            val magicSpeed = (sCachedMagicSpeedMethod?.invoke(null) as? Number)?.toFloat() ?: 1.0f

            if (!sSysPropGetMethodChecked) {
                sSysPropGetMethodChecked = true
                val sysPropClass = Class.forName("android.os.SystemProperties")
                sCachedSysPropGetMethod = sysPropClass.getDeclaredMethod("get", String::class.java, String::class.java).apply { isAccessible = true }
            }
            val persistMsStr = sCachedSysPropGetMethod?.invoke(null, "persist.miui.home_ms", "1") as? String ?: "1"
            val persistMs = persistMsStr.toFloatOrNull() ?: 1.0f

            val ratio = (magicSpeed * persistMs).coerceIn(0.1f, 5.0f)
            sCachedSpeedRatio = ratio
            sLastSpeedRatioCheck = now
            ratio
        } catch (_: Throwable) {
            1.0f
        }
    }

    /**
     * Hook 7: iOS 26 Asymmetric Edge Compression, Z-Axis Depth Trajectory & Zero-Jitter Geometry.
     * - Width, Height, and Ratio share the EXACT SAME response and damping: eliminates the
     *   mathematical aspect-ratio solver conflict in SurfaceFlinger native C++, locking 120 FPS.
     * - Prominent Accordion Squeeze: CenterY response leads Height/Width response by over 2x.
     *   On open from dock/lower half, the window center shoots up before the height expands,
     *   pinning the bottom edge to the icon and rocketing the top edge skyward with immense 3D depth.
     * - Zero-Resistance Minimizing: Critically tuned damping (zeta = 0.84 - 0.88) and natural
     *   flick momentum allow minimizing windows to glide effortlessly into their icon targets.
     */
    private fun hookSpringParamsProvider(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val companionClass = Class.forName("com.miui.home.recents.util.SpringParamsProvider\$Companion", false, classLoader)
            val animTypeEnum = Class.forName("com.miui.home.recents.util.RectFSpringAnim\$AnimType", false, classLoader)
            val springParamClass = Class.forName("com.miui.home.recents.util.SpringParamsProvider\$SpringParam", false, classLoader)

            val springParamConst: Constructor<*> = springParamClass.getDeclaredConstructor(
                String::class.java,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            ).apply { isAccessible = true }

            try {
                sKeyField = springParamClass.getDeclaredField("key").apply { isAccessible = true }
                sImpField = springParamClass.getDeclaredField("impulse").apply { isAccessible = true }
                sDField = springParamClass.getDeclaredField("damping").apply { isAccessible = true }
                sRField = springParamClass.getDeclaredField("response").apply { isAccessible = true }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to reflect SpringParam fields", t)
            }

            val generateMethod: Method = companionClass.getDeclaredMethod(
                "generateSpringBundlesByAnimType",
                animTypeEnum,
                RectF::class.java,
                RectF::class.java
            )

            module.hook(generateMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed() as? Array<Any?> ?: return chain.proceed()
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled) {
                        return result
                    }

                    val animType = (chain.args[0] as? Enum<*>)?.name ?: ""

                    try {
                        val speedRatio = getAnimationSpeedRatio(classLoader)

                        // Start/Target rects for screen coordinate normalization
                        val iconRect = if (animType.startsWith("OPEN")) {
                            (chain.args[1] as? RectF) ?: (chain.args[2] as? RectF)
                        } else {
                            (chain.args[2] as? RectF) ?: (chain.args[1] as? RectF)
                        }

                        val (deviceWidth, deviceHeight) = getCachedDeviceDimensions(classLoader)

                        val iconCenterY = iconRect?.centerY() ?: (deviceHeight * 0.75f)
                        val iconCenterX = iconRect?.centerX() ?: (deviceWidth * 0.50f)
                        val normY = (iconCenterY / deviceHeight).coerceIn(0f, 1f)
                        val normX = (iconCenterX / deviceWidth).coerceIn(0f, 1f)

                        val vertOffset = (abs(normY - 0.5f) * 2.0f).coerceIn(0f, 1f)
                        val horizOffset = (abs(normX - 0.5f) * 2.0f).coerceIn(0f, 1f)

                        // Check live flick velocity
                        val now = SystemClock.uptimeMillis()
                        val isRecentFlick = (now - sLastVelocityTime) < 800L
                        val vy = sLastVelocityY // px/ms (< 0 on upward flick)
                        val isFastFlick = isRecentFlick && vy < -0.8f
                        val flickBoost = if (isFastFlick) -0.03f * speedRatio else 0.0f

                        // 1. OPEN DYNAMICS (iOS 26 Accordion Squeeze & Z-Axis Depth Trajectory):
                        // CenterY response leads Size response by > 2x. Bottom edge anchors while top edge launches!
                        val openResponseCenterY = ((0.20f - (vertOffset * 0.04f)) * speedRatio).coerceAtLeast(0.14f)
                        val openResponseCenterX = ((0.24f - (horizOffset * 0.04f)) * speedRatio).coerceAtLeast(0.15f)
                        // Synchronized geometry: Width, Height, and Ratio MUST share openResponseSize for 120fps zero jitter
                        val openResponseSize = ((0.40f + (vertOffset * 0.04f)) * speedRatio).coerceAtLeast(0.22f)
                        val openDamping = 1.00f // Critically damped: locked 120fps fluidity, zero jitter
                        val openCornerDamping = 1.05f
                        val openCornerResponse = (0.28f * speedRatio).coerceAtLeast(0.16f)
                        val openAlphaDamping = 0.98f
                        val openAlphaResponse = (0.22f * speedRatio).coerceAtLeast(0.14f)

                        // 2. CLOSE DYNAMICS (Weightless, Zero Resistance Minimizing):
                        val closeDampingY = if (normY >= 0.5f) {
                            if (isRecentFlick) 0.84f else 0.88f
                        } else {
                            if (isRecentFlick) 0.92f else 0.95f
                        }
                        val closeDampingX = if (isRecentFlick) 0.94f else 0.95f
                        val closeDampingSize = if (isRecentFlick) 0.94f else 0.95f

                        val closeResponseCenterY = (((0.33f - (vertOffset * 0.04f)) * speedRatio) + flickBoost).coerceAtLeast(0.18f)
                        val closeResponseCenterX = (((0.36f - (horizOffset * 0.03f)) * speedRatio) + flickBoost).coerceAtLeast(0.18f)
                        // Synchronized geometry: Width, Height, and Ratio MUST share closeResponseSize
                        val closeResponseSize = (((0.43f + (vertOffset * 0.04f)) * speedRatio) + flickBoost).coerceAtLeast(0.20f)
                        val closeCornerDamping = 1.05f
                        val closeCornerResponse = (0.28f * speedRatio).coerceAtLeast(0.16f)
                        val closeAlphaDamping = 0.96f
                        val closeAlphaResponse = (0.28f * speedRatio).coerceAtLeast(0.16f)

                        // 3. RECENTS & APP-TO-APP DYNAMICS:
                        val recentsDamping = 0.98f
                        val recentsResponse = (0.36f * speedRatio).coerceAtLeast(0.18f)
                        val recentsCornerDamping = 1.05f
                        val recentsCornerResponse = (0.28f * speedRatio).coerceAtLeast(0.16f)
                        val recentsAlphaDamping = 0.95f
                        val recentsAlphaResponse = (0.30f * speedRatio).coerceAtLeast(0.16f)

                        val recentsOpenDamping = 1.00f
                        val recentsOpenResponse = (0.36f * speedRatio).coerceAtLeast(0.18f)

                        val appToAppDamping = 0.98f
                        val appToAppResponse = (0.38f * speedRatio).coerceAtLeast(0.18f)

                        // Apply parameters per SpringParam key
                        for (i in result.indices) {
                            val sp = result[i] ?: continue
                            val key = sKeyField?.get(sp) as? String ?: continue

                            var newDamping: Float? = null
                            var newResponse: Float? = null

                            when {
                                animType.startsWith("OPEN_FROM_HOME") || animType.startsWith("OPEN_FROM_ELEMENT") -> {
                                    when (key) {
                                        "centerY" -> { newDamping = openDamping; newResponse = openResponseCenterY }
                                        "centerX" -> { newDamping = openDamping; newResponse = openResponseCenterX }
                                        "Height", "Width", "Ratio" -> { newDamping = openDamping; newResponse = openResponseSize }
                                        "Radius", "RadiusRatio" -> { newDamping = openCornerDamping; newResponse = openCornerResponse }
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { newDamping = openAlphaDamping; newResponse = openAlphaResponse }
                                    }
                                }
                                animType.startsWith("CLOSE_TO_HOME") || animType.startsWith("CLOSE_TO_ELEMENT") -> {
                                    when (key) {
                                        "centerY" -> { newDamping = closeDampingY; newResponse = closeResponseCenterY }
                                        "centerX" -> { newDamping = closeDampingX; newResponse = closeResponseCenterX }
                                        "Height", "Width", "Ratio" -> { newDamping = closeDampingSize; newResponse = closeResponseSize }
                                        "Radius", "RadiusRatio" -> { newDamping = closeCornerDamping; newResponse = closeCornerResponse }
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { newDamping = closeAlphaDamping; newResponse = closeAlphaResponse }
                                    }
                                }
                                animType.startsWith("CLOSE_TO_RECENTS") -> {
                                    when (key) {
                                        "centerX", "centerY" -> { newDamping = recentsDamping; newResponse = recentsResponse }
                                        "Width", "Height", "Ratio" -> { newDamping = recentsDamping; newResponse = recentsResponse }
                                        "Radius", "RadiusRatio" -> { newDamping = recentsCornerDamping; newResponse = recentsCornerResponse }
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { newDamping = recentsAlphaDamping; newResponse = recentsAlphaResponse }
                                    }
                                }
                                animType.startsWith("OPEN_FROM_RECENTS") -> {
                                    when (key) {
                                        "centerX", "centerY" -> { newDamping = recentsOpenDamping; newResponse = recentsOpenResponse }
                                        "Width", "Height", "Ratio" -> { newDamping = recentsOpenDamping; newResponse = recentsOpenResponse }
                                        "Radius", "RadiusRatio" -> { newDamping = recentsCornerDamping; newResponse = recentsCornerResponse }
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { newDamping = recentsAlphaDamping; newResponse = recentsAlphaResponse }
                                    }
                                }
                                animType.startsWith("APP_TO_APP") || animType.startsWith("START_FIRST_TASK") -> {
                                    when (key) {
                                        "centerX", "centerY" -> { newDamping = appToAppDamping; newResponse = appToAppResponse }
                                        "Width", "Height", "Ratio" -> { newDamping = appToAppDamping; newResponse = appToAppResponse }
                                        "Radius", "RadiusRatio" -> { newDamping = recentsCornerDamping; newResponse = recentsCornerResponse }
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { newDamping = 0.90f; newResponse = (0.32f * speedRatio).coerceAtLeast(0.16f) }
                                    }
                                }
                            }

                            if (newDamping != null && newResponse != null) {
                                if (sDField != null && sRField != null) {
                                    sDField?.set(sp, newDamping)
                                    sRField?.set(sp, newResponse)
                                } else {
                                    val imp = (sImpField?.get(sp) as? Number)?.toFloat() ?: 0.0f
                                    result[i] = springParamConst.newInstance(key, newDamping, newResponse, imp)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error calculating iOS spring parameters", t)
                    }

                    return result
                }
            })
            Log.i(TAG, "Hooked SpringParamsProvider for iOS 26 accordion squeeze, Z-lift & zero-jitter 120fps sync")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook SpringParamsProvider", t)
        }
    }

    /**
     * Hook 8: Parabolic Ballistic Flight Arc Momentum.
     * Injects natural flick launch momentum and horizontal drift into SurfaceFlinger parameters.
     * Never forces artificial impulses on gentle releases, ensuring zero minimizing resistance.
     */
    private fun hookSfAnimImplementor(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val sfImplClass = Class.forName("com.miui.home.recents.anim.windowanim.sfanim.SfWindowAnimImplementor", false, classLoader)
            val rectFParamsClass = Class.forName("com.miui.home.recents.anim.RectFParams", false, classLoader)

            val buildMethod = sfImplClass.getDeclaredMethod("buildSfAnimParam", rectFParamsClass).apply {
                isAccessible = true
            }

            module.hook(buildMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val sfAnimParam = chain.proceed() ?: return null
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled) {
                        return sfAnimParam
                    }

                    try {
                        val rectFParams = chain.args[0] ?: return sfAnimParam
                        val animTypeObj = rectFParams.javaClass.getMethod("getAnimType").invoke(rectFParams)
                        val animType = (animTypeObj as? Enum<*>)?.name ?: ""

                        if (animType.startsWith("CLOSE_TO_HOME") || animType.startsWith("CLOSE_TO_ELEMENT")) {
                            val now = SystemClock.uptimeMillis()
                            val isRecentFlick = (now - sLastVelocityTime) < 800L
                            val vy = sLastVelocityY // in px/ms (negative when swiping up)

                            // ONLY inject ballistic launch momentum when user actually flicked upward!
                            // When NOT flicking, preserve natural gesture release velocity (ZERO drag resistance)
                            if (isRecentFlick && vy < -0.10f) {
                                val windowAnimParams = rectFParams.javaClass.getMethod("getWindowAnimParams").invoke(rectFParams)
                                val targetRect = windowAnimParams?.javaClass?.getMethod("getTargetRect")?.invoke(windowAnimParams) as? RectF

                                val (deviceWidth, deviceHeight) = getCachedDeviceDimensions(classLoader)

                                val targetCenterY = targetRect?.centerY() ?: (deviceHeight * 0.75f)
                                val normY = (targetCenterY / deviceHeight).coerceIn(0f, 1f)
                                val targetCenterX = targetRect?.centerX() ?: (deviceWidth * 0.50f)
                                val dx = targetCenterX - (deviceWidth * 0.50f)

                                val thumbNormY = if (sLastThumbY > 0f) {
                                    (sLastThumbY / deviceHeight).coerceIn(0.20f, 1.0f)
                                } else {
                                    0.75f
                                }
                                val thumbMultiplier = 1.30f - (thumbNormY * 0.35f)

                                val flickSpeed = abs(vy).coerceIn(0.10f, 12.0f)
                                // Upward ballistic launch momentum: scales with flick velocity, thumb position & bottom placement
                                val upwardVelocity = -flickSpeed * 2200f * thumbMultiplier * (0.70f + normY * 0.60f)
                                val setCenterYVelMethod = sfAnimParam.javaClass.getMethod("setCenterYVelocity", Float::class.javaPrimitiveType)
                                setCenterYVelMethod.invoke(sfAnimParam, upwardVelocity.coerceIn(-18000f, -600f))

                                // Centrifugal horizontal drift for natural flight arc if icon is off-center or gesture has horizontal velocity
                                if (abs(sLastVelocityX) > 0.10f || abs(dx) > 50f) {
                                    val driftVx = (dx / deviceWidth) * 1600f
                                    val gestureVx = if (abs(sLastVelocityX) > 0.10f) {
                                        (sLastVelocityX * 900f).coerceIn(-5000f, 5000f)
                                    } else {
                                        0f
                                    }
                                    val totalVx = (gestureVx + driftVx).coerceIn(-6000f, 6000f)
                                    val setCenterXVelMethod = sfAnimParam.javaClass.getMethod("setCenterXVelocity", Float::class.javaPrimitiveType)
                                    setCenterXVelMethod.invoke(sfAnimParam, totalVx)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error injecting ballistic trajectory into SfAnimParam", t)
                    }

                    return sfAnimParam
                }
            })
            Log.i(TAG, "Hooked SfWindowAnimImplementor for parabolic ballistic flight arcs")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook SfWindowAnimImplementor.buildSfAnimParam", t)
        }
    }

    /**
     * Hook 9: Desktop Grid HomeLayer scaling.
     * Keeps desktop grid breathing smoothly during app open/close transitions.
     */
    private fun hookHomeLayerScaling(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val homeLayerClass = Class.forName("com.miui.home.recents.anim.HomeLayerParams\$Companion", false, classLoader)
            val homeLayerParamClass = Class.forName("com.miui.home.recents.anim.HomeLayerParams", false, classLoader)

            val homeLayerParamConst = homeLayerParamClass.getDeclaredConstructor(
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            ).apply { isAccessible = true }

            val getAppStateMethod = homeLayerClass.getDeclaredMethod("getAppStateParams")
            module.hook(getAppStateMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled) {
                        return chain.proceed()
                    }
                    return homeLayerParamConst.newInstance(0.92f, 0.92f, 0.0f, 0.0f)
                }
            })

            val getHomeStateMethod = homeLayerClass.getDeclaredMethod("getHomeStateParams")
            module.hook(getHomeStateMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled) {
                        return chain.proceed()
                    }
                    return homeLayerParamConst.newInstance(1.0f, 1.0f, 0.0f, 0.0f)
                }
            })

            // Ensure HomeLayerElement is active so the desktop actually scales
            try {
                val elemClass = Class.forName("com.miui.home.recents.anim.HomeLayerElement", false, classLoader)
                val enableField = elemClass.getDeclaredField("enable").apply { isAccessible = true }
                val bindMethod = elemClass.getDeclaredMethod("bindHomeLeash")
                module.hook(bindMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        if (LauncherConfigHolder.iosAnimConfig.enabled) {
                            try { enableField.set(chain.thisObject, true) } catch (_: Throwable) {}
                        }
                        return chain.proceed()
                    }
                })
            } catch (_: Throwable) {}

            Log.i(TAG, "Hooked HomeLayerParams for desktop environmental depth scaling")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook HomeLayerParams", t)
        }
    }

    /**
     * Hook 10: Wallpaper depth zoom staging.
     */
    private fun hookWallpaperDynamics(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val wpCompanionClass = Class.forName("com.miui.home.recents.anim.WallpaperParam\$Companion", false, classLoader)
            val wpParamClass = Class.forName("com.miui.home.recents.anim.WallpaperParam", false, classLoader)

            val wpParamConst = wpParamClass.getDeclaredConstructor(
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            ).apply { isAccessible = true }

            val getAppStateMethod = wpCompanionClass.getDeclaredMethod("getAppStateParams")
            module.hook(getAppStateMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled) {
                        return chain.proceed()
                    }
                    return wpParamConst.newInstance(1.08f, 1.08f, 0.0f, 0.0f)
                }
            })

            val getHomeStateMethod = wpCompanionClass.getDeclaredMethod("getHomeStateParams")
            module.hook(getHomeStateMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.enabled) {
                        return chain.proceed()
                    }
                    return wpParamConst.newInstance(1.0f, 1.0f, 0.0f, 0.0f)
                }
            })
            Log.i(TAG, "Hooked WallpaperParam for iOS depth zoom staging")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook WallpaperParam", t)
        }
    }

    /**
     * Hook 11: Forces high animation capabilities and bypasses low-tier device throttling in DeviceLevelUtils.
     */
    private fun hookDeviceLevelUtils(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val dluClass = Class.forName("com.miui.home.launcher.common.DeviceLevelUtils", false, classLoader)

            val isUseSimpleAnimMethod = dluClass.getDeclaredMethod("isUseSimpleAnim")
            module.hook(isUseSimpleAnimMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (LauncherConfigHolder.iosAnimConfig.enabled) {
                        return false // Never use low-tier simple animation fallbacks
                    }
                    return chain.proceed()
                }
            })
            Log.i(TAG, "Hooked DeviceLevelUtils to bypass low-tier throttling")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook DeviceLevelUtils", t)
        }
    }

    /**
     * Hook 12: Eliminates landscape recents lag and touch drops in games & immersive apps.
     * Overrides NavStubView.isMistakeTouch to return false, enabling instant gesture start
     * and immediate pre-warming of SurfaceControl leashes in startRecentsAnimationPre.
     */
    private fun hookLandscapeRecentsOptimization(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val navStubClass = Class.forName("com.miui.home.recents.NavStubView", false, classLoader)
            val isMistakeTouchMethod = navStubClass.declaredMethods.find {
                it.name == "isMistakeTouch" && it.parameterCount == 0
            }
            if (isMistakeTouchMethod != null) {
                isMistakeTouchMethod.isAccessible = true
                module.hook(isMistakeTouchMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (cfg.enabled) {
                            return false
                        }
                        return chain.proceed()
                    }
                })
                Log.i(TAG, "Hooked NavStubView.isMistakeTouch to eliminate landscape recents lag in games")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook NavStubView.isMistakeTouch", t)
        }
    }

    /**
     * Hook 13: Parallel App Transitions & Zero-Delay Launch Interruptibility.
     * Enables concurrent multi-layer SurfaceFlinger animations and unlocks fast-launch:
     * - Unlocks SfAnimCapability.getUseMultiLayersForSfAnim() to allow concurrent animations.
     * - Enables FastLaunchUtils.isSupportFastLaunch so tapping icons during transitions is never blocked.
     * - Enables FastLaunchWindowElement.useFastLaunch() for zero-lag parallel app execution.
     */
    private fun hookParallelAppTransitions(module: XposedInterface, classLoader: ClassLoader) {
        // 1. Enable SurfaceFlinger Multi-Layers for concurrent window animations
        try {
            val capabilityClassNames = listOf(
                "com.miui.home.recents.anim.windowanim.sfanim.SfAnimCapability",
                "com.miui.home.recents.anim.windowanim.sfanim.SfAnimCapability\$Companion"
            )
            for (className in capabilityClassNames) {
                runCatching {
                    val clazz = Class.forName(className, false, classLoader)
                    clazz.declaredMethods.filter { method ->
                        val name = method.name.lowercase()
                        (name.contains("multilayers") || name.contains("multiwindow") || name.contains("parallel")) &&
                                (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == java.lang.Boolean::class.java)
                    }.forEach { method ->
                        method.isAccessible = true
                        module.hook(method).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any {
                                if (LauncherConfigHolder.iosAnimConfig.enabled) {
                                    return true
                                }
                                return chain.proceed()
                            }
                        })
                        Log.i(TAG, "Hooked $className.${method.name} for multi-layer concurrent animations")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook SfAnimCapability for multi-layers", t)
        }

        // 2. Enable Fast Launch so tapping an app while another is animating starts immediately
        try {
            val fastLaunchClassNames = listOf(
                "com.miui.home.launcher.anim.FastLaunchUtils",
                "com.miui.home.launcher.anim.FastLaunchUtils\$Companion"
            )
            for (className in fastLaunchClassNames) {
                runCatching {
                    val clazz = Class.forName(className, false, classLoader)
                    clazz.declaredMethods.filter { method ->
                        method.name.contains("FastLaunch", ignoreCase = true) &&
                                (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == java.lang.Boolean::class.java)
                    }.forEach { method ->
                        method.isAccessible = true
                        module.hook(method).intercept(object : XposedInterface.Hooker {
                            override fun intercept(chain: XposedInterface.Chain): Any {
                                if (LauncherConfigHolder.iosAnimConfig.enabled) {
                                    return true
                                }
                                return chain.proceed()
                            }
                        })
                        Log.i(TAG, "Hooked $className.${method.name} for instant interruptible app launch")
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook FastLaunchUtils", t)
        }

        // 3. FastLaunchWindowElement optimization
        try {
            val elemClass = Class.forName("com.miui.home.launcher.anim.FastLaunchWindowElement", false, classLoader)
            elemClass.declaredMethods.filter { method ->
                method.name.contains("FastLaunch", ignoreCase = true) &&
                        (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == java.lang.Boolean::class.java)
            }.forEach { method ->
                method.isAccessible = true
                module.hook(method).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any {
                        if (LauncherConfigHolder.iosAnimConfig.enabled) {
                            return true
                        }
                        return chain.proceed()
                    }
                })
                Log.i(TAG, "Hooked FastLaunchWindowElement.${method.name} for fast window element launch")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "FastLaunchWindowElement not found or hook skipped", t)
        }
    }
}
