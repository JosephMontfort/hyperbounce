package dev.joseph.hyperbounce.controlcenter

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator

/**
 * Custom Anti-Aliased Rotate Icon Badge Drawable for Sliders and 2-Tile/Asymmetric Groups.
 * Designed with a sleek dark pill background and dual circular circulating vector arrows
 * matching the HyperOS / iOS control center edit badge aesthetic.
 */
class RotateIconDrawable(private val density: Float = 2.75f) : Drawable() {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6202022") // Rich frosted dark disc
        style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#26FFFFFF") // 15% white subtle border
        style = Paint.Style.STROKE
        strokeWidth = 1.0f * density
    }
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.0f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val arcRect = RectF()

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val r = Math.min(w, h) / 2f - (0.5f * density)
        if (r <= 0f) return

        // 1. Draw circular pill background disc and subtle border
        canvas.drawCircle(cx, cy, r, bgPaint)
        canvas.drawCircle(cx, cy, r - 0.5f * density, borderPaint)

        // 2. Dual circular circulating arcs
        val orbitR = r * 0.52f
        arcRect.set(cx - orbitR, cy - orbitR, cx + orbitR, cy + orbitR)

        // Top-right to bottom-left arc (sweeps from 25° clockwise by 125° to 150°)
        canvas.drawArc(arcRect, 25f, 125f, false, glyphPaint)
        // Bottom-left to top-right arc (sweeps from 205° clockwise by 125° to 330°)
        canvas.drawArc(arcRect, 205f, 125f, false, glyphPaint)

        // 3. Arrowheads at ends of arcs
        val headSize = 3.6f * density
        drawArrowHead(canvas, cx, cy, orbitR, 150.0, headSize)
        drawArrowHead(canvas, cx, cy, orbitR, 330.0, headSize)
    }

    private fun drawArrowHead(canvas: Canvas, cx: Float, cy: Float, orbitR: Float, angleDeg: Double, headSize: Float) {
        val rad = Math.toRadians(angleDeg)
        val endX = (cx + orbitR * Math.cos(rad)).toFloat()
        val endY = (cy + orbitR * Math.sin(rad)).toFloat()

        // Tangent direction pointing along clockwise motion: angleDeg + 90
        val tangentRad = Math.toRadians(angleDeg + 90.0)

        // Arrow wings point backwards from tangent
        val wingAngle1 = tangentRad + Math.toRadians(145.0)
        val wingAngle2 = tangentRad - Math.toRadians(145.0)

        val p1X = (endX + headSize * Math.cos(wingAngle1)).toFloat()
        val p1Y = (endY + headSize * Math.sin(wingAngle1)).toFloat()
        val p2X = (endX + headSize * Math.cos(wingAngle2)).toFloat()
        val p2Y = (endY + headSize * Math.sin(wingAngle2)).toFloat()

        canvas.drawLine(endX, endY, p1X, p1Y, glyphPaint)
        canvas.drawLine(endX, endY, p2X, p2Y, glyphPaint)
    }

    override fun setAlpha(alpha: Int) {
        bgPaint.alpha = (alpha * 0.9f).toInt()
        borderPaint.alpha = (alpha * 0.15f).toInt()
        glyphPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        glyphPaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Custom Anti-Aliased Curved Resize Handle Drawable.
 * Renders a sleek curved arc pill hugging the bottom-right corner of tiles and cards,
 * exactly matching the iOS/HyperOS interactive drag handle design.
 */
open class CurvedResizeHandleDrawable(private val density: Float = 2.75f) : Drawable() {
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4.8f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000") // 60% dark translucent shadow for contrast
        style = Paint.Style.STROKE
        strokeWidth = 6.4f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val arcPath = Path()
    private val arcRect = RectF()

    override fun draw(canvas: Canvas) {
        val bLeft = bounds.left.toFloat()
        val bTop = bounds.top.toFloat()
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        // In a bounding box (e.g. 34dp x 34dp):
        // Curve along bottom-right quadrant respecting bounds position
        val inset = 4.0f * density
        val radius = (w - inset) * 0.70f
        val cx = bLeft + w - inset - radius
        val cy = bTop + h - inset - radius

        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        arcPath.reset()
        arcPath.arcTo(arcRect, 12f, 66f, false)

        // Drop shadow for crisp visibility on light wallpapers
        canvas.drawPath(arcPath, shadowPaint)
        // Main white curved pill arc
        canvas.drawPath(arcPath, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        strokePaint.alpha = alpha
        shadowPaint.alpha = (alpha * 0.5f).toInt()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        strokePaint.colorFilter = colorFilter
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

class ResizeHandleDrawable(density: Float = 2.75f) : CurvedResizeHandleDrawable(density)

/**
 * Live Morphing Ghost Overlay Drawable for tile resizing.
 */
class TileResizeGhostDrawable(private val density: Float = 2.75f) : Drawable() {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#4DFFFFFF") // 30% translucent white
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = Color.parseColor("#B3FFFFFF") // 70% white border
    }
    private val snapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.0f * density
        color = Color.parseColor("#FF0A84FF") // iOS Blue snap highlight
    }

    private val rect = RectF()
    private var cornerRadius = 24f * density
    private var isSnapping = false

    fun setOriginBounds(left: Float, top: Float, width: Float, height: Float, initialRadius: Float? = null) {
        rect.set(left, top, left + width, top + height)
        cornerRadius = initialRadius ?: (Math.min(width, height) / 2.0f)
        setBounds(left.toInt(), top.toInt(), (left + width).toInt(), (top + height).toInt())
        invalidateSelf()
    }

    fun updateMorph(left: Float, top: Float, width: Float, height: Float, radius: Float, snapping: Boolean) {
        rect.set(left, top, left + width, top + height)
        isSnapping = snapping
        cornerRadius = radius
        setBounds(left.toInt(), top.toInt(), (left + width).toInt(), (top + height).toInt())
        invalidateSelf()
    }

    fun updateMorph(left: Float, top: Float, width: Float, height: Float, snapping: Boolean) {
        val maxDim = Math.max(width, height)
        val minDim = Math.min(width, height)
        val r = if (maxDim / minDim > 1.3f) 24f * density else minDim / 2f
        updateMorph(left, top, width, height, r, snapping)
    }

    override fun draw(canvas: Canvas) {
        if (rect.isEmpty) return
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, fillPaint)
        if (isSnapping) {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, snapPaint)
        } else {
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, borderPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = (alpha * 0.3f).toInt()
        borderPaint.alpha = alpha
        snapPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
        snapPaint.colorFilter = colorFilter
    }

    private var morphAnimator: ValueAnimator? = null

    fun animateToAndFadeOut(
        targetLeft: Float,
        targetTop: Float,
        targetWidth: Float,
        targetHeight: Float,
        targetRadius: Float,
        durationMs: Long = 180L,
        onEnd: (() -> Unit)? = null
    ) {
        morphAnimator?.cancel()
        val startRect = RectF(rect)
        val targetRect = RectF(targetLeft, targetTop, targetLeft + targetWidth, targetTop + targetHeight)
        val startRadius = cornerRadius
        val startAlpha = 255

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                val f = va.animatedFraction
                rect.set(
                    startRect.left + (targetRect.left - startRect.left) * f,
                    startRect.top + (targetRect.top - startRect.top) * f,
                    startRect.right + (targetRect.right - startRect.right) * f,
                    startRect.bottom + (targetRect.bottom - startRect.bottom) * f
                )
                cornerRadius = startRadius + (targetRadius - startRadius) * f
                val currentAlpha = (startAlpha * (1f - f)).toInt().coerceIn(0, 255)
                setAlpha(currentAlpha)
                setBounds(rect.left.toInt(), rect.top.toInt(), rect.right.toInt(), rect.bottom.toInt())
                invalidateSelf()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    morphAnimator = null
                    onEnd?.invoke()
                }
                override fun onAnimationCancel(animation: Animator) {
                    morphAnimator = null
                    onEnd?.invoke()
                }
            })
        }
        morphAnimator = anim
        anim.start()
    }

    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Custom Anti-Aliased Selection Circle Badge Drawable (Hollow vs Checked).
 */
