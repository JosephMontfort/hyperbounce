package dev.joseph.hyperbounce.launcher

import android.graphics.RectF
import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * OriginOS 6 App Transition & Physics Engine for HyperOS Launcher (com.miui.home)
 *
 * Implements 1:1 analytical physical models and harmonic spring curves extracted from
 * OriginOS Launcher (BBKLauncher2.apk):
 *
 * 1. Harmonized Multi-Scenario Spring Matrix:
 *    - Profile 0: Gentle / 柔和 (OriginOS 6 Signature Style 3)
 *        Open: k=200, zeta=1.00, T0=0.444s (Luxurious floating expansion)
 *        Close: k=150, zeta=0.88, T0=0.513s (Underdamped organic cushion into icon squircle)
 *        Alpha: k=550, zeta=0.60, T0=0.268s (Signature smooth fade)
 *        Recents: k=224, zeta=0.95, T0=0.419s (Decelerating glide)
 *        Parallel / Interrupt: k=224, zeta=0.95, height clip k=987 (multiple.close)
 *    - Profile 1: Comfort / 舒适 (Style 4 - Balanced Dynamics)
 *        Open: k=300, zeta=1.00, T0=0.363s
 *        Close: k=200, zeta=0.92, T0=0.444s
 *    - Profile 2: Swift / 轻快 (Flagship High-Speed)
 *        Open: k=450, zeta=1.00, T0=0.296s
 *        Close: k=250, zeta=0.96, T0=0.397s
 *    - Profile 3: Custom Tuner (Granular user sliders)
 *
 * 2. Spatial & Depth Staging:
 *    - Wallpaper depth zoom (1.15x) matching OriginOS depth staging
 *    - Desktop HomeLayer scaling (1.00 -> 0.90 on open, 0.90 -> 1.00 on close)
 *
 * 3. Flagship Capabilities:
 *    - Unlocks high animation rate and bypasses low-tier throttling in DeviceLevelUtils
 */
object AppTransitionHook {
    private const val TAG = "HyperBounce_OriginOS"

    private var sKeyField: Field? = null
    private var sImpField: Field? = null
    private var sDField: Field? = null
    private var sRField: Field? = null

    fun init(module: XposedInterface, classLoader: ClassLoader?) {
        if (classLoader == null) return

        hookSpringParamsProvider(module, classLoader)
        hookWallpaperDynamics(module, classLoader)
        hookHomeLayerScaling(module, classLoader)
        hookDeviceLevelUtils(module, classLoader)
    }

