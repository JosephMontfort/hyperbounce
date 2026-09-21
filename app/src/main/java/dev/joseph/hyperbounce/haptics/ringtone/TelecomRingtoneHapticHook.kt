package dev.joseph.hyperbounce.haptics.ringtone

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.util.Log
import io.github.libxposed.api.XposedModule
import org.json.JSONObject
import java.io.File
import java.lang.reflect.Method

/**
 * Xposed hook for com.android.server.telecom (Telecom System Service).
 * Intercepts incoming call ringtone vibration resolution and seamlessly substitutes
 * HyperBounce's synthesized, beat-synchronized waveform effect in place of stock generic buzzes.
 */
object TelecomRingtoneHapticHook {

    private const val TAG = "HyperBounce_Telecom"

    fun init(module: XposedModule, classLoader: ClassLoader) {
        Log.i(TAG, "Initializing Telecom Ringtone Haptic Hook...")

        try {
            val ringerClass = Class.forName("com.android.server.telecom.Ringer", false, classLoader)

            // 1. Hook getVibrationEffectForRingtone(Uri)
            for (m: Method in ringerClass.declaredMethods) {
                if (m.name == "getVibrationEffectForRingtone" &&
                    m.parameterTypes.size == 1 &&
                    m.parameterTypes[0] == Uri::class.java
                ) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        val uri = chain.args[0] as? Uri
                        if (uri != null) {
                            try {
                                val context = getContextFromRinger(chain.thisObject)
                                val query = if (context != null) RingtoneRegistry.resolvePathFromUri(context, uri) ?: uri.toString() else uri.toString()
                                val customId = RingtoneRegistry.resolveEffectId(context, query)
                                if (customId != null) {
                                    Log.i(TAG, "getVibrationEffectForRingtone mapped [$query] to hardware Effect ID #$customId")
                                    return@intercept VibrationEffect.createPredefined(customId)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error resolving custom effect in getVibrationEffectForRingtone", t)
                            }
                        }

                        val originalResult = chain.proceed() as? VibrationEffect
                        if (originalResult != null) return@intercept originalResult

                        // Fallback waveform ONLY if no hardware effect exists
                        try {
                            val fallbackEffect = createWaveformEffectForActiveRingtone(uri)
                            if (fallbackEffect != null) {
                                Log.i(TAG, "Telecom fallback: provided custom haptic waveform effect")
                                return@intercept fallbackEffect
                            }
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error in fallback createWaveformEffectForActiveRingtone", t)
                        }

                        null
                    }
                    Log.d(TAG, "Hooked Ringer.getVibrationEffectForRingtone successfully")
                    break
                }
            }

            // 2. Hook VibrationEffectProxy.get(Uri, Context)
            try {
                val proxyClass = Class.forName("com.android.server.telecom.Ringer\$VibrationEffectProxy", false, classLoader)
                for (m: Method in proxyClass.declaredMethods) {
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
                                        Log.i(TAG, "VibrationEffectProxy.get mapped [$path] to hardware Effect ID #$customId")
                                        return@intercept VibrationEffect.createPredefined(customId)
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Error in VibrationEffectProxy.get interceptor", t)
                                }
                            }

                            val originalResult = chain.proceed() as? VibrationEffect
                            if (originalResult != null) return@intercept originalResult

                            // Fallback waveform ONLY if stock returned null
                            val fallbackEffect = createWaveformEffectForActiveRingtone(uri)
                            if (fallbackEffect != null) {
                                return@intercept fallbackEffect
                            }

                            null
                        }
                        Log.d(TAG, "Hooked VibrationEffectProxy.get successfully")
                        break
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "VibrationEffectProxy not hooked: ${t.message}")
            }

            // 3. Hook maybeStartVibration(Call, boolean, VibrationEffect, boolean, boolean)
            for (m: Method in ringerClass.declaredMethods) {
                if (m.name == "maybeStartVibration" && m.parameterTypes.size >= 3) {
                    val vibrationEffectIndex = m.parameterTypes.indexOf(VibrationEffect::class.java)
                    if (vibrationEffectIndex != -1) {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            val currentEffect = chain.args[vibrationEffectIndex] as? VibrationEffect
                            if (currentEffect != null) {
                                // A valid effect (hardware predefined effect or stock vibration) is already bound!
                                // DO NOT OVERWRITE IT with synthetic tick clicks!
                                Log.i(TAG, "maybeStartVibration: keeping hardware/resolved effect: $currentEffect")
                            } else {
                                // Only substitute waveform if current effect is null
                                try {
                                    val customEffect = createWaveformEffectForActiveRingtone(null)
                                    if (customEffect != null) {
                                        chain.args[vibrationEffectIndex] = customEffect
                                        Log.i(TAG, "maybeStartVibration substituted fallback haptic waveform")
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Error substituting fallback effect in maybeStartVibration", t)
                                }
                            }
                            chain.proceed()
                        }
                        Log.d(TAG, "Hooked Ringer.maybeStartVibration successfully")
                        break
                    }
                }
            }

        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook Telecom Ringer", t)
        }
    }

    private fun createWaveformEffectForActiveRingtone(ringtoneUri: Uri?): VibrationEffect? {
        val file = File("/data/system/hyperbounce_ringtone_haptics.json")
        val jsonText = when {
            file.exists() && file.canRead() -> file.readText()
            else -> {
                val fallback = File("/data/data/dev.joseph.hyperbounce/files/active_ringtone_haptics.json")
                if (fallback.exists() && fallback.canRead()) fallback.readText() else null
            }
        } ?: return null

        try {
            val json = JSONObject(jsonText)
            val durationMs = json.optLong("durationMs", 20000L).coerceAtLeast(1000L)
            val eventsArray = json.optJSONArray("events") ?: return null
            if (eventsArray.length() == 0) return null

            val events = mutableListOf<Pair<Long, Int>>()
            for (i in 0 until eventsArray.length()) {
                val ev = eventsArray.getJSONObject(i)
                events.add(Pair(ev.getLong("timestampMs"), ev.getInt("effectId")))
            }
            events.sortBy { it.first }

            val timings = mutableListOf<Long>()
            val amplitudes = mutableListOf<Int>()

            var lastMs = 0L
            for ((ts, effectId) in events) {
                if (ts >= durationMs) break
                val silence = ts - lastMs
                if (silence > 0) {
                    timings.add(silence)
                    amplitudes.add(0)
                }

                // Sub-cycle impulse durations: 12-22ms keeps the LRA in pure resonant strike without rattling
                val dur = if (effectId == 5) 22L else 14L
                val amp = if (effectId == 5) 255 else 200

                timings.add(dur)
                amplitudes.add(amp)

                lastMs = ts + dur
            }

            val tail = durationMs - lastMs
            if (tail > 0) {
                timings.add(tail)
                amplitudes.add(0)
            }

            if (timings.isEmpty()) return null

            return VibrationEffect.createWaveform(
                timings.toLongArray(),
                amplitudes.toIntArray(),
                0 // repeat index 0 loops indefinitely while call rings
            )
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to parse active ringtone haptics JSON", t)
            return null
        }
    }

    private fun getContextFromRinger(ringerObj: Any?): Context? {
        if (ringerObj == null) return null
        return try {
            val field = ringerObj.javaClass.getDeclaredField("mContext")
            field.isAccessible = true
            field.get(ringerObj) as? Context
        } catch (_: Throwable) {
            null
        }
    }
}
