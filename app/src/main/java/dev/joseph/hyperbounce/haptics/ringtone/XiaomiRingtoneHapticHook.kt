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

                        chain.proceed()
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
