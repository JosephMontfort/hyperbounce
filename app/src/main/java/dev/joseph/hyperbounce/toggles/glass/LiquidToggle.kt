package dev.joseph.hyperbounce.toggles.glass

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
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

/**
 * Mutable gesture bookkeeping that intentionally is NOT Compose state.
 *
 * Pointer movement can arrive much more frequently than frames. Keeping these
 * values outside the snapshot system prevents a recomposition for every move.
 */
private class ToggleDragState {
    var didDrag: Boolean = false

    /**
     * Signed amount by which the finger has travelled beyond an edge.
     * Negative = beyond the off/left edge, positive = beyond the on/right edge.
     *
     * While this value is non-zero the thumb remains pinned to the edge. The
     * finger must travel back through the same out-of-bounds distance before
     * the thumb starts moving again. This gives the desired iOS-style
     * edge-pinning/elastic feel.
     */
    var edgeOverscroll: Float = 0f
}

private class ToggleHapticState(initial: Float) {
    var lastValue: Float = initial
}

@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (isLightTheme) Color(0xFF34C759) else Color(0xFF30D158)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    val view = LocalView.current

    // These are ordinary mutable fields, not snapshot state. Gesture bookkeeping
    // therefore does not trigger recomposition on every MotionEvent.
    val dragState = remember { ToggleDragState() }

    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = if (selected()) 1f else 0f,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {
                dragState.didDrag = false
                dragState.edgeOverscroll = 0f
            },
            onDragStopped = {
                if (dragState.didDrag) {
                    val target = if (targetValue >= 0.5f) 1f else 0f
                    dragState.edgeOverscroll = 0f
                    onSelect(target == 1f)
                } else {
                    // Tap: the state update is immediate, but the selected-state
                    // effect below animates the visible thumb deliberately slowly.
                    dragState.edgeOverscroll = 0f
                    onSelect(!selected())
                }
                dragState.didDrag = false
            },
            onDrag = { _, dragAmount ->
                val delta =
                    (if (isLtr) dragAmount.x else -dragAmount.x) / dragWidth

                if (delta != 0f) {
                    if (dragState.edgeOverscroll < 0f) {
                    // Finger is still outside the left edge: consume rightward
                    // movement until the entire overscroll has been cancelled.
                    if (delta > 0f) {
                        val remaining = dragState.edgeOverscroll + delta
                        if (remaining < 0f) {
                            dragState.edgeOverscroll = remaining
                            updateValue(0f)
                        } else {
                            dragState.edgeOverscroll = 0f
                            if (remaining > 0f) {
                                updateValue((value + remaining).fastCoerceIn(0f, 1f))
                            } else {
                                updateValue(0f)
                            }
                        }
                    } else {
                        dragState.edgeOverscroll += delta
                        updateValue(0f)
                    }
                } else if (dragState.edgeOverscroll > 0f) {
                    // Finger is still outside the right edge: consume leftward
                    // movement until the entire overscroll has been cancelled.
                    if (delta < 0f) {
                        val remaining = dragState.edgeOverscroll + delta
                        if (remaining > 0f) {
                            dragState.edgeOverscroll = remaining
                            updateValue(1f)
                        } else {
                            dragState.edgeOverscroll = 0f
                            if (remaining < 0f) {
                                updateValue((value + remaining).fastCoerceIn(0f, 1f))
                            } else {
                                updateValue(1f)
                            }
                        }
                    } else {
                        dragState.edgeOverscroll += delta
                        updateValue(1f)
                    }
                } else {
                    val rawTarget = value + delta

                    when {
                        rawTarget < 0f -> {
                            dragState.edgeOverscroll = rawTarget
                            updateValue(0f)
                        }
                        rawTarget > 1f -> {
                            dragState.edgeOverscroll = rawTarget - 1f
                            updateValue(1f)
                        }
                        else -> {
                            updateValue(rawTarget)
                        }
                    }
                }

                    if (!dragState.didDrag) {
                        dragState.didDrag = true
                    }
                }
            }
        )
    }

    val hapticState = remember { ToggleHapticState(dampedDragAnimation.value) }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { dampedDragAnimation.value }.collectLatest { value ->
            // Haptic only; unlike the old key-based effect this coroutine is not
            // restarted every time the animated value changes. The previous value
            // is kept outside Compose state so haptic samples do not cause recomposition.
            val previous = hapticState.lastValue
            if ((value >= 0.99f && previous < 0.99f) ||
                (value <= 0.01f && previous > 0.01f)
            ) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
            hapticState.lastValue = value
        }
    }

    // One long-lived state observer rather than a LaunchedEffect keyed by the
    // `selected` lambda identity. This avoids restarting the collector when the
    // host recomposes.
    LaunchedEffect(Unit) {
        snapshotFlow { selected() }.collectLatest { isSelected ->
            val target = if (isSelected) 1f else 0f
            if (target != dampedDragAnimation.targetValue) {
                dampedDragAnimation.animateToValue(target)
            }
        }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind {
                    val fraction = dampedDragAnimation.value
                    drawRect(lerp(trackColor, accentColor, fraction))
                }
                .size(64f.dp, 28f.dp)
        )
        Box(
            Modifier
                .graphicsLayer {
                    val fraction = dampedDragAnimation.value
                    val padding = 2f.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, fraction)
                        else lerp(-padding, -(padding + dragWidth), fraction)
                }
                .semantics { role = Role.Switch }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 0.75f, progress)
                            val scaleY = lerp(0f, 0.75f, progress)
                            scale(scaleX, scaleY) { drawBackdrop() }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            5f.dp.toPx() * progress,
                            10f.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
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
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f -
                            (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f -
                            (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
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
