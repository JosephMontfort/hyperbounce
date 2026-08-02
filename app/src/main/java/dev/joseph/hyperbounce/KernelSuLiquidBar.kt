package dev.joseph.hyperbounce

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import androidx.compose.animation.core.*
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.blur.highlight.*
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

// ============================================================================
// 1. FLOATING BOTTOM BAR — 1:1 port of KernelSU FloatingBottomBar.kt
// ============================================================================

val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

private val iosIndicatorSpecular = Highlight(
    width = 1.dp,
    alpha = 1f,
    style = BloomStroke(
        color = Color.White.copy(alpha = 0.12f),
        innerBlurRadius = 2.0.dp,
        primaryLight = LightSource(LightPosition(0.5f, -0.3f, -0.05f), Color.White, 1f),
        secondaryLight = LightSource(LightPosition(0.5f, 0.8f, -0.5f), Color.White, 0.4f),
        dualPeak = true,
    ),
)

private const val LIGHT_REF_X = 0.5f
private const val LIGHT_REF_Y = 0.7f

@Composable
private fun rememberGravityRotatedHighlight(base: Highlight, extraDegrees: Float = 0f): Highlight {
    val baseStyle = base.style as BloomStroke
    // No sensor tilt available in module context — use fixed "up" gravity direction.
    val rotatedPrimary = remember(baseStyle.primaryLight, extraDegrees) {
        val basePrimary = baseStyle.primaryLight
        val rad = extraDegrees * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        // Fixed gravity direction: (0, -1) = screen upright
        val lx = c * 0f - s * -1f
        val ly = s * 0f + c * -1f
        basePrimary.copy(
            position = LightPosition(
                x = LIGHT_REF_X + lx,
                y = LIGHT_REF_Y + ly,
                z = basePrimary.position.z,
            )
        )
    }
    return remember(base, rotatedPrimary) {
        base.copy(style = baseStyle.copy(primaryLight = rotatedPrimary))
    }
}

