package dev.joseph.hyperbounce.ui.state

/*
 * These shapes mirror real parameters already exposed by the backend:
 *  - NavBarAppSettings fields map 1:1 to LiquidNavigationBar's own parameters
 *    (effectMultiplier, containerAlpha, cornerRadius, barHeight, tabWidth,
 *    forceLightTheme/forceDarkTheme, customAccentColor) — see nav/LiquidNavigationBar.kt.
 *  - GlassFeel fields map to the per-press blur()/lens()/highlight/shadow formulas and
 *    DampedDragAnimation spring parameters inside toggles/glass/LiquidToggle.kt and
 *    toggles/glass/LiquidSlider.kt. The toggle engine already accepts custom spring
 *    values (see toggles/glass/DampedDragAnimation.kt); the slider engine does not yet —
 *    Phase 2 will parameterize it the same way.
 *
 * This is a UI-design-phase (Phase 1) model: values here are read/written to
 * SharedPreferences by SettingsRepository, but nothing in the Xposed hooks reads these
 * keys yet. That wiring is Phase 2. Defaults below match the backend's current hardcoded
 * behavior exactly, so toggling nothing in the UI changes nothing once it is wired up.
 */

enum class ThemeMode { System, Light, Dark }

enum class AccentPreset(val label: String, val argb: Long?) {
    Auto("Automatic", null),
    Blue("Blue", 0xFF0088FFL),
    Pink("Pink", 0xFFFF2D78L),
    Purple("Purple", 0xFFAF52DEL),
    Orange("Orange", 0xFFFF9500L),
    Green("Green", 0xFF34C759L)
}

enum class RenderEngine(val label: String) {
    Native("Native Canvas"),
    LiquidGlass("Liquid Glass")
}

enum class NavApp(val displayName: String, val packageName: String) {
    Instagram("Instagram", "com.instagram.android"),
    WhatsApp("WhatsApp", "com.whatsapp"),
    YouTube("YouTube", "com.google.android.youtube")
}

/** One app's floating nav bar settings. Stored independently per [NavApp] — changing
 *  WhatsApp's values never touches Instagram's or YouTube's stored state. */
data class NavBarAppSettings(
    val enabled: Boolean = true,
    val glassIntensity: Float = 1.0f,
    val containerOpacity: Float = 0.4f,
    val cornerRadius: Float = 32f,
    val barHeight: Float = 64f,
    val tabWidth: Float = 64f,
    val themeMode: ThemeMode = ThemeMode.System,
    val accent: AccentPreset = AccentPreset.Auto
)

/** The blur/lens/highlight/shadow/spring "feel" of one glass control (toggle or slider). */
data class GlassFeel(
    val blurDp: Float,
    val lensSizeDp: Float,
    val lensDepthDp: Float,
    val highlightStrength: Float,
    val shadowRadiusDp: Float,
    val dragSensitivity: Float,
    val springDamping: Float,
    val springStiffness: Float
)

data class EngineSettings(
    val toggleEngine: RenderEngine = RenderEngine.LiquidGlass,
    val sliderEngine: RenderEngine = RenderEngine.LiquidGlass,
    val keypadEnabled: Boolean = true,
    val toggleGlass: GlassFeel = GlassFeel(
        blurDp = 8f, lensSizeDp = 5f, lensDepthDp = 10f,
        highlightStrength = 0.67f, shadowRadiusDp = 4f,
        dragSensitivity = 0.72f, springDamping = 0.82f, springStiffness = 400f
    ),
    val sliderGlass: GlassFeel = GlassFeel(
        blurDp = 8f, lensSizeDp = 10f, lensDepthDp = 14f,
        highlightStrength = 0.67f, shadowRadiusDp = 4f,
        dragSensitivity = 1.0f, springDamping = 0.7f, springStiffness = 800f
    )
)

/**
 * The one master switch plus the shared "how glassy is the chrome" parameters — applies to
 * every structural glass surface in the settings app itself (back buttons, cards, the
 * expandable menu, segmented pickers, the nav-bar preview). Deliberately mirrors the exact
 * control surface of the user-authored ExpandableGlassMenu catalog page (glass enabled,
 * blur radius, refraction height/amount, chromatic aberration, haptics) rather than
 * inventing a broader set.
 *
 * Does NOT apply to LiquidToggle/LiquidSlider anywhere (this app's own on/off switches and
 * sliders, or the HyperOS toggle/slider engine on the Engine screen) — those always render
 * with full glass regardless of this switch, since a translucent-flat interactive control
 * mid-drag would be confusing. When [enabled] is false, everything else falls back to a
 * flat translucent fill instead of real backdrop blur/refraction.
 */
data class GlobalGlassSettings(
    val enabled: Boolean = true,
    val blurDp: Float = 6f,
    val refractionHeightDp: Float = 10f,
    val refractionAmountDp: Float = 16f,
    val chromaticAberration: Boolean = false,
    val hapticsEnabled: Boolean = true
)
