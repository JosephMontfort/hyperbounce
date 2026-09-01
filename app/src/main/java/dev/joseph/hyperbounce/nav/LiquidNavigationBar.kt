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
        DisposableEffect(liveBackdrop) {
            sourceView.post { liveBackdrop.start() }
            onDispose { liveBackdrop.release() }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = bottomPadding)
                .width((tabs.size * tabWidth.value).dp)
                .height(barHeight),
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
                            val index = kotlin.math.round(value)
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

                if (!liquidGlass) {
                    val barShape = RoundedCornerShape(cornerRadius)
                    val pillShape = RoundedCornerShape(cornerRadius)

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

                    Box(
                        Modifier
                            .graphicsLayer {
                                translationX = panelOffset
                            }
                            .background(classicBarBg, barShape)
                            .border(0.75.dp, classicBarBorder, barShape)
                            .then(interactiveHighlight.modifier)
                            .height(barHeight)
                            .fillMaxWidth()
                            .padding(4.dp),
                    ) {
                        // Layer 1: Inactive unaccented tab items
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
                                    onClick = {
                                        onTabSelected(index)
                                        dampedDragAnimation.animateToValue(index.toFloat())
                                    },
                                )
                            }
                        }

                        // Layer 2: Moving Tab Indicator Pill (Horizontal Bloom only)
                        Box(
                            Modifier
                                .graphicsLayer {
                                    translationX = if (isLtr) {
                                        dampedDragAnimation.value * tabWidthPx
                                    } else {
                                        (tabCount - 1 - dampedDragAnimation.value) * tabWidthPx
                                    }
                                    scaleX = dampedDragAnimation.scaleX
                                    scaleY = 1f // Horizontal bloom only in non-liquid mode
                                }
                                .then(interactiveHighlight.gestureModifier)
                                .then(dampedDragAnimation.modifier)
                                .background(classicPillBg, pillShape)
                                .border(0.5.dp, classicPillBorder, pillShape)
                                .fillMaxHeight()
                                .width(with(density) { tabWidthPx.toDp() })
                        )

                        // Layer 3: Active accented tab items, dynamically clipped strictly to the moving pill shape
                        Row(
                            Modifier
                                .fillMaxSize()
                                .clip(object : Shape {
                                    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
                                        val pillX = if (isLtr) {
                                            dampedDragAnimation.value * tabWidthPx
                                        } else {
                                            (tabCount - 1 - dampedDragAnimation.value) * tabWidthPx
                                        }
                                        val pillW = tabWidthPx * dampedDragAnimation.scaleX
                                        val pillCenter = pillX + tabWidthPx / 2f
                                        val pLeft = (pillCenter - pillW / 2f).coerceAtLeast(0f)
                                        val pRight = (pillCenter + pillW / 2f).coerceAtMost(size.width)
                                        val r = with(density) { cornerRadius.toPx() }
                                        val path = Path().apply {
                                            addRoundRect(
                                                RoundRect(
                                                    left = pLeft,
                                                    top = 0f,
                                                    right = pRight,
                                                    bottom = size.height,
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
                                    onClick = {
                                        onTabSelected(index)
                                        dampedDragAnimation.animateToValue(index.toFloat())
                                    },
                                )
                            }
                        }
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
                                shape = { androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius) },
                                effects = {
                                    vibrancy()
                                    blur(8f.dp.toPx() * effectMultiplier)
                                    lens(18f.dp.toPx() * effectMultiplier, 18f.dp.toPx() * effectMultiplier)
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
                            .height(barHeight)
                            .fillMaxWidth()
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEachIndexed { index, bitmap ->
                            LiquidBottomTab(
                                bitmap = bitmap,
                                isCircular = index in circularIndices,
                                accentColor = null,
                                label = labels.getOrNull(index),
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
                                    shape = { androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius) },
                                    effects = {
                                        val progress = dampedDragAnimation.pressProgress
                                        vibrancy()
                                        blur(8f.dp.toPx() * effectMultiplier)
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
                                .height(barHeight - 8.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            tabs.forEachIndexed { index, _ ->
                                val bitmap = activeTabs.getOrNull(index) ?: tabs.getOrNull(index)
                                LiquidBottomTab(
                                    bitmap = bitmap,
                                    isCircular = index in circularIndices,
                                    accentColor = accentColor,
                                    label = labels.getOrNull(index),
                                    onClick = {
                                        onTabSelected(index)
                                        dampedDragAnimation.animateToValue(index.toFloat())
                                    },
                                )
                            }
                        }
                    }

                    val pillShape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)

                    if (pillGlassIntensity > 0.001f) {
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
                                    shape = { pillShape },
                                    effects = {
                                        val progress = dampedDragAnimation.pressProgress
                                        lens(
                                            8f.dp.toPx() * progress * pillGlassIntensity,
                                            11f.dp.toPx() * progress * pillGlassIntensity,
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
                                .height(barHeight - 8.dp)
                                .fillMaxWidth(1f / tabCount),
                        )
                    } else {
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
                                .height(barHeight - 8.dp)
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
    onClick: () -> Unit,
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        Modifier
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
                colorFilter = if (isCircular || accentColor == null) null else ColorFilter.tint(accentColor),
                modifier = Modifier
                    .size(if (label.isNullOrEmpty()) 28.dp else 24.dp)
                    .then(
                        if (isCircular) Modifier.clip(CircleShape) else Modifier
                    ),
            )
            if (!label.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                androidx.compose.foundation.text.BasicText(
                    text = label,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                        color = accentColor ?: if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color.Black
                    ),
                    overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }
    }
}