@Composable
fun RowScope.FloatingBottomBarItem(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scale = LocalFloatingBottomBarTabScale.current
    Column(
        modifier
            .clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun FloatingBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: () -> Int,
    onSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    isBlurEnabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val isInDark = isSystemInDarkTheme()
    val pillShape = remember { CircleShape }
    val accentColor = MiuixTheme.colorScheme.primary
    val tabContentColor = MiuixTheme.colorScheme.onSurface
    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    val containerColor = androidx.compose.ui.graphics.Color.Transparent

    // ── tabsBackdrop: captures the invisible accent-coloured row so the
    //    indicator pill can composite the tinted icons on top of the blur. ──
    val tabsBackdrop = rememberLayerBackdrop()

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) 0f
            else {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember(selectedIndex) { mutableIntStateOf(selectedIndex()) }

    class DampedDragAnimationHolder { var instance: DampedDragAnimation? = null }
    val holder = remember { DampedDragAnimationHolder() }

    val dampedDragAnimation = remember(animationScope, tabsCount, density, isLtr) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndex().toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 60f / 56f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false
                val indicatorX = anim.value * tabWidthPx
                val padding = with(density) { 4.dp.toPx() }
                val globalTouchX =
                    if (isLtr) padding + indicatorX + offset.x
                    else totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                currentIndex = targetIndex
                animateToValue(targetIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        ).also { holder.instance = it }
    }

    LaunchedEffect(selectedIndex) {
        snapshotFlow { selectedIndex() }.collectLatest { currentIndex = it }
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
            dampedDragAnimation.animateToValue(index.toFloat())
            onSelected(index)
        }
    }

    val interactiveHighlight = remember(animationScope, tabWidthPx) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { size, _ ->
                Offset(
                    if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                    else size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset,
                    size.height / 2f,
                )
            },
        )
    }

    val baseHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = -45f)
    val pillHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = 90f)

    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)

    Box(
        modifier = modifier.width(IntrinsicSize.Min),
        contentAlignment = Alignment.CenterStart,
    ) {
        // ── Main visible row ──────────────────────────────────────────────
        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    tabWidthPx = ((totalWidthPx - with(density) { 8.dp.toPx() }) / tabsCount)
                        .coerceAtLeast(0f)
                }
                .graphicsLayer { translationX = panelOffset }
                .shadow(elevation = 10.dp, shape = pillShape, ambientColor = Color.Black.copy(if (isInDark) 0.2f else 0.1f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .then(
                    if (isBlurEnabled) {
                        Modifier.drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy()
                                blur(28.dp.toPx(), 28.dp.toPx())
                                lens(
                                    refractionHeight = 42.dp.toPx(),
                                    refractionAmount = 36.dp.toPx(),
                                )
                            },
                            highlight = { baseHighlight.copy(alpha = 0.75f) },
                            layerBlock = {
                                val w = size.width.coerceAtLeast(1f)
                                val s = lerp(1f, 1f + 16.dp.toPx() / w, dampedDragAnimation.pressProgress)
                                scaleX = s
                                scaleY = s
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                    } else {
                        Modifier.background(containerColor, pillShape)
                    }
                )
                .then(if (isBlurEnabled) interactiveHighlight.modifier else Modifier)
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompositionLocalProvider(LocalContentColor provides tabContentColor) {
                content()
            }
        }

        // ── Invisible accent-coloured clone — feeds tabsBackdrop ─────────
        if (isBlurEnabled) {
            CompositionLocalProvider(
                LocalFloatingBottomBarTabScale provides {
                    lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                },
                LocalContentColor provides accentColor,
            ) {
                Row(
                    Modifier
                        .clearAndSetSemantics {}
                        .alpha(0f)
                        .layerBackdrop(tabsBackdrop)         // ← fills tabsBackdrop
                        .graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { pillShape },
                            effects = {
                                vibrancy()
                                blur(28.dp.toPx(), 28.dp.toPx())
                                lens(
                                    refractionHeight = 42.dp.toPx(),
                                    refractionAmount = 36.dp.toPx(),
                                )
                            },
                            onDrawSurface = { drawRect(containerColor) },
                        )
                        .then(interactiveHighlight.modifier)
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content,
                )
            }
        }

        // ── Animated indicator pill ───────────────────────────────────────
        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }

            if (isBlurEnabled) {
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDragAnimation.value * tabWidthPx
                            translationX =
                                if (isLtr) progressOffset + panelOffset
                                else -progressOffset + panelOffset
                        }
                        .then(interactiveHighlight.gestureModifier)
                        .then(dampedDragAnimation.modifier)
                        .drawBackdrop(
                            backdrop = combinedBackdrop,       // ← combined: screen + accent icons
                            shape = { pillShape },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                lens(
                                    refractionHeight = 10.dp.toPx() * progress,
                                    refractionAmount = 14.dp.toPx() * progress,
                                    depthEffect = true,
                                    chromaticAberration = 0.5f,
                                )
                            },
                            highlight = {
                                pillHighlight.copy(alpha = dampedDragAnimation.pressProgress)
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
                                drawRect(
                                    color = if (!isInDark) Color.Black.copy(alpha = 0.1f)
                                            else Color.White.copy(alpha = 0.1f),
                                    alpha = 1f - progress,
                                )
                                drawRect(Color.Black.copy(alpha = 0.03f * progress))
                            },
                        )
                        .innerShadow(shape = pillShape) {
                            InnerShadow(
                                radius = 8.dp * dampedDragAnimation.pressProgress,
                                color = Color.Black.copy(alpha = 0.15f),
                                alpha = dampedDragAnimation.pressProgress,
                            )
                        }
                        .height(56.dp)
                        .width(tabWidthDp),
                )
            } else {
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            val progressOffset = dampedDragAnimation.value * tabWidthPx
                            translationX =
                                if (isLtr) progressOffset + panelOffset
                                else -progressOffset + panelOffset
                        }
                        .then(dampedDragAnimation.modifier)
                        .clip(pillShape)
                        .background(accentColor.copy(alpha = 0.15f), pillShape)
                        .height(56.dp)
                        .width(tabWidthDp),
                )
            }
        }
    }
}

