package dev.joseph.hyperbounce.ui.components

/*
 * Ported from the user-authored ExpandableGlassMenuContent catalog page's MenuRow —
 * the touch-position-based hover detection (boundsInWindow().contains(globalTouchPosition)
 * while dragging), the hover-alpha animation, and click handling are unchanged.
 *
 * One necessary adaptation: the source takes `icon: ImageVector` (via
 * rememberVectorPainter + ColorFilter.tint), but this project has no icon-font/vector-icon
 * dependency — every icon here is hand-drawn Canvas (see ui/components/Icons.kt). So `icon`
 * is a composable slot instead; everything else is unchanged.
 */

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MenuRow(
    title: String,
    description: String,
    contentColor: Color,
    secondaryColor: Color,
    isHovered: Boolean,
    onHoverChange: (Boolean) -> Unit,
    globalTouchPosition: Offset,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    var rowCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    val contains = remember(globalTouchPosition, rowCoords) {
        if (globalTouchPosition.isUnspecified || rowCoords == null) false
        else rowCoords!!.boundsInWindow().contains(globalTouchPosition)
    }

    LaunchedEffect(contains) { onHoverChange(contains) }

    val hoverAlpha by animateFloatAsState(if (isHovered) 0.1f else 0f, tween(150))

    Row(
        modifier = Modifier.fillMaxWidth().onGloballyPositioned { rowCoords = it }.clip(RoundedCornerShape(14.dp)).clickable { onClick() }.background(contentColor.copy(alpha = hoverAlpha)).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            BasicText(title, style = TextStyle(contentColor, 15.sp, FontWeight.SemiBold))
            BasicText(description, style = TextStyle(secondaryColor, 12.sp), maxLines = 1)
        }
    }
}
