package dev.joseph.hyperbounce.launcher

import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * iOS-Style App Open & Close Animation Engine for HyperOS Launcher (com.miui.home)
 *
 * Implements pure iOS physical dynamics and weightless trajectory equations:
 *
 * 1. Zero Drag Resistance & Continuous Elevation (No Downward Dip):
 *    - Replaces HyperOS's heavy friction curves with fluid, direct-tracking physics.
 *    - Center-anchored displacement equation: as soon as the user touches and pushes up,
 *      the app moves strictly UPWARDS from frame 0. Zero downward shift when triggering Recents!
 *    - The app scales down continuously and travels higher up freely along with the finger
 *      without hitting any artificial clamp ceilings.
 *
 * 2. Recents Current Task Centering:
 *    - Overrides TaskStackViewsAlgorithmHorizontal, TaskStackViewsAlgorithmStack, and TaskViewsElement.
 *    - Eliminates the intentional HyperOS offset that shifted the current app to the right.
 *    - Keeps the active running app perfectly centered on screen when opening Recents, matching iOS
 *      behavior, while preserving Quick Switch app cycling.
 *
 * 3. Velocity-Dependent Flight & Thumb-Coupled Minimizing:
 *    - Captures live gesture flick velocity and thumb release position.
 *    - Couples thumb release height and flick speed directly into the launch trajectory.
 *    - High-speed flick launches the window effortlessly into a soaring parabolic arc before
 *      landing smoothly into its icon squircle.
 *
 * 4. SurfaceFlinger Hardware Momentum Injection:
 *    - Injects velocity directly into SfAnimParam.centerY on hardware-accelerated transitions.
 *
 * 5. Standard X-Axis Linear Motor Keyboard Tick for Back Gestures:
 *    - Replaces the heavy thud in BackGesture with the crisp, standard keyboard tick
 *      (MIUI_KEYBOARD / EFFECT_CLICK) from the linear motor.
 *
 * 6. Multi-Scenario Resistance Reduction:
 *    - Fluid back gesture indicator tracking in BackGestureUtils.
 *    - Feather-light swipe dismissal in VerticalSwipe.
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

    private var sKeyField: Field? = null
    private var sImpField: Field? = null
    private var sDField: Field? = null
    private var sRField: Field? = null

    fun init(module: XposedInterface, classLoader: ClassLoader?) {
        if (classLoader == null) return

        hookGestureVelocityTracking(module, classLoader)
        hookDragResistanceAndScaling(module, classLoader)
        hookRecentsCurrentTaskCentering(module, classLoader)
        hookBackGestureHaptics(module, classLoader)
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
     * Hook 2: Eliminates drag resistance, removes the downward dip on Recents trigger,
     * and enables the app to travel higher up easily with progressive scaling.
     */
    private fun hookDragResistanceAndScaling(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val algoClass = Class.forName("com.miui.home.recents.views.TaskStackLayoutAlgorithm", false, classLoader)

            // 1. Hook calculateTaskWidth(float f, float f2)
            // f = base width (screen width)
            // f2 = drag progress mPer (0.0 to 1.0+)
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
                    val progress = (chain.args[1] as? Number)?.toFloat()?.coerceAtLeast(0f) ?: 0f

                    // Smooth, continuous scaling down as the app climbs higher:
                    // Progressively shrinks down to 0.30x without abrupt cliffs
                    val scale = (1.0f - (progress * 0.48f)).coerceIn(0.28f, 1.0f)
                    return baseWidth * scale
                }
            })

            // 2. Hook calculateTaskY(int i, float f, float f2, float f3)
            // i = gesture position (1 = bottom up)
            // f = screen height
            // f2 = current task height (already scaled by calculateTaskWidth!)
            // f3 = drag progress mPer
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

                    // If upward gesture from bottom (i == 1):
                    if (i == 1 && screenH > 0f) {
                        // Center-anchored elevation:
                        // At progress = 0: yCenter = 0.50 * screenH, taskH = screenH => yTop = 0.0f
                        // As progress > 0: yTop strictly decreases (moves UPWARDS).
                        // The app moves UP from the very first frame of the gesture:
                        // ZERO downward shift when triggering Recents!
                        // And as the user pulls higher, it travels freely upward with ZERO clamp resistance!
                        val yCenter = screenH * (0.50f - (progress * 0.65f))
                        val yTop = yCenter - (taskH * 0.50f)
                        return yTop
                    }

                    return chain.proceed()
                }
            })

            Log.i(TAG, "Hooked TaskStackLayoutAlgorithm for fluid upward elevation & progressive scaling")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook TaskStackLayoutAlgorithm drag calculations", t)
        }

        // 3. Hook afterFrictionValue on algorithm subclasses to eliminate artificial rubber-band wall
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
                        return f * 0.94f
                    }
                })
            } catch (_: Throwable) {}
        }
    }

    /**
     * Hook 3: Centers the current running task in Recents Overview instead of pushing it to the right.
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
                // z = isQuickSwitchMode
                // i = quickSwitchTaskIndex
                // i2 = runningTaskIndex (0)
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
                            // In quick switch mode, maintain sequential app cycling!
                            return chain.proceed()
                        }

                        // Entering Recents from an app: center the current task (index 0)
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
                            return 0 // Center current task during transition
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
     * Hook 4: Replaces the heavy back gesture thud with the standard X-axis linear motor keyboard tick.
     */
    private fun hookBackGestureHaptics(module: XposedInterface, classLoader: ClassLoader) {
        val hapticClasses = listOf(
            "com.miui.home.common.hapticfeedback.HapticFeedbackCompatLinear",
            "com.miui.home.common.hapticfeedback.HapticFeedbackCompatV2",
            "com.miui.home.common.hapticfeedback.HapticFeedbackCompatNormal"
        )

        for (className in hapticClasses) {
            try {
                val clazz = Class.forName(className, false, classLoader)

                // 1. Hook performGestureReadyBack()
                val readyBackMethod = clazz.getDeclaredMethod("performGestureReadyBack")
                module.hook(readyBackMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (!cfg.enabled) {
                            return chain.proceed()
                        }
                        triggerKeyboardTickHaptic(chain.thisObject)
                        return null // Do NOT proceed with the heavy thud
                    }
                })

                // 2. Hook performGestureBackHandUp()
                val handUpMethod = clazz.getDeclaredMethod("performGestureBackHandUp")
                module.hook(handUpMethod).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val cfg = LauncherConfigHolder.iosAnimConfig
                        if (!cfg.enabled) {
                            return chain.proceed()
                        }
                        triggerKeyboardTickHaptic(chain.thisObject)
                        return null // Do NOT proceed with the heavy thud
                    }
                })

                Log.i(TAG, "Hooked $className to replace back gesture thud with standard linear keyboard tick")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to hook back gesture haptics on $className", t)
            }
        }
    }

    /**
     * Triggers the crisp, standard X-axis linear motor keyboard tick (not light tick).
     */
    private fun triggerKeyboardTickHaptic(instance: Any) {
        try {
            // First attempt: MIUI HapticHelper with MIUI_KEYBOARD constant (268435479)
            val helperField = instance.javaClass.getDeclaredField("mHapticHelper").apply { isAccessible = true }
            val helper = helperField.get(instance)
            if (helper != null) {
                val performMethod = helper.javaClass.getMethod(
                    "performHapticFeedback",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val success = performMethod.invoke(helper, 268435479, 268435457) as? Boolean ?: false
                if (success) return
            }
        } catch (_: Throwable) {}

        // Fallback: Android system Vibrator with standard linear motor EFFECT_CLICK
        try {
            val activityThreadClass = Class.forName("android.app.ActivityThread")
            val currentApp = activityThreadClass.getMethod("currentApplication").invoke(null) as? android.content.Context
            val vibrator = currentApp?.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? Vibrator
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(15L)
                }
            }
        } catch (_: Throwable) {}
    }

    /**
     * Hook 5: Eliminates artificial resistance in other core navigation interactions:
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
                    return f * 0.94f
                }
            })
            Log.i(TAG, "Hooked VerticalSwipe.afterFrictionValue for feather-light recents dismissal")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook VerticalSwipe", t)
        }
    }

    /**
     * Hook 6: Row-Dependent Parabolic Trajectory Physics in SpringParamsProvider.
     * Computes spring damping & response dynamically based on target icon row position and flick velocity.
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
                    val targetRect = chain.args[2] as? RectF

                    try {
                        val deviceHeight = runCatching {
                            val dcClass = Class.forName("com.miui.home.common.device.DeviceConfigs", false, classLoader)
                            (dcClass.getMethod("getDeviceHeight").invoke(null) as? Number)?.toFloat() ?: 2400f
                        }.getOrDefault(2400f).coerceAtLeast(1000f)

                        val targetCenterY = targetRect?.centerY() ?: (deviceHeight * 0.75f)
                        val normY = (targetCenterY / deviceHeight).coerceIn(0f, 1f)

                        // Check live flick velocity
                        val now = SystemClock.uptimeMillis()
                        val isRecentFlick = (now - sLastVelocityTime) < 800L
                        val vy = sLastVelocityY // px/ms (< 0 on upward flick)
                        val isFastFlick = isRecentFlick && vy < -1.0f

                        val openDamping: Float
                        val openResponse: Float
                        val closeDampingY: Float
                        val closeResponseY: Float
                        val closeDampingX: Float
                        val closeResponseX: Float
                        val closeDampingSize: Float
                        val closeResponseSize: Float
                        val recentsDamping: Float
                        val recentsResponse: Float
                        val cornerDamping: Float
                        val cornerResponse: Float
                        val alphaDamping: Float
                        val alphaResponse: Float

                        // --- Scenario 1: Closing to Home (Row-Dependent Parabolic Dynamics) ---
                        if (normY > 0.58f) {
                            val depthRatio = ((normY - 0.58f) / 0.42f).coerceIn(0f, 1f)
                            // Underdamped cushion for weightless floating apogee:
                            val baseDamping = 0.88f - (depthRatio * 0.04f)
                            val baseResponse = 0.54f + (depthRatio * 0.10f)

                            // On fast flick: enhance floating flight
                            closeDampingY = if (isFastFlick) (baseDamping - 0.03f).coerceAtLeast(0.81f) else baseDamping
                            closeResponseY = if (isFastFlick) (baseResponse + 0.06f).coerceAtMost(0.68f) else baseResponse
                        } else if (normY < 0.38f) {
                            closeDampingY = if (isFastFlick) 0.94f else 0.98f
                            closeResponseY = if (isFastFlick) 0.48f else 0.44f
                        } else {
                            val midRatio = (normY - 0.38f) / 0.20f
                            val baseD = 0.98f - (midRatio * 0.10f)
                            val baseR = 0.44f + (midRatio * 0.10f)
                            closeDampingY = if (isFastFlick) (baseD - 0.03f) else baseD
                            closeResponseY = if (isFastFlick) (baseR + 0.04f) else baseR
                        }

                        // Horizontal tracking: Fluid, natural iOS curve
                        closeDampingX = 1.00f
                        closeResponseX = 0.46f

                        // Window geometry (width & height): stays light and broad during ascent,
                        // then collapses gracefully into icon squircle
                        closeDampingSize = 0.92f
                        closeResponseSize = 0.50f

                        // Corner squircle morphing
                        cornerDamping = 1.05f
                        cornerResponse = 0.30f

                        // Alpha fade: stays solid until landing
                        alphaDamping = 0.88f
                        alphaResponse = 0.38f

                        // --- Scenario 2: Expansive App Open ---
                        openDamping = 1.00f
                        openResponse = 0.410f

                        // --- Scenario 3: Recents ---
                        recentsDamping = 0.94f
                        recentsResponse = 0.420f

                        // Apply parameters per SpringParam key
                        for (i in result.indices) {
                            val sp = result[i] ?: continue
                            val key = sKeyField?.get(sp) as? String ?: continue

                            var newDamping: Float? = null
                            var newResponse: Float? = null

                            when {
                                animType.startsWith("OPEN_FROM_HOME") || animType.startsWith("OPEN_FROM_ELEMENT") -> {
                                    when (key) {
                                        "centerX", "centerY" -> { newDamping = openDamping; newResponse = openResponse }
                                        "Width", "Height", "Ratio" -> { newDamping = openDamping; newResponse = openResponse }
                                        "Radius", "RadiusRatio" -> { newDamping = 1.10f; newResponse = 0.320f }
                                        "alpha" -> { newDamping = 0.95f; newResponse = 0.280f }
                                    }
                                }
                                animType.startsWith("CLOSE_TO_HOME") || animType.startsWith("CLOSE_TO_ELEMENT") -> {
                                    when (key) {
                                        "centerY" -> { newDamping = closeDampingY; newResponse = closeResponseY }
                                        "centerX" -> { newDamping = closeDampingX; newResponse = closeResponseX }
                                        "Width", "Height", "Ratio" -> { newDamping = closeDampingSize; newResponse = closeResponseSize }
                                        "Radius", "RadiusRatio" -> { newDamping = cornerDamping; newResponse = cornerResponse }
                                        "alpha" -> { newDamping = alphaDamping; newResponse = alphaResponse }
                                    }
                                }
                                animType.startsWith("CLOSE_TO_RECENTS") -> {
                                    when (key) {
                                        "centerX", "centerY" -> { newDamping = recentsDamping; newResponse = recentsResponse }
                                        "Width", "Height", "Ratio" -> { newDamping = recentsDamping; newResponse = recentsResponse }
                                        "Radius", "RadiusRatio" -> { newDamping = cornerDamping; newResponse = cornerResponse }
                                        "alpha" -> { newDamping = 0.90f; newResponse = 0.350f }
                                    }
                                }
                                animType.startsWith("OPEN_FROM_RECENTS") -> {
                                    when (key) {
                                        "centerX", "centerY" -> { newDamping = 1.00f; newResponse = 0.380f }
                                        "Width", "Height", "Ratio" -> { newDamping = 1.00f; newResponse = 0.380f }
                                        "Radius", "RadiusRatio" -> { newDamping = cornerDamping; newResponse = cornerResponse }
                                        "alpha" -> { newDamping = 0.95f; newResponse = 0.300f }
                                    }
                                }
                                animType.startsWith("APP_TO_APP") || animType.startsWith("START_FIRST_TASK") -> {
                                    when (key) {
                                        "centerX", "centerY" -> { newDamping = 0.96f; newResponse = 0.400f }
                                        "Width", "Height", "Ratio" -> { newDamping = 0.96f; newResponse = 0.400f }
                                        "Radius", "RadiusRatio" -> { newDamping = cornerDamping; newResponse = cornerResponse }
                                        "alpha" -> { newDamping = 0.90f; newResponse = 0.320f }
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
            Log.i(TAG, "Hooked SpringParamsProvider.generateSpringBundlesByAnimType with velocity-coupled dynamics")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook SpringParamsProvider", t)
        }
    }

    /**
     * Hook 7: Injects live upward gesture momentum directly into SurfaceFlinger hardware animation parameters.
     * Directly couples flick velocity and thumb release position into the flight launch curve.
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

                            val windowAnimParams = rectFParams.javaClass.getMethod("getWindowAnimParams").invoke(rectFParams)
                            val targetRect = windowAnimParams?.javaClass?.getMethod("getTargetRect")?.invoke(windowAnimParams) as? RectF

                            val deviceHeight = runCatching {
                                val dcClass = Class.forName("com.miui.home.common.device.DeviceConfigs", false, classLoader)
                                (dcClass.getMethod("getDeviceHeight").invoke(null) as? Number)?.toFloat() ?: 2400f
                            }.getOrDefault(2400f).coerceAtLeast(1000f)

                            val targetCenterY = targetRect?.centerY() ?: (deviceHeight * 0.75f)
                            val normY = (targetCenterY / deviceHeight).coerceIn(0f, 1f)

                            // Thumb position when flicked
                            val thumbNormY = if (sLastThumbY > 0f) {
                                (sLastThumbY / deviceHeight).coerceIn(0.20f, 1.0f)
                            } else {
                                0.75f
                            }

                            // Thumb release multiplier: higher thumb position provides sustained flight momentum
                            val thumbMultiplier = 1.25f - (thumbNormY * 0.35f)

                            // Base speed calculation directly coupled to flick velocity
                            val speedFactor = if (isRecentFlick && vy < -0.15f) {
                                (kotlin.math.abs(vy) * 1000f).coerceIn(1200f, 14000f)
                            } else {
                                1400f
                            }

                            // Calculate upward velocity for parabolic minimizing flight
                            val upwardVelocity = -speedFactor * thumbMultiplier * (0.60f + (normY * 0.75f))

                            val setCenterYVelMethod = sfAnimParam.javaClass.getMethod("setCenterYVelocity", Float::class.javaPrimitiveType)
                            setCenterYVelMethod.invoke(sfAnimParam, upwardVelocity)

                            // Preserve horizontal flick curve if user swiped diagonally
                            if (isRecentFlick && kotlin.math.abs(sLastVelocityX) > 0.15f) {
                                val vxPxSec = (sLastVelocityX * 850f).coerceIn(-6000f, 6000f)
                                val setCenterXVelMethod = sfAnimParam.javaClass.getMethod("setCenterXVelocity", Float::class.javaPrimitiveType)
                                setCenterXVelMethod.invoke(sfAnimParam, vxPxSec)
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error injecting upward velocity into SfAnimParam", t)
                    }

                    return sfAnimParam
                }
            })
            Log.i(TAG, "Hooked SfWindowAnimImplementor.buildSfAnimParam for velocity & thumb coupled trajectories")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook SfWindowAnimImplementor.buildSfAnimParam", t)
        }
    }

    /**
     * Hook 8: Desktop Grid HomeLayer scaling.
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
                    // Desktop scales to 0.92x on open (clean, subtle iOS depth staging)
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
     * Hook 9: Wallpaper depth zoom staging.
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
                    // 1.08x subtle iOS depth zoom
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
     * Hook 10: Forces high animation capabilities and bypasses low-tier device throttling in DeviceLevelUtils.
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
}
