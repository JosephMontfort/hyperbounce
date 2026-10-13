package dev.hyperios.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.ui.components.DropdownPicker
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.ResetArrowIcon
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsClickableRow
import dev.hyperios.app.ui.components.SettingsDropdownRow
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.components.SettingsToggleRow
import dev.hyperios.app.ui.components.SlidersIcon
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
import dev.hyperios.app.ui.theme.HyperBounceType
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
    val dialogHost = dev.hyperios.app.ui.glass.LocalGlassDialogHost.current

    ScreenScaffold(
        title = "Control Centre",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = cc::reloadFromDefaults,
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Enable Hook",
                    colors = colors,
                    backdrop = backdrop,
                    checked = cc.masterEnabled,
                    onCheckedChange = cc::setMasterEnabled
                )
            }
        }

        item {
            SectionHeader("Modular Control Center Edit", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Unlock Control Center Edit Page",
                    subtitle = "Unlocks all control center tiles to be resized and grouped dynamically",
                    colors = colors,
                    backdrop = backdrop,
                    checked = cc.modularEditEnabled,
                    onCheckedChange = cc::setModularEditEnabled,
                    enabled = cc.masterEnabled
                )
                if (cc.modularEditEnabled) {
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Landscape Right Panel Rows",
                        subtitle = "Maximum row height allocated to the right side in landscape mode (Divider position in Edit Mode)",
                        colors = colors,
                        backdrop = backdrop,
                        value = cc.landscapeRightMaxRows.toFloat().coerceIn(1f, 6f),
                        onValueChange = { cc.setLandscapeRightMaxRows(it.roundToInt()) },
                        valueRange = ModuleDefaults.ControlCenter.LANDSCAPE_RIGHT_MAX_ROWS_RANGE,
                        defaultValue = ModuleDefaults.ControlCenter.LANDSCAPE_RIGHT_MAX_ROWS.toFloat(),
                        valueLabel = { "${it.roundToInt()} Rows" },
                        enabled = cc.masterEnabled
                    )
                    RowDivider(colors.separator)
                    SettingsToggleRow(
                        title = "Enhanced Tile Resize Handling",
                        subtitle = "Real-time tile expansion with dynamic neighbour displacement during resize drag",
                        colors = colors,
                        backdrop = backdrop,
                        checked = cc.fluidResizeEnabled,
                        onCheckedChange = cc::setFluidResizeEnabled,
                        enabled = cc.masterEnabled
                    )
                }
            }
        }

        item {
            SectionHeader("Animations", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsDropdownRow(
                    title = "Animation Engine",
                    subtitle = "Control center & shade motion",
                    options = listOf("High-End", "Low-End"),
                    selectedIndex = cc.animEngineMode.coerceIn(0, 1),
                    onSelect = { cc.setAnimEngineMode(it) },
                    colors = colors,
                    backdrop = backdrop,
                    enabled = cc.masterEnabled
                )

                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Center Bounce Animation",
                    subtitle = "Emerge outward from screen center with harmonic spring physics",
                    colors = colors,
                    backdrop = backdrop,
                    checked = cc.animEnabled,
                    onCheckedChange = cc::setAnimEnabled,
                    enabled = cc.masterEnabled
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Jelly Physics Engine",
                    subtitle = "Velocity-coupled Poisson lateral squish & top-anchored stretch",
                    colors = colors,
                    backdrop = backdrop,
                    checked = cc.jellyEnabled,
                    onCheckedChange = cc::setJellyEnabled,
                    enabled = cc.masterEnabled
                )
                if (cc.jellyEnabled) {
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Jelly Stretch & Rebound Intensity",
                        subtitle = "Multiplies physical deformation amplitude of vertical stretch, Poisson squish, and rebound",
                        colors = colors,
                        backdrop = backdrop,
                        value = cc.spreadRatio.coerceIn(0.20f, 2.50f),
                        onValueChange = { cc.setSpreadRatio((it * 100).roundToInt() / 100f) },
                        valueRange = 0.20f..2.50f,
                        defaultValue = ModuleDefaults.ControlCenter.SPREAD_RATIO,
                        step = 0.05f,
                        valueLabel = { String.format(java.util.Locale.US, "%.2fx", it) },
                        enabled = cc.masterEnabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Release Rebound Damping",
                        subtitle = "Elasticity and settling bounce of tiles upon pull-down and overstretch release",
                        colors = colors,
                        backdrop = backdrop,
                        value = cc.spreadReleaseDamping.coerceIn(0.20f, 0.80f),
                        onValueChange = { cc.setSpreadReleaseDamping((it * 100).roundToInt() / 100f) },
                        valueRange = 0.20f..0.80f,
                        defaultValue = ModuleDefaults.ControlCenter.SPREAD_RELEASE_DAMPING,
                        step = 0.01f,
                        valueLabel = { String.format(java.util.Locale.US, "%.2f", it) },
                        enabled = cc.masterEnabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Row Ripple Wave Propagation",
                        subtitle = "Cascading delay between rows during overstretch and release rebound waves",
                        colors = colors,
                        backdrop = backdrop,
                        value = cc.tileDelayScale.coerceIn(0.00f, 2.00f),
                        onValueChange = { cc.setTileDelayScale((it * 100).roundToInt() / 100f) },
                        valueRange = 0.00f..2.00f,
                        defaultValue = ModuleDefaults.ControlCenter.TILE_DELAY_SCALE,
                        step = 0.05f,
                        valueLabel = { String.format(java.util.Locale.US, "%.2fx", it) },
                        enabled = cc.masterEnabled
                    )
                }
            }
        }

        item {
            SectionHeader("Flashlight Unlock", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Unlock Flashlight Brightness Control",
                    subtitle = "Unlocks flashlight brightness control for low-end devices",
                    colors = colors,
                    backdrop = backdrop,
                    checked = cc.lowEndFlashlightEnabled,
                    onCheckedChange = cc::setLowEndFlashlightEnabled,
                    enabled = cc.masterEnabled
                )
                if (cc.lowEndFlashlightEnabled) {
                    val hwMax = dev.hyperios.app.controlcenter.FlashlightBrightnessHook.getHardwareMaxBrightness()
                    RowDivider(colors.separator)
                    val minBright = ModuleDefaults.ControlCenter.FLASHLIGHT_MAX_BRIGHTNESS_RANGE.start
                    SettingsSliderRow(
                        title = "Maximum Brightness Limit",
                        subtitle = "Caps maximum brightness across the slider",
                        colors = colors,
                        backdrop = backdrop,
                        value = cc.flashlightMaxBrightness.toFloat().coerceIn(minBright, hwMax.toFloat()),
                        onValueChange = { cc.setFlashlightMaxBrightness((it.roundToInt() / 10 * 10).coerceIn(minBright.toInt(), hwMax)) },
                        valueRange = minBright..hwMax.toFloat(),
                        defaultValue = ModuleDefaults.ControlCenter.FLASHLIGHT_MAX_BRIGHTNESS.toFloat().coerceIn(minBright, hwMax.toFloat()),
                        step = 10f,
                        valueLabel = { "${(it.roundToInt() / 10 * 10).coerceIn(100, hwMax)}" },
                        enabled = cc.masterEnabled
                    )
                }
            }
        }

        item {
            SectionHeader("Layout Actions", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsClickableRow(
                    title = "Reset Layout to Stock",
                    subtitle = "Dissolves all tile groups and restores stock component positions",
                    colors = colors,
                    titleColor = Color(0xFFFF453A),
                    enabled = cc.masterEnabled,
                    onClick = {
                        dialogHost.showConfirmation(
                            title = "Reset Layout to Stock?",
                            message = "This will reset the custom sizing of all tiles, media player and sliders, to stock, and dissolve all the dynamic groups.",
                            confirmText = "Reset",
                            confirmColor = Color(0xFFFF453A),
                            onConfirm = {
                                try {
                                    cc.setLandscapeRightMaxRows(ModuleDefaults.ControlCenter.LANDSCAPE_RIGHT_MAX_ROWS)
                                    context.sendBroadcast(Intent("dev.hyperios.app.RESET_CC_LAYOUT"))
                                    context.contentResolver.call(
                                        Uri.parse("content://dev.hyperios.app.provider"),
                                        "resetCCLayout", null, null
                                    )
                                    Toast.makeText(context, "Control Center layout reset to stock!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to reset layout: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                )
            }
        }
    }
}
