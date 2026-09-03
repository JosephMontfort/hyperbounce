package dev.joseph.hyperbounce.safemode

import android.content.SharedPreferences
import android.util.Log

/**
 * Hyperceiler-compatible Safe Mode Handler for HyperBounce.
 * Provides real-time crash monitoring, automatic safe mode triggering, and manual toggles.
 */
object SafeModeHandler {
    private const val TAG = "HyperBounce_SafeMode"

    private var watchdogInstalled = false

    private fun getStorageFiles(name: String): List<java.io.File> {
        return listOf(
            java.io.File("/data/system/$name"),
            java.io.File("/data/local/tmp/$name")
        )
    }

    private fun readPersistentFlag(name: String): Boolean {
        for (f in getStorageFiles(name)) {
            try {
                if (f.exists() && f.length() > 0) return true
            } catch (_: Throwable) {}
        }
        return false
    }

    private fun writePersistentFlag(name: String, content: String) {
        for (f in getStorageFiles(name)) {
            try {
                f.parentFile?.mkdirs()
                f.writeText(content)
                f.setReadable(true, false)
                f.setWritable(true, false)
            } catch (_: Throwable) {}
        }
    }

    private fun deletePersistentFlag(name: String) {
        for (f in getStorageFiles(name)) {
            try {
                if (f.exists()) f.delete()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Checks if a package is in Safe Mode via either system properties, flag files, preferences, or global kill switch.
     */
    fun isSafeMode(packageName: String, prefs: SharedPreferences? = null): Boolean {
        val alias = CrashScope.getAlias(packageName) ?: packageName

        // 1. Check persistent flag files (reliable even if setprop is blocked by SELinux)
        if (readPersistentFlag("hb_safemode_${alias.lowercase()}.flag") || readPersistentFlag("hb_safemode_all.flag")) {
            Log.w(TAG, "[SAFE MODE ACTIVE] Package $packageName ($alias) is blocked by safe mode flag file")
            return true
        }

        // 2. Check System Property (fastest, persists across reboots/crashes)
        if (CrashScope.isPackageInSafeModeProp(packageName)) {
            Log.w(TAG, "[SAFE MODE ACTIVE] Package $packageName is blocked by system property ${CrashScope.PROP_SAFE_MODE}")
            return true
        }

        // 3. Check Preferences if available
        if (prefs != null) {
            val globalSafe = prefs.getBoolean("prefs_key_global_safe_mode", false)
            if (globalSafe) {
                Log.w(TAG, "[SAFE MODE ACTIVE] Global Safe Mode is active in preferences")
                return true
            }

            val key = "prefs_key_${alias}_safe_mode_enable"
            if (prefs.getBoolean(key, false)) {
                Log.w(TAG, "[SAFE MODE ACTIVE] Alias $alias ($packageName) is disabled in preferences ($key)")
                return true
            }
        }

        return false
    }

    /**
     * Tracks PID restarts within a 60-second sliding window.
     * If a package (like SystemUI or its plugins) changes PID 3 times within 60 seconds,
     * Safe Mode is automatically engaged.
     */
    fun checkAndTrackPidRestart(packageName: String): Boolean {
        val alias = CrashScope.getAlias(packageName) ?: packageName
        val currentPid = android.os.Process.myPid()
        val now = System.currentTimeMillis()
        val fileName = "hb_pids_${alias.lowercase()}.txt"

        val history = mutableListOf<Pair<Long, Int>>()

        // 1. Read existing PID history
        for (f in getStorageFiles(fileName)) {
            try {
                if (f.exists()) {
                    f.readLines().forEach { line ->
                        val parts = line.trim().split(":")
                        if (parts.size == 2) {
                            val time = parts[0].toLongOrNull()
                            val pid = parts[1].toIntOrNull()
                            if (time != null && pid != null) {
                                history.add(Pair(time, pid))
                            }
                        }
                    }
                    if (history.isNotEmpty()) break
                }
            } catch (_: Throwable) {}
        }

        // 2. Filter out entries older than 60 seconds
        val recent = history.filter { now - it.first in 0..60_000L }.toMutableList()

        // 3. Add current PID if not already the latest
        if (recent.none { it.second == currentPid }) {
            recent.add(Pair(now, currentPid))
        }

        // 4. Save updated history
        val serialized = recent.joinToString("\n") { "${it.first}:${it.second}" }
        writePersistentFlag(fileName, serialized)

        // 5. Count distinct PIDs in the last 60 seconds
        val distinctPids = recent.map { it.second }.distinct()
        if (distinctPids.size >= 3) {
            Log.e(TAG, "[SAFE MODE TRIPPED] $packageName ($alias) changed PID 3 times within 60s (PIDs: $distinctPids)! Engaging Safe Mode.")
            updateCrashProp(alias)
            writePersistentFlag("hb_safemode_${alias.lowercase()}.flag", "tripped_at_$now")
            return true
        }

        return false
    }

    /**
     * Updates the system property and flag files to activate safe mode for a specific alias.
     */
    fun updateCrashProp(alias: String) {
        val current = CrashScope.getCrashingAliases().toMutableSet()
        current.add(alias.lowercase())
        applySafeModeProp(current)
        writePersistentFlag("hb_safemode_${alias.lowercase()}.flag", "1")
    }

    /**
     * Removes an alias from the safe mode system property and flag files.
     */
    fun removeCrashProp(alias: String) {
        val current = CrashScope.getCrashingAliases().toMutableSet()
        current.remove(alias.lowercase())
        applySafeModeProp(current)
        deletePersistentFlag("hb_safemode_${alias.lowercase()}.flag")
    }

    /**
     * Clears all safe modes in the system property and flag files.
     */
    fun clearAllSafeModes() {
        applySafeModeProp(emptySet())
        deletePersistentFlag("hb_safemode_systemui.flag")
        deletePersistentFlag("hb_safemode_home.flag")
        deletePersistentFlag("hb_safemode_all.flag")
        deletePersistentFlag("hb_pids_systemui.txt")
        deletePersistentFlag("hb_pids_home.txt")
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
     * Installs an UncaughtExceptionHandler in the hooked process.
     * Automatically engages Safe Mode immediately upon crash detection to prevent bootloops.
     */
    fun installCrashWatchdog(packageName: String) {
        if (watchdogInstalled) return
        watchdogInstalled = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val alias = CrashScope.getAlias(packageName) ?: packageName
                Log.e(TAG, "[CRASH DETECTED] Process $packageName ($alias) crashed! Engaging Safe Mode immediately.", throwable)
                updateCrashProp(alias)
            } catch (t: Throwable) {
                Log.e(TAG, "Error in crash watchdog", t)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
