package dev.hyperios.app.haptics.ringtone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

class IncomingCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            when (state) {
                TelephonyManager.EXTRA_STATE_RINGING -> {
                    if (isRinging) return
                    isRinging = true
                    val profile = HapticRingtoneStorage.load(context)
                    if (profile != null && profile.events.isNotEmpty()) {
                        suppressStockRingtoneVibration()
                        IncomingCallHapticService.start(context)
                    }
                }
                TelephonyManager.EXTRA_STATE_OFFHOOK,
                TelephonyManager.EXTRA_STATE_IDLE -> {
                    isRinging = false
                    IncomingCallHapticService.stop(context)
                    restoreStockRingtoneVibration()
                }
            }
        }
    }

    companion object {
        @Volatile
        private var isRinging = false
        private var wasSuppressed = false

        fun suppressStockRingtoneVibration() {
            wasSuppressed = true
            Thread {
                try {
                    Runtime.getRuntime().exec(
                        arrayOf(
                            "su", "-c",
                            "settings put system vibrate_when_ringing 0 ; cmd vibrator_manager cancel || cmd vibrator cancel"
                        )
                    ).waitFor()
                } catch (ignored: Exception) {}
            }.start()
        }

        fun restoreStockRingtoneVibration() {
            if (!wasSuppressed) return
            wasSuppressed = false
            Thread {
                try {
                    Runtime.getRuntime().exec(
                        arrayOf(
                            "su", "-c",
                            "settings put system vibrate_when_ringing 1"
                        )
                    ).waitFor()
                } catch (ignored: Exception) {}
            }.start()
        }
    }
}
