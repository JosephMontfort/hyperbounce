package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun AppTransitionsScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val trans = repository.appTransition
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "App Transitions",
        subtitle = "Launcher app open & close spring channel physics",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = trans::reloadFromDefaults,
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartLauncher(context) }
    ) { _ ->
        item {
            SectionHeader("MASTER SWITCH", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Enable App Transition Hooks",
                    subtitle = "Remaps spring parameter bundles in MIUI Launcher",
                    colors = colors,
                    iconTint = colors.accentGreen,
                    backdrop = backdrop,
                    checked = trans.enabled,
                    onCheckedChange = trans::setEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
            }
        }

        item {
            SectionHeader("POSITION CHANNEL (centerX, centerY)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Flight Path Damping",
                    subtitle = "Lower = more flight bounce on open/close, Higher = stiffer flight settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.posDamping,
                    onValueChange = trans::setPosDamping,
                    valueRange = 0.30f..1.0f,
                    defaultValue = 0.80f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Flight Path Response",
                    subtitle = "Lower = window snaps faster along path, Higher = floats along path",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.posResponse,
                    onValueChange = trans::setPosResponse,
                    valueRange = 0.10f..1.50f,
                    defaultValue = 0.40f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
            }
        }

        item {
            SectionHeader("SCALE CHANNEL (Width, Height, Ratio)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Window Scale Damping",
                    subtitle = "Lower = more elastic window scale bounce, Higher = rigid scale settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.scaleDamping,
                    onValueChange = trans::setScaleDamping,
                    valueRange = 0.30f..1.0f,
                    defaultValue = 0.80f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Window Scale Response",
                    subtitle = "Lower = window expands faster, Higher = window expands smoothly",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.scaleResponse,
                    onValueChange = trans::setScaleResponse,
                    valueRange = 0.10f..1.50f,
                    defaultValue = 0.40f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
            }
        }

        item {
            SectionHeader("CORNER RADIUS CHANNEL (RadiusRatio, Radius)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Corner Morph Damping",
                    subtitle = "Lower = springy corner rounding, Higher = smooth corner settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.cornerDamping,
                    onValueChange = trans::setCornerDamping,
                    valueRange = 0.30f..1.0f,
                    defaultValue = 1.00f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Corner Morph Response",
                    subtitle = "Lower = morphs corners rapidly, Higher = morphs corners smoothly",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.cornerResponse,
                    onValueChange = trans::setCornerResponse,
                    valueRange = 0.10f..1.50f,
                    defaultValue = 0.35f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
            }
        }

        item {
            SectionHeader("SURFACE FADE CHANNEL (alpha, SurfaceExtAlpha)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Surface Fade Damping",
                    subtitle = "Lower = bouncy opacity fade, Higher = smooth critical opacity fade",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.alphaDamping,
                    onValueChange = trans::setAlphaDamping,
                    valueRange = 0.30f..1.0f,
                    defaultValue = 1.00f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Surface Fade Response",
                    subtitle = "Lower = fades opacity quickly, Higher = fades opacity smoothly",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.alphaResponse,
                    onValueChange = trans::setAlphaResponse,
                    valueRange = 0.10f..1.50f,
                    defaultValue = 0.30f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
            }
        }

        item {
            SectionHeader("SPATIAL TRAJECTORY & 3D DEPTH", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Vertical Parabolic Arc",
                    subtitle = "Vertical flight lift applied during transition envelope",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.appYArc,
                    onValueChange = trans::setAppYArc,
                    valueRange = -200f..300f,
                    defaultValue = 0.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "3D Bottom-Edge Compression",
                    subtitle = "Perspective squeeze along bottom window boundary",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.appBottomZWarp,
                    onValueChange = trans::setAppBottomZWarp,
                    valueRange = 0.0f..0.70f,
                    defaultValue = 0.0f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Horizontal Sway Curve",
                    subtitle = "Lateral trajectory offset through flight",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.appXSway,
                    onValueChange = trans::setAppXSway,
                    valueRange = -150f..150f,
                    defaultValue = 0.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Gesture Interrupt Stabilizer",
                    subtitle = "Lower = loose gesture takeover, Higher = instant smooth gesture recovery",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.appInterruptSmooth,
                    onValueChange = trans::setAppInterruptSmooth,
                    valueRange = 0.1f..1.5f,
                    defaultValue = 1.0f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Ghost Icon 3D Tilt Angle",
                    subtitle = "3D perspective tilt angle for recents ghost icon",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.appIconPitchDeg,
                    onValueChange = trans::setAppIconPitchDeg,
                    valueRange = 0.0f..45.0f,
                    defaultValue = 0.0f,
                    valueLabel = { String.format(Locale.US, "%.0f°", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "3D Camera Distance",
                    subtitle = "Virtual camera focal distance for 3D projections",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.appIconCameraDist,
                    onValueChange = trans::setAppIconCameraDist,
                    valueRange = 2000f..15000f,
                    defaultValue = 8000.0f,
                    valueLabel = { String.format(Locale.US, "%.0f", it) },
                    enabled = trans.enabled
                )
            }
        }
    }
}
