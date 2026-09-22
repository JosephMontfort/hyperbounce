package dev.joseph.hyperbounce.haptics.ringtone

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
    private const val AUDIO_BUFFER_COMPENSATION_DELAY_MS = 130L
    private val previewHandler by lazy { Handler(Looper.getMainLooper()) }
    @Volatile
    private var pendingPreviewRunnable: Runnable? = null

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

        // 2. Hook into BatchMediaPlayer.l(Uri) with audio buffer latency compensation (130ms)
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
                                    Log.i(TAG, "BatchMediaPlayer.${m.name} intercepted custom ringtone [$uri] -> Effect ID #$customId")
                                    val thisObj = chain.thisObject
                                    val vibratorField = bmpClass.declaredFields.firstOrNull {
                                        android.os.Vibrator::class.java.isAssignableFrom(it.type)
                                    }

                                    // Cancel any previous pending preview haptic to avoid collision
                                    pendingPreviewRunnable?.let { previewHandler.removeCallbacks(it) }

                                    val r = Runnable {
                                        try {
                                            if (vibratorField != null) {
                                                vibratorField.isAccessible = true
                                                val vib = vibratorField.get(thisObj) as? android.os.Vibrator
                                                if (vib != null) {
                                                    val attr = android.media.AudioAttributes.Builder()
                                                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                                                        .build()
                                                    vib.vibrate(VibrationEffect.createPredefined(customId), attr)
                                                    Log.i(TAG, "BatchMediaPlayer delayed vibration fired for #$customId (+${AUDIO_BUFFER_COMPENSATION_DELAY_MS}ms audio sync)")
                                                }
                                            }
                                        } catch (t: Throwable) {
                                            Log.e(TAG, "Error in delayed preview vibration", t)
                                        }
                                    }
                                    pendingPreviewRunnable = r
                                    previewHandler.postDelayed(r, AUDIO_BUFFER_COMPENSATION_DELAY_MS)

                                    // Return null to prevent immediate un-delayed vibration and eliminate double-trigger
                                    return@intercept null
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error in BatchMediaPlayer hook", t)
                            }
                        }
                        chain.proceed()
                    }
                    Log.i(TAG, "Successfully hooked BatchMediaPlayer.${m.name}(Uri) with audio delay compensation")
                    break
                }
            }

            // Hook BatchMediaPlayer.t(int) to cancel delayed vibration if preview stopped early
            for (m: Method in bmpClass.declaredMethods) {
                if (m.name == "t" && m.parameterTypes.size == 1 && m.parameterTypes[0] == Int::class.javaPrimitiveType) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        pendingPreviewRunnable?.let {
                            previewHandler.removeCallbacks(it)
                            pendingPreviewRunnable = null
                        }
                        chain.proceed()
                    }
                    Log.i(TAG, "Successfully hooked BatchMediaPlayer.t(int) for preview cancellation")
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
