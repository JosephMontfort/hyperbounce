package dev.joseph.hyperbounce.lockscreen

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.PI

/**
 * Specular Fresnel Frame Drawable for 4-Sided Lock Screen Card Sheet.
 * Renders directional specular highlights using AGSL RuntimeShader on Android 13+ with smooth falloff.
 */
class LiquidGlassFrameDrawable(
    private val density: Float
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private var fresnelShader: Any? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                fresnelShader = RuntimeShader(LockScreenShaders.FRESNEL_FRAME_SHADER_SRC)
            } catch (_: Throwable) {}
        }
    }

    override fun draw(canvas: Canvas) {
        val bounds: Rect = bounds
        if (bounds.width() <= 0 || bounds.height() <= 0) return

        val cfg = LockScreenConfigHolder.sheet
        val strokeA = (cfg.borderAlpha.coerceIn(0f, 1f) * 255).toInt()
        val strokeW = cfg.borderWidth.coerceAtLeast(0.5f) * density
        val cornerR = 28f * density

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && fresnelShader is RuntimeShader) {
            try {
                val shader = fresnelShader as RuntimeShader
                shader.setFloatUniform("size", bounds.width().toFloat(), bounds.height().toFloat())
                shader.setFloatUniform("cornerRadii", 0f, 0f, cornerR, cornerR)
                shader.setColorUniform("color", 0x00FFFFFF or (strokeA shl 24))
                shader.setFloatUniform("angle", (45f * (PI / 180f)).toFloat())
                shader.setFloatUniform("falloff", 1.2f)
                strokePaint.shader = shader
                strokePaint.strokeWidth = strokeW

                val rf = RectF(bounds)
                canvas.drawRoundRect(rf, cornerR, cornerR, strokePaint)
                return
            } catch (_: Throwable) {}
        }

        strokePaint.shader = null
        strokePaint.color = 0x00FFFFFF or (strokeA shl 24)
        strokePaint.strokeWidth = strokeW
        val rf = RectF(bounds)
        canvas.drawRoundRect(rf, cornerR, cornerR, strokePaint)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
    }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
