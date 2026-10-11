package dev.hyperios.app.notification

import android.content.SharedPreferences
import dev.hyperios.app.common.ModuleDefaults

data class NotificationSheetConfig(
    var masterEnabled: Boolean = ModuleDefaults.MasterToggles.NOTIF_MASTER_ENABLED,
    var enabled: Boolean = ModuleDefaults.MasterToggles.NOTIF_SHEET_ENABLED,
    var lockWallpaperEnabled: Boolean = ModuleDefaults.NotificationSheet.LOCK_WALLPAPER_ENABLED,
    var customWallpaperEnabled: Boolean = ModuleDefaults.NotificationSheet.CUSTOM_WALLPAPER_ENABLED,
    var lockWallpaperHomescreenOnly: Boolean = ModuleDefaults.NotificationSheet.LOCK_WALLPAPER_HOMESCREEN_ONLY,
    var depthWallpaperEnabled: Boolean = ModuleDefaults.NotificationSheet.DEPTH_WALLPAPER_ENABLED,
    var depthCustomSubject: Boolean = ModuleDefaults.NotificationSheet.DEPTH_CUSTOM_SUBJECT,
    var lockScreenAsNotifCenterEnabled: Boolean = ModuleDefaults.NotificationSheet.LOCK_SCREEN_AS_NOTIF_CENTER_ENABLED,
    var showBottomWidgets: Boolean = ModuleDefaults.NotificationSheet.SHOW_BOTTOM_WIDGETS,
    var clockMorphOnNotifications: Boolean = ModuleDefaults.NotificationSheet.CLOCK_MORPH_ON_NOTIFICATIONS,
    var glassEffectsEnabled: Boolean = ModuleDefaults.NotificationSheet.GLASS_EFFECTS_ENABLED,
    var glassOnlyWithoutWallpaper: Boolean = ModuleDefaults.NotificationSheet.GLASS_ONLY_WITHOUT_WALLPAPER,
    // Optics (Liquid Glass 2.0)
    var refractionHeight: Float = ModuleDefaults.NotificationSheet.REFRACTION_HEIGHT,
    var sideRefraction: Float = ModuleDefaults.NotificationSheet.SIDE_REFRACTION,
    var bottomRefraction: Float = ModuleDefaults.NotificationSheet.BOTTOM_REFRACTION,
    var bevelWidth: Float = ModuleDefaults.NotificationSheet.BEVEL_WIDTH,
    var leftBevelWidth: Float = ModuleDefaults.NotificationSheet.LEFT_BEVEL_WIDTH,
    var rightBevelWidth: Float = ModuleDefaults.NotificationSheet.RIGHT_BEVEL_WIDTH,
    var bottomBevelWidth: Float = ModuleDefaults.NotificationSheet.BOTTOM_BEVEL_WIDTH,
    var dispersionStrength: Float = ModuleDefaults.NotificationSheet.DISPERSION_STRENGTH,
    var specularStrength: Float = ModuleDefaults.NotificationSheet.SPECULAR_STRENGTH,
    var hairlineStrength: Float = ModuleDefaults.NotificationSheet.HAIRLINE_STRENGTH,
    var innerGlowStrength: Float = ModuleDefaults.NotificationSheet.INNER_GLOW_STRENGTH,
    var rimSoftness: Float = ModuleDefaults.NotificationSheet.RIM_SOFTNESS,
    var innerShadow: Float = ModuleDefaults.NotificationSheet.INNER_SHADOW,
    var saturationFactor: Float = ModuleDefaults.NotificationSheet.SATURATION_FACTOR,
    var blurRadius: Float = ModuleDefaults.NotificationSheet.BLUR_RADIUS,
    var cornerRadius: Float = ModuleDefaults.NotificationSheet.CORNER_RADIUS,
    // Notification item horizontal margin inset
    var sideMargin: Float = ModuleDefaults.NotificationSheet.SIDE_MARGIN,
    // Home indicator pill
    var pillWidth: Float = ModuleDefaults.NotificationSheet.PILL_WIDTH,
    var pillHeight: Float = ModuleDefaults.NotificationSheet.PILL_HEIGHT,
    var pillBottomMargin: Float = ModuleDefaults.NotificationSheet.PILL_BOTTOM_MARGIN,
    // 1:1 physical finger tracking on pulldown
    var oneToOneTrackingEnabled: Boolean = ModuleDefaults.NotificationSheet.ONE_TO_ONE_TRACKING_ENABLED,
    // iOS 26 Heads-Up Banner Animation
    var iosHunAnimationEnabled: Boolean = ModuleDefaults.NotificationSheet.IOS_HUN_ANIMATION_ENABLED,
    var iosHunDurationMs: Float = ModuleDefaults.NotificationSheet.IOS_HUN_DURATION_MS,
    var iosHunStartScale: Float = ModuleDefaults.NotificationSheet.IOS_HUN_START_SCALE,
    var iosHunSqueezeStrength: Float = ModuleDefaults.NotificationSheet.IOS_HUN_SQUEEZE_STRENGTH,
    // Clearance spacing below big lock screen clock
    var notifStartMarginTop: Float = ModuleDefaults.NotificationSheet.NOTIF_START_MARGIN_TOP,
    // Clock size scale in notification center
    var notifClockScale: Float = ModuleDefaults.NotificationSheet.NOTIF_CLOCK_SCALE,
    // iOS-Style Stacked Notifications
    var notifStackEnabled: Boolean = ModuleDefaults.NotificationSheet.NOTIF_STACK_ENABLED,
    // Launcher Unlock Animation on Notification Center Dismiss
    var launcherUnlockOnDismiss: Boolean = ModuleDefaults.NotificationSheet.LAUNCHER_UNLOCK_ON_DISMISS
) {
    /**
     * True only if master switch and sheet toggle are both on.
     */
    val isSheetActive: Boolean
        get() = masterEnabled && enabled

    /**
     * True only if sheet is active and lockscreen wallpaper toggle is on.
     */
    val isWallpaperActive: Boolean
        get() = isSheetActive && lockWallpaperEnabled

    /**
     * Custom wallpaper is active only if lockscreen wallpaper feature is on and custom wallpaper toggle is enabled.
     */
    val isCustomWallpaperActive: Boolean
        get() = isWallpaperActive && customWallpaperEnabled

    /**
     * Depth wallpaper effect is strictly gated behind an active wallpaper source.
     * Stored user preferences are preserved even when wallpaper is toggled off.
     */
    val isDepthActive: Boolean
        get() = isWallpaperActive && depthWallpaperEnabled

    /**
     * Lock screen replication as notification center is strictly gated behind an active wallpaper source.
     * Stored user preferences are preserved even when wallpaper is toggled off.
     */
    val isLockScreenActive: Boolean
        get() = isWallpaperActive && lockScreenAsNotifCenterEnabled

    /**
     * 1:1 finger tracking is active whenever wallpaper background is enabled and the toggle is on.
     */
    val isOneToOneTrackingActive: Boolean
        get() = isWallpaperActive && oneToOneTrackingEnabled

    /**
     * 3-Edge Liquid Glass effects are active only if the lockscreen wallpaper feature is enabled.
     * Stored user preferences are preserved even when wallpaper is toggled off.
     */
    val isGlassEffectsActive: Boolean
        get() = isWallpaperActive && glassEffectsEnabled
}

