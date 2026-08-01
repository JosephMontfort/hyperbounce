package dev.joseph.hyperbounce

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first

val LocalFloatingBottomBarTabScale = staticCompositionLocalOf { { 1f } }

@Composable
fun RowScope.FloatingBottomBarItem(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val scale = LocalFloatingBottomBarTabScale.current
    Column(
        // Ensure tabs have a premium minimum width so the bar doesn't look squished
        modifier.defaultMinSize(minWidth = 72.dp).clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick)
            .fillMaxHeight().weight(1f).graphicsLayer { val s = scale(); scaleX = s; scaleY = s },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
fun FloatingBottomBar(
    modifier: Modifier = Modifier,
    selectedIndex: () -> Int, onSelected: (index: Int) -> Unit,
    tabsCount: Int, isDarkTheme: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    val pillShape = CircleShape
    // Highly tuned frosted glass aesthetic colors
    val containerColor = if (isDarkTheme) Color(0xCC181818) else Color(0xCCF2F2F2)
    val strokeColor = if (isDarkTheme) Color(0x20FFFFFF) else Color(0x15000000)
    val indicatorColor = if (isDarkTheme) Color(0x30FFFFFF) else Color(0x15000000)

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
            animationScope = animationScope, initialValue = selectedIndex().toFloat(), valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f, initialScale = 1f, pressedScale = 1.39f, // KernelSU 78/56 scale
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

    // Dynamic width using IntrinsicSize.Min to tightly pack the tabs
    Box(modifier = modifier.width(IntrinsicSize.Min), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier
                .onGloballyPositioned { coords ->
                    totalWidthPx = coords.size.width.toFloat()
                    tabWidthPx = ((totalWidthPx - with(density) { 8.dp.toPx() }) / tabsCount).coerceAtLeast(0f)
                }
                .graphicsLayer { translationX = panelOffset }
                .shadow(elevation = 16.dp, shape = pillShape, clip = false) // clip = false prevents bounding box cutoffs
                .background(containerColor, pillShape)
                .border(1.dp, strokeColor, pillShape)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                .height(64.dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) { 
            CompositionLocalProvider(LocalFloatingBottomBarTabScale provides { androidx.compose.ui.util.lerp(1f, 1.2f, dampedDragAnimation.pressProgress) }) { 
                content() 
            } 
        }

        if (tabWidthPx > 0f) {
            val tabWidthDp = with(density) { tabWidthPx.toDp() }
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .graphicsLayer {
                        val progressOffset = dampedDragAnimation.value * tabWidthPx
                        translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                    }
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDragAnimation.modifier)
                    // Apply scale here so it naturally bleeds outside the parent container without hitting a clip mask
                    .graphicsLayer {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).coerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).coerceIn(-0.2f, 0.2f)
                    }
                    .then(interactiveHighlight.modifier)
                    .background(indicatorColor, pillShape)
                    .height(56.dp)
                    .width(tabWidthDp)
            )
        }
    }
}

class DampedDragAnimation(
    private val animationScope: CoroutineScope, val initialValue: Float, val valueRange: ClosedRange<Float>, val visibilityThreshold: Float,
    val initialScale: Float, val pressedScale: Float, val canDrag: (Offset) -> Boolean = { true },
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit, val onDragStopped: DampedDragAnimation.() -> Unit, val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
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
            onDrag = { change, dragAmount -> if (canDrag(change.position) && canDrag(change.previousPosition)) { change.consume(); onDrag(size, dragAmount) } }
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
            if (value != targetValue) { val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f; snapshotFlow { valueAnimation.value }.first { abs(it - valueAnimation.targetValue) < threshold } }
            launch { pressProgressAnimation.animateTo(0f, spring(1f, 1000f, 0.001f)) }
            launch { scaleXAnimation.animateTo(initialScale, spring(0.6f, 250f, 0.001f)) }
            launch { scaleYAnimation.animateTo(initialScale, spring(0.7f, 250f, 0.001f)) }
        }
    }
    fun updateValue(value: Float) { val targetValue = value.coerceIn(valueRange); animationScope.launch { launch { valueAnimation.animateTo(targetValue, spring(1f, 1000f, visibilityThreshold)) { updateVelocity() } } } }
    fun animateToValue(value: Float) { animationScope.launch { press(); val targetValue = value.coerceIn(valueRange); launch { valueAnimation.animateTo(targetValue, spring(1f, 1000f, visibilityThreshold)) }; if (velocity != 0f) launch { velocityAnimation.animateTo(0f, spring(0.5f, 300f, visibilityThreshold * 10f)) }; release() } }
    private fun updateVelocity() { velocityTracker.addPosition(System.currentTimeMillis(), Offset(value, 0f)); val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start); animationScope.launch { velocityAnimation.animateTo(targetVelocity, spring(0.5f, 300f, visibilityThreshold * 10f)) } }
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
            shader.apply { val pos = position(size, positionAnimation.value); setFloatUniform("size", size.width, size.height); setColorUniform("color", Color.White.copy(0.15f * progress).toArgb()); setFloatUniform("radius", size.minDimension * 1.2f); setFloatUniform("position", pos.x.coerceIn(0f, size.width), pos.y.coerceIn(0f, size.height)) }
            drawRect(ShaderBrush(shader), blendMode = BlendMode.Plus)
        }
        drawContent()
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        detectDragGestures(
            onDragStart = { offset -> startPosition = offset; animationScope.launch { launch { pressProgressAnimation.animateTo(1f, spring(0.5f, 300f, 0.001f)) }; launch { positionAnimation.snapTo(startPosition) } } },
            onDragEnd = { animationScope.launch { launch { pressProgressAnimation.animateTo(0f, spring(0.5f, 300f, 0.001f)) }; launch { positionAnimation.animateTo(startPosition, spring(0.5f, 300f, Offset.VisibilityThreshold)) } } },
            onDragCancel = { animationScope.launch { launch { pressProgressAnimation.animateTo(0f, spring(0.5f, 300f, 0.001f)) }; launch { positionAnimation.animateTo(startPosition, spring(0.5f, 300f, Offset.VisibilityThreshold)) } } },
            onDrag = { change, _ -> change.consume(); animationScope.launch { positionAnimation.snapTo(change.position) } }
        )
    }
}
