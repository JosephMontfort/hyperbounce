package dev.joseph.hyperbounce.ui.glass

/*
 * Faithful port of com.kyant.backdrop.catalog.components.LiquidButton from the
 * AndroidLiquidGlass 2.0.0 demo catalog (backdrop library by Kyant0). Unmodified logic —
 * only the package changed. This is the base "jelly, touch-and-pulled" interactive glass
 * surface: CircularGlassBackButton and the picker rows in this app build on top of it.
 */

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

@Composable
fun LiquidButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    height: Dp = 48f.dp,
    horizontalPadding: Dp = 16f.dp,
    size: Dp? = null,
    content: @Composable RowScope.() -> Unit
) {
    val componentStyle = dev.joseph.hyperbounce.ui.state.LocalComponentStyle.current
    if (componentStyle == dev.joseph.hyperbounce.ui.state.ComponentStyle.MIUIX) {
        val btnColors = if (tint.isSpecified && tint != Color.Unspecified) {
            top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColorsPrimary()
        } else {
            top.yukonga.miuix.kmp.basic.ButtonDefaults.buttonColors()
        }
        val btnModifier = if (size != null) {
            modifier.size(size)
        } else {
            modifier.height(height)
        }
        top.yukonga.miuix.kmp.basic.Button(
            onClick = onClick,
            modifier = btnModifier,
            colors = btnColors,
            insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = horizontalPadding, vertical = 0.dp),
            content = content
        )
        return
    }

    val animationScope = rememberCoroutineScope()

    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(
            animationScope = animationScope
        )
    }

    val baseModifier = if (size != null) {
        modifier.size(size)
    } else {
        modifier
            .height(height)
            .padding(horizontal = horizontalPadding)
    }

    Row(
        baseModifier
            .zIndex(if (interactiveHighlight.pressProgress > 0.005f) 10f else 1f)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(2f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                layerBlock = if (isInteractive) {
                    {
                        val width = this.size.width
                        val heightPx = this.size.height

                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4f.dp.toPx() / heightPx, progress)

                        val maxOffset = this.size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 4f.dp.toPx() / heightPx
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX =
                            scale +
                                    maxDragScale * abs(cos(offsetAngle) * offset.x / this.size.maxDimension) *
                                    (width / heightPx).fastCoerceAtMost(1f)
                        scaleY =
                            scale +
                                    maxDragScale * abs(sin(offsetAngle) * offset.y / this.size.maxDimension) *
                                    (heightPx / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                }
            )
            .clickable(
                interactionSource = null,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            ),
        horizontalArrangement = if (size != null) Arrangement.Center else Arrangement.spacedBy(8f.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}
