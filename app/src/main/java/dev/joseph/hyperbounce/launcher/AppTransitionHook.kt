package dev.joseph.hyperbounce.launcher

import android.graphics.RectF
import android.os.SystemClock
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
 * 1. Zero Drag Resistance & Progressive Scale-Down:
 *    - Replaces HyperOS's heavy rubber-band friction curve (laterFriction / afterFrictionValue)
 *      in TaskStackLayoutAlgorithm with a fluid, 1:1 direct tracking displacement.
 *    - Progressively scales the app down as it is pulled up, keeping it completely on screen
 *      rather than allowing it to shoot off-screen into the void.
 *    - Even when pulled high towards the top edge or top-right corner, the app scales into a
 *      compact floating card with zero drag resistance.
 *
 * 2. Recents Current Task Centering:
 *    - Overrides TaskStackViewsAlgorithmHorizontal, TaskStackViewsAlgorithmStack, and TaskViewsElement.
 *    - Eliminates the intentional HyperOS offset that shifted the current app to the right to focus
 *      the previous app.
 *    - Keeps the active running app perfectly centered on screen when opening Recents, matching iOS
 *      behavior, while preserving Quick Switch app cycling.
 *
 * 3. Velocity-Dependent Flight & Row-Adaptive Trajectories:
 *    - Captures live gesture flick velocity (px/ms) via GestureHomeCalculator.updateVelocity.
 *    - For apps in the lower half of the screen and dock (Row 4..6 + Dock):
 *      When flicked upward, the window carries upward momentum into a graceful, soaring parabolic
 *      arc before smoothly decelerating and descending into its icon squircle.
 *    - Height of flight is directly velocity-dependent: faster flick = higher apogee flight.
 *    - For top-row apps (Row 0..2): smoothly guides the window directly to the top icon with
 *      crisp critical damping, preventing excessive overshoot beyond the screen top edge.
 *
 * 4. SurfaceFlinger Hardware Upward Momentum Injection:
 *    - Injects calculated upward flight velocity into SfAnimParam.centerY on hardware-accelerated transitions.
 *
 * 5. Multi-Scenario Resistance Reduction:
 *    - Fluid back gesture indicator tracking in BackGestureUtils.
 *    - Feather-light swipe dismissal in VerticalSwipe.
 *
 * 6. Feather-Light Expansive App Opening:
 *    - Organic, critically damped (zeta=1.00, T0=0.410s) expansive flight from squircle to full screen.
 */
object AppTransitionHook {
    private const val TAG = "HyperBounce_iOS"

