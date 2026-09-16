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
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

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
        subtitle = "Radial bloom, 2D inertia squish & decoupled glass lenses",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            keypad.setIosBloomDuration(520.0f)
            keypad.setIosBloomClusterFactor(0.70f)
            keypad.setIosBloomOvershoot(0.16f)
            keypad.setIosBloomDamping(0.54f)
            keypad.setIosBloomStiffness(13.5f)
            keypad.setIosBloomRadialSquish(0.28f)
            keypad.setIosBloomVelocityStretch(0.16f)
            keypad.setIosBloomWaveStagger(22.0f)
            keypad.setIosBloomDecoupleText(true)
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
                    valueRange = 300.0f..900.0f,
                    defaultValue = 520.0f,
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
                    defaultValue = 0.70f,
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
                    valueRange = 0.30f..0.85f,
                    defaultValue = 0.54f,
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
                    valueRange = 8.0f..22.0f,
                    defaultValue = 13.5f,
                    valueLabel = { String.format(Locale.US, "%.1f rad/s", it) },
                    enabled = keypad.enabled
                )
            }
            SectionFooter(
                "Buttons originate in an organic cluster behind Key 5 and shoot outward radially into their final grid coordinates.",
                colors
            )
        }

        item {
            SectionHeader("Inertia Squish & Velocity Stretch", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Radial Braking Squish",
                    subtitle = "Compressive mass deformation when outer buttons hit peak overshoot",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.iosBloomRadialSquish,
                    onValueChange = keypad::setIosBloomRadialSquish,
                    valueRange = 0.05f..0.60f,
                    defaultValue = 0.28f,
                    valueLabel = { String.format(Locale.US, "%.0f%%", it * 100f) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "In-Flight Velocity Stretch",
                    subtitle = "Dynamic elongation along radial travel vector during outward burst",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.iosBloomVelocityStretch,
                    onValueChange = keypad::setIosBloomVelocityStretch,
                    valueRange = 0.02f..0.40f,
                    defaultValue = 0.16f,
                    valueLabel = { String.format(Locale.US, "%.0f%%", it * 100f) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Radial Shockwave Delay",
                    subtitle = "Stagger propagation delay per grid distance unit from center",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.iosBloomWaveStagger,
                    onValueChange = keypad::setIosBloomWaveStagger,
                    valueRange = 0.0f..50.0f,
                    defaultValue = 22.0f,
                    valueLabel = { "${it.toInt()} ms" },
                    enabled = keypad.enabled
                )
            }
            SectionFooter(
                "Volume (2D area) is strictly preserved: radial compression automatically causes transverse bulge, reproducing iOS 26 fluid mechanics.",
                colors
            )
        }

        item {
            SectionHeader("Layer Architecture", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Decouple Digit Labels",
                    subtitle = "Lock numbers & letters stationary while liquid glass blooms underneath",
                    colors = colors,
                    value = keypad.iosBloomDecoupleText,
                    onValueChange = keypad::setIosBloomDecoupleText,
                    enabled = keypad.enabled
                )
            }
            SectionFooter(
                "In iOS 26, digit labels fade in directly at their destination while the liquid glass lenses explode and bounce underneath.",
                colors
            )
        }
    }
}
