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
import java.util.WeakHashMap

/**
 * Local real-time backdrop bridge for the imported Kyant LiquidToggle.
 *
 * IMPORTANT DIFFERENCE FROM THE PREVIOUS IMPLEMENTATION
 * ------------------------------------------------------
 * The previous bridge captured a large ancestor at decor/window level and the
 * Compose overlay was manually repositioned when the hierarchy scrolled.
 *
 * That architecture was the source of the "scrambled/stuck" toggle bug because
 * the injected surface had a different coordinate lifetime from the native row.
 *
 * This bridge captures ONLY the native toggle's immediate parent/container and
 * the LiquidToggle is hosted in that same parent's ViewGroupOverlay.
 *
 * Therefore:
 *
 *   scrolling container
 *       ├── native toggle
 *       └── overlay LiquidToggle
 *
 * both move together through the normal Android parent transform. No global
 * scroll listener, no per-frame position chasing, and no LayoutParams relayout
 * are required.
 *
 * The actual backdrop texture is local to the containing row/container. That is
 * enough for the Liquid Glass refraction effect and is considerably cheaper than
 * capturing the entire application.
 */
internal data class LiquidToggleCapturedFrame(
    val bitmap: Bitmap,
    val originInSource: Offset,
)

internal class LiquidToggleViewBackdrop(
    private val sourceView: View,
    private val overlayViewProvider: () -> View?,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = true

    private var front: Bitmap? = null
    private var back: Bitmap? = null

    var capturedFrame: LiquidToggleCapturedFrame? by mutableStateOf(null)
        private set

    /*
     * Target region is stored in SOURCE-VIEW coordinates, not window coordinates.
     * Once the toggle's row is laid out, this remains stable while the entire row
     * scrolls. The parent itself moves on screen, so there is no need to update it
     * during scrolling.
     */
    private var region: CaptureRegion? = null
    private var lastCaptureNanos = 0L

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

        try {
            ensureBuffers(currentRegion.width, currentRegion.height)

            val target = back ?: return
            target.eraseColor(Color.TRANSPARENT)

            /*
             * Hide only our injected overlay while recording the container.
             * The native toggle itself is already suppressed by PhysicsHook.java.
             *
             * This means the captured source is:
             *
             *   row/container background + neighbouring native content
             *
             * but NOT the LiquidToggle itself. No recursive glass feedback loop.
             */
            val overlay = overlayViewProvider()
            val wasVisible = overlay?.visibility == View.VISIBLE
            if (overlay != null && wasVisible) {
                overlay.visibility = View.INVISIBLE
            }

            val canvas = Canvas(target)
            canvas.save()
            canvas.clipRect(0, 0, currentRegion.width, currentRegion.height)
            canvas.translate(-currentRegion.left, -currentRegion.top)

            sourceView.draw(canvas)

            canvas.restore()

            if (overlay != null && wasVisible) {
                overlay.visibility = View.VISIBLE
            }

            val previousFront = front
            front = target
            back = previousFront

            capturedFrame = LiquidToggleCapturedFrame(
                bitmap = target,
                originInSource = Offset(
                    currentRegion.left,
                    currentRegion.top
                ),
            )
            lastCaptureNanos = now
        } catch (_: Throwable) {
            // Restore visibility even if a HyperOS View rejects software drawing.
            try {
                val overlay = overlayViewProvider()
                if (overlay != null && overlay.visibility != View.VISIBLE) {
                    overlay.visibility = View.VISIBLE
                }
            } catch (_: Throwable) {
                // Never let a backdrop failure crash the host app.
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
        lastCaptureNanos = 0L
    }

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        val frame = capturedFrame ?: return
        val coords = coordinates ?: return

        /*
         * Convert the stable SOURCE-local bitmap origin into current window
         * coordinates, then into the current Compose draw coordinates.
         *
         * Both sourceView and the overlay share the same parent, so scrolling
         * translates them by the same amount. Their relative coordinates remain
         * stable and this calculation never needs a scroll callback.
         */
        val sourceWindow = IntArray(2)
        sourceView.getLocationInWindow(sourceWindow)

        val bitmapOriginInWindow = Offset(
            sourceWindow[0] + frame.originInSource.x,
            sourceWindow[1] + frame.originInSource.y
        )

        val targetPosition = coords.positionInWindow()
        val drawOffset = bitmapOriginInWindow - targetPosition

        drawImage(
            image = frame.bitmap.asImageBitmap(),
            topLeft = drawOffset,
        )
    }

    private data class CaptureRegion(
        val left: Float,
        val top: Float,
        val width: Int,
        val height: Int,
    )
}
