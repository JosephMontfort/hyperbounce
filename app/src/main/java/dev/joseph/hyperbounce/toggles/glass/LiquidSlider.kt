package dev.joseph.hyperbounce.toggles.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFF007AFF),
    blurDp: Float = 8f,
    lensSizeDp: Float = 10f,
    lensDepthDp: Float = 14f,
    highlightStrength: Float = 0.67f,
    shadowRadiusDp: Float = 4f,
    springDamping: Float = 0.7f,
    springStiffness: Float = 800f
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = activeColor
    val trackColor = if (isLightTheme) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth
        val horizontalPaddingDp = 16.dp
        val horizontalPaddingPx = with(androidx.compose.ui.platform.LocalDensity.current) { horizontalPaddingDp.toPx() }
        val usableTrackWidth = (trackWidth - horizontalPaddingPx * 2).coerceAtLeast(1f)

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope, valueRange) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDrag) {
                        onValueChange(targetValue)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / usableTrackWidth)
                    onValueChange(
                        if (isLtr) (targetValue + delta).coerceIn(valueRange)
                        else (targetValue - delta).coerceIn(valueRange)
                    )
                },
                valueSpringDamping = springDamping,
                valueSpringStiffness = springStiffness
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }
                .collectLatest { value ->
                    if (dampedDragAnimation.targetValue != value) {
                        dampedDragAnimation.updateValue(value)
                    }
                }
        }

        // CUSTOM TRACK SHRINK EFFECT: The track mathematically shrinks when the thumb is pressed
        Box(
            Modifier
                .padding(horizontal = horizontalPaddingDp)
                .graphicsLayer {
                    val progress = dampedDragAnimation.pressProgress
                    scaleX = androidx.compose.ui.util.lerp(1f, 0.96f, progress)
                    scaleY = androidx.compose.ui.util.lerp(1f, 0.90f, progress)
                }
        ) {
            Box(Modifier.layerBackdrop(trackBackdrop)) {
                Box(
                    Modifier
                        .clip(Capsule())
                        .background(trackColor)
                        .pointerInput(animationScope) {
                            detectTapGestures { position ->
                                val delta = (valueRange.endInclusive - valueRange.start) * (position.x / usableTrackWidth)
                                val targetValue =
                                    (if (isLtr) valueRange.start + delta
                                    else valueRange.endInclusive - delta)
                                        .coerceIn(valueRange)
                                dampedDragAnimation.animateToValue(targetValue)
                                onValueChange(targetValue)
                            }
                        }
                        .height(6f.dp)
                        .fillMaxWidth()
                )

                Box(
                    Modifier
                        .clip(Capsule())
                        .background(accentColor)
                        .height(6f.dp)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val width = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                            layout(width, placeable.height) {
                                placeable.place(0, 0)
                            }
                        }
                )
            }
        }

        Box(
            Modifier
                .graphicsLayer {
                    val thumbCenter = horizontalPaddingPx + usableTrackWidth * dampedDragAnimation.progress
                    val minCenter = horizontalPaddingPx
                    val maxCenter = horizontalPaddingPx + usableTrackWidth
                    val clampedCenter = thumbCenter.fastCoerceIn(minCenter, maxCenter)
                    translationX = (clampedCenter - size.width / 2f) * if (isLtr) 1f else -1f
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(blurDp.dp.toPx() * (1f - progress))
                        lens(
                            lensSizeDp.dp.toPx() * progress,
                            lensDepthDp.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width * highlightStrength,
                            blurRadius = Highlight.Ambient.blurRadius * highlightStrength,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = shadowRadiusDp.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}
