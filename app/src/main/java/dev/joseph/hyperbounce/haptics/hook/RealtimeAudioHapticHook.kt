package dev.joseph.hyperbounce.haptics.hook

import android.app.Application
import android.content.Context
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import io.github.libxposed.api.XposedModule
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
            var hookedCount = 0

            for (method in audioTrackClass.declaredMethods) {
                if (method.name == "write") {
                    try {
                        module.hook(method).intercept { chain ->
                            val result = chain.proceed()
                            if (AudioHapticConfigHolder.config.enabled) {
                                handleAudioTrackWrite(chain.args)
                            }
                            result
                        }
                        hookedCount++
                    } catch (t: Throwable) {
                        Log.w(TAG, "Could not hook AudioTrack.write overload: ${method.parameterTypes.joinToString()}", t)
                    }
                }
            }

            Log.i(TAG, "Successfully installed AudioTrack real-time haptic hooks on $hookedCount overloads")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook AudioTrack", t)
        }
    }

    private fun handleAudioTrackWrite(args: Array<Any?>) {
        if (args.isEmpty()) return
        val firstArg = args[0] ?: return

        when (firstArg) {
            is ShortArray -> {
                val offset = (args.getOrNull(1) as? Int) ?: 0
                val size = (args.getOrNull(2) as? Int) ?: firstArg.size
                if (size > 0) processShortAudioBuffer(firstArg, offset, size)
            }
            is ByteArray -> {
                val offset = (args.getOrNull(1) as? Int) ?: 0
                val size = (args.getOrNull(2) as? Int) ?: firstArg.size
                if (size > 0) processByteAudioBuffer(firstArg, offset, size)
            }
            is FloatArray -> {
                val offset = (args.getOrNull(1) as? Int) ?: 0
                val size = (args.getOrNull(2) as? Int) ?: firstArg.size
                if (size > 0) processFloatAudioBuffer(firstArg, offset, size)
            }
            is ByteBuffer -> {
                val sizeInBytes = (args.getOrNull(1) as? Int) ?: firstArg.remaining()
                if (sizeInBytes > 0) processByteBuffer(firstArg, sizeInBytes)
            }
        }
    }

    private fun checkDebounceAndThrottle(): Boolean {
        val now = SystemClock.uptimeMillis()
        val debounce = AudioHapticConfigHolder.config.refractoryDebounceMs
        if (now - lastHapticTimestamp < debounce) return false
        lastHapticTimestamp = now
        return true
    }

    private fun processShortAudioBuffer(buffer: ShortArray, offset: Int, size: Int) {
        val step = max(1, size / 32)
        var sumSquares = 0.0
        var count = 0
        var maxPeak = 0

        var i = offset
        val limit = minOf(buffer.size, offset + size)
        while (i < limit) {
            val sample = abs(buffer[i].toInt())
            if (sample > maxPeak) maxPeak = sample
            sumSquares += (sample * sample)
            count++
            i += step
        }

        if (count == 0) return
        val rms = sqrt(sumSquares / count)

        val sensitivity = AudioHapticConfigHolder.config.sensitivity.coerceAtLeast(0.1f)
        val rmsThreshold = 3500.0 / sensitivity
        val peakThreshold = 7000.0 / sensitivity

        if (rms > rmsThreshold || maxPeak > peakThreshold) {
            if (checkDebounceAndThrottle()) {
                triggerHapticTick()
            }
        }
    }

    private fun processByteAudioBuffer(buffer: ByteArray, offset: Int, size: Int) {
        val step = max(2, size / 32)
        var maxPeak = 0

        var i = offset
        val limit = minOf(buffer.size - 1, offset + size - 1)
        while (i < limit) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = abs((high shl 8) or low)
            if (sample > maxPeak) maxPeak = sample
            i += step
        }

        val sensitivity = AudioHapticConfigHolder.config.sensitivity.coerceAtLeast(0.1f)
        val peakThreshold = 7000.0 / sensitivity

        if (maxPeak > peakThreshold) {
            if (checkDebounceAndThrottle()) {
                triggerHapticTick()
            }
        }
    }

    private fun processFloatAudioBuffer(buffer: FloatArray, offset: Int, size: Int) {
        val step = max(1, size / 32)
        var maxPeak = 0f
        var sumSquares = 0.0
        var count = 0

        var i = offset
        val limit = minOf(buffer.size, offset + size)
        while (i < limit) {
            val s = abs(buffer[i])
            if (s > maxPeak) maxPeak = s
            sumSquares += (s * s)
            count++
            i += step
        }

        if (count == 0) return
        val rms = sqrt(sumSquares / count).toFloat()

        val sensitivity = AudioHapticConfigHolder.config.sensitivity.coerceAtLeast(0.1f)
        val rmsThreshold = 0.12f / sensitivity
        val peakThreshold = 0.25f / sensitivity

        if (rms > rmsThreshold || maxPeak > peakThreshold) {
            if (checkDebounceAndThrottle()) {
                triggerHapticTick()
            }
        }
    }

    private fun processByteBuffer(buffer: ByteBuffer, sizeInBytes: Int) {
        try {
            val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val shorts = dup.asShortBuffer()
            val availableShorts = minOf(shorts.remaining(), sizeInBytes / 2)
            if (availableShorts <= 0) return

            val step = max(1, availableShorts / 32)
            var maxPeak = 0
            var i = 0
            while (i < availableShorts) {
                val s = abs(shorts.get(i).toInt())
                if (s > maxPeak) maxPeak = s
                i += step
            }

            val sensitivity = AudioHapticConfigHolder.config.sensitivity.coerceAtLeast(0.1f)
            val peakThreshold = 7000.0 / sensitivity

            if (maxPeak > peakThreshold) {
                if (checkDebounceAndThrottle()) {
                    triggerHapticTick()
                }
            }
        } catch (t: Throwable) {
            // Buffer parsing safety
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

                vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && app != null) {
                    val manager = app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    manager?.defaultVibrator ?: (app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
                } else {
                    app?.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
            }

            val vib = vibrator ?: return
            val tickMs = AudioHapticConfigHolder.config.tickDurationMs.coerceIn(5L, 30L)

            // Use EFFECT_CLICK (0) for a distinct, crisp snap (same as ResonanceX EFFECT_CLICK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
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
