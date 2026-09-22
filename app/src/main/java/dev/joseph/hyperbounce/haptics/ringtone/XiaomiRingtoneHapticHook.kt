package dev.joseph.hyperbounce.haptics.ringtone

import android.content.Context
import android.net.Uri
import android.os.VibrationEffect
import android.util.Log
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

/**
 * Modern LSPosed Hook that intercepts MIUI / HyperOS ringtone haptic resolution
 * across system_server, Settings, ThemeManager, and Telecom.
 *
 * Intercepts `android.os.VibrationEffectStubImpl.get` and `android.os.VibrationEffect.get`,
 * mapping custom ringtones to their dedicated hardware Effect IDs (e.g. 901, 902...)
 * to trigger the vendor vibrator HAL with full native LRA motor force.
 */
object XiaomiRingtoneHapticHook {

    private const val TAG = "HyperBounce_RingtoneHook"

    fun init(module: XposedModule, classLoader: ClassLoader) {
        hookVibrationEffectStubImpl(module, classLoader)
        hookVibrationEffectStatic(module, classLoader)
        hookThemeManager(module, classLoader)
    }

    private fun hookThemeManager(module: XposedModule, classLoader: ClassLoader) {
        // 1. Hook com.android.thememanager.basemodule.utils.a0.h(Uri, Context)
        try {
            val a0Class = Class.forName("com.android.thememanager.basemodule.utils.a0", false, classLoader)
            for (m: Method in a0Class.declaredMethods) {
                if (m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Uri::class.java &&
                    m.parameterTypes[1] == Context::class.java &&
                    VibrationEffect::class.java.isAssignableFrom(m.returnType)
                ) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        val uri = chain.args[0] as? Uri
                        val ctx = chain.args[1] as? Context
                        if (ctx != null && uri != null) {
                            try {
                                val path = RingtoneRegistry.resolvePathFromUri(ctx, uri) ?: uri.toString()
                                val customId = RingtoneRegistry.resolveEffectId(ctx, path)
                                if (customId != null) {
                                    Log.i(TAG, "ThemeManager a0.${m.name} intercepted [$path] -> Effect ID #$customId")
                                    return@intercept VibrationEffect.createPredefined(customId)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error resolving custom ringtone in ThemeManager a0", t)
                            }
                        }

                        val original = chain.proceed() as? VibrationEffect
                        if (original != null) return@intercept original

                        if (uri != null) {
                            val fallbackId = RingtoneRegistry.resolveEffectId(ctx, uri.toString())
                            if (fallbackId != null) {
                                Log.i(TAG, "ThemeManager a0.${m.name} fallback matched [$uri] -> Effect ID #$fallbackId")
                                return@intercept VibrationEffect.createPredefined(fallbackId)
                            }
                        }

                        null
                    }
                    Log.i(TAG, "Successfully hooked ThemeManager a0.${m.name}(Uri, Context)")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "ThemeManager a0 not present in this classloader: ${t.message}")
        }

        // 2. Direct hook into BatchMediaPlayer.l(Uri) to guarantee vibration playback
        try {
            val bmpClass = Class.forName("com.android.thememanager.basemodule.ui.widget.BatchMediaPlayer", false, classLoader)
            for (m: Method in bmpClass.declaredMethods) {
                if (m.parameterTypes.size == 1 && m.parameterTypes[0] == Uri::class.java) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        val uri = chain.args[0] as? Uri
                        if (uri != null) {
                            try {
                                val customId = RingtoneRegistry.resolveEffectId(null, uri.toString())
                                if (customId != null) {
                                    Log.i(TAG, "BatchMediaPlayer.${m.name} intercepted [$uri] -> Effect ID #$customId")
                                    // Let original run, and also ensure vibrator features fire
                                    val thisObj = chain.thisObject
                                    val vibratorField = bmpClass.declaredFields.firstOrNull {
                                        android.os.Vibrator::class.java.isAssignableFrom(it.type)
                                    }
                                    if (vibratorField != null) {
                                        vibratorField.isAccessible = true
                                        val vib = vibratorField.get(thisObj) as? android.os.Vibrator
                                        if (vib != null) {
                                            val attr = android.media.AudioAttributes.Builder()
                                                .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                                .build()
                                            vib.vibrate(VibrationEffect.createPredefined(customId), attr)
                                            Log.i(TAG, "BatchMediaPlayer directly triggered LRA effect #$customId on vibrator")
                                        }
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in BatchMediaPlayer hook", t)
                            }
                        }
                        chain.proceed()
                    }
                    Log.i(TAG, "Successfully hooked BatchMediaPlayer.${m.name}(Uri)")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "BatchMediaPlayer not present in this classloader: ${t.message}")
        }
    }

    private fun hookVibrationEffectStubImpl(module: XposedModule, classLoader: ClassLoader) {
        try {
            val stubImplClass = Class.forName("android.os.VibrationEffectStubImpl", false, classLoader)
            for (m: Method in stubImplClass.declaredMethods) {
                if (m.name == "get" && m.parameterTypes.size == 2) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        val pathObj = chain.args[0]
                        val ctxObj = chain.args[1] as? Context

                        // 1. Try resolving custom registered effect ID
                        if (ctxObj != null && pathObj != null) {
                            try {
                                val queryStr = when (pathObj) {
                                    is Uri -> RingtoneRegistry.resolvePathFromUri(ctxObj, pathObj) ?: pathObj.toString()
                                    else -> pathObj.toString()
                                }
                                val customId = RingtoneRegistry.resolveEffectId(ctxObj, queryStr)
                                if (customId != null) {
                                    Log.i(TAG, "VibrationEffectStubImpl.get intercepted custom ringtone [$queryStr] -> Effect ID #$customId")
                                    return@intercept VibrationEffect.createPredefined(customId)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error resolving custom ringtone in VibrationEffectStubImpl.get", t)
                            }
                        }

                        // 2. Otherwise proceed with stock Xiaomi resolution
                        val original = chain.proceed() as? VibrationEffect
                        if (original != null) return@intercept original

                        // 3. Fallback: if stock returned null, check filename one more time
                        if (ctxObj != null && pathObj != null) {
                            val fallbackId = RingtoneRegistry.resolveEffectId(ctxObj, pathObj.toString())
                            if (fallbackId != null) {
                                return@intercept VibrationEffect.createPredefined(fallbackId)
                            }
                        }

                        null
                    }
                    Log.d(TAG, "Successfully hooked android.os.VibrationEffectStubImpl.get")
                    break
                }
            }
        } catch (t: Throwable) {
            // Class might not be loaded in this process classloader
            Log.d(TAG, "VibrationEffectStubImpl not present or not hookable in this classloader: ${t.message}")
        }
    }

