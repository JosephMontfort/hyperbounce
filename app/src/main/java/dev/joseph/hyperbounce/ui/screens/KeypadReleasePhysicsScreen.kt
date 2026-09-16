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
            keypad.setReleaseDuration(320.0f)
            keypad.setReleaseSquishAmp(0.35f)
            keypad.setReleaseOvershoot(2.0f)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
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
                    valueRange = 0.0f..0.70f,
                    defaultValue = 0.35f,
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
                    valueRange = 150.0f..600.0f,
                    defaultValue = 320.0f,
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
                    valueRange = 1.0f..3.5f,
                    defaultValue = 2.0f,
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
