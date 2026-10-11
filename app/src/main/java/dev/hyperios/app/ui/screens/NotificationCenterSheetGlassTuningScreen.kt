package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionFooter
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun NotificationCenterSheetGlassTuningScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val sheet = repository.notificationSheet
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Liquid Glass Tuning",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            sheet.setSideRefraction(ModuleDefaults.NotificationSheet.SIDE_REFRACTION)
            sheet.setBottomRefraction(ModuleDefaults.NotificationSheet.BOTTOM_REFRACTION)
            sheet.setDispersionStrength(ModuleDefaults.NotificationSheet.DISPERSION_STRENGTH)
            sheet.setRimSoftness(ModuleDefaults.NotificationSheet.RIM_SOFTNESS)
            sheet.setSaturationFactor(ModuleDefaults.NotificationSheet.SATURATION_FACTOR)
            sheet.setBlurRadius(ModuleDefaults.NotificationSheet.BLUR_RADIUS)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("Optics & Refraction", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Side Edges Refraction",
                    subtitle = "Inward lens magnification and curvature along left and right borders",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.sideRefraction,
                    onValueChange = sheet::setSideRefraction,
                    valueRange = ModuleDefaults.NotificationSheet.SIDE_REFRACTION_RANGE,
                    defaultValue = ModuleDefaults.NotificationSheet.SIDE_REFRACTION,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled && sheet.lockWallpaperEnabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Bottom Edge Refraction",
                    subtitle = "Upward lens refraction along bottom rim (calibrated against stretching)",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.bottomRefraction,
                    onValueChange = sheet::setBottomRefraction,
                    valueRange = ModuleDefaults.NotificationSheet.BOTTOM_REFRACTION_RANGE,
                    defaultValue = ModuleDefaults.NotificationSheet.BOTTOM_REFRACTION,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled && sheet.lockWallpaperEnabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Chromatic Dispersion",
                    subtitle = "Physical R/G/B spectral separation along surface normals",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.dispersionStrength,
                    onValueChange = sheet::setDispersionStrength,
                    valueRange = ModuleDefaults.NotificationSheet.DISPERSION_RANGE,
                    defaultValue = ModuleDefaults.NotificationSheet.DISPERSION_STRENGTH,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled && sheet.lockWallpaperEnabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Caustic Rim Softness",
                    subtitle = "Normal-directed 3-tap sample smoothing radius along glass bevels",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.rimSoftness,
                    onValueChange = sheet::setRimSoftness,
                    valueRange = ModuleDefaults.NotificationSheet.RIM_SOFTNESS_RANGE,
                    defaultValue = ModuleDefaults.NotificationSheet.RIM_SOFTNESS,
                    valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                    enabled = sheet.enabled && sheet.lockWallpaperEnabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Saturation Boost Factor",
                    subtitle = "Color vibrancy multiplication inside the glass lens",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.saturationFactor,
                    onValueChange = sheet::setSaturationFactor,
                    valueRange = ModuleDefaults.NotificationSheet.SATURATION_RANGE,
                    defaultValue = ModuleDefaults.NotificationSheet.SATURATION_FACTOR,
                    valueLabel = { String.format(Locale.US, "%.0f%%", it) },
                    enabled = sheet.enabled && sheet.lockWallpaperEnabled && sheet.glassEffectsEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Lens Blur Radius",
                    subtitle = "Internal gaussian dispersion blur filter",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.blurRadius,
                    onValueChange = sheet::setBlurRadius,
                    valueRange = ModuleDefaults.NotificationSheet.BLUR_RADIUS_RANGE,
                    defaultValue = ModuleDefaults.NotificationSheet.BLUR_RADIUS,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled && sheet.lockWallpaperEnabled && sheet.glassEffectsEnabled
                )
            }
            SectionFooter(
                "Refracts underlying applications and wallpapers with crystal-clear 100% transparent interior.",
                colors
            )
        }
    }
}
