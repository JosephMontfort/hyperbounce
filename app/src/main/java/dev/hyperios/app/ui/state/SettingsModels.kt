package dev.hyperios.app.ui.state

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

import dev.hyperios.app.common.ModuleDefaults

enum class ThemeMode { System, Light, Dark }

enum class AppTheme(val label: String) {
    FollowSystem("Follow System"),
    Light("Light"),
    Dark("Dark")
}

enum class ComponentStyle(val label: String) {
    LiquidGlass("Liquid Glass"),
    MIUIX("MIUIX")
}

val LocalComponentStyle = androidx.compose.runtime.compositionLocalOf { ComponentStyle.MIUIX }

enum class AppIconStyle(val label: String) {
    Monet("Monet"),
    Flat("Flat")
}

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
    LiquidGlass("Liquid Glass"),
    Disabled("Disabled")
}

enum class NavApp(val displayName: String, val packageName: String) {
    Instagram("Instagram", "com.instagram.android"),
    WhatsApp("WhatsApp", "com.whatsapp"),
    YouTube("YouTube", "com.google.android.youtube")
}

/** One app's floating nav bar settings. Stored independently per [NavApp] — changing
 *  WhatsApp's values never touches Instagram's or YouTube's stored state. */
data class NavBarAppSettings(
    val enabled: Boolean = false,
    val liquidGlass: Boolean = true,
    val glassIntensity: Float = 1.0f,
    val barBlurIntensity: Float = 1.0f,
    val containerOpacity: Float = ModuleDefaults.NavApps.Instagram.CONTAINER_OPACITY,
    val cornerRadius: Float = ModuleDefaults.NavApps.Instagram.CORNER_RADIUS,
    val barHeight: Float = 50f,
    val tabWidth: Float = ModuleDefaults.NavApps.Instagram.TAB_WIDTH,
    val bottomMargin: Float = ModuleDefaults.NavApps.Instagram.BOTTOM_MARGIN,
    val themeMode: ThemeMode = ThemeMode.System,
    val accent: AccentPreset = AccentPreset.Auto,
    val customAccentColor: Long = ModuleDefaults.NavApps.Instagram.CUSTOM_ACCENT_COLOR,
    val pillGlassIntensity: Float = ModuleDefaults.NavApps.WhatsApp.PILL_GLASS_INTENSITY,
    val pillContainerOpacity: Float = ModuleDefaults.NavApps.Instagram.PILL_CONTAINER_OPACITY,
    val pillChromaticAberration: Boolean = true,
    val pillShadow: Boolean = false,
    val autoShrink: Boolean = ModuleDefaults.NavApps.YouTube.AUTO_SHRINK,
    val autoShrinkScale: Float = ModuleDefaults.NavApps.YouTube.AUTO_SHRINK_SCALE,
    val reelsFullscreenZoom: Boolean = ModuleDefaults.NavApps.Instagram.REELS_FULLSCREEN_ZOOM
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
    val togglesSlidersHookEnabled: Boolean = ModuleDefaults.MasterToggles.MASTER_HOOKS_ENABLED,
    val toggleEngine: RenderEngine = RenderEngine.LiquidGlass,
    val sliderEngine: RenderEngine = RenderEngine.LiquidGlass,
    val keypadEnabled: Boolean = ModuleDefaults.MasterToggles.ENGINE_KEYPAD_ENABLED,
    val miuixAppsHookingEnabled: Boolean = ModuleDefaults.MasterToggles.MIUIX_APPS_HOOKING_ENABLED,
    val toggleAccent: AccentPreset = AccentPreset.Green,
    val toggleCustomColor: Long = 0xFF30D158L,
    val sliderAccent: AccentPreset = AccentPreset.Blue,
    val sliderCustomColor: Long = 0xFF30D158L,
    val toggleGlass: GlassFeel = GlassFeel(
        blurDp = ModuleDefaults.Engine.TOGGLE_GLASS_BLUR,
        lensSizeDp = ModuleDefaults.Engine.TOGGLE_GLASS_LENS_SIZE,
        lensDepthDp = ModuleDefaults.Engine.TOGGLE_GLASS_LENS_DEPTH,
        highlightStrength = ModuleDefaults.Engine.TOGGLE_GLASS_HIGHLIGHT,
        shadowRadiusDp = ModuleDefaults.Engine.TOGGLE_GLASS_SHADOW,
        dragSensitivity = ModuleDefaults.Engine.TOGGLE_GLASS_DRAG_SENSITIVITY,
        springDamping = ModuleDefaults.Engine.TOGGLE_GLASS_SPRING_DAMPING,
        springStiffness = ModuleDefaults.Engine.TOGGLE_GLASS_SPRING_STIFFNESS
    ),
    val sliderGlass: GlassFeel = GlassFeel(
        blurDp = ModuleDefaults.Engine.SLIDER_GLASS_BLUR,
        lensSizeDp = ModuleDefaults.Engine.SLIDER_GLASS_LENS_SIZE,
        lensDepthDp = ModuleDefaults.Engine.SLIDER_GLASS_LENS_DEPTH,
        highlightStrength = ModuleDefaults.Engine.SLIDER_GLASS_HIGHLIGHT,
        shadowRadiusDp = ModuleDefaults.Engine.SLIDER_GLASS_SHADOW,
        dragSensitivity = ModuleDefaults.Engine.SLIDER_GLASS_DRAG_SENSITIVITY,
        springDamping = ModuleDefaults.Engine.SLIDER_GLASS_SPRING_DAMPING,
        springStiffness = ModuleDefaults.Engine.SLIDER_GLASS_SPRING_STIFFNESS
    )
)

