package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.AppGridIcon
import dev.joseph.hyperbounce.ui.components.AppsIcon
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
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = trans::reloadFromDefaults,
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartLauncher(context) }
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
                    valueRange = 0.50f..1.80f,
                    defaultValue = 1.00f,
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
                    valueRange = 0.80f..1.25f,
                    defaultValue = 0.98f,
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
                    valueRange = 0.70f..1.10f,
                    defaultValue = 0.84f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) }
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
