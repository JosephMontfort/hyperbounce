package dev.hyperios.app.haptics.ringtone

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
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
    private const val AUDIO_BUFFER_COMPENSATION_DELAY_MS = 130L
    @Volatile
    private var telecomPendingRunnable: Runnable? = null

    fun init(module: XposedModule, classLoader: ClassLoader) {
        Log.i(TAG, "Initializing Telecom Ringtone Haptic Hook...")
        hookRinger(module, classLoader)
        hookMiuiAsyncRingtonePlayer(module, classLoader)
    }

    private fun hookRinger(module: XposedModule, classLoader: ClassLoader) {
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
                        originalResult
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

                            chain.proceed()
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
                                Log.i(TAG, "maybeStartVibration: keeping hardware/resolved effect: $currentEffect")
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

    private fun hookMiuiAsyncRingtonePlayer(module: XposedModule, classLoader: ClassLoader) {
        try {
            val miuiPlayerClass = Class.forName("com.android.server.telecom.MiuiAsyncRingtonePlayer", false, classLoader)

            // 1. Hook startLinearVibratorInner() to inject audio buffer compensation delay
            for (m: Method in miuiPlayerClass.declaredMethods) {
                if (m.name == "startLinearVibratorInner" && m.parameterTypes.isEmpty()) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        val thisObj = chain.thisObject
                        val effectField = miuiPlayerClass.getDeclaredField("mEffect").apply { isAccessible = true }
                        val effect = effectField.get(thisObj) as? VibrationEffect
                        if (isCustomRingtoneEffect(effect)) {
                            Log.i(TAG, "MiuiAsyncRingtonePlayer.startLinearVibratorInner: custom ringtone haptic detected. Applying ${AUDIO_BUFFER_COMPENSATION_DELAY_MS}ms audio sync delay.")

                            val vibField = miuiPlayerClass.getDeclaredField("mVibrator").apply { isAccessible = true }
                            val attrField = miuiPlayerClass.getDeclaredField("mAttributes").apply { isAccessible = true }
                            val ringerField = miuiPlayerClass.getDeclaredField("mHasRinger").apply { isAccessible = true }
                            val ownerField = miuiPlayerClass.getDeclaredField("mOwner").apply { isAccessible = true }
                            val owner = ownerField.get(thisObj)
                            val ownerHandler = owner?.javaClass?.getDeclaredField("mHandler")?.apply { isAccessible = true }?.get(owner) as? Handler
                                ?: Handler(Looper.getMainLooper())

                            telecomPendingRunnable?.let { ownerHandler.removeCallbacks(it) }

                            val r = Runnable {
                                try {
                                    val hasRinger = ringerField.getBoolean(thisObj)
                                    val vib = vibField.get(thisObj) as? Vibrator
                                    val attr = attrField.get(thisObj) as? VibrationAttributes
                                    val curEffect = effectField.get(thisObj) as? VibrationEffect
                                    if (hasRinger && vib != null && curEffect != null) {
                                        Log.i(TAG, "MiuiAsyncRingtonePlayer delayed vibration firing now (+${AUDIO_BUFFER_COMPENSATION_DELAY_MS}ms, synced with speaker audio)")
                                        if (attr != null) {
                                            vib.vibrate(curEffect, attr)
                                        } else {
                                            vib.vibrate(curEffect)
                                        }

                                        // Register looping listener after 1 second
                                        ownerHandler.postDelayed({
                                            try {
                                                    val listenerField = miuiPlayerClass.getDeclaredField("vibratorStateChangedListener").apply { isAccessible = true }
                                                    val listener = listenerField.get(thisObj)
                                                    if (listener != null) {
                                                        val addListenerMethod = vib.javaClass.methods.firstOrNull {
                                                            it.name == "addVibratorStateListener" && it.parameterTypes.size == 1
                                                        }
                                                        addListenerMethod?.invoke(vib, listener)
                                                        miuiPlayerClass.getDeclaredField("mIsVibratorListenerRegisted").apply { isAccessible = true }.setBoolean(thisObj, true)
                                                    }
                                            } catch (t: Throwable) {
                                                Log.e(TAG, "Error registering delayed looping listener", t)
                                            }
                                        }, 1000L)
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Error in delayed custom ringtone vibration", t)
                                }
                            }
                            telecomPendingRunnable = r
                            ownerHandler.postDelayed(r, AUDIO_BUFFER_COMPENSATION_DELAY_MS)
                            return@intercept null
                        }

                        chain.proceed()
                    }
                    Log.d(TAG, "Hooked MiuiAsyncRingtonePlayer.startLinearVibratorInner successfully")
                    break
                }
            }

            // 2. Hook stopVibrator() to cancel pending delayed vibration when call ends
            for (m: Method in miuiPlayerClass.declaredMethods) {
                if (m.name == "stopVibrator" && m.parameterTypes.isEmpty()) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        telecomPendingRunnable?.let { r ->
                            try {
                                val thisObj = chain.thisObject
                                val ownerField = miuiPlayerClass.getDeclaredField("mOwner").apply { isAccessible = true }
                                val owner = ownerField.get(thisObj)
                                val ownerHandler = owner?.javaClass?.getDeclaredField("mHandler")?.apply { isAccessible = true }?.get(owner) as? Handler
                                ownerHandler?.removeCallbacks(r)
                                telecomPendingRunnable = null
                            } catch (_: Throwable) {}
                        }
                        chain.proceed()
                    }
                    Log.d(TAG, "Hooked MiuiAsyncRingtonePlayer.stopVibrator successfully")
                    break
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "MiuiAsyncRingtonePlayer not hooked in this classloader: ${t.message}")
        }
    }

    private fun isCustomRingtoneEffect(effect: VibrationEffect?): Boolean {
        if (effect == null) return false
        try {
            if (effect.javaClass.name.contains("Composed")) {
                val segmentsField = effect.javaClass.getDeclaredField("mSegments").apply { isAccessible = true }
                val segments = segmentsField.get(effect) as? List<*>
                val first = segments?.firstOrNull()
                if (first != null) {
                    val effectIdField = first.javaClass.getDeclaredField("mEffectId").apply { isAccessible = true }
                    val id = effectIdField.getInt(first)
                    if (id >= RingtoneRegistry.BASE_CUSTOM_EFFECT_ID) return true
                }
            }
        } catch (_: Throwable) {}

        val str = effect.toString()
        val match = Regex("""(?:effectId|effect|id)[=:\s]+(\d+)""").find(str)
        if (match != null) {
            val id = match.groupValues[1].toIntOrNull() ?: 0
            if (id >= RingtoneRegistry.BASE_CUSTOM_EFFECT_ID) return true
        }
        return false
    }

    private fun createWaveformEffectForActiveRingtone(ringtoneUri: Uri?): VibrationEffect? {
        val file = File("/data/system/hyperbounce_ringtone_haptics.json")
        val jsonText = when {
            file.exists() && file.canRead() -> file.readText()
            else -> {
                val fallback = File("/data/data/dev.hyperios.app/files/active_ringtone_haptics.json")
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
