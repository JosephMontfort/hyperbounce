package dev.joseph.hyperbounce.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
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
    var isScanning by remember { mutableStateOf(false) }
    var hasCustomSubject by remember { mutableStateOf(DepthWallpaperHelper.hasCustomSubject(context)) }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            Toast.makeText(context, "Processing custom cutout image...", Toast.LENGTH_SHORT).show()
            DepthWallpaperHelper.saveCustomSubjectFromUri(context, uri) { success ->
                if (success) {
                    hasCustomSubject = true
                    sheet.setDepthCustomSubject(true)
                    Toast.makeText(context, "Custom subject image applied successfully", Toast.LENGTH_SHORT).show()
                    try {
                        val intent = Intent("dev.joseph.hyperbounce.ACTION_REFRESH_DEPTH_WALLPAPER")
                        context.sendBroadcast(intent)
                    } catch (_: Throwable) {}
                } else {
                    Toast.makeText(context, "Failed to load custom image", Toast.LENGTH_SHORT).show()
                }
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
                }
            }
            SectionFooter(
                "Attaches your crisp, unblurred lock screen wallpaper to the notification container sheet. Automatically disabled in landscape orientation to prevent distortion.",
                colors
            )
        }

        item {
            SectionHeader("iOS 27 Depth Wallpaper", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "iOS 27 Depth Wallpaper Effect",
                    subtitle = if (sheet.depthWallpaperEnabled) "Static subject revealed over 100% transparent unblurred background as shade descends" else "Disabled by default",
                    colors = colors,
                    iconTint = colors.accentPurple,
                    backdrop = backdrop,
                    checked = sheet.depthWallpaperEnabled,
                    onCheckedChange = { enabled ->
                        sheet.setDepthWallpaperEnabled(enabled)
                        if (enabled) {
                            DepthWallpaperHelper.triggerAsyncSubjectGeneration(context)
                            try {
                                val intent = Intent("dev.joseph.hyperbounce.ACTION_REFRESH_DEPTH_WALLPAPER")
                                context.sendBroadcast(intent)
                            } catch (_: Throwable) {}
                        }
                    },
                    icon = { LockIcon(color = Color.White, size = 15.dp) },
                    enabled = sheet.enabled
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
                            title = "Remove Custom Subject",
                            subtitle = "Revert to auto-segmentation of lockscreen wallpaper",
                            colors = colors,
                            iconTint = colors.accentRed,
                            titleColor = colors.accentRed,
                            onClick = {
                                DepthWallpaperHelper.clearCustomSubject(context) { success ->
                                    hasCustomSubject = false
                                    sheet.setDepthCustomSubject(false)
                                    Toast.makeText(context, "Reverted to lockscreen wallpaper segmentation", Toast.LENGTH_SHORT).show()
                                    try {
                                        val intent = Intent("dev.joseph.hyperbounce.ACTION_REFRESH_DEPTH_WALLPAPER")
                                        context.sendBroadcast(intent)
                                    } catch (_: Throwable) {}
                                }
                            },
                            icon = { TrashIcon(color = Color.White, size = 15.dp) }
                        )
                    }
                    RowDivider(colors.separator)
                    SettingsClickableRow(
                        title = if (isScanning) "Scanning Depth Subject..." else "Re-scan Wallpaper Subject",
                        subtitle = if (isScanning) "Extracting subject using on-device ML segmentation..." else "Re-runs MediaPipe segmentation on active lockscreen wallpaper",
                        colors = colors,
                        iconTint = colors.accentPurple,
                        onClick = {
                            if (isScanning) return@SettingsClickableRow
                            isScanning = true
                            Toast.makeText(context, "Scanning lockscreen wallpaper depth...", Toast.LENGTH_SHORT).show()
                            DepthWallpaperHelper.triggerAsyncSubjectGeneration(context, forceRefresh = true) { bmp ->
                                isScanning = false
                                if (bmp != null) {
                                    hasCustomSubject = false
                                    sheet.setDepthCustomSubject(false)
                                    Toast.makeText(context, "Depth subject extracted successfully", Toast.LENGTH_SHORT).show()
                                    try {
                                        val intent = Intent("dev.joseph.hyperbounce.ACTION_REFRESH_DEPTH_WALLPAPER")
                                        context.sendBroadcast(intent)
                                    } catch (_: Throwable) {}
                                } else {
                                    Toast.makeText(context, "Failed to detect subject in wallpaper", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        icon = { ResetArrowIcon(color = Color.White, size = 15.dp) }
                    )
                }
            }
            SectionFooter(
                "Isolates foreground subject from your lock screen wallpaper using on-device ML segmentation, or uses your custom transparent cutout image. While pulling down the shade, the background is 100% transparent with zero blur, and the subject remains fixed in place, clipped at the descending sheet's edge.",
                colors
            )
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
                    title = "Inward Refraction Displacement",
                    subtitle = "Real-time lens magnification offset along edges",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.refractionHeight,
                    onValueChange = sheet::setRefractionHeight,
                    valueRange = 0.0f..160.0f,
                    defaultValue = 50.0f,
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
                    defaultValue = 0.08f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Specular Highlight Intensity",
                    subtitle = "Surface directional lighting rim brightness",
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
                "Refracts the live blurred shade background layer without sampling or modifying the wallpaper.",
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
