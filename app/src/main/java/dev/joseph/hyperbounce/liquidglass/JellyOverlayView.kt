package dev.joseph.hyperbounce.liquidglass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tanh

class JellyOverlayView(context: Context) : View(context) {

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textSize = 34f * context.resources.displayMetrics.scaledDensity
    }

    // A lightweight 60fps spring math class to eliminate thread-heavy Animators
    class Spring(var current: Float = 0f, var target: Float = 0f, var velocity: Float = 0f) {
        fun update(stiffness: Float, damping: Float): Boolean {
            val displacement = current - target
            val springForce = -stiffness * displacement
            val dampingForce = -damping * velocity
            val acceleration = springForce + dampingForce
            
            velocity += acceleration * 0.016f 
            current += velocity * 0.016f
            
            return abs(velocity) > 0.001f || abs(displacement) > 0.001f
        }
        fun snap(value: Float) {
            current = value
            target = value
            velocity = 0f
        }
    }

    class KeyNode(val digit: String, delayMs: Long) {
        var bounds = Rect()
        
        // Independent X/Y and Scale Physics State
        val tx = Spring()
        val ty = Spring()
        val scaleX = Spring(0f, 1f) // Start at 0 for pop-in animation
        val scaleY = Spring(0f, 1f)
        val alpha = Spring(0f, 1f)
        
        var isPressed = false
        var startTouchX = 0f
        var startTouchY = 0f
        
        val enterTime = System.currentTimeMillis() + delayMs
        var hasEntered = false
    }

    val nodes = mutableMapOf<String, KeyNode>()
    private var isAnimating = true // Start instantly for entrance animation

    init {
        isClickable = false
        isFocusable = false
    }

    fun registerNode(digit: String, bounds: Rect) {
        if (!nodes.containsKey(digit)) {
            val numericVal = digit.toIntOrNull() ?: 10
            val delayMs = if (numericVal == 0) 315L else (numericVal - 1) * 35L
            val node = KeyNode(digit, delayMs)
            node.bounds = bounds
            nodes[digit] = node
        } else {
            nodes[digit]!!.bounds = bounds
        }
        startAnimationLoop()
    }

    fun handleTouch(digit: String, action: Int, rawX: Float, rawY: Float) {
        val node = nodes[digit] ?: return
        val maxOffset = node.bounds.width() * 0.8f // Drag limit

        when (action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                node.isPressed = true
                node.startTouchX = rawX
                node.startTouchY = rawY
                node.scaleX.target = 0.88f // Depth compression on touch
                node.scaleY.target = 0.88f
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (!node.isPressed) return
                val dx = rawX - node.startTouchX
                val dy = rawY - node.startTouchY

                // Elastic drag limit (tanh asymptote)
                val targetTx = maxOffset * tanh(dx / maxOffset)
                val targetTy = maxOffset * tanh(dy / maxOffset)
                
                node.tx.target = targetTx
                node.ty.target = targetTy

                // Jelly Squash and Stretch Mathematics
                val dragDist = hypot(targetTx, targetTy)
                val stretch = 0.35f * (dragDist / maxOffset).coerceAtMost(1f) // 35% max stretch
                val angle = atan2(targetTy, targetTx)

                val baseScale = 0.88f
                node.scaleX.target = baseScale + stretch * abs(cos(angle)) - stretch * 0.5f * abs(sin(angle))
                node.scaleY.target = baseScale + stretch * abs(sin(angle)) - stretch * 0.5f * abs(cos(angle))
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                node.isPressed = false
                node.tx.target = 0f
                node.ty.target = 0f
                node.scaleX.target = 1f
                node.scaleY.target = 1f
            }
        }
        startAnimationLoop()
    }

    private fun startAnimationLoop() {
        if (!isAnimating) {
            isAnimating = true
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var needsNextFrame = false
        val now = System.currentTimeMillis()

        for (node in nodes.values) {
            if (node.bounds.isEmpty) continue

            if (!node.hasEntered && now >= node.enterTime) {
                node.hasEntered = true
            }
            if (!node.hasEntered) {
                needsNextFrame = true
                continue
            }

            // High stiffness = fast snap. High damping = low wobble.
            val s1 = node.tx.update(stiffness = 350f, damping = 25f)
            val s2 = node.ty.update(350f, 25f)
            val s3 = node.scaleX.update(250f, 15f)
            val s4 = node.scaleY.update(250f, 15f)
            val s5 = node.alpha.update(150f, 20f)
            if (s1 || s2 || s3 || s4 || s5) needsNextFrame = true

            val cx = node.bounds.exactCenterX() + node.tx.current
            val cy = node.bounds.exactCenterY() + node.ty.current
            val radius = (node.bounds.width() / 2f)

            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(node.scaleX.current, node.scaleY.current)

            // Draw Subtle Glass Background (Unclipped)
            val alphaVal = (255 * node.alpha.current.coerceIn(0f, 1f)).toInt()
            if (node.isPressed) {
                circlePaint.color = Color.parseColor("#45FFFFFF") // Brighten slightly on press
            } else {
                circlePaint.color = Color.parseColor("#28FFFFFF") // Subtle translucent glass
            }
            circlePaint.alpha = alphaVal
            canvas.drawCircle(0f, 0f, radius, circlePaint)

            // Draw Crisp Text
            textPaint.alpha = alphaVal
            val textOffset = (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(node.digit, 0f, -textOffset, textPaint)

            canvas.restore()
        }

        if (needsNextFrame) {
            invalidate()
        } else {
            isAnimating = false
        }
    }
}
