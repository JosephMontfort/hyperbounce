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
    val autoShrink: Boolean = true,
    val autoShrinkScale: Float = 0.70f,
    val autoShrinkSensitivity: Float = 1.0f,
    val reelsFullscreenZoom: Boolean = false
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
    val miuixAppsHookingEnabled: Boolean = false,
    val toggleAccent: AccentPreset = AccentPreset.Green,
    val toggleCustomColor: Long = 0xFF30D158L,
    val sliderAccent: AccentPreset = AccentPreset.Green,
    val sliderCustomColor: Long = 0xFF30D158L,
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

/** Feature 1: Lock Screen Sheet & Liquid Glass 2.0 3-Edge Refraction Settings */
data class LockScreenSheetSettings(
    val enabled: Boolean = true,
    val enableOptimization: Boolean = true,
    val refractionHeight: Float = 68.0f,
    val sideRefraction: Float = 32.0f,
    val bottomRefraction: Float = 18.0f,
    val bevelWidth: Float = 38.0f,
    val leftBevelWidth: Float = 38.0f,
    val rightBevelWidth: Float = 38.0f,
    val bottomBevelWidth: Float = 38.0f,
    val dispersionStrength: Float = 0.09f,
    val specularStrength: Float = 0.95f,
    val hairlineStrength: Float = 0.75f,
    val innerGlowStrength: Float = 0.20f,
    val innerShadow: Float = 0.45f,
    val saturationFactor: Float = 125.0f,
    val rimSoftness: Float = 2.0f,
    val blurRadius: Float = 0.0f,
    val cornerRadius: Float = 38.0f,
    val edgeStretch: Float = 28.0f,
    val pillWidth: Float = 156.0f,
    val pillHeight: Float = 6.0f,
    val pillBottomMargin: Float = 9.0f
)

/** Feature 7: Notification Center iOS Sheet & Liquid Glass Refraction Settings */
data class NotificationSheetSettings(
    val enabled: Boolean = true,
    val lockWallpaperEnabled: Boolean = false,
    val customWallpaperEnabled: Boolean = false,
    val lockWallpaperHomescreenOnly: Boolean = false,
    val depthWallpaperEnabled: Boolean = false,
    val depthCustomSubject: Boolean = false,
    val oneToOneTrackingEnabled: Boolean = false,
    val lockScreenAsNotifCenterEnabled: Boolean = false,
    val showBottomWidgets: Boolean = true,
    val clockMorphOnNotifications: Boolean = true,
    val glassEffectsEnabled: Boolean = false,
    val glassOnlyWithoutWallpaper: Boolean = false,
    val refractionHeight: Float = 24.0f,
    val sideRefraction: Float = 32.0f,
    val bottomRefraction: Float = 18.0f,
    val bevelWidth: Float = 28.0f,
    val leftBevelWidth: Float = 32.0f,
    val rightBevelWidth: Float = 32.0f,
    val bottomBevelWidth: Float = 28.0f,
    val dispersionStrength: Float = 0.030f,
    val specularStrength: Float = 0.90f,
    val hairlineStrength: Float = 0.75f,
    val innerGlowStrength: Float = 0.20f,
    val rimSoftness: Float = 2.0f,
    val innerShadow: Float = 0.40f,
    val saturationFactor: Float = 120.0f,
    val blurRadius: Float = 0.0f,
    val cornerRadius: Float = 36.0f,
    val sideMargin: Float = 14.0f,
    val iosHunAnimationEnabled: Boolean = true,
    val iosHunDurationMs: Float = 380.0f,
    val iosHunStartScale: Float = 0.45f,
    val iosHunSqueezeStrength: Float = 0.40f
)

