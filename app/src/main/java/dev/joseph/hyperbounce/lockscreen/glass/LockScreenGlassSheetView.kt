package dev.joseph.hyperbounce.lockscreen.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup

class LockScreenGlassSheetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val renderer: GlassLensRenderer? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GlassLensRenderer()
        } else null

    private val fallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x24FFFFFF
        style = Paint.Style.FILL
    }

    private val boundsRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val density = resources.displayMetrics.density
        val bottomCornerR = 36f * density
        val margin = (36f * density).toInt()

        val backdropSource = findBackdropSource()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && renderer != null && backdropSource != null) {
            val halfW = w / 2f
            val halfH = h / 2f
            val cornerRadii = floatArrayOf(0f, 0f, bottomCornerR, bottomCornerR)

            val params = GlassLensRenderer.LensParams(
                blurRadius = 18f * density,
                shapeCX = halfW,
                shapeCY = halfH,
                shapeHW = halfW,
                shapeHH = halfH,
                cornerRadii = cornerRadii,
                bevel = 32f * density,
                refract = 36f * density,
                dispersion = 0.20f,
                lightX = 0f,
                lightY = 1f,
                spec = 0.85f,
                innerShadow = 0.40f,
                tint = 0x1AFFFFFF,
                glassTint = 0x00000000,
                dim = 0.04f,
                saturation = 120f
            )

            val success = renderer.draw(canvas, this, backdropSource, params, margin)
            if (success) return
        }

        // Fallback for non-hardware or API < 33
        boundsRect.set(0f, 0f, w, h)
        val radii = floatArrayOf(0f, 0f, 0f, 0f, bottomCornerR, bottomCornerR, bottomCornerR, bottomCornerR)
        val path = android.graphics.Path().apply {
            addRoundRect(boundsRect, radii, android.graphics.Path.Direction.CW)
        }
        canvas.drawPath(path, fallbackPaint)
    }

    private fun findBackdropSource(): View? {
        var current: View? = parent as? View
        var topMost: View? = current
        while (current != null) {
            topMost = current
            val name = current.javaClass.simpleName
            if (name.contains("NotificationShadeWindowView") ||
                name.contains("NotificationPanelView") ||
                name.contains("KeyguardPanelView")
            ) {
                return current
            }
            current = current.parent as? View
        }
        return topMost
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            renderer?.release()
        }
    }
}
