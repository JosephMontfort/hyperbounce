package dev.joseph.hyperbounce.launcher

import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * OriginOS 6 App Transition & Physics Engine for HyperOS Launcher (com.miui.home)
 *
 * Implements exact physical models, harmonic spring curves, and parallel orchestration
 * extracted from OriginOS Launcher (BBKLauncher2.apk):
 *
 * 1. Harmonized Multi-Scenario Spring Matrix:
 *    - Open from Desktop / Elements (Swift k=450, Gentle k=200, Comfort k=300)
 *    - Close to Home / Squircle Collapse (k=180-250, signature underdamped alpha k=550, zeta=0.60)
 *    - Open App to Recents (k=224, zeta=0.95 decelerating glide)
 *    - Recents to App (k=400, zeta=1.0 critically damped expansion)
 *    - Parallel App Switching (k=224, zeta=0.95 with ultra-snappy k=987 height-clip)
 *
 * 2. Parallel Multi-App Opening Engine:
 *    - Bypasses ConnectAnimManager.isRecentOpenAnimRunning() click drop gate
 *    - Bypasses SuperDraglayer & Application touch suppression during active flight
 *    - Instantly dispatches queued launches without waiting for ShellTransitionCallback
 *
 * 3. Flagship Capability & FastLaunch Unlock:
 *    - Forces FastLaunchUtils.isSupportFastLaunch -> true across all device tiers
 *    - Bypasses DeviceLevelUtils low-tier/lite device throttling
 *
 * 4. OriginOS Delayed Alpha & Height-Clip Mechanics:
 *    - 120ms alpha hold on close for solid window geometry before fadeout
 *    - Wallpaper scale zoom (1.15x) matching OriginOS depth staging
 */
object AppTransitionHook {
    private const val TAG = "HyperBounce_OriginOS"

    private var sKeyField: Field? = null
    private var sImpField: Field? = null
    private var sDField: Field? = null
    private var sRField: Field? = null

    @Volatile
    private var sCloseStartTime: Long = 0L
    @Volatile
    private var sIsClosingNow: Boolean = false

    private val sMainHandler by lazy { Handler(Looper.getMainLooper()) }

