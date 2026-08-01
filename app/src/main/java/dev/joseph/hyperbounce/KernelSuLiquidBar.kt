package dev.joseph.hyperbounce

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
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
import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import top.yukonga.miuix.kmp.blur.*
import top.yukonga.miuix.kmp.blur.highlight.*
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.isSystemInDarkTheme

// ============================================================================
// 1. KERNELSU FLOATING BOTTOM BAR
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

@Composable
private fun rememberGravityRotatedHighlight(base: Highlight, extraDegrees: Float = 0f): Highlight {
    val baseStyle = base.style as BloomStroke
    val rotatedPrimary = remember(baseStyle.primaryLight, extraDegrees) {
        val basePrimary = baseStyle.primaryLight
        val rad = extraDegrees * PI / 180.0
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        val lx = c * 0f - s * -1f
        val ly = s * 0f + c * -1f
        basePrimary.copy(position = LightPosition(0.5f + lx, 0.7f + ly, basePrimary.position.z))
    }
    return remember(base, rotatedPrimary) { base.copy(style = baseStyle.copy(primaryLight = rotatedPrimary)) }
}

@Composable
fun RowScope.FloatingBottomBarItem(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scale = LocalFloatingBottomBarTabScale.current
    Column(
        modifier.clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .fillMaxHeight().weight(1f).graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
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
    content: @Composable RowScope.() -> Unit
) {
    val isInDark = isSystemInDarkTheme()
    val pillShape = remember { CircleShape }
    val accentColor = MiuixTheme.colorScheme.primary
    val tabContentColor = MiuixTheme.colorScheme.onSurface
    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    val containerColor = if (isBlurEnabled) surfaceContainer.copy(0.4f) else surfaceContainer

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) 0f else {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
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
            pressedScale = 78f / 56f,
            canDrag = { offset ->
                val anim = holder.instance ?: return@DampedDragAnimation true
                if (tabWidthPx == 0f) return@DampedDragAnimation false
                val indicatorX = anim.value * tabWidthPx
                val padding = with(density) { 4.dp.toPx() }
                val globalTouchX = if (isLtr) padding + indicatorX + offset.x else totalWidthPx - padding - tabWidthPx - indicatorX + offset.x
                globalTouchX in 0f..totalWidthPx
            },
            onDragStarted = {},
            onDragStopped = {
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                currentIndex = targetIndex
                animateToValue(targetIndex.toFloat())
                animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
            },
            onDrag = { _, dragAmount ->
                if (tabWidthPx > 0) {
                    updateValue((targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f).coerceIn(0f, (tabsCount - 1).toFloat()))
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                }
            }
        ).also { holder.instance = it }
    }

    LaunchedEffect(selectedIndex) { snapshotFlow { selectedIndex() }.collectLatest { currentIndex = it } }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
            dampedDragAnimation.animateToValue(index.toFloat())
            onSelected(index)
        }
    }

    val interactiveHighlight = remember(animationScope, tabWidthPx) {
        InteractiveHighlight(animationScope = animationScope, position = { size, _ ->
            Offset(if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset else size.width - (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset, size.height / 2f)
        })
    }

    val baseHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = -45f)
    val pillHighlight = rememberGravityRotatedHighlight(iosIndicatorSpecular, extraDegrees = 90f)

    Box(modifier = modifier, contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier.fillMaxWidth().onGloballyPositioned { coords ->
                totalWidthPx = coords.size.width.toFloat()
                tabWidthPx = ((totalWidthPx - with(density) { 8.dp.toPx() }) / tabsCount).coerceAtLeast(0f)
            }.graphicsLayer { translationX = panelOffset }.shadow(elevation = 10.dp, shape = pillShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .then(
                if (isBlurEnabled) Modifier.drawBackdrop(
                    backdrop = backdrop, shape = { pillShape },
                    effects = { vibrancy(); blur(4.dp.toPx(), 4.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx()) },
                    highlight = { baseHighlight.copy(alpha = 0.75f) },
                    layerBlock = {
                        val width = size.width.coerceAtLeast(1f)
                        val s = androidx.compose.ui.util.lerp(1f, 1f + 16.dp.toPx() / width, dampedDragAnimation.pressProgress)
                        scaleX = s; scaleY = s
                    },
                    onDrawSurface = { drawRect(containerColor) }
                ) else Modifier.background(containerColor, pillShape)
            ).then(if (isBlurEnabled) interactiveHighlight.modifier else Modifier).height(64.dp).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { CompositionLocalProvider(LocalContentColor provides tabContentColor) { content() } }

        if (isBlurEnabled) {
            CompositionLocalProvider(LocalFloatingBottomBarTabScale provides { androidx.compose.ui.util.lerp(1f, 1.2f, dampedDragAnimation.pressProgress) }, LocalContentColor provides accentColor) {
                Row(
                    Modifier.fillMaxWidth().clearAndSetSemantics {}.alpha(0f).graphicsLayer { translationX = panelOffset }
                        .drawBackdrop(
                            backdrop = backdrop, shape = { pillShape },
                            effects = { vibrancy(); blur(4.dp.toPx(), 4.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx()) },
                            onDrawSurface = { drawRect(containerColor) }
                        ).then(interactiveHighlight.modifier).height(56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically, content = content
                )
            }
        }

        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            if (isBlurEnabled) {
                Box(
                    Modifier.padding(horizontal = 4.dp).graphicsLayer {
                        val progressOffset = dampedDragAnimation.value * tabWidthPx
                        translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                    }.then(interactiveHighlight.gestureModifier).then(dampedDragAnimation.modifier)
                    .drawBackdrop(
                        backdrop = backdrop, shape = { pillShape },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, depthEffect = true, chromaticAberration = 0.5f)
                        },
                        highlight = { pillHighlight.copy(alpha = dampedDragAnimation.pressProgress) },
                        layerBlock = {
                            scaleX = dampedDragAnimation.scaleX; scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                        },
                        onDrawSurface = {
                            val progress = dampedDragAnimation.pressProgress
                            drawRect(color = if (!isInDark) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f), alpha = 1f - progress)
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        }
                    ).innerShadow(shape = pillShape) { InnerShadow(radius = 8.dp * dampedDragAnimation.pressProgress, color = Color.Black.copy(alpha = 0.15f), alpha = dampedDragAnimation.pressProgress) }
                    .height(56.dp).width(tabWidthDp)
                )
            } else {
                Box(
                    Modifier.padding(horizontal = 4.dp).graphicsLayer {
                        val progressOffset = dampedDragAnimation.value * tabWidthPx
                        translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                    }.then(dampedDragAnimation.modifier).clip(pillShape).background(accentColor.copy(alpha = 0.15f), pillShape).height(56.dp).width(tabWidthDp)
                )
            }
        }
    }
}