/**
 * The one master switch plus the shared "how glassy is the chrome" parameters.
 */
data class GlobalGlassSettings(
    val enabled: Boolean = ModuleDefaults.GlobalGlass.ENABLED,
    val blurDp: Float = ModuleDefaults.GlobalGlass.BLUR_DP,
    val refractionHeightDp: Float = ModuleDefaults.GlobalGlass.REFRACTION_HEIGHT_DP,
    val refractionAmountDp: Float = ModuleDefaults.GlobalGlass.REFRACTION_AMOUNT_DP,
    val chromaticAberration: Boolean = ModuleDefaults.GlobalGlass.CHROMATIC_ABERRATION,
    val hapticsEnabled: Boolean = ModuleDefaults.GlobalGlass.HAPTICS_ENABLED
)

/** Feature 1: Lock Screen Sheet & Liquid Glass 2.0 3-Edge Refraction Settings */
data class LockScreenSheetSettings(
    val enabled: Boolean = ModuleDefaults.MasterToggles.LOCK_SHEET_ENABLED,
    val refractionHeight: Float = ModuleDefaults.LockScreenSheet.REFRACTION_HEIGHT,
    val sideRefraction: Float = ModuleDefaults.LockScreenSheet.SIDE_REFRACTION,
    val bottomRefraction: Float = ModuleDefaults.LockScreenSheet.BOTTOM_REFRACTION,
    val leftBevelWidth: Float = ModuleDefaults.LockScreenSheet.LEFT_BEVEL_WIDTH,
    val rightBevelWidth: Float = ModuleDefaults.LockScreenSheet.RIGHT_BEVEL_WIDTH,
    val bottomBevelWidth: Float = ModuleDefaults.LockScreenSheet.BOTTOM_BEVEL_WIDTH,
    val dispersionStrength: Float = ModuleDefaults.LockScreenSheet.DISPERSION_STRENGTH,
    val specularStrength: Float = ModuleDefaults.LockScreenSheet.SPECULAR_STRENGTH,
    val hairlineStrength: Float = ModuleDefaults.LockScreenSheet.HAIRLINE_STRENGTH,
    val innerGlowStrength: Float = ModuleDefaults.LockScreenSheet.INNER_GLOW_STRENGTH,
    val innerShadow: Float = ModuleDefaults.LockScreenSheet.INNER_SHADOW,
    val saturationFactor: Float = ModuleDefaults.LockScreenSheet.SATURATION_FACTOR,
    val rimSoftness: Float = ModuleDefaults.LockScreenSheet.RIM_SOFTNESS,
    val blurRadius: Float = ModuleDefaults.LockScreenSheet.BLUR_RADIUS,
    val cornerRadius: Float = ModuleDefaults.LockScreenSheet.CORNER_RADIUS,
    val edgeStretch: Float = ModuleDefaults.LockScreenSheet.EDGE_STRETCH,
    val pillEnabled: Boolean = ModuleDefaults.LockScreenSheet.PILL_ENABLED,
    val pillWidth: Float = ModuleDefaults.LockScreenSheet.PILL_WIDTH,
    val pillHeight: Float = ModuleDefaults.LockScreenSheet.PILL_HEIGHT,
    val pillBottomMargin: Float = ModuleDefaults.LockScreenSheet.PILL_BOTTOM_MARGIN
)

