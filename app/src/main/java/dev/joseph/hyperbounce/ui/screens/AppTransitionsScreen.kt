package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.AppGridIcon
import dev.joseph.hyperbounce.ui.components.AppsIcon
import dev.joseph.hyperbounce.ui.components.DropdownPicker
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
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
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

    val profileOptions = listOf(
        "Gentle / 柔和 (Signature)",
        "Comfort / 舒适 (Style 4)",
        "Swift / 轻快 (Flagship Fast)",
        "Custom Tuner"
    )

    ScreenScaffold(
        title = "OriginOS Transitions",
        subtitle = "OriginOS 6 Physics, Harmonic Springs & Parallel Launch",
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
                    title = "Enable OriginOS Animation Engine",
                    subtitle = "Ported 1:1 analytical harmonic springs & parallel transitions",
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
            SectionHeader("ANIMATION CURVE PROFILE", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicText("Physics Style", style = HyperBounceType.body.copy(color = colors.label))
                    DropdownPicker(
                        options = profileOptions,
                        selectedIndex = trans.curveProfile.coerceIn(0, 3),
                        onSelect = trans::setCurveProfile,
                        backdrop = backdrop
                    )
                }
            }
            SectionFooter(
                when (trans.curveProfile) {
                    0 -> "Gentle (柔和 - OriginOS 6 Signature): Luxurious float & organic cushion. k=200 on open (T₀=0.444s), k=150 on close (T₀=0.513s, ζ=0.88 underdamped)."
                    1 -> "Comfort (舒适): OriginOS Style 4. k=300 on open (T₀=0.363s), k=200 on close (T₀=0.444s, ζ=0.92 balanced)."
                    2 -> "Swift (轻快): OriginOS Flagship Fast. k=450 on open (T₀=0.296s), k=250 on close (T₀=0.397s)."
                    else -> "Custom: Fine-tune every harmonic channel independently using the sliders below."
                },
                colors
            )
        }

        item {
            SectionHeader("FLAGSHIP PARALLEL & INTERRUPT FEATURES", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "OriginOS Interrupt Dynamics (multiple.close)",
                    subtitle = "Applies OriginOS multiple.close harmonic curves during parallel swipes and app switching",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = trans.parallelAnimEnabled,
                    onCheckedChange = trans::setParallelAnimEnabled,
                    enabled = trans.enabled,
                    icon = { AppsIcon(color = Color.White, size = 16.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Desktop Layer Dynamic Scaling",
                    subtitle = "OriginOS HomeLayer staging: desktop and icon grid shrink to 0.90x on open and float back on close",
                    colors = colors,
                    iconTint = colors.accentOrange,
                    backdrop = backdrop,
                    checked = trans.delayedAlphaClose,
                    onCheckedChange = trans::setDelayedAlphaClose,
                    enabled = trans.enabled,
                    icon = { AppGridIcon(color = Color.White, size = 16.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Unlock High-End Capabilities",
                    subtitle = "Bypasses MIUI lite/low-tier device throttles and unlocks full 120Hz refresh curves and wallpaper zoom",
                    colors = colors,
                    iconTint = colors.accentPurple,
                    backdrop = backdrop,
                    checked = trans.unlockHighEndCapabilities,
                    onCheckedChange = trans::setUnlockHighEndCapabilities,
                    enabled = trans.enabled,
                    icon = { PowerIcon(color = Color.White, size = 16.dp) }
                )
            }
        }

        item {
            SectionHeader("APP OPEN PHYSICS (Flight Path & Scaling)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Open Damping Ratio (ζ)",
                    subtitle = "1.00 = Critically damped (OriginOS standard), <1.0 = Elastic bounce",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.customOpenDamping,
                    onValueChange = trans::setCustomOpenDamping,
                    valueRange = 0.50f..1.40f,
                    defaultValue = 1.00f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Open Response Time (T₀)",
                    subtitle = "0.444s (k=200 Gentle Signature), 0.363s (k=300 Comfort), 0.296s (k=450 Swift)",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.customOpenResponse,
                    onValueChange = trans::setCustomOpenResponse,
                    valueRange = 0.15f..0.80f,
                    defaultValue = 0.444f,
                    valueLabel = { String.format(Locale.US, "%.3fs", it) },
                    enabled = trans.enabled
                )
            }
        }

        item {
            SectionHeader("APP CLOSE TO HOME PHYSICS (Squircle Morph)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Close Damping Ratio (ζ)",
                    subtitle = "0.88 = Signature OriginOS underdamped cushion into squircle, 1.00 = Critically damped",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.customCloseDamping,
                    onValueChange = trans::setCustomCloseDamping,
                    valueRange = 0.50f..1.40f,
                    defaultValue = 0.88f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Close Response Time (T₀)",
                    subtitle = "0.513s (k=150 Gentle Signature), 0.444s (k=200 Comfort), 0.397s (k=250 Swift)",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.customCloseResponse,
                    onValueChange = trans::setCustomCloseResponse,
                    valueRange = 0.20f..0.90f,
                    defaultValue = 0.513f,
                    valueLabel = { String.format(Locale.US, "%.3fs", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Close Alpha Fade Damping (ζ)",
                    subtitle = "0.60 = OriginOS signature underdamped smooth fade",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.customAlphaDamping,
                    onValueChange = trans::setCustomAlphaDamping,
                    valueRange = 0.30f..1.20f,
                    defaultValue = 0.60f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Close Alpha Fade Response (T₀)",
                    subtitle = "0.268s (k=550) smooth fade duration after delay",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.customAlphaResponse,
                    onValueChange = trans::setCustomAlphaResponse,
                    valueRange = 0.15f..0.60f,
                    defaultValue = 0.268f,
                    valueLabel = { String.format(Locale.US, "%.3fs", it) },
                    enabled = trans.enabled
                )
            }
        }

        item {
            SectionHeader("OPEN APP TO RECENTS & RECENTS TO APP", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "App-to-Recents Damping (ζ)",
                    subtitle = "0.95 = OriginOS decelerating glide into task card slot",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.customRecentsDamping,
                    onValueChange = trans::setCustomRecentsDamping,
                    valueRange = 0.50f..1.40f,
                    defaultValue = 0.95f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "App-to-Recents Response (T₀)",
                    subtitle = "0.419s (k=224 OriginOS persist.launcher.to.recent)",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.customRecentsResponse,
                    onValueChange = trans::setCustomRecentsResponse,
                    valueRange = 0.20f..0.80f,
                    defaultValue = 0.419f,
                    valueLabel = { String.format(Locale.US, "%.3fs", it) },
                    enabled = trans.enabled
                )
            }
        }

        item {
            SectionHeader("PARALLEL MULTI-APP TRANSITIONS", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Parallel Trajectory Damping (ζ)",
                    subtitle = "0.95 = multiple.close spring damping from OriginOS gdm.java",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.customParallelDamping,
                    onValueChange = trans::setCustomParallelDamping,
                    valueRange = 0.50f..1.40f,
                    defaultValue = 0.95f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Parallel Trajectory Response (T₀)",
                    subtitle = "0.419s (k=224 multiple.close flight path speed)",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.customParallelResponse,
                    onValueChange = trans::setCustomParallelResponse,
                    valueRange = 0.20f..0.80f,
                    defaultValue = 0.419f,
                    valueLabel = { String.format(Locale.US, "%.3fs", it) },
                    enabled = trans.enabled
                )
            }
        }

        item {
            SectionHeader("CORNER SQUIRCLE MORPH CHANNELS", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Corner Morph Damping (ζ)",
                    subtitle = "1.15 = Overdamped for stable squircle corner settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.customCornerDamping,
                    onValueChange = trans::setCustomCornerDamping,
                    valueRange = 0.50f..1.50f,
                    defaultValue = 1.15f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Corner Morph Response (T₀)",
                    subtitle = "0.256s (k=600 OriginOS cornerradius stiffness)",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.customCornerResponse,
                    onValueChange = trans::setCustomCornerResponse,
                    valueRange = 0.15f..0.60f,
                    defaultValue = 0.256f,
                    valueLabel = { String.format(Locale.US, "%.3fs", it) },
                    enabled = trans.enabled
                )
            }
        }

        item {
            SectionHeader("SPATIAL & DEPTH STAGING", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Wallpaper Zoom Depth",
                    subtitle = "1.15x = OriginOS wallpaper depth staging (1.0 -> 1.15)",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.wallpaperScaleZoom,
                    onValueChange = trans::setWallpaperScaleZoom,
                    valueRange = 1.00f..1.30f,
                    defaultValue = 1.15f,
                    valueLabel = { String.format(Locale.US, "%.2fx", it) },
                    enabled = trans.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Desktop DragLayer End Scale",
                    subtitle = "0.90x = OriginOS desktop card shrink during app launch",
                    colors = colors,
                    backdrop = backdrop,
                    value = trans.dragLayerEndScale,
                    onValueChange = trans::setDragLayerEndScale,
                    valueRange = 0.80f..1.00f,
                    defaultValue = 0.90f,
                    valueLabel = { String.format(Locale.US, "%.2fx", it) },
                    enabled = trans.enabled
                )
            }
        }
    }
}

