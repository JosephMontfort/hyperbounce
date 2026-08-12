package dev.joseph.hyperbounce

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.graphics.ColorSpace
import android.os.Build
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
import de.robv.android.xposed.XposedBridge

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
    private var lastHardwareSurfaceCount = -1
    private val minCaptureIntervalNanos = 16_666_667L // ~60 Hz

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        captureOncePerFrame()
        true
    }

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameNumber++

            // Do not rely on the target app's View hierarchy being invalidated.
            // Instagram Reels can be updated by the media compositor without
            // traversing/drawing mainWrapper at all. In that case an OnPreDraw
            // listener never fires, so the backdrop would freeze on the first
            // reel frame (which contains no video pixels in View.draw()).
            // Capture once directly from the display-frame callback instead.
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
        traceOnce("backdrop START source=${sourceView.javaClass.name}")
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
            var capturedBySurfaceControl = false

            // First choice for Instagram: capture the actual SurfaceControl layer tree
            // of the underlying activity window. This includes SurfaceView/media
            // compositor layers that View.draw() cannot see.
            if (Build.VERSION.SDK_INT >= 29) {
                capturedBySurfaceControl = captureSurfaceControlRegion(
                    target = target,
                    sourceCrop = Rect(
                        localLeft.toInt().coerceAtLeast(0),
                        localTop.toInt().coerceAtLeast(0),
                        (localLeft + width).toInt().coerceAtLeast(1),
                        (localTop + height).toInt().coerceAtLeast(1),
                    ),
                    scale = scale,
                )
            }

            if (!capturedBySurfaceControl) {
                target.eraseColor(Color.parseColor("#40000000"))
                val canvas = Canvas(target)
                canvas.save()
                canvas.scale(scale, scale)
                canvas.clipRect(0, 0, width, height)
                canvas.translate(-localLeft, -localTop)
                sourceView.draw(canvas)
                canvas.restore()

                requestHardwareSurfaceFrames(currentRegion, location, scale)
                drawLatestHardwareSurfaceFrames(canvas, currentRegion, location, scale)
            }

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
            trace("capture failed: ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /**
     * Hidden framework capture path. SurfaceControl.captureLayers() captures the
     * compositor layer tree rather than only View.draw(), so a hardware video
     * SurfaceView remains available to the backdrop. The Instagram bar is placed
     * in a separate application-panel window, so this capture does not recurse into
     * our own glass overlay.
     */
    private fun captureSurfaceControlRegion(
        target: Bitmap,
        sourceCrop: Rect,
        scale: Float,
    ): Boolean {
        return try {
            val viewRootImplMethod = View::class.java.getDeclaredMethod("getViewRootImpl")
            viewRootImplMethod.isAccessible = true
            val viewRootImpl = viewRootImplMethod.invoke(sourceView) ?: return false

            val rootSurfaceMethod = viewRootImpl.javaClass.getDeclaredMethod("getSurfaceControl")
            rootSurfaceMethod.isAccessible = true
            val rootSurface = rootSurfaceMethod.invoke(viewRootImpl) ?: return false

            val scClass = Class.forName("android.view.SurfaceControl")
            val argsClass = Class.forName("android.view.SurfaceControl\$LayerCaptureArgs")
            val builderClass = Class.forName("android.view.SurfaceControl\$LayerCaptureArgs\$Builder")
            val builderCtor = builderClass.getConstructor(scClass)
            val builder = builderCtor.newInstance(rootSurface)

            builderClass.getMethod("setSourceCrop", Rect::class.java)
                .invoke(builder, sourceCrop)
            builderClass.getMethod("setFrameScale", Float::class.javaPrimitiveType)
                .invoke(builder, scale)
            builderClass.getMethod(
                "setPixelFormat",
                Int::class.javaPrimitiveType,
            ).invoke(builder, android.graphics.PixelFormat.RGBA_8888)
            builderClass.getMethod("setChildrenOnly", Boolean::class.javaPrimitiveType)
                .invoke(builder, false)

            val args = builderClass.getMethod("build").invoke(builder)
            val captureMethod = scClass.getDeclaredMethod(
                "captureLayers",
                argsClass,
            )
            captureMethod.isAccessible = true
            val screenshot = captureMethod.invoke(null, args) ?: return false

            val asBitmapMethod = screenshot.javaClass.getMethod("asBitmap")
            val bitmap = asBitmapMethod.invoke(screenshot) as? Bitmap ?: return false

            val copy = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: return false
            if (copy.width != target.width || copy.height != target.height) {
                Canvas(target).drawBitmap(
                    copy,
                    null,
                    Rect(0, 0, target.width, target.height),
                    null,
                )
                copy.recycle()
            } else {
                Canvas(target).drawBitmap(copy, 0f, 0f, null)
                copy.recycle()
            }

            traceOnce("SurfaceControl backdrop capture ACTIVE")
            true
        } catch (t: Throwable) {
            traceOnce("SurfaceControl capture unavailable: ${t.javaClass.simpleName}: ${t.message}")
            false
        }
    }

    private var traceState = ""
    private fun traceOnce(message: String) {
        if (traceState == message) return
        traceState = message
        try {
            Log.e("HyperBounce-Backdrop", message)
            XposedBridge.log("[HyperBounce-Backdrop] $message")
        } catch (_: Throwable) {}
    }

    private fun trace(message: String) {
        try {
            Log.e("HyperBounce-Backdrop", message)
            XposedBridge.log("[HyperBounce-Backdrop] $message")
        } catch (_: Throwable) {}
    }

    private fun requestHardwareSurfaceFrames(
        currentRegion: RectFState,
        sourceLocation: IntArray,
        scale: Float,
    ) {
        val surfaces = ArrayList<View>()
        val root = sourceView.rootView
        collectHardwareSurfaceViews(root, surfaces)

        // Avoid duplicate requests if the hierarchy traversal encounters a view
        // through an unusual parent/SurfaceControl arrangement.
        val uniqueSurfaces = surfaces.distinctBy { System.identityHashCode(it) }
        if (uniqueSurfaces.size != lastHardwareSurfaceCount) {
            lastHardwareSurfaceCount = uniqueSurfaces.size
            Log.e(
                "HyperBounce-Backdrop",
                "root=${root.javaClass.name} hardwareSurfaces=${uniqueSurfaces.size}",
            )
        }

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
                                    Log.d("HyperBounce-Backdrop", "PixelCopy SurfaceView failed=$result")
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
