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
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Screen Unlock",
        subtitle = "Velocity-coupled kinetic grid & radial dispersion",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = unlock::reloadFromDefaults,
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartLauncher(context) }
    ) { _ ->
        item {
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
        }

        item {
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
                        title = "Mode 1: Y-axis Cascade",
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
        }

        if (unlock.animMode == 0) {
            item {
                SectionHeader("CLASSIC RADIAL UNLOCK", colors)
                GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                    SettingsSliderRow(
                        title = "Center Damping",
                        subtitle = "Lower = more bounce on release, Higher = stiffer settling",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.centerDamping,
                        onValueChange = unlock::setCenterDamping,
                        valueRange = 0.1f..1.0f,
                        defaultValue = 0.58f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Center Response",
                        subtitle = "Lower = snaps faster to position, Higher = slower float",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.centerResponse,
                        onValueChange = unlock::setCenterResponse,
                        valueRange = 0.1f..1.5f,
                        defaultValue = 0.48f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Edge Damping Drop",
                        subtitle = "Lower = uniform bounce across screen, Higher = looser edges",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.edgeDampingDrop,
                        onValueChange = unlock::setEdgeDampingDrop,
                        valueRange = 0.0f..0.5f,
                        defaultValue = 0.10f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Edge Response Delay",
                        subtitle = "Lower = simultaneous expansion, Higher = edges lag behind",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.edgeResponseDelay,
                        onValueChange = unlock::setEdgeResponseDelay,
                        valueRange = 0.0f..1.0f,
                        defaultValue = 0.22f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Z-Depth Scale",
                        subtitle = "Multiplies initial 3D depth perspective",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.zDepthMultiplier,
                        onValueChange = unlock::setZDepthMultiplier,
                        valueRange = 1.0f..4.0f,
                        defaultValue = 1.60f,
                        valueLabel = { String.format(Locale.US, "%.2fx", it) },
                        enabled = unlock.enabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Dock Damping",
                        subtitle = "Lower = bouncier dock icons, Higher = stiffer dock settling",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.dockDamping,
                        onValueChange = unlock::setDockDamping,
                        valueRange = 0.1f..1.0f,
                        defaultValue = 0.70f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Dock Response",
                        subtitle = "Lower = snaps dock faster, Higher = slower dock arrival",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.dockResponse,
                        onValueChange = unlock::setDockResponse,
                        valueRange = 0.1f..1.5f,
                        defaultValue = 0.40f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                }
            }
        } else {
            item {
                SectionHeader("KINETIC Y-AXIS UNLOCK GLOBALS", colors)
                GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                    SettingsSliderRow(
                        title = "Global Anim Duration",
                        subtitle = "Lower = faster overall animation, Higher = slower motion",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.durationScale,
                        onValueChange = unlock::setDurationScale,
                        valueRange = 0.1f..3.0f,
                        defaultValue = 1.0f,
                        valueLabel = { String.format(Locale.US, "%.2fx", it) },
                        enabled = unlock.enabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Velocity Impact",
                        subtitle = "Lower = subtle speed scaling, Higher = strong speed scaling",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.velocityInfluence,
                        onValueChange = unlock::setVelocityInfluence,
                        valueRange = 0.0f..2.0f,
                        defaultValue = 1.0f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Max Swipe Normalizer",
                        subtitle = "Ceiling velocity for maximum kinetic bounce",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.maxVelocity,
                        onValueChange = unlock::setMaxVelocity,
                        valueRange = 5000f..30000f,
                        defaultValue = 16000.0f,
                        valueLabel = { String.format(Locale.US, "%.0f px/s", it) },
                        enabled = unlock.enabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Resting Damping",
                        subtitle = "Lower = bouncy settling curve, Higher = smooth critical damping",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.cascadeDamping,
                        onValueChange = unlock::setCascadeDamping,
                        valueRange = 0.5f..1.0f,
                        defaultValue = 0.95f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                }
            }

            item {
                SectionHeader("DOCK ISOLATION", colors)
                GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                    SettingsSliderRow(
                        title = "Dock Damping",
                        subtitle = "Lower = bouncier dock icons, Higher = stiffer dock settling",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.kineticDockDamping,
                        onValueChange = unlock::setKineticDockDamping,
                        valueRange = 0.1f..1.0f,
                        defaultValue = 0.85f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Dock Response",
                        subtitle = "Lower = snaps dock faster, Higher = slower dock arrival",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.kineticDockResponse,
                        onValueChange = unlock::setKineticDockResponse,
                        valueRange = 0.1f..1.5f,
                        defaultValue = 0.38f,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                    RowDivider(colors.separator)
                    SettingsSliderRow(
                        title = "Dock Z-Depth",
                        subtitle = "Starting 3D perspective depth for bottom dock",
                        colors = colors,
                        backdrop = backdrop,
                        value = unlock.kineticDockZDepth,
                        onValueChange = unlock::setKineticDockZDepth,
                        valueRange = 0f..4000f,
                        defaultValue = 800.0f,
                        valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                        enabled = unlock.enabled
                    )
                }
            }

            val rows = unlock.gridRows.coerceIn(1, 12)

            item {
                SectionHeader("ROW-BY-ROW 3D PERSPECTIVE (Z-DEPTH)", colors)
                GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                    for (r in 0 until rows) {
                        if (r > 0) RowDivider(colors.separator)
                        val currentZ = unlock.getRowZ(r)
                        val defZ = (2000.0f - (r * 200.0f)).coerceAtLeast(100.0f)
                        SettingsSliderRow(
                            title = "Row $r Z-Depth",
                            subtitle = "Starting 3D perspective depth for row $r",
                            colors = colors,
                            backdrop = backdrop,
                            value = currentZ,
                            onValueChange = { unlock.setRowZ(r, it) },
                            valueRange = 100f..4000f,
                            defaultValue = defZ,
                            valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                            enabled = unlock.enabled
                        )
                    }
                }
            }

            item {
                SectionHeader("DYNAMIC VELOCITY BOUNCE (CENTER VS OUTER)", colors)
                GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                    for (r in 0 until rows) {
                        if (r > 0) RowDivider(colors.separator)
                        val cBounce = unlock.getRowCenterBounce(r)
                        val oBounce = unlock.getRowOuterBounce(r)
                        val defBounce = (0.45f - (r * 0.05f)).coerceAtLeast(0.0f)
                        SettingsSliderRow(
                            title = "Row $r Center Bounce",
                            subtitle = "Lower = subtle overshoot, Higher = pronounced center bounce",
                            colors = colors,
                            backdrop = backdrop,
                            value = cBounce,
                            onValueChange = { unlock.setRowCenterBounce(r, it) },
                            valueRange = 0.0f..1.0f,
                            defaultValue = defBounce,
                            valueLabel = { String.format(Locale.US, "%.2f", it) },
                            enabled = unlock.enabled
                        )
                        RowDivider(colors.separator)
                        SettingsSliderRow(
                            title = "Row $r Outer Bounce",
                            subtitle = "Lower = subtle overshoot, Higher = pronounced outer bounce",
                            colors = colors,
                            backdrop = backdrop,
                            value = oBounce,
                            onValueChange = { unlock.setRowOuterBounce(r, it) },
                            valueRange = 0.0f..1.0f,
                            defaultValue = defBounce,
                            valueLabel = { String.format(Locale.US, "%.2f", it) },
                            enabled = unlock.enabled
                        )
                    }
                }
            }

            item {
                SectionHeader("DYNAMIC SETTLING TIME (CENTER VS OUTER)", colors)
                GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                    for (r in 0 until rows) {
                        if (r > 0) RowDivider(colors.separator)
                        val cSettle = unlock.getRowCenterSettle(r)
                        val oSettle = unlock.getRowOuterSettle(r)
                        val defSettle = 0.55f + (r * 0.04f)
                        SettingsSliderRow(
                            title = "Row $r Center Settling",
                            subtitle = "Lower = rapid center arrival, Higher = delayed float",
                            colors = colors,
                            backdrop = backdrop,
                            value = cSettle,
                            onValueChange = { unlock.setRowCenterSettle(r, it) },
                            valueRange = 0.1f..1.5f,
                            defaultValue = defSettle,
                            valueLabel = { String.format(Locale.US, "%.2f", it) },
                            enabled = unlock.enabled
                        )
                        RowDivider(colors.separator)
                        SettingsSliderRow(
                            title = "Row $r Outer Settling",
                            subtitle = "Lower = rapid edge arrival, Higher = delayed float",
                            colors = colors,
                            backdrop = backdrop,
                            value = oSettle,
                            onValueChange = { unlock.setRowOuterSettle(r, it) },
                            valueRange = 0.1f..1.5f,
                            defaultValue = defSettle,
                            valueLabel = { String.format(Locale.US, "%.2f", it) },
                            enabled = unlock.enabled
                        )
                    }
                }
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
