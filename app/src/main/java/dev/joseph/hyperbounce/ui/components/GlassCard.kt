package dev.joseph.hyperbounce.ui.components

/*
 * A flat, solid-fill card used to group settings rows — iOS's grouped-table-view section
 * card. Per the app's aesthetic direction, glass/backdrop is reserved for toggles,
 * sliders, and the dropdown/popup menus only, so this is deliberately a plain fill, not a
 * blurred surface.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

@Composable
fun GlassCard(
    // Kept for call-site compatibility with every screen in the app (all of which already
    // pass their shared backdrop here) even though this card is now a flat fill —
    // harmless to keep threading it in case that changes again.
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val isDark = isSystemInDarkTheme()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (isDark) Color(0xFF1C1C1E) else Color.White),
        content = content
    )
}

/** A faint 1px inset divider between rows inside a [GlassCard]. */
@Composable
fun RowDivider(color: Color, insetStart: Dp = 56.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = insetStart)
    ) {
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(0.75.dp)
                .background(color)
        )
    }
}
