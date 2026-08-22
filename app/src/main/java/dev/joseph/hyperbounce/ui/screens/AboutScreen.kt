package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun AboutScreen(
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    ScreenScaffold(
        title = "About",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(88.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (isDark) Color(0xFF1C1C1E) else Color.White)
                    .background(colors.accentBlue.copy(alpha = 0.18f))
            )
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BasicText("HyperBounce", style = HyperBounceType.title2.copy(color = colors.label))
                BasicText(
                    "Version 1.0.0 · UI Phase 1",
                    style = HyperBounceType.footnote.copy(color = colors.secondaryLabel)
                )
            }
        }

        SectionHeader("Status", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            AboutRow(
                "This build",
                "Settings UI only — every screen you've just used reads and writes real preferences, but the Xposed hooks don't read them yet.",
                colors
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            AboutRow(
                "Next phase",
                "Wire these preferences into PhysicsHook, LiquidTogglesHook, and the nav/toggles hook classes, and split the shared nav-bar code out per app.",
                colors
            )
        }

        SectionHeader("Built With", colors)
        GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
            AboutRow(
                "AndroidLiquidGlass 2.0.0",
                "Every toggle, slider, and the dropdown menu in this app is built on Kyant0's backdrop + shapes libraries.",
                colors
            )
            RowDivider(colors.separator, insetStart = 16.dp)
            AboutRow(
                "libxposed API 102",
                "The framework interface HyperBounce's hooks are written against.",
                colors
            )
        }
    }
}

@Composable
private fun AboutRow(title: String, body: String, colors: HyperBounceColors) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        BasicText(title, style = HyperBounceType.body.copy(color = colors.label))
        BasicText(
            body,
            modifier = Modifier.padding(top = 4.dp),
            style = HyperBounceType.footnote.copy(color = colors.secondaryLabel)
        )
    }
}
