package dev.joseph.hyperbounce.haptics.hook

import android.app.Application
import android.content.Context
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import io.github.libxposed.api.XposedModule
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

object RealtimeAudioHapticHook {

    private const val TAG = "RealtimeAudioHaptic"

    @Volatile
    private var vibrator: Vibrator? = null

    @Volatile
    private var lastHapticTimestamp = 0L

    fun init(module: XposedModule, classLoader: ClassLoader) {
        try {
            val audioTrackClass = classLoader.loadClass("android.media.AudioTrack")

            // Hook write(short[] audioData, int offsetInShorts, int sizeInShorts)
            val writeShortMethod = audioTrackClass.getDeclaredMethod(
                "write",
                ShortArray::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            module.hook(writeShortMethod).intercept { chain ->
                val result = chain.proceed()
                if (AudioHapticConfigHolder.config.enabled) {
                    val args = chain.args
                    val shorts = args[0] as? ShortArray
                    val offset = (args[1] as? Int) ?: 0
                    val size = (args[2] as? Int) ?: 0
                    if (shorts != null && size > 0) {
                        processShortAudioBuffer(shorts, offset, size)
                    }
                }
                result
            }

            // Hook write(byte[] audioData, int offsetInBytes, int sizeInBytes)
            val writeByteMethod = audioTrackClass.getDeclaredMethod(
                "write",
                ByteArray::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            module.hook(writeByteMethod).intercept { chain ->
                val result = chain.proceed()
                if (AudioHapticConfigHolder.config.enabled) {
                    val args = chain.args
                    val bytes = args[0] as? ByteArray
                    val offset = (args[1] as? Int) ?: 0
                    val size = (args[2] as? Int) ?: 0
                    if (bytes != null && size > 0) {
                        processByteAudioBuffer(bytes, offset, size)
                    }
                }
                result
            }

            Log.i(TAG, "Successfully installed AudioTrack real-time haptic hooks")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook AudioTrack", t)
        }
    }

    private fun processShortAudioBuffer(buffer: ShortArray, offset: Int, size: Int) {
        val now = SystemClock.uptimeMillis()
        val debounce = AudioHapticConfigHolder.config.refractoryDebounceMs
        if (now - lastHapticTimestamp < debounce) return

        val step = max(1, size / 32)
        var sumSquares = 0.0
        var count = 0
        var maxPeak = 0

        var i = offset
        val limit = offset + size
        while (i < limit) {
            val sample = abs(buffer[i].toInt())
            if (sample > maxPeak) maxPeak = sample
            sumSquares += (sample * sample)
            count++
            i += step
        }

        if (count == 0) return
        val rms = sqrt(sumSquares / count)

        // Threshold adjusted by sensitivity (0.1 to 2.0)
        val sensitivity = AudioHapticConfigHolder.config.sensitivity.coerceAtLeast(0.1f)
        val rmsThreshold = 9000.0 / sensitivity
        val peakThreshold = 18000.0 / sensitivity

        if (rms > rmsThreshold || maxPeak > peakThreshold) {
            lastHapticTimestamp = now
            triggerHapticTick()
        }
    }

    private fun processByteAudioBuffer(buffer: ByteArray, offset: Int, size: Int) {
        val now = SystemClock.uptimeMillis()
        val debounce = AudioHapticConfigHolder.config.refractoryDebounceMs
        if (now - lastHapticTimestamp < debounce) return

        val step = max(2, size / 32)
        var maxPeak = 0

        var i = offset
        val limit = offset + size - 1
        while (i < limit) {
            // Little-endian 16-bit PCM
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = abs((high shl 8) or low)
            if (sample > maxPeak) maxPeak = sample
            i += step
        }

        val sensitivity = AudioHapticConfigHolder.config.sensitivity.coerceAtLeast(0.1f)
        val peakThreshold = 18000.0 / sensitivity

        if (maxPeak > peakThreshold) {
            lastHapticTimestamp = now
            triggerHapticTick()
        }
    }

    private fun triggerHapticTick() {
        try {
            if (vibrator == null) {
                val app = try {
                    val activityThreadClass = Class.forName("android.app.ActivityThread")
                    val currentApplicationMethod = activityThreadClass.getDeclaredMethod("currentApplication")
                    currentApplicationMethod.invoke(null) as? Application
                } catch (t: Throwable) {
                    null
                }
                vibrator = app?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            val vib = vibrator ?: return
            val tickMs = AudioHapticConfigHolder.config.tickDurationMs.coerceIn(5L, 30L)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(tickMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(tickMs)
            }
        } catch (t: Throwable) {
            // Avoid crashing host game or media player
        }
    }
}
