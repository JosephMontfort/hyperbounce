package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
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
                    enabled = sheet.enabled
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
                    enabled = sheet.enabled
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
                    enabled = sheet.enabled
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
                    enabled = sheet.enabled
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
                    enabled = sheet.enabled
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
                    enabled = sheet.enabled
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
                    enabled = sheet.enabled
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
                    enabled = sheet.enabled
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
                    enabled = sheet.enabled
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
