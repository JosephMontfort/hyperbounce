package dev.joseph.hyperbounce.haptics.ringtone

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import dev.joseph.hyperbounce.haptics.player.HapticIntensityMode
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
                    // Maximum physical excitation: 36ms full-rail 255 pulse on downbeats, 24ms on accents
                    val dur = if (effectId == 5) 36L else 24L
                    vibrator.vibrate(VibrationEffect.createOneShot(dur, 255))
                }
                HapticIntensityMode.Punchy -> {
                    if (effectId == 5) {
                        vibrator.vibrate(VibrationEffect.createOneShot(30L, 255))
                    } else {
                        val eff = when (effectId) {
                            4 -> 4
                            3 -> 3
                            2 -> VibrationEffect.EFFECT_TICK
                            1 -> VibrationEffect.EFFECT_DOUBLE_CLICK
                            else -> VibrationEffect.EFFECT_CLICK
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            vibrator.vibrate(VibrationEffect.createPredefined(eff))
                        } else {
                            vibrator.vibrate(VibrationEffect.createOneShot(18L, 230))
                        }
                    }
                }
                HapticIntensityMode.Crisp -> {
                    val eff = when (effectId) {
                        5 -> VibrationEffect.EFFECT_HEAVY_CLICK
                        4 -> 4
                        3 -> 3
                        2 -> VibrationEffect.EFFECT_TICK
                        1 -> VibrationEffect.EFFECT_DOUBLE_CLICK
                        else -> VibrationEffect.EFFECT_CLICK
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        vibrator.vibrate(VibrationEffect.createPredefined(eff))
                    } else {
                        vibrator.vibrate(VibrationEffect.createOneShot(18L, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                }
            }
        } catch (ignored: Throwable) {}
    }

    private fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try {
            vibrator?.cancel()
        } catch (ignored: Exception) {}
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.joseph.hyperbounce.action.START_RINGTONE_HAPTIC"
        const val ACTION_STOP = "dev.joseph.hyperbounce.action.STOP_RINGTONE_HAPTIC"

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
