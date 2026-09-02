package dev.joseph.hyperbounce.lockscreen

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * Pure Canvas + AGSL RuntimeShader Circle Glass Drawable for PIN Pad keys (Legacy Fallback).
 */
class CircleGlassDrawable(
    private val density: Float
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    var highlightAlpha: Float = 0f
    var touchX: Float = -1f
    var touchY: Float = -1f

    private var radialRuntimeShader: Any? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                radialRuntimeShader = RuntimeShader(LockScreenShaders.RADIAL_TOUCH_SHADER_SRC)
            } catch (_: Throwable) {}
        }
    }

    override fun draw(canvas: Canvas) {
        val bounds: Rect = bounds
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()

        val cfg = LockScreenConfigHolder.keypad
        var radius = (cfg.circleDiameter / 2.0f) * density
        if (radius <= 0f) radius = 41f * density

        val baseFill = 0.20f
        val boost = 0.35f * highlightAlpha
        val totalFill = (baseFill + boost).coerceAtMost(1.0f)

        val fillA = (totalFill * 255).toInt()
        fillPaint.color = 0x00FFFFFF or (fillA shl 24)

        val strokeA = (0.47f * 255).toInt()
        strokePaint.strokeWidth = 1.5f * density
        val sweepGrad = SweepGradient(
            cx, cy,
            intArrayOf(
                0x00FFFFFF or (strokeA shl 24),
                0x00FFFFFF or (((strokeA * 0.45f).toInt()) shl 24),
                0x00FFFFFF or (((strokeA * 0.90f).toInt()) shl 24),
                0x00FFFFFF or (strokeA shl 24)
            ),
            floatArrayOf(0.0f, 0.45f, 0.75f, 1.0f)
        )
        strokePaint.shader = sweepGrad

        canvas.drawCircle(cx, cy, radius, fillPaint)
        canvas.drawCircle(cx, cy, radius, strokePaint)

        if (highlightAlpha > 0.01f) {
            val pX = if (touchX >= 0) touchX else cx
            val pY = if (touchY >= 0) touchY else cy
            val glowA = (0.35f * highlightAlpha * 255).toInt()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && radialRuntimeShader is RuntimeShader) {
                try {
                    val shader = radialRuntimeShader as RuntimeShader
                    shader.setFloatUniform("size", bounds.width().toFloat(), bounds.height().toFloat())
                    shader.setColorUniform("color", 0x00FFFFFF or (glowA shl 24))
                    shader.setFloatUniform("radius", radius * 1.4f)
                    shader.setFloatUniform("position", pX, pY)
                    glowPaint.shader = shader
                    canvas.drawCircle(cx, cy, radius, glowPaint)
                } catch (_: Throwable) {
                    glowPaint.shader = null
                    glowPaint.color = 0x00FFFFFF or (((glowA * 0.65f).toInt()) shl 24)
                    canvas.drawCircle(pX, pY, radius * 0.75f, glowPaint)
                }
            } else {
                val radGrad = RadialGradient(
                    pX, pY, radius * 1.12f,
                    0x00FFFFFF or (glowA shl 24),
                    0x00FFFFFF,
                    Shader.TileMode.CLAMP
                )
                glowPaint.shader = radGrad
                canvas.drawCircle(cx, cy, radius, glowPaint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        glowPaint.colorFilter = colorFilter
    }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
