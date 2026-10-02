package dev.joseph.hyperbounce.ui.components

/*
 * This project has no icon-font/vector-icon dependency (only backdrop/shapes + Compose
 * foundation are on the classpath), so the handful of glyphs the settings UI needs are
 * hand-drawn here with Canvas/Path — sized and stroked to read as SF Symbols-style thin/
 * medium-weight line icons, matching iOS 26's system iconography as closely as a hand-
 * drawn path reasonably can without shipping Apple's actual glyphs.
 */

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ChevronIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 14.dp,
    strokeWidth: Dp = 2f.dp,
    pointingLeft: Boolean = true
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            if (pointingLeft) {
                moveTo(w * 0.68f, h * 0.08f)
                lineTo(w * 0.28f, h * 0.5f)
                lineTo(w * 0.68f, h * 0.92f)
            } else {
                moveTo(w * 0.32f, h * 0.08f)
                lineTo(w * 0.72f, h * 0.5f)
                lineTo(w * 0.32f, h * 0.92f)
            }
        }
        drawPath(
            path,
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun CheckmarkIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 16.dp,
    strokeWidth: Dp = 2.2f.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.18f, h * 0.54f)
            lineTo(w * 0.42f, h * 0.78f)
            lineTo(w * 0.84f, h * 0.26f)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(
                width = strokeWidth.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
fun ResetArrowIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 18.dp,
    strokeWidth: Dp = 2f.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.34f
        val path = Path().apply {
            addArc(
                androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = -50f,
                sweepAngleDegrees = 270f
            )
        }
        drawPath(
            path,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
        // arrowhead at the open end (angle -50deg on the circle)
        val angle = Math.toRadians(-50.0)
        val tipX = cx + r * cos(angle).toFloat()
        val tipY = cy + r * sin(angle).toFloat()
        val headPath = Path().apply {
            moveTo(tipX - w * 0.14f, tipY - h * 0.05f)
            lineTo(tipX, tipY)
            lineTo(tipX - w * 0.02f, tipY + h * 0.16f)
        }
        drawPath(
            headPath,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
fun InfoIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 18.dp,
    strokeWidth: Dp = 2f.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawCircle(
            color = color,
            radius = w * 0.04f,
            center = Offset(w / 2f, h * 0.28f)
        )
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.44f)
            lineTo(w * 0.5f, h * 0.82f)
        }
        drawPath(
            path,
            color = color,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

val SlidersImageVector: ImageVector = ImageVector.Builder(
    name = "Sliders",
    defaultWidth = 20.dp,
    defaultHeight = 20.dp,
    viewportWidth = 20f,
    viewportHeight = 20f
).apply {
    path(
        stroke = SolidColor(Color.White),
        strokeLineWidth = 1.4f,
        strokeLineCap = StrokeCap.Round
    ) {
        moveTo(2f, 5f)
        horizontalLineTo(18f)
        moveTo(2f, 10f)
        horizontalLineTo(18f)
        moveTo(2f, 15f)
        horizontalLineTo(18f)
    }
    path(
        fill = SolidColor(Color.White)
    ) {
        moveTo(9f, 3f)
        curveTo(10.1f, 3f, 11f, 3.9f, 11f, 5f)
        curveTo(11f, 6.1f, 10.1f, 7f, 9f, 7f)
        curveTo(7.9f, 7f, 7f, 6.1f, 7f, 5f)
        curveTo(7f, 3.9f, 7.9f, 3f, 9f, 3f)
        close()

        moveTo(13f, 8f)
        curveTo(14.1f, 8f, 15f, 8.9f, 15f, 10f)
        curveTo(15f, 11.1f, 14.1f, 12f, 13f, 12f)
        curveTo(11.9f, 12f, 11f, 11.1f, 11f, 10f)
        curveTo(11f, 8.9f, 11.9f, 8f, 13f, 8f)
        close()

        moveTo(7f, 13f)
        curveTo(8.1f, 13f, 9f, 13.9f, 9f, 15f)
        curveTo(9f, 16.1f, 8.1f, 17f, 7f, 17f)
        curveTo(5.9f, 17f, 5f, 16.1f, 5f, 15f)
        curveTo(5f, 13.9f, 5.9f, 13f, 7f, 13f)
        close()
    }
}.build()

@Composable
fun SlidersIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 18.dp,
    strokeWidth: Dp = 1.8f.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val rows = listOf(0.28f, 0.5f, 0.72f)
        val knobs = listOf(0.32f, 0.66f, 0.46f)
        rows.forEachIndexed { i, yFrac ->
            val y = h * yFrac
            drawLine(
                color = color.copy(alpha = 0.45f),
                start = Offset(w * 0.08f, y),
                end = Offset(w * 0.92f, y),
                strokeWidth = strokeWidth.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = color,
                radius = w * 0.07f,
                center = Offset(w * knobs[i], y)
            )
        }
    }
}