/** Feature 7: Notification Center iOS Sheet & Liquid Glass Refraction Settings */
data class NotificationSheetSettings(
    val masterEnabled: Boolean = ModuleDefaults.MasterToggles.NOTIF_MASTER_ENABLED,
    val enabled: Boolean = ModuleDefaults.MasterToggles.NOTIF_SHEET_ENABLED,
    val lockWallpaperEnabled: Boolean = ModuleDefaults.NotificationSheet.LOCK_WALLPAPER_ENABLED,
    val customWallpaperEnabled: Boolean = ModuleDefaults.NotificationSheet.CUSTOM_WALLPAPER_ENABLED,
    val lockWallpaperHomescreenOnly: Boolean = ModuleDefaults.NotificationSheet.LOCK_WALLPAPER_HOMESCREEN_ONLY,
    val depthWallpaperEnabled: Boolean = ModuleDefaults.NotificationSheet.DEPTH_WALLPAPER_ENABLED,
    val depthCustomSubject: Boolean = ModuleDefaults.NotificationSheet.DEPTH_CUSTOM_SUBJECT,
    val oneToOneTrackingEnabled: Boolean = ModuleDefaults.NotificationSheet.ONE_TO_ONE_TRACKING_ENABLED,
    val lockScreenAsNotifCenterEnabled: Boolean = ModuleDefaults.NotificationSheet.LOCK_SCREEN_AS_NOTIF_CENTER_ENABLED,
    val showBottomWidgets: Boolean = ModuleDefaults.NotificationSheet.SHOW_BOTTOM_WIDGETS,
    val glassEffectsEnabled: Boolean = ModuleDefaults.NotificationSheet.GLASS_EFFECTS_ENABLED,
    val refractionHeight: Float = ModuleDefaults.NotificationSheet.REFRACTION_HEIGHT,
    val sideRefraction: Float = ModuleDefaults.NotificationSheet.SIDE_REFRACTION,
    val bottomRefraction: Float = ModuleDefaults.NotificationSheet.BOTTOM_REFRACTION,
    val leftBevelWidth: Float = ModuleDefaults.NotificationSheet.LEFT_BEVEL_WIDTH,
    val rightBevelWidth: Float = ModuleDefaults.NotificationSheet.RIGHT_BEVEL_WIDTH,
    val bottomBevelWidth: Float = ModuleDefaults.NotificationSheet.BOTTOM_BEVEL_WIDTH,
    val dispersionStrength: Float = ModuleDefaults.NotificationSheet.DISPERSION_STRENGTH,
    val specularStrength: Float = ModuleDefaults.NotificationSheet.SPECULAR_STRENGTH,
    val hairlineStrength: Float = ModuleDefaults.NotificationSheet.HAIRLINE_STRENGTH,
    val innerGlowStrength: Float = ModuleDefaults.NotificationSheet.INNER_GLOW_STRENGTH,
    val rimSoftness: Float = ModuleDefaults.NotificationSheet.RIM_SOFTNESS,
    val innerShadow: Float = ModuleDefaults.NotificationSheet.INNER_SHADOW,
    val saturationFactor: Float = ModuleDefaults.NotificationSheet.SATURATION_FACTOR,
    val blurRadius: Float = ModuleDefaults.NotificationSheet.BLUR_RADIUS,
    val cornerRadius: Float = ModuleDefaults.NotificationSheet.CORNER_RADIUS,
    val sideMargin: Float = ModuleDefaults.NotificationSheet.SIDE_MARGIN,
    val pillWidth: Float = ModuleDefaults.NotificationSheet.PILL_WIDTH,
    val pillHeight: Float = ModuleDefaults.NotificationSheet.PILL_HEIGHT,
    val pillBottomMargin: Float = ModuleDefaults.NotificationSheet.PILL_BOTTOM_MARGIN,
    val iosHunAnimationEnabled: Boolean = ModuleDefaults.NotificationSheet.IOS_HUN_ANIMATION_ENABLED,
    val iosHunDurationMs: Float = ModuleDefaults.NotificationSheet.IOS_HUN_DURATION_MS,
    val iosHunStartScale: Float = ModuleDefaults.NotificationSheet.IOS_HUN_START_SCALE,
    val iosHunSqueezeStrength: Float = ModuleDefaults.NotificationSheet.IOS_HUN_SQUEEZE_STRENGTH,
    val notifStartMarginTop: Float = ModuleDefaults.NotificationSheet.NOTIF_START_MARGIN_TOP,
    val notifClockScale: Float = ModuleDefaults.NotificationSheet.NOTIF_CLOCK_SCALE,
    val notifStackEnabled: Boolean = ModuleDefaults.NotificationSheet.NOTIF_STACK_ENABLED,
    val launcherUnlockOnDismiss: Boolean = ModuleDefaults.NotificationSheet.LAUNCHER_UNLOCK_ON_DISMISS
)

