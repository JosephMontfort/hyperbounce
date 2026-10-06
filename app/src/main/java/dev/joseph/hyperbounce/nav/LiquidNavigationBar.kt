package dev.joseph.hyperbounce.nav

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.ui.util.lerp
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import dev.joseph.hyperbounce.nav.glass.LiquidGlassDampedDragAnimation
import dev.joseph.hyperbounce.nav.glass.LiquidGlassInteractiveHighlight
import dev.joseph.hyperbounce.nav.glass.LiquidGlassViewBackdrop
import dev.joseph.hyperbounce.nav.glass.liquidLens
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
    activeTabs: List<Bitmap?> = tabs,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    sourceView: android.view.View,
    circularIndices: Set<Int> = emptySet(),
    forceLightTheme: Boolean = false,
    forceDarkTheme: Boolean = false,
    containerAlpha: Float = 0.4f,
    effectMultiplier: Float = 1f,
    barBlurIntensity: Float = 1f,
    labels: List<String> = emptyList(),
    customAccentColor: Color? = null,
    barHeight: Dp = 64.dp,
    tabWidth: Dp = 64.dp,
    cornerRadius: Dp = 32.dp,
    pillGlassIntensity: Float = 1f,
    pillContainerOpacity: Float = 1f,
    pillChromaticAberration: Boolean = true,
    pillShadow: Boolean = true,
    liquidGlass: Boolean = true,
    isShrunk: Boolean = false,
    shrinkScale: Float = 0.70f,
    scrollShrinkState: ScrollShrinkState? = null,
    badgeCounts: List<String?> = emptyList(),
    badgeDots: List<Boolean> = emptyList(),
    enablePixelCopy: Boolean = false,
) {
    if (tabs.isEmpty()) return

    val isLightTheme = when {
        forceLightTheme -> true
        forceDarkTheme -> false
        else -> !isSystemInDarkTheme()
    }
    val accentColor = customAccentColor ?: (
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    )
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(containerAlpha)
        else Color(0xFF121212).copy(containerAlpha)

    val density = LocalDensity.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val liveBackdrop = if (liquidGlass) {
        remember(sourceView) {
            LiquidGlassViewBackdrop(sourceView)
        }
    } else null
    // Do not read capturedFrame during composition. The custom Backdrop reads the
    // snapshot state from its DrawScope, which invalidates only the render pass for
    // each live frame instead of recomposing the entire navigation bar at 60 Hz.
    val capturePaddingPx = remember(density) {
        with(density) { 24.dp.toPx().toInt() }
    }

    if (liveBackdrop != null) {
        liveBackdrop.enablePixelCopy = enablePixelCopy
        DisposableEffect(liveBackdrop) {
            sourceView.post { liveBackdrop.start() }
            onDispose { liveBackdrop.release() }
        }
    }

    val shrinkAnim = remember { Animatable(if (isShrunk) 1f else 0f) }
    LaunchedEffect(isShrunk) {
        shrinkAnim.animateTo(
            targetValue = if (isShrunk) 1f else 0f,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = 380f,
                visibilityThreshold = 0.001f
            )
        )
    }
    val shrinkProgress = shrinkAnim.value
    val currentScale = lerp(1f, shrinkScale.coerceIn(0.3f, 0.95f), shrinkProgress)
    val maxShiftY = remember(density, bottomPadding) {
        with(density) {
            ((bottomPadding - 4.dp).coerceAtLeast(0.dp) * 0.55f).toPx()
        }
    }
    val currentShiftY = lerp(0f, maxShiftY, shrinkProgress)

    val currentBarHeight = barHeight * currentScale
    val currentTabWidth = tabWidth * currentScale
    val currentCornerRadius = cornerRadius * currentScale

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = bottomPadding)
                .graphicsLayer {
                    translationY = currentShiftY
                }
                .onGloballyPositioned { coordinates ->
                    val position = coordinates.positionInWindow()
                    scrollShrinkState?.updateBounds(
                        l = position.x,
                        t = position.y,
                        r = position.x + coordinates.size.width,
                        b = position.y + coordinates.size.height,
                    )
                }
                .width((tabs.size * currentTabWidth.value).dp)
                .height(currentBarHeight),
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                val tabCount = tabs.size
                val tabWidthPx =
                    (constraints.maxWidth.toFloat() - with(density) { (8.dp * currentScale).toPx() }) /
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
                var lastHapticIndex by remember(selectedIndex) {
                    mutableIntStateOf(selectedIndex)
                }

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
                            val index = kotlin.math.round(value)
                                .toInt()
                                .coerceIn(0, tabCount - 1)
                            lastHapticIndex = index
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
                            val newVal = (
                                targetValue +
                                    dragAmount.x / tabWidthPx *
                                    if (isLtr) 1f else -1f
                                ).fastCoerceIn(0f, (tabCount - 1).toFloat())
                            updateValue(newVal)

                            val newIndex = kotlin.math.round(newVal).toInt().coerceIn(0, tabCount - 1)
                            if (newIndex != lastHapticIndex) {
                                lastHapticIndex = newIndex
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            }

                            animationScope.launch {
                                offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                            }
                        },
                    )
                }

                // Mirror external/native tab selection into the exact damped animation.
                LaunchedEffect(selectedIndex, tabCount) {
                    lastHapticIndex = selectedIndex.coerceIn(0, tabCount - 1)
                    dampedDragAnimation.animateToValue(
                        selectedIndex.coerceIn(0, tabCount - 1).toFloat()
                    )
                }

                scrollShrinkState?.isInteracting = (dampedDragAnimation.pressProgress > 0.01f)

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

                if (!liquidGlass) {
                    val barShape = RoundedCornerShape(currentCornerRadius)
                    val pillShape = CircleShape

                    val classicBarBg = if (isLightTheme) {
                        Color(0xFFE5E5EA).copy(alpha = containerAlpha.coerceIn(0.05f, 1f))
                    } else {
                        Color(0xFF161618).copy(alpha = containerAlpha.coerceIn(0.05f, 1f))
                    }
                    val classicBarBorder = if (isLightTheme) {
                        Color(0xFFD1D1D6).copy(alpha = 0.7f)
                    } else {
                        Color(0xFF2C2C2E).copy(alpha = 0.8f)
                    }

                    val classicPillBg = if (isLightTheme) {
                        Color.White.copy(alpha = pillContainerOpacity.coerceIn(0.4f, 1f))
                    } else {
                        Color(0xFF3A3A3C).copy(alpha = pillContainerOpacity.coerceIn(0.3f, 1f))
                    }
                    val classicPillBorder = if (isLightTheme) {
                        Color(0xFFE5E5EA).copy(alpha = 0.5f)
                    } else {
                        Color(0xFF545458).copy(alpha = 0.45f)
                    }

                    val progress = dampedDragAnimation.pressProgress
                    val velocity = dampedDragAnimation.velocity / 10f
                    val squishX = 1f / (1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f))
                    val squishY = 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)

                    val pillW = tabWidthPx * dampedDragAnimation.scaleX * squishX
                    val basePillH = with(density) { (currentBarHeight - 8.dp * currentScale).toPx() }
                    val maxPillH = with(density) { (currentBarHeight - 2.dp * currentScale).toPx() }
                    val bloomedPillH = lerp(basePillH, maxPillH, progress)
                    val pillH = (bloomedPillH * squishY).coerceIn(with(density) { (16.dp * currentScale).toPx() }, maxPillH)

                    val pillX = if (isLtr) {
                        dampedDragAnimation.value * tabWidthPx
                    } else {
                        (tabCount - 1 - dampedDragAnimation.value) * tabWidthPx
                    }
                    val pillCenter = pillX + tabWidthPx / 2f
                    val pLeft = pillCenter - pillW / 2f
                    val pRight = pillCenter + pillW / 2f

                    Box(
                        Modifier
                            .graphicsLayer {
                                translationX = panelOffset
                                scaleX = lerp(
                                    1f,
                                    1f + (16f.dp * currentScale).toPx() / size.width,
                                    progress,
                                )
                                scaleY = lerp(
                                    1f,
                                    1f + (2f.dp * currentScale).toPx() / size.height,
                                    progress,
                                )
                                clip = true
                                shape = barShape
                            }
                            .background(classicBarBg, barShape)
                            .border(0.75.dp * currentScale, classicBarBorder, barShape)
                            .then(interactiveHighlight.modifier)
                            .height(currentBarHeight)
                            .fillMaxWidth()
                            .padding(4.dp * currentScale),
                    ) {
                        // Layer 1: Inactive unaccented tab items (handles direct tapping on any tab)
                        Row(
                            Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            tabs.forEachIndexed { index, bitmap ->
                                LiquidBottomTab(
                                    bitmap = bitmap,
                                    isCircular = index in circularIndices,
                                    accentColor = null,
                                    label = labels.getOrNull(index),
                                    scaleFactor = currentScale,
                                    badgeCount = badgeCounts.getOrNull(index),
                                    hasBadgeDot = badgeDots.getOrNull(index) ?: false,
                                    onClick = {
                                        onTabSelected(index)
                                        dampedDragAnimation.animateToValue(index.toFloat())
                                    },
                                )
                            }
                        }

                        // Layer 2: Moving Tab Indicator Pill Surface (Horizontal Bloom & Jelly velocity stretch with fixed rounded caps)
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .graphicsLayer {
                                    translationX = pLeft
                                }
                                .width(with(density) { pillW.toDp() })
                                .height(with(density) { pillH.toDp() })
                                .background(classicPillBg, pillShape)
                                .border(0.5.dp * currentScale, classicPillBorder, pillShape)
                        )

                        // Layer 3: Active accented tab items, dynamically clipped strictly to the moving pill shape (no onClick so touches pass through)
                        Row(
                            Modifier
                                .fillMaxSize()
                                .clip(object : Shape {
                                    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                                        val pTop = (size.height - pillH) / 2f
                                        val pBottom = pTop + pillH
                                        val r = pillH / 2f
                                        val path = Path().apply {
                                            addRoundRect(
                                                RoundRect(
                                                    left = pLeft,
                                                    top = pTop,
                                                    right = pRight,
                                                    bottom = pBottom,
                                                    radiusX = r,
                                                    radiusY = r,
                                                )
                                            )
                                        }
                                        return Outline.Generic(path)
                                    }
                                }),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            tabs.forEachIndexed { index, _ ->
                                val bitmap = activeTabs.getOrNull(index) ?: tabs.getOrNull(index)
                                LiquidBottomTab(
                                    bitmap = bitmap,
                                    isCircular = index in circularIndices,
                                    accentColor = accentColor,
                                    label = labels.getOrNull(index),
                                    scaleFactor = currentScale,
                                    badgeCount = badgeCounts.getOrNull(index),
                                    hasBadgeDot = badgeDots.getOrNull(index) ?: false,
                                    onClick = null,
                                )
                            }
                        }

                        // Layer 4: Topmost Interactive Gesture Box for the Pill (receives long-press & pull)
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .graphicsLayer {
                                    translationX = pLeft
                                }
                                .width(with(density) { pillW.toDp() })
                                .height(with(density) { pillH.toDp() })
                                .then(interactiveHighlight.gestureModifier)
                                .then(dampedDragAnimation.modifier)
                        )
                    }
                } else if (liveBackdrop != null) {
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
                                shape = { androidx.compose.foundation.shape.RoundedCornerShape(currentCornerRadius) },
                                effects = {
                                    vibrancy()
                                    if (barBlurIntensity > 0.001f) {
                                        blur(8f.dp.toPx() * effectMultiplier * barBlurIntensity)
                                    }
                                    lens(18f.dp.toPx() * effectMultiplier, 18f.dp.toPx() * effectMultiplier)
                                },
                                layerBlock = {
                                    val progress = dampedDragAnimation.pressProgress
                                    val scale = lerp(
                                        1f,
                                        1f + (16f.dp * currentScale).toPx() / size.width,
                                        progress,
                                    )
                                    scaleX = scale
                                    scaleY = scale
                                    clip = true
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(currentCornerRadius)
                                },
                                onDrawSurface = {
                                    drawRect(containerColor)
                                },
                            )
                            .then(interactiveHighlight.modifier)
                            .height(currentBarHeight)
                            .fillMaxWidth()
                            .padding(4.dp * currentScale),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEachIndexed { index, bitmap ->
                            LiquidBottomTab(
                                bitmap = bitmap,
                                isCircular = index in circularIndices,
                                accentColor = null,
                                label = labels.getOrNull(index),
                                scaleFactor = currentScale,
                                badgeCount = badgeCounts.getOrNull(index),
                                hasBadgeDot = badgeDots.getOrNull(index) ?: false,
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
                                    shape = { androidx.compose.foundation.shape.RoundedCornerShape(currentCornerRadius) },
                                    effects = {
                                        val progress = dampedDragAnimation.pressProgress
                                        vibrancy()
                                        if (barBlurIntensity > 0.001f) {
                                            blur(8f.dp.toPx() * effectMultiplier * barBlurIntensity)
                                        }
                                        lens(
                                            18f.dp.toPx() * progress * effectMultiplier,
                                            18f.dp.toPx() * progress * effectMultiplier,
                                        )
                                    },
                                    highlight = {
                                        val progress = dampedDragAnimation.pressProgress
                                        Highlight.Default.copy(alpha = progress)
                                    },
                                )
                                .height(currentBarHeight - 8.dp * currentScale)
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp * currentScale),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            tabs.forEachIndexed { index, _ ->
                                val bitmap = activeTabs.getOrNull(index) ?: tabs.getOrNull(index)
                                LiquidBottomTab(
                                    bitmap = bitmap,
                                    isCircular = index in circularIndices,
                                    accentColor = accentColor,
                                    label = labels.getOrNull(index),
                                    scaleFactor = currentScale,
                                    badgeCount = badgeCounts.getOrNull(index),
                                    hasBadgeDot = badgeDots.getOrNull(index) ?: false,
                                    onClick = {
                                        onTabSelected(index)
                                        dampedDragAnimation.animateToValue(index.toFloat())
                                    },
                                )
                            }
                        }
                    }

                    val pillShape = androidx.compose.foundation.shape.RoundedCornerShape(currentCornerRadius)

                    if (pillGlassIntensity > 0.001f) {
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp * currentScale)
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
                                    shape = { pillShape },
                                    effects = {
                                        val progress = dampedDragAnimation.pressProgress
                                        liquidLens(
                                            10f.dp.toPx() * progress * pillGlassIntensity,
                                            14f.dp.toPx() * progress * pillGlassIntensity,
                                            chromaticAberration = pillChromaticAberration,
                                        )
                                    },
                                    highlight = {
                                        val progress = dampedDragAnimation.pressProgress
                                        Highlight.Default.copy(alpha = progress)
                                    },
                                    shadow = {
                                        if (pillShadow) {
                                            val progress = dampedDragAnimation.pressProgress
                                            Shadow(alpha = progress)
                                        } else null
                                    },
                                    innerShadow = {
                                        if (pillShadow) {
                                            val progress = dampedDragAnimation.pressProgress
                                            InnerShadow(
                                                radius = 8f.dp * progress,
                                                alpha = progress,
                                            )
                                        } else null
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
                                        if (pillContainerOpacity > 0.001f) {
                                            drawRect(
                                                if (isLightTheme) {
                                                    Color.Black.copy(0.1f * pillContainerOpacity)
                                                } else {
                                                    Color.White.copy(0.1f * pillContainerOpacity)
                                                },
                                                alpha = 1f - progress,
                                            )
                                            drawRect(
                                                Color.Black.copy(alpha = 0.03f * progress * pillContainerOpacity)
                                            )
                                        }
                                    },
                                )
                                .height(currentBarHeight - 8.dp * currentScale)
                                .fillMaxWidth(1f / tabCount),
                        )
                    } else {
                        Box(
                            Modifier
                                .padding(horizontal = 4.dp * currentScale)
                                .graphicsLayer {
                                    translationX =
                                        if (isLtr) {
                                            dampedDragAnimation.value * tabWidthPx + panelOffset
                                        } else {
                                            size.width -
                                                (dampedDragAnimation.value + 1f) * tabWidthPx +
                                                panelOffset
                                        }
                                    scaleX = dampedDragAnimation.scaleX
                                    scaleY = dampedDragAnimation.scaleY

                                    val velocity = dampedDragAnimation.velocity / 10f
                                    scaleX /= 1f -
                                        (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                    scaleY *= 1f -
                                        (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                                    clip = true
                                    shape = pillShape
                                }
                                .then(interactiveHighlight.gestureModifier)
                                .then(dampedDragAnimation.modifier)
                                .then(
                                    if (pillContainerOpacity > 0.001f) {
                                        Modifier.background(
                                            if (isLightTheme) Color.Black.copy(0.1f * pillContainerOpacity)
                                            else Color.White.copy(0.1f * pillContainerOpacity),
                                            shape = pillShape
                                        )
                                    } else Modifier
                                )
                                .height(currentBarHeight - 8.dp * currentScale)
                                .fillMaxWidth(1f / tabCount),
                        )
                    }
                }
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
    label: String?,
    accentColor: Color? = null,
    scaleFactor: Float = 1f,
    badgeCount: String? = null,
    hasBadgeDot: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val scale = LocalLiquidBottomTabScale.current
    val isDark = isSystemInDarkTheme()
    Column(
        Modifier
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val value = scale()
                scaleX = value
                scaleY = value
            },
        verticalArrangement = Arrangement.spacedBy((2 * scaleFactor).dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (bitmap != null) {
            val baseSize = if (label.isNullOrEmpty()) 28.dp else 24.dp
            Box(contentAlignment = Alignment.BottomEnd) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    colorFilter = if (isCircular || accentColor == null) null else ColorFilter.tint(accentColor),
                    modifier = Modifier
                        .size(baseSize * scaleFactor)
                        .then(
                            if (isCircular) Modifier.clip(CircleShape) else Modifier
                        ),
                )
                if (!badgeCount.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .offset(x = (6 * scaleFactor).dp, y = (4 * scaleFactor).dp)
                            .background(Color(0xFFFF3B30), shape = RoundedCornerShape(10.dp))
                            .border((1.5f * scaleFactor).dp, if (isDark) Color(0xFF121212) else Color.White, RoundedCornerShape(10.dp))
                            .padding(horizontal = (5 * scaleFactor).dp, vertical = (1 * scaleFactor).dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.text.BasicText(
                            text = badgeCount,
                            style = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = (11 * scaleFactor).sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            ),
                        )
                    }
                } else if (hasBadgeDot) {
                    Box(
                        modifier = Modifier
                            .offset(x = (3 * scaleFactor).dp, y = (3 * scaleFactor).dp)
                            .size((9 * scaleFactor).dp)
                            .background(Color(0xFFFF3B30), shape = CircleShape)
                            .border((1.5f * scaleFactor).dp, if (isDark) Color(0xFF121212) else Color.White, CircleShape)
                    )
                }
            }
            if (!label.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height((2 * scaleFactor).dp))
                androidx.compose.foundation.text.BasicText(
                    text = label,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = (13 * scaleFactor).sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = accentColor ?: if (isDark) Color.White else Color.Black
                    ),
                    overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }
    }
}
