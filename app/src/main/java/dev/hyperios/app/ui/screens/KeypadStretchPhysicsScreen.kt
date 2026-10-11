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
fun KeypadStretchPhysicsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Pull & Stretch Elasticity",
        subtitle = "Non-linear tension, thresholds & volume conservation",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            keypad.setDragMaxThreshold(0.16f)
            keypad.setDragInitialStiffness(0.22f)
            keypad.setDragResistancePower(1.8f)
            keypad.setDragVolumeStretch(0.11f)
            keypad.setPressLiquidScale(1.15f)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("Stretch Bounds & Resistance", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Max Stretch Threshold",
                    subtitle = "Maximum allowed drag displacement (fraction of diameter)",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.dragMaxThreshold,
                    onValueChange = keypad::setDragMaxThreshold,
                    valueRange = 0.10f..0.35f,
                    defaultValue = 0.16f,
                    valueLabel = { String.format(Locale.US, "%.0f %%", it * 100f) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Initial Pull Resistance",
                    subtitle = "Tactile stiffness when pulling starts (higher = firmer)",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.dragInitialStiffness,
                    onValueChange = keypad::setDragInitialStiffness,
                    valueRange = 0.10f..0.50f,
                    defaultValue = 0.22f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Non-Linear Resistance Power",
                    subtitle = "Rate at which resistance ramps up with pull distance",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.dragResistancePower,
                    onValueChange = keypad::setDragResistancePower,
                    valueRange = 1.0f..3.5f,
                    defaultValue = 1.8f,
                    valueLabel = { String.format(Locale.US, "%.1f", it) },
                    enabled = keypad.enabled
                )
            }
            SectionFooter(
                "Resistance increases non-linearly as distance increases, giving a physical rubberband liquid bubble feel.",
                colors
            )
        }

        item {
            SectionHeader("Liquid Volume & Tactile Optics", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Anisotropic Stretch Factor",
                    subtitle = "Longitudinal elongation intensity along the drag vector",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.dragVolumeStretch,
                    onValueChange = keypad::setDragVolumeStretch,
                    valueRange = 0.05f..0.50f,
                    defaultValue = 0.11f,
                    valueLabel = { String.format(Locale.US, "%.2f x", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Tactile Press Scale",
                    subtitle = "Baseline tactile button expansion on finger touch down",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.pressLiquidScale,
                    onValueChange = keypad::setPressLiquidScale,
                    valueRange = 0.80f..1.50f,
                    defaultValue = 1.15f,
                    valueLabel = { String.format(Locale.US, "%.2f x", it) },
                    enabled = keypad.enabled
                )
            }
            SectionFooter(
                "Volume preservation enforces s_x * s_y = baseScale^2 throughout deformation. When pulled along any angle, the transverse axis compresses proportionally.",
                colors
            )
        }
    }
}
