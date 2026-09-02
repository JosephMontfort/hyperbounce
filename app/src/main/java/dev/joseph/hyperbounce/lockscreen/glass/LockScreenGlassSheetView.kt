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

    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x30FFFFFF
        style = Paint.Style.FILL
    }
    private val fallbackBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80FFFFFF.toInt()
        style = Paint.Style.STROKE
    }
    private val boundsRect = RectF()
    private val location = IntArray(2)

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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        val density = resources.displayMetrics.density
        val cornerRadius = 38f * density
        val bevelWidth = 34f * density
        val refractAmount = 38f * density
        val margin = (36f * density).toInt()

        val wp = getWallpaper()
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels

        getLocationOnScreen(location)
        val viewX = location[0].toFloat()
        val viewY = location[1].toFloat()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && renderer != null) {
            val params = GlassLensRenderer.LensParams(
                blurRadius = 22f * density,
                cornerRadius = cornerRadius,
                bevel = bevelWidth,
                refract = refractAmount,
                dispersion = 0.22f,
                lightX = 0f,
                lightY = 1f,
                spec = 0.90f,
                innerShadow = 0.40f,
                tint = 0x2AFFFFFF,
                glassTint = 0x00000000,
                dim = 0.04f,
                saturation = 125f
            )

            val success = renderer.draw(canvas, w, h, params, margin) { recordingCanvas ->
                recordingCanvas.translate(margin.toFloat() - viewX, margin.toFloat() - viewY)
                if (wp != null) {
                    wp.setBounds(0, 0, screenW, screenH)
                    wp.draw(recordingCanvas)
                } else {
                    // Fallback rich backdrop gradient if wallpaper is protected
                    val p = Paint().apply {
                        shader = LinearGradient(
                            0f, 0f, 0f, screenH.toFloat(),
                            0xFF1C1C1E.toInt(), 0xFF0A0A0C.toInt(),
                            Shader.TileMode.CLAMP
                        )
                    }
                    recordingCanvas.drawRect(0f, 0f, screenW.toFloat(), screenH.toFloat(), p)
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
        canvas.drawPath(path, fallbackPaint)
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
