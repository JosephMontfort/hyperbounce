package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun ScreenUnlockRadialKineticsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val unlock = repository.screenUnlock
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Radial Spring Kinetics",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            unlock.setCenterDamping(0.58f)
            unlock.setCenterResponse(0.48f)
            unlock.setEdgeDampingDrop(0.10f)
            unlock.setEdgeResponseDelay(0.22f)
            unlock.setZDepthMultiplier(1.60f)
        },
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartLauncher(context) }
    ) { _ ->
        item {
            SectionHeader("Radial Dispersion Spring Physics", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Center Damping",
                    subtitle = "Lower = more bounce on release, Higher = stiffer settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.centerDamping,
                    onValueChange = unlock::setCenterDamping,
                    valueRange = 0.1f..1.0f,
                    defaultValue = 0.58f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Center Response",
                    subtitle = "Lower = snaps faster to position, Higher = slower float",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.centerResponse,
                    onValueChange = unlock::setCenterResponse,
                    valueRange = 0.1f..1.5f,
                    defaultValue = 0.48f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Edge Damping Drop",
                    subtitle = "Lower = uniform bounce across screen, Higher = looser edges",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.edgeDampingDrop,
                    onValueChange = unlock::setEdgeDampingDrop,
                    valueRange = 0.0f..0.5f,
                    defaultValue = 0.10f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Edge Response Delay",
                    subtitle = "Lower = simultaneous expansion, Higher = edges lag behind",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.edgeResponseDelay,
                    onValueChange = unlock::setEdgeResponseDelay,
                    valueRange = 0.0f..1.0f,
                    defaultValue = 0.22f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Z-Depth Scale",
                    subtitle = "Multiplies initial 3D depth perspective",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.zDepthMultiplier,
                    onValueChange = unlock::setZDepthMultiplier,
                    valueRange = 1.0f..4.0f,
                    defaultValue = 1.60f,
                    valueLabel = { String.format(Locale.US, "%.2fx", it) },
                    enabled = unlock.enabled
                )
            }
            SectionFooter(
                "Tune the outward harmonic spring dispersion of home screen icons from the radial center.",
                colors
            )
        }
    }
}
