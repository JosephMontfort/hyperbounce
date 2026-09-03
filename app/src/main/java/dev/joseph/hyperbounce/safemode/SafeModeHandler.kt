package dev.joseph.hyperbounce.safemode

import android.content.SharedPreferences
import android.os.SystemClock
import android.os.SystemProperties
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hyperceiler-compatible Safe Mode Handler for HyperBounce.
 * Provides real-time crash monitoring, automatic safe mode triggering, and manual toggles.
 */
object SafeModeHandler {
    private const val TAG = "HyperBounce_SafeMode"
    private const val CRASH_WINDOW_MS = 60_000L // 60 seconds from startup
    private const val CRASH_THRESHOLD = 2      // 2 crashes in 60s triggers Safe Mode

    private val processStartTime = SystemClock.uptimeMillis()
    private val crashCount = AtomicInteger(0)
    private var watchdogInstalled = false

    /**
     * Checks if a package is in Safe Mode via either system properties, preferences, or global kill switch.
     */
    fun isSafeMode(packageName: String, prefs: SharedPreferences? = null): Boolean {
        // 1. Check System Property (fastest, persists across reboots/crashes)
        if (CrashScope.isPackageInSafeModeProp(packageName)) {
            Log.w(TAG, "[SAFE MODE ACTIVE] Package $packageName is blocked by system property ${CrashScope.PROP_SAFE_MODE}")
            return true
        }

        // 2. Check Preferences if available
        if (prefs != null) {
            val globalSafe = prefs.getBoolean("prefs_key_global_safe_mode", false)
            if (globalSafe) {
                Log.w(TAG, "[SAFE MODE ACTIVE] Global Safe Mode is active in preferences")
                return true
            }

            val alias = CrashScope.getAlias(packageName)
            if (alias != null) {
                val key = "prefs_key_${alias}_safe_mode_enable"
                if (prefs.getBoolean(key, false)) {
                    Log.w(TAG, "[SAFE MODE ACTIVE] Alias $alias ($packageName) is disabled in preferences ($key)")
                    return true
                }
            }
        }

        return false
    }

    /**
     * Updates the system property to activate safe mode for a specific alias.
     */
    fun updateCrashProp(alias: String) {
        val current = CrashScope.getCrashingAliases().toMutableSet()
        current.add(alias.lowercase())
        applySafeModeProp(current)
    }

    /**
     * Removes an alias from the safe mode system property.
     */
    fun removeCrashProp(alias: String) {
        val current = CrashScope.getCrashingAliases().toMutableSet()
        current.remove(alias.lowercase())
        applySafeModeProp(current)
    }

    /**
     * Clears all safe modes in the system property.
     */
    fun clearAllSafeModes() {
        applySafeModeProp(emptySet())
    }

    private fun applySafeModeProp(aliases: Set<String>) {
        val propValue = aliases.filter { it.isNotEmpty() }.joinToString(",")
        try {
            SystemProperties.set(CrashScope.PROP_SAFE_MODE, propValue)
            Log.i(TAG, "Updated safe mode property ${CrashScope.PROP_SAFE_MODE} = '$propValue'")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to set SystemProperty ${CrashScope.PROP_SAFE_MODE} to '$propValue'", t)
            // Fallback: try root shell command if app has root access
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop ${CrashScope.PROP_SAFE_MODE} '$propValue'"))
            } catch (_: Throwable) {}
        }
    }

    /**
     * Installs an UncaughtExceptionHandler in the hooked process.
     * If repeated crashes occur within 60s of process startup, Safe Mode is automatically engaged.
     */
    fun installCrashWatchdog(packageName: String) {
        if (watchdogInstalled) return
        watchdogInstalled = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val uptime = SystemClock.uptimeMillis() - processStartTime
                val alias = CrashScope.getAlias(packageName) ?: packageName

                Log.e(TAG, "[CRASH DETECTED] Process $packageName ($alias) crashed after ${uptime}ms", throwable)

                if (uptime in 1..CRASH_WINDOW_MS) {
                    val count = crashCount.incrementAndGet()
                    Log.w(TAG, "Process $packageName crash count within window: $count/$CRASH_THRESHOLD")

                    if (count >= CRASH_THRESHOLD) {
                        Log.e(TAG, "[AUTO SAFE MODE TRIGGERED] $packageName exceeded crash threshold! Enabling Safe Mode.")
                        updateCrashProp(alias)
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error in crash watchdog", t)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
