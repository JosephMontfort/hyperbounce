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
            keypad.setIosBloomDuration(ModuleDefaults.LockScreenKeypad.BLOOM_DURATION)
            keypad.setIosBloomClusterFactor(ModuleDefaults.LockScreenKeypad.BLOOM_CLUSTER_FACTOR)
            keypad.setIosBloomOvershoot(ModuleDefaults.LockScreenKeypad.BLOOM_OVERSHOOT)
            keypad.setIosBloomDamping(ModuleDefaults.LockScreenKeypad.BLOOM_DAMPING)
            keypad.setIosBloomStiffness(ModuleDefaults.LockScreenKeypad.BLOOM_STIFFNESS)
            keypad.setIosBloomRadialSquish(ModuleDefaults.LockScreenKeypad.BLOOM_RADIAL_SQUISH)
            keypad.setIosBloomVelocityStretch(ModuleDefaults.LockScreenKeypad.BLOOM_VELOCITY_STRETCH)
            keypad.setIosBloomWaveStagger(ModuleDefaults.LockScreenKeypad.BLOOM_WAVE_STAGGER)
            keypad.setIosBloomDecoupleText(ModuleDefaults.LockScreenKeypad.BLOOM_DECOUPLE_TEXT)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
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
                    valueRange = ModuleDefaults.LockScreenKeypad.BLOOM_DURATION_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.BLOOM_DURATION,
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
                    valueRange = ModuleDefaults.LockScreenKeypad.BLOOM_CLUSTER_FACTOR_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.BLOOM_CLUSTER_FACTOR,
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
                    valueRange = ModuleDefaults.LockScreenKeypad.BLOOM_DAMPING_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.BLOOM_DAMPING,
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
                    valueRange = ModuleDefaults.LockScreenKeypad.BLOOM_STIFFNESS_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.BLOOM_STIFFNESS,
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
