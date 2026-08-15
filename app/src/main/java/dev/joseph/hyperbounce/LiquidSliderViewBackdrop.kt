package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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

/**
 * Backdrop capture used by the library LiquidSlider.
 *
 * The slider is embedded in a potentially scrolling parent, so the backdrop
 * is captured from the immediate parent layer rather than from the slider
 * itself. The important performance rule is that the parent is NOT redrawn
 * every frame. A synchronous ViewGroup#draw() inside every SystemUI pre-draw
 * was starving the UI thread and made the entire host app appear frozen.
 *
 * We therefore capture only when the backdrop becomes dirty (initial attach,
 * target/layout movement, or parent scrolling), with a small safety interval.
 * The resulting bitmap remains valid while the slider animates because the
 * slider itself is excluded from the capture.
 */
internal class LiquidSliderViewBackdrop(
    private val sourceParent: ViewGroup,
    private val targetView: View,
) : Backdrop {
    override val isCoordinatesDependent: Boolean = true

    var overlayViewToHide: View? = null

    private var front: Bitmap? = null
    private var back: Bitmap? = null
    var capturedFrame: CapturedFrame? by mutableStateOf(null)
        private set

    private var region: CaptureRegion? = null
    private var captureDirty = true
    private var lastCaptureNanos = 0L

    // The parent is synchronously redrawn only when dirty. This interval is a
    // guard against pathological scroll/layout callback storms in SystemUI.
    private val minCaptureIntervalNanos = 50_000_000L // 20 captures/sec max

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        captureIfNeeded()
        true
    }

    private val scrollListener = ViewTreeObserver.OnScrollChangedListener {
        requestCapture()
    }

    fun setTargetBounds(left: Float, top: Float, width: Int, height: Int, paddingPx: Int) {
        region = CaptureRegion(
            left = left - paddingPx,
            top = top - paddingPx,
            width = width + paddingPx * 2,
            height = height + paddingPx * 2,
        )
        requestCapture()
    }

    fun requestCapture() {
        captureDirty = true
    }

    fun start() {
        sourceParent.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        sourceParent.viewTreeObserver.addOnScrollChangedListener(scrollListener)
        requestCapture()
    }

    fun stop() {
        sourceParent.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        sourceParent.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
    }

    private fun captureIfNeeded() {
        if (!captureDirty) return

        val now = System.nanoTime()
        if (now - lastCaptureNanos < minCaptureIntervalNanos) return

        val currentRegion = region ?: return
        if (currentRegion.width <= 0 || currentRegion.height <= 0) return
        if (!sourceParent.isAttachedToWindow) return

        val location = IntArray(2)
        sourceParent.getLocationInWindow(location)
        val localLeft = currentRegion.left - location[0]
        val localTop = currentRegion.top - location[1]

        var overlayVisibility = View.VISIBLE
        var canvas: Canvas? = null

        try {
            ensureBuffers(currentRegion.width, currentRegion.height)
            val target = back ?: return

            val isNight = (sourceParent.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            target.eraseColor(if (isNight) Color.BLACK else Color.WHITE)

            canvas = Canvas(target)
            canvas.save()
            canvas.clipRect(0, 0, currentRegion.width, currentRegion.height)
            canvas.translate(-localLeft.toFloat(), -localTop.toFloat())

            // The native slider already has alpha=0, so changing its visibility
            // here would cause extra layout/invalidation work. Only hide the
            // Compose overlay so it cannot be captured into its own backdrop.
            overlayVisibility = overlayViewToHide?.visibility ?: View.VISIBLE
            overlayViewToHide?.visibility = View.INVISIBLE

            sourceParent.draw(canvas)

            overlayViewToHide?.visibility = overlayVisibility
            canvas.restore()

            val previousFront = front
            front = target
            back = previousFront
            capturedFrame = CapturedFrame(
                target,
                Offset(currentRegion.left.toFloat(), currentRegion.top.toFloat())
            )

            captureDirty = false
            lastCaptureNanos = now
        } catch (_: Throwable) {
            // Backdrop capture must never take down the host system app.
            try {
                overlayViewToHide?.visibility = overlayVisibility
            } catch (_: Throwable) {
            }
            try {
                canvas?.restore()
            } catch (_: Throwable) {
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

    fun release() {
        stop()
        front?.recycle()
        back?.recycle()
        front = null
        back = null
        capturedFrame = null
        region = null
        captureDirty = false
        lastCaptureNanos = 0L
        overlayViewToHide = null
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val frame = capturedFrame ?: return
        val coords = coordinates ?: return
        val drawOffset = coords.positionInWindow() - frame.originInWindow
        drawImage(image = frame.bitmap.asImageBitmap(), topLeft = -drawOffset)
    }

    private data class CapturedFrame(
        val bitmap: Bitmap,
        val originInWindow: Offset,
    )

    private data class CaptureRegion(
        val left: Float,
        val top: Float,
        val width: Int,
        val height: Int,
    )
}
