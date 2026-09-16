package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun KeypadPhysicsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "PIN Pad Liquid Dynamics",
        subtitle = "Inertial mass transfer, non-linear drag & release physics",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            keypad.setEntryAnimationType("ios26")
            keypad.setIosBloomDuration(520.0f)
            keypad.setIosBloomClusterFactor(0.70f)
            keypad.setIosBloomOvershoot(0.16f)
            keypad.setIosBloomDamping(0.54f)
            keypad.setIosBloomStiffness(13.5f)
            keypad.setIosBloomRadialSquish(0.28f)
            keypad.setIosBloomVelocityStretch(0.16f)
            keypad.setIosBloomWaveStagger(22.0f)
            keypad.setIosBloomDecoupleText(true)
            keypad.setDragMaxThreshold(0.28f)
            keypad.setDragInitialStiffness(0.45f)
            keypad.setDragResistancePower(1.8f)
            keypad.setDragVolumeStretch(0.22f)
            keypad.setReleaseDuration(320.0f)
            keypad.setReleaseSquishAmp(0.35f)
            keypad.setReleaseOvershoot(2.0f)
            keypad.setCascadeFlightElongation(0.22f)
            keypad.setCascadeLandingSquish(0.24f)
            keypad.setCascadeInertiaCoupling(1.0f)
            keypad.setCascadeViscousDamping(0.75f)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("Entrance Animation Engine", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                val isIos26 = keypad.entryAnimationType == "ios26"
                dev.joseph.hyperbounce.ui.components.SettingsClickableRow(
                    title = "iOS 26 Liquid Bloom",
                    subtitle = "Radial bloom from center with decoupled liquid lenses & 2D squish",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    onClick = { keypad.setEntryAnimationType("ios26") },
                    trailing = if (isIos26) {
                        { dev.joseph.hyperbounce.ui.components.CheckmarkIcon(color = colors.accentBlue, size = 16.dp) }
                    } else null,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                dev.joseph.hyperbounce.ui.components.SettingsClickableRow(
                    title = "Cascade Flight",
                    subtitle = "Sequential upward cascade glide with vertical deceleration squish",
                    colors = colors,
                    iconTint = colors.accentOrange,
                    onClick = { keypad.setEntryAnimationType("cascade") },
                    trailing = if (!isIos26) {
                        { dev.joseph.hyperbounce.ui.components.CheckmarkIcon(color = colors.accentOrange, size = 16.dp) }
                    } else null,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
            }
            SectionFooter(
                "Select the physical entrance model. iOS 26 Liquid Bloom accurately matches the native radial spring explosion with liquid inertia mass transfer.",
                colors
            )
        }

        item {
            SectionHeader("Physics Subpages", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsNavRow(
                    title = "iOS 26 Radial Bloom Physics",
                    subtitle = "Cluster origin, radial spring damping, 2D squish & decoupled lenses",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    onClick = { onNavigate(Destination.KeypadIosBloomPhysics) },
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsNavRow(
                    title = "Pull & Stretch Elasticity",
                    subtitle = "Threshold limits, non-linear resistance & volume stretch",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    onClick = { onNavigate(Destination.KeypadStretchPhysics) },
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsNavRow(
                    title = "Release Snapback Dynamics",
                    subtitle = "Directional liquid glass inertia squish & spring bounce",
                    colors = colors,
                    iconTint = colors.accentPurple,
                    onClick = { onNavigate(Destination.KeypadReleasePhysics) },
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsNavRow(
                    title = "Cascade Entrance Inertia",
                    subtitle = "Y-axis motion mass transfer, impact squish & liquid rebound",
                    colors = colors,
                    iconTint = colors.accentOrange,
                    onClick = { onNavigate(Destination.KeypadCascadePhysics) },
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
            }
            SectionFooter(
                "Tune individual phases of the liquid glass physical simulation. Each phase preserves 2D area (volume) throughout motion.",
                colors
            )
        }

        item {
            SectionHeader("Quick Dynamics Tuning", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Max Stretch Threshold",
                    subtitle = "Pull limit as ratio of button size (lower = tighter)",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.dragMaxThreshold,
                    onValueChange = keypad::setDragMaxThreshold,
                    valueRange = 0.15f..0.50f,
                    defaultValue = 0.28f,
                    valueLabel = { String.format(Locale.US, "%.0f %%", it * 100f) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Initial Pull Resistance",
                    subtitle = "Tactile firmness at the beginning of a drag",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.dragInitialStiffness,
                    onValueChange = keypad::setDragInitialStiffness,
                    valueRange = 0.15f..0.85f,
                    defaultValue = 0.45f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = keypad.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Release Inertia Squish",
                    subtitle = "Directional liquid compression on snapback arrival",
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
                    title = "Cascade Landing Squish",
                    subtitle = "Impact horizontal squish intensity upon arrival",
                    colors = colors,
                    backdrop = backdrop,
                    value = keypad.cascadeLandingSquish,
                    onValueChange = keypad::setCascadeLandingSquish,
                    valueRange = 0.05f..0.50f,
                    defaultValue = 0.24f,
                    valueLabel = { String.format(Locale.US, "%.2f x", it) },
                    enabled = keypad.enabled
                )
            }
        }
    }
}
