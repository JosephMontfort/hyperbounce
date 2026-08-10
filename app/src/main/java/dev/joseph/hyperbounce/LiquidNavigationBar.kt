package dev.joseph.hyperbounce

import android.graphics.Bitmap
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Purely visual "Liquid Glass" floating navigation pill.
 *
 * This composable has no knowledge of which host app it is running in, and no
 * knowledge of how [tabs] were extracted. All app-specific view-tree walking,
 * tab detection, and click dispatch happens in the per-app *NavBarHost
 * composables (see [InstagramNavBarHost], [WhatsAppNavBarHost], [YouTubeNavBarHost])
 * which call this and supply its dynamic state.
 *
 * @param tabs bitmap per tab slot, in display order. A null entry renders an
 *   empty (but still tappable) slot rather than collapsing the layout.
 * @param circularIndices indices that should be clipped to a circle instead of
 *   receiving the standard tint treatment (e.g. a profile-picture tab).
 */
@Composable
fun LiquidNavigationBar(
    bottomPadding: Dp,
    isDarkTheme: Boolean,
    tabs: List<Bitmap?>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    circularIndices: Set<Int> = emptySet(),
) {
    val containerColor = if (isDarkTheme) Color(0xE6121212) else Color(0xE6FAFAFA)
    val accentColor = if (isDarkTheme) Color.White else Color.Black
    val borderColor = if (isDarkTheme) Color.White.copy(alpha = 0.25f) else Color.Black.copy(alpha = 0.20f)
    val indicatorColor = if (isDarkTheme) Color(0x33FFFFFF) else Color(0x1A000000)
    val pillShape = RoundedCornerShape(50)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .padding(bottom = bottomPadding)
                .width(320.dp)
                .height(64.dp)
                .background(containerColor, pillShape)
                .border(1.dp, borderColor, pillShape),
        ) {
            if (tabs.isNotEmpty()) {
                val density = LocalDensity.current
                val tabWidthDp = 320f / tabs.size
                val tabWidthPx = with(density) { tabWidthDp.dp.toPx() }

                var isPressed by remember { mutableStateOf(false) }
                var dragOffset by remember { mutableFloatStateOf(0f) }

                val targetOffset = if (isPressed) dragOffset else selectedIndex * tabWidthPx
                val animatedOffsetPx by animateFloatAsState(
                    targetValue = targetOffset,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
                    label = "offset",
                )

                val animatedWidthDp by animateFloatAsState(
                    targetValue = if (isPressed) tabWidthDp * 1.6f else tabWidthDp,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
                    label = "width",
                )

                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(animatedOffsetPx.roundToInt(), 0) }
                            .width(animatedWidthDp.dp)
                            .fillMaxHeight()
                            .padding(6.dp)
                            .clip(pillShape)
                            .background(indicatorColor),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { _ ->
                                    isPressed = true
                                    dragOffset = selectedIndex * tabWidthPx
                                },
                                onDragEnd = {
                                    isPressed = false
                                    val nearestIndex = (dragOffset / tabWidthPx)
                                        .roundToInt()
                                        .coerceIn(0, tabs.size - 1)
                                    onTabSelected(nearestIndex)
                                },
                                onDragCancel = { isPressed = false },
                                onDrag = { change, dragAmount ->
                                    dragOffset = (dragOffset + dragAmount.x)
                                        .coerceIn(0f, (tabs.size - 1) * tabWidthPx)
                                    change.consume()
                                },
                            )
                        },
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { index, bitmap ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    onTabSelected(index)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (bitmap != null) {
                                val scale by animateFloatAsState(
                                    targetValue = if (selectedIndex == index) 1.15f else 1.0f,
                                    animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
                                    label = "scale",
                                )

                                var iconModifier = Modifier
                                    .size(26.dp)
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    }

                                val isCircular = index in circularIndices
                                if (isCircular) {
                                    iconModifier = iconModifier.clip(CircleShape)
                                }

                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    colorFilter = if (isCircular) {
                                        null
                                    } else {
                                        ColorFilter.tint(
                                            if (selectedIndex == index) accentColor else accentColor.copy(alpha = 0.6f),
                                        )
                                    },
                                    modifier = iconModifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
