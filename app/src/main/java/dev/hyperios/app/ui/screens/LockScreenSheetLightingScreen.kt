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
fun LockScreenSheetLightingScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val sheet = repository.lockScreenSheet
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Glass Lighting & Highlights",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            sheet.setSpecularStrength(ModuleDefaults.LockScreenSheet.SPECULAR_STRENGTH)
            sheet.setHairlineStrength(ModuleDefaults.LockScreenSheet.HAIRLINE_STRENGTH)
            sheet.setInnerGlowStrength(ModuleDefaults.LockScreenSheet.INNER_GLOW_STRENGTH)
            sheet.setInnerShadow(ModuleDefaults.LockScreenSheet.INNER_SHADOW)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("Lighting & Highlights", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Specular Highlight Intensity",
                    subtitle = "Master surface lighting rim brightness",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.specularStrength,
                    onValueChange = sheet::setSpecularStrength,
                    valueRange = ModuleDefaults.LockScreenSheet.SPECULAR_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.SPECULAR_STRENGTH,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Perimeter Hairline Brightness",
                    subtitle = "Crisp Apple-style white edge boundary reflection along all 3 rims",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.hairlineStrength,
                    onValueChange = sheet::setHairlineStrength,
                    valueRange = ModuleDefaults.LockScreenSheet.HAIRLINE_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.HAIRLINE_STRENGTH,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Inner Glow Band Intensity",
                    subtitle = "Soft refractive inner light diffusion inside bevel slopes",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.innerGlowStrength,
                    onValueChange = sheet::setInnerGlowStrength,
                    valueRange = ModuleDefaults.LockScreenSheet.INNER_GLOW_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.INNER_GLOW_STRENGTH,
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
                    valueRange = ModuleDefaults.LockScreenSheet.INNER_SHADOW_RANGE,
                    defaultValue = ModuleDefaults.LockScreenSheet.INNER_SHADOW,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = sheet.enabled
                )
            }
            SectionFooter(
                "Simulates directional specular gleam and omnidirectional rim reflection across all glass edges.",
                colors
            )
        }
    }
}
