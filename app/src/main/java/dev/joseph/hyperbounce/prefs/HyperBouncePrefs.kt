package dev.joseph.hyperbounce.prefs

/*
 * Reads the settings the companion app (dev.joseph.hyperbounce.ui.state.SettingsRepository)
 * writes, from inside a HOOKED app's process (Instagram, WhatsApp, YouTube, MIUI system
 * apps). Every key name and default value here must exactly match SettingsRepository.kt —
 * they're two independent readers/writers of the same schema, not a shared class, since
 * one runs compileOnly against the libxposed API and the other runs as a normal app.
 *
 * Cross-process access: this module targets the "Modern Xposed API" (io.github.libxposed),
 * not the classic de.robv.android.xposed.XSharedPreferences mechanism (which is a Legacy-
 * API-only compatibility path and isn't available to a module built purely against
 * io.github.libxposed.api). The modern API's own equivalent is "remote preferences":
 *   - Hook-side (here): XposedInterface/XposedModule.getRemotePreferences(group) returns a
 *     READ-ONLY SharedPreferences backed by the LSPosed framework's own storage.
 *   - App-side (see ui/state/XposedServiceBridge.kt): the companion app registers for an
 *     XposedService via XposedServiceHelper.registerListener(...), and once bound, that
 *     service's own getRemotePreferences(group) returns a WRITABLE SharedPreferences for
 *     the exact same group/keys.
 * Both sides use PREFS_GROUP as the group name, matching the local SharedPreferences file
 * name the companion app also keeps for its own immediate UI reactivity (see
 * SettingsRepository.kt's write-through: every edit updates local prefs immediately, and
 * mirrors the same key into remote prefs so this reader sees it too).
 */

import android.content.SharedPreferences
import io.github.libxposed.api.XposedInterface

const val PREFS_GROUP = "hyperbounce_settings"

class HyperBouncePrefs(module: XposedInterface) {

    val prefs: SharedPreferences = module.getRemotePreferences(PREFS_GROUP)

    // --- module-wide ----------------------------------------------------------------------

    val moduleEnabled: Boolean
        get() = prefs.getBoolean("module_enabled", true)

    val callerScreenEnabled: Boolean
        get() = prefs.getBoolean("caller_screen_enabled", true)

    val callerButtonSize: Float
        get() = prefs.getFloat("caller_button_size", 76f)

    val callerBottomOffset: Float
        get() = prefs.getFloat("caller_bottom_offset", 68f)

    val callerDanceEnabled: Boolean
        get() = prefs.getBoolean("caller_dance_enabled", true)

    val dialerRecentsSwipeEnabled: Boolean
        get() = prefs.getBoolean("dialer_recents_swipe_enabled", true)

    val dialerCallThreshold: Float
        get() = prefs.getFloat("dialer_call_threshold", 96f)

    val dialerDeleteWidth: Float
        get() = prefs.getFloat("dialer_delete_width", 84f)

    val dialerConfirmDelete: Boolean
        get() = prefs.getBoolean("dialer_confirm_delete", true)

    val dialerHapticsEnabled: Boolean
        get() = prefs.getBoolean("dialer_haptics_enabled", true)

    // --- per-app nav bar (appKey: "instagram" | "whatsapp" | "youtube") --------------------

