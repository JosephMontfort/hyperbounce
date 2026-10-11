package dev.hyperios.app.ui.components

/*
 * A static (non-interactive) glass panel grouping settings rows — iOS's grouped-table-view
 * section card. Reads LocalGlassSettings (the Settings screen's master switch + blur/
 * refraction sliders): real backdrop blur+vibrancy when on, a flat fill when off.
 * Deliberately subtle/performance-light even at full intensity: a small blur radius and no
 * lens/chromatic aberration, since a whole stack of these sits on screen in a scrolling
 * list at once.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import dev.hyperios.app.ui.state.ComponentStyle
import dev.hyperios.app.ui.state.LocalComponentStyle
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val componentStyle = LocalComponentStyle.current
    if (componentStyle == ComponentStyle.MIUIX) {
        Card(
            modifier = modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(0.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer
            ),
            content = content
        )
    } else {
        val isDark = dev.hyperios.app.ui.theme.LocalIsDarkTheme.current
        val shape = RoundedCornerShape(cornerRadius)
        val cardColor = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
        
        Column(
            modifier
                .fillMaxWidth()
                .background(cardColor, shape)
                .clip(shape),
            content = content
        )
    }
}

/** A faint 1px inset divider between rows inside a [GlassCard]. */
@Composable
fun RowDivider(color: Color, insetStart: Dp = 56.dp) {
    val componentStyle = LocalComponentStyle.current
    val dividerColor = if (componentStyle == ComponentStyle.MIUIX) {
        MiuixTheme.colorScheme.dividerLine
    } else {
        color
    }
    val inset = if (componentStyle == ComponentStyle.MIUIX) 16.dp else insetStart
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = inset)
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(0.75.dp)
                .background(dividerColor)
        )
    }
}