    fun init(module: XposedInterface, classLoader: ClassLoader?) {
        if (classLoader == null) return

        hookSpringParamsProvider(module, classLoader)
        hookFastLaunchAndDeviceLevel(module, classLoader)
        hookParallelLaunchBypasses(module, classLoader)
        hookWallpaperDynamics(module, classLoader)
        hookDelayedAlpha(module, classLoader)
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

                    // Track close animation timestamps for OriginOS delayed alpha fade
                    if (animType.contains("CLOSE_TO_HOME") || animType.contains("CLOSE_TO_ELEMENT") || animType == "CLOSE_FROM_FEED") {
                        sIsClosingNow = true
                        sCloseStartTime = SystemClock.uptimeMillis()
                    } else {
                        sIsClosingNow = false
                    }

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
                                // Profile 1: Gentle / 柔和 (OriginOS Style 3 - Luxurious Glide)
                                openDamping = 1.00f; openResponse = 0.444f      // k = 200.0f
                                closeDamping = 1.00f; closeResponse = 0.468f    // k = 180.0f
                                recentsDamping = 1.00f; recentsResponse = 0.468f
                                parallelDamping = 0.95f; parallelResponse = 0.419f
                                cornerDamping = 1.15f; cornerResponse = 0.256f  // k = 600.0f
                                alphaDamping = 0.60f; alphaResponse = 0.268f    // k = 550.0f
                            }
                            2 -> {
                                // Profile 2: Comfort / 舒适 (OriginOS Style 4 - Balanced Dynamics)
                                openDamping = 1.00f; openResponse = 0.363f      // k = 300.0f
                                closeDamping = 1.00f; closeResponse = 0.444f    // k = 200.0f
                                recentsDamping = 1.00f; recentsResponse = 0.397f
                                parallelDamping = 0.95f; parallelResponse = 0.419f
                                cornerDamping = 1.10f; cornerResponse = 0.268f  // k = 550.0f
                                alphaDamping = 0.75f; alphaResponse = 0.256f    // k = 600.0f
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
                                // Profile 0: Swift / 轻快 (OriginOS Flagship Default)
                                openDamping = 1.00f; openResponse = 0.296f      // k = 450.0f
                                closeDamping = 1.00f; closeResponse = 0.397f    // k = 250.0f
                                recentsDamping = 0.95f; recentsResponse = 0.419f // k = 224.0f
                                parallelDamping = 0.95f; parallelResponse = 0.419f // k = 224.0f
                                cornerDamping = 1.15f; cornerResponse = 0.256f  // k = 600.0f
                                alphaDamping = 0.60f; alphaResponse = 0.268f    // k = 550.0f
                            }
                        }

                        for (i in result.indices) {
                            val param = result[i] ?: continue
                            val key = sKeyField?.get(param) as? String ?: continue
                            val impulse = (sImpField?.get(param) as? Float) ?: 0.0f

                            var d: Float
                            var r: Float

                            when {
                                // 1. APP OPEN (from home icon or floating element)
                                animType.contains("OPEN_FROM_HOME") || animType.contains("OPEN_FROM_ELEMENT") -> {
                                    when (key) {
                                        "centerX", "centerY", "Width" -> { d = openDamping; r = openResponse }
                                        "Height", "Ratio" -> { d = openDamping; r = (openResponse * 0.865f).coerceAtLeast(0.18f) }
                                        "RadiusRatio", "Radius" -> { d = 1.00f; r = (openResponse * 1.06f).coerceAtLeast(0.20f) }
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { d = 0.90f; r = (openResponse * 0.95f).coerceAtLeast(0.20f) }
                                        else -> { d = openDamping; r = openResponse }
                                    }
                                }

                                // 2. APP CLOSE (to home icon or squircle bounds)
                                animType.contains("CLOSE_TO_HOME") || animType.contains("CLOSE_TO_ELEMENT") || animType == "CLOSE_FROM_FEED" -> {
                                    when (key) {
                                        "centerX", "centerY" -> { d = closeDamping; r = closeResponse }
                                        "Width" -> { d = closeDamping; r = (closeResponse * 1.06f).coerceAtLeast(0.20f) }
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
                                        "Width" -> { d = 1.00f; r = (recentsResponse * 1.07f).coerceAtLeast(0.20f) }
                                        "Height", "Ratio" -> { d = 1.00f; r = (recentsResponse * 0.87f).coerceAtLeast(0.20f) }
                                        "RadiusRatio", "Radius" -> { d = 0.95f; r = (recentsResponse * 0.96f).coerceAtLeast(0.20f) }
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { d = 1.00f; r = (recentsResponse * 0.86f).coerceAtLeast(0.20f) }
                                        else -> { d = recentsDamping; r = recentsResponse }
                                    }
                                }

                                // 4. RECENTS TO APP (Launching task card from recents overview)
                                animType.contains("OPEN_FROM_RECENTS") -> {
                                    when (key) {
                                        "centerX", "centerY", "Width", "Height" -> { d = 1.00f; r = 0.314f } // k = 400.0f
                                        "RadiusRatio", "Radius" -> { d = 1.00f; r = 0.336f }               // k = 350.0f
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { d = 0.95f; r = 0.281f } // k = 500.0f
                                        else -> { d = 1.00f; r = 0.314f }
                                    }
                                }

                                // 5. PARALLEL MULTI-APP TRANSITION (APP_TO_APP / START_FIRST_TASK)
                                animType == "APP_TO_APP" || animType == "START_FIRST_TASK" -> {
                                    // OriginOS multiple.close profile (gdr/gdm.java)
                                    when (key) {
                                        "centerX", "centerY" -> { d = parallelDamping; r = parallelResponse } // k = 224.0f, zeta = 0.95
                                        "Width" -> { d = 1.00f; r = (parallelResponse * 1.07f).coerceAtLeast(0.20f) } // k = 195.0f
                                        "Height", "Ratio" -> { d = 1.00f; r = 0.200f } // k = 987.0f (snappy clip so old window clears!)
                                        "RadiusRatio", "Radius" -> { d = 0.95f; r = 0.402f } // k = 244.0f
                                        "alpha", "SurfaceExtAlpha", "appLeashBlurRadius" -> { d = 1.00f; r = 0.362f } // k = 300.0f
                                        else -> { d = parallelDamping; r = parallelResponse }
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
     * Unlocks Flagship FastLaunchWindowElement and prevents low-tier device animation throttling.
     */
    private fun hookFastLaunchAndDeviceLevel(module: XposedInterface, classLoader: ClassLoader) {
        // 1. FastLaunchUtils.isSupportFastLaunch -> true
        try {
            val fastLaunchClass = Class.forName("com.miui.home.recents.anim.FastLaunchUtils\$Companion", false, classLoader)
            val intentClass = Class.forName("android.content.Intent", false, classLoader)
            val viewClass = Class.forName("android.view.View", false, classLoader)

            val isSupportMethod = fastLaunchClass.getDeclaredMethod("isSupportFastLaunch", intentClass, viewClass)
            module.hook(isSupportMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (LauncherConfigHolder.originOSConfig.unlockHighEndCapabilities) {
                        return true
                    }
                    return chain.proceed()
                }
            })
            Log.i(TAG, "Hooked FastLaunchUtils.isSupportFastLaunch -> true")
        } catch (t: Throwable) {
            Log.w(TAG, "FastLaunchUtils hook skipped / not found", t)
        }

        // 2. DeviceLevelUtils: Force high-tier flagship capabilities
        try {
            val deviceLevelClass = Class.forName("com.miui.home.common.utils.DeviceLevelUtils", false, classLoader)

            val bypassFalseMethods = listOf(
                "isLowLevelDevice",
                "isLowLevelOrLiteDevice",
                "isLowLevelOrLiteDeviceOrSpecialDevice",
                "isUseSimpleAnim",
                "isUseSimpleOpenCloseAnim",
                "isUseSimpleOpenOrCloseAnim"
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

            Log.i(TAG, "Hooked DeviceLevelUtils flagship capabilities")
        } catch (t: Throwable) {
            Log.w(TAG, "DeviceLevelUtils hook skipped / not found", t)
        }
    }

    /**
     * Unlocks OriginOS Parallel Multi-App Opening by removing touch suppression,
     * click dropping, and execution stalling between consecutive launches.
     */
    private fun hookParallelLaunchBypasses(module: XposedInterface, classLoader: ClassLoader) {
        // 1. Bypass ConnectAnimManager.isRecentOpenAnimRunning() click drop in BaseLauncher.launch
        try {
            val connectAnimClass = Class.forName("com.miui.home.recents.anim.ConnectAnimManager", false, classLoader)
            val isRecentOpenMethod = connectAnimClass.getDeclaredMethod("isRecentOpenAnimRunning")

            module.hook(isRecentOpenMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (LauncherConfigHolder.originOSConfig.parallelAnimEnabled) {
                        return false // Never drop the subsequent icon click!
                    }
                    return chain.proceed()
                }
            })
            Log.i(TAG, "Hooked ConnectAnimManager.isRecentOpenAnimRunning -> false for parallel taps")
        } catch (t: Throwable) {
            Log.w(TAG, "ConnectAnimManager.isRecentOpenAnimRunning hook skipped", t)
        }

        // 2. Bypass SuperDraglayer touch suppression during in-flight transitions
        try {
            val appClass = Class.forName("com.miui.home.launcher.Application", false, classLoader)
            val isClickWaitForCallbackMethod = appClass.getDeclaredMethod("isClickAppWaitForCallback")

            module.hook(isClickWaitForCallbackMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (LauncherConfigHolder.originOSConfig.parallelAnimEnabled) {
                        return false // Allow DragLayer to dispatch consecutive clicks to desktop icons
                    }
                    return chain.proceed()
                }
            })
            Log.i(TAG, "Hooked Application.isClickAppWaitForCallback -> false")
        } catch (t: Throwable) {
            Log.w(TAG, "Application.isClickAppWaitForCallback hook skipped", t)
        }

        // 3. Bypass StateManager.isReusefulOpeningAnimRunning()
        try {
            val stateManagerCompanionClass = Class.forName("com.miui.home.recents.anim.StateManager\$Companion", false, classLoader)
            val isReusefulMethod = stateManagerCompanionClass.getDeclaredMethod("isReusefulOpeningAnimRunning")

            module.hook(isReusefulMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    if (LauncherConfigHolder.originOSConfig.parallelAnimEnabled) {
                        return false
                    }
                    return chain.proceed()
                }
            })
            Log.i(TAG, "Hooked StateManager.isReusefulOpeningAnimRunning -> false")
        } catch (t: Throwable) {
            Log.w(TAG, "StateManager.isReusefulOpeningAnimRunning hook skipped", t)
        }

        // 4. StateManager.setLaunchIconRunnable: Immediately dispatch queued launch in parallel
        try {
            val stateManagerClass = Class.forName("com.miui.home.recents.anim.StateManager", false, classLoader)
            val setRunnableMethod = stateManagerClass.getDeclaredMethod("setLaunchIconRunnable", Runnable::class.java)

            module.hook(setRunnableMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val runnable = chain.args[0] as? Runnable
                    val result = chain.proceed()
                    if (runnable != null && LauncherConfigHolder.originOSConfig.parallelAnimEnabled) {
                        // Immediately execute without waiting for ShellTransitionCallback.onFinish
                        sMainHandler.post {
                            try {
                                runnable.run()
                            } catch (t: Throwable) {
                                Log.w(TAG, "Parallel launch execution callback", t)
                            }
                        }
                    }
                    return result
                }
            })
            Log.i(TAG, "Hooked StateManager.setLaunchIconRunnable for instant parallel dispatch")
        } catch (t: Throwable) {
            Log.w(TAG, "StateManager.setLaunchIconRunnable hook skipped", t)
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
                        // OriginOS 1.15x app state zoom with k=100.0f (response=0.628s)
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
                        // OriginOS persist.launcher.to.recent.wp.endscale = 1.15f
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
     * Enforces OriginOS 120ms Alpha Delay on app close:
     * App window content stays 100% solid during the initial 120ms collapse,
     * preventing premature ghosting before it reaches the icon boundary.
     */
    private fun hookDelayedAlpha(module: XposedInterface, classLoader: ClassLoader) {
        try {
            val springBundleClass = Class.forName("com.miui.home.recents.anim.SpringBundle", false, classLoader)
            val mKeyField = springBundleClass.getDeclaredField("mKey").apply { isAccessible = true }
            val mValueField = springBundleClass.getDeclaredField("mValue").apply { isAccessible = true }

            val updateMethod = springBundleClass.getDeclaredMethod(
                "updateValueAndVelocity",
                Long::class.javaPrimitiveType,
                Boolean::class.javaPrimitiveType
            )

            module.hook(updateMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val cfg = LauncherConfigHolder.originOSConfig
                    if (cfg.enabled && cfg.delayedAlphaClose && sIsClosingNow) {
                        val key = mKeyField.get(chain.thisObject) as? String
                        if (key == "alpha" || key == "SurfaceExtAlpha") {
                            val elapsed = SystemClock.uptimeMillis() - sCloseStartTime
                            if (elapsed < 120L) {
                                mValueField.setFloat(chain.thisObject, 1.0f)
                                return false // Continue animation while holding full opacity
                            }
                        }
                    }
                    return chain.proceed()
                }
            })
            Log.i(TAG, "Hooked SpringBundle for OriginOS 120ms delayed alpha fade")
        } catch (t: Throwable) {
            Log.w(TAG, "SpringBundle delayed alpha hook skipped", t)
        }
    }
}
