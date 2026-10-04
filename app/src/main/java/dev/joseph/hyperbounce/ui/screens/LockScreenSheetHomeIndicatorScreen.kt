package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsSliderRow
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import java.util.Locale

@Composable
fun LockScreenSheetHomeIndicatorScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val sheet = repository.lockScreenSheet
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "Home Indicator Pill",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = {
            sheet.setPillWidth(156.0f)
            sheet.setPillHeight(6.0f)
            sheet.setPillBottomMargin(9.0f)
        },
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            SectionHeader("Pill Dimensions & Position", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsSliderRow(
                    title = "Pill Width",
                    subtitle = "Horizontal size of the bottom home indicator",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.pillWidth,
                    onValueChange = sheet::setPillWidth,
                    valueRange = 40.0f..280.0f,
                    defaultValue = 156.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Pill Height",
                    subtitle = "Vertical thickness of the indicator bar",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.pillHeight,
                    onValueChange = sheet::setPillHeight,
                    valueRange = 2.0f..16.0f,
                    defaultValue = 6.0f,
                    valueLabel = { String.format(Locale.US, "%.1f dp", it) },
                    enabled = sheet.enabled
                )
                RowDivider(colors.separator)
                SettingsSliderRow(
                    title = "Bottom Margin",
                    subtitle = "Distance from screen bottom to indicator",
                    colors = colors,
                    backdrop = backdrop,
                    value = sheet.pillBottomMargin,
                    onValueChange = sheet::setPillBottomMargin,
                    valueRange = 0.0f..35.0f,
                    defaultValue = 9.0f,
                    valueLabel = { String.format(Locale.US, "%.0f dp", it) },
                    enabled = sheet.enabled
                )
            }
        }
    }
}