@Composable
fun AppGridIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 18.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cell = w * 0.34f
        val gap = w * 0.1f
        val radius = CornerRadius(w * 0.12f, h * 0.12f)
        for (row in 0..1) {
            for (col in 0..1) {
                val x = col * (cell + gap)
                val y = row * (cell + gap)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(cell, cell),
                    cornerRadius = radius
                )
            }
        }
    }
}

@Composable
fun PowerIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 18.dp,
    strokeWidth: Dp = 2f.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val r = w * 0.32f
        val arc = Path().apply {
            addArc(
                androidx.compose.ui.geometry.Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = -240f,
                sweepAngleDegrees = 300f
            )
        }
        drawPath(arc, color = color, style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round))
        drawLine(
            color = color,
            start = Offset(cx, h * 0.12f),
            end = Offset(cx, cy),
            strokeWidth = strokeWidth.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun GearIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 18.dp,
    strokeWidth: Dp = 1.8.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        val cy = h / 2f
        val outerR = w * 0.38f
        val innerR = w * 0.16f
        val toothLen = w * 0.1f
        val teeth = 8
        for (i in 0 until teeth) {
            val angle = Math.toRadians((360.0 / teeth) * i)
            val cos = kotlin.math.cos(angle).toFloat()
            val sin = kotlin.math.sin(angle).toFloat()
            drawLine(
                color = color,
                start = Offset(cx + outerR * cos, cy + outerR * sin),
                end = Offset(cx + (outerR + toothLen) * cos, cy + (outerR + toothLen) * sin),
                strokeWidth = strokeWidth.toPx() * 1.4f,
                cap = StrokeCap.Round
            )
        }
        drawCircle(
            color = color,
            radius = outerR,
            center = Offset(cx, cy),
            style = Stroke(width = strokeWidth.toPx())
        )
        drawCircle(
            color = color,
            radius = innerR,
            center = Offset(cx, cy),
            style = Stroke(width = strokeWidth.toPx())
        )
    }
}

@Composable
fun CopyIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 16.dp,
    strokeWidth: Dp = 1.6.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.06f, h * 0.06f),
            size = Size(w * 0.62f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.1f, h * 0.1f),
            style = Stroke(width = strokeWidth.toPx())
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.32f, h * 0.32f),
            size = Size(w * 0.62f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.1f, h * 0.1f),
            style = Stroke(width = strokeWidth.toPx())
        )
    }
}

/** A faint dashed baseline used behind some preview strips. */
fun dashedStroke(width: Dp, dp: Dp = 6f.dp, gap: Dp = 6f.dp) = Stroke(
    width = width.value,
    cap = StrokeCap.Round,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dp.value, gap.value))
)

@Composable
fun AppsIcon(color: Color, size: Dp = 24.dp, strokeWidth: Dp = 2.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = strokeWidth.toPx()
        val side = (this.size.width - s) / 2f
        
        // Top-left
        drawRoundRect(
            color = color,
            topLeft = Offset(s/2, s/2),
            size = Size(side - s/2, side - s/2),
            cornerRadius = CornerRadius(s, s),
            style = Stroke(s)
        )
        // Top-right
        drawRoundRect(
            color = color,
            topLeft = Offset(this.size.width/2 + s/2, s/2),
            size = Size(side - s/2, side - s/2),
            cornerRadius = CornerRadius(s, s),
            style = Stroke(s)
        )
        // Bottom-left
        drawRoundRect(
            color = color,
            topLeft = Offset(s/2, this.size.height/2 + s/2),
            size = Size(side - s/2, side - s/2),
            cornerRadius = CornerRadius(s, s),
            style = Stroke(s)
        )
        // Bottom-right
        drawRoundRect(
            color = color,
            topLeft = Offset(this.size.width/2 + s/2, this.size.height/2 + s/2),
            size = Size(side - s/2, side - s/2),
            cornerRadius = CornerRadius(s, s),
            style = Stroke(s)
        )
    }
}

