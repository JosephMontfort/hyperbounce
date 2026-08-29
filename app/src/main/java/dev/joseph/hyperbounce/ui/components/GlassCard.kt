package dev.joseph.hyperbounce.ui.components

/*
 * A static (non-interactive) glass panel grouping settings rows — iOS's grouped-table-view
 * section card. Reads LocalGlassSettings (the Settings screen's master switch + blur/
 * refraction sliders): real backdrop blur+vibrancy when on, a flat fill when off.
 * Deliberately subtle/performance-light even at full intensity: a small blur radius and no
 * lens/chromatic aberration, since a whole stack of these sits on screen in a scrolling
 * list at once.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
                    Modifier
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { shape },
                            effects = {
                                vibrancy()
                                blur((glass.blurDp / 4f).dp.toPx())
                            },
                            highlight = { Highlight.Ambient },
                            shadow = { Shadow(radius = 6f.dp, color = Color.Black.copy(alpha = 0.2f)) }
                        )
                        .background(
                            if (isDark) Color(0xFF1C1C1E).copy(alpha = 0.78f)
                            else Color.White.copy(alpha = 0.7f),
                            shape
                        )
                } else {
                    Modifier.background(
                        if (isDark) Color(0xFF1C1C1E) else Color.White,
                        shape
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
                .height(0.5.dp)
                .background(color)
        )
    }
}
