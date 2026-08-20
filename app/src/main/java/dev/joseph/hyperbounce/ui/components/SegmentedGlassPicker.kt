package dev.joseph.hyperbounce.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import dev.joseph.hyperbounce.ui.glass.LocalGlassSettings
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import androidx.compose.animation.core.animateDpAsState

/**
 * iOS-style segmented control (used for "which rendering engine" pickers). The selected
 * segment is a real glass pill — backdrop blur+vibrancy+lens+highlight — that glides
 * between segments with a spring, not a flat color fill. Falls back to a flat tinted pill
 * when LocalGlassSettings' master switch is off.
 */
@Composable
fun SegmentedGlassPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    colors: HyperBounceColors,
    modifier: Modifier = Modifier
) {
    val glass = LocalGlassSettings.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.separator.copy(alpha = 0.18f))
            .padding(3.dp)
    ) {
        val segmentWidth = maxWidth / options.size
        val targetOffset = segmentWidth * selectedIndex
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
            label = "segmentOffset"
        )

        Box(
            Modifier
                .fillMaxSize()
        ) {
            // Sliding pill — real glass when enabled, flat tint otherwise.
            Box(
                Modifier
                    .padding(start = animatedOffset)
                    .fillMaxHeight()
                    .width(segmentWidth)
                    .then(
                        if (glass.enabled) {
                            Modifier.drawBackdrop(
                                backdrop = backdrop,
                                shape = { RoundedCornerShape(9.dp) },
                                effects = {
                                    vibrancy()
                                    blur(glass.blurDp.dp.toPx() / 4f)
                                    lens(
                                        glass.refractionHeightDp.dp.toPx() / 2.5f,
                                        glass.refractionAmountDp.dp.toPx() / 2f,
                                        chromaticAberration = glass.chromaticAberration
                                    )
                                },
                                highlight = { Highlight.Ambient },
                                shadow = { Shadow(radius = 4.dp, color = Color.Black.copy(alpha = 0.18f)) }
                            )
                        } else {
                            Modifier.background(colors.elevatedSurfaceTint.copy(alpha = 0.9f))
                        }
                    )
                    .clip(RoundedCornerShape(9.dp))
            )

            Row(Modifier.fillMaxSize()) {
                options.forEachIndexed { index, label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelect(index) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            label,
                            style = HyperBounceType.footnote.copy(
                                color = if (index == selectedIndex) colors.label else colors.secondaryLabel
                            )
                        )
                    }
                }
            }
        }
    }
}
