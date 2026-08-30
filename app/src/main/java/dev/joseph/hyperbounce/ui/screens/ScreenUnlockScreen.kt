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
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SubpageScaffold
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun ScreenUnlockScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val unlock = repository.screenUnlock

    SubpageScaffold(
        title = "Screen Unlock",
        subtitle = "Velocity-coupled kinetic grid & radial dispersion",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) {
        SectionHeader("MASTER SWITCH", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsToggleRow(
                title = "Enable Unlock Kinetics",
                subtitle = "Custom spring physics on MIUI Launcher when unlocking",
                checked = unlock.enabled,
                colors = colors,
                onCheckedChange = unlock::setEnabled
            )
        }
        SectionFooter(
            "Intercepts UserPresentAnimationCompatPhone to drive physics on home screen icons and dock.",
            colors
        )

        SectionHeader("ANIMATION MODE", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
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
                    title = "Mode 1: Y Cascade",
                    subtitle = "Velocity kinetics",
                    selected = unlock.animMode == 1,
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = { unlock.setAnimMode(1) }
                )
            }
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Grid Columns",
                subtitle = "${unlock.gridColumns} columns",
                value = unlock.gridColumns.toFloat(),
                valueRange = 2f..8f,
                steps = 5,
                valueFormat = "%.0f",
                colors = colors,
                onValueChange = { unlock.setGridColumns(it.toInt()) }
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Grid Rows",
                subtitle = "${unlock.gridRows} rows",
                value = unlock.gridRows.toFloat(),
                valueRange = 3f..12f,
                steps = 8,
                valueFormat = "%.0f",
                colors = colors,
                onValueChange = { unlock.setGridRows(it.toInt()) }
            )
        }
        SectionFooter("Configures grid size for column and row detection.", colors)

        if (unlock.animMode == 0) {
            SectionHeader("CLASSIC RADIAL UNLOCK", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Center Damping",
                    subtitle = "Lower = rubber-band bouncy | Higher = firm",
                    value = unlock.centerDamping,
                    valueRange = 0.1f..1.0f,
                    colors = colors,
                    onValueChange = unlock::setCenterDamping
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Center Response",
                    subtitle = "Lower = snaps faster | Higher = floats",
                    value = unlock.centerResponse,
                    valueRange = 0.1f..1.5f,
                    colors = colors,
                    onValueChange = unlock::setCenterResponse
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Edge Damping Drop",
                    subtitle = "Higher = edges become more bouncy than center",
                    value = unlock.edgeDampingDrop,
                    valueRange = 0.0f..0.5f,
                    colors = colors,
                    onValueChange = unlock::setEdgeDampingDrop
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Edge Response Delay",
                    subtitle = "Higher = outer edges lag behind center",
                    value = unlock.edgeResponseDelay,
                    valueRange = 0.0f..1.0f,
                    colors = colors,
                    onValueChange = unlock::setEdgeResponseDelay
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Z-Depth Scale",
                    subtitle = "Starting 3D depth multiplier",
                    value = unlock.zDepthMultiplier,
                    valueRange = 1.0f..4.0f,
                    colors = colors,
                    onValueChange = unlock::setZDepthMultiplier
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Dock Damping",
                    subtitle = "Damping for dock view and indicators",
                    value = unlock.dockDamping,
                    valueRange = 0.1f..1.0f,
                    colors = colors,
                    onValueChange = unlock::setDockDamping
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Dock Response",
                    subtitle = "Response for dock view and indicators",
                    value = unlock.dockResponse,
                    valueRange = 0.1f..1.5f,
                    colors = colors,
                    onValueChange = unlock::setDockResponse
                )
            }
            SectionFooter("Radial dispersion calculates optical distance from screen center (0.42 Y).", colors)
        } else {
            SectionHeader("KINETIC Y-AXIS UNLOCK GLOBALS", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Global Anim Duration",
                    subtitle = "Lower = faster animation | Higher = slower settling",
                    value = unlock.durationScale,
                    valueRange = 0.1f..3.0f,
                    colors = colors,
                    onValueChange = unlock::setDurationScale
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Velocity Impact",
                    subtitle = "Coupling between swipe-up speed and kinetic bounce",
                    value = unlock.velocityInfluence,
                    valueRange = 0.0f..2.0f,
                    colors = colors,
                    onValueChange = unlock::setVelocityInfluence
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Max Swipe Normalizer",
                    subtitle = "Velocity ceiling for maximum bounce scaling",
                    value = unlock.maxVelocity,
                    valueRange = 5000f..30000f,
                    valueFormat = "%.0f px/s",
                    colors = colors,
                    onValueChange = unlock::setMaxVelocity
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Resting Damping",
                    subtitle = "Baseline damping when settling",
                    value = unlock.cascadeDamping,
                    valueRange = 0.5f..1.0f,
                    colors = colors,
                    onValueChange = unlock::setCascadeDamping
                )
            }
            SectionFooter("Velocity influence dynamically scales row spring properties based on flick speed.", colors)

            SectionHeader("DOCK ISOLATION", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Dock Damping",
                    subtitle = "Lower = bouncier dock | Higher = stiffer dock",
                    value = unlock.kineticDockDamping,
                    valueRange = 0.1f..1.0f,
                    colors = colors,
                    onValueChange = unlock::setKineticDockDamping
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Dock Response",
                    subtitle = "Lower = snaps faster | Higher = floats into place",
                    value = unlock.kineticDockResponse,
                    valueRange = 0.1f..1.5f,
                    colors = colors,
                    onValueChange = unlock::setKineticDockResponse
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Dock Z-Depth",
                    subtitle = "Starting 3D perspective depth for dock",
                    value = unlock.kineticDockZDepth,
                    valueRange = 0f..4000f,
                    valueFormat = "%.0f dp",
                    colors = colors,
                    onValueChange = unlock::setKineticDockZDepth
                )
            }
            SectionFooter("Dedicated motion curve for the bottom dock bar and page indicator.", colors)

            val rows = unlock.gridRows.coerceIn(1, 12)

            SectionHeader("ROW-BY-ROW 3D PERSPECTIVE (Z-DEPTH)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                for (r in 0 until rows) {
                    if (r > 0) RowDivider(colors.separator)
                    val currentZ = unlock.getRowZ(r)
                    SettingsSliderRow(
                        title = "Row $r Z-Depth",
                        subtitle = "Starting 3D perspective depth",
                        value = currentZ,
                        valueRange = 100f..4000f,
                        valueFormat = "%.0f dp",
                        colors = colors,
                        onValueChange = { unlock.setRowZ(r, it) }
                    )
                }
            }
            SectionFooter("Starting 3D depth for each horizontal icon row.", colors)

            SectionHeader("DYNAMIC VELOCITY BOUNCE (CENTER VS OUTER)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                for (r in 0 until rows) {
                    if (r > 0) RowDivider(colors.separator)
                    val cBounce = unlock.getRowCenterBounce(r)
                    val oBounce = unlock.getRowOuterBounce(r)
                    SettingsSliderRow(
                        title = "Row $r Center Bounce",
                        subtitle = "Overshoot for inner columns",
                        value = cBounce,
                        valueRange = 0.0f..1.0f,
                        colors = colors,
                        onValueChange = { unlock.setRowCenterBounce(r, it) }
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Row $r Outer Bounce",
                        subtitle = "Overshoot for outer columns",
                        value = oBounce,
                        valueRange = 0.0f..1.0f,
                        colors = colors,
                        onValueChange = { unlock.setRowOuterBounce(r, it) }
                    )
                }
            }
            SectionFooter("Differential bounce between inner and screen-edge columns.", colors)

            SectionHeader("DYNAMIC SETTLING TIME (CENTER VS OUTER)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                for (r in 0 until rows) {
                    if (r > 0) RowDivider(colors.separator)
                    val cSettle = unlock.getRowCenterSettle(r)
                    val oSettle = unlock.getRowOuterSettle(r)
                    SettingsSliderRow(
                        title = "Row $r Center Settling",
                        subtitle = "Settling response for center columns",
                        value = cSettle,
                        valueRange = 0.1f..1.5f,
                        colors = colors,
                        onValueChange = { unlock.setRowCenterSettle(r, it) }
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Row $r Outer Settling",
                        subtitle = "Settling response for outer columns",
                        value = oSettle,
                        valueRange = 0.1f..1.5f,
                        colors = colors,
                        onValueChange = { unlock.setRowOuterSettle(r, it) }
                    )
                }
            }
            SectionFooter("Settling duration curves per row and column position.", colors)
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
    val borderCol = if (selected) colors.accentBlue else colors.separator

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
                style = HyperBounceType.Body.copy(
                    color = if (selected) colors.accentBlue else colors.label
                )
            )
            BasicText(
                text = subtitle,
                style = HyperBounceType.Caption1.copy(color = colors.secondaryLabel),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
