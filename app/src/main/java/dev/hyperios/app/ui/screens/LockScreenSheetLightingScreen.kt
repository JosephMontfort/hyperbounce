package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
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
            sheet.setSpecularStrength(0.95f)
            sheet.setHairlineStrength(0.75f)
            sheet.setInnerGlowStrength(0.20f)
            sheet.setInnerShadow(0.45f)
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
                    valueRange = 0.0f..2.0f,
                    defaultValue = 0.95f,
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
                    valueRange = 0.0f..2.0f,
                    defaultValue = 0.75f,
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
                    valueRange = 0.0f..1.0f,
                    defaultValue = 0.20f,
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
                    defaultValue = 0.45f,
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
