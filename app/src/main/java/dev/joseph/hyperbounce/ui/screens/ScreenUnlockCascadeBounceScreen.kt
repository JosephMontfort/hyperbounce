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
fun ScreenUnlockCascadeBounceScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val unlock = repository.screenUnlock
    val context = androidx.compose.ui.platform.LocalContext.current
    val rows = unlock.gridRows.coerceIn(1, 12)

    ScreenScaffold(
        title = "Row Velocity Bounce",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            for (r in 0 until rows) {
                unlock.setRowCenterBounce(r, ScreenUnlockDefaults.rowBounce(r, rows, false))
                unlock.setRowOuterBounce(r, ScreenUnlockDefaults.rowBounce(r, rows, true))
            }
        },
        restartAppTitle = "Restart Launcher",
        restartAppSubtitle = "Restart com.miui.home",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartLauncher(context) }
    ) { _ ->
        item {
            SectionHeader("Dynamic Velocity Bounce (Center vs Outer)", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                for (r in 0 until rows) {
                    if (r > 0) RowDivider(colors.separator)
                    val cBounce = unlock.getRowCenterBounce(r)
                    val oBounce = unlock.getRowOuterBounce(r)
                    val defCBounce = ScreenUnlockDefaults.rowBounce(r, rows, false)
                    val defOBounce = ScreenUnlockDefaults.rowBounce(r, rows, true)
                    SettingsSliderRow(
                        title = "Row $r Center Bounce",
                        subtitle = "Lower = subtle overshoot, Higher = pronounced center bounce",
                        colors = colors,
                        backdrop = backdrop,
                        value = cBounce,
                        onValueChange = { unlock.setRowCenterBounce(r, it) },
                        valueRange = 0.0f..1.0f,
                        defaultValue = defCBounce,
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
                        defaultValue = defOBounce,
                        valueLabel = { String.format(Locale.US, "%.2f", it) },
                        enabled = unlock.enabled
                    )
                }
            }
            SectionFooter(
                "Fine-tune the elastic velocity bounce independently between inner columns and edge icons across each row.",
                colors
            )
        }
    }
}