    // Live Gesture Velocity Tracking
    @Volatile
    private var sLastVelocityX: Float = 0f
    @Volatile
    private var sLastVelocityY: Float = 0f
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
        hookExtraInteractionsFriction(module, classLoader)
        hookSpringParamsProvider(module, classLoader)
        hookSfAnimImplementor(module, classLoader)
        hookHomeLayerScaling(module, classLoader)
        hookWallpaperDynamics(module, classLoader)
        hookDeviceLevelUtils(module, classLoader)
    }

    /**
     * Hook 1: Tracks live gesture flick velocity from GestureHomeCalculator.
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

            module.hook(updateVelocityMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    try {
                        val vx = (chain.args[0] as? Number)?.toFloat() ?: 0f
                        val vy = (chain.args[1] as? Number)?.toFloat() ?: 0f
                        sLastVelocityX = vx
                        sLastVelocityY = vy
                        sLastVelocityTime = SystemClock.uptimeMillis()
                    } catch (_: Throwable) {}
                    return result
                }
            })
            Log.i(TAG, "Hooked GestureHomeCalculator.updateVelocity for live flick momentum tracking")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook GestureHomeCalculator.updateVelocity", t)
        }
    }

    /**
     * Hook 2: Eliminates the heavy rubber-band drag resistance AND ensures the app scales down
     * progressively so it never shoots off-screen.
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

                    // As the app is lifted higher, scale down smoothly so it stays on screen:
                    val scale: Float = when {
                        progress <= 0.04f -> 1.0f
                        progress <= 0.60f -> {
                            // Initial progressive shrinkage: 1.0 -> 0.60
                            1.0f - (progress - 0.04f) * (0.40f / 0.56f)
                        }
                        progress <= 1.00f -> {
                            // Mid flight shrinkage: 0.60 -> 0.38
                            0.60f - (progress - 0.60f) * (0.22f / 0.40f)
                        }
                        else -> {
                            // Pulled high up towards top / top-right: scales down to compact card (down to 0.22)
                            0.38f / (1.0f + (progress - 1.00f) * 0.90f)
                        }
                    }
                    return baseWidth * scale.coerceIn(0.22f, 1.0f)
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
                        // Safe top margin (status bar / camera cutout clearance)
                        val topMargin = screenH * 0.035f

                        // Bottom of the card follows the finger upward with zero resistance:
                        val bottomY = screenH * (1.0f - (progress * 0.72f))
                        val desiredTop = bottomY - taskH

                        // Cushion at the top edge so the window NEVER shoots off screen into the void!
                        val finalY = if (desiredTop < topMargin) {
                            topMargin - ((topMargin - desiredTop) * 0.08f)
                        } else {
                            desiredTop
                        }
                        return finalY
                    }

                    return chain.proceed()
                }
            })

            Log.i(TAG, "Hooked TaskStackLayoutAlgorithm for zero-drag pull up and on-screen scaling")
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
                        // Return fluid linear movement instead of asymptotic flattening
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
     * Hook 4: Eliminates artificial resistance in other core navigation interactions:
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
     * Hook 5: Row-Dependent Parabolic Trajectory Physics in SpringParamsProvider.
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
                        // Calculate target vertical position normalized [0.0..1.0]
                        val deviceHeight = runCatching {
                            val dcClass = Class.forName("com.miui.home.common.device.DeviceConfigs", false, classLoader)
                            (dcClass.getMethod("getDeviceHeight").invoke(null) as? Number)?.toFloat() ?: 2400f
                        }.getOrDefault(2400f).coerceAtLeast(1000f)

                        val targetCenterY = targetRect?.centerY() ?: (deviceHeight * 0.75f)
                        val normY = (targetCenterY / deviceHeight).coerceIn(0f, 1f)

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
                        // normY < 0.38: Top rows
                        // 0.38 <= normY <= 0.58: Middle rows
                        // normY > 0.58: Lower half & dock apps
                        if (normY > 0.58f) {
                            // Lower half / Dock: Soaring upward parabolic flight
                            val depthRatio = ((normY - 0.58f) / 0.42f).coerceIn(0f, 1f)
                            // Underdamped cushion for weightless floating apogee:
                            closeDampingY = 0.88f - (depthRatio * 0.04f) // 0.88 -> 0.84
                            closeResponseY = 0.54f + (depthRatio * 0.10f) // 0.54s -> 0.64s
                        } else if (normY < 0.38f) {
                            // Top rows: Light, snappy, decelerates directly into top icon
                            closeDampingY = 0.98f
                            closeResponseY = 0.44f
                        } else {
                            // Mid rows: Smooth progressive blend
                            val midRatio = (normY - 0.38f) / 0.20f
                            closeDampingY = 0.98f - (midRatio * 0.10f)
                            closeResponseY = 0.44f + (midRatio * 0.10f)
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
                        // Feather-light, critically damped (zero bounce, organic ease-out)
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
            Log.i(TAG, "Hooked SpringParamsProvider.generateSpringBundlesByAnimType with iOS row-adaptive physics")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook SpringParamsProvider", t)
        }
    }

    /**
     * Hook 6: Injects live upward gesture momentum directly into SurfaceFlinger's hardware animation parameters.
     * Hooks SfWindowAnimImplementor.buildSfAnimParam(RectFParams) to set CenterY upward velocity on flick.
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
                            // Check gesture upward velocity
                            val now = SystemClock.uptimeMillis()
                            val isRecentFlick = (now - sLastVelocityTime) < 800L
                            val vy = sLastVelocityY // in px/ms (negative when swiping up)

                            // Retrieve target rect to evaluate row position
                            val windowAnimParams = rectFParams.javaClass.getMethod("getWindowAnimParams").invoke(rectFParams)
                            val targetRect = windowAnimParams?.javaClass?.getMethod("getTargetRect")?.invoke(windowAnimParams) as? RectF

                            val deviceHeight = runCatching {
                                val dcClass = Class.forName("com.miui.home.common.device.DeviceConfigs", false, classLoader)
                                (dcClass.getMethod("getDeviceHeight").invoke(null) as? Number)?.toFloat() ?: 2400f
                            }.getOrDefault(2400f).coerceAtLeast(1000f)

                            val targetCenterY = targetRect?.centerY() ?: (deviceHeight * 0.75f)
                            val normY = (targetCenterY / deviceHeight).coerceIn(0f, 1f)

                            // If flicked upward or for lower half / dock apps:
                            // Inject upward initial velocity so the window launches into its parabolic arc!
                            if (normY > 0.45f) {
                                val rowMomentumMultiplier = 0.50f + ((normY - 0.45f) * 1.50f) // stronger for dock apps

                                val upwardVelocity = if (isRecentFlick && vy < -0.2f) {
                                    // Scale gesture velocity (px/ms -> px/s) with row momentum
                                    (vy * 1000f * rowMomentumMultiplier).coerceIn(-12000f, -800f)
                                } else {
                                    // Even on a gentle flick / release, bottom apps get a subtle upward launch impulse
                                    (-1200f * rowMomentumMultiplier)
                                }

                                // Apply to sfAnimParam.setCenterYVelocity
                                val setCenterYVelMethod = sfAnimParam.javaClass.getMethod("setCenterYVelocity", Float::class.javaPrimitiveType)
                                setCenterYVelMethod.invoke(sfAnimParam, upwardVelocity)

                                // Also preserve horizontal flick curve if user swiped diagonally
                                if (isRecentFlick && kotlin.math.abs(sLastVelocityX) > 0.2f) {
                                    val vxPxSec = (sLastVelocityX * 800f).coerceIn(-6000f, 6000f)
                                    val setCenterXVelMethod = sfAnimParam.javaClass.getMethod("setCenterXVelocity", Float::class.javaPrimitiveType)
                                    setCenterXVelMethod.invoke(sfAnimParam, vxPxSec)
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error injecting upward velocity into SfAnimParam", t)
                    }

                    return sfAnimParam
                }
            })
            Log.i(TAG, "Hooked SfWindowAnimImplementor.buildSfAnimParam for upward flight trajectory injection")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to hook SfWindowAnimImplementor.buildSfAnimParam", t)
        }
    }

    /**
     * Hook 7: Desktop Grid HomeLayer scaling.
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
     * Hook 8: Wallpaper depth zoom staging.
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
     * Hook 9: Forces high animation capabilities and bypasses low-tier device throttling in DeviceLevelUtils.
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
