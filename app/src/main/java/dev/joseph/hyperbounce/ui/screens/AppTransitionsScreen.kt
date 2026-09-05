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
        }

        // 2. SPRING DYNAMICS & SPEED
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
                    title = "Opening Spring Damping",
                    subtitle = "Settling elasticity for opening apps (Higher = smoother, critically damped)",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.openSpringDamping,
                    onValueChange = trans::setOpenSpringDamping,
                    valueRange = 0.80f..1.25f,
                    defaultValue = 0.98f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Closing Spring Damping",
                    subtitle = "Settling elasticity for closing apps (Lower = responsive bounce, Higher = controlled)",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.closeSpringDamping,
                    onValueChange = trans::setCloseSpringDamping,
                    valueRange = 0.70f..1.10f,
                    defaultValue = 0.84f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
            }
        }

        // 3. FOLDER & RECENTS GESTURES
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
        }

        // 4. TACTILE HAPTICS
        item {
            SectionHeader("TACTILE HAPTICS", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
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
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Stack Recents Crossing Tick",
                    subtitle = "Light mechanical clock tick as each app card crosses the screen center line in Stack recents",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = trans.stackRecentsHaptics,
                    onCheckedChange = trans::setStackRecentsHaptics,
                    enabled = trans.enabled && trans.refinedHaptics,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
            }
        }
    }
}
