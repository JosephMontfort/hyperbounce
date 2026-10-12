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
            unlock.setCenterDamping(ModuleDefaults.ScreenUnlock.CENTER_DAMPING)
            unlock.setCenterResponse(ModuleDefaults.ScreenUnlock.CENTER_RESPONSE)
            unlock.setEdgeDampingDrop(ModuleDefaults.ScreenUnlock.EDGE_DAMPING_DROP)
            unlock.setEdgeResponseDelay(ModuleDefaults.ScreenUnlock.EDGE_RESPONSE_DELAY)
            unlock.setZDepthMultiplier(ModuleDefaults.ScreenUnlock.Z_DEPTH_MULTIPLIER)
            unlock.setMinResponse(ModuleDefaults.ScreenUnlock.MIN_RESPONSE)
            unlock.setMaxResponse(ModuleDefaults.ScreenUnlock.MAX_RESPONSE)
            unlock.setMinDamping(ModuleDefaults.ScreenUnlock.MIN_DAMPING)
            unlock.setMaxDamping(ModuleDefaults.ScreenUnlock.MAX_DAMPING)
        },
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartLauncher(context) }
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
                    valueRange = ModuleDefaults.ScreenUnlock.CENTER_DAMPING_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.CENTER_DAMPING,
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
                    valueRange = ModuleDefaults.ScreenUnlock.CENTER_RESPONSE_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.CENTER_RESPONSE,
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
                    valueRange = ModuleDefaults.ScreenUnlock.EDGE_DAMPING_DROP_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.EDGE_DAMPING_DROP,
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
                    valueRange = ModuleDefaults.ScreenUnlock.EDGE_RESPONSE_DELAY_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.EDGE_RESPONSE_DELAY,
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
                    valueRange = ModuleDefaults.ScreenUnlock.Z_DEPTH_MULTIPLIER_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.Z_DEPTH_MULTIPLIER,
                    valueLabel = { String.format(Locale.US, "%.2fx", it) },
                    enabled = unlock.enabled
                )
            }
            SectionFooter(
                "Tune the outward harmonic spring dispersion of home screen icons from the radial center.",
                colors
            )
        }
        item {
            SectionHeader("Dynamic Clamp Bounds", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Minimum Response Clamp",
                    subtitle = "Floor boundary for radial dispersion response duration",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.minResponse,
                    onValueChange = unlock::setMinResponse,
                    valueRange = ModuleDefaults.ScreenUnlock.MIN_RESPONSE_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.MIN_RESPONSE,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Maximum Response Clamp",
                    subtitle = "Ceiling boundary for radial dispersion response duration",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.maxResponse,
                    onValueChange = unlock::setMaxResponse,
                    valueRange = ModuleDefaults.ScreenUnlock.MAX_RESPONSE_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.MAX_RESPONSE,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Minimum Damping Clamp",
                    subtitle = "Floor boundary for spring oscillation settling damping",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.minDamping,
                    onValueChange = unlock::setMinDamping,
                    valueRange = ModuleDefaults.ScreenUnlock.MIN_DAMPING_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.MIN_DAMPING,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Maximum Damping Clamp",
                    subtitle = "Ceiling boundary for spring oscillation settling damping",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.maxDamping,
                    onValueChange = unlock::setMaxDamping,
                    valueRange = ModuleDefaults.ScreenUnlock.MAX_DAMPING_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.MAX_DAMPING,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
            }
            SectionFooter(
                "Define boundary constraints preventing harmonic physics from destabilizing or clipping.",
                colors
            )
        }
    }
}
