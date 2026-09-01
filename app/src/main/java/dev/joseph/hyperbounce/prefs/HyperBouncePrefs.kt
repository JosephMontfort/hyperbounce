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

    // --- per-app nav bar (appKey: "instagram" | "whatsapp" | "youtube") --------------------

    fun appEnabled(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_enabled", true)

    fun appBarGlassEnabled(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_bar_glass_enabled", true)

    fun appPillGlassEnabled(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_pill_glass_enabled", true)

    fun appReflectionLayerEnabled(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_reflection_layer_enabled", true)

    fun appGlassIntensity(appKey: String): Float =
        prefs.getFloat("app_${appKey}_glass_intensity", 1.0f)

    fun appBarBlurDp(appKey: String): Float =
        prefs.getFloat("app_${appKey}_bar_blur_dp", 8f)

    fun appBarLensAmount(appKey: String): Float =
        prefs.getFloat("app_${appKey}_bar_lens_amount", 18f)

    fun appPillLensAmount(appKey: String): Float =
        prefs.getFloat("app_${appKey}_pill_lens_amount", 11f)

    fun appPillHighlight(appKey: String): Float =
        prefs.getFloat("app_${appKey}_pill_highlight", 0.8f)

    fun appPillShadow(appKey: String): Float =
        prefs.getFloat("app_${appKey}_pill_shadow", 0.6f)

    fun appContainerOpacity(appKey: String): Float =
        prefs.getFloat("app_${appKey}_container_opacity", 0.4f)

    fun appPillOpacity(appKey: String): Float =
        prefs.getFloat("app_${appKey}_pill_opacity", 0.1f)

    fun appCornerRadius(appKey: String): Float =
        prefs.getFloat("app_${appKey}_corner_radius", if (appKey == "whatsapp") 36f else 32f)

    fun appBarHeight(appKey: String): Float =
        prefs.getFloat("app_${appKey}_bar_height", if (appKey == "whatsapp") 70f else 64f)

    fun appTabWidth(appKey: String): Float =
        prefs.getFloat("app_${appKey}_tab_width", if (appKey == "whatsapp") 92f else 64f)

    /** One of "System" | "Light" | "Dark" — matches ui.state.ThemeMode's enum names. */
    fun appThemeMode(appKey: String): String =
        prefs.getString("app_${appKey}_theme_mode", "System") ?: "System"

    /** One of "Auto" | "Blue" | "Pink" | "Purple" | "Orange" | "Green" — matches
     *  ui.state.AccentPreset's enum names. */
    fun appAccent(appKey: String): String =
        prefs.getString("app_${appKey}_accent", "Auto") ?: "Auto"

    // --- toggle/slider engine --------------------------------------------------------------

    /** true = Liquid Glass engine, false = the original native Canvas/AGSL engine. */
    val toggleEngineIsLiquidGlass: Boolean
        get() = prefs.getString("engine_toggle_render", "LiquidGlass") != "Native"

    val sliderEngineIsLiquidGlass: Boolean
        get() = prefs.getString("engine_slider_render", "LiquidGlass") != "Native"

    val keypadEnabled: Boolean
        get() = prefs.getBoolean("engine_keypad_enabled", true)

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
