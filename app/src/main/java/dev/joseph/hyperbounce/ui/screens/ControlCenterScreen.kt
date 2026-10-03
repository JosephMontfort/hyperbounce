package dev.joseph.hyperbounce.ui.screens

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
import dev.joseph.hyperbounce.controlcenter.DevicePipelineDetector
import dev.joseph.hyperbounce.ui.components.DropdownPicker
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.ResetArrowIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsClickableRow
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
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
                    title = "Unlock Control Center Edit Page",
                    subtitle = "Unlocks all control center tiles to be resized and grouped dynamically",
                    colors = colors,
                    backdrop = backdrop,
                    checked = cc.modularEditEnabled,
                    onCheckedChange = cc::setModularEditEnabled
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
                        valueRange = 1f..6f,
                        defaultValue = 3f,
                        valueLabel = { "${it.roundToInt()} Rows" },
                        allowCustomInput = false
                    )
                    RowDivider(colors.separator)
                    SettingsToggleRow(
                        title = "Enhanced Tile Resize Handling",
                        subtitle = "Real-time tile expansion with dynamic neighbour displacement during resize drag",
                        colors = colors,
                        backdrop = backdrop,
                        checked = cc.fluidResizeEnabled,
                        onCheckedChange = cc::setFluidResizeEnabled
                    )
                }

                RowDivider(colors.separator)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        BasicText(
                            "Animation Engine",
                            style = HyperBounceType.body.copy(color = colors.label)
                        )
                        Spacer(Modifier.height(2.dp))
                        val detectedText = if (DevicePipelineDetector.isNativeLowEnd()) "Default: Low-End" else "Default: High-End"
                        BasicText(
                            "Control center & shade motion ($detectedText)",
                            style = HyperBounceType.subhead.copy(color = colors.secondaryLabel)
                        )
                    }
                    DropdownPicker(
                        options = listOf("High-End", "Low-End"),
                        selectedIndex = cc.animEngineMode.coerceIn(0, 1),
                        onSelect = { cc.setAnimEngineMode(it) },
                        backdrop = backdrop
                    )
                }

                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Center Bounce Animation",
                    subtitle = "Emerge outward from screen center with harmonic spring physics",
                    colors = colors,
                    backdrop = backdrop,
                    checked = cc.animEnabled,
                    onCheckedChange = cc::setAnimEnabled
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Jelly Physics Engine",
                    subtitle = "Velocity-coupled Poisson lateral squish & top-anchored stretch",
                    colors = colors,
                    backdrop = backdrop,
                    checked = cc.jellyEnabled,
                    onCheckedChange = cc::setJellyEnabled
                )
                RowDivider(colors.separator)
                SettingsToggleRow(
                    title = "Unlock Flashlight Brightness Control",
                    subtitle = "Unlocks flashlight brightness control for low-end devices",
                    colors = colors,
                    backdrop = backdrop,
                    checked = cc.lowEndFlashlightEnabled,
                    onCheckedChange = cc::setLowEndFlashlightEnabled
                )
                if (cc.lowEndFlashlightEnabled) {
                    val hwMax = dev.joseph.hyperbounce.controlcenter.FlashlightBrightnessHook.getHardwareMaxBrightness()
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Maximum Brightness Limit",
                        subtitle = "Caps maximum brightness across the slider",
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
            }
        }

        item {
            SectionHeader("LAYOUT ACTIONS", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsClickableRow(
                    title = "Reset Layout to Stock",
                    subtitle = "Dissolves all tile groups and restores stock component positions",
                    colors = colors,
                    titleColor = Color(0xFFFF453A),
                    onClick = {
                        dialogHost.showConfirmation(
                            title = "Reset Layout to Stock?",
                            message = "This will dissolve all 2x2, 2x1, and 1x2 tile groups, restore stock Wi-Fi/Mobile Data expansion, and reset all component positions to stock.",
                            confirmText = "Reset",
                            confirmColor = Color(0xFFFF453A),
                            onConfirm = {
                                try {
                                    cc.setLandscapeRightMaxRows(3)
                                    context.sendBroadcast(Intent("dev.joseph.hyperbounce.RESET_CC_LAYOUT"))
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
