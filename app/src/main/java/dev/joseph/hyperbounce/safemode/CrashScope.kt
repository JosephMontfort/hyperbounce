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
 * Follows the Hyperceiler safe mode design.
 */
object CrashScope {
    private const val TAG = "HyperBounce_CrashScope"
    const val PROP_SAFE_MODE = "persist.hyperbounce.safemode"

    private val packageToAliasMap = mapOf(
        "com.android.systemui" to "systemui",
        "miui.systemui.plugin" to "systemui",
        "com.miui.home" to "home",
        "com.android.settings" to "settings",
        "com.instagram.android" to "instagram",
        "com.whatsapp" to "whatsapp",
        "com.google.android.youtube" to "youtube",
        "com.android.incallui" to "incallui",
        "android" to "framework"
    )

    private val aliasToPackageMap = mapOf(
        "systemui" to "com.android.systemui",
        "home" to "com.miui.home",
        "settings" to "com.android.settings",
        "instagram" to "com.instagram.android",
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "incallui" to "com.android.incallui",
        "framework" to "android"
    )

    fun getAlias(packageName: String): String? {
        if (packageToAliasMap.containsKey(packageName)) {
            return packageToAliasMap[packageName]
        }
        for ((key, value) in packageToAliasMap) {
            if (packageName.contains(value)) {
                return value
            }
        }
        return null
    }

    fun getPackageName(alias: String): String? = aliasToPackageMap[alias]

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
        if (raw.isNullOrBlank()) return emptySet()
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