@Composable
fun LockIcon(color: Color, size: Dp = 24.dp, strokeWidth: Dp = 2.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val s = strokeWidth.toPx()
        val bodyWidth = this.size.width * 0.7f
        val bodyHeight = this.size.height * 0.5f
        val shackleRadius = bodyWidth * 0.35f
        val shackleYOffset = this.size.height * 0.15f
        
        val bodyTop = this.size.height - bodyHeight
        
        // Body
        drawRoundRect(
            color = color,
            topLeft = Offset((this.size.width - bodyWidth) / 2f, bodyTop),
            size = Size(bodyWidth, bodyHeight),
            cornerRadius = CornerRadius(s * 1.5f, s * 1.5f),
            style = Stroke(s)
        )
        
        // Shackle
        drawArc(
            color = color,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset((this.size.width - shackleRadius * 2) / 2f, shackleYOffset),
            size = Size(shackleRadius * 2, shackleRadius * 2),
            style = Stroke(s)
        )
        
        // Shackle legs connecting to body
        drawLine(
            color = color,
            start = Offset((this.size.width - shackleRadius * 2) / 2f, shackleYOffset + shackleRadius),
            end = Offset((this.size.width - shackleRadius * 2) / 2f, bodyTop),
            strokeWidth = s
        )
        drawLine(
            color = color,
            start = Offset((this.size.width + shackleRadius * 2) / 2f, shackleYOffset + shackleRadius),
            end = Offset((this.size.width + shackleRadius * 2) / 2f, bodyTop),
            strokeWidth = s
        )
    }
}

