package dev.hyperios.app.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.hyperios.app.launcher.ScreenUnlockDefaults
import dev.hyperios.app.ui.components.GlassCard
import dev.hyperios.app.ui.components.RowDivider
import dev.hyperios.app.ui.components.SectionFooter
import dev.hyperios.app.ui.components.SectionHeader
import dev.hyperios.app.ui.components.SettingsSliderRow
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun ScreenUnlockCascadeSettleScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val unlock = repository.screenUnlock
    val context = androidx.compose.ui.platform.LocalContext.current
    val rows = unlock.gridRows.coerceIn(1, 12)

    ScreenScaffold(
        title = "Row Settling Dynamics",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            for (r in 0 until rows) {
                unlock.setRowCenterSettle(r, ScreenUnlockDefaults.rowSettle(r, rows, false))
                unlock.setRowOuterSettle(r, ScreenUnlockDefaults.rowSettle(r, rows, true))
            }
        },
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.hyperios.app.ui.utils.RootUtils.restartLauncher(context) }
    ) { _ ->
        item {
            SectionHeader("Dynamic Settling Time (Center vs Outer)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                for (r in 0 until rows) {
                    if (r > 0) RowDivider(colors.separator)
                    val cSettle = unlock.getRowCenterSettle(r)
                    val oSettle = unlock.getRowOuterSettle(r)
                    val defCSettle = ScreenUnlockDefaults.rowSettle(r, rows, false)
                    val defOSettle = ScreenUnlockDefaults.rowSettle(r, rows, true)
                    SettingsSliderRow(
                        title = "Row $r Center Settling",
                        subtitle = "Lower = rapid center arrival, Higher = delayed float",
                        colors = colors,
                        backdrop = backdrop,
                        value = cSettle,
                        onValueChange = { unlock.setRowCenterSettle(r, it) },
                        valueRange = 0.1f..1.5f,
                        defaultValue = defCSettle,
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
                        defaultValue = defOSettle,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                }
            }
            SectionFooter(
                "Adjust the deceleration settling duration independently between center icons and perimeter elements.",
                colors
            )
        }
    }
}
