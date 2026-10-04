package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.launcher.ScreenUnlockDefaults
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun ScreenUnlockCascadeZDepthScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val unlock = repository.screenUnlock
    val context = androidx.compose.ui.platform.LocalContext.current
    val rows = unlock.gridRows.coerceIn(1, 12)

    ScreenScaffold(
        title = "Row 3D Perspective",
        subtitle = "Row-by-row Z-axis camera elevation",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            for (r in 0 until rows) {
                unlock.setRowZ(r, ScreenUnlockDefaults.rowZ(r, rows))
            }
        },
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartLauncher(context) }
    ) { _ ->
        item {
            SectionHeader("Row-By-Row 3D Perspective (Z-Depth)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                for (r in 0 until rows) {
                    if (r > 0) RowDivider(colors.separator)
                    val currentZ = unlock.getRowZ(r)
                    val defZ = ScreenUnlockDefaults.rowZ(r, rows)
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
            SectionFooter(
                "Customize the initial camera drop depth along the Z-axis for each horizontal icon row.",
                colors
            )
        }
    }
}
