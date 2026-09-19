package dev.joseph.hyperbounce.nav.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.PixelCopy
import android.view.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop
import java.util.concurrent.ConcurrentHashMap

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
    private val surfaceBuffers = ConcurrentHashMap<View, HardwareSurfaceBuffer>()

    @Volatile
    var enablePixelCopy: Boolean = false

    companion object {
        private val bgThread by lazy {
            HandlerThread("HB-PixelCopy").apply { start() }
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
            target.eraseColor(Color.TRANSPARENT)

            val canvas = Canvas(target)
            canvas.save()
            canvas.scale(scale, scale)
            canvas.clipRect(0, 0, width, height)
            canvas.translate(-localLeft, -localTop)
            sourceView.draw(canvas)
            canvas.restore()

            if (enablePixelCopy) {
                requestHardwareSurfaceFrames(currentRegion, location, scale)
                drawLatestHardwareSurfaceFrames(canvas, currentRegion, location, scale, scaledWidth, scaledHeight)
            } else {
                clearHardwareSurfaces()
            }

            // Solid opaque dark background under layer hole ensures backdrop never becomes transparent
            val bgPaint = Paint().apply {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
                color = Color.parseColor("#FF121212")
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

    private fun extractSurface(surfaceView: SurfaceView): Surface? {
        try {
            val method = surfaceView.javaClass.getMethod("getSurface")
            val s = method.invoke(surfaceView) as? Surface
            if (s != null && s.isValid) return s
        } catch (_: Throwable) {}

        try {
            val s = surfaceView.holder?.surface
            if (s != null && s.isValid) return s
        } catch (_: Throwable) {}

        return null
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
            val sX = surfaceLocation[0]
            val sY = surfaceLocation[1]
            val sW = surface.width
            val sH = surface.height

            val left = sX.toFloat()
            val top = sY.toFloat()
            val right = left + sW
            val bottom = top + sH

            val overlapLeft = maxOf(currentRegion.left, left)
            val overlapTop = maxOf(currentRegion.top, top)
            val overlapRight = minOf(currentRegion.left + currentRegion.width, right)
            val overlapBottom = minOf(currentRegion.top + currentRegion.height, bottom)
            if (overlapRight <= overlapLeft || overlapBottom <= overlapTop) continue

            val buffer = surfaceBuffers.getOrPut(surface) { HardwareSurfaceBuffer(surface) }
            if (buffer.isCopying) continue

            val srcLeft = (overlapLeft - left).toInt().coerceIn(0, sW)
            val srcTop = (overlapTop - top).toInt().coerceIn(0, sH)
            val srcRight = (overlapRight - left).toInt().coerceIn(0, sW)
            val srcBottom = (overlapBottom - top).toInt().coerceIn(0, sH)
            val srcWidth = srcRight - srcLeft
            val srcHeight = srcBottom - srcTop
            if (srcWidth <= 0 || srcHeight <= 0) continue

            val destWidth = (srcWidth * scale).toInt().coerceAtLeast(1)
            val destHeight = (srcHeight * scale).toInt().coerceAtLeast(1)

            buffer.ensureBuffers(destWidth, destHeight)
            val back = buffer.backBitmap ?: continue

            buffer.isCopying = true

            val targetLeft = ((overlapLeft - currentRegion.left) * scale).toInt()
            val targetTop = ((overlapTop - currentRegion.top) * scale).toInt()

            when (surface) {
                is SurfaceView -> {
                    val viewRect = Rect(srcLeft, srcTop, srcRight, srcBottom)
                    val sFrame = surface.holder?.surfaceFrame
                    val bufW = if (sFrame != null && sFrame.width() > 0) sFrame.width() else sW
                    val bufH = if (sFrame != null && sFrame.height() > 0) sFrame.height() else sH
                    val scaleX = bufW.toFloat() / sW
                    val scaleY = bufH.toFloat() / sH
                    val surfRect = Rect(
                        (srcLeft * scaleX).toInt().coerceIn(0, bufW),
                        (srcTop * scaleY).toInt().coerceIn(0, bufH),
                        (srcRight * scaleX).toInt().coerceIn(0, bufW),
                        (srcBottom * scaleY).toInt().coerceIn(0, bufH)
                    )

                    val listener = PixelCopy.OnPixelCopyFinishedListener { result ->
                        buffer.isCopying = false
                        if (result == PixelCopy.SUCCESS && !back.isRecycled) {
                            buffer.swap(targetLeft, targetTop, destWidth, destHeight, true)
                        } else {
                            Log.w("HyperBounce-Backdrop", "PixelCopy result=$result for SurfaceView")
                        }
                    }

                    try {
                        PixelCopy.request(surface, viewRect, back, listener, bgHandler)
                    } catch (e1: Throwable) {
                        try {
                            val realSurface = extractSurface(surface)
                            if (realSurface != null && realSurface.isValid) {
                                PixelCopy.request(realSurface, surfRect, back, listener, bgHandler)
                            } else {
                                buffer.isCopying = false
                            }
                        } catch (e2: Throwable) {
                            buffer.isCopying = false
                            Log.w("HyperBounce-Backdrop", "PixelCopy exception: ${e2.message}")
                        }
                    }
                }

                is TextureView -> {
                    bgHandler.post {
                        try {
                            val bmp = surface.getBitmap(back)
                            if (bmp != null) {
                                buffer.swap(targetLeft, targetTop, destWidth, destHeight, false)
                            }
                        } catch (_: Throwable) {
                        } finally {
                            buffer.isCopying = false
                        }
                    }
                }
            }
        }

        // Clean up detached surfaces
        val iter = surfaceBuffers.iterator()
        while (iter.hasNext()) {
            val entry = iter.next()
            if (entry.key !in uniqueSurfaces || !entry.key.isAttachedToWindow) {
                entry.value.release()
                iter.remove()
            }
        }
    }

    private fun drawLatestHardwareSurfaceFrames(
        canvas: Canvas,
        currentRegion: RectFState,
        sourceLocation: IntArray,
        scale: Float,
        scaledWidth: Int,
        scaledHeight: Int,
    ) {
        val paintDstOver = Paint().apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OVER)
        }

        for (buffer in surfaceBuffers.values) {
            val bmp = buffer.frontBitmap ?: continue
            if (bmp.isRecycled) continue
            val paint = if (buffer.isSurfaceView) paintDstOver else null
            canvas.drawBitmap(bmp, buffer.left.toFloat(), buffer.top.toFloat(), paint)

            val bottomY = buffer.top + buffer.height
            if (bottomY < scaledHeight && buffer.isSurfaceView && bmp.height > 1) {
                val srcStrip = Rect(0, bmp.height - 1, bmp.width, bmp.height)
                val dstStrip = Rect(buffer.left, bottomY, buffer.left + buffer.width, scaledHeight)
                canvas.drawBitmap(bmp, srcStrip, dstStrip, paintDstOver)
            }
        }
    }

    private fun clearHardwareSurfaces() {
        if (surfaceBuffers.isNotEmpty()) {
            for (buf in surfaceBuffers.values) {
                buf.release()
            }
            surfaceBuffers.clear()
        }
    }

    private fun collectAllHardwareSurfaceViews(result: MutableList<View>) {
        try {
            collectHardwareSurfaceViews(sourceView.rootView, result)
        } catch (_: Throwable) {}

        if (result.isNotEmpty()) return

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
            }
        } catch (_: Throwable) {}
    }

    private fun collectHardwareSurfaceViews(view: View, result: MutableList<View>) {
        if (view.visibility != View.VISIBLE) return
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

    private class HardwareSurfaceBuffer(
        val view: View,
    ) {
        var frontBitmap: Bitmap? = null
        var backBitmap: Bitmap? = null
        var left: Int = 0
        var top: Int = 0
        var width: Int = 0
        var height: Int = 0
        var isSurfaceView: Boolean = false
        @Volatile var isCopying: Boolean = false

        fun ensureBuffers(w: Int, h: Int) {
            if (frontBitmap == null || frontBitmap?.isRecycled == true || frontBitmap?.width != w || frontBitmap?.height != h) {
                frontBitmap?.recycle()
                frontBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            }
            if (backBitmap == null || backBitmap?.isRecycled == true || backBitmap?.width != w || backBitmap?.height != h) {
                backBitmap?.recycle()
                backBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            }
        }

        fun swap(newLeft: Int, newTop: Int, newWidth: Int, newHeight: Int, surfaceViewType: Boolean) {
            val temp = frontBitmap
            frontBitmap = backBitmap
            backBitmap = temp
            left = newLeft
            top = newTop
            width = newWidth
            height = newHeight
            isSurfaceView = surfaceViewType
        }

        fun release() {
            frontBitmap?.recycle()
            frontBitmap = null
            backBitmap?.recycle()
            backBitmap = null
            isCopying = false
        }
    }

    fun release() {
        stop()
        clearHardwareSurfaces()
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

        scale(2f, 2f, Offset.Zero) {
            drawImage(
                image = frame.bitmap.asImageBitmap(),
                topLeft = -drawOffset * 0.5f,
            )
        }
    }
}