/** Feature 2: Liquid Glass 2.0 Button PIN Pad Engine Settings */
data class LockScreenKeypadSettings(
    val enabled: Boolean = ModuleDefaults.MasterToggles.ENGINE_KEYPAD_ENABLED,
    val buttonRefraction: Float = ModuleDefaults.LockScreenKeypad.BTN_REFRACTION,
    val buttonBevel: Float = ModuleDefaults.LockScreenKeypad.BTN_BEVEL,
    val buttonDispersion: Float = ModuleDefaults.LockScreenKeypad.BTN_DISPERSION,
    val buttonSpecular: Float = ModuleDefaults.LockScreenKeypad.BTN_SPECULAR,
    val buttonInnerShadow: Float = ModuleDefaults.LockScreenKeypad.BTN_INNER_SHADOW,
    val buttonBlur: Float = ModuleDefaults.LockScreenKeypad.BTN_BLUR,
    val buttonSaturation: Float = ModuleDefaults.LockScreenKeypad.BTN_SATURATION,
    val circleDiameter: Float = ModuleDefaults.LockScreenKeypad.CIRCLE_DIAMETER,
    val digitTextSize: Float = ModuleDefaults.LockScreenKeypad.DIGIT_TEXT_SIZE,
    val showKlondike: Boolean = ModuleDefaults.LockScreenKeypad.SHOW_KLONDIKE,
    val pressLiquidScale: Float = ModuleDefaults.LockScreenKeypad.PRESS_LIQUID_SCALE,
    val pinpadLift: Float = ModuleDefaults.LockScreenKeypad.PAD_LIFT,
    val row0Offset: Float = ModuleDefaults.LockScreenKeypad.ROW0_OFFSET,
    val rowSpacing: Float = ModuleDefaults.LockScreenKeypad.ROW_SPACING,
    val columnSpacing: Float = ModuleDefaults.LockScreenKeypad.COL_SPACING,
    val delayKey1: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY1,
    val delayKey2: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY2,
    val delayKey3: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY3,
    val delayKey4: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY4,
    val delayKey5: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY5,
    val delayKey6: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY6,
    val delayKey7: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY7,
    val delayKey8: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY8,
    val delayKey9: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY9,
    val delayKey0: Float = ModuleDefaults.LockScreenKeypad.DELAY_KEY0,
    val delayEmergency: Float = ModuleDefaults.LockScreenKeypad.DELAY_EMERGENCY,
    val delayCancel: Float = ModuleDefaults.LockScreenKeypad.DELAY_CANCEL,
    // Liquid Dynamics & Physics Tuning
    val dragMaxThreshold: Float = ModuleDefaults.LockScreenKeypad.DRAG_MAX_THRESHOLD,
    val dragInitialStiffness: Float = ModuleDefaults.LockScreenKeypad.DRAG_INITIAL_STIFFNESS,
    val dragResistancePower: Float = ModuleDefaults.LockScreenKeypad.DRAG_RESISTANCE_POWER,
    val dragVolumeStretch: Float = ModuleDefaults.LockScreenKeypad.DRAG_VOLUME_STRETCH,
    val releaseDuration: Float = ModuleDefaults.LockScreenKeypad.RELEASE_DURATION,
    val releaseSquishAmp: Float = ModuleDefaults.LockScreenKeypad.RELEASE_SQUISH_AMP,
    val releaseOvershoot: Float = ModuleDefaults.LockScreenKeypad.RELEASE_OVERSHOOT,
    // iOS 26 Liquid Bloom
    val iosBloomDuration: Float = ModuleDefaults.LockScreenKeypad.BLOOM_DURATION,
    val iosBloomClusterFactor: Float = ModuleDefaults.LockScreenKeypad.BLOOM_CLUSTER_FACTOR,
    val iosBloomOvershoot: Float = ModuleDefaults.LockScreenKeypad.BLOOM_OVERSHOOT,
    val iosBloomDamping: Float = ModuleDefaults.LockScreenKeypad.BLOOM_DAMPING,
    val iosBloomStiffness: Float = ModuleDefaults.LockScreenKeypad.BLOOM_STIFFNESS,
    val iosBloomRadialSquish: Float = ModuleDefaults.LockScreenKeypad.BLOOM_RADIAL_SQUISH,
    val iosBloomVelocityStretch: Float = ModuleDefaults.LockScreenKeypad.BLOOM_VELOCITY_STRETCH,
    val iosBloomWaveStagger: Float = ModuleDefaults.LockScreenKeypad.BLOOM_WAVE_STAGGER,
    val iosBloomDecoupleText: Boolean = ModuleDefaults.LockScreenKeypad.BLOOM_DECOUPLE_TEXT
)