// ============================================================================
// 2. PHYSICS ENGINE — DampedDragAnimation + InteractiveHighlight
// ============================================================================

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val canDrag: (Offset) -> Boolean = { true },
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimationSpec    = spring(1f,   1000f, visibilityThreshold)
    private val velocityAnimationSpec = spring(0.5f,  300f, visibilityThreshold * 10f)
    private val pressAnimationSpec    = spring(1f,   1000f, 0.001f)
    private val scaleXAnimationSpec   = spring(0.6f,  250f, 0.001f)
    private val scaleYAnimationSpec   = spring(0.7f,  250f, 0.001f)

    private val valueAnimation         = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation      = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation        = Animatable(initialScale, 0.001f)
    private val scaleYAnimation        = Animatable(initialScale, 0.001f)

    private val mutatorMutex   = MutatorMutex()
    private val velocityTracker = VelocityTracker()

    val value: Float         get() = valueAnimation.value
    val targetValue: Float   get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float        get() = scaleXAnimation.value
    val scaleY: Float        get() = scaleYAnimation.value
    val velocity: Float      get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { offset -> onDragStarted(offset); press() },
            onDragEnd   = { onDragStopped(); release() },
            onDragCancel = { onDragStopped(); release() },
            onDrag = { change, dragAmount ->
                if (canDrag(change.position) && canDrag(change.previousPosition)) {
                    change.consume()
                    onDrag(size, dragAmount)
                }
            },
        )
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
    }

    fun release() {
        animationScope.launch {
            awaitFrame()   // matches KernelSU — wait one frame before collapsing scale
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }
                    .first { abs(it - valueAnimation.targetValue) < threshold }
            }
            launch { pressProgressAnimation.animateTo(0f, pressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val target = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(target, valueAnimationSpec) { updateVelocity() } }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val target = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(target, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(System.currentTimeMillis(), Offset(value, 0f))
        val v = velocityTracker.calculateVelocity().x /
                (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(v, velocityAnimationSpec) }
    }
}

@SuppressLint("NewApi")
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset,
) {
    private val pressAnimSpec    = spring(0.5f, 300f, 0.001f)
    private val positionAnimSpec = spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    private val shader = RuntimeShader(
        """
        uniform float2 size;
        layout(color) uniform half4 color;
        uniform float radius;
        uniform float2 position;
        half4 main(float2 coord) {
            float dist = distance(coord, position);
            float intensity = smoothstep(radius, radius * 0.5, dist);
            return color * intensity;
        }
        """
    )

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            drawRect(Color.White.copy(0.06f * progress), blendMode = BlendMode.Plus)
            shader.apply {
                val pos = position(size, positionAnimation.value)
                setFloatUniform("size", size.width, size.height)
                setColorUniform("color", Color.White.copy(0.12f * progress).toArgb())
                setFloatUniform("radius", size.minDimension * 1.2f)
                setFloatUniform(
                    "position",
                    pos.x.fastCoerceIn(0f, size.width),
                    pos.y.fastCoerceIn(0f, size.height),
                )
            }
            drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus)
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        detectDragGestures(
            onDragStart = { offset ->
                startPosition = offset
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(1f, pressAnimSpec) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressAnimSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimSpec) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, pressAnimSpec) }
                    launch { positionAnimation.animateTo(startPosition, positionAnimSpec) }
                }
            },
            onDrag = { change, _ ->
                change.consume()
                animationScope.launch { positionAnimation.snapTo(change.position) }
            },
        )
    }
}

// ============================================================================
// 3. LIQUID GLASS EFFECTS — lens shader, vibrancy, inner shadow, combined backdrop
// ============================================================================

class CombinedBackdrop(val first: Backdrop, val second: Backdrop) : Backdrop {
    override val isCoordinatesDependent = first.isCoordinatesDependent || second.isCoordinatesDependent
    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        with(first)  { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(second) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

@Composable
fun rememberCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop =
    remember(first, second) { CombinedBackdrop(first, second) }

data class InnerShadow(
    val radius: Dp = 24.dp,
    val offset: DpOffset = DpOffset(0.dp, radius),
    val color: Color = Color.Black.copy(alpha = 0.15f),
    val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode,
)

fun Modifier.innerShadow(shape: Shape, shadow: () -> InnerShadow?): Modifier =
    this then InnerShadowElement(shape, shadow)

private class InnerShadowElement(
    val shape: Shape,
    val shadow: () -> InnerShadow?,
) : ModifierNodeElement<InnerShadowNode>() {
    override fun create() = InnerShadowNode(shape, shadow)
    override fun update(node: InnerShadowNode) {
        node.shape = shape
        node.shadow = shadow
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "innerShadow"
        properties["shape"] = shape
        properties["shadow"] = shadow
    }
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InnerShadowElement) return false
        return shape == other.shape && shadow == other.shadow
    }
    override fun hashCode() = 31 * shape.hashCode() + shadow.hashCode()
}