// ============================================================================
// 2. KERNELSU PHYSICS COMPONENTS
// ============================================================================

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float, val valueRange: ClosedRange<Float>, val visibilityThreshold: Float,
    val initialScale: Float, val pressedScale: Float,
    val canDrag: (Offset) -> Boolean = { true },
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)
    private val velocityTracker = VelocityTracker()

    val value: Float get() = valueAnimation.value
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = { offset -> onDragStarted(offset); press() },
            onDragEnd = { onDragStopped(); release() },
            onDragCancel = { onDragStopped(); release() },
            onDrag = { change, dragAmount ->
                if (canDrag(change.position) && canDrag(change.previousPosition)) {
                    change.consume()
                    onDrag(size, dragAmount)
                }
            }
        )
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, spring(1f, 1000f, 0.001f)) }
            launch { scaleXAnimation.animateTo(pressedScale, spring(0.6f, 250f, 0.001f)) }
            launch { scaleYAnimation.animateTo(pressedScale, spring(0.7f, 250f, 0.001f)) }
        }
    }

    fun release() {
        animationScope.launch {
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                snapshotFlow { valueAnimation.value }.first { abs(it - valueAnimation.targetValue) < threshold }
            }
            launch { pressProgressAnimation.animateTo(0f, spring(1f, 1000f, 0.001f)) }
            launch { scaleXAnimation.animateTo(initialScale, spring(0.6f, 250f, 0.001f)) }
            launch { scaleYAnimation.animateTo(initialScale, spring(0.7f, 250f, 0.001f)) }
        }
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(targetValue, spring(1f, 1000f, visibilityThreshold)) { updateVelocity() } }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            press()
            val targetValue = value.coerceIn(valueRange)
            launch { valueAnimation.animateTo(targetValue, spring(1f, 1000f, visibilityThreshold)) }
            if (velocity != 0f) launch { velocityAnimation.animateTo(0f, spring(0.5f, 300f, visibilityThreshold * 10f)) }
            release()
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(System.currentTimeMillis(), Offset(value, 0f))
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch { velocityAnimation.animateTo(targetVelocity, spring(0.5f, 300f, visibilityThreshold * 10f)) }
    }
}

