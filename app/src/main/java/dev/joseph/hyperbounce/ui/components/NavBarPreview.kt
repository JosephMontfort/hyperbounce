package dev.joseph.hyperbounce.ui.components

/*
 * A live-updating miniature of the floating nav bar, styled from the same
 * containerOpacity/cornerRadius/barHeight/tabWidth/glassIntensity values the sliders on
 * AppDetailScreen edit — so dragging a slider visibly changes this preview in real time,
 * the same way iOS's Settings > Display & Brightness > Text Size screen previews live.
 * Rendered as a flat tinted bar (glass/backdrop is reserved for toggles, sliders, and the
 * dropdown/popup menus in this app), with the tint alpha standing in for containerOpacity
 * and a subtle scale-with-glassIntensity so both sliders still visibly do something here.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

@Composable
fun NavBarPreview(
    // Kept for call-site compatibility (unused now that this preview is a flat fill).
    backdrop: Backdrop,
    accentColor: Color,
    glassIntensity: Float,
    containerOpacity: Float,
    cornerRadiusDp: Float,
    barHeightDp: Float,
    tabWidthDp: Float,
    tabCount: Int = 4,
    selectedIndex: Int = 1,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val shape = RoundedCornerShape(cornerRadiusDp.dp)
        Row(
            Modifier
                .height(barHeightDp.dp)
                .clip(shape)
                .background(Color(0xFF3A3A3C).copy(alpha = (0.35f + containerOpacity * 0.5f).coerceIn(0f, 1f)))
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(tabCount) { index ->
                Box(
                    Modifier
                        .size(width = tabWidthDp.dp, height = (barHeightDp - 16f).dp.coerceAtLeast(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size((7f + glassIntensity * 2f).dp)
                            .clip(CircleShape)
                            .background(
                                if (index == selectedIndex) accentColor else Color.White.copy(alpha = 0.55f)
                            )
                    )
                }
            }
        }
    }
}
