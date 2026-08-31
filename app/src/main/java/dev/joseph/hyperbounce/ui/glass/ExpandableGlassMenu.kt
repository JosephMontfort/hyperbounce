package dev.joseph.hyperbounce.ui.glass

/*
 * Faithful port of com.kyant.backdrop.catalog.components.ExpandableGlassMenu from the
 * AndroidLiquidGlass library with advanced physics tuning and seamless 2D shape morphing
 * confirmation dialog support.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
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

data class MenuDialogData(
    val title: String,
    val message: String,
    val confirmText: String = "Confirm",
    val cancelText: String = "Cancel",
    val confirmColor: Color? = null,
    val onConfirm: () -> Unit
)

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
    animationSpeedMultiplier: Float = 1f,
    containerBulgeMultiplier: Float = 0.395f,
    contentBulgeMultiplier: Float = 0.15f,
    contentPopX: Float = 0.03f,
    contentPopY: Float = 0.01f,
    dragJellyTension: Float = 0.05f,
    motionBlurAmount: Float = 25f,
    arcYOffsetDp: Float = 75f,
    onDialogProgressChange: (Float) -> Unit = {},
    onRowSelected: (index: Int, showDialog: (MenuDialogData) -> Unit) -> Boolean = { _, _ -> true },
    label: @Composable () -> Unit,
    content: @Composable (
        globalTouchPosition: Offset,
        closeMenu: () -> Unit,
        hoveredIndex: Int?,
        setHovered: (Int?) -> Unit,
        showDialog: (MenuDialogData) -> Unit
    ) -> Unit
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
    var parentSize by remember { mutableStateOf(Size.Zero) }

    var hoveredIndex by remember { mutableStateOf<Int?>(null) }
    val setHovered: (Int?) -> Unit = { hoveredIndex = it }

    var activeDialog by remember { mutableStateOf<MenuDialogData?>(null) }
    var lastDialog by remember { mutableStateOf<MenuDialogData?>(null) }
    var isConfirmedClose by remember { mutableStateOf(false) }

    val showDialog: (MenuDialogData) -> Unit = { data ->
        isConfirmedClose = false
        activeDialog = data
        lastDialog = data
    }
    val dismissDialog: () -> Unit = {
        isConfirmedClose = false
        activeDialog = null
    }

    val morphSettings = dev.joseph.hyperbounce.ui.glass.LocalMorphPhysics.current
    val morphSpeed = morphSettings.morphSpeedMultiplier
    val effectiveSpeed = animationSpeedMultiplier * morphSpeed

    val dialogProgress by animateFloatAsState(
        targetValue = if (activeDialog != null) 1f else 0f,
        animationSpec = spring(
            dampingRatio = morphSettings.springDamping,
            stiffness = morphSettings.springStiffness * (effectiveSpeed * effectiveSpeed),
            visibilityThreshold = 0.0001f
        ),
        label = "menuDialogMorphProgress",
        finishedListener = { p ->
            if (p == 0f && activeDialog == null) {
                lastDialog = null
                isConfirmedClose = false
            }
        }
    )

    LaunchedEffect(dialogProgress) {
        onDialogProgressChange(dialogProgress)
    }

    val labelSizePx = with(density) { Size(labelSize.width.dp.toPx(), labelSize.height.dp.toPx()) }

    LaunchedEffect(hoveredIndex) {
        if (isHapticsEnabled && isDragging && hoveredIndex != null) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val closeMenu: () -> Unit = {
        hoveredIndex = null
        activeDialog = null
        isConfirmedClose = false
        animationScope.launch { animatableProgress.animateTo(0f, animationPreset.getSpec(isClosing = true, speedMultiplier = animationSpeedMultiplier)) }
    }

    val handleConfirmAndClose: (onConfirmCallback: () -> Unit) -> Unit = { onConfirmCallback ->
        isConfirmedClose = true
        activeDialog = null
        onConfirmCallback()
        animationScope.launch {
            animatableProgress.snapTo(0f)
        }
    }

    // Reset dialog when menu fully closes
    LaunchedEffect(animatableProgress.targetValue) {
        if (animatableProgress.targetValue == 0f && activeDialog != null) {
            activeDialog = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { if (it.width > 0 && it.height > 0) parentSize = it.toSize() },
        contentAlignment = alignment.composeAlignment
    ) {
        // Full-screen backdrop dimming scrim when confirmation dialog is active
        if (dialogProgress > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f * dialogProgress))
                    .pointerInput(Unit) {
                        detectTapGestures { dismissDialog() }
                    }
                    .zIndex(5f)
            )
        }

        GlassEffectContainer(
            animatableProgress = animatableProgress,
            dialogProgress = dialogProgress,
            activeDialog = activeDialog,
            lastDialog = lastDialog,
            isConfirmedClose = isConfirmedClose,
            onDismissDialog = dismissDialog,
            onConfirmAndClose = handleConfirmAndClose,
            closeMenu = closeMenu,
            parentSize = parentSize,
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
            modifier = Modifier.zIndex(10f),
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
                                    if (finalHoveredIndex != null) {
                                        val shouldClose = onRowSelected(finalHoveredIndex, showDialog)
                                        if (shouldClose) {
                                            closeMenu()
                                        }
                                    } else if (dragOffset.getDistance() > 20f) {
                                        closeMenu()
                                    }
                                }

                                dragOffset = Offset.Zero
                                globalTouchPosition = Offset.Unspecified
                                hoveredIndex = null
                            }
                        },
                    contentAlignment = Alignment.Center
                ) { label() }
            },
            content = {
                Box(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .onSizeChanged { if (it.width > 0 && it.height > 0) contentMeasuredSize = it.toSize() }
                        .padding(10f.dp)
                ) {
                    content(globalTouchPosition, closeMenu, hoveredIndex, setHovered, showDialog)
                }
            }
        )
    }
}

@Composable
fun GlassEffectContainer(
    animatableProgress: Animatable<Float, *>,
    dialogProgress: Float,
    activeDialog: MenuDialogData?,
    lastDialog: MenuDialogData?,
    isConfirmedClose: Boolean,
    onDismissDialog: () -> Unit,
    onConfirmAndClose: (onConfirmCallback: () -> Unit) -> Unit,
    closeMenu: () -> Unit,
    parentSize: Size,
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
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val isLightTheme = !isSystemInDarkTheme()

    val morphSettings = dev.joseph.hyperbounce.ui.glass.LocalMorphPhysics.current
    val circleCollapseRatio = morphSettings.circleCollapseRatio
    val circleDiameterPx = with(density) { morphSettings.circleDiameterDp.dp.toPx() }
    val zPlungeDepth = morphSettings.zPlungeDepth
    val gravitySwoopPx = with(density) { morphSettings.gravitySwoopDp.dp.toPx() }
    val yBounceAmountPx = with(density) { morphSettings.yBounceAmountDp.dp.toPx() }
    val jellyOvershootAmp = morphSettings.jellyOvershoot
    val contentStretchAmp = morphSettings.contentStretch
    val lensSurgePx = with(density) { morphSettings.lensSurgeDp.dp.toPx() }
    val motionBlurSurgePx = with(density) { morphSettings.motionBlurSurgeDp.dp.toPx() }

    val animatedDragX by animateFloatAsState(dragOffset.x, spring(stiffness = 400f, dampingRatio = 0.6f))
    val animatedDragY by animateFloatAsState(dragOffset.y, spring(stiffness = 400f, dampingRatio = 0.6f))

    // Smooth 2D content morph animation for sub-page transitions inside expanded menu (simultaneous XY resize)
    val morphSpring = spring<Float>(
        dampingRatio = 0.76f,
        stiffness = 420f,
        visibilityThreshold = 0.05f
    )
    val animatedContentWidth by animateFloatAsState(
        targetValue = if (contentSize.width > 0f) contentSize.width else labelSize.width,
        animationSpec = morphSpring,
        label = "menuContentWidth"
    )
    val animatedContentHeight by animateFloatAsState(
        targetValue = if (contentSize.height > 0f) contentSize.height else labelSize.height,
        animationSpec = morphSpring,
        label = "menuContentHeight"
    )

    // Cache the expanded menu dimensions so removing content() during morph does not cause size collapse
    var cachedMenuSize by remember { mutableStateOf(Size(220f, 160f)) }
    if (contentSize.width > 0f && contentSize.height > 0f) {
        cachedMenuSize = contentSize
    }

    val effectiveMenuW = if (contentSize.width > 0f) (if (animatableProgress.value > 0.5f) animatedContentWidth else contentSize.width) else cachedMenuSize.width
    val effectiveMenuH = if (contentSize.height > 0f) (if (animatableProgress.value > 0.5f) animatedContentHeight else contentSize.height) else cachedMenuSize.height

    // Press illumination progress
    val isPressing = isPressed && animatableProgress.targetValue == 0f
    val buttonPressProgress by animateFloatAsState(if (isPressing) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 400f))

    var dialogMeasuredHeightPx by remember { mutableStateOf(0f) }

    // Generous, luxurious dialog dimensions
    val targetDialogWidthPx = if (parentSize.width > 0f) {
        min(parentSize.width * 0.88f, with(density) { 345.dp.toPx() })
    } else with(density) { 330.dp.toPx() }

    val targetDialogHeightPx = if (dialogMeasuredHeightPx > 0f) {
        dialogMeasuredHeightPx
    } else with(density) { 265.dp.toPx() }

    // Dynamic corner radius: Menu Corner (22dp) -> Singularity Core (19dp) -> Dialog Corner (36dp)
    val targetRadiusPx = if (dialogProgress <= 0.001f) {
        with(density) { cornerRadius.toPx() }
    } else if (dialogProgress <= circleCollapseRatio) {
        val u = (dialogProgress / circleCollapseRatio).coerceIn(0f, 1f)
        val uSmooth = u * u * (3f - 2f * u)
        val startRadius = if (isConfirmedClose) with(density) { (labelSize.height / 2f) } else with(density) { cornerRadius.toPx() }
        lerp(startRadius, circleDiameterPx / 2f, uSmooth)
    } else {
        val v = ((dialogProgress - circleCollapseRatio) / (1f - circleCollapseRatio)).coerceIn(0f, 1f)
        val vSpit = 1f - (1f - v) * (1f - v) * (1f - v)
        lerp(circleDiameterPx / 2f, with(density) { 36.dp.toPx() }, vSpit)
    }
    val animatedRadius = with(density) { targetRadiusPx.toDp() }

    Box(
        modifier = modifier
            .layout { measurable, constraints ->
                val p = animatableProgress.value
                val dp = dialogProgress

                // 1. Normal Menu Expansion Physics (Circular 3-Dot Button at p=0 -> Expanded Rectangle Menu at p=1)
                val widthDiff = (effectiveMenuW - labelSize.width).coerceAtLeast(0f)
                val heightDiff = (effectiveMenuH - labelSize.height).coerceAtLeast(0f)

                val widthProgress = if (p > 1f) 1f + (p - 1f) * containerBulgeMultiplier else (p * p)
                val heightProgress = if (p > 1f) 1f + (p - 1f) * containerBulgeMultiplier else sin(p * (PI / 2f)).toFloat()

                val menuWidthPx = labelSize.width + widthDiff * widthProgress
                val menuHeightPx = labelSize.height + heightDiff * heightProgress

                // 2. Continuous Fluid Morphing: Menu Rectangle -> Dense Liquid Droplet -> Full Center Dialog
                val currentWidthPx: Float
                val currentHeightPx: Float

                if (dp <= 0.001f) {
                    currentWidthPx = menuWidthPx
                    currentHeightPx = menuHeightPx
                } else {
                    val baseMorphW: Float
                    val baseMorphH: Float
                    val startMorphW = if (isConfirmedClose) labelSize.width else effectiveMenuW
                    val startMorphH = if (isConfirmedClose) labelSize.height else effectiveMenuH

                    if (dp <= circleCollapseRatio) {
                        // Stage 1: Continuous fluid suction into dense singularity droplet
                        val u = (dp / circleCollapseRatio).coerceIn(0f, 1f)
                        val uSmooth = u * u * (3f - 2f * u)
                        baseMorphW = lerp(startMorphW, circleDiameterPx, uSmooth)
                        baseMorphH = lerp(startMorphH, circleDiameterPx, uSmooth)
                    } else {
                        // Stage 2: Explosive forward spit-out into full dialog
                        val v = ((dp - circleCollapseRatio) / (1f - circleCollapseRatio)).coerceIn(0f, 1f)
                        val vSpit = 1f - (1f - v) * (1f - v) * (1f - v)
                        baseMorphW = lerp(circleDiameterPx, targetDialogWidthPx, vSpit)
                        baseMorphH = lerp(circleDiameterPx, targetDialogHeightPx, vSpit)
                    }

                    // 4-Edge Isotropic Spit Punch & Rubbery Recoil (Punches outwards then settles)
                    val spitRecoil = if (dp >= circleCollapseRatio) {
                        val v = ((dp - circleCollapseRatio) / (1f - circleCollapseRatio)).coerceIn(0f, 1f)
                        val bulgeProgress = ((v - 0.20f) / 0.65f).coerceIn(0f, 1f)
                        val recoil = sin(bulgeProgress * PI).toFloat() * (jellyOvershootAmp * 2.0f)
                        val springOvershoot = if (dp > 1f) (dp - 1f) * 0.35f else 0f
                        recoil + springOvershoot
                    } else 0f

                    currentWidthPx = baseMorphW * (1f + spitRecoil)
                    currentHeightPx = baseMorphH * (1f + spitRecoil)
                }

                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth = currentWidthPx.toInt().coerceAtLeast(0),
                        maxWidth = currentWidthPx.toInt().coerceAtLeast(0),
                        minHeight = currentHeightPx.toInt().coerceAtLeast(0),
                        maxHeight = currentHeightPx.toInt().coerceAtLeast(0)
                    )
                )

                val layoutW = labelSize.width.toInt()
                val layoutH = labelSize.height.toInt()

                val offset = alignment.composeAlignment.align(
                    size = androidx.compose.ui.unit.IntSize(placeable.width, placeable.height),
                    space = androidx.compose.ui.unit.IntSize(layoutW, layoutH),
                    layoutDirection = layoutDirection
                )

                layout(layoutW, layoutH) {
                    placeable.placeWithLayer(offset.x, offset.y)
                }
            }
            .graphicsLayer {
                val progress = animatableProgress.value
                val dp = dialogProgress
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

                val expandedTx = animatedDragX * 0.03f
                val expandedTy = animatedDragY * 0.03f
                val expandedStretchX = (1f + abs(animatedDragX) * 0.00005f - abs(animatedDragY) * 0.00005f).coerceIn(0.99f, 1.01f)
                val expandedStretchY = (1f + abs(animatedDragY) * 0.00005f - abs(animatedDragX) * 0.00005f).coerceIn(0.99f, 1.01f)

                val menuTx = lerp(jellyTx, expandedTx, progress) + with(density) { horizontalOffset.dp.toPx() * progress }
                val menuTy = offsetY + lerp(jellyTy, expandedTy, progress) + with(density) { verticalOffset.dp.toPx() * progress }

                if (dp <= 0.001f) {
                    // Strict resting state positioning — zero displacement at top-right
                    translationX = menuTx
                    translationY = menuTy
                    scaleX = squishScale * lerp(jellySx, expandedStretchX, progress)
                    scaleY = squishScale * lerp(jellySy, expandedStretchY, progress)
                    this.transformOrigin = alignment.transformOrigin
                } else {
                    // Physical Center of Menu in Layout Space (locks to 3-dots center if returning on confirm)
                    val startCenterX = if (isConfirmedClose) (labelSize.width / 2f) else (labelSize.width - effectiveMenuW / 2f)
                    val startCenterY = if (isConfirmedClose) (labelSize.height / 2f) else (effectiveMenuH / 2f)

                    // True Center of Parent Screen in Layout Space
                    val screenCenterX = if (parentSize.width > 0f) -(parentSize.width / 2f - labelSize.width) else 0f
                    val screenCenterY = if (parentSize.height > 0f) (parentSize.height / 2f) - with(density) { verticalOffset.dp.toPx() } else 0f

                    // Instantaneous Center of current placeable
                    val currentLocalCenterX = labelSize.width - size.width / 2f
                    val currentLocalCenterY = size.height / 2f

                    // Strictly Monotonic Leftward & Downward Curved Trajectory
                    val flightProgress = dp.coerceIn(0f, 1f)
                    val smoothTravel = (1f - cos(flightProgress * PI).toFloat()) / 2f

                    // 3D Z-Axis Depth Plunge: Punches deep into screen Z-plane mid-flight; strictly 1.0 at rest
                    val zPlunge = sin(flightProgress * PI).toFloat()
                    val zDepthScale = 1f - (zPlunge * (zPlungeDepth * 1.25f))

                    // Gravitational Swoop Y + Landing Harmonic Vertical Bounce ONLY
                    val gravitySwoopY = zPlunge * (gravitySwoopPx * 1.4f)
                    val yBounce = if (dp > 1f) (dp - 1f) * (yBounceAmountPx * 1.2f) else 0f

                    val pathCenterX = lerp(startCenterX, screenCenterX, smoothTravel)
                    val pathCenterY = lerp(startCenterY, screenCenterY, smoothTravel) + gravitySwoopY + yBounce

                    translationX = (pathCenterX - currentLocalCenterX) + menuTx * (1f - dp)
                    translationY = (pathCenterY - currentLocalCenterY) + menuTy * (1f - dp)

                    scaleX = squishScale * zDepthScale
                    scaleY = squishScale * zDepthScale
                    this.transformOrigin = TransformOrigin.Center
                }
            }
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(animatedRadius) },
                effects = {
                    val progress = animatableProgress.value
                    val dp = dialogProgress
                    if (isGlassEnabled && (progress > 0.01f || dp > 0.01f)) {
                        val effProgress = max(progress, dp)
                        val suctionPulse = sin((dp / circleCollapseRatio).coerceIn(0f, 1f) * PI).toFloat()
                        val spitPulse = sin(((dp - circleCollapseRatio) / (1f - circleCollapseRatio)).coerceIn(0f, 1f) * PI).toFloat()

                        vibrancy()
                        // Split-second optical motion blur surge
                        val dynamicBlur = ((blurRadius * effProgress) + suctionPulse * (motionBlurSurgePx * 0.6f) + spitPulse * motionBlurSurgePx).dp.toPx()
                        val dynamicLens = ((refractionHeight * effProgress) + suctionPulse * (lensSurgePx * 0.5f) + spitPulse * lensSurgePx).dp.toPx()
                        val dynamicRefract = ((refractionAmount * effProgress) + suctionPulse * 3f + spitPulse * 6f).dp.toPx()

                        blur(dynamicBlur)
                        lens(
                            dynamicLens,
                            dynamicRefract,
                            depthEffect = true,
                            chromaticAberration = chromaticAberration
                        )
                    }
                },
                highlight = { null },
                shadow = {
                    val dp = dialogProgress
                    Shadow(
                        radius = (24f + 14f * dp).dp,
                        color = Color.Black.copy(alpha = lerp(0.25f, 0.45f, dp))
                    )
                },
                innerShadow = { null },
                onDrawSurface = {
                    val progress = animatableProgress.value
                    val dp = dialogProgress
                    val cr = targetRadiusPx
                    val startColor = if (isLightTheme) Color(0xFFFFFFFF) else Color(0xFF1C1C1E)
                    val endColor = if (isLightTheme) Color.White.copy(alpha = lerp(0.60f, 0.96f, dp))
                                   else Color(0xFF1C1C1E).copy(alpha = lerp(0.60f, 0.96f, dp))

                    val outlineColor = if (isLightTheme) Color.Black.copy(alpha = lerp(0.15f, 0.08f, dp))
                                       else Color.White.copy(alpha = lerp(0.25f, 0.18f, dp))
                    drawRoundRect(
                        color = outlineColor,
                        cornerRadius = CornerRadius(cr, cr),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                    )

                    drawRoundRect(
                        color = androidx.compose.ui.graphics.lerp(startColor, endColor, progress),
                        cornerRadius = CornerRadius(cr, cr)
                    )

                    if (progress == 0f && buttonPressProgress > 0f && dp == 0f) {
                        drawRoundRect(color = Color.White.copy(alpha = 0.15f * buttonPressProgress), cornerRadius = CornerRadius(cr, cr), blendMode = BlendMode.Plus)
                    }
                }
            )
            .clip(RoundedCornerShape(animatedRadius)),
        contentAlignment = if (dialogProgress > 0.001f) Alignment.Center else alignment.composeAlignment
    ) {
        // Menu Content Layer (smoothly and visibly compresses directly into the center of the shrinking container)
        if (dialogProgress < 0.50f) {
            Box(
                modifier = Modifier
                    .wrapContentSize(unbounded = true, align = if (dialogProgress > 0.001f) Alignment.Center else alignment.composeAlignment)
                    .graphicsLayer {
                        val progress = animatableProgress.value
                        val dp = dialogProgress
                        val rawContentProgress = if (progress > 1f) 1f + (progress - 1f) * contentBulgeMultiplier else (progress - 0.35f) / 0.65f
                        val minAspectScale = if (contentSize.width > 0f && contentSize.height > 0f) { min(labelSize.width / contentSize.width, labelSize.height / contentSize.height) } else 1f
                        val baseScale = minAspectScale + (1f - minAspectScale) * rawContentProgress

                        val pop = sin(progress.coerceIn(0f, 1f) * PI).toFloat()

                        val collapseProgress = (dp / 0.45f).coerceIn(0f, 1f)
                        val uSmooth = collapseProgress * collapseProgress * (3f - 2f * collapseProgress)
                        
                        // Continuous suction scale down to the core
                        val suctionScale = (1f - uSmooth * 0.75f).coerceIn(0.25f, 1.0f)

                        alpha = (rawContentProgress.coerceIn(0f, 1f) * (1f - uSmooth * uSmooth)).coerceIn(0f, 1f)
                        scaleX = (baseScale + pop * contentPopX) * suctionScale
                        scaleY = (baseScale + pop * contentPopY) * suctionScale
                        this.transformOrigin = TransformOrigin.Center
                    }
                    .zIndex(if (animatableProgress.value > 0.35f) 2f else 0f)
            ) {
                content()
            }
        }

        // Morph Confirmation Dialog Content Layer (explodes outward from singularity core with dramatic 28% content bulge & elastic recoil)
        if (activeDialog != null || dialogProgress > 0.001f) {
            val curDialog = activeDialog ?: lastDialog
            if (curDialog != null && dialogProgress >= 0.35f) {
                Box(
                    modifier = Modifier
                        .wrapContentSize(unbounded = true, align = Alignment.Center)
                        .width(with(density) { targetDialogWidthPx.toDp() })
                        .onSizeChanged { size ->
                            if (size.height > 0) {
                                dialogMeasuredHeightPx = size.height.toFloat()
                            }
                        }
                        .graphicsLayer {
                            val appearProgress = ((dialogProgress - 0.35f) / 0.65f).coerceIn(0f, 1f)
                            val vBurst = 1f - (1f - appearProgress) * (1f - appearProgress) * (1f - appearProgress)

                            // Explosive Outward Content Bulge: shoots outward to 1.28x at peak spit force, then snaps back to 1.0x
                            val bulgePhase = ((appearProgress - 0.15f) / 0.70f).coerceIn(0f, 1f)
                            val bulgeOvershoot = sin(bulgePhase * PI).toFloat() * 0.28f
                            val springBounce = if (dialogProgress > 1f) (dialogProgress - 1f) * 0.35f else 0f

                            val coreScale = (0.20f + 0.80f * vBurst) + bulgeOvershoot + springBounce

                            alpha = (appearProgress / 0.25f).coerceIn(0f, 1f)
                            scaleX = coreScale
                            scaleY = coreScale
                            this.transformOrigin = TransformOrigin.Center
                        }
                        .zIndex(3f),
                    contentAlignment = Alignment.Center
                ) {
                    MenuDialogContent(
                        dialog = curDialog,
                        isDark = !isLightTheme,
                        onCancel = onDismissDialog,
                        onConfirmAndClose = {
                            onConfirmAndClose {
                                curDialog.onConfirm()
                            }
                        }
                    )
                }
            }
        }

        // 3-Dots Label Layer
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val progress = animatableProgress.value
                    val labelOpacity = (progress / 0.35f).coerceIn(0f, 1f)
                    alpha = 1f - labelOpacity
                }
                .zIndex(if (animatableProgress.value <= 0.35f && dialogProgress == 0f) 2f else 0f)
        ) {
            label()
        }
    }
}

@Composable
private fun MenuDialogContent(
    dialog: MenuDialogData,
    isDark: Boolean,
    onCancel: () -> Unit,
    onConfirmAndClose: () -> Unit
) {
    val titleColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFF000000)
    val subtitleColor = if (isDark) Color(0xFF98989D) else Color(0xFF6E6E73)
    val cancelBtnBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
    val defaultConfirmBg = if (isDark) Color(0xFF0A84FF) else Color(0xFF007AFF)
    val confirmBtnBg = dialog.confirmColor ?: defaultConfirmBg

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        BasicText(
            text = dialog.title,
            style = HyperBounceType.headline.copy(
                color = titleColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        )

        BasicText(
            text = dialog.message,
            style = HyperBounceType.footnote.copy(
                color = subtitleColor,
                fontSize = 14.5.sp,
                lineHeight = 21.sp
            )
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cancel capsule button (Spacious 50dp touch target)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(CircleShape)
                    .background(cancelBtnBg)
                    .clickable { onCancel() },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = dialog.cancelText,
                    style = TextStyle(
                        color = titleColor,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }

            // Confirm capsule button (Spacious 50dp touch target)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(CircleShape)
                    .background(confirmBtnBg)
                    .clickable {
                        onConfirmAndClose()
                    },
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    text = dialog.confirmText,
                    style = TextStyle(
                        color = Color.White,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }
    }
}



