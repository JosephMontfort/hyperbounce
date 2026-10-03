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

import androidx.compose.foundation.border

@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)
    val cardColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardColor, shape)
            .border(1.dp, borderColor, shape),
        content = content
    )
}

/** A faint 1px inset divider between rows inside a [GlassCard]. */
@Composable
fun RowDivider(color: Color, insetStart: Dp = 18.dp) {
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

