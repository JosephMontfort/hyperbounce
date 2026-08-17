package dev.joseph.hyperbounce.nav

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.lerp
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
import dev.joseph.hyperbounce.nav.glass.LiquidGlassDampedDragAnimation
import dev.joseph.hyperbounce.nav.glass.LiquidGlassInteractiveHighlight
import dev.joseph.hyperbounce.nav.glass.LiquidGlassViewBackdrop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

enum class LiquidNavContainerMode {
    Translucent,
    FullBackdrop
}

data class LiquidNavGlassConfig(
    val lensRefractionHeight: Dp = 8.dp,
    val lensRefractionAmount: Dp = 11.dp,
    val chromaticAberration: Boolean = true,
    val depthEffect: Boolean = false,
    val highlight: Highlight = Highlight.Default,
    val shadow: Shadow = Shadow.Default,
    val innerShadow: InnerShadow = InnerShadow(radius = 8.dp),
)

data class LiquidNavConfig(
    val containerMode: LiquidNavContainerMode = LiquidNavContainerMode.FullBackdrop,
    val containerAlpha: Float = 0.4f,
    val lightContainerColor: Color = Color(0xFFFAFAFA),
    val darkContainerColor: Color = Color(0xFF121212),
    val restingIndicatorColorLight: Color = Color.Black.copy(alpha = 0.1f),
    val restingIndicatorColorDark: Color = Color.White.copy(alpha = 0.1f),
    val activeGlassConfig: LiquidNavGlassConfig = LiquidNavGlassConfig(),
    val pressedScale: Float = 78f / 56f,
) {
    companion object {
        val Default = LiquidNavConfig()

        val Instagram = LiquidNavConfig(
            containerMode = LiquidNavContainerMode.Translucent,
            containerAlpha = 0.88f,
            lightContainerColor = Color(0xFFF9F9F9),
            darkContainerColor = Color(0xFF121212),
            restingIndicatorColorLight = Color.Black.copy(alpha = 0.07f),
            restingIndicatorColorDark = Color.White.copy(alpha = 0.14f),
            activeGlassConfig = LiquidNavGlassConfig(
                lensRefractionHeight = 8.dp,
                lensRefractionAmount = 11.dp,
                chromaticAberration = true,
                innerShadow = InnerShadow(radius = 6.dp, alpha = 0.75f)
            )
        )

        val YouTube = LiquidNavConfig(
            containerMode = LiquidNavContainerMode.Translucent,
            containerAlpha = 0.88f,
            lightContainerColor = Color(0xFFF9F9F9),
            darkContainerColor = Color(0xFF121212),
            restingIndicatorColorLight = Color.Black.copy(alpha = 0.07f),
            restingIndicatorColorDark = Color.White.copy(alpha = 0.14f),
            activeGlassConfig = LiquidNavGlassConfig(
                lensRefractionHeight = 8.dp,
                lensRefractionAmount = 11.dp,
                chromaticAberration = true,
                innerShadow = InnerShadow(radius = 6.dp, alpha = 0.75f)
            )
        )
    }
}

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
    containerAlpha: Float? = null,
    effectMultiplier: Float = 1f,
    labels: List<String> = emptyList(),
    customAccentColor: Color? = null,
    barHeight: Dp = 64.dp,
    tabWidth: Dp = 64.dp,
    cornerRadius: Dp = 32.dp,
    config: LiquidNavConfig = LiquidNavConfig.Default,
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

    val effectiveAlpha = containerAlpha ?: config.containerAlpha
    val containerColor = (
        if (isLightTheme) config.lightContainerColor
        else config.darkContainerColor
    ).copy(alpha = effectiveAlpha)

    val restingIndicatorColor = if (isLightTheme) {
        config.restingIndicatorColorLight
    } else {
        config.restingIndicatorColorDark
    }

    val density = LocalDensity.current
    val liveBackdrop = remember(sourceView) {
        LiquidGlassViewBackdrop(sourceView)
    }
    val capturePaddingPx = remember(density) {
        with(density) { 24.dp.toPx().toInt() }
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
                        pressedScale = config.pressedScale,
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

                val navShape = RoundedCornerShape(cornerRadius)

                val containerModifier = Modifier
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
                    .then(
                        if (config.containerMode == LiquidNavContainerMode.FullBackdrop) {
                            Modifier.drawBackdrop(
                                backdrop = liveBackdrop,
                                shape = { navShape },
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
                        } else {
                            Modifier
                                .clip(navShape)
                                .background(containerColor)
                                .border(
                                    0.5.dp,
                                    if (isLightTheme) Color.Black.copy(alpha = 0.06f) else Color.White.copy(alpha = 0.12f),
                                    navShape
                                )
                        }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(barHeight)
                    .fillMaxWidth()
                    .padding(4.dp)

                Row(
                    modifier = containerModifier,
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
                            .then(
                                if (config.containerMode == LiquidNavContainerMode.FullBackdrop) {
                                    Modifier.drawBackdrop(
                                        backdrop = liveBackdrop,
                                        shape = { navShape },
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
                                        onDrawSurface = { /* Empty Surface */ }
                                    )
                                } else {
                                    Modifier
                                }
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

                val pressProgress = dampedDragAnimation.pressProgress
                val useBackdrop = config.containerMode == LiquidNavContainerMode.FullBackdrop || pressProgress > 0f

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
                            if (useBackdrop) {
                                Modifier.drawBackdrop(
                                    backdrop = rememberCombinedBackdrop(
                                        liveBackdrop,
                                        tabsBackdrop,
                                    ),
                                    shape = { navShape },
                                    effects = {
                                        val glassCfg = config.activeGlassConfig
                                        lens(
                                            glassCfg.lensRefractionHeight.toPx() * pressProgress * effectMultiplier,
                                            glassCfg.lensRefractionAmount.toPx() * pressProgress * effectMultiplier,
                                            chromaticAberration = glassCfg.chromaticAberration,
                                            depthEffect = glassCfg.depthEffect
                                        )
                                    },
                                    highlight = {
                                        val glassCfg = config.activeGlassConfig
                                        glassCfg.highlight.copy(alpha = pressProgress * glassCfg.highlight.alpha)
                                    },
                                    shadow = {
                                        val glassCfg = config.activeGlassConfig
                                        glassCfg.shadow.copy(alpha = pressProgress * glassCfg.shadow.alpha)
                                    },
                                    innerShadow = {
                                        val glassCfg = config.activeGlassConfig
                                        InnerShadow(
                                            radius = glassCfg.innerShadow.radius * pressProgress,
                                            alpha = pressProgress * glassCfg.innerShadow.alpha,
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
                                        drawRect(
                                            if (isLightTheme) {
                                                Color.Black.copy(0.1f)
                                            } else {
                                                Color.White.copy(0.1f)
                                            },
                                            alpha = 1f - pressProgress,
                                        )
                                        drawRect(
                                            Color.Black.copy(alpha = 0.03f * pressProgress)
                                        )
                                    },
                                )
                            } else {
                                Modifier
                                    .clip(navShape)
                                    .background(restingIndicatorColor)
                            }
                        )
                        .height(barHeight - 8.dp)
                        .fillMaxWidth(1f / tabCount)
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
