package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.common.ModuleDefaults
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionFooter
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
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
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            for (r in 0 until rows) {
                unlock.setRowZ(r, ModuleDefaults.ScreenUnlock.rowZ(r, rows))
            }
        },
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartLauncher(context) }
    ) { _ ->
        item {
            SectionHeader("Row-By-Row 3D Perspective (Z-Depth)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                for (r in 0 until rows) {
                    if (r > 0) RowDivider(colors.separator)
                    val currentZ = unlock.getRowZ(r)
                    val defZ = ModuleDefaults.ScreenUnlock.rowZ(r, rows)
                    SettingsSliderRow(
                        title = "Row $r Z-Depth",
                        subtitle = "Starting 3D perspective depth for row $r",
                        colors = colors,
                        backdrop = backdrop,
                        value = currentZ,
                        onValueChange = { unlock.setRowZ(r, it) },
                        valueRange = ModuleDefaults.ScreenUnlock.ROW_Z_RANGE,
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
