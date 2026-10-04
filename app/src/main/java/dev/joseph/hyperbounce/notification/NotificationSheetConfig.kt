package dev.joseph.hyperbounce.notification

import android.content.SharedPreferences

data class NotificationSheetConfig(
    var enabled: Boolean = true,
    var lockWallpaperEnabled: Boolean = false,
    var customWallpaperEnabled: Boolean = false,
    var lockWallpaperHomescreenOnly: Boolean = false,
    var depthWallpaperEnabled: Boolean = false,
    var depthCustomSubject: Boolean = false,
    var lockScreenAsNotifCenterEnabled: Boolean = false,
    var showBottomWidgets: Boolean = true,
    var clockMorphOnNotifications: Boolean = false,
    var glassEffectsEnabled: Boolean = false,
    var glassOnlyWithoutWallpaper: Boolean = false,
    // Optics (Liquid Glass 2.0)
    var refractionHeight: Float = 24.0f,
    var sideRefraction: Float = 32.0f,
    var bottomRefraction: Float = 18.0f,
    var bevelWidth: Float = 28.0f,
    var leftBevelWidth: Float = 32.0f,
    var rightBevelWidth: Float = 32.0f,
    var bottomBevelWidth: Float = 28.0f,
    var dispersionStrength: Float = 0.030f,
    var specularStrength: Float = 0.90f,
    var hairlineStrength: Float = 0.75f,
    var innerGlowStrength: Float = 0.20f,
    var rimSoftness: Float = 2.0f,
    var innerShadow: Float = 0.40f,
    var saturationFactor: Float = 120.0f,
    var blurRadius: Float = 0.0f,
    var cornerRadius: Float = 36.0f,
    // Notification item horizontal margin inset
    var sideMargin: Float = 14.0f,
    // Home indicator pill
    var pillWidth: Float = 156.0f,
    var pillHeight: Float = 6.0f,
    var pillBottomMargin: Float = 9.0f,
    // 1:1 physical finger tracking on pulldown
    var oneToOneTrackingEnabled: Boolean = false,
    // iOS 26 Heads-Up Banner Animation
    var iosHunAnimationEnabled: Boolean = true,
    var iosHunDurationMs: Float = 600.0f,
    var iosHunStartScale: Float = 0.25f,
    var iosHunSqueezeStrength: Float = 3.0f
) {
    /**
     * True only if sheet is enabled and lockscreen wallpaper toggle is on.
     */
    val isWallpaperActive: Boolean
        get() = enabled && lockWallpaperEnabled

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
        sheet.enabled = prefs.getBoolean("notif_sheet_enabled", true)
        sheet.lockWallpaperEnabled = prefs.getBoolean("notif_sheet_lock_wallpaper_enabled", false)
        sheet.customWallpaperEnabled = prefs.getBoolean("notif_sheet_custom_wallpaper_enabled", false)
        sheet.lockWallpaperHomescreenOnly = prefs.getBoolean("notif_sheet_lock_wallpaper_homescreen_only", false)
        sheet.depthWallpaperEnabled = prefs.getBoolean("notif_sheet_depth_wallpaper_enabled", false)
        sheet.depthCustomSubject = prefs.getBoolean("notif_sheet_depth_custom_subject", false)
        sheet.oneToOneTrackingEnabled = prefs.getBoolean("notif_sheet_one_to_one_tracking", false)
        sheet.lockScreenAsNotifCenterEnabled = prefs.getBoolean("notif_sheet_lockscreen_enabled", false)
        sheet.showBottomWidgets = prefs.getBoolean("notif_sheet_show_bottom_widgets", true)
        sheet.clockMorphOnNotifications = prefs.getBoolean("notif_sheet_clock_morph", false)
        sheet.glassEffectsEnabled = prefs.getBoolean("notif_sheet_glass_effects_enabled", false)
        sheet.glassOnlyWithoutWallpaper = prefs.getBoolean("notif_sheet_glass_only_without_wallpaper", false)
        sheet.refractionHeight = prefs.getFloat("notif_card_refraction_height", 24.0f)
        sheet.sideRefraction = prefs.getFloat("notif_card_side_refraction", 32.0f)
        sheet.bottomRefraction = prefs.getFloat("notif_card_bottom_refraction", 18.0f)
        sheet.bevelWidth = prefs.getFloat("notif_card_bevel_width", 28.0f)
        sheet.leftBevelWidth = prefs.getFloat("notif_card_left_bevel", 32.0f)
        sheet.rightBevelWidth = prefs.getFloat("notif_card_right_bevel", 32.0f)
        sheet.bottomBevelWidth = prefs.getFloat("notif_card_bottom_bevel", 28.0f)
        sheet.dispersionStrength = prefs.getFloat("notif_card_dispersion", 0.030f)
        sheet.specularStrength = prefs.getFloat("notif_card_specular", 0.90f)
        sheet.hairlineStrength = prefs.getFloat("notif_card_hairline", 0.75f)
        sheet.innerGlowStrength = prefs.getFloat("notif_card_inner_glow", 0.20f)
        sheet.rimSoftness = prefs.getFloat("notif_card_rim_softness", 2.0f)
        sheet.innerShadow = prefs.getFloat("notif_card_inner_shadow", 0.40f)
        sheet.saturationFactor = prefs.getFloat("notif_card_saturation", 120.0f)
        sheet.blurRadius = prefs.getFloat("notif_card_blur_radius", 0.0f)
        sheet.cornerRadius = prefs.getFloat("notif_card_corner_radius", 36.0f)
        sheet.sideMargin = prefs.getFloat("notif_side_margin", 14.0f)
        sheet.pillWidth = prefs.getFloat("notif_pill_width", 156.0f)
        sheet.pillHeight = prefs.getFloat("notif_pill_height", 6.0f)
        sheet.pillBottomMargin = prefs.getFloat("notif_pill_bottom_margin", 9.0f)
        sheet.iosHunAnimationEnabled = prefs.getBoolean("notif_ios_hun_animation_enabled", true)
        sheet.iosHunDurationMs = prefs.getFloat("notif_ios_hun_duration_ms", 600.0f)
        sheet.iosHunStartScale = prefs.getFloat("notif_ios_hun_start_scale", 0.25f)
        sheet.iosHunSqueezeStrength = prefs.getFloat("notif_ios_hun_squeeze_strength", 3.0f)
    }
}