object NotificationSheetConfigHolder {
    val sheet = NotificationSheetConfig()

    fun updateFromPrefs(prefs: SharedPreferences) {
        sheet.masterEnabled = prefs.getBoolean("notif_master_enabled", ModuleDefaults.MasterToggles.NOTIF_MASTER_ENABLED)
        sheet.enabled = prefs.getBoolean("notif_sheet_enabled", ModuleDefaults.MasterToggles.NOTIF_SHEET_ENABLED)
        sheet.lockWallpaperEnabled = prefs.getBoolean("notif_sheet_lock_wallpaper_enabled", ModuleDefaults.NotificationSheet.LOCK_WALLPAPER_ENABLED)
        sheet.customWallpaperEnabled = prefs.getBoolean("notif_sheet_custom_wallpaper_enabled", ModuleDefaults.NotificationSheet.CUSTOM_WALLPAPER_ENABLED)
        sheet.lockWallpaperHomescreenOnly = prefs.getBoolean("notif_sheet_lock_wallpaper_homescreen_only", ModuleDefaults.NotificationSheet.LOCK_WALLPAPER_HOMESCREEN_ONLY)
        sheet.depthWallpaperEnabled = prefs.getBoolean("notif_sheet_depth_wallpaper_enabled", ModuleDefaults.NotificationSheet.DEPTH_WALLPAPER_ENABLED)
        sheet.depthCustomSubject = prefs.getBoolean("notif_sheet_depth_custom_subject", ModuleDefaults.NotificationSheet.DEPTH_CUSTOM_SUBJECT)
        sheet.oneToOneTrackingEnabled = prefs.getBoolean("notif_sheet_one_to_one_tracking", ModuleDefaults.NotificationSheet.ONE_TO_ONE_TRACKING_ENABLED)
        sheet.lockScreenAsNotifCenterEnabled = prefs.getBoolean("notif_sheet_lockscreen_enabled", ModuleDefaults.NotificationSheet.LOCK_SCREEN_AS_NOTIF_CENTER_ENABLED)
        sheet.showBottomWidgets = prefs.getBoolean("notif_sheet_show_bottom_widgets", ModuleDefaults.NotificationSheet.SHOW_BOTTOM_WIDGETS)
        sheet.clockMorphOnNotifications = prefs.getBoolean("notif_sheet_clock_morph", ModuleDefaults.NotificationSheet.CLOCK_MORPH_ON_NOTIFICATIONS)
        sheet.glassEffectsEnabled = prefs.getBoolean("notif_sheet_glass_effects_enabled", ModuleDefaults.NotificationSheet.GLASS_EFFECTS_ENABLED)
        sheet.glassOnlyWithoutWallpaper = prefs.getBoolean("notif_sheet_glass_only_without_wallpaper", ModuleDefaults.NotificationSheet.GLASS_ONLY_WITHOUT_WALLPAPER)
        sheet.refractionHeight = prefs.getFloat("notif_card_refraction_height", ModuleDefaults.NotificationSheet.REFRACTION_HEIGHT)
        sheet.sideRefraction = prefs.getFloat("notif_card_side_refraction", ModuleDefaults.NotificationSheet.SIDE_REFRACTION)
        sheet.bottomRefraction = prefs.getFloat("notif_card_bottom_refraction", ModuleDefaults.NotificationSheet.BOTTOM_REFRACTION)
        sheet.bevelWidth = prefs.getFloat("notif_card_bevel_width", ModuleDefaults.NotificationSheet.BEVEL_WIDTH)
        sheet.leftBevelWidth = prefs.getFloat("notif_card_left_bevel", ModuleDefaults.NotificationSheet.LEFT_BEVEL_WIDTH)
        sheet.rightBevelWidth = prefs.getFloat("notif_card_right_bevel", ModuleDefaults.NotificationSheet.RIGHT_BEVEL_WIDTH)
        sheet.bottomBevelWidth = prefs.getFloat("notif_card_bottom_bevel", ModuleDefaults.NotificationSheet.BOTTOM_BEVEL_WIDTH)
        sheet.dispersionStrength = prefs.getFloat("notif_card_dispersion", ModuleDefaults.NotificationSheet.DISPERSION_STRENGTH)
        sheet.specularStrength = prefs.getFloat("notif_card_specular", ModuleDefaults.NotificationSheet.SPECULAR_STRENGTH)
        sheet.hairlineStrength = prefs.getFloat("notif_card_hairline", ModuleDefaults.NotificationSheet.HAIRLINE_STRENGTH)
        sheet.innerGlowStrength = prefs.getFloat("notif_card_inner_glow", ModuleDefaults.NotificationSheet.INNER_GLOW_STRENGTH)
        sheet.rimSoftness = prefs.getFloat("notif_card_rim_softness", ModuleDefaults.NotificationSheet.RIM_SOFTNESS)
        sheet.innerShadow = prefs.getFloat("notif_card_inner_shadow", ModuleDefaults.NotificationSheet.INNER_SHADOW)
        sheet.saturationFactor = prefs.getFloat("notif_card_saturation", ModuleDefaults.NotificationSheet.SATURATION_FACTOR)
        sheet.blurRadius = prefs.getFloat("notif_card_blur_radius", ModuleDefaults.NotificationSheet.BLUR_RADIUS)
        sheet.cornerRadius = prefs.getFloat("notif_card_corner_radius", ModuleDefaults.NotificationSheet.CORNER_RADIUS)
        sheet.sideMargin = prefs.getFloat("notif_side_margin", ModuleDefaults.NotificationSheet.SIDE_MARGIN)
        sheet.pillWidth = prefs.getFloat("notif_pill_width", ModuleDefaults.NotificationSheet.PILL_WIDTH)
        sheet.pillHeight = prefs.getFloat("notif_pill_height", ModuleDefaults.NotificationSheet.PILL_HEIGHT)
        sheet.pillBottomMargin = prefs.getFloat("notif_pill_bottom_margin", ModuleDefaults.NotificationSheet.PILL_BOTTOM_MARGIN)
        sheet.iosHunAnimationEnabled = prefs.getBoolean("notif_ios_hun_animation_enabled", ModuleDefaults.NotificationSheet.IOS_HUN_ANIMATION_ENABLED)
        sheet.iosHunDurationMs = prefs.getFloat("notif_ios_hun_duration_ms", ModuleDefaults.NotificationSheet.IOS_HUN_DURATION_MS)
        sheet.iosHunStartScale = prefs.getFloat("notif_ios_hun_start_scale", ModuleDefaults.NotificationSheet.IOS_HUN_START_SCALE)
        sheet.iosHunSqueezeStrength = prefs.getFloat("notif_ios_hun_squeeze_strength", ModuleDefaults.NotificationSheet.IOS_HUN_SQUEEZE_STRENGTH)
        sheet.notifStartMarginTop = prefs.getFloat("notif_start_margin_top", ModuleDefaults.NotificationSheet.NOTIF_START_MARGIN_TOP)
        sheet.notifClockScale = prefs.getFloat("notif_clock_scale", ModuleDefaults.NotificationSheet.NOTIF_CLOCK_SCALE)
        sheet.notifStackEnabled = prefs.getBoolean("notif_stack_enabled", ModuleDefaults.NotificationSheet.NOTIF_STACK_ENABLED)
        sheet.launcherUnlockOnDismiss = prefs.getBoolean("notif_launcher_unlock_on_dismiss", ModuleDefaults.NotificationSheet.LAUNCHER_UNLOCK_ON_DISMISS)
    }
}
