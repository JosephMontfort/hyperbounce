package dev.joseph.hyperbounce.safemode

import android.util.Log

/**
 * Helper to safely access hidden android.os.SystemProperties via reflection without SDK hidden API compile errors.
 */
object SystemPropertiesHelper {
    private val getMethod by lazy {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            clazz.getDeclaredMethod("get", String::class.java, String::class.java)
        } catch (_: Throwable) {
            null
        }
    }

    private val setMethod by lazy {
        try {
            val clazz = Class.forName("android.os.SystemProperties")
            clazz.getDeclaredMethod("set", String::class.java, String::class.java)
        } catch (_: Throwable) {
            null
        }
    }

    fun get(key: String, def: String = ""): String {
        return try {
            (getMethod?.invoke(null, key, def) as? String) ?: def
        } catch (_: Throwable) {
            def
        }
    }

    fun set(key: String, value: String) {
        try {
            setMethod?.invoke(null, key, value)
        } catch (_: Throwable) {
            try {
                Runtime.getRuntime().exec(arrayOf("su", "-c", "setprop $key \"$value\""))
            } catch (_: Throwable) {}
        }
    }
}

/**
 * Maps packages to crash aliases and manages the system property `persist.hyperbounce.safemode`.
 * Follows the HyperCeiler safe mode design.
 */
object CrashScope {
    private const val TAG = "HyperBounce_CrashScope"
    const val PROP_SAFE_MODE = "persist.hyperbounce.safemode"
    const val CRASH_RECORD_KEY = "hyperbounce_crash_record_data"

    // Primary system scope apps
    private val packageToAliasMap = mapOf(
        "com.android.systemui" to "systemui",
        "miui.systemui.plugin" to "systemui",
        "com.miui.home" to "home",
        "com.mi.android.globallauncher" to "home",
        "com.android.settings" to "settings",
        "com.xiaomi.misettings" to "settings"
    )

    private val aliasToPrimaryPackageMap = mapOf(
        "systemui" to "com.android.systemui",
        "home" to "com.miui.home",
        "settings" to "com.android.settings"
    )

    private val safeModeConfigMap = mapOf(
        "com.android.systemui" to "prefs_key_systemui_safe_mode_enable",
        "miui.systemui.plugin" to "prefs_key_systemui_safe_mode_enable",
        "com.miui.home" to "prefs_key_home_safe_mode_enable",
        "com.mi.android.globallauncher" to "prefs_key_home_safe_mode_enable",
        "com.android.settings" to "prefs_key_system_settings_safe_mode_enable",
        "com.xiaomi.misettings" to "prefs_key_system_settings_safe_mode_enable"
    )

    fun isScopeApp(packageName: String): Boolean {
        return packageToAliasMap.containsKey(packageName)
    }

    fun getAlias(packageName: String): String? {
        return packageToAliasMap[packageName]
    }

    fun getPackageName(alias: String): String? {
        return aliasToPrimaryPackageMap[alias]
    }

    fun getSafeModeConfigKey(packageName: String): String? {
        return safeModeConfigMap[packageName]
    }

    /**
     * Returns the set of active crashing aliases from the system property `persist.hyperbounce.safemode`.
     */
    fun getCrashingAliases(): Set<String> {
        val raw = try {
            SystemPropertiesHelper.get(PROP_SAFE_MODE, "")
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read system property $PROP_SAFE_MODE", t)
            ""
        }
        if (raw.isBlank()) return emptySet()
        return raw.split(",")
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun isAliasInSafeModeProp(alias: String): Boolean {
        val aliases = getCrashingAliases()
        return aliases.contains("all") || aliases.contains(alias.lowercase())
    }

    fun isPackageInSafeModeProp(packageName: String): Boolean {
        val aliases = getCrashingAliases()
        if (aliases.contains("all")) return true
        val alias = getAlias(packageName) ?: return aliases.contains(packageName.lowercase())
        return aliases.contains(alias.lowercase())
    }
}
