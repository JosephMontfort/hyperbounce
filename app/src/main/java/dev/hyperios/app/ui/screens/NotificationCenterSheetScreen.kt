package dev.hyperios.app.ui.screens

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
import dev.hyperios.app.notification.glass.CustomWallpaperHelper
import dev.hyperios.app.notification.glass.DepthWallpaperHelper
import dev.hyperios.app.ui.components.AppGridIcon
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.ImageIcon
import dev.hyperios.app.ui.components.LockIcon
import dev.hyperios.app.ui.components.ResetArrowIcon
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionFooter
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.Destination
import dev.hyperios.app.ui.components.SettingsClickableRow
import dev.hyperios.app.ui.components.SettingsNavRow
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.components.SettingsToggleRow
import dev.hyperios.app.ui.components.SlidersIcon
import dev.hyperios.app.ui.components.TrashIcon
import dev.hyperios.app.ui.components.WallpaperCropperDialog
import dev.hyperios.app.ui.components.DepthSubjectAdjusterDialog
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun NotificationCenterSheetScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
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
                CustomWallpaperHelper.saveCustomWallpaper(context, cropped) { saved ->
                    if (saved) {
                        hasCustomWallpaper = true
                        sheet.setCustomWallpaperEnabled(true)
                        Toast.makeText(context, "Custom wallpaper set successfully", Toast.LENGTH_SHORT).show()
                        try {
                            val intent = Intent("dev.hyperios.app.ACTION_REFRESH_DEPTH_WALLPAPER")
                            context.sendBroadcast(intent)
                        } catch (_: Throwable) {}
                    } else {
                        Toast.makeText(context, "Failed to save wallpaper", Toast.LENGTH_SHORT).show()
                    }
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
                            val intent = Intent("dev.hyperios.app.ACTION_REFRESH_DEPTH_WALLPAPER")
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
        title = "Notification Centre",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = sheet::reloadFromDefaults,
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        val sheetActive = sheet.masterEnabled && sheet.enabled

        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Enable Hook",
                    subtitle = null,
                    colors = colors,
                    backdrop = backdrop,
                    checked = sheet.masterEnabled,
                    onCheckedChange = sheet::setMasterEnabled
                )
            }
        }

        item {
            SectionHeader("Pop-Up Notification Animation", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "iOS 26 Pop-Up Animation",
                    subtitle = "iOS 26 style animation for pop up notifications",
                    colors = colors,
                    backdrop = backdrop,
                    checked = sheet.iosHunAnimationEnabled,
                    onCheckedChange = sheet::setIosHunAnimationEnabled,
                    enabled = sheet.masterEnabled
                )
                if (sheet.iosHunAnimationEnabled) {
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Animation Speed (Duration)",
                        subtitle = null,
                        colors = colors,
                        backdrop = backdrop,
                        value = sheet.iosHunDurationMs,
                        onValueChange = sheet::setIosHunDurationMs,
                        valueRange = 200.0f..1000.0f,
                        defaultValue = 600.0f,
                        valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                        enabled = sheet.masterEnabled && sheet.iosHunAnimationEnabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Emergence Start Scale",
                        subtitle = "Initial size when emerging outward from the center top notch",
                        colors = colors,
                        backdrop = backdrop,
                        value = sheet.iosHunStartScale,
                        onValueChange = sheet::setIosHunStartScale,
                        valueRange = 0.20f..0.70f,
                        defaultValue = 0.25f,
                        valueLabel = { String.format(Locale.US, "%.0f%%", it * 100.0f) },
                        enabled = sheet.masterEnabled && sheet.iosHunAnimationEnabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Inertia Squeeze & Stretch",
                        subtitle = "Physics velocity elongation during transit and lateral squash upon landing",
                        colors = colors,
                        backdrop = backdrop,
                        value = sheet.iosHunSqueezeStrength,
                        onValueChange = sheet::setIosHunSqueezeStrength,
                        valueRange = 0.0f..4.0f,
                        defaultValue = 1.0f,
                        valueLabel = { String.format(Locale.US, "%.0f%%", it * 100.0f) },
                        enabled = sheet.masterEnabled && sheet.iosHunAnimationEnabled
                    )
                }
            }
            SectionFooter(
                "Applies exclusively to incoming heads-up pop-up notifications after the screen is unlocked. Does not affect lock screen notifications or open notification shade.",
                colors
            )
        }

        item {
            SectionHeader("Notification Center Sheet", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Notification Center iOS Sheet",
                    subtitle = null,
                    colors = colors,
                    backdrop = backdrop,
                    checked = sheet.enabled,
                    onCheckedChange = sheet::setEnabled,
                    enabled = sheet.masterEnabled
                )
            }
        }

        item {
            SectionHeader("Sheet Wallpaper Background", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Lock Screen Wallpaper Background",
                    subtitle = "Lockscreen wallpaper attached to the sheet, rolling down with it",
                    colors = colors,
                    backdrop = backdrop,
                    checked = sheet.lockWallpaperEnabled,
                    onCheckedChange = sheet::setLockWallpaperEnabled,
                    enabled = sheetActive
                )
                if (sheet.lockWallpaperEnabled) {
                    RowDivider(colors.separator)
                    SettingsToggleRow(
                        title = "1:1 Finger Tracking",
                        subtitle = "Physical 1:1 finger tracking on notification shade pulldown",
                        colors = colors,
                        backdrop = backdrop,
                        checked = sheet.oneToOneTrackingEnabled,
                        onCheckedChange = sheet::setOneToOneTrackingEnabled,
                        enabled = sheetActive && sheet.lockWallpaperEnabled
                    )
                    RowDivider(colors.separator)
                    SettingsToggleRow(
                        title = "Homescreen Only",
                        subtitle = "Active only when pulling down over homescreen; uses transparent glass over running apps",
                        colors = colors,
                        backdrop = backdrop,
                        checked = sheet.lockWallpaperHomescreenOnly,
                        onCheckedChange = sheet::setLockWallpaperHomescreenOnly,
                        enabled = sheetActive && sheet.lockWallpaperEnabled
                    )
                    RowDivider(colors.separator)
                    SettingsToggleRow(
                        title = "Custom Background Wallpaper",
                        subtitle = "Custom wallpaper displayed behind notification sheet",
                        colors = colors,
                        backdrop = backdrop,
                        checked = sheet.customWallpaperEnabled,
                        onCheckedChange = sheet::setCustomWallpaperEnabled,
                        enabled = sheetActive && sheet.lockWallpaperEnabled
                    )
                    if (sheet.customWallpaperEnabled) {
                        RowDivider(colors.separator)
                        SettingsClickableRow(
                            title = if (hasCustomWallpaper) "Change & Crop Wallpaper" else "Choose & Crop Wallpaper",
                            subtitle = if (hasCustomWallpaper) "Custom wallpaper active. Tap to choose a new one and crop" else "Pick an image and crop",
                            colors = colors,
                            enabled = sheetActive && sheet.lockWallpaperEnabled && sheet.customWallpaperEnabled,
                            onClick = {
                                customWallpaperPickerLauncher.launch("image/*")
                            }
                        )
                        if (hasCustomWallpaper) {
                            RowDivider(colors.separator)
                            SettingsClickableRow(
                                title = "Remove Custom Wallpaper",
                                subtitle = "Revert to lockscreen wallpaper background",
                                colors = colors,
                                titleColor = colors.accentRed,
                                enabled = sheetActive && sheet.lockWallpaperEnabled && sheet.customWallpaperEnabled,
                                onClick = {
                                    CustomWallpaperHelper.clearCustomWallpaper(context)
                                    hasCustomWallpaper = false
                                    sheet.setCustomWallpaperEnabled(false)
                                    Toast.makeText(context, "Custom wallpaper removed", Toast.LENGTH_SHORT).show()
                                    try {
                                        val intent = Intent("dev.hyperios.app.ACTION_REFRESH_DEPTH_WALLPAPER")
                                        context.sendBroadcast(intent)
                                    } catch (_: Throwable) {}
                                }
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
                        subtitle = "Static subject revealed over transparent background as shade descends",
                        colors = colors,
                        backdrop = backdrop,
                        checked = sheet.depthWallpaperEnabled,
                        onCheckedChange = { enabled ->
                            sheet.setDepthWallpaperEnabled(enabled)
                            hasCustomSubject = DepthWallpaperHelper.hasCustomSubject(context) || sheet.depthCustomSubject
                        },
                        enabled = sheetActive && sheet.lockWallpaperEnabled
                    )
                    if (sheet.depthWallpaperEnabled) {
                        RowDivider(colors.separator)
                        SettingsClickableRow(
                            title = if (hasCustomSubject) "Change Custom Subject (PNG)" else "Upload Custom Subject (PNG)",
                            subtitle = if (hasCustomSubject) "Custom subject active (cutout). Tap to pick another" else "Use your own cutout image with background already removed",
                            colors = colors,
                            enabled = sheetActive && sheet.lockWallpaperEnabled && sheet.depthWallpaperEnabled,
                            onClick = {
                                imagePickerLauncher.launch("image/*")
                            }
                        )
                        if (hasCustomSubject) {
                            RowDivider(colors.separator)
                            SettingsClickableRow(
                                title = "Adjust Depth Subject Position",
                                subtitle = "Reposition and scale the cutout over the wallpaper",
                                colors = colors,
                                enabled = sheetActive && sheet.lockWallpaperEnabled && sheet.depthWallpaperEnabled,
                                onClick = {
                                    val currentBmp = DepthWallpaperHelper.getSubjectBitmap(context)
                                    if (currentBmp != null) {
                                        adjustingSubjectBitmap = currentBmp
                                    } else {
                                        Toast.makeText(context, "No subject image available to adjust", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                            RowDivider(colors.separator)
                            SettingsClickableRow(
                                title = "Remove Custom Subject",
                                subtitle = "Clear custom cutout subject",
                                colors = colors,
                                titleColor = colors.accentRed,
                                enabled = sheetActive && sheet.lockWallpaperEnabled && sheet.depthWallpaperEnabled,
                                onClick = {
                                    DepthWallpaperHelper.clearCustomSubject(context) { success ->
                                        hasCustomSubject = false
                                        sheet.setDepthCustomSubject(false)
                                        Toast.makeText(context, "Custom subject removed", Toast.LENGTH_SHORT).show()
                                        try {
                                            val intent = Intent("dev.hyperios.app.ACTION_REFRESH_DEPTH_WALLPAPER")
                                            context.sendBroadcast(intent)
                                        } catch (_: Throwable) {}
                                    }
                                }
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
                        subtitle = "Replicates active HyperOS lock screen clock in notification centre",
                        colors = colors,
                        backdrop = backdrop,
                        checked = sheet.lockScreenAsNotifCenterEnabled,
                        onCheckedChange = sheet::setLockScreenAsNotifCenterEnabled,
                        enabled = sheetActive && sheet.lockWallpaperEnabled
                    )
                    if (sheet.lockScreenAsNotifCenterEnabled) {
                        RowDivider(colors.separator)
                        SettingsToggleRow(
                            title = "Bottom Torch & Camera Widgets",
                            subtitle = "Quick-access flashlight and camera buttons",
                            colors = colors,
                            backdrop = backdrop,
                            checked = sheet.showBottomWidgets,
                            onCheckedChange = sheet::setShowBottomWidgets,
                            enabled = sheetActive && sheet.lockWallpaperEnabled && sheet.lockScreenAsNotifCenterEnabled
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
                    subtitle = "Real-time optical refraction, dispersion, and specular highlight along edges",
                    colors = colors,
                    backdrop = backdrop,
                    checked = sheet.glassEffectsEnabled,
                    onCheckedChange = sheet::setGlassEffectsEnabled,
                    enabled = sheetActive && sheet.lockWallpaperEnabled
                )
                if (sheet.glassEffectsEnabled) {
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Side Edges Bevel Width",
                        subtitle = "Width of the left and right optical glass bevel slopes",
                        colors = colors,
                        backdrop = backdrop,
                        value = sheet.leftBevelWidth,
                        onValueChange = {
                            sheet.setLeftBevelWidth(it)
                            sheet.setRightBevelWidth(it)
                        },
                        valueRange = 0.0f..80.0f,
                        defaultValue = 32.0f,
                        valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                        enabled = sheetActive && sheet.lockWallpaperEnabled && sheet.glassEffectsEnabled
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
                        defaultValue = 45.0f,
                        valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                        enabled = sheetActive && sheet.lockWallpaperEnabled && sheet.glassEffectsEnabled
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
                        enabled = sheetActive
                    )
                }
            }
            SectionFooter(
                "Enables real-time AGSL refractive glass shader along left, right, and bottom sheet edges. Disabled by default to save GPU and capture overhead.",
                colors
            )
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
                    defaultValue = 10.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheetActive
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Notification Top Clearance Margin",
                    subtitle = "Downward margin below clock (0 dp = stock position)",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.notifStartMarginTop,
                    onValueChange = sheet::setNotifStartMarginTop,
                    valueRange = 0.0f..300.0f,
                    defaultValue = 250.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheetActive && sheet.lockScreenAsNotifCenterEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Clock Size Scale",
                    subtitle = "Scale factor of lock screen clock in notification center",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.notifClockScale,
                    onValueChange = sheet::setNotifClockScale,
                    valueRange = 0.50f..1.0f,
                    defaultValue = 0.60f,
                    valueLabel = { String.format(Locale.US, "%.0f%%", it * 100.0f) },
                    enabled = sheetActive && sheet.lockScreenAsNotifCenterEnabled
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Enable Stacked Notifications",
                    subtitle = "Compress notifications into an iOS-style bottom stack with fluid expansion",
                    colors = colors,
                    backdrop = backdrop,
                    checked = sheet.notifStackEnabled,
                    onCheckedChange = sheet::setNotifStackEnabled,
                    enabled = sheetActive
                )
            }
            SectionFooter(
                "Configure horizontal card insets, vertical clearance spacing beneath the lock screen clock, and iOS-style stacked notifications.",
                colors
            )
        }

        item {
            SectionHeader("Screen Unlock Animation", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Launcher Animation on dismiss",
                    subtitle = "Play the screen unlock animation on notification center dismiss",
                    colors = colors,
                    backdrop = backdrop,
                    checked = sheet.launcherUnlockOnDismiss,
                    onCheckedChange = sheet::setLauncherUnlockOnDismiss,
                    enabled = sheetActive
                )
            }
            SectionFooter(
                "Play the screen unlock animation on notification center dismiss. Triggers launcher icon kinetic unlock animation with independent velocity-based scaling.",
                colors
            )
        }

        item {
            SectionHeader("Optics & Components", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsNavRow(
                    title = "Liquid Glass Tuning",
                    subtitle = "Refraction, chromatic dispersion & corner curvature",
                    colors = colors,
                    enabled = sheetActive,
                    onClick = { onNavigate(Destination.NotificationCenterSheetGlassTuning) }
                )
                RowDivider(colors.separator)
                SettingsNavRow(
                    title = "Glass Lighting & Highlights",
                    subtitle = "Specular rims, perimeter hairline & inner glow",
                    colors = colors,
                    enabled = sheetActive,
                    onClick = { onNavigate(Destination.NotificationCenterSheetLighting) }
                )
                RowDivider(colors.separator)
                SettingsNavRow(
                    title = "Home Indicator Pill",
                    subtitle = "Pill dimensions, height & bottom margin",
                    colors = colors,
                    enabled = sheetActive,
                    onClick = { onNavigate(Destination.NotificationCenterSheetHomeIndicator) }
                )
            }
            SectionFooter(
                "Configure optical refraction parameters, specular highlight reflections, and bottom home indicator styling.",
                colors
            )
        }
    }
}
