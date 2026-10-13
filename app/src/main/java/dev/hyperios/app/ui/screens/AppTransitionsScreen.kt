package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.ui.components.AppGridIcon
import dev.hyperios.app.ui.components.AppsIcon
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.PowerIcon
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.components.SettingsToggleRow
import dev.hyperios.app.ui.components.SlidersIcon
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
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
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = trans::reloadFromDefaults,
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartLauncher(context) }
    ) { _ ->
        // 1. ENHANCED ANIMATION ENGINE SWITCH
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Enable Enhanced Animation Engine",
                    subtitle = "Enhances app open and close animations (not recommended)",
                    colors = colors,
                    backdrop = backdrop,
                    checked = trans.enabled,
                    onCheckedChange = trans::setEnabled
                )
            }
        }

        // 2. Spring Dynamics & Speed
        item {
            SectionHeader("Spring Dynamics & Speed", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Animation Speed",
                    subtitle = null,
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.animSpeed,
                    onValueChange = trans::setAnimSpeed,
                    valueRange = ModuleDefaults.IOSTransitions.ANIM_SPEED_RANGE,
                    defaultValue = ModuleDefaults.IOSTransitions.ANIM_SPEED,
                    valueLabel = { String.format(Locale.US, "%.2fx", it) }
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Opening Spring Damping",
                    subtitle = "Lower = more bounce, Higher = smoother settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.openSpringDamping,
                    onValueChange = trans::setOpenSpringDamping,
                    valueRange = ModuleDefaults.IOSTransitions.OPEN_SPRING_DAMPING_RANGE,
                    defaultValue = ModuleDefaults.IOSTransitions.OPEN_SPRING_DAMPING,
                    valueLabel = { String.format(Locale.US, "%.2f", it) }
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Closing Spring Damping",
                    subtitle = "Lower = more bounce, Higher = smoother settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.closeSpringDamping,
                    onValueChange = trans::setCloseSpringDamping,
                    valueRange = ModuleDefaults.IOSTransitions.CLOSE_SPRING_DAMPING_RANGE,
                    defaultValue = ModuleDefaults.IOSTransitions.CLOSE_SPRING_DAMPING,
                    valueLabel = { String.format(Locale.US, "%.2f", it) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Opening Uplift Surge",
                    subtitle = "Initial upward launch velocity surge on app open",
                    colors = colors,
                    backdrop = backdrop,
                    checked = trans.openUpliftSurge,
                    onCheckedChange = trans::setOpenUpliftSurge
                )
                if (trans.openUpliftSurge) {
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Opening Surge Lift",
                        subtitle = "Upward launch velocity multiplier on app open",
                        colors = colors,
                        backdrop = backdrop,
                        value = trans.openSurgeLift,
                        onValueChange = trans::setOpenSurgeLift,
                        valueRange = ModuleDefaults.IOSTransitions.OPEN_SURGE_LIFT_RANGE,
                        defaultValue = ModuleDefaults.IOSTransitions.OPEN_SURGE_LIFT,
                        valueLabel = { String.format(Locale.US, "%.2fx", it) }
                    )
                }
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Closing Uplift Surge",
                    subtitle = "Parabolic arc upward trajectory surge on app close",
                    colors = colors,
                    backdrop = backdrop,
                    checked = trans.closeUpliftSurge,
                    onCheckedChange = trans::setCloseUpliftSurge
                )
                if (trans.closeUpliftSurge) {
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Closing Arc Apex Lift",
                        subtitle = "Parabolic arc trajectory height multiplier on app close",
                        colors = colors,
                        backdrop = backdrop,
                        value = trans.arcApexLift,
                        onValueChange = trans::setArcApexLift,
                        valueRange = ModuleDefaults.IOSTransitions.ARC_APEX_LIFT_RANGE,
                        defaultValue = ModuleDefaults.IOSTransitions.ARC_APEX_LIFT,
                        valueLabel = { String.format(Locale.US, "%.2fx", it) }
                    )
                }
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Keystone Squeeze",
                    subtitle = "Perspective trapezoid squeeze distortion during icon expansion",
                    colors = colors,
                    backdrop = backdrop,
                    checked = trans.keystoneSqueeze,
                    onCheckedChange = trans::setKeystoneSqueeze
                )
            }
        }

        // 3. Folder & Recents Gestures
        item {
            SectionHeader("Folder & Recents Gestures", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "1:1 Folder Icon Trajectories",
                    subtitle = "Desktop preview icons track 1:1 into open folder positions",
                    colors = colors,
                    backdrop = backdrop,
                    checked = trans.folderTracking,
                    onCheckedChange = trans::setFolderTracking
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Center Active App in Recents",
                    subtitle = "Current app stays centered in overview instead of shifting right",
                    colors = colors,
                    backdrop = backdrop,
                    checked = trans.recentsCentering,
                    onCheckedChange = trans::setRecentsCentering
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Zero Friction Drag Resistance",
                    subtitle = "Removes rubber-band friction wall when swiping up from apps",
                    colors = colors,
                    backdrop = backdrop,
                    checked = trans.zeroFrictionDrag,
                    onCheckedChange = trans::setZeroFrictionDrag
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Landscape & Game Optimization",
                    subtitle = "Optimized transition dynamics for landscape and gaming apps",
                    colors = colors,
                    backdrop = backdrop,
                    checked = trans.landscapeGameOpt,
                    onCheckedChange = trans::setLandscapeGameOpt
                )
            }
        }

        // 4. HAPTICS
        item {
            SectionHeader("Haptics", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Refined Haptics",
                    subtitle = "AOSP style crisp haptics for back and recents swipe up gesture",
                    colors = colors,
                    backdrop = backdrop,
                    checked = trans.refinedHaptics,
                    onCheckedChange = trans::setRefinedHaptics
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Haptics for Sliding Cards",
                    subtitle = "Mild haptic feedback during horizontal swiping in recent apps page",
                    colors = colors,
                    backdrop = backdrop,
                    checked = trans.stackRecentsHaptics,
                    onCheckedChange = trans::setStackRecentsHaptics
                )
            }
        }
    }
}
