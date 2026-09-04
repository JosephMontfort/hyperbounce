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

/**
 * iOS-Style App Open & Close Animation Engine for HyperOS Launcher (com.miui.home)
 *
 * Implements pure iOS physical dynamics and weightless trajectory equations:
 *
 * 1. Zero Drag Resistance & Continuous Elevation (Zero Downward Dip):
 *    - Replaces HyperOS's heavy friction curves with fluid, direct-tracking physics.
 *    - Upward elevation from frame 0: strictly moves UPWARDS as soon as touched,
 *      eliminating any downward shift when swiping up for Recents.
 *    - Weightless upward tracking for normal minimize / flick.
 *
 * 2. Complete Floating Window / Top Corner Engulfing & Scaling:
 *    - Continuous non-linear scaling: shrinks progressively down to 0.22x - 0.25x as it reaches the top.
 *    - Smooth boundary docking: when dragged high into the top or top-right corner,
 *      the card top settles cleanly inside the upper screen boundary (below status bar) and
 *      the right edge is clamped inside the display bezel.
 *    - The entire app remains 100% visible on screen, perfectly wrapped and engulfed inside
 *      the floating window circle/squircle, exactly like stock!
 *
 * 3. Recents Current Task Centering:
 *    - Overrides TaskStackViewsAlgorithmHorizontal, TaskStackViewsAlgorithmStack, and TaskViewsElement.
 *    - Eliminates the intentional HyperOS offset that shifted the current app to the right.
 *    - Keeps the active running app perfectly centered on screen when opening Recents, matching iOS
 *      behavior, while preserving Quick Switch app cycling.
 *
 * 4. Velocity-Dependent Flight & Thumb-Coupled Minimizing:
 *    - Captures live gesture flick velocity and thumb release position.
 *    - Boosted launch impulses: app launches effortlessly into a soaring parabolic arc before
 *      landing smoothly into its icon squircle.
 *
 * 5. Refined Haptic Experience:
 *    - Back Gesture: Distinct, crisp, modern elastic pop (MIUI_BUTTON_SMALL / MIUI_MESH_LIGHT)
 *      instead of dull keyboard tick or heavy thud.
 *    - App-to-Recents Gesture: Clean, tactile latch tick (MIUI_SWITCH / MIUI_KEYBOARD) instead of
 *      the stock heavy MIUI_HOLD thud.
 *
 * 6. Feather-Light Expansive App Opening:
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
     * Hook 2: Eliminates drag resistance, ensures zero downward dip when swiping for Recents,
     * and guarantees 100% on-screen engulfing when dragged to the floating window corner.
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
                    val p = (chain.args[1] as? Number)?.toFloat()?.coerceAtLeast(0f) ?: 0f

                    // Continuous non-linear scale down:
                    // Progressively shrinks down to 0.22x - 0.25x so it fits perfectly inside
                    // the top-right floating window circle/squircle
                    val scale = when {
                        p <= 0.02f -> 1.0f
                        p <= 0.45f -> 1.0f - (p - 0.02f) * (0.35f / 0.43f) // 1.0 -> 0.65
                        p <= 0.90f -> 0.65f - (p - 0.45f) * (0.38f / 0.45f) // 0.65 -> 0.27
                        else -> (0.27f / (1.0f + (p - 0.90f) * 1.50f)).coerceAtLeast(0.22f) // down to 0.22
                    }
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

                    if (i == 1 && screenH > 0f) {
                        val p = progress

                        // For normal minimize flick & swipe-up start (p <= 0.60):
                        // Strictly moves UPWARDS from frame 0 (rawTop <= 0).
                        // Completely eliminates the initial downward dip when triggering Recents!
                        val rawTop = screenH * (-p * 0.52f)

                        // When dragged high up into the floating window zone (p > 0.60):
                        // Smoothly transition the card top so it docks inside the upper viewport
                        // (right below status bar ~0.045 * screenH) instead of shooting off-screen!
                        val finalY = if (p <= 0.60f) {
                            rawTop
                        } else {
                            val t = ((p - 0.60f) / 0.40f).coerceIn(0f, 1f)
                            val smoothT = t * t * (3f - 2f * t)
                            val anchoredTop = screenH * 0.045f
                            (rawTop * (1f - smoothT)) + (anchoredTop * smoothT)
                        }
                        return finalY
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

            Log.i(TAG, "Hooked TaskStackLayoutAlgorithm for fluid elevation, 100% on-screen engulfing & scaling")
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
     * Hook 4: Refined, satisfying linear motor haptics:
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
     * Triggers a crisp, refined tactile pop for Back Gesture (MIUI_BUTTON_SMALL = 268435476).
     */
    private fun triggerBackPopHaptic(instance: Any) {
        try {
            val helperField = instance.javaClass.getDeclaredField("mHapticHelper").apply { isAccessible = true }
            val helper = helperField.get(instance)
            if (helper != null) {
                // miuix.view.HapticFeedbackConstants.MIUI_BUTTON_SMALL = 268435476
                // miuix.view.HapticFeedbackConstants.MIUI_MESH_LIGHT = 268435463
                val performMethod = helper.javaClass.getMethod(
                    "performHapticFeedback",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                val success = performMethod.invoke(helper, 268435476, 268435463) as? Boolean ?: false
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
                    return f * 1.0f // 100% responsive card dismiss in recents
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
                        val isFastFlick = isRecentFlick && vy < -0.8f

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
                        // Even lighter, weightless flight feeling
                        if (normY > 0.58f) {
                            val depthRatio = ((normY - 0.58f) / 0.42f).coerceIn(0f, 1f)
                            val baseDamping = 0.86f - (depthRatio * 0.04f)
                            val baseResponse = 0.50f + (depthRatio * 0.10f)

                            closeDampingY = if (isFastFlick) (baseDamping - 0.03f).coerceAtLeast(0.80f) else baseDamping
                            closeResponseY = if (isFastFlick) (baseResponse + 0.06f).coerceAtMost(0.66f) else baseResponse
                        } else if (normY < 0.38f) {
                            closeDampingY = if (isFastFlick) 0.92f else 0.96f
                            closeResponseY = if (isFastFlick) 0.46f else 0.42f
                        } else {
                            val midRatio = (normY - 0.38f) / 0.20f
                            val baseD = 0.96f - (midRatio * 0.10f)
                            val baseR = 0.42f + (midRatio * 0.08f)
                            closeDampingY = if (isFastFlick) (baseD - 0.03f) else baseD
                            closeResponseY = if (isFastFlick) (baseR + 0.04f) else baseR
                        }

                        // Horizontal tracking: Fluid, natural iOS curve
                        closeDampingX = 1.00f
                        closeResponseX = 0.44f

                        // Window geometry (width & height): stays light and broad during ascent,
                        // then collapses gracefully into icon squircle
                        closeDampingSize = 0.90f
                        closeResponseSize = 0.46f

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
                        recentsResponse = 0.400f

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
            Log.i(TAG, "Hooked SpringParamsProvider.generateSpringBundlesByAnimType with weightless minimizing dynamics")
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

                            val thumbMultiplier = 1.30f - (thumbNormY * 0.35f)

                            // Boosted speed calculation for effortless weightless flight
                            val speedFactor = if (isRecentFlick && vy < -0.15f) {
                                (kotlin.math.abs(vy) * 1200f).coerceIn(1600f, 16000f)
                            } else {
                                1600f
                            }

                            // Calculate upward velocity for parabolic minimizing flight across all rows
                            val upwardVelocity = -speedFactor * thumbMultiplier * (0.65f + (normY * 0.80f))

                            val setCenterYVelMethod = sfAnimParam.javaClass.getMethod("setCenterYVelocity", Float::class.javaPrimitiveType)
                            setCenterYVelMethod.invoke(sfAnimParam, upwardVelocity)

                            // Preserve horizontal flick curve if user swiped diagonally
                            if (isRecentFlick && kotlin.math.abs(sLastVelocityX) > 0.15f) {
                                val vxPxSec = (sLastVelocityX * 900f).coerceIn(-6000f, 6000f)
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
            Log.i(TAG, "Hooked SfWindowAnimImplementor.buildSfAnimParam for weightless minimizing launch curves")
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
