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
            unlock.setKineticDockDamping(ModuleDefaults.ScreenUnlock.KINETIC_DOCK_DAMPING)
            unlock.setKineticDockResponse(ModuleDefaults.ScreenUnlock.KINETIC_DOCK_RESPONSE)
            unlock.setKineticDockZDepth(ModuleDefaults.ScreenUnlock.KINETIC_DOCK_Z_DEPTH)
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
                    valueRange = ModuleDefaults.ScreenUnlock.KINETIC_DOCK_DAMPING_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.KINETIC_DOCK_DAMPING,
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
                    valueRange = ModuleDefaults.ScreenUnlock.KINETIC_DOCK_RESPONSE_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.KINETIC_DOCK_RESPONSE,
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
                    valueRange = ModuleDefaults.ScreenUnlock.KINETIC_DOCK_Z_DEPTH_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.KINETIC_DOCK_Z_DEPTH,
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
