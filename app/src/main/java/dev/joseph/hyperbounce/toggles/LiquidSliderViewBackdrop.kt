package dev.joseph.hyperbounce.toggles

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.TypedValue
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

internal data class LiquidSliderCapturedFrame(
    val bitmap: Bitmap,
    val originInWindow: Offset,
)

internal class LiquidSliderViewBackdrop(private val targetView: View) : Backdrop {
    override val isCoordinatesDependent: Boolean = true
    var overlayViewToHide: View? = null

    private var front: Bitmap? = null
    private var back: Bitmap? = null
    var capturedFrame: LiquidSliderCapturedFrame? by mutableStateOf(null)
        private set

    private var paddingPx: Int = 0
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
        this.paddingPx = paddingPx
        captureNow()
    }

    fun start() {
        captureNow()
        val root = findCaptureRoot(targetView)
        root.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    fun stop() {
        val root = findCaptureRoot(targetView)
        if (root.viewTreeObserver.isAlive) {
            root.viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    fun captureNow() {
        doCapture()
    }

    private fun captureOncePerFrame() {
        if (lastCapturedFrame == frameNumber) return
        val now = System.nanoTime()
        if (now - lastCaptureNanos < minCaptureIntervalNanos) return
        doCapture()
    }

    private fun doCapture() {
        if (targetView.width <= 0 || targetView.height <= 0) return
        if (!targetView.isAttachedToWindow) return

        val targetLoc = IntArray(2)
        targetView.getLocationInWindow(targetLoc)
        if (targetLoc[0] == 0 && targetLoc[1] == 0) return

        val root = findCaptureRoot(targetView)
        val rootLoc = IntArray(2)
        root.getLocationInWindow(rootLoc)

        val capPadding = if (paddingPx > 0) paddingPx else (24f * targetView.resources.displayMetrics.density).toInt()
        val capWindowX = targetLoc[0] - capPadding
        val capWindowY = targetLoc[1] - capPadding
        val capWidth = targetView.width + capPadding * 2
        val capHeight = targetView.height + capPadding * 2

        if (capWidth <= 0 || capHeight <= 0) return

        try {
            ensureBuffers(capWidth, capHeight)
            val targetBmp = back ?: return

            val isNight = (targetView.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val typedValue = TypedValue()
            val colorSurfaceId = targetView.context.resources.getIdentifier("colorSurface", "attr", targetView.context.packageName)
            val surfaceColor = if (colorSurfaceId != 0 && targetView.context.theme.resolveAttribute(colorSurfaceId, typedValue, true) && typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                typedValue.data
            } else if (targetView.context.theme.resolveAttribute(android.R.attr.colorBackground, typedValue, true) && typedValue.type >= TypedValue.TYPE_FIRST_COLOR_INT && typedValue.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                typedValue.data
            } else {
                if (isNight) Color.parseColor("#242424") else Color.WHITE
            }

            targetBmp.eraseColor(surfaceColor)

            val canvas = Canvas(targetBmp)
            canvas.save()
            canvas.clipRect(0, 0, capWidth, capHeight)

            val rootLeftInCapture = rootLoc[0] - capWindowX
            val rootTopInCapture = rootLoc[1] - capWindowY
            canvas.translate(rootLeftInCapture.toFloat(), rootTopInCapture.toFloat())

            // Punch out target view region
            val targetLeftInRoot = targetLoc[0] - rootLoc[0]
            val targetTopInRoot = targetLoc[1] - rootLoc[1]
            canvas.clipOutRect(
                targetLeftInRoot.toFloat(),
                targetTopInRoot.toFloat(),
                (targetLeftInRoot + targetView.width).toFloat(),
                (targetTopInRoot + targetView.height).toFloat()
            )

            // Punch out overlay region and set alpha to 0 to prevent OverlayViewGroup from drawing it
            val overlay = overlayViewToHide
            val oldAlpha = overlay?.alpha ?: 1f
            if (overlay != null) {
                val overlayLoc = IntArray(2)
                overlay.getLocationInWindow(overlayLoc)
                val overlayLeftInRoot = overlayLoc[0] - rootLoc[0]
                val overlayTopInRoot = overlayLoc[1] - rootLoc[1]
                canvas.clipOutRect(
                    overlayLeftInRoot.toFloat(),
                    overlayTopInRoot.toFloat(),
                    (overlayLeftInRoot + overlay.width).toFloat(),
                    (overlayTopInRoot + overlay.height).toFloat()
                )
                overlay.alpha = 0f
            }

            root.draw(canvas)

            if (overlay != null) {
                overlay.alpha = oldAlpha
            }

            canvas.restore()

            val previousFront = front
            front = targetBmp
            back = previousFront
            capturedFrame = LiquidSliderCapturedFrame(targetBmp, Offset(capWindowX.toFloat(), capWindowY.toFloat()))
            lastCapturedFrame = frameNumber
            lastCaptureNanos = System.nanoTime()
        } catch (_: Throwable) {}
    }

    private fun findCaptureRoot(target: View): View {
        var curr: View = target.parent as? View ?: target
        var best: View = curr
        while (curr.parent is View) {
            val p = curr.parent as View
            val name = p.javaClass.name
            if (name.contains("RecyclerView") || name.contains("ListView") || name.contains("ScrollView") || name.contains("DecorView")) {
                break
            }
            curr = p
            best = curr
        }
        return best
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
}
