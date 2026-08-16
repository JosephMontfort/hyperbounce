package dev.joseph.hyperbounce

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.tanh

object LiquidKeypadHook {
    private const val TAG = "HyperBounce_Keypad"

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    class Spring(var current: Float = 0f, var target: Float = 0f, var velocity: Float = 0f) {
        fun update(stiffness: Float, damping: Float): Boolean {
            val displacement = current - target
            val acceleration = (-stiffness * displacement) + (-damping * velocity)
            velocity += acceleration * 0.016f 
            current += velocity * 0.016f
            return abs(velocity) > 0.001f || abs(displacement) > 0.001f
        }
        fun snap(value: Float) { current = value; target = value; velocity = 0f }
    }

    class KeyState(val digit: String) {
        val rawDx = Spring()
        val rawDy = Spring()
        val scaleX = Spring(0f, 1f) 
        val scaleY = Spring(0f, 1f)
        val alpha = Spring(0f, 1f)
        
        var isPressed = false
        var startTouchX = 0f
        var startTouchY = 0f
    }

    private val stateMap = WeakHashMap<View, KeyState>()

    private fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val method = current.getDeclaredMethod(name, *parameterTypes)
                method.isAccessible = true
                return method
            } catch (e: NoSuchMethodException) { current = current.superclass }
        }
        throw NoSuchMethodException(name)
    }

    fun apply(module: XposedModule, classLoader: ClassLoader) {
        val keyClass = try {
            Class.forName("com.android.keyguard.widget.MiuiNumPadKey", false, classLoader)
        } catch (e: ClassNotFoundException) { return }

        try {
            val onAttachedKeyMethod = findMethod(keyClass, "onAttachedToWindow")
            module.hook(onAttachedKeyMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.thisObject as View

                    view.post {
                        try {
                            val density = view.resources.displayMetrics.density

                            view.clipToOutline = false
                            (view.parent as? ViewGroup)?.clipChildren = false
                            (view.parent?.parent as? ViewGroup)?.clipChildren = false

                            view.setWillNotDraw(false)
                            view.background = null

                            var digitStr = ""
                            var cls: Class<*>? = view.javaClass
                            while (cls != null) {
                                try {
                                    val mDigitText = cls.getDeclaredField("mDigitText")
                                    mDigitText.isAccessible = true
                                    (mDigitText.get(view) as? TextView)?.let {
                                        digitStr = it.text.toString()
                                        it.visibility = View.INVISIBLE 
                                    }
                                    val mKlondikeText = cls.getDeclaredField("mKlondikeText")
                                    mKlondikeText.isAccessible = true
                                    (mKlondikeText.get(view) as? TextView)?.visibility = View.GONE
                                    break
                                } catch (e: NoSuchFieldException) { cls = cls.superclass }
                            }
                            
                            val state = KeyState(digitStr)
                            stateMap[view] = state

                            val numericVal = digitStr.toIntOrNull() ?: 10
                            val delayMs = if (numericVal == 0) 315L else (numericVal - 1) * 35L
                            view.postDelayed({
                                state.scaleX.target = 1f
                                state.scaleY.target = 1f
                                state.alpha.target = 1f
                                view.postInvalidateOnAnimation()
                            }, delayMs)

                            try {
                                val setAnimMethod = view.javaClass.getMethod("setAnimationEnabled", Boolean::class.javaPrimitiveType)
                                setAnimMethod.invoke(view, false)
                            } catch (e: Throwable) {}

                            val keySizePx = (82f * density).toInt()     
                            val vMarginPx = (28f * density).toInt()     
                            val hMarginPx = (10f * density).toInt()     
                            val bottomLiftPx = (115f * density).toInt() 
                            val topPullPx = (-50f * density).toInt()    

                            val row = view.parent as? LinearLayout
                            val grid = row?.parent as? LinearLayout

                            grid?.let {
                                val gridLp = it.layoutParams as? ViewGroup.MarginLayoutParams
                                if (gridLp != null) {
                                    var changed = false
                                    if (gridLp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                                        gridLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                        changed = true
                                    }
                                    if (gridLp.topMargin != topPullPx) {
                                        gridLp.topMargin = topPullPx
                                        changed = true
                                    }
                                    if (changed) it.layoutParams = gridLp
                                }
                            }

                            row?.let {
                                it.gravity = Gravity.CENTER_HORIZONTAL
                                val rowLp = it.layoutParams as? LinearLayout.LayoutParams
                                if (rowLp != null) {
                                    rowLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    rowLp.weight = 0f
                                    rowLp.bottomMargin = if (digitStr == "0") bottomLiftPx else vMarginPx
                                    it.layoutParams = rowLp
                                }
                            }

                            val keyLp = view.layoutParams as? LinearLayout.LayoutParams
                            if (keyLp != null) {
                                keyLp.width = keySizePx
                                keyLp.height = keySizePx
                                keyLp.weight = 0f
                                keyLp.leftMargin = hMarginPx
                                keyLp.rightMargin = hMarginPx
                                view.layoutParams = keyLp
                            }

                        } catch (e: Throwable) {
                            Log.e(TAG, "Error configuring key constraints", e)
                        }
                    }
                    return result
                }
            })

            val onTouchMethod = findMethod(keyClass, "onTouchEvent", MotionEvent::class.java)
            module.hook(onTouchMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val view = chain.thisObject as View
                    val event = chain.args[0] as MotionEvent
                    val state = stateMap[view] ?: return chain.proceed()

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            // CRITICAL FIX: Stop MIUI from intercepting upward swipes
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            
                            state.isPressed = true
                            state.startTouchX = event.rawX
                            state.startTouchY = event.rawY
                            
                            // Do NOT shrink on drag. Leave target at 1f.
                            state.scaleX.target = 1f 
                            state.scaleY.target = 1f
                            view.postInvalidateOnAnimation()
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (state.isPressed) {
                                val dx = event.rawX - state.startTouchX
                                val dy = event.rawY - state.startTouchY

                                state.rawDx.target = dx
                                state.rawDy.target = dy
                                view.postInvalidateOnAnimation()
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            state.isPressed = false
                            
                            val dx = state.rawDx.current
                            val dy = state.rawDy.current
                            
                            // Click Shrink Logic: Only shrink if they tapped without dragging far
                            if (hypot(dx.toDouble(), dy.toDouble()) < view.width * 0.2f) {
                                // Instantly compress the current spring value to create a visual "pop"
                                state.scaleX.current = 0.8f
                                state.scaleY.current = 0.8f
                            }
                            
                            state.rawDx.target = 0f
                            state.rawDy.target = 0f
                            state.scaleX.target = 1f
                            state.scaleY.target = 1f
                            view.postInvalidateOnAnimation()
                        }
                    }
                    
                    return chain.proceed() 
                }
            })

            val onDrawMethod = findMethod(keyClass, "onDraw", Canvas::class.java)
            module.hook(onDrawMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val view = chain.thisObject as View
                    val canvas = chain.args[0] as Canvas
                    val state = stateMap[view] ?: return chain.proceed()

                    // Wobble physics: Lower stiffness & damping for heavy, fluid jelly return
                    val s1 = state.rawDx.update(120f, 10f)
                    val s2 = state.rawDy.update(120f, 10f)
                    val s3 = state.scaleX.update(250f, 15f)
                    val s4 = state.scaleY.update(250f, 15f)
                    val s5 = state.alpha.update(150f, 20f)
                    if (s1 || s2 || s3 || s4 || s5) {
                        view.postInvalidateOnAnimation()
                    }

                    val cx = view.width / 2f
                    val cy = view.height / 2f
                    val radius = view.width / 2f
                    
                    val maxOffset = view.width * 1.5f
                    val dx = state.rawDx.current
                    val dy = state.rawDy.current
                    
                    val boundedDx = maxOffset * tanh(dx / maxOffset).toFloat()
                    val boundedDy = maxOffset * tanh(dy / maxOffset).toFloat()

                    val dragDist = hypot(boundedDx.toDouble(), boundedDy.toDouble()).toFloat()
                    
                    // Asymmetric stretch mapping
                    val stretch = 0.6f * (dragDist / maxOffset).coerceAtMost(1f)
                    val angleRadians = atan2(boundedDy.toDouble(), boundedDx.toDouble())
                    val angleDegrees = Math.toDegrees(angleRadians).toFloat()

                    // Increase scale along the drag axis, squash the perpendicular axis
                    val finalScaleX = state.scaleX.current + stretch
                    val finalScaleY = state.scaleY.current - (stretch * 0.4f)

                    canvas.save()
                    canvas.translate(cx, cy)
                    
                    // Physically rotate the canvas to point at your finger for true omnidirectional stretching
                    if (dragDist > 1f) {
                        canvas.rotate(angleDegrees)
                    }
                    
                    canvas.scale(finalScaleX, finalScaleY)

                    val baseAlpha = if (state.isPressed) 64 else 35 
                    circlePaint.color = Color.WHITE
                    circlePaint.alpha = (baseAlpha * state.alpha.current.coerceIn(0f, 1f)).toInt()
                    canvas.drawCircle(0f, 0f, radius, circlePaint)

                    // Un-rotate the canvas before drawing the text so the number stays upright
                    if (dragDist > 1f) {
                        canvas.rotate(-angleDegrees)
                    }

                    textPaint.textSize = 34f * view.resources.displayMetrics.scaledDensity
                    textPaint.alpha = (255 * state.alpha.current.coerceIn(0f, 1f)).toInt()
                    val textOffset = (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(state.digit, 0f, -textOffset, textPaint)

                    canvas.restore()
                    
                    return null 
                }
            })

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook Keypad", e)
        }
    }
}
