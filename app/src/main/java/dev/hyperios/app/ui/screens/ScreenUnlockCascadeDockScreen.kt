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
fun ScreenUnlockCascadeDockScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val unlock = repository.screenUnlock
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Kinetic Dock Isolation",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            unlock.setKineticDockDamping(1.00f)
            unlock.setKineticDockResponse(0.55f)
            unlock.setKineticDockZDepth(800.0f)
        },
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartLauncher(context) }
    ) { _ ->
        item {
            SectionHeader("Dock Isolation", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Dock Damping",
                    subtitle = "Lower = bouncier dock icons, Higher = stiffer dock settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.kineticDockDamping,
                    onValueChange = unlock::setKineticDockDamping,
                    valueRange = 0.1f..1.0f,
                    defaultValue = 1.00f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Dock Response",
                    subtitle = "Lower = snaps dock faster, Higher = slower dock arrival",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.kineticDockResponse,
                    onValueChange = unlock::setKineticDockResponse,
                    valueRange = 0.1f..1.5f,
                    defaultValue = 0.55f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Dock Z-Depth",
                    subtitle = "Starting 3D perspective depth for bottom dock",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.kineticDockZDepth,
                    onValueChange = unlock::setKineticDockZDepth,
                    valueRange = 0f..4000f,
                    defaultValue = 800.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = unlock.enabled
                )
            }
            SectionFooter(
                "Isolate the bottom dock icons from standard cascade rows with custom damping, response, and initial 3D Z-depth.",
                colors
            )
        }
    }
}
