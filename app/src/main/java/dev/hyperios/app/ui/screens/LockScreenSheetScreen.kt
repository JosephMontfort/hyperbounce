package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.ui.Destination
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionFooter
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsNavRow
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.components.SettingsToggleRow
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun LockScreenSheetScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    val sheet = repository.lockScreenSheet
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Lock Screen Sheet",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = sheet::reloadFromDefaults,
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Lock Screen Liquid Glass Sheet",
                    subtitle = null,
                    colors = colors,
                    backdrop = backdrop,
                    checked = sheet.enabled,
                    onCheckedChange = sheet::setEnabled
                )
            }
        }

        item {
            SectionHeader("Edge Glass Thickness", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Side Edges Glass Bevel",
                    subtitle = "Thickness of glass refraction band on left & right edges",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.leftBevelWidth,
                    onValueChange = sheet::setSideBevelWidth,
                    valueRange = 0.0f..120.0f,
                    defaultValue = 38.0f,
                    valueLabel = { if (it <= 0.5f) "Off" else String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Bottom Edge Glass Bevel",
                    subtitle = "Thickness of glass refraction band on bottom edge",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.bottomBevelWidth,
                    onValueChange = sheet::setBottomBevelWidth,
                    valueRange = 0.0f..120.0f,
                    defaultValue = 38.0f,
                    valueLabel = { if (it <= 0.5f) "Off" else String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
            }
            SectionFooter(
                "Side and bottom glass thickness can be adjusted or turned completely off (0 dp). Center of the screen remains 100% transparent wallpaper.",
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
                    onClick = { onNavigate(Destination.LockScreenSheetGlassTuning) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsNavRow(
                    title = "Glass Lighting & Highlights",
                    subtitle = "Specular rims, perimeter hairline & inner glow",
                    colors = colors,
                    onClick = { onNavigate(Destination.LockScreenSheetLighting) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsNavRow(
                    title = "Home Indicator Pill",
                    subtitle = "Pill dimensions, height & bottom margin",
                    colors = colors,
                    onClick = { onNavigate(Destination.LockScreenSheetHomeIndicator) },
                    enabled = sheet.enabled
                )
            }
            SectionFooter(
                "Configure optical refraction parameters, specular highlight reflections, and bottom home indicator styling.",
                colors
            )
        }
    }
}
