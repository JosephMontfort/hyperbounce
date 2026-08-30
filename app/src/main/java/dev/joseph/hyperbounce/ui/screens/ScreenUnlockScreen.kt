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
import dev.joseph.hyperbounce.ui.components.LockIcon
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import java.util.Locale

@Composable
fun ScreenUnlockScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val unlock = repository.screenUnlock

    ScreenScaffold(
        title = "Screen Unlock",
        subtitle = "Velocity-coupled kinetic grid & radial dispersion",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { _ ->
        SectionHeader("MASTER SWITCH", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsToggleRow(
                title = "Enable Unlock Kinetics",
                subtitle = "Custom spring physics on MIUI Launcher when unlocking",
                colors = colors,
                iconTint = colors.accentPurple,
                backdrop = backdrop,
                checked = unlock.enabled,
                onCheckedChange = unlock::setEnabled,
                icon = { LockIcon(color = Color.White, size = 15.dp) }
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
                subtitle = "Column detection on home screen",
                colors = colors,
                backdrop = backdrop,
                value = unlock.gridColumns.toFloat(),
                onValueChange = { unlock.setGridColumns(it.toInt()) },
                valueRange = 2f..8f,
                valueLabel = { String.format(Locale.US, "%.0f cols", it) },
                enabled = unlock.enabled
            )
            RowDivider(colors.separator)
            SettingsSliderRow(
                title = "Grid Rows",
                subtitle = "Row detection on home screen",
                colors = colors,
                backdrop = backdrop,
                value = unlock.gridRows.toFloat(),
                onValueChange = { unlock.setGridRows(it.toInt()) },
                valueRange = 3f..12f,
                valueLabel = { String.format(Locale.US, "%.0f rows", it) },
                enabled = unlock.enabled
            )
        }
        SectionFooter("Configures grid size for column and row detection.", colors)

        if (unlock.animMode == 0) {
            SectionHeader("CLASSIC RADIAL UNLOCK", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Center Damping",
                    subtitle = "Lower = rubber-band bouncy | Higher = firm",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.centerDamping,
                    onValueChange = unlock::setCenterDamping,
                    valueRange = 0.1f..1.0f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Center Response",
                    subtitle = "Lower = snaps faster | Higher = floats",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.centerResponse,
                    onValueChange = unlock::setCenterResponse,
                    valueRange = 0.1f..1.5f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Edge Damping Drop",
                    subtitle = "Higher = edges become more bouncy than center",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.edgeDampingDrop,
                    onValueChange = unlock::setEdgeDampingDrop,
                    valueRange = 0.0f..0.5f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Edge Response Delay",
                    subtitle = "Higher = outer edges lag behind center",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.edgeResponseDelay,
                    onValueChange = unlock::setEdgeResponseDelay,
                    valueRange = 0.0f..1.0f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Z-Depth Scale",
                    subtitle = "Starting 3D depth multiplier",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.zDepthMultiplier,
                    onValueChange = unlock::setZDepthMultiplier,
                    valueRange = 1.0f..4.0f,
                    valueLabel = { String.format(Locale.US, "%.2fx", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Dock Damping",
                    subtitle = "Damping for dock view and indicators",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.dockDamping,
                    onValueChange = unlock::setDockDamping,
                    valueRange = 0.1f..1.0f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Dock Response",
                    subtitle = "Response for dock view and indicators",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.dockResponse,
                    onValueChange = unlock::setDockResponse,
                    valueRange = 0.1f..1.5f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
            }
            SectionFooter("Radial dispersion calculates optical distance from screen center (0.42 Y).", colors)
        } else {
            SectionHeader("KINETIC Y-AXIS UNLOCK GLOBALS", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Global Anim Duration",
                    subtitle = "Lower = faster animation | Higher = slower settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.durationScale,
                    onValueChange = unlock::setDurationScale,
                    valueRange = 0.1f..3.0f,
                    valueLabel = { String.format(Locale.US, "%.2fx", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Velocity Impact",
                    subtitle = "Coupling between swipe-up speed and kinetic bounce",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.velocityInfluence,
                    onValueChange = unlock::setVelocityInfluence,
                    valueRange = 0.0f..2.0f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Max Swipe Normalizer",
                    subtitle = "Velocity ceiling for maximum bounce scaling",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.maxVelocity,
                    onValueChange = unlock::setMaxVelocity,
                    valueRange = 5000f..30000f,
                    valueLabel = { String.format(Locale.US, "%.0f px/s", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Resting Damping",
                    subtitle = "Baseline damping when settling",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.cascadeDamping,
                    onValueChange = unlock::setCascadeDamping,
                    valueRange = 0.5f..1.0f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
            }
            SectionFooter("Velocity influence dynamically scales row spring properties based on flick speed.", colors)

            SectionHeader("DOCK ISOLATION", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Dock Damping",
                    subtitle = "Lower = bouncier dock | Higher = stiffer dock",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.kineticDockDamping,
                    onValueChange = unlock::setKineticDockDamping,
                    valueRange = 0.1f..1.0f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Dock Response",
                    subtitle = "Lower = snaps faster | Higher = floats into place",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.kineticDockResponse,
                    onValueChange = unlock::setKineticDockResponse,
                    valueRange = 0.1f..1.5f,
                    valueLabel = { String.format(Locale.US, "%.2f", it) },
                    enabled = unlock.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Dock Z-Depth",
                    subtitle = "Starting 3D perspective depth for dock",
                    colors = colors,
                    backdrop = backdrop,
                    value = unlock.kineticDockZDepth,
                    onValueChange = unlock::setKineticDockZDepth,
                    valueRange = 0f..4000f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = unlock.enabled
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
                        colors = colors,
                        backdrop = backdrop,
                        value = currentZ,
                        onValueChange = { unlock.setRowZ(r, it) },
                        valueRange = 100f..4000f,
                        valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                        enabled = unlock.enabled
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
                        colors = colors,
                        backdrop = backdrop,
                        value = cBounce,
                        onValueChange = { unlock.setRowCenterBounce(r, it) },
                        valueRange = 0.0f..1.0f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Row $r Outer Bounce",
                        subtitle = "Overshoot for outer columns",
                        colors = colors,
                        backdrop = backdrop,
                        value = oBounce,
                        onValueChange = { unlock.setRowOuterBounce(r, it) },
                        valueRange = 0.0f..1.0f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
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
                        colors = colors,
                        backdrop = backdrop,
                        value = cSettle,
                        onValueChange = { unlock.setRowCenterSettle(r, it) },
                        valueRange = 0.1f..1.5f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Row $r Outer Settling",
                        subtitle = "Settling response for outer columns",
                        colors = colors,
                        backdrop = backdrop,
                        value = oSettle,
                        onValueChange = { unlock.setRowOuterSettle(r, it) },
                        valueRange = 0.1f..1.5f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
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
