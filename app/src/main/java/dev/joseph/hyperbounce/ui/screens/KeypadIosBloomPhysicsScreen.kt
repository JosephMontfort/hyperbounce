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

/**
 * KeypadIosBloomPhysicsScreen: Controls for the iOS 26 Radial Bloom entrance kinetics.
 *
 * NOTE FOR FUTURE AGENTS / DEVELOPERS:
 * Per user request, the Inertia Squish & Velocity Stretch controls (radial squish, velocity stretch,
 * wave stagger) and the Decouple Digit Labels toggle have been removed from the UI alone to keep the
 * interface clean and focused. Their corresponding backend preferences and defaults
 * (squish: 0.28, stretch: 0.16, stagger: 22ms, decouple: false) remain fully active in the backend hook.
 */
@Composable
fun KeypadIosBloomPhysicsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "iOS 26 Liquid Bloom Physics",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            keypad.setIosBloomDuration(900.0f)
            keypad.setIosBloomClusterFactor(0.60f)
            keypad.setIosBloomOvershoot(0.16f)
            keypad.setIosBloomDamping(0.40f)
            keypad.setIosBloomStiffness(18.0f)
            keypad.setIosBloomRadialSquish(0.28f)
            keypad.setIosBloomVelocityStretch(0.16f)
            keypad.setIosBloomWaveStagger(22.0f)
            keypad.setIosBloomDecoupleText(false)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("Radial Bloom Kinetics", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Bloom Duration",
                    subtitle = "Overall time for radial bloom expansion and settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.iosBloomDuration,
                    onValueChange = keypad::setIosBloomDuration,
                    valueRange = 300.0f..1200.0f,
                    defaultValue = 900.0f,
                    valueLabel = { "${it.toInt()} ms" },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Cluster Origin Compaction",
                    subtitle = "How tightly buttons start clustered around central Key 5",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.iosBloomClusterFactor,
                    onValueChange = keypad::setIosBloomClusterFactor,
                    valueRange = 0.30f..0.95f,
                    defaultValue = 0.60f,
                    valueLabel = { String.format(Locale.US, "%.0f%%", it * 100f) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Spring Damping Ratio (ζ)",
                    subtitle = "Harmonic oscillation damping (lower = bouncier, higher = stiffer)",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.iosBloomDamping,
                    onValueChange = keypad::setIosBloomDamping,
                    valueRange = 0.20f..0.85f,
                    defaultValue = 0.40f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Spring Angular Frequency (ω)",
                    subtitle = "Radial bounce speed and elasticity frequency",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.iosBloomStiffness,
                    onValueChange = keypad::setIosBloomStiffness,
                    valueRange = 8.0f..25.0f,
                    defaultValue = 18.0f,
                    valueLabel = { String.format(Locale.US, "%.1f rad/s", it) },
                    enabled = keypad.enabled
                )
            }
            SectionFooter(
                "Buttons originate in an organic cluster behind Key 5 and shoot outward radially into their final grid coordinates.",
                colors
            )
        }
    }
}
