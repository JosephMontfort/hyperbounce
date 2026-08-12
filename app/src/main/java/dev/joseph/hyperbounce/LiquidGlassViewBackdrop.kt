package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.graphics.asImageBitmap
import com.kyant.backdrop.Backdrop

/**
 * Live app-window backdrop source for Kyant's Backdrop engine.
 *
 * The normal Instagram/WhatsApp/YouTube hierarchy is captured through View.draw().
 * Some apps, however, put hardware video frames in SurfaceView/TextureView layers
 * that are not reproduced by software View.draw(). In that case the hierarchy still
 * appears live on screen, while a Canvas snapshot contains only the non-video UI.
 *
 * We therefore keep the original hierarchy capture and additionally composite the
 * latest visible hardware-surface pixels that overlap the target region. The liquid
 * glass library itself remains unchanged: it still receives one live Backdrop frame
 * and applies its normal blur/lens/vibrancy/refraction pipeline.
 */
internal data class LiquidGlassCapturedFrame(
    val bitmap: Bitmap,
    val originInWindow: Offset,
)

internal class LiquidGlassViewBackdrop(
    private val sourceView: View,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    private var front: Bitmap? = null
    private var back: Bitmap? = null
    private var surfaceFronts = mutableMapOf<View, SurfaceFrame>()
    private val pendingSurfaceCopies = mutableSetOf<View>()
    private val mainHandler = Handler(Looper.getMainLooper())

    var capturedFrame: LiquidGlassCapturedFrame? by mutableStateOf(null)
        private set

    private var region: RectFState? = null
    private var frameNumber = 0
    private var lastCapturedFrame = -1
    private var lastCaptureNanos = 0L
    private val minCaptureIntervalNanos = 16_666_667L // ~60 Hz

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        captureOncePerFrame()
        true
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameNumber++
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    fun setTargetBounds(left: Float, top: Float, width: Int, height: Int, paddingPx: Int) {
        region = RectFState(
            left = left - paddingPx,
            top = top - paddingPx,
            width = width + paddingPx * 2,
            height = height + paddingPx * 2,
        )
    }

    fun start() {
        sourceView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        sourceView.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    private fun captureOncePerFrame() {
        if (lastCapturedFrame == frameNumber) return

        val now = System.nanoTime()
        if (now - lastCaptureNanos < minCaptureIntervalNanos) return

        val currentRegion = region ?: return
        if (currentRegion.width <= 0 || currentRegion.height <= 0) return

        val location = IntArray(2)
        sourceView.getLocationInWindow(location)

        val localLeft = currentRegion.left - location[0]
        val localTop = currentRegion.top - location[1]
        val width = currentRegion.width
        val height = currentRegion.height

        try {
            val scale = 0.5f
            val scaledWidth = (width * scale).toInt().coerceAtLeast(1)
            val scaledHeight = (height * scale).toInt().coerceAtLeast(1)
            ensureBuffers(scaledWidth, scaledHeight)

            val target = back ?: return
            target.eraseColor(android.graphics.Color.parseColor("#40000000"))

            val canvas = Canvas(target)
            canvas.save()
            canvas.scale(scale, scale)
            canvas.clipRect(0, 0, width, height)
            canvas.translate(-localLeft, -localTop)
            sourceView.draw(canvas)
            canvas.restore()

            // View.draw() deliberately does not contain the producer buffer of a
            // SurfaceView. TextureView can be copied separately as well. Composite
            // those hardware-backed regions over the hierarchy snapshot so the
            // backdrop has the exact pixels that the user sees while a reel plays.
            requestHardwareSurfaceFrames(currentRegion, location, scale)
            drawLatestHardwareSurfaceFrames(canvas, currentRegion, location, scale)

            val previousFront = front
            front = target
            back = previousFront

            capturedFrame = LiquidGlassCapturedFrame(
                bitmap = target,
                originInWindow = Offset(currentRegion.left, currentRegion.top),
            )
            lastCapturedFrame = frameNumber
            lastCaptureNanos = now
        } catch (_: Throwable) {
            // Keep the previous frame if a target app contains a view that rejects
            // software recording or a hardware-surface copy temporarily fails.
        }
    }

    private fun requestHardwareSurfaceFrames(
        currentRegion: RectFState,
        sourceLocation: IntArray,
        scale: Float,
    ) {
        val surfaces = ArrayList<View>()
        collectHardwareSurfaceViews(sourceView, surfaces)

        for (surface in surfaces) {
            if (!surface.isShown || surface.width <= 0 || surface.height <= 0) continue

            val surfaceLocation = IntArray(2)
            surface.getLocationInWindow(surfaceLocation)
            val left = surfaceLocation[0].toFloat()
            val top = surfaceLocation[1].toFloat()
            val right = left + surface.width
            val bottom = top + surface.height

            val overlapLeft = maxOf(currentRegion.left, left)
            val overlapTop = maxOf(currentRegion.top, top)
            val overlapRight = minOf(currentRegion.left + currentRegion.width, right)
            val overlapBottom = minOf(currentRegion.top + currentRegion.height, bottom)
            if (overlapRight <= overlapLeft || overlapBottom <= overlapTop) continue

            if (pendingSurfaceCopies.contains(surface)) continue

            val srcLeft = (overlapLeft - left).toInt().coerceAtLeast(0)
            val srcTop = (overlapTop - top).toInt().coerceAtLeast(0)
            val srcRight = (overlapRight - left).toInt().coerceAtMost(surface.width)
            val srcBottom = (overlapBottom - top).toInt().coerceAtMost(surface.height)
            val srcWidth = srcRight - srcLeft
            val srcHeight = srcBottom - srcTop
            if (srcWidth <= 0 || srcHeight <= 0) continue

            val destWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
            val destHeight = (srcHeight * scale).toInt().coerceAtLeast(1)
            val dest = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)

            pendingSurfaceCopies.add(surface)

            when (surface) {
                is SurfaceView -> {
                    try {
                        PixelCopy.request(
                            surface,
                            Rect(srcLeft, srcTop, srcRight, srcBottom),
                            dest,
                            { result ->
                                pendingSurfaceCopies.remove(surface)
                                if (result == PixelCopy.SUCCESS) {
                                    val frame = SurfaceFrame(
                                        bitmap = dest,
                                        left = ((overlapLeft - currentRegion.left) * scale).toInt(),
                                        top = ((overlapTop - currentRegion.top) * scale).toInt(),
                                    )
                                    replaceSurfaceFrame(surface, frame)
                                } else {
                                    dest.recycle()
                                }
                            },
                            mainHandler,
                        )
                    } catch (_: Throwable) {
                        pendingSurfaceCopies.remove(surface)
                        dest.recycle()
                    }
                }

                is TextureView -> {
                    try {
                        // TextureView#getBitmap(bitmap) copies the texture content
                        // without requiring a software View.draw() pass.
                        surface.getBitmap(dest)
                        pendingSurfaceCopies.remove(surface)
                        replaceSurfaceFrame(
                            surface,
                            SurfaceFrame(
                                bitmap = dest,
                                left = ((overlapLeft - currentRegion.left) * scale).toInt(),
                                top = ((overlapTop - currentRegion.top) * scale).toInt(),
                            )
                        )
                    } catch (_: Throwable) {
                        pendingSurfaceCopies.remove(surface)
                        dest.recycle()
                    }
                }
            }
        }
    }

    private fun drawLatestHardwareSurfaceFrames(
        canvas: Canvas,
        currentRegion: RectFState,
        sourceLocation: IntArray,
        scale: Float,
    ) {
        for (frame in surfaceFronts.values) {
            if (frame.bitmap.isRecycled) continue
            canvas.drawBitmap(frame.bitmap, frame.left.toFloat(), frame.top.toFloat(), null)
        }
    }

    private fun replaceSurfaceFrame(surface: View, newFrame: SurfaceFrame) {
        val old = surfaceFronts.put(surface, newFrame)
        if (old != null && old.bitmap !== newFrame.bitmap && !old.bitmap.isRecycled) {
            old.bitmap.recycle()
        }
    }

    private fun collectHardwareSurfaceViews(view: View, result: MutableList<View>) {
        if (view is SurfaceView || view is TextureView) {
            result += view
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectHardwareSurfaceViews(view.getChildAt(i), result)
            }
        }
    }

    private fun ensureBuffers(width: Int, height: Int) {
        if (front?.width != width || front?.height != height) {
            front?.recycle()
            back?.recycle()
            front = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            back = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
    }

    private data class RectFState(
        val left: Float,
        val top: Float,
        val width: Int,
        val height: Int,
    )

    private data class SurfaceFrame(
        val bitmap: Bitmap,
        val left: Int,
        val top: Int,
    )

    fun release() {
        stop()
        for (frame in surfaceFronts.values) {
            if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
        }
        surfaceFronts.clear()
        pendingSurfaceCopies.clear()
        front?.recycle()
        back?.recycle()
        front = null
        back = null
        capturedFrame = null
        lastCapturedFrame = -1
        lastCaptureNanos = 0L
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val frame = capturedFrame ?: return
        val coords = coordinates ?: return

        val targetPosition = coords.positionInWindow()
        val drawOffset = targetPosition - frame.originInWindow

        scale(2f, 2f, androidx.compose.ui.geometry.Offset.Zero) {
            drawImage(
                image = frame.bitmap.asImageBitmap(),
                topLeft = -drawOffset * 0.5f,
            )
        }
    }
}
