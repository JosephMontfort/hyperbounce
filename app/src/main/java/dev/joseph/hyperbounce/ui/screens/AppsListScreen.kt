package dev.joseph.hyperbounce.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.Destination
import dev.joseph.hyperbounce.ui.components.GlassCard
import dev.joseph.hyperbounce.ui.components.RowDivider
import dev.joseph.hyperbounce.ui.components.SectionHeader
import dev.joseph.hyperbounce.ui.components.SettingsNavRow
import dev.joseph.hyperbounce.ui.state.NavApp
import dev.joseph.hyperbounce.ui.state.SettingsRepository
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors

@Composable
fun AppsListScreen(
    repository: SettingsRepository,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onBack: () -> Unit,
    onNavigate: (Destination) -> Unit
) {
    ScreenScaffold(
        title = "Navigation Bar",
        colors = colors,
        backdrop = backdrop,
        onBack = onBack
    ) { _ ->
        item {
            SectionHeader("Apps", colors)
            GlassCard(backdrop, Modifier.padding(horizontal = 16.dp)) {
                NavApp.entries.forEachIndexed { index, app ->
                    SettingsNavRow(
                        title = app.displayName,
                        colors = colors,
                        iconTint = Color.Transparent,
                        onClick = { onNavigate(Destination.AppDetail(app)) },
                        icon = { AppIcon(app) }
                    )
                    if (index != NavApp.entries.lastIndex) {
                        RowDivider(colors.separator)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIcon(app: NavApp) {
    when (app) {
        NavApp.Instagram -> InstagramBrandIcon()
        NavApp.WhatsApp -> WhatsAppBrandIcon()
        NavApp.YouTube -> YouTubeBrandIcon()
    }
}

@Composable
fun InstagramBrandIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(29.dp)) {
        val w = size.width
        val h = size.height
        val gradient = Brush.linearGradient(
            colors = listOf(
                Color(0xFFFCAF45),
                Color(0xFFFF543E),
                Color(0xFFE1306C),
                Color(0xFFC13584),
                Color(0xFF833AB4),
                Color(0xFF405DE6)
            ),
            start = Offset(0f, h),
            end = Offset(w, 0f)
        )
        drawRoundRect(
            brush = gradient,
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
        val camSize = 14.5.dp.toPx()
        val camLeft = (w - camSize) / 2f
        val camTop = (h - camSize) / 2f
        val stroke = 1.5.dp.toPx()
        val camCorner = 4.2.dp.toPx()
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(camLeft, camTop),
            size = Size(camSize, camSize),
            cornerRadius = CornerRadius(camCorner, camCorner),
            style = Stroke(width = stroke)
        )
        val lensRadius = 3.5.dp.toPx()
        drawCircle(
            color = Color.White,
            radius = lensRadius,
            center = center,
            style = Stroke(width = stroke)
        )
        val dotRadius = 0.9.dp.toPx()
        drawCircle(
            color = Color.White,
            radius = dotRadius,
            center = Offset(camLeft + camSize - 3.2.dp.toPx(), camTop + 3.2.dp.toPx())
        )
    }
}

@Composable
fun WhatsAppBrandIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(29.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = Color(0xFF25D366),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
        // Speech bubble
        val bubblePath = Path().apply {
            val cx = w * 0.50f
            val cy = h * 0.48f
            val r = w * 0.31f
            addOval(Rect(cx - r, cy - r, cx + r, cy + r))
            // Tail at bottom left
            moveTo(cx - r * 0.65f, cy + r * 0.65f)
            lineTo(w * 0.22f, h * 0.77f)
            lineTo(cx - r * 0.15f, cy + r * 0.94f)
            close()
        }
        drawPath(bubblePath, Color.White)

        // Handset inside speech bubble
        val pw = w * 0.34f
        val ph = h * 0.34f
        val ox = w * 0.33f
        val oy = h * 0.31f
        val phonePath = Path().apply {
            moveTo(ox + pw * 0.22f, oy + ph * 0.45f)
            cubicTo(ox + pw * 0.32f, oy + ph * 0.62f, ox + pw * 0.46f, oy + ph * 0.74f, ox + pw * 0.64f, oy + ph * 0.81f)
            lineTo(ox + pw * 0.76f, oy + ph * 0.69f)
            cubicTo(ox + pw * 0.78f, oy + ph * 0.67f, ox + pw * 0.82f, oy + ph * 0.67f, ox + pw * 0.85f, oy + ph * 0.68f)
            cubicTo(ox + pw * 0.91f, oy + ph * 0.72f, ox + pw * 0.97f, oy + ph * 0.74f, ox + pw * 1.00f, oy + ph * 0.74f)
            cubicTo(ox + pw * 1.05f, oy + ph * 0.74f, ox + pw * 1.08f, oy + ph * 0.78f, ox + pw * 1.08f, oy + ph * 0.83f)
            lineTo(ox + pw * 1.08f, oy + ph * 0.96f)
            cubicTo(ox + pw * 1.08f, oy + ph * 1.01f, ox + pw * 1.04f, oy + ph * 1.05f, ox + pw * 0.99f, oy + ph * 1.05f)
            cubicTo(ox + pw * 0.44f, oy + ph * 1.05f, ox + pw * 0.00f, oy + ph * 0.61f, ox + pw * 0.00f, oy + ph * 0.06f)
            cubicTo(ox + pw * 0.00f, oy + ph * 0.01f, ox + pw * 0.04f, oy - ph * 0.03f, ox + pw * 0.09f, oy - ph * 0.03f)
            lineTo(ox + pw * 0.22f, oy - ph * 0.03f)
            cubicTo(ox + pw * 0.27f, oy - ph * 0.03f, ox + pw * 0.31f, oy + ph * 0.01f, ox + pw * 0.31f, oy + ph * 0.06f)
            cubicTo(ox + pw * 0.31f, oy + ph * 0.12f, ox + pw * 0.33f, oy + ph * 0.18f, ox + pw * 0.37f, oy + ph * 0.24f)
            cubicTo(ox + pw * 0.38f, oy + ph * 0.27f, ox + pw * 0.38f, oy + ph * 0.31f, ox + pw * 0.35f, oy + ph * 0.33f)
            close()
        }
        drawPath(
            phonePath,
            color = Color(0xFF25D366),
            style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun YouTubeBrandIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(29.dp)) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = Color(0xFFFF0000),
            cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
        )
        val playPath = Path().apply {
            val left = w * 0.39f
            val right = w * 0.67f
            val top = h * 0.33f
            val bottom = h * 0.67f
            val centerY = h * 0.50f
            moveTo(left, top)
            lineTo(right, centerY)
            lineTo(left, bottom)
            close()
        }
        drawPath(playPath, Color.White)
    }
}
