package dev.joseph.hyperbounce.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlin.math.max

@Composable
fun WallpaperCropperDialog(
    sourceBitmap: Bitmap,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onCropConfirmed: (Bitmap) -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    // Device screen aspect ratio constraint
    val screenW = configuration.screenWidthDp.toFloat()
    val screenH = configuration.screenHeightDp.toFloat()
    val screenAspect = screenW / screenH

    var onConfirmAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                BasicText(
                    "Crop Wallpaper",
                    style = HyperBounceType.title2.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    "Pinch to zoom and drag to adjust position",
                    style = HyperBounceType.caption1.copy(
                        color = Color.White.copy(alpha = 0.65f)
                    )
                )
                Spacer(Modifier.height(20.dp))

                // Crop Viewport Frame constrained to device screen aspect ratio
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(420.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val maxH = maxHeight
                    val maxW = maxWidth
                    val targetW = minOf(maxW, maxH * screenAspect)
                    val targetH = targetW / screenAspect

                    val vpWidthPx = with(density) { targetW.toPx() }
                    val vpHeightPx = with(density) { targetH.toPx() }

                    val bmpW = sourceBitmap.width.toFloat()
                    val bmpH = sourceBitmap.height.toFloat()
                    val baseScale = max(vpWidthPx / bmpW, vpHeightPx / bmpH)

                    var zoom by remember { mutableFloatStateOf(1f) }
                    var panX by remember { mutableFloatStateOf((vpWidthPx - bmpW * baseScale) / 2f) }
                    var panY by remember { mutableFloatStateOf((vpHeightPx - bmpH * baseScale) / 2f) }

                    // Wire confirmation lambda
                    remember(zoom, panX, panY, baseScale, vpWidthPx, vpHeightPx) {
                        onConfirmAction = {
                            val currentScale = baseScale * zoom
                            val currentDrawnW = bmpW * currentScale
                            val currentDrawnH = bmpH * currentScale

                            val clampedPanX = panX.coerceIn(vpWidthPx - currentDrawnW, 0f)
                            val clampedPanY = panY.coerceIn(vpHeightPx - currentDrawnH, 0f)

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

                    Box(
                        modifier = Modifier
                            .width(targetW)
                            .height(targetH)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                            .clipToBounds()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, gestureZoom, _ ->
                                    zoom = (zoom * gestureZoom).coerceIn(1f, 4f)
                                    val currentScale = baseScale * zoom
                                    val currentDrawnW = bmpW * currentScale
                                    val currentDrawnH = bmpH * currentScale

                                    val minX = vpWidthPx - currentDrawnW
                                    val minY = vpHeightPx - currentDrawnH

                                    panX = (panX + pan.x).coerceIn(minX, 0f)
                                    panY = (panY + pan.y).coerceIn(minY, 0f)
                                }
                            }
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val currentScale = baseScale * zoom
                            val currentDrawnW = bmpW * currentScale
                            val currentDrawnH = bmpH * currentScale

                            val clampedPanX = panX.coerceIn(vpWidthPx - currentDrawnW, 0f)
                            val clampedPanY = panY.coerceIn(vpHeightPx - currentDrawnH, 0f)

                            drawIntoCanvas { canvas ->
                                val matrix = android.graphics.Matrix().apply {
                                    postScale(currentScale, currentScale)
                                    postTranslate(clampedPanX, clampedPanY)
                                }
                                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
                                canvas.nativeCanvas.drawBitmap(sourceBitmap, matrix, paint)
                            }

                            // 3x3 Grid Overlay for alignment
                            val linePaint = Color.White.copy(alpha = 0.25f)
                            drawLine(linePaint, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeWidth = 1.dp.toPx())
                            drawLine(linePaint, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), strokeWidth = 1.dp.toPx())
                            drawLine(linePaint, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeWidth = 1.dp.toPx())
                            drawLine(linePaint, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), strokeWidth = 1.dp.toPx())
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action Buttons
                Row(
                    Modifier.fillMaxWidth(0.85f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText("Cancel", style = HyperBounceType.body.copy(color = Color.White))
                    }

                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.accentBlue)
                            .clickable { onConfirmAction?.invoke() },
                        contentAlignment = Alignment.Center
                    ) {
                        BasicText(
                            "Set Wallpaper",
                            style = HyperBounceType.body.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }
}
