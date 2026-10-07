package dev.joseph.hyperbounce.safemode

import android.app.ApplicationErrorReport
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log

/**
 * HyperCeiler-compatible Safe Mode Handler for HyperBounce.
 * Provides real-time crash handling, automatic safe mode triggering, and manual toggles.
 */
object SafeModeHandler {
    private const val TAG = "HyperBounce_SafeMode"

    /**
     * Checks if a package is in Safe Mode via either system properties or preferences.
     */
    @JvmStatic
    @JvmOverloads
    fun isSafeMode(packageName: String, prefs: SharedPreferences? = null): Boolean {
        val alias = CrashScope.getAlias(packageName) ?: packageName

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

            val key = CrashScope.getSafeModeConfigKey(packageName) ?: "prefs_key_${alias}_safe_mode_enable"
            if (prefs.getBoolean(key, false)) {
                Log.w(TAG, "[SAFE MODE ACTIVE] Alias $alias ($packageName) is disabled in preferences ($key)")
                return true
            }
        }

        return false
    }

    /**
     * Updates the system property to activate safe mode for a specific alias.
     */
    @JvmStatic
    fun updateCrashProp(alias: String) {
        val current = CrashScope.getCrashingAliases().toMutableSet()
        current.add(alias.lowercase())
        applySafeModeProp(current)
    }

    /**
     * Removes an alias from the safe mode system property.
     */
    @JvmStatic
    fun removeCrashProp(alias: String) {
        val current = CrashScope.getCrashingAliases().toMutableSet()
        current.remove(alias.lowercase())
        applySafeModeProp(current)
    }

    /**
     * Clears all safe modes in the system property.
     */
    @JvmStatic
    fun clearAllSafeModes() {
        applySafeModeProp(emptySet())
    }

    private fun applySafeModeProp(aliases: Set<String>) {
        val propValue = aliases.filter { it.isNotEmpty() }.joinToString(",")
        try {
            SystemPropertiesHelper.set(CrashScope.PROP_SAFE_MODE, propValue)
            Log.i(TAG, "Updated safe mode property ${CrashScope.PROP_SAFE_MODE} = '$propValue'")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to set SystemProperty ${CrashScope.PROP_SAFE_MODE} to '$propValue'", t)
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop ${CrashScope.PROP_SAFE_MODE} '$propValue'"))
            } catch (_: Throwable) {}
        }
    }

    /**
     * Invoked when a fatal crash or RescueParty threshold is hit in system_server.
     * Updates the safe mode property and launches CrashActivity to notify the user.
     */
    fun onCrashDetected(
        context: Context,
        packageName: String,
        crashInfo: ApplicationErrorReport.CrashInfo?,
        shortMsg: String?,
        stackTrace: String?
    ): Boolean {
        if (isSafeMode(packageName, null)) {
            Log.i(TAG, "$packageName is already in safe mode, skipping UI launch.")
            return false
        }

        val alias = CrashScope.getAlias(packageName) ?: return false
        updateCrashProp(alias)
        Log.e(TAG, "[SAFE MODE ENGAGED] Auto-tripped safe mode for $packageName (alias: $alias) due to repeated crashes!")

        return try {
            val intent = Intent().apply {
                component = ComponentName("dev.hyperios.app", "dev.joseph.hyperbounce.safemode.CrashActivity")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("key_pkg", alias)
                putExtra("key_real_pkg", packageName)
                if (shortMsg != null) putExtra("key_shortMsg", shortMsg)
                if (stackTrace != null) putExtra("key_stackTrace", stackTrace)
                if (crashInfo != null) {
                    putExtra("key_throwClassName", crashInfo.throwClassName)
                    putExtra("key_throwFileName", crashInfo.throwFileName)
                    putExtra("key_throwLineNumber", crashInfo.throwLineNumber)
                    putExtra("key_throwMethodName", crashInfo.throwMethodName)
                }
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to start CrashActivity from SafeModeHandler", t)
            true
        }
    }
}
