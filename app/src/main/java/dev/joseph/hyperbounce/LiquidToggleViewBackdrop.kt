package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
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
 * Live real-time backdrop bridge used by the imported Kyant LiquidToggle.
 *
 * This intentionally follows the same architecture as the working Liquid Glass
 * floating navigation branch:
 *
 *   target app View hierarchy
 *        -> small live software-rendered bitmap
 *        -> Kyant Backdrop abstraction
 *        -> library RenderEffect/AGSL lens + blur pipeline
 *
 * No PixelCopy is used.
 *
 * The source view is an ancestor inside the application's content hierarchy,
 * deliberately kept OUTSIDE the injected Compose overlay at the window/decor
 * level. That prevents the glass toggle from capturing itself.
 */
internal data class LiquidToggleCapturedFrame(
    val bitmap: Bitmap,
    val originInWindow: Offset,
)

internal class LiquidToggleViewBackdrop(
    private val sourceView: View,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    private var front: Bitmap? = null
    private var back: Bitmap? = null

    var capturedFrame: LiquidToggleCapturedFrame? by mutableStateOf(null)
        private set

    private var region: CaptureRegion? = null
    private var lastCaptureNanos = 0L

    // Same proven live cadence as the floating-nav implementation: real-time
    // rendering while avoiding an unnecessary 120 Hz software Canvas capture.
    private val minCaptureIntervalNanos = 16_666_667L

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        captureOncePerFrame()
        true
    }

    fun setTargetBounds(
        left: Float,
        top: Float,
        width: Int,
        height: Int,
        paddingPx: Int,
    ) {
        region = CaptureRegion(
            left = left - paddingPx,
            top = top - paddingPx,
            width = width + paddingPx * 2,
            height = height + paddingPx * 2,
        )
    }

    fun start() {
        sourceView.viewTreeObserver.addOnPreDrawListener(preDrawListener)
    }

    fun stop() {
        sourceView.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
    }

    private fun captureOncePerFrame() {
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
            target.eraseColor(android.graphics.Color.TRANSPARENT)

            val canvas = Canvas(target)
            canvas.save()
            canvas.clipRect(0, 0, currentRegion.width, currentRegion.height)
            canvas.translate(-localLeft, -localTop)

            // PhysicsHook's Liquid Glass mode suppresses the native toggle's
            // onDraw, so this live source contains the surrounding system-app
            // content without recursively drawing the glass Compose overlay.
            sourceView.draw(canvas)

            canvas.restore()

            val previousFront = front
            front = target
            back = previousFront

            capturedFrame = LiquidToggleCapturedFrame(
                bitmap = target,
                originInWindow = Offset(currentRegion.left, currentRegion.top),
            )
            lastCaptureNanos = now
        } catch (_: Throwable) {
            // Keep the last valid frame. Some HyperOS framework views reject
            // software recording; failure must never crash the hooked process.
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

        drawImage(
            image = frame.bitmap.asImageBitmap(),
            topLeft = -drawOffset,
        )
    }

    private data class CaptureRegion(
        val left: Float,
        val top: Float,
        val width: Int,
        val height: Int,
    )
}
