package dev.hyperios.app.launcher

import android.content.Context
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
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

    // Back gesture single-pop debouncing
    @Volatile
    private var sBackGestureArmed: Boolean = false
    @Volatile
    private var sLastBackHapticTime: Long = 0L

    // Recents Stack Scroll Crossing Tracking
    @Volatile
    private var sIsRecentsScrolling: Boolean = false
    @Volatile
    private var sIsRecentsTouchActive: Boolean = false
    @Volatile
    private var sLastScrollP: Float? = null
    @Volatile
    private var sLastCrossedInteger: Int? = null
    @Volatile
    private var sLastStackRecentsTickTime: Long = 0L

    @Volatile
    private var sHapticHelper: Any? = null
    @Volatile
    private var sPerformExtMethod: Method? = null
    @Volatile
    private var sHfInstance: Any? = null
    @Volatile
    private var sPerformFastScrollMethod: Method? = null

    private var sIsScrollingField: Field? = null
    private var sIsStackMethod: Method? = null

    private var sKeyField: Field? = null
    private var sImpField: Field? = null
    private var sDField: Field? = null
    private var sRField: Field? = null

    fun init(module: XposedInterface, classLoader: ClassLoader?) {
        if (classLoader == null) return

        hookGestureVelocityTracking(module, classLoader)
        hookDragResistanceAndScaling(module, classLoader)
        hookLandscapeRecentsOptimization(module, classLoader)
        hookFolder1to1IconTrajectories(module, classLoader)
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
                    if (!cfg.zeroFrictionDrag) {
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
                    if (!cfg.zeroFrictionDrag) {
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
                        if (!cfg.zeroFrictionDrag) {
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
                        if (!cfg.recentsCentering) {
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
                        if (!cfg.recentsCentering) {
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
                    if (!cfg.recentsCentering) {
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
     * As each app crosses the screen center reference line during horizontal swipes,
     * triggers a crisp light mechanical clock tick (MIUI_GEAR_LIGHT / EFFECT_TICK).
     * Functional ONLY in stack style recents.
     */
    private fun hookRecentsStackScrollHaptics(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val tsvClass = Class.forName("com.miui.home.recents.views.TaskStackView", false, classLoader)

            // 1. Track scrolling start
            tsvClass.declaredMethods.firstOrNull { it.name == "onScrollStart" && it.parameterTypes.isEmpty() }?.let { method ->
                module.hook(method).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        sIsRecentsScrolling = true
                        sLastCrossedInteger = null
                        sLastScrollP = null
                        return chain.proceed()
                    }
                })
            }

            // 2. Track scrolling end
            tsvClass.declaredMethods.firstOrNull { it.name == "onScrollEnd" && it.parameterTypes.isEmpty() }?.let { method ->
                module.hook(method).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        sIsRecentsScrolling = false
                        sLastCrossedInteger = null
                        sLastScrollP = null
                        return chain.proceed()
                    }
                })
            }

            // 3. Track touch gestures on TaskStackViewTouchHandler
            try {
                val touchHandlerClass = Class.forName("com.miui.home.recents.views.TaskStackViewTouchHandler", false, classLoader)
                touchHandlerClass.declaredMethods.firstOrNull {
                    it.name == "onTouchEvent" && it.parameterTypes.size == 1 &&
                        it.parameterTypes[0] == MotionEvent::class.java
                }?.let { method ->
                    module.hook(method).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            val ev = chain.args[0] as? MotionEvent
                            if (ev != null) {
                                when (ev.actionMasked) {
                                    MotionEvent.ACTION_DOWN -> {
                                        sIsRecentsTouchActive = true
                                        sLastCrossedInteger = null
                                        sLastScrollP = null
                                    }
                                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                        sIsRecentsTouchActive = false
                                    }
                                }
                            }
                            return chain.proceed()
                        }
                    })
                }
            } catch (t: Throwable) {
                Log.d(TAG, "TaskStackViewTouchHandler onTouchEvent hook fallback", t)
            }

            // 4. Hook onStackScrollChanged(float f, AnimationProps animationProps) on TaskStackView
            tsvClass.declaredMethods.firstOrNull {
                it.name == "onStackScrollChanged" && it.parameterTypes.size == 2 &&
                    (it.parameterTypes[0] == java.lang.Float.TYPE || it.parameterTypes[0] == java.lang.Float::class.java)
            }?.let { method ->
                module.hook(method).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (!cfg.stackRecentsHaptics) {
                            return result
                        }

                        val tsv = chain.thisObject as? View ?: return result

                        // Functional ONLY in stack style recents (TaskStackViewLayoutStyleStack has styleValue == 2)
                        val isStack = try {
                            val layoutStyleMethod = tsv.javaClass.getMethod("getTaskStackViewLayoutStyle")
                            val layoutStyle = layoutStyleMethod.invoke(tsv)
                            val styleVal = layoutStyle?.javaClass?.getMethod("getStyleValue")?.invoke(layoutStyle) as? Int
                            if (styleVal == 2) {
                                true
                            } else {
                                val isStackMethod = sIsStackMethod ?: Class.forName(
                                    "com.miui.home.recents.TaskStackViewLayoutStyle",
                                    false,
                                    tsv.context.classLoader
                                ).getMethod("isStackLayout").also { sIsStackMethod = it }
                                isStackMethod.invoke(null) as? Boolean ?: false
                            }
                        } catch (_: Throwable) {
                            try {
                                val isStackMethod = sIsStackMethod ?: Class.forName(
                                    "com.miui.home.recents.TaskStackViewLayoutStyle",
                                    false,
                                    tsv.context.classLoader
                                ).getMethod("isStackLayout").also { sIsStackMethod = it }
                                isStackMethod.invoke(null) as? Boolean ?: false
                            } catch (_: Throwable) { false }
                        }
                        if (!isStack) return result

                        val f = (chain.args[0] as? Number)?.toFloat() ?: return result

                        val isScrolling = sIsRecentsScrolling || sIsRecentsTouchActive || try {
                            val field = sIsScrollingField ?: tsv.javaClass.getDeclaredField("mIsScrolling").apply { isAccessible = true }.also { sIsScrollingField = it }
                            field.getBoolean(tsv)
                        } catch (_: Throwable) { false }

                        if (!isScrolling) {
                            sLastScrollP = f
                            return result
                        }

                        val prev = sLastScrollP
                        sLastScrollP = f

                        if (prev != null && prev != f) {
                            val minVal = minOf(prev, f)
                            val maxVal = maxOf(prev, f)
                            val lowInt = kotlin.math.ceil(minVal).toInt()
                            val highInt = kotlin.math.floor(maxVal).toInt()
                            if (lowInt <= highInt) {
                                val targetInt = if (f < prev) lowInt else highInt
                                val now = SystemClock.uptimeMillis()
                                if (sLastCrossedInteger != targetInt && now - sLastStackRecentsTickTime > 35L) {
                                    sLastCrossedInteger = targetInt
                                    sLastStackRecentsTickTime = now
                                    triggerLightClockTickHaptic(tsv)
                                }
                            }
                        }

                        return result
                    }
                })
            }

            Log.i(TAG, "Hooked TaskStackView.onStackScrollChanged for Stack Recents center-crossing haptics")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook TaskStackView for scroll haptics", t)
        }
    }

    /**
     * Triggers a single, clean effect ID 5 (MIUI native ext haptic fast scroll tick) on the linear motor.
     * Uses exclusively this single pipeline to avoid guitar string / spring resonance effects.
     */
    private fun triggerLightClockTickHaptic(view: View?) {
        try {
            val cl = view?.context?.classLoader ?: ClassLoader.getSystemClassLoader()
            var helper = sHapticHelper
            var method = sPerformExtMethod

            if (helper == null) {
                val hfCompatClass = Class.forName("com.miui.home.common.hapticfeedback.HapticFeedbackCompat", false, cl)
                val instance = hfCompatClass.getMethod("getInstance").invoke(null)
                if (instance != null) {
                    sHfInstance = instance
                    val helperField = try {
                        instance.javaClass.getDeclaredField("mHapticHelper").apply { isAccessible = true }
                    } catch (_: Throwable) { null }
                    helper = helperField?.get(instance)
                    sHapticHelper = helper

                    if (helper != null) {
                        method = helper.javaClass.methods.firstOrNull {
                            it.name.contains("performExtHapticFeedback") && it.parameterTypes.size == 1 &&
                                (it.parameterTypes[0] == Integer.TYPE || it.parameterTypes[0] == java.lang.Integer::class.java)
                        }
                        sPerformExtMethod = method
                    }

                    if (sPerformFastScrollMethod == null) {
                        sPerformFastScrollMethod = hfCompatClass.methods.firstOrNull {
                            it.name == "performFastScrollSection" && it.parameterTypes.size == 1
                        }
                    }
                }
            }

            if (helper != null && method != null) {
                method.invoke(helper, 5)
                return
            }

            val inst = sHfInstance
            val fastScroll = sPerformFastScrollMethod
            if (inst != null && fastScroll != null && view != null) {
                fastScroll.invoke(inst, view)
                return
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to trigger effect ID 5 haptic", t)
        }
    }

    /**
     * Hook 5: Refined, satisfying linear motor haptics:
     * - Back Gesture: Crisp elastic tactile pop (with single-pop debouncing, never double-ticks)
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

                // 1. Hook performGestureReadyBack() -> Crisp elastic pop (primary trigger)
                val readyBackMethod = clazz.getDeclaredMethod("performGestureReadyBack")
                module.hook(readyBackMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (!cfg.refinedHaptics) {
                            return chain.proceed()
                        }
                        val now = SystemClock.uptimeMillis()
                        if (now - sLastBackHapticTime >= 450L) {
                            sLastBackHapticTime = now
                            sBackGestureArmed = true
                            triggerBackPopHaptic(chain.thisObject)
                        }
                        return null // Suppress heavy thud
                    }
                })

                // 2. Hook performGestureBackHandUp() -> Crisp elastic pop on release (only if readyBack didn't already fire!)
                val handUpMethod = clazz.getDeclaredMethod("performGestureBackHandUp")
                module.hook(handUpMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (!cfg.refinedHaptics) {
                            return chain.proceed()
                        }
                        val now = SystemClock.uptimeMillis()
                        val alreadyFired = sBackGestureArmed || (now - sLastBackHapticTime < 450L)
                        sBackGestureArmed = false
                        if (!alreadyFired) {
                            sLastBackHapticTime = now
                            triggerBackPopHaptic(chain.thisObject)
                        }
                        return null // Suppress heavy thud
                    }
                })

                // 3. Hook performEnterRecent(View view) -> Satisfying latch tick instead of heavy thud
                val enterRecentMethod = clazz.getDeclaredMethod("performEnterRecent", View::class.java)
                module.hook(enterRecentMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (!cfg.refinedHaptics) {
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
                    if (!cfg.zeroFrictionDrag) {
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
                    if (!cfg.zeroFrictionDrag) {
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
                    val animType = (chain.args[0] as? Enum<*>)?.name ?: ""
                    if (animType.contains("ELEMENT") || animType.contains("DYNAMIC_ISLAND", ignoreCase = true)) {
                        // Dynamic Island / Status Bar Element: 100% resistant to custom transition overrides!
                        return result
                    }

                    val cfg = LauncherConfigHolder.iosAnimConfig
                    val speedRatio = (getAnimationSpeedRatio(classLoader) / cfg.animSpeed.coerceIn(0.50f, 2.0f))
                    val openDampingScale = (cfg.openSpringDamping / 0.98f).coerceIn(0.70f, 1.40f)
                    val closeDampingScale = (cfg.closeSpringDamping / 0.84f).coerceIn(0.70f, 1.40f)
                    val dampingScale = when {
                        animType.startsWith("OPEN") -> openDampingScale
                        animType.startsWith("CLOSE_TO_HOME") -> closeDampingScale
                        else -> openDampingScale
                    }

                    if (!cfg.enabled) {
                        try {
                            for (i in result.indices) {
                                val sp = result[i] ?: continue
                                val origD = (sDField?.get(sp) as? Number)?.toFloat()
                                val origR = (sRField?.get(sp) as? Number)?.toFloat()
                                if (origD != null && origR != null) {
                                    val newD = (origD * dampingScale).coerceIn(0.30f, 2.0f)
                                    val newR = (origR * speedRatio).coerceAtLeast(0.05f)
                                    sDField?.set(sp, newD)
                                    sRField?.set(sp, newR)
                                }
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error scaling stock HyperOS spring parameters", t)
                        }
                        return result
                    }

                    try {

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

                        // 1. OPEN DYNAMICS (Distinct Vertical Uplift for Bottom Apps, Downward Dip for Top Apps):
                        val isBottomApp = normY >= 0.5f
                        val openDamping = if (isBottomApp) 0.86f else 0.88f
                        val openResponseCenterY = (if (isBottomApp) 0.44f else 0.46f) * speedRatio
                        val openResponseCenterX = (0.34f * speedRatio).coerceAtLeast(0.18f)
                        val openResponseHeight = (0.36f * speedRatio).coerceAtLeast(0.18f)
                        val openResponseWidth = (0.36f * speedRatio).coerceAtLeast(0.18f)
                        val openResponseRatio = (0.36f * speedRatio).coerceAtLeast(0.18f)
                        val openCornerDamping = 1.05f
                        val openCornerResponse = (0.28f * speedRatio).coerceAtLeast(0.16f)
                        val openAlphaDamping = 0.98f
                        val openAlphaResponse = (0.22f * speedRatio).coerceAtLeast(0.14f)

                        // 2. CLOSE DYNAMICS (High Soaring Parabolic Apex for Bottom Apps, Direct Ascent for Top Apps):
                        val closeDampingY = if (isBottomApp) {
                            if (isRecentFlick) 0.78f else 0.80f // Deep physical underdamping for elevated parabolic flight
                        } else {
                            0.96f // Direct ascent straight into top icon without overshoot
                        }
                        val closeDampingX = if (isRecentFlick) 0.94f else 0.96f
                        val closeDampingSize = if (isRecentFlick) 0.94f else 0.96f

                        val closeResponseCenterY = if (isBottomApp) {
                            (((0.50f - (vertOffset * 0.03f)) * speedRatio) + flickBoost).coerceAtLeast(0.24f)
                        } else {
                            (((0.38f - (vertOffset * 0.03f)) * speedRatio) + flickBoost).coerceAtLeast(0.18f)
                        }
                        val closeResponseCenterX = (((0.36f - (horizOffset * 0.03f)) * speedRatio) + flickBoost).coerceAtLeast(0.18f)
                        val closeResponseSize = (((0.38f + (vertOffset * 0.03f)) * speedRatio) + flickBoost).coerceAtLeast(0.20f)
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

                        val appToAppDamping = 1.00f // Instantaneous, zero-lag parallel transition
                        val appToAppResponse = (0.30f * speedRatio).coerceAtLeast(0.16f)

                        // Apply parameters per SpringParam key
                        for (i in result.indices) {
                            val sp = result[i] ?: continue
                            val key = sKeyField?.get(sp) as? String ?: continue

                            var newDamping: Float? = null
                            var newResponse: Float? = null

                            when {
                                animType.startsWith("OPEN_FROM_HOME") -> {
                                    when (key) {
                                        "centerY" -> { newDamping = openDamping; newResponse = openResponseCenterY }
                                        "centerX" -> { newDamping = openDamping; newResponse = openResponseCenterX }
                                        "Height" -> { newDamping = openDamping; newResponse = openResponseHeight }
                                        "Width" -> { newDamping = openDamping; newResponse = openResponseWidth }
                                        "Ratio" -> { newDamping = openDamping; newResponse = openResponseRatio }
                                        "Radius", "RadiusRatio" -> { newDamping = openCornerDamping; newResponse = openCornerResponse }
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { newDamping = openAlphaDamping; newResponse = openAlphaResponse }
                                    }
                                }
                                animType.startsWith("CLOSE_TO_HOME") -> {
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
                                val finalDamping = (newDamping * dampingScale).coerceIn(0.60f, 1.35f)
                                if (sDField != null && sRField != null) {
                                    sDField?.set(sp, finalDamping)
                                    sRField?.set(sp, newResponse)
                                } else {
                                    val imp = (sImpField?.get(sp) as? Number)?.toFloat() ?: 0.0f
                                    result[i] = springParamConst.newInstance(key, finalDamping, newResponse, imp)
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
     * Hook 8: Ballistic Flight Arc Momentum & Horizontal Keystone Squeeze.
     * Injects natural flick launch momentum, reverse horizontal drift, and StretchType keystone into SurfaceFlinger.
     * - Reverse Curve Symmetry: Minimizing lower-half apps ALWAYS climbs UP into an apex before falling into the icon.
     * - Horizontal Width Squeeze: HMatchVTop (lower apps) / VMatchHCenter (upper apps) tapers width towards the icon.
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

                        // Check if animation is related to Dynamic Island / status bar element
                        val isElementAnim = animType.contains("ELEMENT") || animType.contains("DYNAMIC_ISLAND", ignoreCase = true)
                        val hasElementTarget = runCatching {
                            val targetSet = rectFParams.javaClass.getMethod("getTargetApps").invoke(rectFParams)
                            targetSet?.javaClass?.getMethod("getElementTarget")?.invoke(targetSet) != null
                        }.getOrDefault(false)

                        if (isElementAnim || hasElementTarget) {
                            // Dynamic Island stock animation resistance: 100% stock SurfaceFlinger params!
                            // Do not modify any velocities, damping, or stretch types!
                            return sfAnimParam
                        }

                        val (deviceWidth, deviceHeight) = getCachedDeviceDimensions(classLoader)
                        val windowAnimParams = runCatching {
                            rectFParams.javaClass.getMethod("getWindowAnimParams").invoke(rectFParams)
                        }.getOrNull()
                        val targetRect = runCatching {
                            windowAnimParams?.javaClass?.getMethod("getTargetRect")?.invoke(windowAnimParams) as? RectF
                        }.getOrNull()
                        val startRect = runCatching {
                            windowAnimParams?.javaClass?.getMethod("getStartRect")?.invoke(windowAnimParams) as? RectF
                        }.getOrNull()

                        val isHomeOpen = animType.startsWith("OPEN_FROM_HOME")
                        val isHomeClose = animType.startsWith("CLOSE_TO_HOME")
                        val isOpen = isHomeOpen
                        val isClose = isHomeClose

                        val iconRect = if (isOpen) (startRect ?: targetRect) else (targetRect ?: startRect)
                        val iconCenterY = iconRect?.centerY() ?: (deviceHeight * 0.75f)
                        val normY = (iconCenterY / deviceHeight).coerceIn(0f, 1f)
                        val iconCenterX = iconRect?.centerX() ?: (deviceWidth * 0.50f)
                        val dx = iconCenterX - (deviceWidth * 0.50f)

                        val now = SystemClock.uptimeMillis()
                        val isRecentFlick = (now - sLastVelocityTime) < 800L
                        val vy = sLastVelocityY // in px/ms (negative when swiping up)
                        val vx = sLastVelocityX

                        val setCenterYVelMethod = runCatching {
                            sfAnimParam.javaClass.getMethod("setCenterYVelocity", Float::class.javaPrimitiveType)
                        }.getOrNull()
                        val setCenterXVelMethod = runCatching {
                            sfAnimParam.javaClass.getMethod("setCenterXVelocity", Float::class.javaPrimitiveType)
                        }.getOrNull()
                        val setWidthVelMethod = runCatching {
                            sfAnimParam.javaClass.getMethod("setWidthVelocity", Float::class.javaPrimitiveType)
                        }.getOrNull()
                        val setHeightVelMethod = runCatching {
                            sfAnimParam.javaClass.getMethod("setHeightVelocity", Float::class.javaPrimitiveType)
                        }.getOrNull()
                        val setRatioVelMethod = runCatching {
                            sfAnimParam.javaClass.getMethod("setRatioVelocity", Float::class.javaPrimitiveType)
                        }.getOrNull()

                        if (isHomeOpen) {
                            if (cfg.openUpliftSurge) {
                                val openMultiplier = cfg.openSurgeLift.coerceIn(0.20f, 3.0f)
                                if (normY >= 0.50f) {
                                    // Bottom-half apps (dock/lower rows): open with a DISTINCT UPLIFT surge right on tap!
                                    val openUpward = (-32000f - ((normY - 0.50f) * 16000f)) * openMultiplier
                                    setCenterYVelMethod?.invoke(sfAnimParam, openUpward)

                                    val openVx = -(dx / deviceWidth) * 2600f * openMultiplier
                                    setCenterXVelMethod?.invoke(sfAnimParam, openVx)

                                    // Vertical squeeze to imitate window uplifting:
                                    setWidthVelMethod?.invoke(sfAnimParam, 2400f * openMultiplier)
                                    setHeightVelMethod?.invoke(sfAnimParam, -4200f * openMultiplier)
                                    setRatioVelMethod?.invoke(sfAnimParam, -4.5f * openMultiplier)
                                } else {
                                    // Top-half apps: plunges down distinctly first before expanding!
                                    val openDownward = (+26000f + ((0.50f - normY) * 14000f)) * openMultiplier
                                    setCenterYVelMethod?.invoke(sfAnimParam, openDownward)

                                    val openVx = -(dx / deviceWidth) * 2000f * openMultiplier
                                    setCenterXVelMethod?.invoke(sfAnimParam, openVx)

                                    setWidthVelMethod?.invoke(sfAnimParam, 1800f * openMultiplier)
                                    setHeightVelMethod?.invoke(sfAnimParam, -3600f * openMultiplier)
                                    setRatioVelMethod?.invoke(sfAnimParam, -3.8f * openMultiplier)
                                }
                            }
                        } else if (isHomeClose) {
                            if (cfg.closeUpliftSurge) {
                                val arcMultiplier = cfg.arcApexLift.coerceIn(0.20f, 3.0f)
                                val flickSpeed = if (isRecentFlick && vy < -0.10f) abs(vy).coerceIn(0.10f, 12.0f) else 0f
                                val thumbNormY = if (sLastThumbY > 0f) {
                                    (sLastThumbY / deviceHeight).coerceIn(0.20f, 1.0f)
                                } else {
                                    0.75f
                                }
                                val thumbMultiplier = (1.35f - (thumbNormY * 0.35f)).coerceIn(0.95f, 1.35f)

                                if (normY >= 0.50f) {
                                    // Bottom-half apps: soar high up into a massive parabolic apex before landing into icon!
                                    val baseUpward = (32000f + ((normY - 0.50f) * 16000f)) * arcMultiplier
                                    val flickAdd = flickSpeed * 5000f * arcMultiplier
                                    val totalUpward = -(baseUpward + flickAdd) * thumbMultiplier
                                    val vyLaunch = totalUpward.coerceIn(-64000f * arcMultiplier, -4000f * arcMultiplier)
                                    setCenterYVelMethod?.invoke(sfAnimParam, vyLaunch)

                                    // Symmetrical vertical squeeze on close at the exact same point in space:
                                    setWidthVelMethod?.invoke(sfAnimParam, 2400f * arcMultiplier)
                                    setHeightVelMethod?.invoke(sfAnimParam, -4200f * arcMultiplier)
                                    setRatioVelMethod?.invoke(sfAnimParam, -4.5f * arcMultiplier)
                                } else {
                                    // Top-half apps: fly directly up into top icon without overshoot
                                    val topUpward = (-(12000f + flickSpeed * 3000f) * arcMultiplier).coerceIn(-36000f * arcMultiplier, -2000f * arcMultiplier)
                                    setCenterYVelMethod?.invoke(sfAnimParam, topUpward)

                                    setWidthVelMethod?.invoke(sfAnimParam, 1800f * arcMultiplier)
                                    setHeightVelMethod?.invoke(sfAnimParam, -3600f * arcMultiplier)
                                    setRatioVelMethod?.invoke(sfAnimParam, -3.8f * arcMultiplier)
                                }

                                // Reverse horizontal trajectory: drift towards the target icon's side
                                val driftVx = (dx / deviceWidth) * 2800f * arcMultiplier
                                val gestureVx = if (isRecentFlick && abs(vx) > 0.10f) {
                                    (vx * 900f).coerceIn(-5000f * arcMultiplier, 5000f * arcMultiplier)
                                } else {
                                    0f
                                }
                                val totalVx = (gestureVx + driftVx).coerceIn(-7000f * arcMultiplier, 7000f * arcMultiplier)
                                setCenterXVelMethod?.invoke(sfAnimParam, totalVx)
                            }
                        }

                        // Apply Horizontal Edge Squeeze via StretchType on BOTH open and close transitions
                        if (cfg.keystoneSqueeze && (isOpen || isClose)) {
                            runCatching {
                                val iconsField = sfAnimParam.javaClass.getDeclaredField("icons").apply { isAccessible = true }
                                val iconsArray = iconsField.get(sfAnimParam) as? Array<*>
                                if (iconsArray != null && iconsArray.isNotEmpty()) {
                                    val stretchTypeClass = Class.forName("com.miui.home.recents.anim.windowanim.sfanim.StretchType", false, classLoader)
                                    val hMatchVTop = java.lang.Enum.valueOf(stretchTypeClass as Class<out Enum<*>>, "HMatchVTop")
                                    val vMatchHCenter = java.lang.Enum.valueOf(stretchTypeClass, "VMatchHCenter")
                                    val chosenStretch = if (normY >= 0.50f) hMatchVTop else vMatchHCenter

                                    for (layerParam in iconsArray) {
                                        if (layerParam != null) {
                                            val stField = layerParam.javaClass.getDeclaredField("stretchType").apply { isAccessible = true }
                                            stField.set(layerParam, chosenStretch)
                                        }
                                    }
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error injecting ballistic trajectory into SfAnimParam", t)
                    }

                    return sfAnimParam
                }
            })
            Log.i(TAG, "Hooked SfWindowAnimImplementor for parabolic ballistic flight arcs and keystone stretch")
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
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (cfg.enabled) {
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
     * Hook 11: Forces high animation capabilities and hardware SurfaceFlinger direct animation on ALL device tiers.
     * Hooks both DeviceLevelUtils and SfAnimCapability.
     */
    private fun hookDeviceLevelUtils(module: XposedInterface, classLoader: ClassLoader) {
        // 1. Hook com.miui.home.common.utils.DeviceLevelUtils
        try {
            val dluClass = Class.forName("com.miui.home.common.utils.DeviceLevelUtils", false, classLoader)

            val simpleAnimMethods = listOf(
                "isUseSimpleAnim",
                "isUseSimpleOpenCloseAnim",
                "isUseSimpleOpenOrCloseAnim",
                "isLowLevelDevice",
                "isLowLevelDeviceFromFolme",
                "isMiddleLevelDeviceFromFolme",
                "isLowLevelOrLiteDevice",
                "isLowLevelOrLiteDeviceOrSpecialDevice",
                "isLowMemoryDevices",
                "isLowMemoryDevicesOrSpecialDevice"
            )
            for (name in simpleAnimMethods) {
                runCatching {
                    val m = dluClass.getDeclaredMethod(name)
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (LauncherConfigHolder.iosAnimConfig.enabled) {
                                return false // Never use low/mid-tier fallback on any device
                            }
                            return chain.proceed()
                        }
                    })
                }
            }

            val highLevelMethods = listOf("isHighLevelDeviceByDefault", "isHighLevelDeviceFromFolme")
            for (name in highLevelMethods) {
                runCatching {
                    val m = dluClass.getDeclaredMethod(name)
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (LauncherConfigHolder.iosAnimConfig.enabled) {
                                return true
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
            Log.i(TAG, "Hooked DeviceLevelUtils to bypass low-tier throttling")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook DeviceLevelUtils", t)
        }

        // 2. Hook com.miui.home.common.sfanim.SfAnimCapability to guarantee SurfaceFlinger direct hardware animation
        try {
            val sfCapClass = Class.forName("com.miui.home.common.sfanim.SfAnimCapability", false, classLoader)
            runCatching {
                val fld = sfCapClass.getDeclaredField("shouldUseSfAnim").apply { isAccessible = true }
                fld.set(null, true)
            }
            runCatching {
                val fld = sfCapClass.getDeclaredField("useMultiLayersForSfAnim").apply { isAccessible = true }
                fld.set(null, true)
            }

            val companionClass = Class.forName("com.miui.home.common.sfanim.SfAnimCapability\$Companion", false, classLoader)
            val trueMethods = listOf("getShouldUseSfAnim", "isSceneSupport", "getUseMultiLayersForSfAnim")
            for (name in trueMethods) {
                runCatching {
                    val m = companionClass.getDeclaredMethod(name)
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (LauncherConfigHolder.iosAnimConfig.enabled) {
                                return true
                            }
                            return chain.proceed()
                        }
                    })
                }
            }
            Log.i(TAG, "Hooked SfAnimCapability to guarantee SurfaceFlinger direct animation on all devices")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook SfAnimCapability", t)
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
                        if (cfg.enabled && cfg.landscapeGameOpt) {
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
     * Hook 14: Universal Modular 1:1 Folder Icon Trajectory Tracking & OriginOS Expansion Physics.
     *
     * 1. Eliminates outline borders (no border enclosing expanded folders).
     * 2. Modular 1:1 affine trajectory mapping for ALL folder types & grid sizes:
     *    - Regular (1x1)
     *    - Enlarged (2x2) standard (3 large + 4 small) AND "Perfect Grid" (4 full-size large icons)
     *    - 1x3, 3x1, XXL (2x4, 3x3, etc.), 3x6, and any custom grid layout.
     *    - Matches each preview icon dynamically to its open grid cell via IShortcutInfo / buddy tag
     *      and actual on-screen measured coordinates.
     * 3. OriginOS Physical Dynamics:
     *    - Opening: PathInterpolator(0.33f, 0.0f, 0.67f, 1.0f) / Folme Spring (zeta=0.88, T0=0.32s), zero delay.
     *    - Closing: PathInterpolator(0.25f, 0.1f, 0.25f, 1.0f) / Folme Spring (zeta=0.90, T0=0.28s), zero delay.
     *    - Seamless 1:1 continuous icon morph with zero ghosting or double icons.
     */
    private fun hookFolder1to1IconTrajectories(module: XposedInterface, classLoader: ClassLoader) {
        // 1. Hook FolderAnimController.getIconIndex: safe linear lookup preventing Collectors.toMap Duplicate key crash
        try {
            val folderAnimControllerClass = Class.forName("com.miui.home.folder.FolderAnimController", false, classLoader)
            val getIconIndexMethod = folderAnimControllerClass.getDeclaredMethod("getIconIndex", Integer::class.java).apply {
                isAccessible = true
            }

            module.hook(getIconIndexMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.folderTracking) return chain.proceed()

                    val controller = chain.thisObject ?: return chain.proceed()
                    val num = (chain.args[0] as? Integer)?.toInt() ?: return chain.proceed()
                    val locMapField = controller.javaClass.getDeclaredField("mFolderIconLocMap").apply { isAccessible = true }
                    val locMap = locMapField.get(controller) as? Map<*, *> ?: return chain.proceed()
                    for ((k, v) in locMap) {
                        if ((v as? Number)?.toInt() == num) {
                            val keyInt = (k as? Number)?.toInt() ?: continue
                            return Integer.valueOf(keyInt)
                        }
                    }
                    return null
                }
            })
            Log.i(TAG, "Hooked FolderAnimController.getIconIndex for safe linear key lookup")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook FolderAnimController.getIconIndex", t)
        }

        // 2. Hook FolderAnimController.initIconLoc: modular 1:1 preview-to-grid mapping with guaranteed unique values
        try {
            val folderAnimControllerClass = Class.forName("com.miui.home.folder.FolderAnimController", false, classLoader)
            val folderIconClass = Class.forName("com.miui.home.folder.FolderIcon", false, classLoader)
            val initIconLocMethod = folderAnimControllerClass.getDeclaredMethod(
                "initIconLoc",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                folderIconClass
            ).apply { isAccessible = true }

            module.hook(initIconLocMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.folderTracking) return chain.proceed()

                    try {
                        val controller = chain.thisObject ?: return chain.proceed()
                        val folderIcon = chain.args[3] ?: return chain.proceed()

                        val locMapField = controller.javaClass.getDeclaredField("mFolderIconLocMap").apply { isAccessible = true }
                        val locMap = locMapField.get(controller) as? MutableMap<Int, Int> ?: return chain.proceed()
                        locMap.clear()

                        val gridViewField = controller.javaClass.getDeclaredField("mAnimaFolderGridView").apply { isAccessible = true }
                        val folderGridView = gridViewField.get(controller) as? ViewGroup

                        val getPreviewArrayMethod = folderIcon.javaClass.getMethod("getPreviewArray")
                        val previewArray = getPreviewArrayMethod.invoke(folderIcon) as? Array<*>
                        val previewCount = previewArray?.size ?: 0
                        val gridChildCount = folderGridView?.childCount ?: 0

                        if (previewCount > 0 && gridChildCount > 0) {
                            val matchedGridIndices = mutableSetOf<Int>()

                            // Pass 1: Match preview views to open grid views by IShortcutInfo / buddy tag
                            for (p in 0 until previewCount) {
                                val pv = previewArray?.get(p) as? View
                                val pvBuddy = runCatching {
                                    pv?.javaClass?.getMethod("getMBuddyInfo")?.invoke(pv)
                                }.getOrNull() ?: pv?.tag

                                if (pvBuddy != null && folderGridView != null) {
                                    for (g in 0 until gridChildCount) {
                                        if (matchedGridIndices.contains(g)) continue
                                        val gv = folderGridView.getChildAt(g)
                                        val gvTag = gv?.tag ?: runCatching {
                                            val m = folderGridView.javaClass.getMethod("getChildView", Int::class.javaPrimitiveType)
                                            (m.invoke(folderGridView, g) as? View)?.tag
                                        }.getOrNull()
                                        if (gvTag != null && (gvTag == pvBuddy || gvTag.toString() == pvBuddy.toString())) {
                                            locMap[p] = g
                                            matchedGridIndices.add(g)
                                            break
                                        }
                                    }
                                }
                            }

                            // Pass 2: Fill remaining unmatched preview slots with strictly UNIQUE grid indices
                            val isXXL = folderIcon.javaClass.simpleName.contains("2x2_9") || runCatching {
                                val m = folderIcon.javaClass.getMethod("getLargeIconNum")
                                (m.invoke(folderIcon) as? Number)?.toInt() == 8
                            }.getOrDefault(false) || runCatching {
                                val getFolderInfo = folderIcon.javaClass.getMethod("getFolderInfo")
                                val fi = getFolderInfo.invoke(folderIcon)
                                val itField = fi?.javaClass?.getField("itemType")
                                itField?.getInt(fi) == 22
                            }.getOrDefault(false)

                            val is2x2 = !isXXL && runCatching {
                                val getFolderIconType = folderIcon.javaClass.getMethod("getFolderIconType")
                                (getFolderIconType.invoke(folderIcon) as? Number)?.toInt() == 2
                            }.getOrDefault(false)

                            val cols = runCatching {
                                val m = folderGridView?.javaClass?.getMethod("getColumnCount")
                                (m?.invoke(folderGridView) as? Number)?.toInt()
                            }.getOrNull() ?: 3

                            for (p in 0 until previewCount) {
                                if (locMap.containsKey(p)) continue

                                // Calculate ideal candidate grid index for this slot
                                val candidate = if (is2x2) {
                                    if (gridChildCount == 4) {
                                        // Perfect Grid (2x2 layout: 4 icons full size)
                                        when (p) {
                                            0 -> 0
                                            1 -> 1
                                            2 -> cols
                                            3 -> cols + 1
                                            else -> null
                                        }
                                    } else {
                                        // Standard 2x2 layout (3 large + small icons)
                                        when (p) {
                                            0 -> 0
                                            1 -> 1
                                            2 -> cols
                                            3 -> 2 // row 0, col 2
                                            else -> null
                                        }
                                    }
                                } else {
                                    // Regular 1x1, 1x3, 3x1, XXL, 3x6, etc.: sequential candidate
                                    p
                                }

                                val assignedG = if (candidate != null && candidate in 0 until gridChildCount && !matchedGridIndices.contains(candidate)) {
                                    candidate
                                } else {
                                    // Find next free grid child index that is strictly unmapped
                                    (0 until gridChildCount).firstOrNull { !matchedGridIndices.contains(it) }
                                }

                                if (assignedG != null) {
                                    locMap[p] = assignedG
                                    matchedGridIndices.add(assignedG)
                                }
                                // If no unused grid index is available (e.g. preview slots exceed items in folder),
                                // we DO NOT map slot p! This guarantees 100% uniqueness in locMap.
                            }

                            val maxKey = locMap.keys.maxOrNull() ?: (previewCount - 1)
                            return maxKey.coerceAtLeast(0)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in modular 1:1 initIconLoc", t)
                    }
                    return chain.proceed()
                }
            })
            Log.i(TAG, "Hooked FolderAnimController.initIconLoc for universal 1:1 multi-grid tracking")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook FolderAnimController.initIconLoc", t)
        }

        // 2. Hook FolderAnimController.updateGridItemAnimationPosition: exact 1:1 starting transform
        try {
            val folderAnimControllerClass = Class.forName("com.miui.home.folder.FolderAnimController", false, classLoader)
            val updatePosMethod = folderAnimControllerClass.getDeclaredMethod(
                "updateGridItemAnimationPosition",
                View::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }

            module.hook(updatePosMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.folderTracking) return chain.proceed()

                    try {
                        val controller = chain.thisObject ?: return chain.proceed()
                        val view = chain.args[0] as? View ?: return chain.proceed()
                        val previewIndex = (chain.args[1] as? Number)?.toInt() ?: 0

                        val desktopViewsField = runCatching {
                            controller.javaClass.getDeclaredField("mDesktopImageViews").apply { isAccessible = true }
                        }.getOrNull()
                        val desktopViews = desktopViewsField?.get(controller) as? Array<*>

                        val animViewField = controller.javaClass.getDeclaredField("mFolderIconAnimView").apply { isAccessible = true }
                        val folderAnimIcon = animViewField.get(controller) ?: return chain.proceed()
                        val getPreviewArrayMethod = folderAnimIcon.javaClass.getMethod("getPreviewArray")
                        val previewArray = getPreviewArrayMethod.invoke(folderAnimIcon) as? Array<*> ?: return chain.proceed()

                        val pv = (desktopViews?.getOrNull(previewIndex) as? View) ?: (previewArray.getOrNull(previewIndex) as? View)
                        if (pv != null) {
                            val pvLoc = IntArray(2)
                            pv.getLocationOnScreen(pvLoc)

                            val gridEndLocField = controller.javaClass.getDeclaredField("mGridEndLoc").apply { isAccessible = true }
                            val gridEndLoc = gridEndLocField.get(controller) as IntArray

                            var pvW = pv.width.toFloat().takeIf { it > 0f } ?: pv.measuredWidth.toFloat()
                            var pvH = pv.height.toFloat().takeIf { it > 0f } ?: pv.measuredHeight.toFloat()
                            if (pvW <= 0f) pvW = 100f
                            if (pvH <= 0f) pvH = 100f

                            val desktopIcon = (view as? Any)
                            val iconW = (runCatching {
                                (desktopIcon?.javaClass?.getMethod("getIconWidth")?.invoke(desktopIcon) as? Number)?.toFloat()
                            }.getOrNull())?.takeIf { it > 0f } ?: view.width.toFloat().takeIf { it > 0f } ?: pvW

                            val iconH = (runCatching {
                                (desktopIcon?.javaClass?.getMethod("getIconHeight")?.invoke(desktopIcon) as? Number)?.toFloat()
                            }.getOrNull())?.takeIf { it > 0f } ?: view.height.toFloat().takeIf { it > 0f } ?: pvH

                            val editScaleField = runCatching {
                                controller.javaClass.getDeclaredField("mNormalEditScale").apply { isAccessible = true }
                            }.getOrNull()
                            val editScale = (editScaleField?.get(controller) as? Number)?.toFloat() ?: 1.0f

                            val folmeScaleField = runCatching {
                                controller.javaClass.getDeclaredField("mFolmeScale").apply { isAccessible = true }
                            }.getOrNull()
                            val folmeScale = (folmeScaleField?.get(controller) as? Number)?.toFloat() ?: 1.0f

                            val transX = (pvLoc[0] - gridEndLoc[0]).toFloat() / (editScale * folmeScale)
                            val transY = (pvLoc[1] - gridEndLoc[1]).toFloat() / (editScale * folmeScale)
                            val scaleX = if (iconW > 0f && pvW > 0f) (pvW / iconW).coerceIn(0.12f, 1.20f) else 1.0f
                            val scaleY = if (iconH > 0f && pvH > 0f) (pvH / iconH).coerceIn(0.12f, 1.20f) else 1.0f

                            // Set initial 1:1 affine pre-animation state
                            val preAnimMethod = controller.javaClass.getDeclaredMethod(
                                "preAnimState",
                                View::class.java,
                                Float::class.javaPrimitiveType,
                                Float::class.javaPrimitiveType,
                                Float::class.javaPrimitiveType,
                                Float::class.javaPrimitiveType
                            ).apply { isAccessible = true }
                            preAnimMethod.invoke(controller, view, scaleX, scaleY, transX, transY)

                            // Hide the preview icon cleanly so only the 1:1 grid icon is rendered, eliminating double icons
                            (previewArray.getOrNull(previewIndex) as? View)?.alpha = 0f
                            pv.alpha = 0f
                            return null
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in 1:1 updateGridItemAnimationPosition", t)
                    }
                    return chain.proceed()
                }
            })
            Log.i(TAG, "Hooked FolderAnimController.updateGridItemAnimationPosition for 1:1 preview-to-grid flight")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook FolderAnimController.updateGridItemAnimationPosition", t)
        }

        // 3. Hook FolderAnimController.setVisibleScale: exact 1:1 collapse scaling into preview
        try {
            val folderAnimControllerClass = Class.forName("com.miui.home.folder.FolderAnimController", false, classLoader)
            val animStateClass = Class.forName("miuix.animation.controller.AnimState", false, classLoader)
            val viewPropertyClass = Class.forName("miuix.animation.property.ViewProperty", false, classLoader)
            val scaleXProp = viewPropertyClass.getField("SCALE_X").get(null)
            val scaleYProp = viewPropertyClass.getField("SCALE_Y").get(null)
            val addMethod = animStateClass.getMethod("add", Object::class.java, Double::class.javaPrimitiveType)

            val setVisibleScaleMethod = folderAnimControllerClass.getDeclaredMethod(
                "setVisibleScale",
                animStateClass,
                Int::class.javaPrimitiveType
            ).apply { isAccessible = true }

            module.hook(setVisibleScaleMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.folderTracking) return chain.proceed()

                    try {
                        val controller = chain.thisObject ?: return chain.proceed()
                        val animState = chain.args[0] ?: return chain.proceed()
                        val gridIndex = (chain.args[1] as? Number)?.toInt() ?: 0

                        val desktopViewsField = runCatching {
                            controller.javaClass.getDeclaredField("mDesktopImageViews").apply { isAccessible = true }
                        }.getOrNull()
                        val desktopViews = desktopViewsField?.get(controller) as? Array<*>

                        val animViewField = runCatching {
                            controller.javaClass.getDeclaredField("mFolderIconAnimView").apply { isAccessible = true }
                        }.getOrNull()
                        val folderAnimIcon = animViewField?.get(controller)
                        val previewArray = (runCatching {
                            folderAnimIcon?.javaClass?.getMethod("getPreviewArray")?.invoke(folderAnimIcon) as? Array<*>
                        }.getOrNull()) ?: desktopViews ?: return chain.proceed()

                        val locMapField = controller.javaClass.getDeclaredField("mFolderIconLocMap").apply { isAccessible = true }
                        val locMap = locMapField.get(controller) as? Map<Int, Int> ?: return chain.proceed()

                        var pvIndex = -1
                        for ((k, v) in locMap) {
                            if (v == gridIndex) {
                                pvIndex = k
                                break
                            }
                        }

                        val pvCount = desktopViews?.size ?: previewArray.size
                        if (pvIndex in 0 until pvCount) {
                            // Primary view is from mDesktopImageViews on desktop for exact 1:1 resting size match!
                            val pv = (desktopViews?.getOrNull(pvIndex) as? View) ?: (previewArray.getOrNull(pvIndex) as? View)
                            var pvW = pv?.width?.toFloat()?.takeIf { it > 0f } ?: pv?.measuredWidth?.toFloat() ?: 0f
                            var pvH = pv?.height?.toFloat()?.takeIf { it > 0f } ?: pv?.measuredHeight?.toFloat() ?: 0f

                            if (pvW <= 0f || pvH <= 0f) {
                                val parentContainer = pv?.parent as? ViewGroup
                                pvW = parentContainer?.width?.toFloat()?.takeIf { it > 0f } ?: 100f
                                pvH = pvW
                            }

                            val gridViewField = controller.javaClass.getDeclaredField("mAnimaFolderGridView").apply { isAccessible = true }
                            val folderGridView = gridViewField.get(controller) as? ViewGroup
                            val child = folderGridView?.getChildAt(gridIndex)

                            // Retrieve true icon dimensions inside DesktopIcon (excluding padding and text label)
                            val desktopIcon = (child as? Any) ?: runCatching {
                                folderGridView?.javaClass?.getMethod("getChildView", Int::class.javaPrimitiveType)?.invoke(folderGridView, gridIndex)
                            }.getOrNull()
                            val iconW = (runCatching {
                                (desktopIcon?.javaClass?.getMethod("getIconWidth")?.invoke(desktopIcon) as? Number)?.toFloat()
                            }.getOrNull())?.takeIf { it > 0f } ?: child?.width?.toFloat()?.takeIf { it > 0f } ?: pvW

                            val iconH = (runCatching {
                                (desktopIcon?.javaClass?.getMethod("getIconHeight")?.invoke(desktopIcon) as? Number)?.toFloat()
                            }.getOrNull())?.takeIf { it > 0f } ?: child?.height?.toFloat()?.takeIf { it > 0f } ?: pvH

                            val quickEditScaleField = runCatching {
                                controller.javaClass.getDeclaredField("mQuickEditScale").apply { isAccessible = true }
                            }.getOrNull()
                            val quickEditScale = (quickEditScaleField?.get(controller) as? Number)?.toFloat() ?: 1.0f

                            // Affine scale: preview dimension divided by desktop icon dimensions * quickEditScale
                            val scaleX = if (iconW > 0f && pvW > 0f) ((pvW / iconW) * quickEditScale).coerceIn(0.12f, 1.20f) else 0.35f
                            val scaleY = if (iconH > 0f && pvH > 0f) ((pvH / iconH) * quickEditScale).coerceIn(0.12f, 1.20f) else 0.35f

                            addMethod.invoke(animState, scaleXProp, scaleX.toDouble())
                            addMethod.invoke(animState, scaleYProp, scaleY.toDouble())
                            return null
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error in 1:1 setVisibleScale", t)
                    }
                    return chain.proceed()
                }
            })
            Log.i(TAG, "Hooked FolderAnimController.setVisibleScale for 1:1 collapse scaling")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook FolderAnimController.setVisibleScale", t)
        }

        // 4. Hook FolderAnimController.startFolderAnim: OriginOS physical curves & synchronous timing
        try {
            val folderAnimControllerClass = Class.forName("com.miui.home.folder.FolderAnimController", false, classLoader)
            val iFolderIconClass = Class.forName("com.miui.home.folder.api.IFolderIcon", false, classLoader)
            val startFolderAnimMethod = folderAnimControllerClass.getDeclaredMethod(
                "startFolderAnim",
                Boolean::class.javaPrimitiveType,
                iFolderIconClass,
                IntArray::class.java,
                Boolean::class.javaPrimitiveType
            ).apply { isAccessible = true }

            module.hook(startFolderAnimMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (!cfg.folderTracking) return chain.proceed()

                    val isOpen = chain.args[0] as? Boolean ?: true
                    val iFolderIcon = chain.args[1]
                    val controller = chain.thisObject

                    val result = chain.proceed()

                    try {
                        if (controller != null) {
                            // OriginOS Physical Springs:
                            // Open: Damping 0.88f, Response 0.32s (matches OriginOS V0 = PathInterpolator(0.33, 0.0, 0.67, 1.0))
                            // Close: Damping 0.90f, Response 0.28s
                            val damping = if (isOpen) 0.88f else 0.90f
                            val response = if (isOpen) 0.32f else 0.28f

                            fun applyOriginOSSpring(animListField: String) {
                                runCatching {
                                    val field = controller.javaClass.getDeclaredField(animListField).apply { isAccessible = true }
                                    val list = field.get(controller) as? List<*> ?: return
                                    for (anim in list) {
                                        if (anim == null) continue
                                        val getAnimConfigMethod = anim.javaClass.getMethod("getAnimConfig")
                                        val animConfig = getAnimConfigMethod.invoke(anim) ?: continue
                                        val setEaseMethod = animConfig.javaClass.getMethod("setEase", Int::class.javaPrimitiveType, FloatArray::class.java)
                                        setEaseMethod.invoke(animConfig, -2, floatArrayOf(damping, response))
                                        val setDelayMethod = animConfig.javaClass.getMethod("setDelay", Long::class.javaPrimitiveType)
                                        setDelayMethod.invoke(animConfig, 0L)
                                    }
                                }
                            }

                            applyOriginOSSpring("mGridIconSpringAnimList")
                            applyOriginOSSpring("mFolderIconSpringAnimList")
                            applyOriginOSSpring("mBgSpringAnimList")
                            applyOriginOSSpring("mTitleSpringAnimList")

                            // When closing: restore preview view alphas on desktop icon after flight finishes
                            if (!isOpen && iFolderIcon != null) {
                                val desktopIcon = (iFolderIcon as? View) ?: runCatching {
                                    val m = iFolderIcon.javaClass.getMethod("getIconImageView")
                                    m.invoke(iFolderIcon) as? View
                                }.getOrNull()

                                desktopIcon?.postDelayed({
                                    runCatching {
                                        val getPreviewArrayMethod = iFolderIcon.javaClass.getMethod("getPreviewArray")
                                        val pvs = getPreviewArrayMethod.invoke(iFolderIcon) as? Array<*>
                                        pvs?.forEach { (it as? View)?.alpha = 1.0f }
                                    }
                                }, 320L)
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error applying OriginOS curves to folder animation", t)
                    }

                    return result
                }
            })
            Log.i(TAG, "Hooked FolderAnimController.startFolderAnim with OriginOS spring dynamics")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook FolderAnimController.startFolderAnim", t)
        }

        // 5. Hook FolderAnimControllerPre for synchronous OriginOS timing
        try {
            val preClass = Class.forName("com.miui.home.folder.FolderAnimControllerPre", false, classLoader)
            val getDelayMethod = preClass.getDeclaredMethod("getGridItemDelay", Long::class.javaPrimitiveType)
            module.hook(getDelayMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (cfg.folderTracking) return 0L
                    return chain.proceed()
                }
            })
            val getGridRespMethod = preClass.getDeclaredMethod("getGridViewResponse", Int::class.javaPrimitiveType)
            module.hook(getGridRespMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (cfg.folderTracking) return 0.32f
                    return chain.proceed()
                }
            })
            val getPrevRespMethod = preClass.getDeclaredMethod("getPreViewOpenResponse", Int::class.javaPrimitiveType)
            module.hook(getPrevRespMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (cfg.folderTracking) return 0.32f
                    return chain.proceed()
                }
            })
            val getClosePrevRespMethod = preClass.getDeclaredMethod("getClosePreviewResponse")
            module.hook(getClosePrevRespMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any {
                    val cfg = LauncherConfigHolder.iosAnimConfig
                    if (cfg.folderTracking) return 0.28f
                    return chain.proceed()
                }
            })
            Log.i(TAG, "Hooked FolderAnimControllerPre for synchronous OriginOS response times")
        } catch (t: Throwable) {
            Log.w(TAG, "FolderAnimControllerPre hook bypassed or not present", t)
        }
    }
}
