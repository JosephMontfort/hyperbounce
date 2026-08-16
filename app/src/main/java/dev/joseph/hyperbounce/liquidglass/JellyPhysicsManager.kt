package dev.joseph.hyperbounce.liquidglass

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

object JellyPhysicsManager {
    private var circlePaint: Paint? = null
    private var textPaint: Paint? = null

    var pinView: View? = null
    private var isAnimating = false
    private var startTime = 0L

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
            current = value; target = value; velocity = 0f
        }
    }

    class KeyNode(val digit: String) {
        val numericVal = digit.toIntOrNull() ?: 10
        val delayMs = if (numericVal == 0) 315L else (numericVal - 1) * 35L
        
        var bounds = Rect()
        val tx = Spring()
        val ty = Spring()
        val scaleX = Spring(0f, 1f)
        val scaleY = Spring(0f, 1f)
        val alpha = Spring(0f, 1f)
        
        var isPressed = false
        var startTouchX = 0f
        var startTouchY = 0f
        var hasEntered = false
    }

    val nodes = mutableMapOf<String, KeyNode>()

    fun init(view: View) {
        pinView = view
        startTime = System.currentTimeMillis()
        isAnimating = true
        
        if (circlePaint == null) {
            circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }
            textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                textSize = 34f * view.resources.displayMetrics.scaledDensity
            }
        }
        
        // Reset entrance animations on wake
        nodes.values.forEach { 
            it.hasEntered = false
            it.scaleX.snap(0f)
            it.scaleY.snap(0f)
            it.alpha.snap(0f)
            it.scaleX.target = 1f
            it.scaleY.target = 1f
            it.alpha.target = 1f
        }
        view.invalidate()
    }

    fun registerNode(digit: String, bounds: Rect) {
        val node = nodes.getOrPut(digit) { KeyNode(digit) }
        node.bounds = bounds
    }

    fun handleTouch(digit: String, action: Int, rawX: Float, rawY: Float) {
        val node = nodes[digit] ?: return
        val maxOffset = node.bounds.width() * 0.8f

        when (action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                node.isPressed = true
                node.startTouchX = rawX
                node.startTouchY = rawY
                node.scaleX.target = 0.88f // Depth pop
                node.scaleY.target = 0.88f
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (!node.isPressed) return
                val dx = rawX - node.startTouchX
                val dy = rawY - node.startTouchY

                // Tanh Asymptote (Elastic Drag)
                val targetTx = maxOffset * tanh(dx / maxOffset).toFloat()
                val targetTy = maxOffset * tanh(dy / maxOffset).toFloat()
                
                node.tx.target = targetTx
                node.ty.target = targetTy

                // Squash and stretch deformation
                val dragDist = hypot(targetTx.toDouble(), targetTy.toDouble()).toFloat()
                val stretch = 0.35f * (dragDist / maxOffset).coerceAtMost(1f)
                val angle = atan2(targetTy.toDouble(), targetTx.toDouble()).toFloat()

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
        if (!isAnimating) {
            isAnimating = true
            pinView?.invalidate()
        }
    }

    fun onDraw(canvas: Canvas) {
        if (pinView == null || circlePaint == null || textPaint == null) return
        var needsNextFrame = false
        val now = System.currentTimeMillis()

        for (node in nodes.values) {
            if (node.bounds.isEmpty) continue

            // Handle iOS 26 Entrance Delay
            if (!node.hasEntered && now - startTime >= node.delayMs) {
                node.hasEntered = true
            }
            if (!node.hasEntered) {
                needsNextFrame = true
                continue
            }

            val s1 = node.tx.update(350f, 25f)
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

            val alphaVal = (255 * node.alpha.current.coerceIn(0f, 1f)).toInt()
            if (node.isPressed) {
                circlePaint!!.color = Color.parseColor("#45FFFFFF") // Brighten on press
            } else {
                circlePaint!!.color = Color.parseColor("#25FFFFFF") // Subtle glass tint
            }
            circlePaint!!.alpha = alphaVal
            canvas.drawCircle(0f, 0f, radius, circlePaint!!)

            textPaint!!.alpha = alphaVal
            val textOffset = (textPaint!!.descent() + textPaint!!.ascent()) / 2f
            canvas.drawText(node.digit, 0f, -textOffset, textPaint!!)

            canvas.restore()
        }

        if (needsNextFrame) {
            pinView?.invalidate()
        } else {
            isAnimating = false
        }
    }
}
