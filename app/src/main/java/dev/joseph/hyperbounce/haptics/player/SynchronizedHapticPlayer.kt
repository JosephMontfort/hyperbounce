package dev.joseph.hyperbounce.haptics.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dev.joseph.hyperbounce.haptics.dsp.HapticEvent
import dev.joseph.hyperbounce.haptics.dsp.SynthesizedHaptic

enum class HapticIntensityMode(
    val title: String,
    val description: String
) {
    Crisp("Crisp", "Fast transient hardware clicks with ultra-sharp separation"),
    Punchy("Punchy", "Heavy hardware click on downbeats with melodic snaps"),
    MaxPunch("Max Punch", "Dual-transient composed impulse kicks for maximum physical strike")
}

class SynchronizedHapticPlayer(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

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
        endAtMs: Int = 0,
        intensityMode: HapticIntensityMode = HapticIntensityMode.Punchy,
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
                    val targetEnd = if (endAtMs in (startFromMs + 1)..duration) endAtMs else duration

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

                                if (targetEnd in 1 until duration && current >= targetEnd) {
                                    this@SynchronizedHapticPlayer.stop()
                                    onCompletion()
                                    return
                                }

                                onProgress(current, duration)

                                // Advance events up to current playback position
                                while (nextEventIndex < currentEvents.size && currentEvents[nextEventIndex].timestampMs <= current) {
                                    val event = currentEvents[nextEventIndex]
                                    // Guarantee hardware refractory debounce so the LRA motor has full spring recovery
                                    if (current - lastVibratedMs >= 95L) {
                                        triggerHapticPulse(vibrator, event.effectId, intensityMode)
                                        lastVibratedMs = current.toLong()
                                    }
                                    nextEventIndex++
                                }

                                if (current < targetEnd) {
                                    handler.postDelayed(this, 12L) // 12ms sub-frame polling for tight synchronization
                                } else {
                                    this@SynchronizedHapticPlayer.stop()
                                    onCompletion()
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

    companion object {
        private val RINGTONE_AUDIO_ATTRS: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        fun triggerHapticPulse(
            vibrator: Vibrator,
            effectId: Int,
            intensityMode: HapticIntensityMode = HapticIntensityMode.Punchy
        ) {
            try {
                when (intensityMode) {
                    HapticIntensityMode.MaxPunch -> {
                        // Maximum physical strike: full amplitude 255 OneShot kicks on downbeats and clicks on notes
                        val dur = if (effectId == 5) 34L else 20L
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vibrator.vibrate(VibrationEffect.createOneShot(dur, 255), RINGTONE_AUDIO_ATTRS)
                        } else {
                            vibrator.vibrate(dur)
                        }
                    }
                    HapticIntensityMode.Punchy -> {
                        // Downbeats get solid 26ms 255 kick, melodic notes get crisp hardware clicks
                        if (effectId == 5) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                vibrator.vibrate(VibrationEffect.createOneShot(26L, 255), RINGTONE_AUDIO_ATTRS)
                            } else {
                                vibrator.vibrate(26L)
                            }
                        } else {
                            val eff = when (effectId) {
                                4 -> 4 // EFFECT_POP
                                3 -> 3 // EFFECT_THUD
                                2 -> VibrationEffect.EFFECT_TICK
                                1 -> VibrationEffect.EFFECT_DOUBLE_CLICK
                                else -> VibrationEffect.EFFECT_CLICK
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                vibrator.vibrate(VibrationEffect.createPredefined(eff), RINGTONE_AUDIO_ATTRS)
                            } else {
                                vibrator.vibrate(VibrationEffect.createOneShot(16L, 200), RINGTONE_AUDIO_ATTRS)
                            }
                        }
                    }
                    HapticIntensityMode.Crisp -> {
                        // Ultra-sharp, lightweight tactile clicks without heavy motor drag
                        val eff = when (effectId) {
                            5 -> VibrationEffect.EFFECT_CLICK
                            2 -> VibrationEffect.EFFECT_TICK
                            1 -> VibrationEffect.EFFECT_DOUBLE_CLICK
                            else -> VibrationEffect.EFFECT_TICK
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            vibrator.vibrate(VibrationEffect.createPredefined(eff), RINGTONE_AUDIO_ATTRS)
                        } else {
                            vibrator.vibrate(VibrationEffect.createOneShot(12L, 140), RINGTONE_AUDIO_ATTRS)
                        }
                    }
                }
            } catch (ignored: Throwable) {}
        }
    }
}