    /**
     * Injects OriginOS 6 exact physical harmonic oscillator parameters per animation scenario.
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
                    val cfg = LauncherConfigHolder.originOSConfig
                    if (!cfg.enabled) {
                        return result
                    }

                    val animType = (chain.args[0] as? Enum<*>)?.name ?: ""

                    try {
                        // Resolve Damping (zeta) and Response (T0 = 2*pi / sqrt(k)) per scenario
                        val profile = cfg.curveProfile
                        val openDamping: Float
                        val openResponse: Float
                        val closeDamping: Float
                        val closeResponse: Float
                        val recentsDamping: Float
                        val recentsResponse: Float
                        val parallelDamping: Float
                        val parallelResponse: Float
                        val cornerDamping: Float
                        val cornerResponse: Float
                        val alphaDamping: Float
                        val alphaResponse: Float

                        when (profile) {
                            1 -> {
                                // Profile 1: Comfort / 舒适 (OriginOS Style 4 - Balanced Dynamics)
                                openDamping = 1.00f; openResponse = 0.363f      // k = 300.0f
                                closeDamping = 0.92f; closeResponse = 0.444f    // k = 200.0f
                                recentsDamping = 0.95f; recentsResponse = 0.389f // k = 260.0f
                                parallelDamping = 0.95f; parallelResponse = 0.419f
                                cornerDamping = 1.10f; cornerResponse = 0.268f  // k = 550.0f
                                alphaDamping = 0.70f; alphaResponse = 0.256f    // k = 600.0f
                            }
                            2 -> {
                                // Profile 2: Swift / 轻快 (OriginOS Flagship Fast)
                                openDamping = 1.00f; openResponse = 0.296f      // k = 450.0f
                                closeDamping = 0.96f; closeResponse = 0.397f    // k = 250.0f
                                recentsDamping = 1.00f; recentsResponse = 0.363f // k = 300.0f
                                parallelDamping = 0.95f; parallelResponse = 0.397f
                                cornerDamping = 1.15f; cornerResponse = 0.256f  // k = 600.0f
                                alphaDamping = 0.60f; alphaResponse = 0.246f    // k = 650.0f
                            }
                            3 -> {
                                // Profile 3: Custom Tuner (User configured sliders)
                                openDamping = cfg.customOpenDamping
                                openResponse = cfg.customOpenResponse
                                closeDamping = cfg.customCloseDamping
                                closeResponse = cfg.customCloseResponse
                                recentsDamping = cfg.customRecentsDamping
                                recentsResponse = cfg.customRecentsResponse
                                parallelDamping = cfg.customParallelDamping
                                parallelResponse = cfg.customParallelResponse
                                cornerDamping = cfg.customCornerDamping
                                cornerResponse = cfg.customCornerResponse
                                alphaDamping = cfg.customAlphaDamping
                                alphaResponse = cfg.customAlphaResponse
                            }
                            else -> {
                                // Profile 0: Gentle / 柔和 (OriginOS 6 Signature Style 3 - Luxurious Float)
                                openDamping = 1.00f; openResponse = 0.444f      // k = 200.0f (Expansive float)
                                closeDamping = 0.88f; closeResponse = 0.513f    // k = 150.0f (Underdamped organic cushion)
                                recentsDamping = 0.95f; recentsResponse = 0.419f // k = 224.0f (persist.launcher.to.recent)
                                parallelDamping = 0.95f; parallelResponse = 0.419f // k = 224.0f (multiple.close)
                                cornerDamping = 1.15f; cornerResponse = 0.256f  // k = 600.0f (Overdamped squircle)
                                alphaDamping = 0.60f; alphaResponse = 0.268f    // k = 550.0f (Soft delayed fade)
                            }
                        }

                        for (i in result.indices) {
                            val param = result[i] ?: continue
                            val key = sKeyField?.get(param) as? String ?: continue
                            val impulse = (sImpField?.get(param) as? Float) ?: 0.0f

                            val d: Float
                            val r: Float

                            when {
                                // 1. APP OPEN (from home icon or floating element)
                                animType.contains("OPEN_FROM_HOME") || animType.contains("OPEN_FROM_ELEMENT") -> {
                                    when (key) {
                                        "centerX", "centerY", "Width" -> { d = openDamping; r = openResponse }
                                        "Height", "Ratio" -> { d = openDamping; r = openResponse }
                                        "RadiusRatio", "Radius" -> { d = cornerDamping; r = cornerResponse }
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { d = 0.90f; r = (openResponse * 0.90f).coerceAtLeast(0.20f) }
                                        else -> { d = openDamping; r = openResponse }
                                    }
                                }

                                // 2. APP CLOSE (to home icon or squircle bounds)
                                animType.contains("CLOSE_TO_HOME") || animType.contains("CLOSE_TO_ELEMENT") || animType == "CLOSE_FROM_FEED" -> {
                                    when (key) {
                                        "centerX", "centerY", "Width" -> { d = closeDamping; r = closeResponse }
                                        "Height", "Ratio" -> { d = closeDamping; r = closeResponse }
                                        "RadiusRatio", "Radius" -> { d = cornerDamping; r = cornerResponse }
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { d = alphaDamping; r = alphaResponse }
                                        else -> { d = closeDamping; r = closeResponse }
                                    }
                                }

                                // 3. OPEN APP TO RECENTS (Swiping / transitioning from open app to recents overview)
                                animType.contains("CLOSE_TO_RECENTS") -> {
                                    when (key) {
                                        "centerX", "centerY" -> { d = recentsDamping; r = recentsResponse }
                                        "Width", "Height", "Ratio" -> { d = 1.00f; r = (recentsResponse * 1.07f).coerceAtLeast(0.20f) }
                                        "RadiusRatio", "Radius" -> { d = cornerDamping; r = cornerResponse }
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { d = 1.00f; r = (recentsResponse * 0.86f).coerceAtLeast(0.20f) }
                                        else -> { d = recentsDamping; r = recentsResponse }
                                    }
                                }

                                // 4. RECENTS TO APP (Launching task card from recents overview)
                                animType.contains("OPEN_FROM_RECENTS") -> {
                                    when (key) {
                                        "centerX", "centerY", "Width", "Height" -> { d = 1.00f; r = 0.375f } // k = 280.0f
                                        "RadiusRatio", "Radius" -> { d = cornerDamping; r = cornerResponse }
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { d = 0.95f; r = 0.281f }
                                        else -> { d = 1.00f; r = 0.375f }
                                    }
                                }

                                // 5. PARALLEL APP SWITCH / GESTURE INTERRUPT (APP_TO_APP / START_FIRST_TASK)
                                animType == "APP_TO_APP" || animType == "START_FIRST_TASK" -> {
                                    if (cfg.parallelAnimEnabled) {
                                        when (key) {
                                            "centerX", "centerY" -> { d = parallelDamping; r = parallelResponse } // k = 224.0f, zeta = 0.95
                                            "Width" -> { d = 1.00f; r = (parallelResponse * 1.07f).coerceAtLeast(0.20f) }
                                            "Height", "Ratio" -> { d = 1.00f; r = 0.200f } // k = 987.0f snappy clear
                                            "RadiusRatio", "Radius" -> { d = cornerDamping; r = cornerResponse }
                                            "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { d = 1.00f; r = 0.362f }
                                            else -> { d = parallelDamping; r = parallelResponse }
                                        }
                                    } else {
                                        d = (sDField?.get(param) as? Float) ?: 1.00f
                                        r = (sRField?.get(param) as? Float) ?: 0.35f
                                    }
                                }

                                else -> {
                                    d = (sDField?.get(param) as? Float) ?: 1.00f
                                    r = (sRField?.get(param) as? Float) ?: 0.35f
                                }
                            }

                            result[i] = springParamConst.newInstance(key, d, r, impulse)
                        }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Error calculating OriginOS spring matrix", t)
                    }

                    return result
                }
            })

            Log.i(TAG, "OriginOS spring parameter engine installed successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook SpringParamsProvider", t)
        }
    }

    /**
     * Staged OriginOS Wallpaper Zooming (1.15x zoom on app open & recents overview).
     */
    private fun hookWallpaperDynamics(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val wallpaperParamClass = Class.forName("com.miui.home.recents.anim.WallpaperParam", false, classLoader)
            val wallpaperCompanionClass = Class.forName("com.miui.home.recents.anim.WallpaperParam\$Companion", false, classLoader)

            val wallpaperConstructor = wallpaperParamClass.getDeclaredConstructor(
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            ).apply { isAccessible = true }

            val getAppStateMethod = wallpaperCompanionClass.getDeclaredMethod("getAppStateParams")
            module.hook(getAppStateMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.originOSConfig
                    if (cfg.enabled) {
                        return wallpaperConstructor.newInstance(cfg.wallpaperScaleZoom, 1.05f, 0.60f)
                    }
                    return chain.proceed()
                }
            })

            val getRecentStateMethod = wallpaperCompanionClass.getDeclaredMethod("getRecentStateParams")
            module.hook(getRecentStateMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.originOSConfig
                    if (cfg.enabled) {
                        return wallpaperConstructor.newInstance(cfg.wallpaperScaleZoom, 1.10f, 0.589f)
                    }
                    return chain.proceed()
                }
            })

            Log.i(TAG, "Hooked WallpaperParam for OriginOS depth zoom")
        } catch (t: Throwable) {
            Log.w(TAG, "WallpaperParam hook skipped / not found", t)
        }
    }

    /**
     * OriginOS Desktop Layer Scaling:
     * Desktop and icon grid scales smoothly down to 0.90x when entering an app,
     * and scales back to 1.00x when returning home.
     */
    private fun hookHomeLayerScaling(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val homeLayerParamsClass = Class.forName("com.miui.home.recents.anim.HomeLayerParams", false, classLoader)
            val homeLayerCompanionClass = Class.forName("com.miui.home.recents.anim.HomeLayerParams\$Companion", false, classLoader)

            val constructor = homeLayerParamsClass.getDeclaredConstructor(
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType,
                Float::class.javaPrimitiveType
            ).apply { isAccessible = true }

            val getAppState = homeLayerCompanionClass.getDeclaredMethod("getAppStateParams")
            module.hook(getAppState).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.originOSConfig
                    if (cfg.enabled) {
                        return constructor.newInstance(1.0f, 1.0f, 0.0f, cfg.dragLayerEndScale)
                    }
                    return chain.proceed()
                }
            })

            val getHomeState = homeLayerCompanionClass.getDeclaredMethod("getHomeStateParams")
            module.hook(getHomeState).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.originOSConfig
                    if (cfg.enabled) {
                        return constructor.newInstance(0.0f, cfg.dragLayerEndScale, 1.0f, 1.0f)
                    }
                    return chain.proceed()
                }
            })

            // Ensure HomeLayerElement is enabled so mHomeLeash binds
            try {
                val homeLayerElementClass = Class.forName("com.miui.home.recents.anim.HomeLayerElement", false, classLoader)
                val enableField = homeLayerElementClass.getDeclaredField("enable").apply { isAccessible = true }
                val defaultConstructor = homeLayerElementClass.getDeclaredConstructor().apply { isAccessible = true }
                module.hook(defaultConstructor).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        try {
                            if (LauncherConfigHolder.originOSConfig.enabled) {
                                enableField.setBoolean(chain.thisObject, true)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed to enable HomeLayerElement", t)
                        }
                        return result
                    }
                })
            } catch (t: Throwable) {
                Log.w(TAG, "HomeLayerElement constructor hook skipped", t)
            }

            Log.i(TAG, "Hooked HomeLayerParams for OriginOS desktop scaling")
        } catch (t: Throwable) {
            Log.w(TAG, "HomeLayerParams hook skipped / not found", t)
        }
    }

    /**
     * Bypasses low-tier device throttling in DeviceLevelUtils.
     */
    private fun hookDeviceLevelUtils(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val deviceLevelClass = Class.forName("com.miui.home.common.utils.DeviceLevelUtils", false, classLoader)

            val bypassFalseMethods = listOf(
                "isLowLevelDevice",
                "isLowLevelOrLiteDevice",
                "isLowLevelOrLiteDeviceOrSpecialDevice"
            )

            for (name in bypassFalseMethods) {
                try {
                    val m = deviceLevelClass.getDeclaredMethod(name)
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (LauncherConfigHolder.originOSConfig.unlockHighEndCapabilities) {
                                return false
                            }
                            return chain.proceed()
                        }
                    })
                } catch (_: NoSuchMethodException) {}
            }

            val bypassTrueMethods = listOf(
                "isUseWallpaperAnim",
                "isHighLevelDeviceFromFolme",
                "isHighAnimationRate"
            )

            for (name in bypassTrueMethods) {
                try {
                    val m = deviceLevelClass.getDeclaredMethod(name)
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (LauncherConfigHolder.originOSConfig.unlockHighEndCapabilities) {
                                return true
                            }
                            return chain.proceed()
                        }
                    })
                } catch (_: NoSuchMethodException) {}
            }

            Log.i(TAG, "Hooked DeviceLevelUtils high-tier capabilities")
        } catch (t: Throwable) {
            Log.w(TAG, "DeviceLevelUtils hook skipped", t)
        }
    }
}