class SelectCircleDrawable : Drawable() {
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E6FFFFFF")
        style = Paint.Style.STROKE
        strokeWidth = 3.0f
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D84FF")
        style = Paint.Style.FILL
    }
    private val checkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var isChecked = false

    fun setChecked(checked: Boolean) {
        this.isChecked = checked
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w / 2.0f
        val cy = h / 2.0f
        val radius = Math.min(w, h) / 2.0f - 2.0f

        if (isChecked) {
            canvas.drawCircle(cx, cy, radius, bgPaint)
            val checkPath = Path().apply {
                moveTo(cx - radius * 0.45f, cy)
                lineTo(cx - radius * 0.10f, cy + radius * 0.40f)
                lineTo(cx + radius * 0.50f, cy - radius * 0.35f)
            }
            canvas.drawPath(checkPath, checkPaint)
        } else {
            val darkBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#80000000")
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, radius, darkBg)
            canvas.drawCircle(cx, cy, radius, ringPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        ringPaint.alpha = alpha
        bgPaint.alpha = alpha
    }
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Custom Anti-Aliased 4-Squares Group Vector Drawable for Header Button.
 */
class GroupGridDrawable(active: Boolean) : Drawable() {
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (active) Color.parseColor("#0D84FF") else Color.WHITE
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0 || h <= 0) return

        val pad = w * 0.22f
        val size = (w - pad * 2.0f - w * 0.12f) / 2.0f
        val gap = w * 0.12f
        val corner = size * 0.28f

        // Box 1 (top-left)
        canvas.drawRoundRect(pad, pad, pad + size, pad + size, corner, corner, boxPaint)
        // Box 2 (top-right)
        canvas.drawRoundRect(pad + size + gap, pad, pad + size + gap + size, pad + size, corner, corner, boxPaint)
        // Box 3 (bottom-left)
        canvas.drawRoundRect(pad, pad + size + gap, pad + size, pad + size + gap + size, corner, corner, boxPaint)
        // Box 4 (bottom-right)
        canvas.drawRoundRect(pad + size + gap, pad + size + gap, pad + size + gap + size, pad + size + gap + size, corner, corner, boxPaint)
    }

    override fun setAlpha(alpha: Int) { boxPaint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Custom Anti-Aliased Disassemble / Ungroup Header Vector Drawable.
 */
class UngroupHeaderDrawable(active: Boolean) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (active) Color.parseColor("#FF453A") else Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.0f
        strokeCap = Paint.Cap.ROUND
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0 || h <= 0) return

        val cx = w * 0.5f
        val cy = h * 0.5f
        val off = w * 0.22f
        val d = w * 0.08f

        // 4 outward corners mark
        canvas.drawLine(cx - off, cy - d, cx - off, cy - off, paint)
        canvas.drawLine(cx - off, cy - off, cx - d, cy - off, paint)

        canvas.drawLine(cx + d, cy - off, cx + off, cy - off, paint)
        canvas.drawLine(cx + off, cy - off, cx + off, cy - d, paint)

        canvas.drawLine(cx - off, cy + d, cx - off, cy + off, paint)
        canvas.drawLine(cx - off, cy + off, cx - d, cy + off, paint)

        canvas.drawLine(cx + d, cy + off, cx + off, cy + off, paint)
        canvas.drawLine(cx + off, cy + off, cx + off, cy + d, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Custom Anti-Aliased Ungroup / Split Badge Drawable.
 */
class UngroupBadgeDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.0f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3E53935")
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0 || h <= 0) return

        canvas.drawCircle(w / 2.0f, h / 2.0f, Math.min(w, h) / 2.0f, bgPaint)

        val cx = w * 0.50f
        val cy = h * 0.50f
        val off = w * 0.22f

        canvas.drawLine(cx - off, cy - off, cx + off, cy + off, paint)
        canvas.drawLine(cx + off, cy - off, cx - off, cy + off, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
