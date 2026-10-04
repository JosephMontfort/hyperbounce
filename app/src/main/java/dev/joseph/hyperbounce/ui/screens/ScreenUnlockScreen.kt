package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.LockIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import java.util.Locale

@Composable
fun ScreenUnlockScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    val unlock = repository.screenUnlock
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Screen Unlock Animation",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = unlock::reloadFromDefaults,
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartLauncher(context) }
    ) { _ ->
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Enable Unlock Kinetics",
                    subtitle = null,
                    colors = colors,
                    backdrop = backdrop,
                    checked = unlock.enabled,
                    onCheckedChange = unlock::setEnabled
                )
            }
        }

        item {
            SectionHeader("ANIMATION MODE", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModeSegment(
                            title = "Mode 0: iOS Radial",
                            subtitle = "Outward dispersion",
                            selected = unlock.animMode == 0,
                            colors = colors,
                            modifier = Modifier.weight(1f),
                            onClick = { unlock.setAnimMode(0) }
                        )
                        ModeSegment(
                            title = "Mode 1: Y-axis Cascade",
                            subtitle = "Velocity kinetics",
                            selected = unlock.animMode == 1,
                            colors = colors,
                            modifier = Modifier.weight(1f),
                            onClick = { unlock.setAnimMode(1) }
                        )
                    }
                }
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Grid Columns",
                    subtitle = "Number of icon columns detected on home screen",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.gridColumns.toFloat(),
                    onValueChange = { unlock.setGridColumns(it.toInt()) },
                    valueRange = 2f..8f,
                    defaultValue = 4f,
                    valueLabel = { String.format(Locale.US, "%.0f cols", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Grid Rows",
                    subtitle = "Number of icon rows detected on home screen",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.gridRows.toFloat(),
                    onValueChange = { unlock.setGridRows(it.toInt()) },
                    valueRange = 3f..12f,
                    defaultValue = 7f,
                    valueLabel = { String.format(Locale.US, "%.0f rows", it) },
                    enabled = unlock.enabled
                )
            }
            SectionFooter(
                "Select between Mode 0 (iOS-style radial bloom) and Mode 1 (kinetic Y-axis velocity cascade).",
                colors
            )
        }

        if (unlock.animMode == 0) {
            item {
                SectionHeader("Fine Tuning", colors)
                GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                    SettingsNavRow(
                        title = "Radial Spring Kinetics",
                        subtitle = "Center damping, edge delay & 3D perspective",
                        colors = colors,
                        onClick = { onNavigate(Destination.ScreenUnlockRadialKinetics) }
                    )
                    RowDivider(colors.separator)
                    SettingsNavRow(
                        title = "Radial Dock Settling",
                        subtitle = "Dock icons bounce & arrival settling",
                        colors = colors,
                        onClick = { onNavigate(Destination.ScreenUnlockRadialDock) }
                    )
                }
                SectionFooter(
                    "Mode 0 applies an iOS-style radial bloom dispersion radiating outward from the lock screen touch point.",
                    colors
                )
            }
        } else {
            item {
                SectionHeader("Fine Tuning", colors)
                GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                    SettingsNavRow(
                        title = "Global Kinetics & Velocity",
                        subtitle = "Duration scale, velocity influence & resting damping",
                        colors = colors,
                        onClick = { onNavigate(Destination.ScreenUnlockCascadeGlobals) }
                    )
                    RowDivider(colors.separator)
                    SettingsNavRow(
                        title = "Kinetic Dock Isolation",
                        subtitle = "Dock damping, arrival response & 3D depth",
                        colors = colors,
                        onClick = { onNavigate(Destination.ScreenUnlockCascadeDock) }
                    )
                    RowDivider(colors.separator)
                    SettingsNavRow(
                        title = "Row 3D Perspective (Z-Depth)",
                        subtitle = "Row-by-row Z-axis camera elevation",
                        colors = colors,
                        onClick = { onNavigate(Destination.ScreenUnlockCascadeZDepth) }
                    )
                    RowDivider(colors.separator)
                    SettingsNavRow(
                        title = "Row Velocity Bounce",
                        subtitle = "Center vs outer dynamic overshoot ratios",
                        colors = colors,
                        onClick = { onNavigate(Destination.ScreenUnlockCascadeBounce) }
                    )
                    RowDivider(colors.separator)
                    SettingsNavRow(
                        title = "Row Settling Dynamics",
                        subtitle = "Center vs outer dynamic deceleration timing",
                        colors = colors,
                        onClick = { onNavigate(Destination.ScreenUnlockCascadeSettle) }
                    )
                }
                SectionFooter(
                    "Mode 1 cascades rows downwards along the Y-axis with velocity-coupled spring dynamics and 3D depth perspective.",
                    colors
                )
            }
        }
    }
}

@Composable
private fun ModeSegment(
    title: String,
    subtitle: String,
    selected: Boolean,
    colors: HyperBounceColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = if (selected) colors.accentBlue.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                text = title,
                style = HyperBounceType.body.copy(
                    color = if (selected) colors.accentBlue else colors.label
                )
            )
            BasicText(
                text = subtitle,
                style = HyperBounceType.caption.copy(color = colors.secondaryLabel),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
