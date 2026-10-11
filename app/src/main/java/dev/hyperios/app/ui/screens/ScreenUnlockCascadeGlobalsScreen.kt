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
fun ScreenUnlockCascadeGlobalsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val unlock = repository.screenUnlock
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Global Kinetics & Velocity",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            unlock.setDurationScale(ModuleDefaults.ScreenUnlock.DURATION_SCALE)
            unlock.setVelocityInfluence(ModuleDefaults.ScreenUnlock.VELOCITY_INFLUENCE)
            unlock.setMaxVelocity(ModuleDefaults.ScreenUnlock.MAX_VELOCITY)
            unlock.setCascadeDamping(ModuleDefaults.ScreenUnlock.CASCADE_DAMPING)
        },
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartLauncher(context) }
    ) { _ ->
        item {
            SectionHeader("Kinetic Y-Axis Unlock Globals", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Global Anim Duration",
                    subtitle = "Lower = faster overall animation, Higher = slower motion",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.durationScale,
                    onValueChange = unlock::setDurationScale,
                    valueRange = ModuleDefaults.ScreenUnlock.DURATION_SCALE_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.DURATION_SCALE,
                    valueLabel = { String.format(Locale.US, "%.2fx", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Velocity Impact",
                    subtitle = "Lower = subtle speed scaling, Higher = strong speed scaling",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.velocityInfluence,
                    onValueChange = unlock::setVelocityInfluence,
                    valueRange = ModuleDefaults.ScreenUnlock.VELOCITY_INFLUENCE_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.VELOCITY_INFLUENCE,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Max Swipe Normalizer",
                    subtitle = "Ceiling velocity for maximum kinetic bounce",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.maxVelocity,
                    onValueChange = unlock::setMaxVelocity,
                    valueRange = ModuleDefaults.ScreenUnlock.MAX_VELOCITY_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.MAX_VELOCITY,
                    valueLabel = { String.format(Locale.US, "%.0f px/s", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Resting Damping",
                    subtitle = "Lower = bouncy settling curve, Higher = smooth critical damping",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.cascadeDamping,
                    onValueChange = unlock::setCascadeDamping,
                    valueRange = ModuleDefaults.ScreenUnlock.CASCADE_DAMPING_RANGE,
                    defaultValue = ModuleDefaults.ScreenUnlock.CASCADE_DAMPING,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
            }
            SectionFooter(
                "Global animation duration multiplier, swipe velocity momentum coupling, and harmonic settling tension.",
                colors
            )
        }
    }
}
