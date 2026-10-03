package dev.joseph.hyperbounce.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.Backdrop
import com.kyant.shapes.Capsule
import dev.joseph.hyperbounce.lockscreen.glass.LockScreenWallpaperHelper
import dev.joseph.hyperbounce.notification.glass.CustomWallpaperHelper
import dev.joseph.hyperbounce.ui.glass.LiquidToggle
import dev.joseph.hyperbounce.ui.theme.HyperBounceColors
import dev.joseph.hyperbounce.ui.theme.HyperBounceType
import kotlin.math.max
import kotlin.math.min

@Composable
fun DepthSubjectAdjusterDialog(
    subjectBitmap: Bitmap,
    colors: HyperBounceColors,
    backdrop: Backdrop,
    onDismiss: () -> Unit,
    onSubjectConfirmed: (Bitmap) -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var showBackground by remember { mutableStateOf(true) }

    // Retrieve static background wallpaper (custom cropped wallpaper or system lockscreen wallpaper)
    val backgroundBitmap = remember {
        val custom = CustomWallpaperHelper.getCustomWallpaperBitmap(context)
        if (custom != null && !custom.isRecycled) {
            custom
        } else {
            val d = LockScreenWallpaperHelper.getUnblurredLockWallpaper(context)
            if (d is BitmapDrawable && d.bitmap != null && !d.bitmap.isRecycled) {
                d.bitmap
            } else if (d != null) {
                try {
                    val w = d.intrinsicWidth.takeIf { it > 0 } ?: 1080
                    val h = d.intrinsicHeight.takeIf { it > 0 } ?: 2400
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val c = android.graphics.Canvas(bmp)
                    d.setBounds(0, 0, w, h)
                    d.draw(c)
                    bmp
                } catch (_: Throwable) {
                    null
                }
            } else {
                null
            }
        }
    }

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
                .background(Color(0xFF121214))
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                val vpWidthPx = with(density) { maxWidth.toPx() }
                val vpHeightPx = with(density) { maxHeight.toPx() }

                val bmpW = subjectBitmap.width.toFloat()
                val bmpH = subjectBitmap.height.toFloat()

                // Match exact full-screen placement as it displays when pulling down notification shade
                val (initialScale, initialPanX, initialPanY) = remember(bmpW, bmpH, vpWidthPx, vpHeightPx, backgroundBitmap) {
                    if (backgroundBitmap != null &&
                        kotlin.math.abs(bmpW - backgroundBitmap.width.toFloat()) < 2f &&
                        kotlin.math.abs(bmpH - backgroundBitmap.height.toFloat()) < 2f
                    ) {
                        // Cutout generated from this wallpaper — align 1:1 pixel-for-pixel over background
                        val bgW = backgroundBitmap.width.toFloat()
                        val bgH = backgroundBitmap.height.toFloat()
                        val s = max(vpWidthPx / bgW, vpHeightPx / bgH)
                        val px = (vpWidthPx - bgW * s) / 2f
                        val py = (vpHeightPx - bgH * s) / 2f
                        Triple(s, px, py)
                    } else {
                        // Full-screen center-crop scale matching notification shade
                        val s = max(vpWidthPx / bmpW, vpHeightPx / bmpH)
                        val px = (vpWidthPx - bmpW * s) / 2f
                        val py = (vpHeightPx - bmpH * s) / 2f
                        Triple(s, px, py)
                    }
                }

                var zoom by remember { mutableFloatStateOf(1f) }
                var panX by remember { mutableFloatStateOf(initialPanX) }
                var panY by remember { mutableFloatStateOf(initialPanY) }

                // Confirmation lambda: renders subject at exact scale & position on screen-sized canvas
                remember(zoom, panX, panY, initialScale, vpWidthPx, vpHeightPx) {
                    onConfirmAction = {
                        val currentScale = initialScale * zoom
                        val screenW = vpWidthPx.toInt().coerceAtLeast(1)
                        val screenH = vpHeightPx.toInt().coerceAtLeast(1)
                        val outputBitmap = Bitmap.createBitmap(screenW, screenH, Bitmap.Config.ARGB_8888)
                        val outCanvas = android.graphics.Canvas(outputBitmap)
                        val matrix = android.graphics.Matrix().apply {
                            postScale(currentScale, currentScale)
                            postTranslate(panX, panY)
                        }
                        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                        outCanvas.drawBitmap(subjectBitmap, matrix, paint)
                        onSubjectConfirmed(outputBitmap)
                    }
                    Unit
                }

                // Interactive adjustment canvas
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { centroid, pan, gestureZoom, _ ->
                                val oldZoom = zoom
                                val newZoom = (zoom * gestureZoom).coerceIn(0.20f, 6.0f)
                                val actualZoomFactor = newZoom / oldZoom
                                zoom = newZoom

                                // Adjust pan relative to centroid focal point
                                panX = centroid.x - (centroid.x - panX) * actualZoomFactor + pan.x
                                panY = centroid.y - (centroid.y - panY) * actualZoomFactor + pan.y
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val currentScale = initialScale * zoom

                        drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas
                            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)

                            // 1. Static background wallpaper layer
                            if (showBackground && backgroundBitmap != null && !backgroundBitmap.isRecycled) {
                                val bgW = backgroundBitmap.width.toFloat()
                                val bgH = backgroundBitmap.height.toFloat()
                                val bgScale = max(size.width / bgW, size.height / bgH)
                                val drawnBgW = bgW * bgScale
                                val drawnBgH = bgH * bgScale
                                val bgX = (size.width - drawnBgW) / 2f
                                val bgY = (size.height - drawnBgH) / 2f

                                val bgMatrix = android.graphics.Matrix().apply {
                                    postScale(bgScale, bgScale)
                                    postTranslate(bgX, bgY)
                                }
                                native.drawBitmap(backgroundBitmap, bgMatrix, paint)

                                // Subtle dark overlay for depth separation
                                native.drawColor(0x33000000.toInt())
                            }

                            // 2. Dynamic depth subject overlay
                            val subjectMatrix = android.graphics.Matrix().apply {
                                postScale(currentScale, currentScale)
                                postTranslate(panX, panY)
                            }
                            native.drawBitmap(subjectBitmap, subjectMatrix, paint)
                        }

                        // Alignment guide grid
                        val linePaint = Color.White.copy(alpha = 0.18f)
                        val gridStroke = 1.dp.toPx()
                        drawLine(linePaint, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), strokeWidth = gridStroke)
                        drawLine(linePaint, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), strokeWidth = gridStroke)
                        drawLine(linePaint, Offset(0f, size.height / 3f), Offset(size.width, size.height / 3f), strokeWidth = gridStroke)
                        drawLine(linePaint, Offset(0f, size.height * 2f / 3f), Offset(size.width, size.height * 2f / 3f), strokeWidth = gridStroke)
                    }

                    // Top gradient scrim
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.70f), Color.Transparent)
                                )
                            )
                    )

                    // Bottom gradient scrim
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

                    // Top Controls: Header + "Show Background" Toggle
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
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 18.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                BasicText(
                                    "Adjust Depth Subject",
                                    style = HyperBounceType.title3.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(Modifier.height(3.dp))
                                BasicText(
                                    "Pinch to zoom and align subject over wallpaper",
                                    style = HyperBounceType.caption.copy(
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Show Background Toggle Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier
                                .clip(Capsule())
                                .background(Color.Black.copy(alpha = 0.55f))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            BasicText(
                                "Show Background",
                                style = HyperBounceType.subhead.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            LiquidToggle(
                                selected = { showBackground },
                                onSelect = { showBackground = it },
                                backdrop = backdrop
                            )
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
                                "Set Subject",
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
