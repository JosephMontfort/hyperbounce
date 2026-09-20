package dev.joseph.hyperbounce.haptics.player

import android.content.Context
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
                                        val effectIdToPlay = when (event.effectId) {
                                            5 -> VibrationEffect.EFFECT_HEAVY_CLICK
                                            4 -> 4
                                            3 -> 3
                                            2 -> VibrationEffect.EFFECT_TICK
                                            1 -> VibrationEffect.EFFECT_DOUBLE_CLICK
                                            0 -> VibrationEffect.EFFECT_CLICK
                                            else -> event.effectId
                                        }
                                        try {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                vibrator.vibrate(VibrationEffect.createPredefined(effectIdToPlay))
                                            } else {
                                                val dur = if (event.effectId == 5) 24L else 18L
                                                val amp = if (event.effectId == 5) 255 else 200
                                                vibrator.vibrate(VibrationEffect.createOneShot(dur, amp))
                                            }
                                            lastVibratedMs = current.toLong()
                                        } catch (ignored: Throwable) {}
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
