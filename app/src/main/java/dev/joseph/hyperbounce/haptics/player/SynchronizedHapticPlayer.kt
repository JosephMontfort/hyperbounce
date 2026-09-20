package dev.joseph.hyperbounce.haptics.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dev.joseph.hyperbounce.haptics.dsp.HapticEvent
import dev.joseph.hyperbounce.haptics.dsp.SynthesizedHaptic

class SynchronizedHapticPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private val ringtoneAttributes: VibrationAttributes? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_RINGTONE)
            .build()
    } else null

    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    private var currentEvents: List<HapticEvent> = emptyList()
    @Volatile private var nextEventIndex = 0
    @Volatile private var lastVibratedMs = -150L

    var isPlaying: Boolean = false
        private set

    fun play(
        audioUri: Uri,
        haptic: SynthesizedHaptic,
        startFromMs: Int = 0,
        onProgress: (currentMs: Int, totalMs: Int) -> Unit = { _, _ -> },
        onCompletion: () -> Unit = {}
    ) {
        stop()

        currentEvents = haptic.events.sortedBy { it.timestampMs }

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, audioUri)
                setOnPreparedListener { mp ->
                    val duration = mp.duration
                    if (startFromMs in 1 until duration) {
                        mp.seekTo(startFromMs)
                    }
                    mp.start()
                    this@SynchronizedHapticPlayer.isPlaying = true

                    val seekIdx = currentEvents.binarySearchBy(startFromMs.toLong()) { it.timestampMs }
                    nextEventIndex = if (seekIdx >= 0) seekIdx else -seekIdx - 1
                    lastVibratedMs = -150L

                    progressRunnable = object : Runnable {
                        override fun run() {
                            val player = mediaPlayer
                            if (this@SynchronizedHapticPlayer.isPlaying && player != null) {
                                val current = player.currentPosition
                                onProgress(current, duration)

                                // Advance events up to current playback position
                                while (nextEventIndex < currentEvents.size && currentEvents[nextEventIndex].timestampMs <= current) {
                                    val event = currentEvents[nextEventIndex]
                                    // Guarantee hardware refractory debounce so the LRA motor has full spring recovery
                                    if (current - lastVibratedMs >= 95L) {
                                        triggerFullIntensityEffect(event.effectId)
                                        lastVibratedMs = current.toLong()
                                    }
                                    nextEventIndex++
                                }

                                if (current < duration) {
                                    handler.postDelayed(this, 12L) // 12ms sub-frame polling for tight synchronization
                                }
                            }
                        }
                    }
                    progressRunnable?.let { handler.post(it) }
                }
                setOnCompletionListener {
                    this@SynchronizedHapticPlayer.isPlaying = false
                    progressRunnable?.let { handler.removeCallbacks(it) }
                    vibrator.cancel()
                    onCompletion()
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("SynchronizedPlayer", "Error initializing synchronized playback", e)
            stop()
        }
    }

    /**
     * Instantly seeks audio playback and repositions the haptic event stream.
     * Uses binary search to jump the event index with zero latency.
     */
    fun seekTo(positionMs: Int) {
        try {
            mediaPlayer?.seekTo(positionMs)
            val idx = currentEvents.binarySearchBy(positionMs.toLong()) { it.timestampMs }
            nextEventIndex = if (idx >= 0) idx else -idx - 1
            lastVibratedMs = -150L
        } catch (e: Exception) {
            Log.e("SynchronizedPlayer", "Error seeking playback", e)
        }
    }

    /**
     * Fires the hardware actuator at 100% full-rail voltage using USAGE_RINGTONE
     * and API 30+ primitive composition to match stock Xiaomi ringtone punch.
     */
    private fun triggerFullIntensityEffect(effectId: Int) {
        try {
            val effect = when (effectId) {
                5 -> { // Bass downbeat / Heavy punch
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        VibrationEffect.startComposition()
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                            .compose()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    } else {
                        VibrationEffect.createOneShot(24L, 255)
                    }
                }
                4 -> { // Pop / Droplet
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        VibrationEffect.startComposition()
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 0.9f)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                            .compose()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_POP)
                    } else {
                        VibrationEffect.createOneShot(20L, 240)
                    }
                }
                2 -> { // Tick / Micro-texture
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        VibrationEffect.startComposition()
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1.0f)
                            .compose()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                    } else {
                        VibrationEffect.createOneShot(12L, 180)
                    }
                }
                1 -> { // Double Click
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        VibrationEffect.startComposition()
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f, 25)
                            .compose()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                    } else {
                        VibrationEffect.createWaveform(longArrayOf(0, 16, 25, 16), intArrayOf(0, 255, 0, 255), -1)
                    }
                }
                else -> { // Melodic & Transient Click (Effect 0)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        VibrationEffect.startComposition()
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                            .compose()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    } else {
                        VibrationEffect.createOneShot(18L, 255)
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && ringtoneAttributes != null) {
                vibrator.vibrate(effect, ringtoneAttributes)
            } else {
                vibrator.vibrate(effect)
            }
        } catch (ignored: Throwable) {}
    }

    fun stop() {
        isPlaying = false
        progressRunnable?.let { handler.removeCallbacks(it) }
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (ignored: Exception) {}
        mediaPlayer = null
        try {
            vibrator.cancel()
        } catch (ignored: Exception) {}
    }

    fun release() {
        stop()
    }
}
