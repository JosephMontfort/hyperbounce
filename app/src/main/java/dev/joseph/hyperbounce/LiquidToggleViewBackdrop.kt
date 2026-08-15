package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.Choreographer
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

internal data class LiquidToggleCapturedFrame(
    val bitmap: Bitmap,
    val originInWindow: Offset,
)

internal class LiquidToggleViewBackdrop(private val sourceView: View) : Backdrop {
    override val isCoordinatesDependent: Boolean = true
    var overlayViewToHide: View? = null

    private var front: Bitmap? = null
    private var back: Bitmap? = null
    var capturedFrame: LiquidToggleCapturedFrame? by mutableStateOf(null)
        private set

    private var region: CaptureRegion? = null
    private var frameNumber = 0
    private var lastCapturedFrame = -1
    private var lastCaptureNanos = 0L
    private val minCaptureIntervalNanos = 16_666_667L

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
        region = CaptureRegion(
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

        try {
            ensureBuffers(currentRegion.width, currentRegion.height)
            val target = back ?: return
            
            val isNight = (sourceView.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            target.eraseColor(if (isNight) Color.parseColor("#242424") else Color.WHITE)

            val canvas = Canvas(target)
            canvas.save()
            canvas.clipRect(0, 0, currentRegion.width, currentRegion.height)
            canvas.translate(-localLeft.toFloat(), -localTop.toFloat())

            val hiddenStates = mutableListOf<Pair<View, Int>>()
            
            fun hideToggles(v: View) {
                val name = v.javaClass.simpleName
                if (v === overlayViewToHide || name.contains("SlidingButton") || name.contains("SlidingSwitch")) {
                    hiddenStates.add(Pair(v, v.visibility))
                    v.visibility = View.INVISIBLE
                } else if (v is ViewGroup) {
                    for (i in 0 until v.childCount) {
                        hideToggles(v.getChildAt(i))
                    }
                }
            }
            
            hideToggles(sourceView)
            sourceView.draw(canvas)

            for ((child, oldVis) in hiddenStates) {
                child.visibility = oldVis
            }

            canvas.restore()

            val previousFront = front
            front = target
            back = previousFront
            capturedFrame = LiquidToggleCapturedFrame(target, Offset(currentRegion.left.toFloat(), currentRegion.top.toFloat()))
            lastCapturedFrame = frameNumber
            lastCaptureNanos = now
        } catch (_: Throwable) {}
    }

    private fun ensureBuffers(width: Int, height: Int) {
        if (front?.width != width || front?.height != height) {
            front?.recycle()
            back?.recycle()
            front = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            back = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
    }

    fun release() {
        stop()
        front?.recycle()
        back?.recycle()
        front = null
        back = null
        capturedFrame = null
        region = null
        lastCapturedFrame = -1
        lastCaptureNanos = 0L
        overlayViewToHide = null
    }

    override fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        val frame = capturedFrame ?: return
        val coords = coordinates ?: return
        val drawOffset = coords.positionInWindow() - frame.originInWindow
        drawImage(image = frame.bitmap.asImageBitmap(), topLeft = -drawOffset)
    }

    private data class CaptureRegion(val left: Float, val top: Float, val width: Int, val height: Int)
}
