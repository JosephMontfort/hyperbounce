package dev.joseph.hyperbounce.toggles.glass

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
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

@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    blurDp: Float = 8f,
    lensSizeDp: Float = 5f,
    lensDepthDp: Float = 10f,
    highlightStrength: Float = 0.67f,
    shadowRadiusDp: Float = 4f,
    dragSensitivity: Float = 0.72f,
    springDamping: Float = 0.82f,
    springStiffness: Float = 400f
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = if (isLightTheme) Color(0xFF34C759) else Color(0xFF30D158)
    val trackColor = if (isLightTheme) Color(0xFF787878).copy(0.2f) else Color(0xFF787880).copy(0.36f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20f.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    val view = LocalView.current
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableFloatStateOf(if (selected()) 1f else 0f) }
    
    val dampedDragAnimation = remember(animationScope) {
        dev.joseph.hyperbounce.toggles.glass.DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    onSelect(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (selected()) 0f else 1f
                    onSelect(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) didDrag = dragAmount.x != 0f

                // Deliberately reduce the direct finger-to-track mapping. The animation spring
                // then eases the rendered thumb toward this softened target, creating a very
                // small, premium-feeling follow delay even during fast pulls.
                val delta = (dragAmount.x / dragWidth) * dragSensitivity
                fraction = if (isLtr) {
                    (fraction + delta).fastCoerceIn(0f, 1f)
                } else {
                    (fraction - delta).fastCoerceIn(0f, 1f)
                }
            },
            valueSpringDamping = springDamping,
            valueSpringStiffness = springStiffness,
            pressSpringDamping = 1f,
            pressSpringStiffness = 720f,
            scaleXSpringDamping = 0.72f,
            scaleYSpringDamping = 0.78f,
            scaleSpringStiffness = 190f
        )
    }

    var lastHapticValue by remember { mutableFloatStateOf(dampedDragAnimation.value) }
    LaunchedEffect(dampedDragAnimation.value) {
        val value = dampedDragAnimation.value
        if ((value >= 0.99f && lastHapticValue < 0.99f) || (value <= 0.01f && lastHapticValue > 0.01f)) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        lastHapticValue = value
    }

    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }.collectLatest { fraction ->
            dampedDragAnimation.updateValue(fraction)
        }
    }
    LaunchedEffect(selected) {
        snapshotFlow { selected() }.collectLatest { isSelected ->
            val target = if (isSelected) 1f else 0f
            if (target != fraction) {
                fraction = target
                dampedDragAnimation.animateToValue(target)
            }
        }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier.layerBackdrop(trackBackdrop)
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
                    translationX = if (isLtr) lerp(padding, padding + dragWidth, fraction) else lerp(-padding, -(padding + dragWidth), fraction)
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
                        blur(blurDp.dp.toPx() * (1f - progress))
                        lens(lensSizeDp.dp.toPx() * progress, lensDepthDp.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(width = Highlight.Ambient.width * highlightStrength, blurRadius = Highlight.Ambient.blurRadius * highlightStrength, alpha = progress)
                    },
                    shadow = { Shadow(radius = shadowRadiusDp.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = { val progress = dampedDragAnimation.pressProgress; InnerShadow(radius = 4f.dp * progress, alpha = progress) },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
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
