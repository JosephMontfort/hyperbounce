package dev.joseph.hyperbounce.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import dev.joseph.hyperbounce.ui.glass.InteractiveHighlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/** Catalog-style floating jelly back button; the glass surface and icon move as one unit. */
@Composable
fun CircularGlassBackButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp
) {
    val isDark = isSystemInDarkTheme()
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) { InteractiveHighlight(animationScope = animationScope) }

    Box(
        modifier
            .size(size)
            .graphicsLayer {
                val progress = interactiveHighlight.pressProgress
                val baseScale = lerp(1f, 1f + 4.dp.toPx() / this.size.height, progress)
                val minDim = this.size.minDimension
                val maxDim = this.size.maxDimension
                val offset = interactiveHighlight.offset
                val maxOffset = minDim
                val derivative = 0.05f
                val tx = if (maxOffset > 0f) maxOffset * tanh(derivative * offset.x / maxOffset) else 0f
                val ty = if (maxOffset > 0f) maxOffset * tanh(derivative * offset.y / maxOffset) else 0f
                val angle = atan2(offset.y, offset.x)
                val maxDragScale = 4.dp.toPx() / this.size.height
                translationX = tx
                translationY = ty
                scaleX = baseScale + maxDragScale * abs(cos(angle) * offset.x / maxDim).fastCoerceAtMost(1f)
                scaleY = baseScale + maxDragScale * abs(sin(angle) * offset.y / maxDim).fastCoerceAtMost(1f)
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { androidx.compose.foundation.shape.CircleShape },
                effects = {
                    vibrancy()
                    blur(4.dp.toPx())
                    lens(10.dp.toPx(), 14.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Default.copy(alpha = 0.52f) },
                shadow = { Shadow(radius = 14.dp, color = Color.Black.copy(alpha = 0.14f)) },
                innerShadow = { InnerShadow(radius = 7.dp, color = Color.White.copy(alpha = 0.16f)) },
                onDrawSurface = {
                    drawCircle(if (isDark) Color(0xFF18181B).copy(alpha = 0.34f) else Color.White.copy(alpha = 0.26f))
                }
            )
            .clickable(interactionSource = null, indication = null, role = Role.Button, onClick = onClick)
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        ChevronIcon(
            color = if (isDark) Color.White else Color.Black.copy(alpha = 0.86f),
            size = 15.dp,
            strokeWidth = 2.2.dp,
            pointingLeft = true
        )
    }
}
