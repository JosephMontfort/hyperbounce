package dev.joseph.hyperbounce.nav

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.ui.util.lerp
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.lerp
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
    barGlassEnabled: Boolean = true,
    pillGlassEnabled: Boolean = true,
    reflectionLayerEnabled: Boolean = true,
    barBlurDp: Float = 8f,
    barLensAmount: Float = 18f,
    pillLensAmount: Float = 11f,
    pillHighlight: Float = 0.8f,
    pillShadow: Float = 0.6f,
    pillOpacity: Float = 0.1f,
    labels: List<String> = emptyList(),
    customAccentColor: Color? = null,
    barHeight: Dp = 64.dp,
    tabWidth: Dp = 64.dp,
    cornerRadius: Dp = 32.dp,
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

    val isBackdropActive = (barGlassEnabled || pillGlassEnabled) && effectMultiplier > 0.001f
    val density = LocalDensity.current
    val liveBackdrop = remember(sourceView) {
        LiquidGlassViewBackdrop(sourceView)
    }
    val capturePaddingPx = remember(density) {
        with(density) { 24.dp.toPx().toInt() }
    }

    DisposableEffect(liveBackdrop, isBackdropActive) {
        if (isBackdropActive) {
            sourceView.post { liveBackdrop.start() }
        } else {
            liveBackdrop.stop()
        }
        onDispose { liveBackdrop.release() }
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

                val barShape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)
                val pillShape = androidx.compose.foundation.shape.RoundedCornerShape(cornerRadius)

                // ==========================================
                // LAYER 1: The Bar Container Background
                // ==========================================
                Box(
                    Modifier
                        .graphicsLayer {
                            translationX = panelOffset
                        }
                        .onGloballyPositioned { coordinates ->
                            if (isBackdropActive) {
                                val position = coordinates.positionInWindow()
                                liveBackdrop.setTargetBounds(
                                    left = position.x,
                                    top = position.y,
                                    width = coordinates.size.width,
                                    height = coordinates.size.height,
                                    paddingPx = capturePaddingPx,
                                )
                            }
                        }
                        .then(
                            if (barGlassEnabled && effectMultiplier > 0.001f) {
                                Modifier.drawBackdrop(
                                    backdrop = liveBackdrop,
                                    shape = { barShape },
                                    effects = {
                                        vibrancy()
                                        blur(barBlurDp.dp.toPx() * effectMultiplier)
                                        lens(barLensAmount.dp.toPx() * effectMultiplier, barLensAmount.dp.toPx() * effectMultiplier)
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
                            } else {
                                Modifier
                                    .background(containerColor, barShape)
                                    .clip(barShape)
                            }
                        )
                        .then(interactiveHighlight.modifier)
                        .height(barHeight)
                        .fillMaxWidth()
                )

                // ==========================================
                // LAYER 2: The Non-Glass Sliding Pill (When pill glass is OFF)
                // ==========================================
                if (!pillGlassEnabled || effectMultiplier <= 0.001f) {
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
                                scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                            }
                            .then(interactiveHighlight.gestureModifier)
                            .then(dampedDragAnimation.modifier)
                            .background(
                                if (isLightTheme) {
                                    Color.Black.copy(pillOpacity)
                                } else {
                                    Color.White.copy((pillOpacity * 1.5f).coerceIn(0f, 1f))
                                },
                                shape = pillShape
                            )
                            .clip(pillShape)
                            .height(barHeight - 8.dp)
                            .fillMaxWidth(1f / tabCount)
                    )
                }

                // ==========================================
                // LAYER 3: Tabs Backdrop Layer (For Liquid Lens Refraction)
                // ==========================================
                val tabsBackdrop = rememberLayerBackdrop()
                val showLiquidPill = pillGlassEnabled && effectMultiplier > 0.001f

                if (showLiquidPill && reflectionLayerEnabled) {
                    CompositionLocalProvider(
                        LocalLiquidBottomTabScale provides {
                            lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
                        },
                    ) {
                        Row(
                            Modifier
                                .clearAndSetSemantics {}
                                .alpha(0.001f)
                                .layerBackdrop(tabsBackdrop)
                                .graphicsLayer {
                                    translationX = panelOffset
                                }
                                .height(barHeight - 8.dp)
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            tabs.forEachIndexed { index, inactiveBitmap ->
                                val activeBitmap = activeTabs.getOrNull(index) ?: inactiveBitmap
                                val highlight = (1f - abs(dampedDragAnimation.value - index) * 1.2f).coerceIn(0f, 1f)
                                LiquidBottomTab(
                                    inactiveBitmap = inactiveBitmap,
                                    activeBitmap = activeBitmap,
                                    isCircular = index in circularIndices,
                                    accentColor = accentColor,
                                    label = labels.getOrNull(index),
                                    highlightFraction = highlight,
                                    isDark = !isLightTheme,
                                    onClick = {
                                        onTabSelected(index)
                                        dampedDragAnimation.animateToValue(index.toFloat())
                                    },
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // LAYER 4: The Liquid Glass Pill (When pill glass is ON)
                // ==========================================
                if (showLiquidPill) {
                    val pillBackdrop = if (reflectionLayerEnabled) {
                        if (barGlassEnabled) rememberCombinedBackdrop(liveBackdrop, tabsBackdrop) else tabsBackdrop
                    } else {
                        if (barGlassEnabled) liveBackdrop else null
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
                            .then(
                                if (pillBackdrop != null) {
                                    Modifier.drawBackdrop(
                                        backdrop = pillBackdrop,
                                        shape = { pillShape },
                                        effects = {
                                            val progress = dampedDragAnimation.pressProgress
                                            lens(
                                                (pillLensAmount * 0.72f).dp.toPx() * progress * effectMultiplier,
                                                pillLensAmount.dp.toPx() * progress * effectMultiplier,
                                                chromaticAberration = true,
                                            )
                                        },
                                        highlight = {
                                            val progress = dampedDragAnimation.pressProgress
                                            Highlight.Default.copy(alpha = progress * pillHighlight)
                                        },
                                        shadow = {
                                            val progress = dampedDragAnimation.pressProgress
                                            Shadow(alpha = progress * pillShadow)
                                        },
                                        innerShadow = {
                                            val progress = dampedDragAnimation.pressProgress
                                            InnerShadow(
                                                radius = 8f.dp * progress,
                                                alpha = progress * pillShadow,
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
                                            if (!barGlassEnabled) {
                                                drawRect(containerColor)
                                            }
                                            drawRect(
                                                if (isLightTheme) {
                                                    Color.Black.copy(pillOpacity)
                                                } else {
                                                    Color.White.copy(pillOpacity)
                                                },
                                                alpha = 1f - progress,
                                            )
                                            drawRect(
                                                Color.Black.copy(alpha = 0.03f * progress)
                                            )
                                        },
                                    )
                                } else {
                                    Modifier
                                        .graphicsLayer {
                                            scaleX = dampedDragAnimation.scaleX
                                            scaleY = dampedDragAnimation.scaleY

                                            val velocity = dampedDragAnimation.velocity / 10f
                                            scaleX /= 1f -
                                                (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                                            scaleY *= 1f -
                                                (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                                        }
                                        .background(
                                            if (isLightTheme) {
                                                Color.Black.copy(pillOpacity)
                                            } else {
                                                Color.White.copy((pillOpacity * 1.5f).coerceIn(0f, 1f))
                                            },
                                            shape = pillShape
                                        )
                                        .clip(pillShape)
                                }
                            )
                            .height(barHeight - 8.dp)
                            .fillMaxWidth(1f / tabCount),
                    )
                }

                // ==========================================
                // LAYER 5: The Base Interactive Tab Icons
                // ==========================================
                Row(
                    Modifier
                        .graphicsLayer {
                            translationX = panelOffset
                        }
                        .height(barHeight - 8.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { index, inactiveBitmap ->
                        val activeBitmap = activeTabs.getOrNull(index) ?: inactiveBitmap
                        val highlight = (1f - abs(dampedDragAnimation.value - index) * 1.2f).coerceIn(0f, 1f)
                        LiquidBottomTab(
                            inactiveBitmap = inactiveBitmap,
                            activeBitmap = activeBitmap,
                            isCircular = index in circularIndices,
                            accentColor = accentColor,
                            label = labels.getOrNull(index),
                            highlightFraction = highlight,
                            isDark = !isLightTheme,
                            onClick = {
                                onTabSelected(index)
                                dampedDragAnimation.animateToValue(index.toFloat())
                            },
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
    inactiveBitmap: Bitmap?,
    activeBitmap: Bitmap?,
    isCircular: Boolean,
    label: String?,
    accentColor: Color,
    highlightFraction: Float,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val scale = LocalLiquidBottomTabScale.current
    val inactiveTint = if (isDark) Color(0xB3FFFFFF) else Color(0x99000000)
    val currentTint = lerp(inactiveTint, accentColor, highlightFraction)

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
                val extraScale = lerp(1f, 1.08f, highlightFraction)
                scaleX = value * extraScale
                scaleY = value * extraScale
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bmp = if (highlightFraction > 0.5f) (activeBitmap ?: inactiveBitmap) else (inactiveBitmap ?: activeBitmap)
        if (bmp != null) {
            Box(contentAlignment = Alignment.Center) {
                if (inactiveBitmap != null && activeBitmap != null && inactiveBitmap != activeBitmap) {
                    if (highlightFraction < 0.99f) {
                        Image(
                            bitmap = inactiveBitmap.asImageBitmap(),
                            contentDescription = null,
                            colorFilter = if (isCircular) null else ColorFilter.tint(inactiveTint),
                            alpha = (1f - highlightFraction).coerceIn(0f, 1f),
                            modifier = Modifier
                                .size(if (label.isNullOrEmpty()) 28.dp else 24.dp)
                                .then(
                                    if (isCircular) Modifier.clip(CircleShape) else Modifier
                                ),
                        )
                    }
                    if (highlightFraction > 0.01f) {
                        Image(
                            bitmap = activeBitmap.asImageBitmap(),
                            contentDescription = null,
                            colorFilter = if (isCircular) null else ColorFilter.tint(accentColor),
                            alpha = highlightFraction.coerceIn(0f, 1f),
                            modifier = Modifier
                                .size(if (label.isNullOrEmpty()) 28.dp else 24.dp)
                                .then(
                                    if (isCircular) Modifier.clip(CircleShape) else Modifier
                                ),
                        )
                    }
                } else {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = null,
                        colorFilter = if (isCircular) null else ColorFilter.tint(currentTint),
                        modifier = Modifier
                            .size(if (label.isNullOrEmpty()) 28.dp else 24.dp)
                            .then(
                                if (isCircular) Modifier.clip(CircleShape) else Modifier
                            ),
                    )
                }
            }
            if (!label.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                androidx.compose.foundation.text.BasicText(
                    text = label,
                    style = androidx.compose.ui.text.TextStyle(
                        fontSize = 12.sp,
                        fontWeight = if (highlightFraction > 0.5f) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal,
                        color = currentTint
                    ),
                    overflow = androidx.compose.ui.text.style.TextOverflow.Visible,
                    softWrap = false,
                    maxLines = 1
                )
            }
        }
    }
}