    fun appEnabled(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_enabled", true)

    fun appLiquidGlass(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_liquid_glass", true)

    fun appGlassIntensity(appKey: String): Float =
        prefs.getFloat("app_${appKey}_glass_intensity", 1.0f)

    fun appContainerOpacity(appKey: String): Float =
        prefs.getFloat("app_${appKey}_container_opacity", 0.4f)

    fun appCornerRadius(appKey: String): Float =
        prefs.getFloat("app_${appKey}_corner_radius", if (appKey == "whatsapp") 36f else 32f)

    fun appBarHeight(appKey: String): Float =
        prefs.getFloat("app_${appKey}_bar_height", if (appKey == "whatsapp") 70f else 64f)

    fun appTabWidth(appKey: String): Float =
        prefs.getFloat("app_${appKey}_tab_width", if (appKey == "whatsapp") 92f else 64f)

    fun appBottomMargin(appKey: String): Float =
        prefs.getFloat("app_${appKey}_bottom_margin", if (appKey == "youtube") 28f else 40f)

    /** One of "System" | "Light" | "Dark" — matches ui.state.ThemeMode's enum names. */
    fun appThemeMode(appKey: String): String =
        prefs.getString("app_${appKey}_theme_mode", "System") ?: "System"

    /** One of "Auto" | "Blue" | "Pink" | "Purple" | "Orange" | "Green" | "Custom" — matches
     *  ui.state.AccentPreset's enum names. */
    fun appAccent(appKey: String): String =
        prefs.getString("app_${appKey}_accent", "Auto") ?: "Auto"

    fun appCustomAccentColor(appKey: String): Long =
        prefs.getLong("app_${appKey}_custom_accent_color", 0xFF0088FFL)

    fun appPillGlassIntensity(appKey: String): Float =
        prefs.getFloat("app_${appKey}_pill_glass_intensity", 1.0f)

    fun appPillContainerOpacity(appKey: String): Float =
        prefs.getFloat("app_${appKey}_pill_container_opacity", 1.0f)

    fun appPillChromaticAberration(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_pill_chromatic_aberration", true)

    fun appPillShadow(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_pill_shadow", true)

    fun appAutoShrink(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_auto_shrink", true)

    fun appAutoShrinkScale(appKey: String): Float =
        prefs.getFloat("app_${appKey}_auto_shrink_scale", 0.70f)

    fun appAutoShrinkSensitivity(appKey: String): Float =
        prefs.getFloat("app_${appKey}_auto_shrink_sensitivity", 1.0f)

    val reelsFullscreenZoom: Boolean
        get() = prefs.getBoolean("app_instagram_reels_fullscreen_zoom", prefs.getBoolean("reels_fullscreen_zoom", false))

    // --- toggle/slider engine --------------------------------------------------------------

    val togglesSlidersHookEnabled: Boolean
        get() = prefs.getBoolean("toggles_sliders_hook_enabled", true)

    val toggleEngine: String
        get() = prefs.getString("engine_toggle_render", "LiquidGlass") ?: "LiquidGlass"

    val sliderEngine: String
        get() = prefs.getString("engine_slider_render", "LiquidGlass") ?: "LiquidGlass"

    val toggleEnabled: Boolean
        get() = togglesSlidersHookEnabled && toggleEngine != "Disabled"

    val sliderEnabled: Boolean
        get() = togglesSlidersHookEnabled && sliderEngine != "Disabled"

    /** true = Liquid Glass engine, false = the original native Canvas/AGSL engine. */
    val toggleEngineIsLiquidGlass: Boolean
        get() = toggleEngine == "LiquidGlass"

    val sliderEngineIsLiquidGlass: Boolean
        get() = sliderEngine == "LiquidGlass"

    val keypadEnabled: Boolean
        get() = prefs.getBoolean("engine_keypad_enabled", true)

    val miuixAppsHookingEnabled: Boolean
        get() = prefs.getBoolean("miuix_apps_hooking_enabled", false)

    fun toggleAccentColor(): Long {
        val presetName = prefs.getString("toggle_accent", "Green") ?: "Green"
        val customColor = prefs.getLong("toggle_custom_color", 0xFF30D158L)
        return when (presetName) {
            "Blue" -> 0xFF0088FFL
            "Pink" -> 0xFFFF2D78L
            "Purple" -> 0xFFAF52DEL
            "Orange" -> 0xFFFF9500L
            "Green" -> 0xFF30D158L
            "Custom" -> customColor
            else -> 0xFF30D158L
        }
    }

    fun sliderAccentColor(): Long {
        val presetName = prefs.getString("slider_accent", "Green") ?: "Green"
        val customColor = prefs.getLong("slider_custom_color", 0xFF30D158L)
        return when (presetName) {
            "Blue" -> 0xFF0088FFL
            "Pink" -> 0xFFFF2D78L
            "Purple" -> 0xFFAF52DEL
            "Orange" -> 0xFFFF9500L
            "Green" -> 0xFF30D158L
            "Custom" -> customColor
            else -> 0xFF30D158L
        }
    }

    fun glassFeel(prefix: String): GlassFeelValues = GlassFeelValues(
        blurDp = prefs.getFloat("${prefix}_blur", if (prefix == "toggle_glass") 8f else 8f),
        lensSizeDp = prefs.getFloat("${prefix}_lens_size", if (prefix == "toggle_glass") 5f else 10f),
        lensDepthDp = prefs.getFloat("${prefix}_lens_depth", if (prefix == "toggle_glass") 10f else 14f),
        highlightStrength = prefs.getFloat("${prefix}_highlight", 0.67f),
        shadowRadiusDp = prefs.getFloat("${prefix}_shadow", 4f),
        dragSensitivity = prefs.getFloat("${prefix}_drag_sensitivity", if (prefix == "toggle_glass") 0.72f else 1.0f),
        springDamping = prefs.getFloat("${prefix}_spring_damping", if (prefix == "toggle_glass") 0.82f else 0.7f),
        springStiffness = prefs.getFloat("${prefix}_spring_stiffness", if (prefix == "toggle_glass") 400f else 800f)
    )

    val toggleGlassFeel: GlassFeelValues get() = glassFeel("toggle_glass")
    val sliderGlassFeel: GlassFeelValues get() = glassFeel("slider_glass")
}

data class GlassFeelValues(
    val blurDp: Float,
    val lensSizeDp: Float,
    val lensDepthDp: Float,
    val highlightStrength: Float,
    val shadowRadiusDp: Float,
    val dragSensitivity: Float,
    val springDamping: Float,
    val springStiffness: Float
)