/** Feature 2: Liquid Glass 2.0 Button PIN Pad Engine Settings */
data class LockScreenKeypadSettings(
    val enabled: Boolean = true,
    val buttonRefraction: Float = 48.0f,
    val buttonBevel: Float = 24.0f,
    val buttonDispersion: Float = 0.09f,
    val buttonSpecular: Float = 0.95f,
    val buttonInnerShadow: Float = 0.40f,
    val buttonBlur: Float = 0.0f,
    val buttonSaturation: Float = 125.0f,
    val circleDiameter: Float = 82.0f,
    val digitTextSize: Float = 36.0f,
    val showKlondike: Boolean = false,
    val pressLiquidScale: Float = 0.88f,
    val pressLiquidAmp: Float = 0.60f,
    val pinpadLift: Float = 0.0f,
    val row0Offset: Float = -115.0f,
    val rowSpacing: Float = 30.0f,
    val columnSpacing: Float = 0.0f,
    val row1Offset: Float = -90.0f,
    val row2Offset: Float = -60.0f,
    val row3Offset: Float = -30.0f,
    val col1Offset: Float = 0.0f,
    val col3Offset: Float = 0.0f,
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
    val delayCancel: Float = -1f,
    // Liquid Dynamics & Physics Tuning
    val dragMaxThreshold: Float = 0.28f,
    val dragInitialStiffness: Float = 0.45f,
    val dragResistancePower: Float = 1.8f,
    val dragVolumeStretch: Float = 0.22f,
    val releaseDuration: Float = 320.0f,
    val releaseSquishAmp: Float = 0.35f,
    val releaseOvershoot: Float = 2.0f,
    val cascadeFlightElongation: Float = 0.22f,
    val cascadeLandingSquish: Float = 0.24f,
    val cascadeInertiaCoupling: Float = 1.0f,
    val cascadeViscousDamping: Float = 0.75f,
    // iOS 26 Liquid Bloom
    val entryAnimationType: String = "ios26",
    val iosBloomDuration: Float = 520.0f,
    val iosBloomClusterFactor: Float = 0.70f,
    val iosBloomOvershoot: Float = 0.16f,
    val iosBloomDamping: Float = 0.54f,
    val iosBloomStiffness: Float = 13.5f,
    val iosBloomRadialSquish: Float = 0.28f,
    val iosBloomVelocityStretch: Float = 0.16f,
    val iosBloomWaveStagger: Float = 22.0f,
    val iosBloomDecoupleText: Boolean = true
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
    val durationScale: Float = 1.00f,
    val velocityInfluence: Float = 2.00f,
    val maxVelocity: Float = 8288.0f,
    val cascadeDamping: Float = 0.86f,
    val kineticDockDamping: Float = 1.00f,
    val kineticDockResponse: Float = 0.55f,
    val kineticDockZDepth: Float = 800.0f
)

/** Feature 4: iOS App Transition & Physics Settings */
data class IOSTransitionSettings(
    val enabled: Boolean = true,
    val arcApexLift: Float = 1.0f,
    val openUpliftSurge: Boolean = true,
    val keystoneSqueeze: Boolean = true,
    val animSpeed: Float = 1.0f,
    val openSpringDamping: Float = 0.98f,
    val closeSpringDamping: Float = 0.84f,
    val folderTracking: Boolean = true,
    val recentsCentering: Boolean = true,
    val zeroFrictionDrag: Boolean = true,
    val landscapeGameOpt: Boolean = true,
    val refinedHaptics: Boolean = true,
    val stackRecentsHaptics: Boolean = true
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
    val frictionMultiplier: Float = 0.40f,
    val lowEndFlashlightEnabled: Boolean = false,
    val flashlightMaxBrightness: Int = 100
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

/** Safe Mode & Crash Recovery Settings */
data class SafeModeSettings(
    val globalSafeMode: Boolean = false,
    val systemUiSafeMode: Boolean = false,
    val launcherSafeMode: Boolean = false,
    val instagramSafeMode: Boolean = false,
    val whatsappSafeMode: Boolean = false,
    val youtubeSafeMode: Boolean = false,
    val crashingAliases: Set<String> = emptySet()
)


