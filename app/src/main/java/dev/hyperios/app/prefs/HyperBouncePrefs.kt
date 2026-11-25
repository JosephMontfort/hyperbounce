package dev.hyperios.app.prefs

/*
 * Reads the settings the companion app (dev.hyperios.app.ui.state.SettingsRepository)
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
import dev.hyperios.app.common.ModuleDefaults
import io.github.libxposed.api.XposedInterface

const val PREFS_GROUP = "hyperbounce_settings"

class HyperBouncePrefs(module: XposedInterface) {

    val prefs: SharedPreferences = module.getRemotePreferences(PREFS_GROUP)

    // --- module-wide ----------------------------------------------------------------------

    val hotReloadEnabled: Boolean
        get() = prefs.getBoolean("hot_reload_enabled", false)

    val callerScreenEnabled: Boolean
        get() = prefs.getBoolean("caller_screen_enabled", ModuleDefaults.MasterToggles.CALLER_SCREEN_ENABLED)

    val callerButtonSize: Float
        get() = prefs.getFloat("caller_button_size", ModuleDefaults.CallerScreen.BUTTON_SIZE)

    val callerBottomOffset: Float
        get() = prefs.getFloat("caller_bottom_offset", ModuleDefaults.CallerScreen.BOTTOM_OFFSET)

    val dialerRecentsSwipeEnabled: Boolean
        get() = prefs.getBoolean("dialer_recents_swipe_enabled", ModuleDefaults.MasterToggles.DIALER_RECENTS_SWIPE_ENABLED)

    val dialerHapticsEnabled: Boolean
        get() = prefs.getBoolean("dialer_haptics_enabled", true)

    // --- per-app nav bar (appKey: "instagram" | "whatsapp" | "youtube") --------------------

    fun appEnabled(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_enabled", false)

    fun appLiquidGlass(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_liquid_glass", true)

    fun appGlassIntensity(appKey: String): Float =
        prefs.getFloat("app_${appKey}_glass_intensity", 1.0f)

    fun appBarBlurIntensity(appKey: String): Float =
        prefs.getFloat("app_${appKey}_bar_blur_intensity", 1.0f)

    fun appContainerOpacity(appKey: String): Float =
        prefs.getFloat("app_${appKey}_container_opacity", if (!appLiquidGlass(appKey)) 0.90f else 0.4f)

    fun appCornerRadius(appKey: String): Float =
        prefs.getFloat("app_${appKey}_corner_radius", when (appKey) {
            "whatsapp" -> ModuleDefaults.NavApps.WhatsApp.CORNER_RADIUS
            "facebook" -> ModuleDefaults.NavApps.Facebook.CORNER_RADIUS
            "tiktok" -> ModuleDefaults.NavApps.TikTok.CORNER_RADIUS
            else -> ModuleDefaults.NavApps.Instagram.CORNER_RADIUS
        })

    fun appBarHeight(appKey: String): Float =
        prefs.getFloat("app_${appKey}_bar_height", when (appKey) {
            "whatsapp" -> ModuleDefaults.NavApps.WhatsApp.BAR_HEIGHT
            "facebook" -> ModuleDefaults.NavApps.Facebook.BAR_HEIGHT
            "tiktok" -> ModuleDefaults.NavApps.TikTok.BAR_HEIGHT
            else -> 50f
        })

    fun appTabWidth(appKey: String): Float =
        prefs.getFloat("app_${appKey}_tab_width", when (appKey) {
            "whatsapp" -> ModuleDefaults.NavApps.WhatsApp.TAB_WIDTH
            "instagram" -> ModuleDefaults.NavApps.Instagram.TAB_WIDTH
            "youtube" -> ModuleDefaults.NavApps.YouTube.TAB_WIDTH
            "facebook" -> ModuleDefaults.NavApps.Facebook.TAB_WIDTH
            "tiktok" -> ModuleDefaults.NavApps.TikTok.TAB_WIDTH
            else -> 75f
        })

    fun appBottomMargin(appKey: String): Float =
        prefs.getFloat("app_${appKey}_bottom_margin", when (appKey) {
            "whatsapp" -> ModuleDefaults.NavApps.WhatsApp.BOTTOM_MARGIN
            "facebook" -> ModuleDefaults.NavApps.Facebook.BOTTOM_MARGIN
            "tiktok" -> ModuleDefaults.NavApps.TikTok.BOTTOM_MARGIN
            else -> ModuleDefaults.NavApps.Instagram.BOTTOM_MARGIN
        })

    /** One of "System" | "Light" | "Dark" — matches ui.state.ThemeMode's enum names. */
    fun appThemeMode(appKey: String): String =
        prefs.getString("app_${appKey}_theme_mode", "System") ?: "System"

    /** One of "Auto" | "Blue" | "Pink" | "Purple" | "Orange" | "Green" | "Custom" — matches
     *  ui.state.AccentPreset's enum names. */
    fun appAccent(appKey: String): String =
        prefs.getString("app_${appKey}_accent", when (appKey) {
            "whatsapp" -> ModuleDefaults.NavApps.WhatsApp.ACCENT
            "facebook" -> ModuleDefaults.NavApps.Facebook.ACCENT
            else -> "Auto"
        }) ?: "Auto"

    fun appCustomAccentColor(appKey: String): Long =
        prefs.getLong("app_${appKey}_custom_accent_color", when (appKey) {
            "facebook" -> ModuleDefaults.NavApps.Facebook.CUSTOM_ACCENT_COLOR
            "tiktok" -> ModuleDefaults.NavApps.TikTok.CUSTOM_ACCENT_COLOR.toLong() and 0xFFFFFFFFL
            else -> 0xFF0088FFL
        })

    fun appPillGlassIntensity(appKey: String): Float =
        prefs.getFloat("app_${appKey}_pill_glass_intensity", 1.0f)

    fun appPillContainerOpacity(appKey: String): Float =
        prefs.getFloat("app_${appKey}_pill_container_opacity", 1.0f)

    fun appPillChromaticAberration(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_pill_chromatic_aberration", true)

    fun appPillShadow(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_pill_shadow", false)

    fun appAutoShrink(appKey: String): Boolean =
        prefs.getBoolean("app_${appKey}_auto_shrink", when (appKey) {
            "whatsapp", "facebook" -> false
            else -> true
        })

    fun appAutoShrinkScale(appKey: String): Float =
        prefs.getFloat("app_${appKey}_auto_shrink_scale", when (appKey) {
            "whatsapp" -> ModuleDefaults.NavApps.WhatsApp.AUTO_SHRINK_SCALE
            "facebook" -> ModuleDefaults.NavApps.Facebook.AUTO_SHRINK_SCALE
            "tiktok" -> ModuleDefaults.NavApps.TikTok.AUTO_SHRINK_SCALE
            else -> ModuleDefaults.NavApps.YouTube.AUTO_SHRINK_SCALE
        })

    val facebookReelsAutohide: Boolean
        get() = prefs.getBoolean("app_facebook_reels_autohide", ModuleDefaults.NavApps.Facebook.REELS_AUTOHIDE)

    val reelsFullscreenZoom: Boolean
        get() = prefs.getBoolean("app_instagram_reels_fullscreen_zoom", prefs.getBoolean("reels_fullscreen_zoom", ModuleDefaults.NavApps.Instagram.REELS_FULLSCREEN_ZOOM))

    // --- toggle/slider engine --------------------------------------------------------------

    val togglesSlidersHookEnabled: Boolean
        get() = prefs.getBoolean("toggles_sliders_hook_enabled", ModuleDefaults.MasterToggles.MASTER_HOOKS_ENABLED)

    val toggleEngine: String
        get() = prefs.getString("engine_toggle_render", ModuleDefaults.Engine.TOGGLE_ENGINE) ?: ModuleDefaults.Engine.TOGGLE_ENGINE

    val sliderEngine: String
        get() = prefs.getString("engine_slider_render", ModuleDefaults.Engine.SLIDER_ENGINE) ?: ModuleDefaults.Engine.SLIDER_ENGINE

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
        get() = prefs.getBoolean("engine_keypad_enabled", ModuleDefaults.MasterToggles.ENGINE_KEYPAD_ENABLED)

    val miuixAppsHookingEnabled: Boolean
        get() = prefs.getBoolean("miuix_apps_hooking_enabled", ModuleDefaults.MasterToggles.MIUIX_APPS_HOOKING_ENABLED)

    fun toggleAccentColor(): Long {
        val presetName = prefs.getString("toggle_accent", ModuleDefaults.Engine.TOGGLE_ACCENT) ?: ModuleDefaults.Engine.TOGGLE_ACCENT
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
        val presetName = prefs.getString("slider_accent", "Blue") ?: "Blue"
        val customColor = prefs.getLong("slider_custom_color", 0xFF30D158L)
        return when (presetName) {
            "Blue" -> 0xFF0088FFL
            "Pink" -> 0xFFFF2D78L
            "Purple" -> 0xFFAF52DEL
            "Orange" -> 0xFFFF9500L
            "Green" -> 0xFF30D158L
            "Custom" -> customColor
            else -> 0xFF0088FFL
        }
    }

    fun glassFeel(prefix: String): GlassFeelValues = GlassFeelValues(
        blurDp = prefs.getFloat("${prefix}_blur", if (prefix == "toggle_glass") ModuleDefaults.Engine.TOGGLE_GLASS_BLUR else ModuleDefaults.Engine.SLIDER_GLASS_BLUR),
        lensSizeDp = prefs.getFloat("${prefix}_lens_size", if (prefix == "toggle_glass") ModuleDefaults.Engine.TOGGLE_GLASS_LENS_SIZE else ModuleDefaults.Engine.SLIDER_GLASS_LENS_SIZE),
        lensDepthDp = prefs.getFloat("${prefix}_lens_depth", if (prefix == "toggle_glass") ModuleDefaults.Engine.TOGGLE_GLASS_LENS_DEPTH else ModuleDefaults.Engine.SLIDER_GLASS_LENS_DEPTH),
        highlightStrength = prefs.getFloat("${prefix}_highlight", if (prefix == "toggle_glass") ModuleDefaults.Engine.TOGGLE_GLASS_HIGHLIGHT else ModuleDefaults.Engine.SLIDER_GLASS_HIGHLIGHT),
        shadowRadiusDp = prefs.getFloat("${prefix}_shadow", if (prefix == "toggle_glass") ModuleDefaults.Engine.TOGGLE_GLASS_SHADOW else ModuleDefaults.Engine.SLIDER_GLASS_SHADOW),
        dragSensitivity = prefs.getFloat("${prefix}_drag_sensitivity", if (prefix == "toggle_glass") ModuleDefaults.Engine.TOGGLE_GLASS_DRAG_SENSITIVITY else ModuleDefaults.Engine.SLIDER_GLASS_DRAG_SENSITIVITY),
        springDamping = prefs.getFloat("${prefix}_spring_damping", if (prefix == "toggle_glass") ModuleDefaults.Engine.TOGGLE_GLASS_SPRING_DAMPING else ModuleDefaults.Engine.SLIDER_GLASS_SPRING_DAMPING),
        springStiffness = prefs.getFloat("${prefix}_spring_stiffness", if (prefix == "toggle_glass") ModuleDefaults.Engine.TOGGLE_GLASS_SPRING_STIFFNESS else ModuleDefaults.Engine.SLIDER_GLASS_SPRING_STIFFNESS)
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
