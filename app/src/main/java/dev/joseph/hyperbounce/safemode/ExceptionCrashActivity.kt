package dev.joseph.hyperbounce.safemode

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.util.Date

/**
 * Detailed crash viewer displaying stack traces, error locations, and one-tap copy button.
 */
class ExceptionCrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val alias = intent.getStringExtra("key_pkg") ?: "systemui"
        val realPkg = intent.getStringExtra("key_real_pkg") ?: alias
        val shortMsg = intent.getStringExtra("key_shortMsg") ?: "Unknown error"
        val stackTrace = intent.getStringExtra("key_stackTrace") ?: "No stack trace available."
        val throwClass = intent.getStringExtra("key_throwClassName") ?: "Unknown"
        val throwFile = intent.getStringExtra("key_throwFileName") ?: "Unknown"
        val throwLine = intent.getIntExtra("key_throwLineNumber", -1)
        val throwMethod = intent.getStringExtra("key_throwMethodName") ?: "Unknown"
        val crashTime = Date()

        val logSummary = buildString {
            appendLine("App: $realPkg ($alias)")
            appendLine("Time: $crashTime")
            appendLine("Message: $shortMsg")
            appendLine("Location: $throwClass.$throwMethod ($throwFile:$throwLine)")
            appendLine("----------------------------------------")
            appendLine("Stack Trace:")
            appendLine(stackTrace)
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 48, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val titleView = TextView(this).apply {
            text = "Crash Diagnostics"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, 24)
        }
        rootLayout.addView(titleView)

        val metaView = TextView(this).apply {
            text = "Package: $realPkg\nClass: $throwClass\nMethod: $throwMethod (Line $throwLine)\nTime: $crashTime"
            textSize = 14f
            setPadding(0, 0, 0, 24)
        }
        rootLayout.addView(metaView)

        val copyButton = Button(this).apply {
            text = "Copy Full Log to Clipboard"
            setOnClickListener {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("HyperBounce Crash Log", logSummary)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this@ExceptionCrashActivity, "Crash log copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        rootLayout.addView(copyButton)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1.0f
            ).apply {
                topMargin = 24
                bottomMargin = 24
            }
        }

        val logTextView = TextView(this).apply {
            text = stackTrace
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(logTextView)
        rootLayout.addView(scrollView)

        val exitButton = Button(this).apply {
            text = "Exit Safe Mode & Close"
            setOnClickListener {
                SafeModeHandler.removeCrashProp(alias)
                Toast.makeText(this@ExceptionCrashActivity, "Safe Mode cleared for $realPkg", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
        rootLayout.addView(exitButton)

        setContentView(rootLayout)
    }
}
