package dev.joseph.hyperbounce

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

internal val LocalLiquidBottomTabScale = staticCompositionLocalOf { { 1f } }
val LocalTabColor = staticCompositionLocalOf { Color.Unspecified }

@Composable
fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val s = scale()
                scaleX = s
                scaleY = s
            },
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    
    // Theme-aware colors
    val containerColor = Color.Transparent
    val inactiveColor = if (isLightTheme) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)
    val activeColor = if (isLightTheme) Color.Black else Color.White

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(modifier, contentAlignment = Alignment.CenterStart) {
        val density = LocalDensity.current
        val tabWidth = with(density) { (constraints.maxWidth.toFloat() - 8.dp.toPx()) / tabsCount }
        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) { 4.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction)) }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) { mutableIntStateOf(selectedTabIndex()) }
        
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch { offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f)) }
                },
                onDrag = { _, dragAmount ->
                    updateValue((targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f).fastCoerceIn(0f, (tabsCount - 1).toFloat()))
                    animationScope.launch { offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x) }
                }
            )
        }
        
        LaunchedEffect(selectedTabIndex) { snapshotFlow { selectedTabIndex() }.collectLatest { index -> currentIndex = index } }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }.drop(1).collectLatest { index ->
                dampedDragAnimation.animateToValue(index.toFloat())
                onTabSelected(index)
            }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset, size.height / 2f)
                }
            )
        }

        // BACKGROUND ROW (Inactive Icons)
        CompositionLocalProvider(LocalTabColor provides inactiveColor) {
            Row(
                Modifier
                    .graphicsLayer { translationX = panelOffset }
                    .then(interactiveHighlight.modifier)
                    .height(64.dp)
                    .fillMaxWidth()
                    .padding(4.dp), // CRITICAL: Padding matches foreground exactly to prevent doubling
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        // FOREGROUND ROW (Active Icons - only visible through the liquid pill)
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides { lerp(1f, 1.2f, dampedDragAnimation.pressProgress) },
            LocalTabColor provides activeColor
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f) // Keep invisible until captured
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer { translationX = panelOffset }
                    .then(interactiveHighlight.modifier)
                    .height(64.dp)
                    .fillMaxWidth()
                    .padding(4.dp), // CRITICAL: Padding matches background exactly to prevent doubling
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        // LIQUID INDICATOR PILL (The Lens that reveals the foreground)
        Box(
            Modifier
                .padding(4.dp) // Offsets the pill to match the inner bounds of the rows
                .graphicsLayer { translationX = if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(10.dp.toPx() * progress, 14.dp.toPx() * progress, chromaticAberration = true)
                    },
                    highlight = { Highlight.Default.copy(alpha = dampedDragAnimation.pressProgress) },
                    shadow = { Shadow(alpha = dampedDragAnimation.pressProgress) },
                    innerShadow = { InnerShadow(radius = 8.dp * dampedDragAnimation.pressProgress, alpha = dampedDragAnimation.pressProgress) },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX; scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        val frostedAlpha = if (isLightTheme) 0.35f else 0.25f
                        drawRect(Color.White.copy(alpha = frostedAlpha), alpha = 1f - progress)
                        drawRect(Color.White.copy(alpha = frostedAlpha + 0.15f * progress))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}
