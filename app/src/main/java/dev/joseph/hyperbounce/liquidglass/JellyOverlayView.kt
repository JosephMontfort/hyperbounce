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
        color = Color.parseColor("#35FFFFFF") // iOS 26 translucent glass base
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        textSize = 34f * context.resources.displayMetrics.scaledDensity
    }

    // A lightweight physics spring class to eliminate the need for thread-heavy Animators
    class Spring(var current: Float = 0f, var target: Float = 0f, var velocity: Float = 0f) {
        fun update(stiffness: Float, damping: Float): Boolean {
            val displacement = current - target
            val springForce = -stiffness * displacement
            val dampingForce = -damping * velocity
            val acceleration = springForce + dampingForce
            
            velocity += acceleration * 0.016f // Assume ~60fps delta
            current += velocity * 0.016f
            
            // Return true if still moving
            return abs(velocity) > 0.001f || abs(displacement) > 0.001f
        }
        fun snap(value: Float) {
            current = value
            target = value
            velocity = 0f
        }
    }

    class KeyNode(val digit: String) {
        var bounds = Rect()
        
        // Physics State
        val tx = Spring()
        val ty = Spring()
        val scaleX = Spring(1f, 1f)
        val scaleY = Spring(1f, 1f)
        val alpha = Spring()
        
        var isPressed = false
        var startTouchX = 0f
        var startTouchY = 0f
    }

    val nodes = mutableMapOf<String, KeyNode>()
    private var isAnimating = false

    init {
        // Ensure this view doesn't intercept touches meant for the invisible native grid below it
        isClickable = false
        isFocusable = false
    }

    fun registerNode(digit: String, bounds: Rect) {
        if (!nodes.containsKey(digit)) {
            val node = KeyNode(digit)
            node.bounds = bounds
            nodes[digit] = node
            triggerEntrance(node)
        } else {
            nodes[digit]!!.bounds = bounds
        }
        invalidate()
    }

    private fun triggerEntrance(node: KeyNode) {
        // Staggered iOS 26 Entrance Delay
        val numericVal = node.digit.toIntOrNull() ?: 10
        val delayMs = if (numericVal == 0) 315L else (numericVal - 1) * 35L
        
        node.scaleX.snap(0f)
        node.scaleY.snap(0f)
        node.alpha.snap(0f)

        postDelayed({
            node.scaleX.target = 1f
            node.scaleY.target = 1f
            node.alpha.target = 1f
            startAnimationLoop()
        }, delayMs)
    }

    fun handleTouch(digit: String, action: Int, rawX: Float, rawY: Float) {
        val node = nodes[digit] ?: return
        val maxOffset = node.bounds.width() * 1.2f

        when (action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                node.isPressed = true
                node.startTouchX = rawX
                node.startTouchY = rawY
                
                // Depth pop on press
                node.scaleX.target = 0.88f
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

                // Jelly Squash and Stretch
                val dragDist = hypot(targetTx, targetTy)
                val stretch = 0.35f * (dragDist / maxOffset).coerceAtMost(1f)
                val angle = atan2(targetTy, targetTx)

                val baseScale = 0.88f
                node.scaleX.target = baseScale + stretch * abs(cos(angle)) - stretch * 0.5f * abs(sin(angle))
                node.scaleY.target = baseScale + stretch * abs(sin(angle)) - stretch * 0.5f * abs(cos(angle))
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                node.isPressed = false
                // Bouncy snap back to origin
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
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        var needsNextFrame = false

        for (node in nodes.values) {
            if (node.bounds.isEmpty) continue

            // Update Physics Springs
            // Higher stiffness = faster snap. Higher damping = less wobble.
            val s1 = node.tx.update(stiffness = 350f, damping = 25f)
            val s2 = node.ty.update(350f, 25f)
            val s3 = node.scaleX.update(stiffness = 250f, damping = 15f)
            val s4 = node.scaleY.update(250f, 15f)
            val s5 = node.alpha.update(150f, 20f)
            if (s1 || s2 || s3 || s4 || s5) needsNextFrame = true

            val cx = node.bounds.exactCenterX() + node.tx.current
            val cy = node.bounds.exactCenterY() + node.ty.current
            val radius = (node.bounds.width() / 2f)

            canvas.save()
            // Apply jelly scaling to the exact center of the pulled coordinate
            canvas.translate(cx, cy)
            canvas.scale(node.scaleX.current, node.scaleY.current)

            // Draw Background
            circlePaint.alpha = (255 * node.alpha.current.coerceIn(0f, 1f)).toInt()
            if (node.isPressed) {
                // Brighten on press
                circlePaint.color = Color.parseColor("#45FFFFFF") 
            } else {
                circlePaint.color = Color.parseColor("#35FFFFFF")
            }
            canvas.drawCircle(0f, 0f, radius, circlePaint)

            // Draw Centered Text
            textPaint.alpha = (255 * node.alpha.current.coerceIn(0f, 1f)).toInt()
            val textOffset = (textPaint.descent() + textPaint.ascent()) / 2f
            canvas.drawText(node.digit, 0f, -textOffset, textPaint)

            canvas.restore()
        }

        if (needsNextFrame) {
            postInvalidateOnAnimation()
        } else {
            isAnimating = false
        }
    }
}
