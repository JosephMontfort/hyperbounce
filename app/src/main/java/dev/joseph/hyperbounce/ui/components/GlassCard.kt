package dev.joseph.hyperbounce.ui.components

/*
 * Reads LocalGlassSettings: when the master switch is off, this renders as a flat
 * translucent card instead of real backdrop blur+vibrancy — see GlobalGlassSettings' kdoc.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings

/**
 * A static (non-interactive) glass panel used to group settings rows, mirroring iOS's
 * grouped-table-view section card — but rendered as real backdrop blur+vibrancy instead of
 * a flat fill.
 */
@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val glass = LocalGlassSettings.current
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier
            .fillMaxWidth()
            .then(
                if (glass.enabled) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { shape },
                        effects = {
                            vibrancy()
                            blur(glass.blurDp.dp.toPx() / 4f)
                        },
                        highlight = { Highlight.Ambient },
                        shadow = { Shadow(radius = 6f.dp, color = Color.Black.copy(alpha = 0.08f)) }
                    )
                } else {
                    Modifier.background(
                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.5f)
                    )
                }
            )
            .clip(shape),
        content = content
    )
}

/** A faint 1px inset divider between rows inside a [GlassCard]. */
@Composable
fun RowDivider(color: Color, insetStart: Dp = 56.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = insetStart)
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(0.75.dp)
                .background(color)
        )
    }
}
