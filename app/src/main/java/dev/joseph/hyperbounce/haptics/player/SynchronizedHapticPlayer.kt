package dev.joseph.hyperbounce.haptics.player

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
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

    var isPlaying: Boolean = false
        private set

    fun play(
        audioUri: Uri,
        haptic: SynthesizedHaptic,
        onProgress: (currentMs: Int, totalMs: Int) -> Unit = { _, _ -> },
        onCompletion: () -> Unit = {}
    ) {
        stop()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, audioUri)
                setOnPreparedListener { mp ->
                    val duration = mp.duration
                    mp.start()
                    // Fire haptic in exact lockstep
                    vibrator.vibrate(haptic.vibrationEffect)
                    this@SynchronizedHapticPlayer.isPlaying = true

                    progressRunnable = object : Runnable {
                        override fun run() {
                            if (this@SynchronizedHapticPlayer.isPlaying && mediaPlayer != null) {
                                val current = mediaPlayer?.currentPosition ?: 0
                                onProgress(current, duration)
                                if (current < duration) {
                                    handler.postDelayed(this, 16L) // 60fps UI sync
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

    fun stop() {
        isPlaying = false
        progressRunnable?.let { handler.removeCallbacks(it) }
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (ignored: Exception) {
        }
        mediaPlayer = null
        try {
            vibrator.cancel()
        } catch (ignored: Exception) {
        }
    }

    fun release() {
        stop()
    }
}
