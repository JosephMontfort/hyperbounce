package dev.joseph.hyperbounce.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/** Shared subtle liquid-glass surface for grouped app content. */
@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    content: @Composable ColumnScope.(Backdrop) -> Unit
) {
    val contentBackdrop = rememberLayerBackdrop()
    Column(
        modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(cornerRadius) },
                effects = {
                    vibrancy()
                    blur(5.dp.toPx())
                    lens(7.dp.toPx(), 12.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Default.copy(alpha = 0.34f) },
                shadow = { Shadow(radius = 14.dp, color = Color.Black.copy(alpha = 0.10f)) },
                innerShadow = { InnerShadow(radius = 7.dp, color = Color.White.copy(alpha = 0.11f)) },
                exportedBackdrop = contentBackdrop,
                onDrawSurface = {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.055f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx())
                    )
                }
            ),
        content = { content(contentBackdrop) }
    )
}

@Composable
fun RowDivider(color: Color, insetStart: Dp = 56.dp) {
    Box(Modifier.fillMaxWidth().padding(start = insetStart)) {
        Spacer(Modifier.fillMaxWidth().height(0.75.dp).background(color.copy(alpha = 0.55f)))
    }
}
