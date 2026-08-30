package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import dev.joseph.hyperbounce.R
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

@Composable
fun AboutScreen(
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit
) {
    ScreenScaffold(
        title = "About",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { _ ->
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(20.dp)
            Image(
                painter = painterResource(R.drawable.ic_app_icon),
                contentDescription = "HyperBounce Icon",
                modifier = Modifier
                    .size(88.dp)
                    .shadow(12.dp, shape = shape, clip = false)
                    .clip(shape)
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
