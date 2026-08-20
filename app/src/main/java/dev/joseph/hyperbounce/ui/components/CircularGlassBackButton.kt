package dev.joseph.hyperbounce.ui.components

/*
 * The floating circular back button every subpage in this app uses, built the same way
 * the library's own LiquidButton is: an InteractiveHighlight drives a radial highlight
 * plus press/drag-driven translation and squish on the backdrop's own graphicsLayer, so
 * touching and dragging the button in any direction makes the glass itself stretch toward
 * your finger like a soft, jelly-like blob before springing back — exactly the "jelly
 * effect when touched and pulled" behavior already baked into this library.
 *
 * Reads LocalGlassSettings for blur/refraction/chromatic-aberration and the master switch
 * (Home screen's "Liquid Glass Effects" toggle): when off, this renders as a flat
 * translucent circle instead of real backdrop blur — the jelly press/drag motion still
 * works either way, only the material changes. See GlobalGlassSettings' kdoc.
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
    val isDark = isSystemInDarkTheme()
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
            .then(
                if (glass.enabled) {
                    Modifier.drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            vibrancy()
                            blur(glass.blurDp.dp.toPx() / 3f)
                            lens(
                                glass.refractionHeightDp.dp.toPx(),
                                glass.refractionAmountDp.dp.toPx(),
                                chromaticAberration = glass.chromaticAberration
                            )
                        },
                        highlight = { Highlight.Ambient },
                        shadow = { Shadow(radius = 8f.dp, color = Color.Black.copy(alpha = 0.16f)) },
                        layerBlock = jellyLayerBlock
                    )
                } else {
                    Modifier
                        .clip(CircleShape)
                        .background(if (isDark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.1f))
                        .graphicsLayer(block = jellyLayerBlock)
                }
            )
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
        ChevronIcon(color = Color.White, size = 13.dp, strokeWidth = 2.4.dp, pointingLeft = true)
    }
}
