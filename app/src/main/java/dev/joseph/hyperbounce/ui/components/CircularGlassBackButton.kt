package dev.joseph.hyperbounce.ui.components

/*
 * The floating circular back button every subpage in this app uses. Per the app's
 * aesthetic direction, glass/backdrop is reserved for toggles, sliders, and the dropdown/
 * popup menus only — everything else (this button included) is flat. The "jelly" touch-
 * and-drag interaction stays: InteractiveHighlight still drives a radial touch glow plus
 * press/drag-driven translation and squish on a plain graphicsLayer, so the button still
 * stretches toward your finger and springs back — that's a motion/interaction quality, not
 * a rendering one, so it doesn't need a backdrop to work.
 */

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.glass.InteractiveHighlight
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun CircularGlassBackButton(
    onClick: () -> Unit,
    // Kept for call-site compatibility with the rest of the app (every screen already
    // passes its shared backdrop here) even though this button no longer draws glass —
    // harmless to keep threading it in case that changes again.
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val isDark = isSystemInDarkTheme()
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(if (isDark) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.08f))
            .graphicsLayer {
                val progress = interactiveHighlight.pressProgress
                val scale = lerp(1f, 1f + 4f.dp.toPx() / this.size.height, progress)

                val maxOffset = this.size.minDimension
                val initialDerivative = 0.06f
                val offset = interactiveHighlight.offset
                translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                val maxDragScale = 5f.dp.toPx() / this.size.height
                val offsetAngle = atan2(offset.y, offset.x)
                scaleX = scale + maxDragScale * abs(cos(offsetAngle) * offset.x / this.size.maxDimension)
                    .fastCoerceAtMost(1f)
                scaleY = scale + maxDragScale * abs(sin(offsetAngle) * offset.y / this.size.maxDimension)
                    .fastCoerceAtMost(1f)
            }
            .clickable(
                interactionSource = null,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        ChevronIcon(
            color = if (isDark) Color.White else Color.Black.copy(alpha = 0.82f),
            size = 13.dp,
            strokeWidth = 2.4.dp,
            pointingLeft = true
        )
    }
}
