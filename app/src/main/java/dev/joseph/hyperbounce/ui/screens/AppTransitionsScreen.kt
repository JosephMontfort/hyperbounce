package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.AppGridIcon
import dev.joseph.hyperbounce.ui.components.AppsIcon
import dev.joseph.hyperbounce.ui.components.GearIcon
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.PowerIcon
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
        subtitle = "Weightless Physics, Zero Drag Resistance & Parabolic Flight",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = trans::reloadFromDefaults,
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartLauncher(context) }
    ) { _ ->
        // 1. MASTER SWITCH
        item {
            SectionHeader("MASTER SWITCH", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Enable iOS Animation Engine",
                    subtitle = "Master switch for custom transition physics and ballistic flight curves",
                    colors = colors,
                    iconTint = colors.accentGreen,
                    backdrop = backdrop,
                    checked = trans.enabled,
                    onCheckedChange = trans::setEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
            }
            SectionFooter(
                "• Enables velocity-dependent parabolic trajectory arcs, 1:1 folder tracking, zero drag friction, and organic spring dynamics.",
                colors
            )
        }

        // 2. FLIGHT ARC & TRAJECTORY DYNAMICS
        item {
            SectionHeader("FLIGHT ARC & TRAJECTORY", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Flight Arc Apex Lift",
                    subtitle = "Parabolic trajectory height when closing lower-half apps",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.arcApexLift,
                    onValueChange = trans::setArcApexLift,
                    valueRange = 0.40f..2.00f,
                    defaultValue = 1.00f,
                    valueLabel = { String.format(Locale.US, "%.2fx", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Expansive Open Uplift",
                    subtitle = "Distinct upward launch surge when tapping bottom & dock apps",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = trans.openUpliftSurge,
                    onCheckedChange = trans::setOpenUpliftSurge,
                    enabled = trans.enabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Keystone Edge Squeeze",
                    subtitle = "Trapezoidal width taper (HMatchVTop) during vertical flight",
                    colors = colors,
                    iconTint = colors.accentPurple,
                    backdrop = backdrop,
                    checked = trans.keystoneSqueeze,
                    onCheckedChange = trans::setKeystoneSqueeze,
                    enabled = trans.enabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
            }
            SectionFooter(
                "• Flight Arc Lift: Higher values cause lower-half apps to soar higher into an apex before diving into their icon.\n• Open Uplift: Bottom apps surge upward before expanding outward to full display.\n• Keystone Squeeze: Naturally tapers window boundaries during vertical climb.",
                colors
            )
        }

        // 3. SPRING DYNAMICS & SPEED
        item {
            SectionHeader("SPRING DYNAMICS & SPEED", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Animation Speed",
                    subtitle = "Higher = snappier & faster, Lower = more relaxed & fluid",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.animSpeed,
                    onValueChange = trans::setAnimSpeed,
                    valueRange = 0.50f..1.80f,
                    defaultValue = 1.00f,
                    valueLabel = { String.format(Locale.US, "%.2fx", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Spring Damping Ratio",
                    subtitle = "Settling elasticity (Lower = subtle bounce, Higher = critically damped)",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.springDamping,
                    onValueChange = trans::setSpringDamping,
                    valueRange = 0.80f..1.15f,
                    defaultValue = 0.98f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
            }
            SectionFooter(
                "• Animation Speed: Dynamically scales open, close, and recents spring response duration.\n• Damping Ratio: Controls whether windows settle with an organic elastic rebound or crisp critical damping.",
                colors
            )
        }

        // 4. FOLDER & RECENTS GESTURES
        item {
            SectionHeader("FOLDER & RECENTS GESTURES", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "1:1 Folder Icon Trajectories",
                    subtitle = "Desktop preview icons track 1:1 into open folder positions with zero outline border",
                    colors = colors,
                    iconTint = colors.accentOrange,
                    backdrop = backdrop,
                    checked = trans.folderTracking,
                    onCheckedChange = trans::setFolderTracking,
                    enabled = trans.enabled,
                    icon = { AppGridIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Center Active App in Recents",
                    subtitle = "Current app stays centered in overview instead of shifting right",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = trans.recentsCentering,
                    onCheckedChange = trans::setRecentsCentering,
                    enabled = trans.enabled,
                    icon = { AppsIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Zero Friction Drag Resistance",
                    subtitle = "Removes rubber-band friction wall when swiping up from apps",
                    colors = colors,
                    iconTint = colors.accentGreen,
                    backdrop = backdrop,
                    checked = trans.zeroFrictionDrag,
                    onCheckedChange = trans::setZeroFrictionDrag,
                    enabled = trans.enabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Game Mode Landscape Recents",
                    subtitle = "Removes gesture touch-drop delay in games and landscape apps",
                    colors = colors,
                    iconTint = colors.accentPink,
                    backdrop = backdrop,
                    checked = trans.landscapeGameOpt,
                    onCheckedChange = trans::setLandscapeGameOpt,
                    enabled = trans.enabled,
                    icon = { GearIcon(color = Color.White, size = 15.dp) }
                )
            }
            SectionFooter(
                "• Folder Trajectories: Uses modular 1:1 affine scaling and OriginOS expansion curves.\n• Zero Friction Drag: Full 1:1 finger tracking with zero artificial drag resistance.\n• Game Mode Recents: Eliminates gesture hesitation when playing games.",
                colors
            )
        }

        // 5. DEPTH ZOOM & HAPTICS
        item {
            SectionHeader("DEPTH ZOOM & HAPTICS", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Environmental Depth Zoom",
                    subtitle = "Home screen icons and wallpaper breathe dynamically during transitions",
                    colors = colors,
                    iconTint = colors.accentPurple,
                    backdrop = backdrop,
                    checked = trans.depthZoom,
                    onCheckedChange = trans::setDepthZoom,
                    enabled = trans.enabled,
                    icon = { AppGridIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Refined Tactile Haptics",
                    subtitle = "Crisp elastic pop on back gestures and tactile latch tick on Recents",
                    colors = colors,
                    iconTint = colors.accentGreen,
                    backdrop = backdrop,
                    checked = trans.refinedHaptics,
                    onCheckedChange = trans::setRefinedHaptics,
                    enabled = trans.enabled,
                    icon = { PowerIcon(color = Color.White, size = 15.dp) }
                )
            }
            SectionFooter(
                "• Dynamic Island: Dynamic Island animations for calls and media players remain 100% stock and isolated from transition overrides.",
                colors
            )
        }
    }
}
