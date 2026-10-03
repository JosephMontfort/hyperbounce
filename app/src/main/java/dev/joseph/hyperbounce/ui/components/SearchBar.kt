package dev.joseph.hyperbounce.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType

/**
 * HyperLight / iOS aesthetic search bar with spring bounce dynamics:
 * - Shrinks horizontally when active with increased bounce
 * - Springs in the "Cancel" action button
 * - Clear ('X') button appears when query is present
 */
@Composable
fun HyperLightSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    colors: HyperBounceColors,
    modifier: Modifier = Modifier,
    placeholder: String = "Search"
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val isDark = isSystemInDarkTheme()

    val isActive = isFocused || query.isNotEmpty()

    val searchBarBg = if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    val iconColor = colors.secondaryLabel.copy(alpha = 0.6f)
    val textStyle = TextStyle(
        color = colors.label,
        fontSize = 15.sp
    )

    // Increased bounce spring spec for lively tactile feel
    val bounceSpec = spring<Float>(
        dampingRatio = 0.65f,
        stiffness = 340f
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(searchBarBg)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    focusRequester.requestFocus()
                }
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search icon
                androidx.compose.foundation.Canvas(modifier = Modifier.size(16.dp)) {
                    val w = size.width
                    val h = size.height
                    val r = w * 0.32f
                    val cx = w * 0.42f
                    val cy = h * 0.42f
                    drawCircle(
                        color = iconColor,
                        radius = r,
                        center = androidx.compose.ui.geometry.Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.8f.dp.toPx())
                    )
                    drawLine(
                        color = iconColor,
                        start = androidx.compose.ui.geometry.Offset(cx + r * 0.7f, cy + r * 0.7f),
                        end = androidx.compose.ui.geometry.Offset(w * 0.92f, h * 0.92f),
                        strokeWidth = 2.2f.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        BasicText(
                            placeholder,
                            style = HyperBounceType.body.copy(color = colors.tertiaryLabel)
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFocused = it.isFocused },
                        textStyle = textStyle,
                        singleLine = true,
                        cursorBrush = SolidColor(colors.accentBlue)
                    )
                }

                // Clear button
                if (query.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(colors.secondaryLabel.copy(alpha = 0.35f))
                            .clickable { onQueryChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.size(10.dp)) {
                            val stroke = 1.6f.dp.toPx()
                            drawLine(
                                color = Color.White,
                                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                                end = androidx.compose.ui.geometry.Offset(size.width, size.height),
                                strokeWidth = stroke,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            drawLine(
                                color = Color.White,
                                start = androidx.compose.ui.geometry.Offset(size.width, 0f),
                                end = androidx.compose.ui.geometry.Offset(0f, size.height),
                                strokeWidth = stroke,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }

        // Animated Cancel button reveal with simultaneous shrinking of search bar (HyperLight style)
        AnimatedVisibility(
            visible = isActive,
            enter = expandHorizontally(
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 400f),
                expandFrom = Alignment.End
            ) + fadeIn(spring(stiffness = 400f)),
            exit = shrinkHorizontally(
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 400f),
                shrinkTowards = Alignment.End
            ) + fadeOut(spring(stiffness = 400f))
        ) {
            BasicText(
                "Cancel",
                modifier = Modifier
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onQueryChange("")
                        focusManager.clearFocus()
                    }
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                style = HyperBounceType.body.copy(color = colors.accentBlue)
            )
        }
    }
}