@Composable
fun ImageIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 18.dp,
    strokeWidth: Dp = 1.8.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = strokeWidth.toPx()
        // Outer frame
        drawRoundRect(
            color = color,
            topLeft = Offset(s / 2, s / 2),
            size = Size(w - s, h - s),
            cornerRadius = CornerRadius(w * 0.15f, h * 0.15f),
            style = Stroke(s)
        )
        // Sun / dot
        drawCircle(
            color = color,
            radius = w * 0.08f,
            center = Offset(w * 0.32f, h * 0.35f)
        )
        // Mountain peak path
        val path = Path().apply {
            moveTo(w * 0.16f, h * 0.76f)
            lineTo(w * 0.42f, h * 0.48f)
            lineTo(w * 0.60f, h * 0.66f)
            lineTo(w * 0.72f, h * 0.54f)
            lineTo(w * 0.84f, h * 0.76f)
        }
        drawPath(path, color = color, style = Stroke(s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun TrashIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 18.dp,
    strokeWidth: Dp = 1.8.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = strokeWidth.toPx()
        // Can body
        val bodyPath = Path().apply {
            moveTo(w * 0.25f, h * 0.34f)
            lineTo(w * 0.30f, h * 0.88f)
            lineTo(w * 0.70f, h * 0.88f)
            lineTo(w * 0.75f, h * 0.34f)
        }
        drawPath(bodyPath, color = color, style = Stroke(s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Rim line
        drawLine(
            color = color,
            start = Offset(w * 0.16f, h * 0.34f),
            end = Offset(w * 0.84f, h * 0.34f),
            strokeWidth = s,
            cap = StrokeCap.Round
        )
        // Handle
        val handlePath = Path().apply {
            moveTo(w * 0.38f, h * 0.34f)
            lineTo(w * 0.38f, h * 0.20f)
            lineTo(w * 0.62f, h * 0.20f)
            lineTo(w * 0.62f, h * 0.34f)
        }
        drawPath(handlePath, color = color, style = Stroke(s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun PencilIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 18.dp,
    strokeWidth: Dp = 1.8.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = strokeWidth.toPx()
        val bodyPath = Path().apply {
            moveTo(w * 0.22f, h * 0.78f)
            lineTo(w * 0.32f, h * 0.82f)
            lineTo(w * 0.82f, h * 0.32f)
            lineTo(w * 0.68f, h * 0.18f)
            lineTo(w * 0.18f, h * 0.68f)
            close()
        }
        drawPath(bodyPath, color = color, style = Stroke(s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val tipPath = Path().apply {
            moveTo(w * 0.18f, h * 0.68f)
            lineTo(w * 0.13f, h * 0.87f)
            lineTo(w * 0.32f, h * 0.82f)
        }
        drawPath(tipPath, color = color, style = Stroke(s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(
            color = color,
            start = Offset(w * 0.61f, h * 0.25f),
            end = Offset(w * 0.75f, h * 0.39f),
            strokeWidth = s,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun PhoneIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 16.dp,
    strokeWidth: Dp = 1.8.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = strokeWidth.toPx()
        val path = Path().apply {
            moveTo(w * 0.28f, h * 0.45f)
            cubicTo(w * 0.34f, h * 0.57f, w * 0.43f, h * 0.66f, w * 0.55f, h * 0.72f)
            lineTo(w * 0.64f, h * 0.63f)
            cubicTo(w * 0.65f, h * 0.62f, w * 0.67f, h * 0.62f, w * 0.68f, h * 0.62f)
            cubicTo(w * 0.73f, h * 0.64f, w * 0.78f, h * 0.65f, w * 0.83f, h * 0.65f)
            cubicTo(w * 0.86f, h * 0.65f, w * 0.88f, h * 0.67f, w * 0.88f, h * 0.69f)
            lineTo(w * 0.88f, h * 0.83f)
            cubicTo(w * 0.88f, h * 0.86f, w * 0.86f, h * 0.88f, w * 0.83f, h * 0.88f)
            cubicTo(w * 0.44f, h * 0.88f, w * 0.12f, h * 0.56f, w * 0.12f, h * 0.17f)
            cubicTo(w * 0.12f, h * 0.14f, w * 0.14f, h * 0.12f, w * 0.17f, h * 0.12f)
            lineTo(w * 0.31f, h * 0.12f)
            cubicTo(w * 0.33f, h * 0.12f, w * 0.35f, h * 0.14f, w * 0.35f, h * 0.17f)
            cubicTo(w * 0.35f, h * 0.22f, w * 0.36f, h * 0.27f, w * 0.38f, h * 0.32f)
            cubicTo(w * 0.38f, h * 0.33f, w * 0.38f, h * 0.35f, w * 0.37f, h * 0.36f)
            close()
        }
        drawPath(path, color = color, style = Stroke(s, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun FlashlightIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 16.dp,
    strokeWidth: Dp = 1.8.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = strokeWidth.toPx()
        val path = Path().apply {
            // Flashlight body
            moveTo(w * 0.32f, h * 0.12f)
            lineTo(w * 0.68f, h * 0.12f)
            lineTo(w * 0.62f, h * 0.40f)
            lineTo(w * 0.58f, h * 0.88f)
            lineTo(w * 0.42f, h * 0.88f)
            lineTo(w * 0.38f, h * 0.40f)
            close()
        }
        drawPath(path, color = color, style = Stroke(s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Power button dot
        drawCircle(
            color = color,
            radius = s * 0.8f,
            center = Offset(w * 0.50f, h * 0.55f)
        )
    }
}

@Composable
fun CameraIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    size: Dp = 16.dp,
    strokeWidth: Dp = 1.8.dp
) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val s = strokeWidth.toPx()
        val bodyPath = Path().apply {
            moveTo(w * 0.15f, h * 0.32f)
            lineTo(w * 0.35f, h * 0.32f)
            lineTo(w * 0.42f, h * 0.20f)
            lineTo(w * 0.58f, h * 0.20f)
            lineTo(w * 0.65f, h * 0.32f)
            lineTo(w * 0.85f, h * 0.32f)
            cubicTo(w * 0.90f, h * 0.32f, w * 0.92f, h * 0.35f, w * 0.92f, h * 0.40f)
            lineTo(w * 0.92f, h * 0.80f)
            cubicTo(w * 0.92f, h * 0.85f, w * 0.90f, h * 0.88f, w * 0.85f, h * 0.88f)
            lineTo(w * 0.15f, h * 0.88f)
            cubicTo(w * 0.10f, h * 0.88f, w * 0.08f, h * 0.85f, w * 0.08f, h * 0.80f)
            lineTo(w * 0.08f, h * 0.40f)
            cubicTo(w * 0.08f, h * 0.35f, w * 0.10f, h * 0.32f, w * 0.15f, h * 0.32f)
            close()
        }
        drawPath(bodyPath, color = color, style = Stroke(s, cap = StrokeCap.Round, join = StrokeJoin.Round))
        // Center lens circle
        drawCircle(
            color = color,
            radius = w * 0.18f,
            center = Offset(w * 0.50f, h * 0.60f),
            style = Stroke(s, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

