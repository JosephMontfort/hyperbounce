package dev.hyperios.app.ui.glass

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/**
 * Circular Liquid Glass Button powered directly by Kyant's AndroidLiquidGlass 2.0.0 backdrop library.
 * Delegates to LiquidButton with fixed circular size and Capsule geometry.
 */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    tint: Color,
    modifier: Modifier = Modifier,
    surfaceColor: Color = Color.Unspecified,
    size: Dp = 32.dp,
    content: @Composable RowScope.() -> Unit
) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier,
        tint = tint,
        surfaceColor = surfaceColor,
        size = size,
        content = content
    )
}
