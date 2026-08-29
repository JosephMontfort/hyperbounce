package dev.joseph.hyperbounce.ui.glass

/*
 * Ported from the user-authored ExpandableGlassMenuContent catalog page (built against
 * this same AndroidLiquidGlass library) — MenuAlignment, MenuAnimationPreset,
 * ExpandableGlassMenu, and GlassEffectContainer below are carried over with their
 * animation/gesture/haptics logic unchanged: the long-press-or-tap-to-open gesture, the
 * drag-to-hover-then-release-to-select interaction, the per-frame jelly/squish
 * graphicsLayer math, the zero-recomposition width/height morph via Modifier.layout, and
 * the haptic tick on row hover are all byte-for-byte the same as the source, including the
 * later "advanced physics tuning" pass (spring overshoot bulge, motion blur peak, drag
 * jelly tension, content pop amplitude, arc Y offset, animation speed multiplier) that
 * replaced several previously-hardcoded constants with named parameters — those are code-
 * level tuning knobs: change the defaults below to retune the motion, no UI needed.
 *
 * Only the package changed, plus the demo-only "Properties"/"Advanced Physics Tuning"
 * editor screen (ExpandableGlassMenuContent, TuningControl) was not ported — this app
 * doesn't need a UI for live-tuning the menu's own parameters, it just calls
 * ExpandableGlassMenu directly (see ui/screens/RootScaffold.kt) with isGlassEnabled/
 * blurRadius/refractionHeight/refractionAmount/chromaticAberration/isHapticsEnabled
 * sourced from LocalGlassSettings — the component already exposes exactly those as hoisted
 * parameters, so no internal change was needed to wire it to this app's global glass
 * settings.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

enum class MenuAlignment(val label: String) {
    TopLeading("T-Left"), TopTrailing("T-Right"), BottomLeading("B-Left"), BottomTrailing("B-Right");

    val composeAlignment: Alignment
        get() = when (this) {
            TopLeading -> Alignment.TopStart
            TopTrailing -> Alignment.TopEnd
            BottomLeading -> Alignment.BottomStart
            BottomTrailing -> Alignment.BottomEnd
        }

    val transformOrigin: TransformOrigin
        get() = when (this) {
            TopLeading -> TransformOrigin(0f, 0f)
            TopTrailing -> TransformOrigin(1f, 0f)
            BottomLeading -> TransformOrigin(0f, 1f)
            BottomTrailing -> TransformOrigin(1f, 1f)
        }

    fun calculateOffsetY(blurProgress: Float, maxOffsetPx: Float): Float {
        return when (this) {
            TopLeading, TopTrailing -> maxOffsetPx * blurProgress
            BottomLeading, BottomTrailing -> -maxOffsetPx * blurProgress
        }
    }
}

enum class MenuAnimationPreset(val label: String) {
    Bouncy("Bouncy"), Smooth("Smooth"), Snappy("Snappy");

    fun getSpec(isClosing: Boolean = false, speedMultiplier: Float = 1f): AnimationSpec<Float> = when (this) {
        // Ultra-soft settling via lower stiffness and precise threshold
        Bouncy -> if (isClosing) spring(dampingRatio = 0.8f, stiffness = 200f * (speedMultiplier * speedMultiplier), visibilityThreshold = 0.001f)
                  else spring(dampingRatio = 0.55f, stiffness = 120f * (speedMultiplier * speedMultiplier), visibilityThreshold = 0.001f)
        Smooth -> tween(durationMillis = (650f / speedMultiplier).toInt(), easing = FastOutSlowInEasing)
        Snappy -> spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow * (speedMultiplier * speedMultiplier))
    }
}

@Composable
fun ExpandableGlassMenu(
    animatableProgress: Animatable<Float, *>,
    animationPreset: MenuAnimationPreset,
    alignment: MenuAlignment,
    backdrop: Backdrop,
    isGlassEnabled: Boolean,
    isHapticsEnabled: Boolean,
    cornerRadius: Dp,
    blurRadius: Float,
    refractionHeight: Float,
    refractionAmount: Float,
    chromaticAberration: Boolean,
    horizontalOffset: Float,
    verticalOffset: Float,
    modifier: Modifier = Modifier,
    labelSize: Size = Size(55f, 55f),
    // Advanced physics tuning — code-level knobs for the animation feel. Change these
    // values directly to retune the motion; defaults match the reference implementation.
    animationSpeedMultiplier: Float = 1f,
    containerBulgeMultiplier: Float = 0.395f,
    contentBulgeMultiplier: Float = 0.15f,
    contentPopX: Float = 0.03f,
    contentPopY: Float = 0.01f,
    dragJellyTension: Float = 0.05f,
    motionBlurAmount: Float = 25f,
    arcYOffsetDp: Float = 75f,
    // Fires when the finger is released while hovering a row during a long-press-drag
    // gesture (not a plain tap on the row — that's handled by MenuRow's own onClick).
    // Called just before the menu closes, with the hovered row's index. Default no-op so
    // existing callers that only want tap-to-select keep compiling unchanged.
    onRowSelected: (index: Int) -> Unit = {},
    label: @Composable () -> Unit,
    content: @Composable (globalTouchPosition: Offset, closeMenu: () -> Unit, hoveredIndex: Int?, setHovered: (Int?) -> Unit) -> Unit
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val haptic = LocalHapticFeedback.current
    val animationScope = rememberCoroutineScope()

    var contentMeasuredSize by remember { mutableStateOf(Size.Zero) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var isPressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var globalTouchPosition by remember { mutableStateOf(Offset.Unspecified) }
    var labelCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

    var hoveredIndex by remember { mutableStateOf<Int?>(null) }
    val setHovered: (Int?) -> Unit = { hoveredIndex = it }

    val labelSizePx = with(density) { Size(labelSize.width.dp.toPx(), labelSize.height.dp.toPx()) }

    LaunchedEffect(hoveredIndex) {
        if (isHapticsEnabled && isDragging && hoveredIndex != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val closeMenu: () -> Unit = {
        hoveredIndex = null
        animationScope.launch { animatableProgress.animateTo(0f, animationPreset.getSpec(isClosing = true, speedMultiplier = animationSpeedMultiplier)) }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = alignment.composeAlignment) {
        GlassEffectContainer(
            animatableProgress = animatableProgress,
            dragOffset = dragOffset,
            isPressed = isPressed,
            alignment = alignment,
            backdrop = backdrop,
            isGlassEnabled = isGlassEnabled,
            cornerRadius = cornerRadius,
            blurRadius = blurRadius,
            refractionHeight = refractionHeight,
            refractionAmount = refractionAmount,
            chromaticAberration = chromaticAberration,
            horizontalOffset = horizontalOffset,
            verticalOffset = verticalOffset,
            containerBulgeMultiplier = containerBulgeMultiplier,
            contentBulgeMultiplier = contentBulgeMultiplier,
            contentPopX = contentPopX,
            contentPopY = contentPopY,
            dragJellyTension = dragJellyTension,
            motionBlurAmount = motionBlurAmount,
            arcYOffsetDp = arcYOffsetDp,
            labelSize = labelSizePx,
            contentSize = contentMeasuredSize,
            label = {
                Box(
                    modifier = Modifier.size(with(density) { labelSizePx.width.toDp() }).onGloballyPositioned { labelCoordinates = it }
                        .pointerInput(animationPreset, animationSpeedMultiplier) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                isPressed = true
                                val downPos = down.position
                                val tapTimeout = viewConfiguration.longPressTimeoutMillis

                                var isLongPress = false
                                var isSimpleDrag = false
                                var upEvent: androidx.compose.ui.input.pointer.PointerInputChange? = null

                                val isExpanded = animatableProgress.value > 0.5f

                                val timeoutResult = withTimeoutOrNull(tapTimeout) {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        val change = event.changes.firstOrNull()
                                        if (change != null) {
                                            if (!change.pressed) {
                                                upEvent = change
                                                break
                                            } else if ((change.position - downPos).getDistance() > viewConfiguration.touchSlop) {
                                                isSimpleDrag = true
                                                break
                                            }
                                        }
                                    }
                                    true
                                }

                                if (timeoutResult == null && !isExpanded) {
                                    isLongPress = true
                                    animationScope.launch { animatableProgress.animateTo(1f, animationPreset.getSpec(isClosing = false, speedMultiplier = animationSpeedMultiplier)) }
                                }

                                var tracking = upEvent == null
                                isDragging = true
                                while (tracking) {
                                    val event = awaitPointerEvent(PointerEventPass.Main)
                                    val change = event.changes.firstOrNull()
                                    if (change == null || !change.pressed) {
                                        tracking = false
                                    } else {
                                        dragOffset = change.position - downPos
                                        if (isLongPress || isExpanded) {
                                            globalTouchPosition = labelCoordinates?.localToWindow(change.position) ?: Offset.Unspecified
                                        }
                                        change.consume()
                                    }
                                }

                                isPressed = false
                                isDragging = false
                                val finalHoveredIndex = hoveredIndex

                                if (upEvent != null && !isSimpleDrag && !isLongPress && timeoutResult != null) {
                                    upEvent.consume()
                                    val target = if (isExpanded) 0f else 1f
                                    animationScope.launch { animatableProgress.animateTo(target, animationPreset.getSpec(isClosing = target == 0f, speedMultiplier = animationSpeedMultiplier)) }
                                } else if (isSimpleDrag || isLongPress) {
                                    // Deliberately not gated on the pre-gesture `isExpanded` value: a
                                    // single continuous long-press-then-drag-then-release starting from
                                    // the closed state must still be able to select whatever row the
                                    // finger was over when it lifted, not just a drag that started on an
                                    // already-open menu.
                                    if (finalHoveredIndex != null) {
                                        onRowSelected(finalHoveredIndex)
                                        closeMenu()
                                    } else if (dragOffset.getDistance() > 20f) {
                                        closeMenu()
                                    }
                                }

                                dragOffset = Offset.Zero
                                globalTouchPosition = Offset.Unspecified
                                if (animatableProgress.targetValue == 0f) hoveredIndex = null
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { label() }
            },
            content = {
                Column(
                    modifier = Modifier.width(IntrinsicSize.Max).onSizeChanged { if (it.width > 0 && it.height > 0) contentMeasuredSize = it.toSize() }.padding(10f.dp),
                    verticalArrangement = Arrangement.spacedBy(12f.dp)
                ) {
                    content(globalTouchPosition, closeMenu, hoveredIndex, setHovered)
                }
            }
        )
    }
}

@Composable
fun GlassEffectContainer(
    animatableProgress: Animatable<Float, *>,
    dragOffset: Offset,
    isPressed: Boolean,
    alignment: MenuAlignment,
    backdrop: Backdrop,
    isGlassEnabled: Boolean,
    cornerRadius: Dp,
    blurRadius: Float,
    refractionHeight: Float,
    refractionAmount: Float,
    chromaticAberration: Boolean,
    horizontalOffset: Float,
    verticalOffset: Float,
    containerBulgeMultiplier: Float,
    contentBulgeMultiplier: Float,
    contentPopX: Float,
    contentPopY: Float,
    dragJellyTension: Float,
    motionBlurAmount: Float,
    arcYOffsetDp: Float,
    labelSize: Size,
    contentSize: Size,
    label: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val isLightTheme = !isSystemInDarkTheme()

    val animatedDragX by animateFloatAsState(dragOffset.x, spring(stiffness = 400f, dampingRatio = 0.6f))
    val animatedDragY by animateFloatAsState(dragOffset.y, spring(stiffness = 400f, dampingRatio = 0.6f))

    // Press illumination progress
    val isPressing = isPressed && animatableProgress.targetValue == 0f
    val buttonPressProgress by animateFloatAsState(if (isPressing) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 400f))

    Box(
        modifier = Modifier
            // ZERO-RECOMPOSITION LAYOUT (Executes measuring natively without calling recompose)
            .layout { measurable, constraints ->
                val p = animatableProgress.value
                val widthDiff = (contentSize.width - labelSize.width).coerceAtLeast(0f)
                val heightDiff = (contentSize.height - labelSize.height).coerceAtLeast(0f)

                // Vertical Pill Morph (Height pops instantly, width follows softly)
                val widthProgress = if (p > 1f) 1f + (p - 1f) * containerBulgeMultiplier else (p * p)
                val heightProgress = if (p > 1f) 1f + (p - 1f) * containerBulgeMultiplier else sin(p * (PI / 2f)).toFloat()

                val currentWidthPx = labelSize.width + widthDiff * widthProgress
                val currentHeightPx = labelSize.height + heightDiff * heightProgress

                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = currentWidthPx.toInt(),
                        maxWidth = currentWidthPx.toInt(),
                        minHeight = currentHeightPx.toInt(),
                        maxHeight = currentHeightPx.toInt()
                    )
                )
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            .graphicsLayer {
                val progress = animatableProgress.value
                val blurProgress = if (progress > 0.5f) (1f - progress) / 0.5f else progress / 0.5f
                val squishScale = 1f - (blurProgress.coerceIn(0f, 1f) * 0.05f)

                val maxOffsetPx = with(density) { arcYOffsetDp.dp.toPx() }
                val offsetY = alignment.calculateOffsetY(blurProgress, maxOffsetPx)

                val w = size.width
                val h = size.height
                val minDim = min(w, h)
                val maxDim = max(w, h)

                val maxJellyOffset = minDim
                val initialDerivative = dragJellyTension
                val jellyTx = if (maxJellyOffset > 0f) maxJellyOffset * tanh(initialDerivative * animatedDragX / maxJellyOffset) else 0f
                val jellyTy = if (maxJellyOffset > 0f) maxJellyOffset * tanh(initialDerivative * animatedDragY / maxJellyOffset) else 0f

                val baseScale = lerp(1f, 1f + with(density) { 4f.dp.toPx() } / h, buttonPressProgress)
                val maxDragScale = with(density) { 4f.dp.toPx() / h }
                val offsetAngle = atan2(animatedDragY, animatedDragX)

                val jellySx = baseScale + maxDragScale * abs(cos(offsetAngle) * animatedDragX / maxDim) * (w / h).coerceAtMost(1f)
                val jellySy = baseScale + maxDragScale * abs(sin(offsetAngle) * animatedDragY / maxDim) * (h / w).coerceAtMost(1f)

                val expandedTx = -animatedDragX * 0.03f
                val expandedTy = -animatedDragY * 0.03f
                val expandedStretchX = (1f + abs(animatedDragX) * 0.00005f - abs(animatedDragY) * 0.00005f).coerceIn(0.99f, 1.01f)
                val expandedStretchY = (1f + abs(animatedDragY) * 0.00005f - abs(animatedDragX) * 0.00005f).coerceIn(0.99f, 1.01f)

                translationX = lerp(jellyTx, expandedTx, progress) + with(density) { horizontalOffset.dp.toPx() * progress }
                translationY = offsetY + lerp(jellyTy, expandedTy, progress) + with(density) { verticalOffset.dp.toPx() * progress }
                scaleX = squishScale * lerp(jellySx, expandedStretchX, progress)
                scaleY = squishScale * lerp(jellySy, expandedStretchY, progress)
                this.transformOrigin = alignment.transformOrigin
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(cornerRadius) },
                effects = {
                    val progress = animatableProgress.value
                    if (isGlassEnabled && progress > 0.01f) {
                        vibrancy()
                        blur((blurRadius * progress).dp.toPx())
                        lens(
                            refractionHeight.dp.toPx() * progress, 
                            refractionAmount.dp.toPx() * progress, 
                            depthEffect = true, 
                            chromaticAberration = chromaticAberration
                        )
                    }
                },
                highlight = { null },
                shadow = { Shadow(radius = 18f.dp, color = Color.Black.copy(alpha = 0.12f)) },
                innerShadow = { null },
                onDrawSurface = {
                    val progress = animatableProgress.value
                    val cr = cornerRadius.toPx()
                    val startColor = if (isLightTheme) Color(0xFFFFFFFF) else Color(0xFF1C1C1E)
                    val endColor = if (isLightTheme) Color.White.copy(alpha = 0.6f) else Color(0xFF1E1E1E).copy(alpha = 0.6f)
                    
                    // Border stroke for sharp edges
                    drawRoundRect(
                        color = Color.Gray.copy(alpha = 0.3f),
                        cornerRadius = CornerRadius(cr, cr),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(0.5.dp.toPx())
                    )

                    drawRoundRect(
                        color = androidx.compose.ui.graphics.lerp(startColor, endColor, progress),
                        cornerRadius = CornerRadius(cr, cr)
                    )
                    if (progress == 0f && buttonPressProgress > 0f) {
                        drawRoundRect(color = Color.White.copy(alpha = 0.15f * buttonPressProgress), cornerRadius = CornerRadius(cr, cr), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
                    }
                }
            )
            .clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = alignment.composeAlignment
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize(unbounded = true, align = alignment.composeAlignment)
                .graphicsLayer {
                    val progress = animatableProgress.value
                    val rawContentProgress = if (progress > 1f) 1f + (progress - 1f) * contentBulgeMultiplier else (progress - 0.35f) / 0.65f
                    val minAspectScale = if (contentSize.width > 0f && contentSize.height > 0f) { min(labelSize.width / contentSize.width, labelSize.height / contentSize.height) } else 1f
                    val baseScale = minAspectScale + (1f - minAspectScale) * rawContentProgress

                    // iOS Squish stretching from the absolute center
                    val pop = sin(progress.coerceIn(0f, 1f) * PI).toFloat()

                    alpha = rawContentProgress.coerceIn(0f, 1f)
                    scaleX = baseScale + pop * contentPopX
                    scaleY = baseScale + pop * contentPopY
                    this.transformOrigin = TransformOrigin.Center
                }
        ) {
            content()
        }

        Box(
            modifier = Modifier.graphicsLayer {
                val progress = animatableProgress.value
                val labelOpacity = (progress / 0.35f).coerceIn(0f, 1f)
                alpha = 1f - labelOpacity
            }
        ) {
            label()
        }
    }
}
