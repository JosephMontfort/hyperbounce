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
fun KeypadReleasePhysicsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Release Snapback Dynamics",
        subtitle = "Directional inertia mass transfer & rebound wobble",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            keypad.setReleaseDuration(ModuleDefaults.LockScreenKeypad.RELEASE_DURATION)
            keypad.setReleaseSquishAmp(ModuleDefaults.LockScreenKeypad.RELEASE_SQUISH_AMP)
            keypad.setReleaseOvershoot(ModuleDefaults.LockScreenKeypad.RELEASE_OVERSHOOT)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("Directional Inertia Squish", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Inertia Squish Amplitude",
                    subtitle = "Liquid mass transfer squish scaled by pull distance & vector",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.releaseSquishAmp,
                    onValueChange = keypad::setReleaseSquishAmp,
                    valueRange = ModuleDefaults.LockScreenKeypad.RELEASE_SQUISH_AMP_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.RELEASE_SQUISH_AMP,
                    valueLabel = { String.format(Locale.US, "%.2f x", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Snapback Duration",
                    subtitle = "Total time for center restitution and damped fluid wobble",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.releaseDuration,
                    onValueChange = keypad::setReleaseDuration,
                    valueRange = ModuleDefaults.LockScreenKeypad.RELEASE_DURATION_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.RELEASE_DURATION,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Snapback Spring Tension",
                    subtitle = "Restoring spring tension crossing center into opposite overshoot",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.releaseOvershoot,
                    onValueChange = keypad::setReleaseOvershoot,
                    valueRange = ModuleDefaults.LockScreenKeypad.RELEASE_OVERSHOOT_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.RELEASE_OVERSHOOT,
                    valueLabel = { String.format(Locale.US, "%.1f", it) },
                    enabled = keypad.enabled
                )
            }
            SectionFooter(
                "When released from an arbitrary angle and distance, the droplet snaps back along that vector. Fluid momentum slams forward at the opposite overshoot boundary, squishing along the release trajectory and expanding transversely.",
                colors
            )
        }
    }
}