/** Feature 3: Screen Unlock Velocity Kinetics Settings */
data class ScreenUnlockSettings(
    val enabled: Boolean = ModuleDefaults.MasterToggles.UNLOCK_ENABLED,
    val animMode: Int = ModuleDefaults.ScreenUnlock.MODE,
    val gridColumns: Int = ModuleDefaults.ScreenUnlock.GRID_COLUMNS,
    val gridRows: Int = ModuleDefaults.ScreenUnlock.GRID_ROWS,
    val dockDamping: Float = ModuleDefaults.ScreenUnlock.DOCK_DAMPING,
    val dockResponse: Float = ModuleDefaults.ScreenUnlock.DOCK_RESPONSE,
    val centerResponse: Float = ModuleDefaults.ScreenUnlock.CENTER_RESPONSE,
    val centerDamping: Float = ModuleDefaults.ScreenUnlock.CENTER_DAMPING,
    val edgeResponseDelay: Float = ModuleDefaults.ScreenUnlock.EDGE_RESPONSE_DELAY,
    val edgeDampingDrop: Float = ModuleDefaults.ScreenUnlock.EDGE_DAMPING_DROP,
    val zDepthMultiplier: Float = ModuleDefaults.ScreenUnlock.Z_DEPTH_MULTIPLIER,
    val minResponse: Float = ModuleDefaults.ScreenUnlock.MIN_RESPONSE,
    val maxResponse: Float = ModuleDefaults.ScreenUnlock.MAX_RESPONSE,
    val minDamping: Float = ModuleDefaults.ScreenUnlock.MIN_DAMPING,
    val maxDamping: Float = ModuleDefaults.ScreenUnlock.MAX_DAMPING,
    val durationScale: Float = ModuleDefaults.ScreenUnlock.DURATION_SCALE,
    val velocityInfluence: Float = ModuleDefaults.ScreenUnlock.VELOCITY_INFLUENCE,
    val maxVelocity: Float = ModuleDefaults.ScreenUnlock.MAX_VELOCITY,
    val cascadeDamping: Float = ModuleDefaults.ScreenUnlock.CASCADE_DAMPING,
    val kineticDockDamping: Float = ModuleDefaults.ScreenUnlock.KINETIC_DOCK_DAMPING,
    val kineticDockResponse: Float = ModuleDefaults.ScreenUnlock.KINETIC_DOCK_RESPONSE,
    val kineticDockZDepth: Float = ModuleDefaults.ScreenUnlock.KINETIC_DOCK_Z_DEPTH
)

