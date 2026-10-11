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

    // Dynamic energy history buffer to detect sudden transient spikes (beats/gunshots)
    // instead of firing on continuous steady sound
    private const val HISTORY_SIZE = 24
    private val energyHistory = DoubleArray(HISTORY_SIZE)
    private var historyIndex = 0
    private var historyCount = 0
    private var previousEnergy = 0.0

    fun init(module: XposedModule, classLoader: ClassLoader) {
        try {
            val audioTrackClass = try {
                classLoader.loadClass("android.media.AudioTrack")
            } catch (e: Exception) {
                ClassLoader.getSystemClassLoader().loadClass("android.media.AudioTrack")
            }

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
                        Log.w(TAG, "Could not hook AudioTrack.write: ${method.parameterTypes.joinToString()}", t)
                    }
                }
            }

            Log.i(TAG, "Successfully installed AudioTrack real-time haptic hooks on $hookedCount overloads")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook AudioTrack", t)
        }
    }

    private fun handleAudioTrackWrite(args: List<Any?>) {
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

    private fun evaluateOnsetBeat(instantEnergy: Double, silenceFloor: Double) {
        if (instantEnergy < silenceFloor) {
            previousEnergy = instantEnergy
            return
        }

        // Calculate moving average energy of recent frames
        var sum = 0.0
        for (i in 0 until historyCount) {
            sum += energyHistory[i]
        }
        val avgEnergy = if (historyCount > 0) sum / historyCount else instantEnergy

        // Record current energy into circular history
        energyHistory[historyIndex] = instantEnergy
        historyIndex = (historyIndex + 1) % HISTORY_SIZE
        if (historyCount < HISTORY_SIZE) historyCount++

        val now = SystemClock.uptimeMillis()
        val debounce = AudioHapticConfigHolder.config.refractoryDebounceMs.coerceAtLeast(60L)

        // Beat onset requires:
        // 1. Current energy is a sharp relative surge above moving average
        // 2. Current energy is rising (onset attack), not trailing off
        // 3. Debounce lockout has passed
        val sensitivity = AudioHapticConfigHolder.config.sensitivity.coerceIn(0.2f, 2.0f)
        val spikeMultiplier = (1.95 - (sensitivity * 0.45)).coerceIn(1.25, 2.1)
        val isSpike = instantEnergy > (avgEnergy * spikeMultiplier)
        val isRisingEdge = instantEnergy > (previousEnergy * 1.15)

        previousEnergy = instantEnergy

        if (isSpike && isRisingEdge && (now - lastHapticTimestamp >= debounce)) {
            lastHapticTimestamp = now
            triggerHapticTick()
        }
    }

    private fun processShortAudioBuffer(buffer: ShortArray, offset: Int, size: Int) {
        val step = max(1, size / 32)
        var sumSquares = 0.0
        var count = 0

        var i = offset
        val limit = minOf(buffer.size, offset + size)
        while (i < limit) {
            val sample = abs(buffer[i].toInt())
            sumSquares += (sample * sample)
            count++
            i += step
        }

        if (count == 0) return
        val rms = sqrt(sumSquares / count)
        evaluateOnsetBeat(rms, silenceFloor = 1400.0)
    }

    private fun processByteAudioBuffer(buffer: ByteArray, offset: Int, size: Int) {
        val step = max(2, size / 32)
        var sumSquares = 0.0
        var count = 0

        var i = offset
        val limit = minOf(buffer.size - 1, offset + size - 1)
        while (i < limit) {
            val low = buffer[i].toInt() and 0xFF
            val high = buffer[i + 1].toInt()
            val sample = abs((high shl 8) or low)
            sumSquares += (sample * sample)
            count++
            i += step
        }

        if (count == 0) return
        val rms = sqrt(sumSquares / count)
        evaluateOnsetBeat(rms, silenceFloor = 1400.0)
    }

    private fun processFloatAudioBuffer(buffer: FloatArray, offset: Int, size: Int) {
        val step = max(1, size / 32)
        var sumSquares = 0.0
        var count = 0

        var i = offset
        val limit = minOf(buffer.size, offset + size)
        while (i < limit) {
            val s = abs(buffer[i])
            sumSquares += (s * s)
            count++
            i += step
        }

        if (count == 0) return
        val rms = sqrt(sumSquares / count)
        // Scale float -1..1 to roughly 16-bit range for unified onset logic
        evaluateOnsetBeat(rms * 32768.0, silenceFloor = 1400.0)
    }

    private fun processByteBuffer(buffer: ByteBuffer, sizeInBytes: Int) {
        try {
            val dup = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
            val shorts = dup.asShortBuffer()
            val availableShorts = minOf(shorts.remaining(), sizeInBytes / 2)
            if (availableShorts <= 0) return

            val step = max(1, availableShorts / 32)
            var sumSquares = 0.0
            var count = 0
            var i = 0
            while (i < availableShorts) {
                val s = abs(shorts.get(i).toInt())
                sumSquares += (s * s)
                count++
                i += step
            }

            if (count == 0) return
            val rms = sqrt(sumSquares / count)
            evaluateOnsetBeat(rms, silenceFloor = 1400.0)
        } catch (t: Throwable) {
            // Safety
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

            // Fire Xiaomi's crisp 16ms hardware click (Effect 0 / EFFECT_CLICK) on each beat onset
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(16L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(16L)
            }
        } catch (t: Throwable) {
            // Never crash host app
        }
    }
}