private class InnerShadowNode(
    var shape: Shape,
    var shadow: () -> InnerShadow?,
) : Modifier.Node(), DrawModifierNode {
    override val shouldAutoInvalidate = false

    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()
    private val clipPath = Path()
    private var prevRadius = Float.NaN

    override fun ContentDrawScope.draw() {
        drawContent()
        val s = shadow() ?: return
        val layer = shadowLayer ?: return

        val radius = s.radius.toPx()
        val offsetX = s.offset.x.toPx()
        val offsetY = s.offset.y.toPx()

        val outline = shape.createOutline(size, layoutDirection, this)
        clipPath.reset()
        when (outline) {
            is Outline.Rectangle -> clipPath.addRect(outline.rect)
            is Outline.Rounded   -> clipPath.addRoundRect(outline.roundRect)
            is Outline.Generic   -> clipPath.addPath(outline.path)
        }

        paint.color = s.color
        layer.alpha     = s.alpha
        layer.blendMode = s.blendMode
        if (prevRadius != radius) {
            layer.renderEffect =
                if (radius > 0f) BlurEffect(radius, radius, TileMode.Decal) else null
            prevRadius = radius
        }

        layer.record {
            drawContext.canvas.let { canvas ->
                canvas.save()
                canvas.clipPath(clipPath)
                canvas.drawOutline(outline, paint)
                canvas.translate(offsetX, offsetY)
                canvas.drawOutline(outline, ShadowMaskPaint)
                canvas.restore()
            }
        }
        drawContext.canvas.let { canvas ->
            canvas.save()
            canvas.clipPath(clipPath)
            drawLayer(layer)
            canvas.restore()
        }
    }

    override fun onAttach() {
        shadowLayer = requireGraphicsContext().createGraphicsLayer().apply {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    }

    override fun onDetach() {
        shadowLayer?.let {
            requireGraphicsContext().releaseGraphicsLayer(it)
            shadowLayer = null
        }
    }
}

private val ShadowMaskPaint = Paint().apply { blendMode = BlendMode.Clear }

fun BackdropEffectScope.vibrancy() {
    colorControls(brightness = 0f, contrast = 1f, saturation = 1.5f)
}

fun BackdropEffectScope.lens(
    refractionHeight: Float,
    refractionAmount: Float,
    depthEffect: Boolean = false,
    chromaticAberration: Float = 0f,
) {
    if (!isRuntimeShaderSupported() || refractionHeight <= 0f || refractionAmount <= 0f) return
    if (padding < refractionAmount) padding = refractionAmount

    val radii = roundedRectCornerRadii() ?: return
    val dispersionEnabled = chromaticAberration > 0f
    val shaderString = if (dispersionEnabled)
        ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER
    else
        ROUNDED_RECT_REFRACTION_SHADER
    val key = if (dispersionEnabled) "LiquidGlassLensDispersion" else "LiquidGlassLens"

    val sf = downscaleFactor.coerceAtLeast(1).toFloat()
    runtimeShaderEffect(
        key = key,
        shaderString = shaderString,
        uniformShaderName = "content",
    ) {
        setFloatUniform("size", size.width / sf, size.height / sf)
        setFloatUniform("offset", -padding / sf, -padding / sf)
        setFloatUniform("cornerRadii", FloatArray(radii.size) { radii[it] / sf })
        setFloatUniform("refractionHeight", refractionHeight / sf)
        setFloatUniform("refractionAmount", -refractionAmount / sf)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (dispersionEnabled) setFloatUniform("chromaticAberration", chromaticAberration)
    }
}

private fun BackdropEffectScope.roundedRectCornerRadii(): FloatArray? {
    val cornerShape = shape as? CornerBasedShape ?: return null
    val maxRadius = size.minDimension / 2f
    val isLtr = layoutDirection == LayoutDirection.Ltr
    return floatArrayOf(
        (if (isLtr) cornerShape.topStart.toPx(size, this) else cornerShape.topEnd.toPx(size, this)).coerceAtMost(maxRadius),
        (if (isLtr) cornerShape.topEnd.toPx(size, this) else cornerShape.topStart.toPx(size, this)).coerceAtMost(maxRadius),
        (if (isLtr) cornerShape.bottomEnd.toPx(size, this) else cornerShape.bottomStart.toPx(size, this)).coerceAtMost(maxRadius),
        (if (isLtr) cornerShape.bottomStart.toPx(size, this) else cornerShape.bottomEnd.toPx(size, this)).coerceAtMost(maxRadius),
    )
}

// ── AGSL Shaders ─────────────────────────────────────────────────────────────

private const val ROUNDED_RECT_SDF = """
float radiusAt(float2 coord, float4 radii) {
    if (coord.x >= 0.0) { if (coord.y <= 0.0) return radii.y; else return radii.z; }
    else                 { if (coord.y <= 0.0) return radii.x; else return radii.w; }
}
float sdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 c = abs(coord) - (halfSize - float2(radius));
    return length(max(c, 0.0)) - radius + min(max(c.x, c.y), 0.0);
}
float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {
    float2 c = abs(coord) - (halfSize - float2(radius));
    if (c.x >= 0.0 || c.y >= 0.0) return sign(coord) * normalize(max(c, 0.0));
    float gx = step(c.y, c.x);
    return sign(coord) * float2(gx, 1.0 - gx);
}
"""

private const val ROUNDED_RECT_REFRACTION_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
$ROUNDED_RECT_SDF
float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 cc = (coord + offset) - halfSize;
    float radius = radiusAt(cc, cornerRadii);
    float sd = sdRoundedRect(cc, halfSize, radius);
    if (-sd >= refractionHeight) return content.eval(coord);
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(cc, halfSize, gradRadius) + depthEffect * normalize(cc));
    return content.eval(coord + d * grad);
}
"""

private const val ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER = """
uniform shader content;
uniform float2 size;
uniform float2 offset;
uniform float4 cornerRadii;
uniform float refractionHeight;
uniform float refractionAmount;
uniform float depthEffect;
uniform float chromaticAberration;
$ROUNDED_RECT_SDF
float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }
half4 main(float2 coord) {
    float2 halfSize = size * 0.5;
    float2 cc = (coord + offset) - halfSize;
    float radius = radiusAt(cc, cornerRadii);
    float sd = sdRoundedRect(cc, halfSize, radius);
    if (-sd >= refractionHeight) return content.eval(coord);
    sd = min(sd, 0.0);
    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;
    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));
    float2 grad = normalize(gradSdRoundedRect(cc, halfSize, gradRadius) + depthEffect * normalize(cc));
    float2 rc = coord + d * grad;
    float di = chromaticAberration * ((cc.x * cc.y) / (halfSize.x * halfSize.y));
    float2 dc = d * grad * di;
    half4 color = half4(0.0);
    half4 red    = content.eval(rc + dc);           color.r += red.r / 3.5;  color.a += red.a / 7.0;
    half4 orange = content.eval(rc + dc * (2.0/3.0)); color.r += orange.r / 3.5; color.g += orange.g / 7.0; color.a += orange.a / 7.0;
    half4 yellow = content.eval(rc + dc * (1.0/3.0)); color.r += yellow.r / 3.5; color.g += yellow.g / 3.5; color.a += yellow.a / 7.0;
    half4 green  = content.eval(rc);                color.g += green.g / 3.5; color.a += green.a / 7.0;
    half4 cyan   = content.eval(rc - dc * (1.0/3.0)); color.g += cyan.g / 3.5;  color.b += cyan.b / 3.0;  color.a += cyan.a / 7.0;
    half4 blue   = content.eval(rc - dc * (2.0/3.0)); color.b += blue.b / 3.0;  color.a += blue.a / 7.0;
    half4 purple = content.eval(rc - dc);           color.r += purple.r / 7.0; color.b += purple.b / 3.0; color.a += purple.a / 7.0;
    return color;
}
"""
