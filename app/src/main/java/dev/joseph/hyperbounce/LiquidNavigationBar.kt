package dev.joseph.hyperbounce

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.ui.util.lerp
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/**
 * One-to-one visual port of the LiquidBottomTabs component from
 * AndroidLiquidGlass-kmp, with only the backdrop source substituted:
 *
 *   library: LayerBackdrop / GraphicsLayer from Compose content
 *   here:    LiquidGlassViewBackdrop fed from the underlying target app View
 *
 * The actual liquid rendering, lens refraction, chromatic aberration,
 * highlight, shadow, inner-shadow and damped drag parameters are retained.
 */
@Composable
fun LiquidNavigationBar(
    bottomPadding: Dp,
    tabs: List<Bitmap?>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    sourceView: android.view.View,
    circularIndices: Set<Int> = emptySet(),
) {
    if (tabs.isEmpty()) return

    val isLightTheme = !isSystemInDarkTheme()
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    val density = LocalDensity.current
    val liveBackdrop = remember(sourceView) {
        LiquidGlassViewBackdrop(sourceView)
    }
    // Read the live-frame state at composition time so every captured frame invalidates
    // the Backdrop modifier and the library's GPU RenderEffect pipeline is redrawn.
    @Suppress("UNUSED_VARIABLE")
    val liveFrame = liveBackdrop.capturedFrame
    val capturePaddingPx = remember(density) {
        with(density) { 32.dp.toPx().toInt() }
    }

    DisposableEffect(liveBackdrop) {
        sourceView.post { liveBackdrop.start() }
        onDispose { liveBackdrop.release() }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = bottomPadding)
                .width(320.dp)
                .height(64.dp),
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                val tabCount = tabs.size
                val tabWidthPx =
                    (constraints.maxWidth.toFloat() - with(density) { 8.dp.toPx() }) /
                        tabCount.coerceAtLeast(1)

                val offsetAnimation = remember { Animatable(0f) }
                val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr

                val panelOffset by remember(density) {
                    derivedStateOf {
                        val fraction = (offsetAnimation.value / constraints.maxWidth.toFloat())
                            .fastCoerceIn(-1f, 1f)
                        with(density) {
                            4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                        }
                    }
                }

                val animationScope = rememberCoroutineScope()

                val dampedDragAnimation = remember(animationScope, tabCount) {
                    LiquidGlassDampedDragAnimation(
                        animationScope = animationScope,
                        initialValue = selectedIndex.coerceIn(0, tabCount - 1).toFloat(),
                        valueRange = 0f..(tabCount - 1).toFloat(),
                        visibilityThreshold = 0.001f,
                        initialScale = 1f,
                        pressedScale = 78f / 56f,
                        onDragStarted = {},
                        onDragStopped = {
                            val index = targetValue
                                .toInt()
                                .coerceIn(0, tabCount - 1)
                            animateToValue(index.toFloat())
                            animationScope.launch {
                                offsetAnimation.animateTo(
                                    0f,
                                    spring(1f, 300f, 0.5f)
                                )
                            }
                            onTabSelected(index)
                        },
                        onDrag = { _, dragAmount ->
                            updateValue(
                                (
                                    targetValue +
                                        dragAmount.x / tabWidthPx *
                                        if (isLtr) 1f else -1f
                                    ).fastCoerceIn(0f, (tabCount - 1).toFloat())
                            )
                            animationScope.launch {
                                offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                            }
                        },
                    )
                }

                // Mirror external/native tab selection into the exact damped animation.
                LaunchedEffect(selectedIndex, tabCount) {
                    dampedDragAnimation.animateToValue(
                        selectedIndex.coerceIn(0, tabCount - 1).toFloat()
                    )
                }

                val interactiveHighlight = remember(animationScope, tabCount) {
                    LiquidGlassInteractiveHighlight(
                        animationScope = animationScope,
                        position = { size, _ ->
                            Offset(
                                if (isLtr) {
                                    (dampedDragAnimation.value + 0.5f) * tabWidthPx + panelOffset
                                } else {
                                    size.width -
                                        (dampedDragAnimation.value + 0.5f) * tabWidthPx +
                                        panelOffset
                                },
                                size.height / 2f,
                            )
                        },
                    )
                }

                Row(
                    Modifier
                        .graphicsLayer {
                            translationX = panelOffset
                        }
                        .onGloballyPositioned { coordinates ->
                            val position = coordinates.positionInWindow()
                            liveBackdrop.setTargetBounds(
                                left = position.x,
                                top = position.y,
                                width = coordinates.size.width,
                                height = coordinates.size.height,
                                paddingPx = capturePaddingPx,
                            )
                        }
                        .drawBackdrop(
                            backdrop = liveBackdrop,
                            shape = { Capsule() },
                            effects = {
                                vibrancy()
                                blur(8f.dp.toPx())
                                lens(24f.dp.toPx(), 24f.dp.toPx())
                            },
                            layerBlock = {
                                val progress = dampedDragAnimation.pressProgress
                                val scale = lerp(
                                    1f,
                                    1f + 16f.dp.toPx() / size.width,
                                    progress,
                                )
                                scaleX = scale
                                scaleY = scale
                            },
                            onDrawSurface = {
                                drawRect(containerColor)
                            },
                        )
                        .then(interactiveHighlight.modifier)
                        .height(64.dp)
                        .fillMaxWidth()
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { index, bitmap ->
                        LiquidBottomTab(
                            bitmap = bitmap,
                            isCircular = index in circularIndices,
                            onClick = {
                                onTabSelected(index)
                                dampedDragAnimation.animateToValue(index.toFloat())
                            },
                        )
                    }
                }

                // Exact library trick: record tab contents separately so that the
                // selected liquid capsule refracts the icons themselves as well.
                val tabsBackdrop = rememberLayerBackdrop()

                CompositionLocalProvider(
                    LocalLiquidBottomTabScale provides {
                        lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                    },
                ) {
                    Row(
                        Modifier
                            .clearAndSetSemantics {}
                            .alpha(0f)
                            .layerBackdrop(tabsBackdrop)
                            .graphicsLayer {
                                translationX = panelOffset
                            }
                            .drawBackdrop(
                                backdrop = liveBackdrop,
                                shape = { Capsule() },
                                effects = {
                                    val progress = dampedDragAnimation.pressProgress
                                    vibrancy()
                                    blur(8f.dp.toPx())
                                    lens(
                                        24f.dp.toPx() * progress,
                                        24f.dp.toPx() * progress,
                                    )
                                },
                                highlight = {
                                    val progress = dampedDragAnimation.pressProgress
                                    Highlight.Default.copy(alpha = progress)
                                },
                                onDrawSurface = {
                                    drawRect(containerColor)
                                },
                            )
                            .then(interactiveHighlight.modifier)
                            .height(56.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .graphicsLayer(
                                colorFilter = ColorFilter.tint(accentColor),
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEachIndexed { index, bitmap ->
                            LiquidBottomTab(
                                bitmap = bitmap,
                                isCircular = index in circularIndices,
                                onClick = {},
                            )
                        }
                    }
                }

                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .graphicsLayer {
                            translationX =
                                if (isLtr) {
                                    dampedDragAnimation.value * tabWidthPx + panelOffset
                                } else {
                                    size.width -
                                        (dampedDragAnimation.value + 1f) * tabWidthPx +
                                        panelOffset
                                }
                        }
                        .then(interactiveHighlight.gestureModifier)
                        .then(dampedDragAnimation.modifier)
                        .drawBackdrop(
                            backdrop = rememberCombinedBackdrop(
                                liveBackdrop,
                                tabsBackdrop,
                            ),
                            shape = { Capsule() },
                            effects = {
                                val progress = dampedDragAnimation.pressProgress
                                lens(
                                    10f.dp.toPx() * progress,
                                    14f.dp.toPx() * progress,
                                    chromaticAberration = true,
                                )
                            },
                            highlight = {
                                val progress = dampedDragAnimation.pressProgress
                                Highlight.Default.copy(alpha = progress)
                            },
                            shadow = {
                                val progress = dampedDragAnimation.pressProgress
                                Shadow(alpha = progress)
                            },
                            innerShadow = {
                                val progress = dampedDragAnimation.pressProgress
                                InnerShadow(
                                    radius = 8f.dp * progress,
                                    alpha = progress,
                                )
                            },
                            layerBlock = {
                                scaleX = dampedDragAnimation.scaleX
                                scaleY = dampedDragAnimation.scaleY

                                val velocity = dampedDragAnimation.velocity / 10f
                                scaleX /= 1f -
                                    (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f -
                                    (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            },
                            onDrawSurface = {
                                val progress = dampedDragAnimation.pressProgress
                                drawRect(
                                    if (isLightTheme) {
                                        Color.Black.copy(0.1f)
                                    } else {
                                        Color.White.copy(0.1f)
                                    },
                                    alpha = 1f - progress,
                                )
                                drawRect(
                                    Color.Black.copy(alpha = 0.03f * progress)
                                )
                            },
                        )
                        .height(56.dp)
                        .fillMaxWidth(1f / tabCount),
                )
            }
        }
    }
}

private val LocalLiquidBottomTabScale =
    staticCompositionLocalOf { { 1f } }

@Composable
private fun RowScope.LiquidBottomTab(
    bitmap: Bitmap?,
    isCircular: Boolean,
    onClick: () -> Unit,
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        Modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val value = scale()
                scaleX = value
                scaleY = value
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .then(
                        if (isCircular) Modifier.clip(CircleShape) else Modifier
                    ),
            )
        }
    }
}
