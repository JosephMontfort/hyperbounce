package dev.joseph.hyperbounce.compat

import android.content.Context
import android.util.Log
import dev.joseph.hyperbounce.safemode.SystemPropertiesHelper
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

/**
 * Handles compatibility for Xiaomi HyperOS "Advanced Textures" (background_blur_enable).
 *
 * On devices where SurfaceFlinger hardware window blur is not active (persist.sys.background_blur_mode == 0,
 * typical on Snapdragon 7s Gen 2 / garnet and similar mid-range tiers), enabling HyperOS "Advanced Textures"
 * causes Xiaomi UI frameworks (HyperMaterialUtils, MiuiBlurUiHelper, ControlCenterUtils) to discard the rich
 * fallback blur drawables ("Xiaomi intense blur") and request window blur from SurfaceFlinger.
 *
 * Because SurfaceFlinger renders zero window blur, UI elements like launcher folders, control center
 * backgrounds, and notification cards become 100% transparent.
 *
 * This hook detects when hardware window blur is unavailable and forces the system to preserve its rich,
 * intense fallback blur drawables across the Launcher, Control Center, and SystemUI.
 */
object AdvancedTextureFallbackHook {
    private const val TAG = "HyperBounce_BlurFallback"

    /**
     * Checks if the device's hardware window blur pipeline is disabled or broken in SurfaceFlinger.
     */
    fun isHardwareBlurBroken(): Boolean {
        val mode = SystemPropertiesHelper.get("persist.sys.background_blur_mode", "0")
        return mode == "0"
    }

    /**
     * In com.miui.home / com.mi.android.globallauncher:
     * Hooks miuix.core.util.HyperMaterialUtils to prevent folder backgrounds from losing their rich blur.
     */
    fun initLauncher(module: XposedModule, classLoader: ClassLoader) {
        try {
            val hmuClass = Class.forName("miuix.core.util.HyperMaterialUtils", false, classLoader)
            for (m in hmuClass.declaredMethods) {
                if ((m.name == "isFeatureEnable" || m.name == "isFeatureEnableNoCache") &&
                    m.parameterTypes.size == 1 && Context::class.java.isAssignableFrom(m.parameterTypes[0])
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (isHardwareBlurBroken()) {
                                return false
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked launcher HyperMaterialUtils.${m.name} successfully")
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Launcher HyperMaterialUtils hook note: ${t.message}")
        }
    }

    /**
     * In miui.systemui.plugin:
     * Hooks ControlCenterUtils and MiBlurCompat to prevent Control Center background from losing its rich blur.
     */
    fun initPlugin(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook ControlCenterUtils.getBackgroundBlurOpenedInDefaultTheme(Context)
        try {
            val ccuClass = Class.forName("miui.systemui.controlcenter.utils.ControlCenterUtils", false, classLoader)
            for (m in ccuClass.declaredMethods) {
                if (m.name == "getBackgroundBlurOpenedInDefaultTheme" &&
                    m.parameterTypes.size == 1 && Context::class.java.isAssignableFrom(m.parameterTypes[0])
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (isHardwareBlurBroken()) {
                                return false
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked ControlCenterUtils.getBackgroundBlurOpenedInDefaultTheme successfully")
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "ControlCenterUtils hook note: ${t.message}")
        }

        // 2. Hook MiBlurCompat.getBackgroundBlurOpenedInDefaultTheme(Context)
        try {
            val mbcClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader)
            for (m in mbcClass.declaredMethods) {
                if (m.name == "getBackgroundBlurOpenedInDefaultTheme" &&
                    m.parameterTypes.size == 1 && Context::class.java.isAssignableFrom(m.parameterTypes[0])
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (isHardwareBlurBroken()) {
                                return false
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked plugin MiBlurCompat.getBackgroundBlurOpenedInDefaultTheme successfully")
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Plugin MiBlurCompat hook note: ${t.message}")
        }

        // 3. Hook HyperMaterialUtils.isFeatureEnable(Context) in plugin
        try {
            val hmuClass = Class.forName("miuix.core.util.HyperMaterialUtils", false, classLoader)
            for (m in hmuClass.declaredMethods) {
                if ((m.name == "isFeatureEnable" || m.name == "isFeatureEnableNoCache") &&
                    m.parameterTypes.size == 1 && Context::class.java.isAssignableFrom(m.parameterTypes[0])
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (isHardwareBlurBroken()) {
                                return false
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked plugin HyperMaterialUtils.${m.name} successfully")
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "Plugin HyperMaterialUtils hook note: ${t.message}")
        }
    }

    /**
     * In com.android.systemui:
     * Hooks MiBlurCompat and HyperMaterialUtils in SystemUI.
     */
    fun initSystemUI(module: XposedModule, classLoader: ClassLoader) {
        try {
            val mbcClass = Class.forName("miui.systemui.util.MiBlurCompat", false, classLoader)
            for (m in mbcClass.declaredMethods) {
                if (m.name == "getBackgroundBlurOpenedInDefaultTheme" &&
                    m.parameterTypes.size == 1 && Context::class.java.isAssignableFrom(m.parameterTypes[0])
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (isHardwareBlurBroken()) {
                                return false
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked SystemUI MiBlurCompat.getBackgroundBlurOpenedInDefaultTheme successfully")
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "SystemUI MiBlurCompat hook note: ${t.message}")
        }

        try {
            val hmuClass = Class.forName("miuix.core.util.HyperMaterialUtils", false, classLoader)
            for (m in hmuClass.declaredMethods) {
                if ((m.name == "isFeatureEnable" || m.name == "isFeatureEnableNoCache") &&
                    m.parameterTypes.size == 1 && Context::class.java.isAssignableFrom(m.parameterTypes[0])
                ) {
                    module.hook(m).intercept(object : XposedInterface.Hooker {
                        override fun intercept(chain: XposedInterface.Chain): Any? {
                            if (isHardwareBlurBroken()) {
                                return false
                            }
                            return chain.proceed()
                        }
                    })
                    Log.d(TAG, "Hooked SystemUI HyperMaterialUtils.${m.name} successfully")
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "SystemUI HyperMaterialUtils hook note: ${t.message}")
        }
    }
}
