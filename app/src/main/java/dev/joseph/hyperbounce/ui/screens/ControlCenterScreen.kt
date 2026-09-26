package dev.joseph.hyperbounce.ui.screens

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.ResetArrowIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsClickableRow
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ControlCenterScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val cc = repository.controlCenter
    val context = LocalContext.current
    val dialogHost = dev.joseph.hyperbounce.ui.glass.LocalGlassDialogHost.current

    ScreenScaffold(
        title = "Control Center",
        subtitle = "Modular customizer, tile grouping & iOS 26 jelly physics",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = cc::reloadFromDefaults,
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("MASTER SWITCHES", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Modular Customizer (iOS Edit Mode)",
                    subtitle = "Unlocks major cards and enables cross-component reordering",
                    colors = colors,
                    iconTint = colors.accentBlue,
                    backdrop = backdrop,
                    checked = cc.modularEditEnabled,
                    onCheckedChange = cc::setModularEditEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Fluid Tile Resizing",
                    subtitle = "Real-time tile expansion with dynamic neighbour displacement during resize drag",
                    colors = colors,
                    iconTint = colors.accentPurple,
                    backdrop = backdrop,
                    checked = cc.fluidResizeEnabled,
                    onCheckedChange = cc::setFluidResizeEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Landscape Right Panel Rows",
                    subtitle = "Maximum row height allocated to the right side in landscape mode (Divider position in Edit Mode)",
                    colors = colors,
                    backdrop = backdrop,
                    value = cc.landscapeRightMaxRows.toFloat().coerceIn(1f, 6f),
                    onValueChange = { cc.setLandscapeRightMaxRows(it.roundToInt()) },
                    valueRange = 1f..6f,
                    defaultValue = 4f,
                    valueLabel = { "${it.roundToInt()} Rows" },
                    allowCustomInput = false
                )

                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Center Bounce Animation",
                    subtitle = "Emerge outward from screen center with harmonic spring physics",
                    colors = colors,
                    iconTint = colors.accentPink,
                    backdrop = backdrop,
                    checked = cc.animEnabled,
                    onCheckedChange = cc::setAnimEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "iOS 26 Jelly Physics Engine",
                    subtitle = "Velocity-coupled Poisson lateral squish & top-anchored stretch",
                    colors = colors,
                    iconTint = colors.accentOrange,
                    backdrop = backdrop,
                    checked = cc.jellyEnabled,
                    onCheckedChange = cc::setJellyEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Xiaomi Native Low-End Engine",
                    subtitle = "Enables stock low-end rendering switch for ultra smoothness",
                    colors = colors,
                    iconTint = colors.accentPurple,
                    backdrop = backdrop,
                    checked = cc.lowEndAnim,
                    onCheckedChange = cc::setLowEndAnim,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Low-End Flashlight Brightness Control",
                    subtitle = "Hardware sysfs brightness unlock (0–100% slider) for low-end devices",
                    colors = colors,
                    iconTint = colors.accentOrange,
                    backdrop = backdrop,
                    checked = cc.lowEndFlashlightEnabled,
                    onCheckedChange = cc::setLowEndFlashlightEnabled,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
                if (cc.lowEndFlashlightEnabled) {
                    val hwMax = dev.joseph.hyperbounce.controlcenter.FlashlightBrightnessHook.getHardwareMaxBrightness()
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Maximum Brightness Limit",
                        subtitle = "Caps maximum brightness across the slider (Hardware max: $hwMax)",
                        colors = colors,
                        backdrop = backdrop,
                        value = cc.flashlightMaxBrightness.toFloat().coerceIn(100f, hwMax.toFloat()),
                        onValueChange = { cc.setFlashlightMaxBrightness(it.roundToInt()) },
                        valueRange = 100f..hwMax.toFloat(),
                        defaultValue = 100f,
                        valueLabel = { "${it.roundToInt()}" },
                        allowCustomInput = false
                    )
                }
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Auto-Restart System UI on Edit Exit",
                    subtitle = "Rebinds 100% clean touch targets after saving new tile groups",
                    colors = colors,
                    iconTint = colors.accentGreen,
                    backdrop = backdrop,
                    checked = cc.restartOnEditExit,
                    onCheckedChange = cc::setRestartOnEditExit,
                    icon = { SlidersIcon(color = Color.White, size = 15.dp) }
                )
            }
        }

        item {
            SectionHeader("LAYOUT ACTIONS", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsClickableRow(
                    title = "Reset Layout to Stock",
                    subtitle = "Dissolves all tile groups and restores stock component positions",
                    colors = colors,
                    iconTint = Color(0xFFE53935),
                    titleColor = Color(0xFFFF453A),
                    onClick = {
                        dialogHost.showConfirmation(
                            title = "Reset Layout to Stock?",
                            message = "This will dissolve all 2x2, 2x1, and 1x2 tile groups, restore stock Wi-Fi/Mobile Data expansion, and reset all component positions to stock.",
                            confirmText = "Reset",
                            confirmColor = Color(0xFFFF453A),
                            onConfirm = {
                                try {
                                    context.sendBroadcast(Intent("dev.joseph.hyperbounce.RESET_CC_LAYOUT"))
                                    context.contentResolver.call(
                                        Uri.parse("content://dev.joseph.hyperbounce.provider"),
                                        "resetCCLayout", null, null
                                    )
                                    Toast.makeText(context, "Control Center layout reset to stock!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to reset layout: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    },
                    icon = { ResetArrowIcon(color = Color.White, size = 15.dp) }
                )
            }
        }

        item {
            SectionHeader("ENTRY DYNAMICS & SCALE", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Entry Initial Scale",
                    subtitle = "Initial scale of cards when pulled down from top",
                    colors = colors,
                    backdrop = backdrop,
                    value = cc.entryInitialScale,
                    onValueChange = cc::setEntryInitialScale,
                    valueRange = 0.10f..1.00f,
                    defaultValue = 0.50f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = cc.animEnabled && !cc.lowEndAnim
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Entry Translation Y",
                    subtitle = "Vertical emergence offset when starting shade drag",
                    colors = colors,
                    backdrop = backdrop,
                    value = cc.entryTranslationY,
                    onValueChange = cc::setEntryTranslationY,
                    valueRange = 0.0f..300.0f,
                    defaultValue = 75.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = cc.animEnabled && !cc.lowEndAnim
                )
            }
        }

        item {
            SectionHeader("SPREAD & ROW SPRING PHYSICS", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Spread Spring Damping",
                    subtitle = "Lower = more row bounce on drag, Higher = stiffer row settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = cc.spreadDamping,
                    onValueChange = cc::setSpreadDamping,
                    valueRange = 0.30f..1.50f,
                    defaultValue = 0.85f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = cc.animEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Spread Spring Response",
                    subtitle = "Lower = rows snap faster, Higher = rows float into position",
                    colors = colors,
                    backdrop = backdrop,
                    value = cc.spreadResponse,
                    onValueChange = cc::setSpreadResponse,
                    valueRange = 0.20f..1.50f,
                    defaultValue = 0.55f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = cc.animEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Spread Release Bounce Damping",
                    subtitle = "Lower = lively overshoot rebound, Higher = stiff rebound settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = cc.spreadReleaseDamping,
                    onValueChange = cc::setSpreadReleaseDamping,
                    valueRange = 0.30f..1.50f,
                    defaultValue = 0.78f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = cc.animEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Spread Release Bounce Response",
                    subtitle = "Lower = rapid bounce frequency, Higher = slower rebound motion",
                    colors = colors,
                    backdrop = backdrop,
                    value = cc.spreadReleaseResponse,
                    onValueChange = cc::setSpreadReleaseResponse,
                    valueRange = 0.20f..1.50f,
                    defaultValue = 0.48f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = cc.animEnabled
                )
            }
        }

        item {
            SectionHeader("TILE SHOW & HIDE SPRINGS", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Tile Show Damping",
                    subtitle = "Lower = more tile entrance bounce, Higher = rigid tile settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = cc.tileShowDamping,
                    onValueChange = cc::setTileShowDamping,
                    valueRange = 0.30f..1.50f,
                    defaultValue = 0.80f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = cc.animEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Tile Show Response",
                    subtitle = "Lower = tiles pop in faster, Higher = tiles float in smoothly",
                    colors = colors,
                    backdrop = backdrop,
                    value = cc.tileShowResponse,
                    onValueChange = cc::setTileShowResponse,
                    valueRange = 0.20f..1.50f,
                    defaultValue = 0.50f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = cc.animEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Tile Hide Damping",
                    subtitle = "Lower = bouncy tile exit, Higher = quick rigid dismissal",
                    colors = colors,
                    backdrop = backdrop,
                    value = cc.tileHideDamping,
                    onValueChange = cc::setTileHideDamping,
                    valueRange = 0.30f..1.50f,
                    defaultValue = 0.90f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = cc.animEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Tile Hide Response",
                    subtitle = "Lower = tiles dismiss instantly, Higher = tiles fade out smoothly",
                    colors = colors,
                    backdrop = backdrop,
                    value = cc.tileHideResponse,
                    onValueChange = cc::setTileHideResponse,
                    valueRange = 0.20f..1.50f,
                    defaultValue = 0.35f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = cc.animEnabled
                )
            }
        }

        item {
            SectionHeader("BLUR & SHADE RESPONSE", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Window Blur Damping",
                    subtitle = "Lower = bouncy blur settling, Higher = smooth critical blur fade",
                    colors = colors,
                    backdrop = backdrop,
                    value = cc.blurDamping,
                    onValueChange = cc::setBlurDamping,
                    valueRange = 0.30f..1.50f,
                    defaultValue = 0.80f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = cc.animEnabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Window Blur Response",
                    subtitle = "Lower = blur appears instantly, Higher = blur fades in smoothly",
                    colors = colors,
                    backdrop = backdrop,
                    value = cc.blurResponse,
                    onValueChange = cc::setBlurResponse,
                    valueRange = 0.20f..1.50f,
                    defaultValue = 0.45f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = cc.animEnabled
                )
            }
        }
    }
}