/** Feature 4: App Transition & Physics Settings */
data class IOSTransitionSettings(
    val enabled: Boolean = ModuleDefaults.MasterToggles.IOS_ANIM_ENABLED,
    val arcApexLift: Float = ModuleDefaults.IOSTransitions.ARC_APEX_LIFT,
    val openSurgeLift: Float = ModuleDefaults.IOSTransitions.OPEN_SURGE_LIFT,
    val openUpliftSurge: Boolean = ModuleDefaults.IOSTransitions.OPEN_UPLIFT_SURGE,
    val closeUpliftSurge: Boolean = ModuleDefaults.IOSTransitions.CLOSE_UPLIFT_SURGE,
    val keystoneSqueeze: Boolean = ModuleDefaults.IOSTransitions.KEYSTONE_SQUEEZE,
    val animSpeed: Float = ModuleDefaults.IOSTransitions.ANIM_SPEED,
    val openSpringDamping: Float = ModuleDefaults.IOSTransitions.OPEN_SPRING_DAMPING,
    val closeSpringDamping: Float = ModuleDefaults.IOSTransitions.CLOSE_SPRING_DAMPING,
    val folderTracking: Boolean = ModuleDefaults.IOSTransitions.FOLDER_TRACKING,
    val recentsCentering: Boolean = ModuleDefaults.IOSTransitions.RECENTS_CENTERING,
    val zeroFrictionDrag: Boolean = ModuleDefaults.IOSTransitions.ZERO_FRICTION_DRAG,
    val landscapeGameOpt: Boolean = ModuleDefaults.IOSTransitions.LANDSCAPE_GAME_OPT,
    val refinedHaptics: Boolean = ModuleDefaults.IOSTransitions.REFINED_HAPTICS,
    val stackRecentsHaptics: Boolean = ModuleDefaults.IOSTransitions.STACK_RECENTS_HAPTICS
)

