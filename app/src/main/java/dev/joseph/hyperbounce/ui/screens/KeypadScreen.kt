package dev.joseph.hyperbounce.ui.screens

/*
 * Deliberately minimal: just the on/off switch. The keypad hook's actual backend logic is
 * being replaced later with a different implementation, so this page doesn't try to expose
 * any visual tuning for it yet — only the enable/disable wiring is real right now.
 */

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.SectionFooter
import dev.joseph.hyperbounce.ui.components.SettingsToggleRow
import dev.joseph.hyperbounce.ui.components.SlidersIcon
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors

@Composable
fun KeypadScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    ScreenScaffold(
        title = "Keypad",
        subtitle = "Dialer & lock screen keypad",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) {
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            SettingsToggleRow(
                title = "Liquid Glass Keypad",
                subtitle = if (repository.keypadEnabled) "Enabled" else "Disabled",
                colors = colors,
                iconTint = colors.accentBlue.copy(alpha = 0.85f),
                backdrop = backdrop,
                checked = repository.keypadEnabled,
                onCheckedChange = repository::setKeypadEnabled,
                icon = { SlidersIcon(color = Color.White, size = 14.dp) }
            )
        }
        SectionFooter(
            "This hook is getting a new backend implementation — visual tuning for it will show up here once that's ready.",
            colors
        )
    }
}
