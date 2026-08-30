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
import dev.joseph.hyperbounce.ui.components.SubpageScaffold
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors

@Composable
fun AppTransitionsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val trans = repository.appTransition

    SubpageScaffold(
        title = "App Transitions",
        subtitle = "Launcher app open & close spring channel physics",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) {
        SectionHeader("MASTER SWITCH", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsToggleRow(
                title = "Enable App Transition Hooks",
                subtitle = "Remaps spring parameter bundles in MIUI Launcher",
                checked = trans.enabled,
                colors = colors,
                onCheckedChange = trans::setEnabled
            )
        }
        SectionFooter(
            "Hooks SpringParamsProvider to dynamically customize per-channel spring curves.",
            colors
        )

        SectionHeader("POSITION CHANNEL (centerX, centerY)", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsSliderRow(
                title = "Flight Path Damping",
                subtitle = "Friction for center X and Y movement (lower = bouncier)",
                value = trans.posDamping,
                valueRange = 0.30f..1.0f,
                colors = colors,
                onValueChange = trans::setPosDamping
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Flight Path Response",
                subtitle = "Response time for center position channels",
                value = trans.posResponse,
                valueRange = 0.10f..1.50f,
                colors = colors,
                onValueChange = trans::setPosResponse
            )
        }
        SectionFooter("Controls the travel curve as the app window expands from or collapses to its icon.", colors)

        SectionHeader("SCALE CHANNEL (Width, Height, Ratio)", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsSliderRow(
                title = "Window Scale Damping",
                subtitle = "Damping for Width, Height, and Ratio channels",
                value = trans.scaleDamping,
                valueRange = 0.30f..1.0f,
                colors = colors,
                onValueChange = trans::setScaleDamping
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Window Scale Response",
                subtitle = "Response for Width, Height, and Ratio channels",
                value = trans.scaleResponse,
                valueRange = 0.10f..1.50f,
                colors = colors,
                onValueChange = trans::setScaleResponse
            )
        }
        SectionFooter("Controls geometric expansion stiffness and stretch during launch and recents dismiss.", colors)

        SectionHeader("CORNER RADIUS CHANNEL (RadiusRatio, Radius)", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsSliderRow(
                title = "Corner Morph Damping",
                subtitle = "Damping for corner radius interpolation",
                value = trans.cornerDamping,
                valueRange = 0.30f..1.0f,
                colors = colors,
                onValueChange = trans::setCornerDamping
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Corner Morph Response",
                subtitle = "Response time for corner radius morphing",
                value = trans.cornerResponse,
                valueRange = 0.10f..1.50f,
                colors = colors,
                onValueChange = trans::setCornerResponse
            )
        }
        SectionFooter("Smoothness of rectangular window morphing into rounded squircle icon.", colors)

        SectionHeader("SURFACE FADE CHANNEL (alpha, SurfaceExtAlpha)", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsSliderRow(
                title = "Surface Fade Damping",
                subtitle = "Damping for opacity and leash blur channels",
                value = trans.alphaDamping,
                valueRange = 0.30f..1.0f,
                colors = colors,
                onValueChange = trans::setAlphaDamping
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Surface Fade Response",
                subtitle = "Response time for opacity channels",
                value = trans.alphaResponse,
                valueRange = 0.10f..1.50f,
                colors = colors,
                onValueChange = trans::setAlphaResponse
            )
        }
        SectionFooter("Fade duration and transparency envelope during app entry and exit.", colors)

        SectionHeader("SPATIAL TRAJECTORY & 3D DEPTH", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsSliderRow(
                title = "Vertical Parabolic Arc",
                subtitle = "Vertical flight lift applied during transition envelope",
                value = trans.appYArc,
                valueRange = -200f..300f,
                valueFormat = "%.0f dp",
                colors = colors,
                onValueChange = trans::setAppYArc
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "3D Bottom-Edge Compression",
                subtitle = "Perspective squeeze along bottom window boundary",
                value = trans.appBottomZWarp,
                valueRange = 0.0f..0.70f,
                colors = colors,
                onValueChange = trans::setAppBottomZWarp
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Horizontal Sway Curve",
                subtitle = "Lateral trajectory offset through flight",
                value = trans.appXSway,
                valueRange = -150f..150f,
                valueFormat = "%.0f dp",
                colors = colors,
                onValueChange = trans::setAppXSway
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Gesture Interrupt Stabilizer",
                subtitle = "Rate of recovery when interrupting an ongoing animation",
                value = trans.appInterruptSmooth,
                valueRange = 0.1f..1.5f,
                colors = colors,
                onValueChange = trans::setAppInterruptSmooth
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Ghost Icon 3D Tilt Angle",
                subtitle = "3D perspective tilt angle for recents ghost icon",
                value = trans.appIconPitchDeg,
                valueRange = 0.0f..45.0f,
                valueFormat = "%.0f°",
                colors = colors,
                onValueChange = trans::setAppIconPitchDeg
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "3D Camera Distance",
                subtitle = "Virtual camera focal distance for 3D projections",
                value = trans.appIconCameraDist,
                valueRange = 2000f..15000f,
                valueFormat = "%.0f",
                colors = colors,
                onValueChange = trans::setAppIconCameraDist
            )
        }
        SectionFooter("Spatial 3D transformations applied during app launch and dismiss flight paths.", colors)
    }
}
