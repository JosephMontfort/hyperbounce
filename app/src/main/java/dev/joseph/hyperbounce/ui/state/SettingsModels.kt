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
    Green("Green", 0xFF34C759L),
    Custom("Custom", null)
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
    val liquidGlass: Boolean = true,
    val glassIntensity: Float = 1.0f,
    val containerOpacity: Float = 0.4f,
    val cornerRadius: Float = 32f,
    val barHeight: Float = 64f,
    val tabWidth: Float = 64f,
    val bottomMargin: Float = 40f,
    val themeMode: ThemeMode = ThemeMode.System,
    val accent: AccentPreset = AccentPreset.Auto,
    val customAccentColor: Long = 0xFF0088FFL,
    val pillGlassIntensity: Float = 1.0f,
    val pillContainerOpacity: Float = 1.0f,
    val pillChromaticAberration: Boolean = true,
    val pillShadow: Boolean = true,
    val autoShrink: Boolean = true
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

/** Feature 1: Lock Screen Sheet & 4-Sided AGSL Liquid Glass Refraction Settings */
data class LockScreenSheetSettings(
    val enabled: Boolean = true,
    val borderWidth: Float = 2.5f,
    val borderAlpha: Float = 0.47f,
    val refractionHeight: Float = 24.0f,
    val refractionAmount: Float = 32.0f,
    val depthEffect: Float = 0.12f,
    val pillWidth: Float = 156.0f,
    val pillHeight: Float = 6.0f,
    val pillBottomMargin: Float = 9.0f,
    val lipHeight: Float = 28.0f
)

/** Feature 2: Fluid iOS PIN Pad Geometry & Jelly Squish Engine Settings */
data class LockScreenKeypadSettings(
    val enabled: Boolean = true,
    val circleDiameter: Float = 82.0f,
    val fillAlpha: Float = 0.20f,
    val strokeWidth: Float = 1.5f,
    val strokeAlpha: Float = 0.47f,
    val showKlondike: Boolean = false,
    val digitTextSize: Float = 36.0f,
    val row0Offset: Float = -115.0f,
    val row1Offset: Float = -90.0f,
    val row2Offset: Float = -60.0f,
    val row3Offset: Float = -30.0f,
    val tapSquishScale: Float = 0.88f,
    val releasePlunge: Float = 0.10f,
    val jellyMaxStretch: Float = 0.16f,
    val jellyElasticLimit: Float = 0.40f,
    val springStiffness: Float = 50.0f,
    val springDamping: Float = 5.5f,
    val pressGlowAlpha: Float = 0.35f,
    val glowRadius: Float = 1.4f,
    val entryTranslationY: Float = 90.0f,
    val entryInitialScale: Float = 0.50f,
    val entryDuration: Float = 420.0f,
    val rowDelayStep: Float = 55.0f,
    val colDelayStep: Float = 25.0f,
    val entryOvershoot: Float = 1.8f,
    val delayKey1: Float = -1f,
    val delayKey2: Float = -1f,
    val delayKey3: Float = -1f,
    val delayKey4: Float = -1f,
    val delayKey5: Float = -1f,
    val delayKey6: Float = -1f,
    val delayKey7: Float = -1f,
    val delayKey8: Float = -1f,
    val delayKey9: Float = -1f,
    val delayKey0: Float = -1f,
    val delayEmergency: Float = -1f,
    val delayCancel: Float = -1f
)

/** Feature 3: Screen Unlock Velocity Kinetics Settings */
data class ScreenUnlockSettings(
    val enabled: Boolean = true,
    val animMode: Int = 1,
    val gridColumns: Int = 4,
    val gridRows: Int = 7,
    val dockDamping: Float = 0.70f,
    val dockResponse: Float = 0.40f,
    val centerResponse: Float = 0.48f,
    val centerDamping: Float = 0.58f,
    val edgeResponseDelay: Float = 0.22f,
    val edgeDampingDrop: Float = 0.10f,
    val zDepthMultiplier: Float = 1.60f,
    val minResponse: Float = 0.45f,
    val maxResponse: Float = 0.75f,
    val minDamping: Float = 0.42f,
    val maxDamping: Float = 0.65f,
    val durationScale: Float = 1.0f,
    val velocityInfluence: Float = 1.0f,
    val maxVelocity: Float = 16000.0f,
    val cascadeDamping: Float = 0.95f,
    val kineticDockDamping: Float = 0.85f,
    val kineticDockResponse: Float = 0.38f,
    val kineticDockZDepth: Float = 800.0f
)

/** Feature 4: Launcher App Open / Close Spring Physics Settings */
data class AppTransitionSettings(
    val enabled: Boolean = true,
    val posDamping: Float = 0.80f,
    val posResponse: Float = 0.40f,
    val scaleDamping: Float = 0.80f,
    val scaleResponse: Float = 0.40f,
    val cornerDamping: Float = 1.00f,
    val cornerResponse: Float = 0.35f,
    val alphaDamping: Float = 1.00f,
    val alphaResponse: Float = 0.30f,
    val appYArc: Float = 0.0f,
    val appBottomZWarp: Float = 0.0f,
    val appXSway: Float = 0.0f,
    val appInterruptSmooth: Float = 1.0f,
    val appIconPitchDeg: Float = 0.0f,
    val appIconCameraDist: Float = 8000.0f
)

/** Features 5, 6, 7, 8, 9: Control Center Modular Customization, Jelly Physics & Dynamics */
data class ControlCenterSettings(
    val modularEditEnabled: Boolean = true,
    val restartOnEditExit: Boolean = true,
    val animEnabled: Boolean = true,
    val jellyEnabled: Boolean = true,
    val lowEndAnim: Boolean = false,
    val entryInitialScale: Float = 0.50f,
    val entryTranslationY: Float = 120.0f,
    val waterfallEnabled: Boolean = true,
    val waterfallDelayStep: Float = 0.04f,
    val spreadRatio: Float = 0.02f,
    val spreadResponse: Float = 0.85f,
    val spreadDamping: Float = 0.85f,
    val spreadReleaseResponse: Float = 0.70f,
    val spreadReleaseDamping: Float = 0.90f,
    val tileShowResponse: Float = 0.85f,
    val tileShowDamping: Float = 0.85f,
    val tileHideResponse: Float = 0.80f,
    val tileHideDamping: Float = 0.90f,
    val tileShrinkResponse: Float = 0.85f,
    val tileShrinkDamping: Float = 0.85f,
    val tileDelayScale: Float = 60.0f,
    val blurResponse: Float = 0.90f,
    val blurDamping: Float = 0.88f,
    val frictionMultiplier: Float = 0.40f
)

/** Feature 10: Dropdown Menu to Dialog Fluid Morph Physics */
data class MorphPhysicsSettings(
    val morphSpeedMultiplier: Float = 0.78f,
    val circleCollapseRatio: Float = 0.35f,
    val circleDiameterDp: Float = 44.0f,
    val zPlungeDepth: Float = 0.50f,
    val gravitySwoopDp: Float = 60.0f,
    val yBounceAmountDp: Float = 0.0f,
    val jellyOvershoot: Float = 0.0f,
    val contentStretch: Float = 0.0f,
    val springDamping: Float = 0.70f,
    val springStiffness: Float = 220.0f,
    val lensSurgeDp: Float = 20.0f,
    val motionBlurSurgeDp: Float = 30.0f
)