@SuppressLint("NewApi")
class InteractiveHighlight(val animationScope: CoroutineScope, val position: (size: Size, offset: Offset) -> Offset) {
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val positionAnimation = Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
    private var startPosition = Offset.Zero

    private val shader = RuntimeShader("uniform float2 size; layout(color) uniform half4 color; uniform float radius; uniform float2 position; half4 main(float2 coord) { float dist = distance(coord, position); float intensity = smoothstep(radius, radius * 0.5, dist); return color * intensity; }")

    val modifier: Modifier = Modifier.drawWithContent {
        val progress = pressProgressAnimation.value
        if (progress > 0f) {
            drawRect(Color.White.copy(0.06f * progress), blendMode = BlendMode.Plus)
            shader.apply {
                val pos = position(size, positionAnimation.value)
                setFloatUniform("size", size.width, size.height)
                setColorUniform("color", Color.White.copy(0.12f * progress).toArgb())
                setFloatUniform("radius", size.minDimension * 1.2f)
                setFloatUniform("position", pos.x.coerceIn(0f, size.width), pos.y.coerceIn(0f, size.height))
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
                    launch { pressProgressAnimation.animateTo(1f, spring(0.5f, 300f, 0.001f)) }
                    launch { positionAnimation.snapTo(startPosition) }
                }
            },
            onDragEnd = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, spring(0.5f, 300f, 0.001f)) }
                    launch { positionAnimation.animateTo(startPosition, spring(0.5f, 300f, Offset.VisibilityThreshold)) }
                }
            },
            onDragCancel = {
                animationScope.launch {
                    launch { pressProgressAnimation.animateTo(0f, spring(0.5f, 300f, 0.001f)) }
                    launch { positionAnimation.animateTo(startPosition, spring(0.5f, 300f, Offset.VisibilityThreshold)) }
                }
            },
            onDrag = { change, _ ->
                change.consume()
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        )
    }
}

// ============================================================================
// 3. KERNELSU LIQUID GLASS BACKDROP SHADERS
// ============================================================================

