package dev.joseph.hyperbounce.nav.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
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
 * Normal tab hierarchy (Feed, Direct, Search, Profile) is captured cleanly through View.draw().
 * For video tabs (Instagram Reels), video frames reside in hardware SurfaceView/TextureView
 * layers. When enablePixelCopy is true, we asynchronously composite the hardware video frames
 * into the backdrop snapshot without blocking UI threads.
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
    private var surfaceCopyBuffer: Bitmap? = null
    private var textureCopyBuffer: Bitmap? = null
    private val surfaceFronts = java.util.concurrent.ConcurrentHashMap<View, SurfaceFrame>()
    private val pendingSurfaceCopies = java.util.concurrent.ConcurrentHashMap.newKeySet<View>()

    @Volatile
    var enablePixelCopy: Boolean = false

    companion object {
        private val bgThread by lazy {
            android.os.HandlerThread("HB-PixelCopy").apply { start() }
        }
        private val bgHandler by lazy {
            Handler(bgThread.looper)
        }
    }

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
            captureOncePerFrame()
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
            target.eraseColor(android.graphics.Color.TRANSPARENT)

            val canvas = Canvas(target)
            canvas.save()
            canvas.scale(scale, scale)
            canvas.clipRect(0, 0, width, height)
            canvas.translate(-localLeft, -localTop)
            sourceView.draw(canvas)
            canvas.restore()

            if (enablePixelCopy) {
                requestHardwareSurfaceFrames(currentRegion, location, scale)
                drawLatestHardwareSurfaceFrames(canvas, currentRegion, location, scale)
            } else {
                clearHardwareSurfaces()
            }

            val bgPaint = android.graphics.Paint().apply {
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OVER)
                color = android.graphics.Color.parseColor("#40000000")
            }
            canvas.drawRect(0f, 0f, scaledWidth.toFloat(), scaledHeight.toFloat(), bgPaint)

            val previousFront = front
            front = target
            back = previousFront

            capturedFrame = LiquidGlassCapturedFrame(
                bitmap = target,
                originInWindow = Offset(currentRegion.left, currentRegion.top),
            )
            lastCapturedFrame = frameNumber
            lastCaptureNanos = now
        } catch (t: Throwable) {
            Log.e(
                "HyperBounce-Backdrop",
                "capture failed: ${t.javaClass.simpleName}: ${t.message}",
            )
        }
    }

    private fun requestHardwareSurfaceFrames(
        currentRegion: RectFState,
        sourceLocation: IntArray,
        scale: Float,
    ) {
        val surfaces = ArrayList<View>()
        collectAllHardwareSurfaceViews(surfaces)

        val uniqueSurfaces = surfaces.distinctBy { System.identityHashCode(it) }

        for (surface in uniqueSurfaces) {
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

            pendingSurfaceCopies.add(surface)

            when (surface) {
                is SurfaceView -> {
                    try {
                        val sWidth = surface.width
                        val sHeight = surface.height
                        if (sWidth > 0 && sHeight > 0) {
                            var sBuf = surfaceCopyBuffer
                            if (sBuf == null || sBuf.isRecycled || sBuf.width != sWidth || sBuf.height != sHeight) {
                                sBuf?.recycle()
                                sBuf = Bitmap.createBitmap(sWidth, sHeight, Bitmap.Config.ARGB_8888)
                                surfaceCopyBuffer = sBuf
                            }
                            val bufferToUse = sBuf
                            PixelCopy.request(
                                surface,
                                null,
                                bufferToUse,
                                { result ->
                                    pendingSurfaceCopies.remove(surface)
                                    if (result == PixelCopy.SUCCESS && !bufferToUse.isRecycled) {
                                        try {
                                            val destBmp = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
                                            val c = Canvas(destBmp)
                                            c.drawBitmap(
                                                bufferToUse,
                                                Rect(srcLeft, srcTop, srcRight, srcBottom),
                                                Rect(0, 0, destWidth, destHeight),
                                                null
                                            )
                                            val frame = SurfaceFrame(
                                                bitmap = destBmp,
                                                left = ((overlapLeft - currentRegion.left) * scale).toInt(),
                                                top = ((overlapTop - currentRegion.top) * scale).toInt(),
                                                isSurfaceView = true,
                                            )
                                            replaceSurfaceFrame(surface, frame)
                                        } catch (_: Throwable) {}
                                    }
                                },
                                bgHandler,
                            )
                        } else {
                            pendingSurfaceCopies.remove(surface)
                        }
                    } catch (_: Throwable) {
                        pendingSurfaceCopies.remove(surface)
                    }
                }

                is TextureView -> {
                    try {
                        var tBuf = textureCopyBuffer
                        if (tBuf == null || tBuf.isRecycled || tBuf.width != surface.width || tBuf.height != surface.height) {
                            tBuf?.recycle()
                            tBuf = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
                            textureCopyBuffer = tBuf
                        }
                        if (surface.getBitmap(tBuf) != null) {
                            val destBmp = Bitmap.createBitmap(destWidth, destHeight, Bitmap.Config.ARGB_8888)
                            val c = Canvas(destBmp)
                            c.drawBitmap(
                                tBuf,
                                Rect(srcLeft, srcTop, srcRight, srcBottom),
                                Rect(0, 0, destWidth, destHeight),
                                null
                            )
                            pendingSurfaceCopies.remove(surface)
                            val frame = SurfaceFrame(
                                bitmap = destBmp,
                                left = ((overlapLeft - currentRegion.left) * scale).toInt(),
                                top = ((overlapTop - currentRegion.top) * scale).toInt(),
                                isSurfaceView = false,
                            )
                            replaceSurfaceFrame(surface, frame)
                        } else {
                            pendingSurfaceCopies.remove(surface)
                        }
                    } catch (_: Throwable) {
                        pendingSurfaceCopies.remove(surface)
                    }
                }
            }
        }

        val iter = surfaceFronts.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.key !in uniqueSurfaces) {
                if (!entry.value.bitmap.isRecycled) {
                    entry.value.bitmap.recycle()
                }
                iter.remove()
            }
        }
    }

    private fun drawLatestHardwareSurfaceFrames(
        canvas: Canvas,
        currentRegion: RectFState,
        sourceLocation: IntArray,
        scale: Float,
    ) {
        val paintDstOver = android.graphics.Paint().apply {
            xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OVER)
        }

        for (frame in surfaceFronts.values) {
            if (frame.bitmap.isRecycled) continue
            val paint = if (frame.isSurfaceView) paintDstOver else null
            canvas.drawBitmap(frame.bitmap, frame.left.toFloat(), frame.top.toFloat(), paint)
        }
    }

    private fun clearHardwareSurfaces() {
        if (surfaceFronts.isNotEmpty()) {
            for (frame in surfaceFronts.values) {
                if (!frame.bitmap.isRecycled) frame.bitmap.recycle()
            }
            surfaceFronts.clear()
            pendingSurfaceCopies.clear()
        }
    }

    private fun replaceSurfaceFrame(surface: View, newFrame: SurfaceFrame) {
        val old = surfaceFronts.put(surface, newFrame)
        if (old != null && old.bitmap !== newFrame.bitmap && !old.bitmap.isRecycled) {
            old.bitmap.recycle()
        }
    }

    private fun collectAllHardwareSurfaceViews(result: MutableList<View>) {
        try {
            val wmgClass = Class.forName("android.view.WindowManagerGlobal")
            val getInstanceMethod = wmgClass.getMethod("getInstance")
            val wmg = getInstanceMethod.invoke(null)
            val mViewsField = wmgClass.getDeclaredField("mViews")
            mViewsField.isAccessible = true
            val views = mViewsField.get(wmg)
            if (views is Iterable<*>) {
                for (view in views) {
                    if (view is View) {
                        collectHardwareSurfaceViews(view, result)
                    }
                }
            } else if (views is Array<*>) {
                for (view in views) {
                    if (view is View) {
                        collectHardwareSurfaceViews(view, result)
                    }
                }
            } else {
                collectHardwareSurfaceViews(sourceView.rootView, result)
            }
        } catch (_: Throwable) {
            collectHardwareSurfaceViews(sourceView.rootView, result)
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
        val isSurfaceView: Boolean = false,
    )

    fun release() {
        stop()
        clearHardwareSurfaces()
        front?.recycle()
        back?.recycle()
        surfaceCopyBuffer?.recycle()
        surfaceCopyBuffer = null
        textureCopyBuffer?.recycle()
        textureCopyBuffer = null
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
