package dev.hyperios.app.haptics.ringtone

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dev.hyperios.app.haptics.player.HapticIntensityMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class IncomingCallHapticService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var playbackJob: Job? = null
    private var vibrator: Vibrator? = null

    private val ringtoneAttributes by lazy {
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator ?: (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP) {
            stopPlayback()
            stopSelf()
            return START_NOT_STICKY
        }

        startPlayback()
        return START_NOT_STICKY
    }

    private fun startPlayback() {
        if (playbackJob?.isActive == true) {
            Log.d("IncomingCallHapticService", "Playback already running, ignoring duplicate start")
            return
        }
        playbackJob?.cancel()
        val profile = HapticRingtoneStorage.load(this) ?: run {
            stopSelf()
            return
        }

        if (profile.events.isEmpty()) {
            stopSelf()
            return
        }

        val v = vibrator ?: run {
            stopSelf()
            return
        }

        val events = profile.events.sortedBy { it.timestampMs }
        val durationMs = if (profile.durationMs > 0) profile.durationMs else (events.last().timestampMs + 500L)
        val mode = profile.intensityMode

        playbackJob = serviceScope.launch {
            val totalTimeoutMs = 60_000L // 60s standard call timeout

            // Calibrated audio preroll sync lead-in:
            // Telecom takes ~250-350ms to gain audio focus, initialize MediaPlayer, and begin speaker output.
            // We poll until STREAM_RING is active (or fallback to 320ms) so haptics hit right on the first beat.
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            var leadInWaitMs = 0L
            while (isActive && leadInWaitMs < 320L) {
                if (isStreamRingActive(audioManager)) {
                    break
                }
                delay(20)
                leadInWaitMs += 20
            }

            val globalStartTime = SystemClock.uptimeMillis()

            try {
                while (isActive && (SystemClock.uptimeMillis() - globalStartTime) < totalTimeoutMs) {
                    val loopStartTime = SystemClock.uptimeMillis()

                    for (event in events) {
                        if (!isActive) break

                        val targetTime = loopStartTime + event.timestampMs
                        val now = SystemClock.uptimeMillis()
                        val waitMs = targetTime - now
                        if (waitMs > 0) {
                            delay(waitMs)
                        }

                        if (!isActive) break
                        triggerRingtoneVibration(v, event.effectId, mode)
                    }

                    // Wait out the remainder of the ringtone loop duration
                    val elapsedLoop = SystemClock.uptimeMillis() - loopStartTime
                    val remainingLoopTime = durationMs - elapsedLoop
                    if (isActive && remainingLoopTime > 0) {
                        delay(remainingLoopTime.coerceAtMost(3000L))
                    }
                }
            } finally {
                try {
                    v.cancel()
                } catch (ignored: Exception) {}
            }
            stopSelf()
        }
    }

    private fun triggerRingtoneVibration(vibrator: Vibrator, effectId: Int, mode: HapticIntensityMode) {
        try {
            when (mode) {
                HapticIntensityMode.MaxPunch -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                        vibrator.areAllPrimitivesSupported(
                            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
                            VibrationEffect.Composition.PRIMITIVE_CLICK
                        )
                    ) {
                        val effect = VibrationEffect.startComposition()
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 1.0f)
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1.0f)
                            .compose()
                        vibrator.vibrate(effect, ringtoneAttributes)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(
                            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK),
                            ringtoneAttributes
                        )
                    } else {
                        vibrator.vibrate(VibrationEffect.createOneShot(24L, 255), ringtoneAttributes)
                    }
                }
                HapticIntensityMode.Punchy -> {
                    val eff = when (effectId) {
                        5 -> VibrationEffect.EFFECT_HEAVY_CLICK
                        4, 3, 1 -> VibrationEffect.EFFECT_CLICK
                        2 -> VibrationEffect.EFFECT_TICK
                        else -> VibrationEffect.EFFECT_CLICK
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(VibrationEffect.createPredefined(eff), ringtoneAttributes)
                    } else {
                        vibrator.vibrate(VibrationEffect.createOneShot(18L, 230), ringtoneAttributes)
                    }
                }
                HapticIntensityMode.Crisp -> {
                    val eff = when (effectId) {
                        5 -> VibrationEffect.EFFECT_HEAVY_CLICK
                        4, 3, 1 -> VibrationEffect.EFFECT_CLICK
                        2 -> VibrationEffect.EFFECT_TICK
                        else -> VibrationEffect.EFFECT_CLICK
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(VibrationEffect.createPredefined(eff), ringtoneAttributes)
                    } else {
                        vibrator.vibrate(VibrationEffect.createOneShot(14L, 180), ringtoneAttributes)
                    }
                }
            }
        } catch (ignored: Throwable) {}
    }

    private fun isStreamRingActive(audioManager: android.media.AudioManager?): Boolean {
        if (audioManager == null) return false
        return try {
            val method = android.media.AudioManager::class.java.getMethod(
                "isStreamActive",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(audioManager, android.media.AudioManager.STREAM_RING, 0) as? Boolean ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            vibrator?.cancel()
        } catch (ignored: Exception) {}
        IncomingCallReceiver.restoreStockRingtoneVibration()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.hyperios.app.action.START_RINGTONE_HAPTIC"
        const val ACTION_STOP = "dev.hyperios.app.action.STOP_RINGTONE_HAPTIC"

        fun start(context: Context) {
            val intent = Intent(context, IncomingCallHapticService::class.java).apply {
                action = ACTION_START
            }
            try {
                context.startService(intent)
            } catch (ignored: Exception) {}
        }

        fun stop(context: Context) {
            val intent = Intent(context, IncomingCallHapticService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (ignored: Exception) {}
        }
    }
}