    private fun hookVibrationEffectStatic(module: XposedModule, classLoader: ClassLoader) {
        try {
            val effectClass = Class.forName("android.os.VibrationEffect", false, classLoader)
            for (m: Method in effectClass.declaredMethods) {
                if (m.name == "get" &&
                    m.parameterTypes.size == 2 &&
                    m.parameterTypes[0] == Uri::class.java &&
                    m.parameterTypes[1] == Context::class.java
                ) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        val uri = chain.args[0] as? Uri
                        val ctx = chain.args[1] as? Context

                        if (ctx != null && uri != null) {
                            try {
                                val path = RingtoneRegistry.resolvePathFromUri(ctx, uri) ?: uri.toString()
                                val customId = RingtoneRegistry.resolveEffectId(ctx, path)
                                if (customId != null) {
                                    Log.i(TAG, "VibrationEffect.get intercepted custom ringtone [$path] -> Effect ID #$customId")
                                    return@intercept VibrationEffect.createPredefined(customId)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error resolving custom ringtone in VibrationEffect.get", t)
                            }
                        }

                        val original = chain.proceed() as? VibrationEffect
                        if (original != null) return@intercept original

                        if (ctx != null && uri != null) {
                            val fallbackId = RingtoneRegistry.resolveEffectId(ctx, uri.toString())
                            if (fallbackId != null) {
                                Log.i(TAG, "VibrationEffect.get fallback matched [$uri] -> Effect ID #$fallbackId")
                                return@intercept VibrationEffect.createPredefined(fallbackId)
                            }
                        }

                        null
                    }
                    Log.d(TAG, "Successfully hooked android.os.VibrationEffect.get(Uri, Context)")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "VibrationEffect static get not hookable in this classloader: ${t.message}")
        }
    }
}
