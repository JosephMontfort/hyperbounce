package dev.hyperios.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import dev.hyperios.app.ui.theme.HyperBounceColors
import dev.hyperios.app.ui.theme.HyperBounceType
import kotlin.math.max

@Composable
fun WallpaperCropperDialog(
    sourceBitmap: Bitmap,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onCropConfirmed: (Bitmap) -> Unit
) {
    val density = LocalDensity.current

    var onConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val vpWidthPx = with(density) { maxWidth.toPx() }
                val vpHeightPx = with(density) { maxHeight.toPx() }

                val bmpW = sourceBitmap.width.toFloat()
                val bmpH = sourceBitmap.height.toFloat()
                val baseScale = max(vpWidthPx / bmpW, vpHeightPx / bmpH)

                var zoom by remember { mutableFloatStateOf(1f) }
                var panX by remember { mutableFloatStateOf((vpWidthPx - bmpW * baseScale) / 2f) }
                var panY by remember { mutableFloatStateOf((vpHeightPx - bmpH * baseScale) / 2f) }

                // Wire confirmation lambda with exact crop math
                remember(zoom, panX, panY, baseScale, vpWidthPx, vpHeightPx) {
                    onConfirmAction = {
                        val currentScale = baseScale * zoom
                        val currentDrawnW = bmpW * currentScale
                        val currentDrawnH = bmpH * currentScale

                        val minPanX = (vpWidthPx - currentDrawnW).coerceAtMost(0f)
                        val maxPanX = 0f.coerceAtLeast((vpWidthPx - currentDrawnW) / 2f)
                        val minPanY = (vpHeightPx - currentDrawnH).coerceAtMost(0f)
                        val maxPanY = 0f.coerceAtLeast((vpHeightPx - currentDrawnH) / 2f)

                        val clampedPanX = if (minPanX < maxPanX) panX.coerceIn(minPanX, maxPanX) else (vpWidthPx - currentDrawnW) / 2f
                        val clampedPanY = if (minPanY < maxPanY) panY.coerceIn(minPanY, maxPanY) else (vpHeightPx - currentDrawnH) / 2f

                        val srcX = ((-clampedPanX) / currentScale).toInt().coerceIn(0, sourceBitmap.width - 1)
                        val srcY = ((-clampedPanY) / currentScale).toInt().coerceIn(0, sourceBitmap.height - 1)
                        val srcW = (vpWidthPx / currentScale).toInt().coerceIn(1, sourceBitmap.width - srcX)
                        val srcH = (vpHeightPx / currentScale).toInt().coerceIn(1, sourceBitmap.height - srcY)

                        val cropped = try {
                            Bitmap.createBitmap(sourceBitmap, srcX, srcY, srcW, srcH)
                        } catch (_: Throwable) {
                            sourceBitmap
                        }
                        onCropConfirmed(cropped)
                    }
                    Unit
                }

                // Interactive full-screen viewport with focal-point zoom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                val oldZoom = zoom
                                val newZoom = (zoom * gestureZoom).coerceIn(1f, 5f)
                                val actualZoomFactor = newZoom / oldZoom
                                zoom = newZoom

                                // Adjust pan relative to gesture focal point (centroid)
                                panX = centroid.x - (centroid.x - panX) * actualZoomFactor + pan.x
                                panY = centroid.y - (centroid.y - panY) * actualZoomFactor + pan.y

                                val currentScale = baseScale * zoom
                                val currentDrawnW = bmpW * currentScale
                                val currentDrawnH = bmpH * currentScale

                                val minPanX = (vpWidthPx - currentDrawnW).coerceAtMost(0f)
                                val maxPanX = 0f.coerceAtLeast((vpWidthPx - currentDrawnW) / 2f)
                                val minPanY = (vpHeightPx - currentDrawnH).coerceAtMost(0f)
                                val maxPanY = 0f.coerceAtLeast((vpHeightPx - currentDrawnH) / 2f)

                                panX = if (minPanX < maxPanX) panX.coerceIn(minPanX, maxPanX) else (vpWidthPx - currentDrawnW) / 2f
                                panY = if (minPanY < maxPanY) panY.coerceIn(minPanY, maxPanY) else (vpHeightPx - currentDrawnH) / 2f
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val currentScale = baseScale * zoom
                        val currentDrawnW = bmpW * currentScale
                        val currentDrawnH = bmpH * currentScale

                        val minPanX = (vpWidthPx - currentDrawnW).coerceAtMost(0f)
                        val maxPanX = 0f.coerceAtLeast((vpWidthPx - currentDrawnW) / 2f)
                        val minPanY = (vpHeightPx - currentDrawnH).coerceAtMost(0f)
                        val maxPanY = 0f.coerceAtLeast((vpHeightPx - currentDrawnH) / 2f)

                        val clampedPanX = if (minPanX < maxPanX) panX.coerceIn(minPanX, maxPanX) else (vpWidthPx - currentDrawnW) / 2f
                        val clampedPanY = if (minPanY < maxPanY) panY.coerceIn(minPanY, maxPanY) else (vpHeightPx - currentDrawnH) / 2f

                        drawIntoCanvas { canvas ->
                            val matrix = android.graphics.Matrix().apply {
                                postScale(currentScale, currentScale)
                                postTranslate(clampedPanX, clampedPanY)
                            }
                            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
                            canvas.nativeCanvas.drawBitmap(sourceBitmap, matrix, paint)
                        }

                        // 3x3 Rule-of-Thirds Grid Overlay
                        val linePaint = Color.White.copy(alpha = 0.22f)
                        val gridStroke = 1.dp.toPx()
                        drawLine(linePaint, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeWidth = gridStroke)
                        drawLine(linePaint, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), strokeWidth = gridStroke)
                        drawLine(linePaint, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeWidth = gridStroke)
                        drawLine(linePaint, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), strokeWidth = gridStroke)

                        // Outer framing border & corner brackets to clearly show it's a cropping viewport
                        val cornerLen = 28.dp.toPx()
                        val cornerStroke = 3.dp.toPx()
                        val cornerColor = Color.White.copy(alpha = 0.85f)
                        val pad = 12.dp.toPx()

                        // Top-Left corner
                        drawLine(cornerColor, Offset(pad, pad), Offset(pad + cornerLen, pad), strokeWidth = cornerStroke)
                        drawLine(cornerColor, Offset(pad, pad), Offset(pad, pad + cornerLen), strokeWidth = cornerStroke)
                        // Top-Right corner
                        drawLine(cornerColor, Offset(size.width - pad, pad), Offset(size.width - pad - cornerLen, pad), strokeWidth = cornerStroke)
                        drawLine(cornerColor, Offset(size.width - pad, pad), Offset(size.width - pad, pad + cornerLen), strokeWidth = cornerStroke)
                        // Bottom-Left corner
                        drawLine(cornerColor, Offset(pad, size.height - pad), Offset(pad + cornerLen, size.height - pad), strokeWidth = cornerStroke)
                        drawLine(cornerColor, Offset(pad, size.height - pad), Offset(pad, size.height - pad - cornerLen), strokeWidth = cornerStroke)
                        // Bottom-Right corner
                        drawLine(cornerColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - cornerLen, size.height - pad), strokeWidth = cornerStroke)
                        drawLine(cornerColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - cornerLen), strokeWidth = cornerStroke)
                    }

                    // Top gradient scrim for readability
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                                )
                            )
                    )

                    // Bottom gradient scrim for buttons
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.80f))
                                )
                            )
                    )

                    // Top Header Overlay
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(top = 16.dp, start = 20.dp, end = 20.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(Capsule())
                                .background(Color.Black.copy(alpha = 0.50f))
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                BasicText(
                                    "Crop Wallpaper",
                                    style = HyperBounceType.title3.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(Modifier.height(3.dp))
                                BasicText(
                                    "Pinch to zoom and drag to adjust position",
                                    style = HyperBounceType.caption.copy(
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                )
                            }
                        }
                    }

                    // Bottom Action Overlay with non-stretchable capsule buttons
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 24.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                "Cancel",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(CircleShape)
                                .background(colors.accentBlue)
                                .clickable { onConfirmAction?.invoke() },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                "Set Wallpaper",
                                style = TextStyle(
                                    color = Color.White,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
