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
            keypad.setDragMaxThreshold(ModuleDefaults.LockScreenKeypad.DRAG_MAX_THRESHOLD)
            keypad.setDragInitialStiffness(ModuleDefaults.LockScreenKeypad.DRAG_INITIAL_STIFFNESS)
            keypad.setDragResistancePower(ModuleDefaults.LockScreenKeypad.DRAG_RESISTANCE_POWER)
            keypad.setDragVolumeStretch(ModuleDefaults.LockScreenKeypad.DRAG_VOLUME_STRETCH)
            keypad.setPressLiquidScale(ModuleDefaults.LockScreenKeypad.PRESS_LIQUID_SCALE)
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
                    valueRange = ModuleDefaults.LockScreenKeypad.DRAG_MAX_THRESHOLD_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.DRAG_MAX_THRESHOLD,
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
                    valueRange = ModuleDefaults.LockScreenKeypad.DRAG_INITIAL_STIFFNESS_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.DRAG_INITIAL_STIFFNESS,
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
                    valueRange = ModuleDefaults.LockScreenKeypad.DRAG_RESISTANCE_POWER_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.DRAG_RESISTANCE_POWER,
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
                    valueRange = ModuleDefaults.LockScreenKeypad.DRAG_VOLUME_STRETCH_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.DRAG_VOLUME_STRETCH,
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
                    valueRange = ModuleDefaults.LockScreenKeypad.PRESS_LIQUID_SCALE_RANGE,
                    defaultValue = ModuleDefaults.LockScreenKeypad.PRESS_LIQUID_SCALE,
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