class CombinedBackdrop(val first: Backdrop, val second: Backdrop) : Backdrop {
    override val isCoordinatesDependent: Boolean = first.isCoordinatesDependent || second.isCoordinatesDependent
    override val offsetResidualX: Float get() = first.offsetResidualX
    override val offsetResidualY: Float get() = first.offsetResidualY
    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?, downscaleFactor: Int) {
        with(first) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        with(second) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

@Composable
fun rememberCombinedBackdrop(first: Backdrop, second: Backdrop): Backdrop = remember(first, second) { CombinedBackdrop(first, second) }

data class InnerShadow(val radius: Dp = 24.dp, val offset: DpOffset = DpOffset(0.dp, radius), val color: Color = Color.Black.copy(alpha = 0.15f), val alpha: Float = 1f, val blendMode: BlendMode = DrawScope.DefaultBlendMode)
fun Modifier.innerShadow(shape: Shape, shadow: () -> InnerShadow?): Modifier = this then InnerShadowElement(shape, shadow)
private class InnerShadowElement(val shape: Shape, val shadow: () -> InnerShadow?) : ModifierNodeElement<InnerShadowNode>() {
    override fun create() = InnerShadowNode(shape, shadow)
    override fun update(node: InnerShadowNode) { node.shape = shape; node.shadow = shadow; node.invalidateDraw() }
    override fun InspectorInfo.inspectableProperties() {}
    override fun equals(other: Any?) = true
    override fun hashCode() = 0
}
private class InnerShadowNode(var shape: Shape, var shadow: () -> InnerShadow?) : Modifier.Node(), DrawModifierNode {
    private var shadowLayer: GraphicsLayer? = null
    private val paint = Paint()
    private val clipPath = Path()
    override fun ContentDrawScope.draw() {
        drawContent()
        val shadow = shadow() ?: return
        val layer = shadowLayer ?: return
        val outline = shape.createOutline(size, layoutDirection, this)
        clipPath.reset()
        when (outline) { is Outline.Rectangle -> clipPath.addRect(outline.rect); is Outline.Rounded -> clipPath.addRoundRect(outline.roundRect); is Outline.Generic -> clipPath.addPath(outline.path) }
        paint.color = shadow.color; layer.alpha = shadow.alpha; layer.blendMode = shadow.blendMode
        layer.renderEffect = if (shadow.radius.toPx() > 0f) BlurEffect(shadow.radius.toPx(), shadow.radius.toPx(), TileMode.Decal) else null
        layer.record { drawContext.canvas.let { canvas -> canvas.save(); canvas.clipPath(clipPath); canvas.drawOutline(outline, paint); canvas.translate(shadow.offset.x.toPx(), shadow.offset.y.toPx()); canvas.drawOutline(outline, Paint().apply { blendMode = BlendMode.Clear }); canvas.restore() } }
        drawContext.canvas.let { canvas -> canvas.save(); canvas.clipPath(clipPath); drawLayer(layer); canvas.restore() }
    }
    override fun onAttach() { shadowLayer = requireGraphicsContext().createGraphicsLayer().apply { compositingStrategy = CompositingStrategy.Offscreen } }
    override fun onDetach() { shadowLayer?.let { requireGraphicsContext().releaseGraphicsLayer(it); shadowLayer = null } }
}

fun BackdropEffectScope.vibrancy() { colorControls(brightness = 0f, contrast = 1f, saturation = 1.5f) }

fun BackdropEffectScope.lens(refractionHeight: Float, refractionAmount: Float, depthEffect: Boolean = false, chromaticAberration: Float = 0f) {
    if (!isRuntimeShaderSupported() || refractionHeight <= 0f || refractionAmount <= 0f) return
    if (padding < refractionAmount) padding = refractionAmount
    val radii = roundedRectCornerRadii() ?: return
    val sf = downscaleFactor.coerceAtLeast(1).toFloat()
    runtimeShaderEffect(
        key = if (chromaticAberration > 0f) "LiquidGlassLensDispersion" else "LiquidGlassLens",
        shaderString = if (chromaticAberration > 0f) ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER else ROUNDED_RECT_REFRACTION_SHADER,
        uniformShaderName = "content"
    ) {
        setFloatUniform("size", size.width / sf, size.height / sf)
        setFloatUniform("offset", -padding / sf, -padding / sf)
        setFloatUniform("cornerRadii", FloatArray(radii.size) { radii[it] / sf })
        setFloatUniform("refractionHeight", refractionHeight / sf)
        setFloatUniform("refractionAmount", -refractionAmount / sf)
        setFloatUniform("depthEffect", if (depthEffect) 1f else 0f)
        if (chromaticAberration > 0f) setFloatUniform("chromaticAberration", chromaticAberration)
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
        (if (isLtr) cornerShape.bottomStart.toPx(size, this) else cornerShape.bottomEnd.toPx(size, this)).coerceAtMost(maxRadius)
    )
}

private const val ROUNDED_RECT_SDF = "float radiusAt(float2 coord, float4 radii) { if (coord.x >= 0.0) { if (coord.y <= 0.0) return radii.y; else return radii.z; } else { if (coord.y <= 0.0) return radii.x; else return radii.w; } } float sdRoundedRect(float2 coord, float2 halfSize, float radius) { float2 cornerCoord = abs(coord) - (halfSize - float2(radius)); float outside = length(max(cornerCoord, 0.0)) - radius; float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0); return outside + inside; } float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) { float2 cornerCoord = abs(coord) - (halfSize - float2(radius)); if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) { return sign(coord) * normalize(max(cornerCoord, 0.0)); } else { float gradX = step(cornerCoord.y, cornerCoord.x); return sign(coord) * float2(gradX, 1.0 - gradX); } }"
private const val ROUNDED_RECT_REFRACTION_SHADER = "uniform shader content; uniform float2 size; uniform float2 offset; uniform float4 cornerRadii; uniform float refractionHeight; uniform float refractionAmount; uniform float depthEffect; $ROUNDED_RECT_SDF float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); } half4 main(float2 coord) { float2 halfSize = size * 0.5; float2 centeredCoord = (coord + offset) - halfSize; float radius = radiusAt(coord, cornerRadii); float sd = sdRoundedRect(centeredCoord, halfSize, radius); if (-sd >= refractionHeight) { return content.eval(coord); } sd = min(sd, 0.0); float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount; float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y)); float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord)); float2 refractedCoord = coord + d * grad; return content.eval(refractedCoord); }"
private const val ROUNDED_RECT_REFRACTION_WITH_DISPERSION_SHADER = "uniform shader content; uniform float2 size; uniform float2 offset; uniform float4 cornerRadii; uniform float refractionHeight; uniform float refractionAmount; uniform float depthEffect; uniform float chromaticAberration; $ROUNDED_RECT_SDF float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); } half4 main(float2 coord) { float2 halfSize = size * 0.5; float2 centeredCoord = (coord + offset) - halfSize; float radius = radiusAt(coord, cornerRadii); float sd = sdRoundedRect(centeredCoord, halfSize, radius); if (-sd >= refractionHeight) { return content.eval(coord); } sd = min(sd, 0.0); float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount; float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y)); float2 grad = normalize(gradSdRoundedRect(centeredCoord, halfSize, gradRadius) + depthEffect * normalize(centeredCoord)); float2 refractedCoord = coord + d * grad; float dispersionIntensity = chromaticAberration * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y)); float2 dispersedCoord = d * grad * dispersionIntensity; half4 color = half4(0.0); half4 red = content.eval(refractedCoord + dispersedCoord); color.r += red.r / 3.5; color.a += red.a / 7.0; half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0)); color.r += orange.r / 3.5; color.g += orange.g / 7.0; color.a += orange.a / 7.0; half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0)); color.r += yellow.r / 3.5; color.g += yellow.g / 3.5; color.a += yellow.a / 7.0; half4 green = content.eval(refractedCoord); color.g += green.g / 3.5; color.a += green.a / 7.0; half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0)); color.g += cyan.g / 3.5; color.b += cyan.b / 3.0; color.a += cyan.a / 7.0; half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0)); color.b += blue.b / 3.0; color.a += blue.a / 7.0; half4 purple = content.eval(refractedCoord - dispersedCoord); color.r += purple.r / 7.0; color.b += purple.b / 3.0; color.a += purple.a / 7.0; return color; }"
