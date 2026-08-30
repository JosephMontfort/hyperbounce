package dev.joseph.hyperbounce.controlcenter

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * Custom Anti-Aliased Rotate Icon Badge Drawable for 2-Tile Groups.
 */
class RotateIconDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.0f
        strokeCap = Paint.Cap.ROUND
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val r = Math.min(bounds.width(), bounds.height()) / 2f
        canvas.drawCircle(cx, cy, r, bgPaint)

        val arcR = r * 0.55f
        val arcRect = RectF(cx - arcR, cy - arcR, cx + arcR, cy + arcR)
        canvas.drawArc(arcRect, 30f, 260f, false, paint)

        val arrowX = cx + arcR * Math.cos(Math.toRadians(290.0)).toFloat()
        val arrowY = cy + arcR * Math.sin(Math.toRadians(290.0)).toFloat()
        canvas.drawLine(arrowX, arrowY, arrowX - 5f, arrowY - 5f, paint)
        canvas.drawLine(arrowX, arrowY, arrowX + 5f, arrowY - 5f, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

/**
 * Custom Anti-Aliased Resize Handle Vector Badge Drawable.
 */
class ResizeHandleDrawable : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B3000000")
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

        canvas.drawLine(cx - off, cy + off, cx + off, cy - off, paint)

        // Top-right arrow head
        val p1 = Path().apply {
            moveTo(cx + off - (w * 0.14f), cy - off)
            lineTo(cx + off, cy - off)
            lineTo(cx + off, cy - off + (h * 0.14f))
        }
        canvas.drawPath(p1, paint)

        // Bottom-left arrow head
        val p2 = Path().apply {
            moveTo(cx - off + (w * 0.14f), cy + off)
            lineTo(cx - off, cy + off)
            lineTo(cx - off, cy + off - (h * 0.14f))
        }
        canvas.drawPath(p2, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
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
