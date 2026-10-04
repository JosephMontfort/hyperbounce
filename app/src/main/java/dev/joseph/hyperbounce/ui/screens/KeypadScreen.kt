package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors

@Composable
fun KeypadScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    val keypad = repository.lockScreenKeypad
    val context = androidx.compose.ui.platform.LocalContext.current

    ScreenScaffold(
        title = "PIN Pad",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack,
        onResetPage = keypad::reloadFromDefaults,
        restartAppTitle = "Restart System UI",
        restartAppSubtitle = "Restart com.android.systemui",
        onRestartApp = { dev.joseph.hyperbounce.ui.utils.RootUtils.restartSystemUI(context) }
    ) { _ ->
        item {
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsToggleRow(
                    title = "Liquid Glass PIN Pad",
                    subtitle = null,
                    colors = colors,
                    backdrop = backdrop,
                    checked = keypad.enabled,
                    onCheckedChange = repository::setKeypadEnabled
                )
            }
        }

        item {
            SectionHeader("Physics & Layout", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                SettingsNavRow(
                    title = "iOS 26 Radial Bloom Physics",
                    subtitle = "Cluster origin, radial spring damping & bloom expansion",
                    colors = colors,
                    onClick = { onNavigate(Destination.KeypadIosBloomPhysics) }
                )
                RowDivider(colors.separator)
                SettingsNavRow(
                    title = "Liquid Glass Tuning",
                    subtitle = "Refraction, bevel thickness, dispersion & specular rims",
                    colors = colors,
                    onClick = { onNavigate(Destination.KeypadGlassTuning) }
                )
                RowDivider(colors.separator)
                SettingsNavRow(
                    title = "Buttons Layout",
                    subtitle = "Key circle diameter, liquid press scale & typography",
                    colors = colors,
                    onClick = { onNavigate(Destination.KeypadButtonsLayout) }
                )
                RowDivider(colors.separator)
                SettingsNavRow(
                    title = "PIN Pad Layout",
                    subtitle = "Keypad lift, PIN dots position & grid spacing",
                    colors = colors,
                    onClick = { onNavigate(Destination.KeypadPadLayout) }
                )
            }
            SectionFooter(
                "Configure radial bloom kinetics, real-time glass optics, button dimensions, and lockscreen layout coordinates.",
                colors
            )
        }
    }
}
