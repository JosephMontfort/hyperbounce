package dev.hyperios.app.safemode

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Recovery dialog launched when Safe Mode is triggered by system_server.
 * Notifies the user, explains that hooks have been suspended, and allows one-tap recovery or log viewing.
 */
class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val alias = intent.getStringExtra("key_pkg") ?: "systemui"
        val realPkg = intent.getStringExtra("key_real_pkg") ?: CrashScope.getPackageName(alias) ?: alias
        val shortMsg = intent.getStringExtra("key_shortMsg") ?: "Repeated fatal crashes detected."
        val stackTrace = intent.getStringExtra("key_stackTrace")

        val appName = when (alias) {
            "systemui" -> "System UI"
            "home" -> "HyperOS Launcher"
            "settings" -> "System Settings"
            else -> realPkg
        }

        val message = "HyperBounce detected frequent crashes in $appName ($realPkg).\n\n" +
                "Safe Mode has been automatically activated for this app to protect your device from bootloops. " +
                "All HyperBounce hooks for $appName are temporarily suspended while the rest of your system remains operational."

        val builder = AlertDialog.Builder(this)
            .setTitle("⚠️ Safe Mode Activated")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("Exit Safe Mode") { dialog, _ ->
                SafeModeHandler.removeCrashProp(alias)
                Toast.makeText(this, "Safe Mode cleared for $appName. Restarting app may re-apply hooks.", Toast.LENGTH_LONG).show()
                dialog.dismiss()
                finish()
            }
            .setNegativeButton("Keep Safe Mode") { dialog, _ ->
                dialog.dismiss()
                finish()
            }

        if (!stackTrace.isNullOrBlank()) {
            builder.setNeutralButton("View Crash Log") { _, _ ->
                val detailIntent = Intent(this, ExceptionCrashActivity::class.java).apply {
                    putExtras(intent)
                }
                startActivity(detailIntent)
                finish()
            }
        }

        val dialog = builder.create()
        dialog.show()
    }
}
