package dev.joseph.hyperbounce.lockscreen.glass

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.View
import dev.joseph.hyperbounce.lockscreen.LockScreenConfigHolder
import kotlin.math.max

class LockScreenGlassSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val renderer: GlassLensRenderer? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GlassLensRenderer()
        } else null

    private var wallpaperDrawable: Drawable? = null
    private var lastWallpaperFetchTime = 0L

    private val fallbackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80FFFFFF.toInt()
        style = Paint.Style.STROKE
    }
    private val boundsRect = RectF()
    private val location = IntArray(2)
    private val parentLocation = IntArray(2)

    private fun getWallpaper(): Drawable? {
        val now = SystemClock.uptimeMillis()
        if (wallpaperDrawable == null || now - lastWallpaperFetchTime > 4000L) {
            lastWallpaperFetchTime = now
            try {
                val wm = WallpaperManager.getInstance(context)
                wallpaperDrawable = wm.drawable ?: wm.peekDrawable()
            } catch (t: Throwable) {
                Log.w("HyperBounce_LockSheet", "Failed to retrieve lockscreen wallpaper", t)
            }
        }
        return wallpaperDrawable
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parent = parent as? View
        val w = if (parent != null && parent.width > 0) parent.width else resources.displayMetrics.widthPixels
        val h = if (parent != null && parent.height > 0) parent.height else resources.displayMetrics.heightPixels
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        if (LiquidGlassKeyDrawable.isCapturingGlobalBackdrop) return
        super.onDraw(canvas)
        if (visibility != VISIBLE) return

        val parent = parent as? View
        val w = if (width > 0) width else (parent?.width ?: resources.displayMetrics.widthPixels)
        val h = if (height > 0) height else (parent?.height ?: resources.displayMetrics.heightPixels)
        if (w <= 0 || h <= 0) return

        val density = resources.displayMetrics.density
        val cfg = LockScreenConfigHolder.sheet

        val leftBevel = cfg.leftBevelWidth * density
        val rightBevel = cfg.rightBevelWidth * density
        val bottomBevel = cfg.bottomBevelWidth * density
        val maxBevel = max(max(leftBevel, rightBevel), bottomBevel)
        val refractAmount = cfg.refractionHeight * density
        val cornerRadius = cfg.cornerRadius * density
        val margin = ((maxBevel + refractAmount * 0.4f) + 16f * density).toInt().coerceAtLeast(32)

        val wp = getWallpaper()
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        getLocationOnScreen(location)
        val viewX = location[0]
        val viewY = location[1]

        val backdrop = rootView ?: (parent as? View)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && renderer != null) {
            val params = GlassLensRenderer.LensParams(
                cornerRadius = cornerRadius,
                leftBevel = leftBevel,
                rightBevel = rightBevel,
                bottomBevel = bottomBevel,
                refract = refractAmount,
                dispersion = cfg.dispersionStrength,
                lightX = 0f,
                lightY = 1f,
                spec = cfg.specularStrength,
                innerShadow = cfg.innerShadow,
                saturation = cfg.saturationFactor,
                blurRadius = cfg.blurRadius * density
            )

            val success = renderer.draw(canvas, w, h, params, margin) { recordingCanvas ->
                var capturedLive = false
                if (backdrop != null && backdrop.width > 0 && backdrop.height > 0 && backdrop !== this) {
                    backdrop.getLocationOnScreen(parentLocation)
                    val offsetX = (viewX - parentLocation[0]).toFloat()
                    val offsetY = (viewY - parentLocation[1]).toFloat()

                    recordingCanvas.save()
                    recordingCanvas.translate(margin.toFloat() - offsetX, margin.toFloat() - offsetY)
                    LiquidGlassKeyDrawable.isCapturingGlobalBackdrop = true
                    LiquidGlassKeyDrawable.setTransitionVisibilitySafe(this, View.INVISIBLE)
                    try {
                        backdrop.draw(recordingCanvas)
                        capturedLive = true
                    } catch (t: Throwable) {
                        Log.w("HyperBounce_LockSheet", "Live backdrop capture error", t)
                    } finally {
                        LiquidGlassKeyDrawable.setTransitionVisibilitySafe(this, View.VISIBLE)
                        LiquidGlassKeyDrawable.isCapturingGlobalBackdrop = false
                        recordingCanvas.restore()
                    }
                }

                if (!capturedLive) {
                    recordingCanvas.save()
                    recordingCanvas.translate(margin.toFloat() - viewX.toFloat(), margin.toFloat() - viewY.toFloat())
                    if (wp != null) {
                        wp.setBounds(0, 0, screenW, screenH)
                        wp.draw(recordingCanvas)
                    } else {
                        val p = Paint().apply {
                            shader = LinearGradient(
                                0f, 0f, 0f, screenH.toFloat(),
                                0xFF1C1C1E.toInt(), 0xFF0A0A0C.toInt(),
                                Shader.TileMode.CLAMP
                            )
                        }
                        recordingCanvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), p)
                    }
                    recordingCanvas.restore()
                }
            }
            if (success) return
        }

        // Fallback for non-hardware or API < 33
        boundsRect.set(0f, 0f, w.toFloat(), h.toFloat())
        val radii = floatArrayOf(0f, 0f, 0f, 0f, cornerRadius, cornerRadius, cornerRadius, cornerRadius)
        val path = Path().apply {
            addRoundRect(boundsRect, radii, Path.Direction.CW)
        }
        fallbackBorderPaint.strokeWidth = 2.5f * density
        canvas.drawPath(path, fallbackBorderPaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            renderer?.release()
        }
        wallpaperDrawable = null
    }
}
