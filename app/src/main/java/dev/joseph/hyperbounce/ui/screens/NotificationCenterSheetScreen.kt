package dev.joseph.hyperbounce.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.notification.glass.CustomWallpaperHelper
import dev.joseph.hyperbounce.notification.glass.DepthWallpaperHelper
import dev.joseph.hyperbounce.ui.components.AppGridIcon
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.ImageIcon
import dev.joseph.hyperbounce.ui.components.LockIcon
import dev.joseph.hyperbounce.ui.components.ResetArrowIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsClickableRow
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.components.TrashIcon
import dev.joseph.hyperbounce.ui.components.WallpaperCropperDialog
import dev.joseph.hyperbounce.ui.components.DepthSubjectAdjusterDialog
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun NotificationCenterSheetScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val sheet = repository.notificationSheet
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasCustomSubject by remember { mutableStateOf(DepthWallpaperHelper.hasCustomSubject(context) || sheet.depthCustomSubject) }
    var hasCustomWallpaper by remember { mutableStateOf(CustomWallpaperHelper.hasCustomWallpaper(context)) }
    var croppingBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var adjustingSubjectBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(sheet.depthWallpaperEnabled, sheet.depthCustomSubject) {
        hasCustomSubject = DepthWallpaperHelper.hasCustomSubject(context) || sheet.depthCustomSubject
    }
    LaunchedEffect(sheet.customWallpaperEnabled) {
        hasCustomWallpaper = CustomWallpaperHelper.hasCustomWallpaper(context)
    }

    val customWallpaperPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = CustomWallpaperHelper.decodeBitmapFromUri(context, uri)
            if (bmp != null) {
                croppingBitmap = bmp
            } else {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    croppingBitmap?.let { bmp ->
        WallpaperCropperDialog(
            sourceBitmap = bmp,
            colors = colors,
            backdrop = backdrop,
            onDismiss = { croppingBitmap = null },
            onCropConfirmed = { cropped ->
                croppingBitmap = null
                val saved = CustomWallpaperHelper.saveCustomWallpaper(context, cropped)
                if (saved) {
                    hasCustomWallpaper = true
                    sheet.setCustomWallpaperEnabled(true)
                    Toast.makeText(context, "Custom wallpaper set successfully", Toast.LENGTH_SHORT).show()
                    try {
                        val intent = Intent("dev.joseph.hyperbounce.ACTION_REFRESH_DEPTH_WALLPAPER")
                        context.sendBroadcast(intent)
                    } catch (_: Throwable) {}
                } else {
                    Toast.makeText(context, "Failed to save wallpaper", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    adjustingSubjectBitmap?.let { bmp ->
        DepthSubjectAdjusterDialog(
            subjectBitmap = bmp,
            colors = colors,
            backdrop = backdrop,
            onDismiss = { adjustingSubjectBitmap = null },
            onSubjectConfirmed = { adjusted ->
                adjustingSubjectBitmap = null
                DepthWallpaperHelper.saveCustomSubjectBitmap(context, adjusted) { success ->
                    if (success) {
                        hasCustomSubject = true
                        sheet.setDepthCustomSubject(true)
                        Toast.makeText(context, "Custom depth subject applied successfully", Toast.LENGTH_SHORT).show()
                        try {
                            val intent = Intent("dev.joseph.hyperbounce.ACTION_REFRESH_DEPTH_WALLPAPER")
                            context.sendBroadcast(intent)
                        } catch (_: Throwable) {}
                    } else {
                        Toast.makeText(context, "Failed to save custom subject", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = DepthWallpaperHelper.decodeBitmapFromUri(context, uri)
            if (bmp != null) {
                adjustingSubjectBitmap = bmp
            } else {
                Toast.makeText(context, "Failed to decode cutout image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ScreenScaffold(
        title = "Notification Center",
        subtitle = "iOS Glass Sheet & Static Flow",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = sheet::reloadFromDefaults,
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Notification Center iOS Sheet",
                    subtitle = if (sheet.enabled) "Single cohesive container sliding down with static notifications" else "Disabled (Stock rolling animation)",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = sheet.enabled,
                    onCheckedChange = sheet::setEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
            }
        }

        item {
            SectionHeader("Sheet Wallpaper Background", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Lock Screen Wallpaper Background",
                    subtitle = if (sheet.lockWallpaperEnabled) "Unblurred lockscreen wallpaper attached to the sheet, rolling down with it" else "Disabled (Transparent glass sheet)",
                    colors = colors,
                    iconTint = colors.accentPurple,
                    backdrop = backdrop,
                    checked = sheet.lockWallpaperEnabled,
                    onCheckedChange = sheet::setLockWallpaperEnabled,
                    icon = { LockIcon(color = Color.White, size = 15.dp) },
                    enabled = sheet.enabled
                )
                if (sheet.lockWallpaperEnabled) {
                    RowDivider(colors.separator)
                    SettingsToggleRow(
                        title = "1:1 Finger Tracking",
                        subtitle = if (sheet.oneToOneTrackingEnabled) "Physical 1:1 finger tracking on shade pulldown with 50% halfway threshold snap on release" else "Disabled (HyperOS native 200px threshold)",
                        colors = colors,
                        iconTint = colors.accentBlue,
                        backdrop = backdrop,
                        checked = sheet.oneToOneTrackingEnabled,
                        onCheckedChange = sheet::setOneToOneTrackingEnabled,
                        icon = { SlidersIcon(color = Color.White, size = 15.dp) },
                        enabled = sheet.enabled && sheet.lockWallpaperEnabled
                    )
                    RowDivider(colors.separator)
                    SettingsToggleRow(
                        title = "Homescreen Only",
                        subtitle = if (sheet.lockWallpaperHomescreenOnly) "Active only when pulling down over homescreen; uses transparent glass over running apps" else "Active everywhere (over homescreen and apps)",
                        colors = colors,
                        iconTint = colors.accentGreen,
                        backdrop = backdrop,
                        checked = sheet.lockWallpaperHomescreenOnly,
                        onCheckedChange = sheet::setLockWallpaperHomescreenOnly,
                        icon = { AppGridIcon(color = Color.White, size = 15.dp) },
                        enabled = sheet.enabled && sheet.lockWallpaperEnabled
                    )
                    RowDivider(colors.separator)
                    SettingsToggleRow(
                        title = "Custom Background Wallpaper",
                        subtitle = if (sheet.customWallpaperEnabled) "Custom cropped wallpaper displayed behind notification sheet" else "Disabled (Uses lockscreen wallpaper)",
                        colors = colors,
                        iconTint = colors.accentBlue,
                        backdrop = backdrop,
                        checked = sheet.customWallpaperEnabled,
                        onCheckedChange = sheet::setCustomWallpaperEnabled,
                        icon = { ImageIcon(color = Color.White, size = 15.dp) },
                        enabled = sheet.enabled && sheet.lockWallpaperEnabled
                    )
                    if (sheet.customWallpaperEnabled) {
                        RowDivider(colors.separator)
                        SettingsClickableRow(
                            title = if (hasCustomWallpaper) "Change & Crop Wallpaper" else "Choose & Crop Wallpaper",
                            subtitle = if (hasCustomWallpaper) "Custom wallpaper active. Tap to choose a new one and crop" else "Pick an image and crop to match screen aspect ratio",
                            colors = colors,
                            iconTint = colors.accentBlue,
                            onClick = {
                                customWallpaperPickerLauncher.launch("image/*")
                            },
                            icon = { ImageIcon(color = Color.White, size = 15.dp) }
                        )
                        if (hasCustomWallpaper) {
                            RowDivider(colors.separator)
                            SettingsClickableRow(
                                title = "Remove Custom Wallpaper",
                                subtitle = "Revert to lockscreen wallpaper background",
                                colors = colors,
                                iconTint = colors.accentRed,
                                titleColor = colors.accentRed,
                                onClick = {
                                    CustomWallpaperHelper.clearCustomWallpaper(context)
                                    hasCustomWallpaper = false
                                    sheet.setCustomWallpaperEnabled(false)
                                    Toast.makeText(context, "Custom wallpaper removed", Toast.LENGTH_SHORT).show()
                                    try {
                                        val intent = Intent("dev.joseph.hyperbounce.ACTION_REFRESH_DEPTH_WALLPAPER")
                                        context.sendBroadcast(intent)
                                    } catch (_: Throwable) {}
                                },
                                icon = { TrashIcon(color = Color.White, size = 15.dp) }
                            )
                        }
                    }
                }
            }
            SectionFooter(
                "Attaches your lock screen or custom cropped wallpaper to the notification container sheet. Automatically disabled in landscape orientation to prevent distortion.",
                colors
            )
        }

        if (sheet.lockWallpaperEnabled) {
            item {
                SectionHeader("iOS 27 Depth Wallpaper", colors)
                GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                    SettingsToggleRow(
                        title = "iOS 27 Depth Wallpaper Effect",
                        subtitle = if (sheet.depthWallpaperEnabled) "Static subject revealed over transparent background as shade descends" else "Disabled by default",
                        colors = colors,
                        iconTint = colors.accentPurple,
                        backdrop = backdrop,
                        checked = sheet.depthWallpaperEnabled,
                        onCheckedChange = { enabled ->
                            sheet.setDepthWallpaperEnabled(enabled)
                            hasCustomSubject = DepthWallpaperHelper.hasCustomSubject(context) || sheet.depthCustomSubject
                        },
                        icon = { LockIcon(color = Color.White, size = 15.dp) },
                        enabled = sheet.enabled && sheet.lockWallpaperEnabled
                    )
                    if (sheet.depthWallpaperEnabled) {
                        RowDivider(colors.separator)
                        SettingsClickableRow(
                            title = if (hasCustomSubject) "Change Custom Subject (PNG)" else "Upload Custom Subject (PNG)",
                            subtitle = if (hasCustomSubject) "Custom subject active (cutout). Tap to pick another" else "Use your own cutout image with background already removed",
                            colors = colors,
                            iconTint = colors.accentBlue,
                            onClick = {
                                imagePickerLauncher.launch("image/*")
                            },
                            icon = { ImageIcon(color = Color.White, size = 15.dp) }
                        )
                        if (hasCustomSubject) {
                            RowDivider(colors.separator)
                            SettingsClickableRow(
                                title = "Adjust Depth Subject Position",
                                subtitle = "Reposition and scale the cutout over the wallpaper",
                                colors = colors,
                                iconTint = colors.accentPurple,
                                onClick = {
                                    val currentBmp = DepthWallpaperHelper.getSubjectBitmap(context)
                                    if (currentBmp != null) {
                                        adjustingSubjectBitmap = currentBmp
                                    } else {
                                        Toast.makeText(context, "No subject image available to adjust", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                icon = { LockIcon(color = Color.White, size = 15.dp) }
                            )
                            RowDivider(colors.separator)
                            SettingsClickableRow(
                                title = "Remove Custom Subject",
                                subtitle = "Clear custom cutout subject",
                                colors = colors,
                                iconTint = colors.accentRed,
                                titleColor = colors.accentRed,
                                onClick = {
                                    DepthWallpaperHelper.clearCustomSubject(context) { success ->
                                        hasCustomSubject = false
                                        sheet.setDepthCustomSubject(false)
                                        Toast.makeText(context, "Custom subject removed", Toast.LENGTH_SHORT).show()
                                        try {
                                            val intent = Intent("dev.joseph.hyperbounce.ACTION_REFRESH_DEPTH_WALLPAPER")
                                            context.sendBroadcast(intent)
                                        } catch (_: Throwable) {}
                                    }
                                },
                                icon = { TrashIcon(color = Color.White, size = 15.dp) }
                            )
                        }
                    }
                }
                SectionFooter(
                    "Uses your custom transparent PNG cutout image (with background removed). While pulling down the shade, the background is transparent and the subject remains fixed in place, occluding the clock numbers and clipped at the descending sheet's edge.",
                    colors
                )
            }

            item {
                SectionHeader("Lock Screen Cover Sheet", colors)
                GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                    SettingsToggleRow(
                        title = "Lock Screen as Notification Center",
                        subtitle = if (sheet.lockScreenAsNotifCenterEnabled) "Replicates your active HyperOS lock screen clock, depth layering & shortcuts when pulling down shade" else "Disabled (Standard notification shade)",
                        colors = colors,
                        iconTint = colors.accentBlue,
                        backdrop = backdrop,
                        checked = sheet.lockScreenAsNotifCenterEnabled,
                        onCheckedChange = sheet::setLockScreenAsNotifCenterEnabled,
                        icon = { LockIcon(color = Color.White, size = 15.dp) },
                        enabled = sheet.enabled && sheet.lockWallpaperEnabled
                    )
                    if (sheet.lockScreenAsNotifCenterEnabled) {
                        RowDivider(colors.separator)
                        SettingsToggleRow(
                            title = "Bottom Torch & Camera Widgets",
                            subtitle = if (sheet.showBottomWidgets) "Quick-access flashlight and camera buttons with tactile haptics & pop-in springs" else "Hidden",
                            colors = colors,
                            iconTint = colors.accentOrange,
                            backdrop = backdrop,
                            checked = sheet.showBottomWidgets,
                            onCheckedChange = sheet::setShowBottomWidgets,
                            icon = { SlidersIcon(color = Color.White, size = 15.dp) },
                            enabled = sheet.enabled && sheet.lockWallpaperEnabled && sheet.lockScreenAsNotifCenterEnabled
                        )
                        RowDivider(colors.separator)
                        SettingsToggleRow(
                            title = "Dynamic Clock Morphing",
                            subtitle = if (sheet.clockMorphOnNotifications) "Clock smoothly scales to compact variant when notifications are present" else "Static full-size clock layout",
                            colors = colors,
                            iconTint = colors.accentPurple,
                            backdrop = backdrop,
                            checked = sheet.clockMorphOnNotifications,
                            onCheckedChange = sheet::setClockMorphOnNotifications,
                            icon = { AppGridIcon(color = Color.White, size = 15.dp) },
                            enabled = sheet.enabled && sheet.lockWallpaperEnabled && sheet.lockScreenAsNotifCenterEnabled
                        )
                    }
                }
                SectionFooter(
                    "Turns the notification center into an authentic replica of your lock screen. The clock sits behind the depth cutout (occluded by your subject's head) and refracts through glass bevels with 1:1 physical finger tracking.",
                    colors
                )
            }
        }

        item {
            SectionHeader("3-Edge Liquid Glass Effects", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "3-Edge Liquid Glass Effects",
                    subtitle = if (sheet.glassEffectsEnabled) "Real-time optical refraction, dispersion, and specular highlight along edges" else "Disabled by default (Clean border outline without shader overhead)",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = sheet.glassEffectsEnabled,
                    onCheckedChange = sheet::setGlassEffectsEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) },
                    enabled = sheet.enabled
                )
                if (sheet.glassEffectsEnabled) {
                    RowDivider(colors.separator)
                    SettingsToggleRow(
                        title = "Only Without Lockscreen Wallpaper",
                        subtitle = if (sheet.glassOnlyWithoutWallpaper) "Show glass effects only when lockscreen wallpaper is not actively shown on the sheet" else "Always show glass effects when enabled",
                        colors = colors,
                        iconTint = colors.accentPurple,
                        backdrop = backdrop,
                        checked = sheet.glassOnlyWithoutWallpaper,
                        onCheckedChange = sheet::setGlassOnlyWithoutWallpaper,
                        icon = { LockIcon(color = Color.White, size = 15.dp) },
                        enabled = sheet.enabled && sheet.glassEffectsEnabled
                    )
                }
            }
            SectionFooter(
                "Enables real-time AGSL refractive glass shader along left, right, and bottom sheet edges. Disabled by default to save GPU and capture overhead.",
                colors
            )
        }

        item {
            SectionHeader("Optics & Refraction (Liquid Glass 2.0)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Side Edges Refraction",
                    subtitle = "Inward lens magnification and curvature along left and right borders",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.sideRefraction,
                    onValueChange = sheet::setSideRefraction,
                    valueRange = 0.0f..120.0f,
                    defaultValue = 32.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Bottom Edge Refraction",
                    subtitle = "Upward lens refraction along the bottom rim (calibrated against stretching)",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.bottomRefraction,
                    onValueChange = sheet::setBottomRefraction,
                    valueRange = 0.0f..80.0f,
                    defaultValue = 18.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Chromatic Dispersion",
                    subtitle = "Physical R/G/B spectral separation along surface normals",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.dispersionStrength,
                    onValueChange = sheet::setDispersionStrength,
                    valueRange = 0.0f..0.30f,
                    defaultValue = 0.03f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Caustic Rim Softness",
                    subtitle = "Normal-directed 3-tap sample smoothing radius along glass bevels",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.rimSoftness,
                    onValueChange = sheet::setRimSoftness,
                    valueRange = 0.0f..8.0f,
                    defaultValue = 2.0f,
                    valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Saturation Boost Factor",
                    subtitle = "Color vibrancy multiplication inside the glass lens",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.saturationFactor,
                    onValueChange = sheet::setSaturationFactor,
                    valueRange = 50.0f..200.0f,
                    defaultValue = 120.0f,
                    valueLabel = { String.format(Locale.US, "%.0f%%", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Lens Blur Radius",
                    subtitle = "Internal gaussian dispersion blur filter",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.blurRadius,
                    onValueChange = sheet::setBlurRadius,
                    valueRange = 0.0f..60.0f,
                    defaultValue = 0.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
            }
            SectionFooter(
                "Refracts underlying applications and wallpapers with crystal-clear 100% transparent interior.",
                colors
            )
        }

        item {
            SectionHeader("Glass Lighting & Specular Highlights", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Specular Highlight Intensity",
                    subtitle = "Master surface lighting rim brightness",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.specularStrength,
                    onValueChange = sheet::setSpecularStrength,
                    valueRange = 0.0f..2.0f,
                    defaultValue = 0.90f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Perimeter Hairline Brightness",
                    subtitle = "Crisp Apple-style white edge boundary reflection along all 3 rims",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.hairlineStrength,
                    onValueChange = sheet::setHairlineStrength,
                    valueRange = 0.0f..2.0f,
                    defaultValue = 0.75f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Inner Glow Band Intensity",
                    subtitle = "Soft refractive inner light diffusion inside bevel slopes",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.innerGlowStrength,
                    onValueChange = sheet::setInnerGlowStrength,
                    valueRange = 0.0f..1.0f,
                    defaultValue = 0.20f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Inner Shadow Depth",
                    subtitle = "Bevel inner groove shadow depth factor",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.innerShadow,
                    onValueChange = sheet::setInnerShadow,
                    valueRange = 0.0f..1.0f,
                    defaultValue = 0.40f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
            }
            SectionFooter(
                "Simulates directional specular gleam and omnidirectional rim reflection across all glass edges.",
                colors
            )
        }

        item {
            SectionHeader("3-Edge Bevel Geometry", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Left Edge Bevel Width",
                    subtitle = "Width of the left optical glass bevel slope",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.leftBevelWidth,
                    onValueChange = sheet::setLeftBevelWidth,
                    valueRange = 0.0f..80.0f,
                    defaultValue = 28.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Right Edge Bevel Width",
                    subtitle = "Width of the right optical glass bevel slope",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.rightBevelWidth,
                    onValueChange = sheet::setRightBevelWidth,
                    valueRange = 0.0f..80.0f,
                    defaultValue = 28.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Bottom Edge Bevel Width",
                    subtitle = "Width of the bottom optical glass bevel slope",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.bottomBevelWidth,
                    onValueChange = sheet::setBottomBevelWidth,
                    valueRange = 0.0f..80.0f,
                    defaultValue = 28.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Bottom Corner Radius",
                    subtitle = "Curvature radius of bottom left/right sheet corners",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.cornerRadius,
                    onValueChange = sheet::setCornerRadius,
                    valueRange = 0.0f..64.0f,
                    defaultValue = 36.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
            }
        }

        item {
            SectionHeader("Layout & Spacing", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Notification Inset Margin",
                    subtitle = "Horizontal margin reduction so glass bevels frame notifications",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.sideMargin,
                    onValueChange = sheet::setSideMargin,
                    valueRange = 0.0f..32.0f,
                    defaultValue = 14.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
            }
            SectionFooter(
                "Narrows notifications inward from container edges so the 3-edge liquid glass border renders cleanly around them without clipping content.",
                colors
            )
        }
    }
}