/** Features 5, 6, 7, 8, 9: Control Center Modular Customization, Jelly Physics & Dynamics */
data class ControlCenterSettings(
    val masterEnabled: Boolean = ModuleDefaults.MasterToggles.CC_MASTER_ENABLED,
    val modularEditEnabled: Boolean = ModuleDefaults.ControlCenter.MODULAR_EDIT_ENABLED,
    val animEnabled: Boolean = ModuleDefaults.ControlCenter.ANIM_ENABLED,
    val jellyEnabled: Boolean = ModuleDefaults.ControlCenter.JELLY_ENABLED,
    val animEngineMode: Int = ModuleDefaults.ControlCenter.ANIM_ENGINE_MODE,
    val spreadRatio: Float = ModuleDefaults.ControlCenter.SPREAD_RATIO,
    val spreadResponse: Float = ModuleDefaults.ControlCenter.SPREAD_RESPONSE,
    val spreadDamping: Float = ModuleDefaults.ControlCenter.SPREAD_DAMPING,
    val spreadReleaseResponse: Float = ModuleDefaults.ControlCenter.SPREAD_RELEASE_RESPONSE,
    val spreadReleaseDamping: Float = ModuleDefaults.ControlCenter.SPREAD_RELEASE_DAMPING,
    val tileDelayScale: Float = ModuleDefaults.ControlCenter.TILE_DELAY_SCALE,
    val frictionMultiplier: Float = ModuleDefaults.ControlCenter.FRICTION_MULTIPLIER,
    val lowEndFlashlightEnabled: Boolean = ModuleDefaults.ControlCenter.LOW_END_FLASHLIGHT_ENABLED,
    val flashlightMaxBrightness: Int = ModuleDefaults.ControlCenter.FLASHLIGHT_MAX_BRIGHTNESS,
    val true2dGridEnabled: Boolean = ModuleDefaults.ControlCenter.TRUE_2D_GRID_ENABLED,
    val fluidResizeEnabled: Boolean = ModuleDefaults.ControlCenter.FLUID_RESIZE_ENABLED,
    val landscapeRightMaxRows: Int = ModuleDefaults.ControlCenter.LANDSCAPE_RIGHT_MAX_ROWS,
    val pendingResetLayout: Boolean = ModuleDefaults.ControlCenter.PENDING_RESET_LAYOUT
)

/** Feature 10: Dropdown Menu to Dialog Fluid Morph Physics */
data class MorphPhysicsSettings(
    val morphSpeedMultiplier: Float = ModuleDefaults.MorphPhysics.MORPH_SPEED_MULTIPLIER,
    val circleCollapseRatio: Float = ModuleDefaults.MorphPhysics.CIRCLE_COLLAPSE_RATIO,
    val circleDiameterDp: Float = ModuleDefaults.MorphPhysics.CIRCLE_DIAMETER_DP,
    val zPlungeDepth: Float = ModuleDefaults.MorphPhysics.Z_PLUNGE_DEPTH,
    val gravitySwoopDp: Float = ModuleDefaults.MorphPhysics.GRAVITY_SWOOP_DP,
    val yBounceAmountDp: Float = ModuleDefaults.MorphPhysics.Y_BOUNCE_AMOUNT_DP,
    val jellyOvershoot: Float = ModuleDefaults.MorphPhysics.JELLY_OVERSHOOT,
    val contentStretch: Float = ModuleDefaults.MorphPhysics.CONTENT_STRETCH,
    val springDamping: Float = ModuleDefaults.MorphPhysics.SPRING_DAMPING,
    val springStiffness: Float = ModuleDefaults.MorphPhysics.SPRING_STIFFNESS,
    val lensSurgeDp: Float = ModuleDefaults.MorphPhysics.LENS_SURGE_DP,
    val motionBlurSurgeDp: Float = ModuleDefaults.MorphPhysics.MOTION_BLUR_SURGE_DP
)

/** Safe Mode & Crash Recovery Settings */
data class SafeModeSettings(
    val globalSafeMode: Boolean = false,
    val systemUiSafeMode: Boolean = false,
    val launcherSafeMode: Boolean = false,
    val settingsSafeMode: Boolean = false,
    val crashingAliases: Set<String> = emptySet()
)



