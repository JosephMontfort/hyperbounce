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
fun KeypadCascadePhysicsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Cascade Entrance Inertia",
        subtitle = "Y-axis motion coupling, vertical squish & fluid landing",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            keypad.setCascadeInertiaCoupling(1.0f)
            keypad.setCascadeLandingSquish(0.24f)
            keypad.setCascadeFlightElongation(0.22f)
            keypad.setCascadeViscousDamping(0.75f)
            keypad.setEntryTranslationY(90.0f)
            keypad.setEntryDuration(420.0f)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("Inertial Mass Transfer Coupling", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Y-Axis Motion Coupling",
                    subtitle = "Couples internal liquid mass strictly to vertical deceleration",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.cascadeInertiaCoupling,
                    onValueChange = keypad::setCascadeInertiaCoupling,
                    valueRange = 0.0f..1.5f,
                    defaultValue = 1.0f,
                    valueLabel = { String.format(Locale.US, "%.1f x", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Impact Landing Squish",
                    subtitle = "Horizontal expansion driven by braking deceleration",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.cascadeLandingSquish,
                    onValueChange = keypad::setCascadeLandingSquish,
                    valueRange = 0.05f..0.50f,
                    defaultValue = 0.24f,
                    valueLabel = { String.format(Locale.US, "%.2f x", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Flight Fluid Elongation",
                    subtitle = "Dynamic vertical elongation along motion path during rapid rise",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.cascadeFlightElongation,
                    onValueChange = keypad::setCascadeFlightElongation,
                    valueRange = 0.05f..0.45f,
                    defaultValue = 0.22f,
                    valueLabel = { String.format(Locale.US, "%.2f x", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Viscous Damping Rate",
                    subtitle = "Speed at which fluid rebound wobble settles to circular shape",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.cascadeViscousDamping,
                    onValueChange = keypad::setCascadeViscousDamping,
                    valueRange = 0.30f..1.00f,
                    defaultValue = 0.75f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
            }
            SectionFooter(
                "Inertial deformation is physically synchronized with the y-axis motion of each button. Squishing occurs during deceleration and landing rather than lingering after settling.",
                colors
            )
        }

        item {
            SectionHeader("Cascade Motion Trajectory", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Entry Y-Drop Distance",
                    subtitle = "Starting vertical travel distance for upward cascade flight",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.entryTranslationY,
                    onValueChange = keypad::setEntryTranslationY,
                    valueRange = 30.0f..200.0f,
                    defaultValue = 90.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Entrance Duration",
                    subtitle = "Overall duration of each button's arrival and landing",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.entryDuration,
                    onValueChange = keypad::setEntryDuration,
                    valueRange = 200.0f..1000.0f,
                    defaultValue = 420.0f,
                    valueLabel = { String.format(Locale.US, "%.0f ms", it) },
                    enabled = keypad.enabled
                )
            }
        }
    }
}
