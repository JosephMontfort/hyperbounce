package dev.joseph.hyperbounce.ui.components

/*
 * The floating circular back button every subpage in this app uses, built the same way
 * the library's own LiquidButton is: InteractiveHighlight drives a radial touch glow, and
 * the jelly translate/scale is passed as drawBackdrop's own `layerBlock` — NOT a separate
 * `.graphicsLayer{}` modifier appended after `.clip()`. That distinction matters: a
 * previous version of this file called `.clip(CircleShape)` and `.graphicsLayer{ jelly }`
 * as two separate modifiers, which put the clip on a static, untransformed layer while the
 * jelly transform moved a different, inner layer — so during a drag the circular clip
 * boundary stayed put while the background+icon slid underneath it, exposing the button's
 * square underlying bounds as a hard edge. drawBackdrop's `layerBlock` applies the
 * transform on the SAME graphics layer that establishes the clip+shape, so the circular
 * boundary itself moves and squishes together with the content — no seam, ever, exactly
 * like the catalog's LiquidButton.
 */

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import dev.joseph.hyperbounce.ui.glass.InteractiveHighlight
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun CircularGlassBackButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val glass = LocalGlassSettings.current
    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    val jellyLayerBlock: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
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

    Box(
        modifier
            .size(size)
            .clickable(
                interactionSource = null,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                Modifier
                    .graphicsLayer {
                        jellyLayerBlock()
                        shape = CircleShape
                        clip = true
                    }
                    .background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color(0xFFFFFFFF))
                    .border(0.5.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
            )
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier),
        contentAlignment = Alignment.Center
    ) {
        val iconColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black
        ChevronIcon(color = iconColor, size = 13.dp, strokeWidth = 2.4.dp, pointingLeft = true)
    }
}
