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
import kotlin.math.sin
import kotlin.math.cos
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
        val scale = Spring(0f, 1f) 
        val alpha = Spring(0f, 1f)
        
        val numericVal = digit.toIntOrNull() ?: 10
        val row = if (numericVal == 0) 3 else (numericVal - 1) / 3
        val col = if (numericVal == 0) 1 else (numericVal - 1) % 3
        val delayMs = (row * 40L) + (col * 20L)
        
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
        val pinViewClass = try {
            Class.forName("com.android.keyguard.widget.MiuiKeyguardPINView", false, classLoader)
        } catch (e: ClassNotFoundException) { return }
        
        val keyClass = try {
            Class.forName("com.android.keyguard.widget.MiuiNumPadKey", false, classLoader)
        } catch (e: ClassNotFoundException) { return }

        try {
            // 1. SURGICAL OVERRIDE: Kill stock animations and spoof state
            val startAppearMethod = findMethod(pinViewClass, "startAppearAnimation")
            module.hook(startAppearMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val view = chain.thisObject as View
                    
                    try {
                        view.javaClass.getDeclaredField("initAppearAnimed").apply { isAccessible = true; setBoolean(view, true) }
                        view.javaClass.getDeclaredField("mAppearAnimating").apply { isAccessible = true; setBoolean(view, false) }
                        
                        view.alpha = 1f
                        val containerField = view.javaClass.getDeclaredField("mContainerConstraintLayout")
                        containerField.isAccessible = true
                        (containerField.get(view) as? View)?.alpha = 1f
                        
                        // Fade in auxiliary items cleanly
                        arrayOf("mBouncerMessageAreaTitle", "mBouncerMessageArea", "mEmergencyCall", "mCancelButton").forEach { fieldName ->
                            try {
                                val field = view.javaClass.getDeclaredField(fieldName)
                                field.isAccessible = true
                                (field.get(view) as? View)?.let { v ->
                                    v.alpha = 0f
                                    v.translationY = 30f
                                    v.animate().alpha(1f).translationY(0f).setDuration(400).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                                }
                            } catch (e: Throwable) {}
                        }
                    } catch (e: Throwable) { Log.e(TAG, "Failed to reflect SystemUI states", e) }

                    // Trigger our custom iOS 26 cascade
                    stateMap.forEach { (keyView, state) ->
                        state.scale.snap(0.2f)
                        state.alpha.snap(0f)
                        keyView.postDelayed({
                            state.scale.target = 1f
                            state.alpha.target = 1f
                            keyView.postInvalidateOnAnimation()
                        }, state.delayMs)
                    }
                    return null // Kills stock Folme animation
                }
            })

            val startBackMethod = findMethod(pinViewClass, "startBackAnimation", java.lang.Runnable::class.java)
            module.hook(startBackMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val view = chain.thisObject as View
                    val runnable = chain.args[0] as? java.lang.Runnable
                    
                    stateMap.forEach { (keyView, state) ->
                        state.scale.target = 0.6f
                        state.alpha.target = 0f
                        keyView.postInvalidateOnAnimation()
                    }
                    view.postDelayed({ runnable?.run() }, 150)
                    return null
                }
            })

            val cancelAppearMethod = findMethod(pinViewClass, "cancelAppearAnim")
            module.hook(cancelAppearMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    stateMap.forEach { (keyView, state) ->
                        state.scale.target = 0.6f
                        state.alpha.target = 0f
                        keyView.postInvalidateOnAnimation()
                    }
                    return null
                }
            })

            // 2. Setup Physics State per Key
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

            // 3. True Touch Interception
            val onTouchMethod = findMethod(keyClass, "onTouchEvent", MotionEvent::class.java)
            module.hook(onTouchMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val view = chain.thisObject as View
                    val event = chain.args[0] as MotionEvent
                    val state = stateMap[view] ?: return chain.proceed()

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            var p = view.parent
                            while (p != null) {
                                p.requestDisallowInterceptTouchEvent(true)
                                p = p.parent
                            }
                            
                            state.isPressed = true
                            state.startTouchX = event.rawX
                            state.startTouchY = event.rawY
                            
                            state.scale.target = 1f 
                            view.postInvalidateOnAnimation()
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (state.isPressed) {
                                state.rawDx.target = event.rawX - state.startTouchX
                                state.rawDy.target = event.rawY - state.startTouchY
                                view.postInvalidateOnAnimation()
                            }
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            state.isPressed = false
                            val dist = hypot(state.rawDx.current.toDouble(), state.rawDy.current.toDouble()).toFloat()
                            
                            // Deliberate "pop" only if tapped without heavy dragging
                            if (event.actionMasked == MotionEvent.ACTION_UP && dist < view.width * 0.2f) {
                                state.scale.current = 0.8f 
                            }
                            
                            state.rawDx.target = 0f
                            state.rawDy.target = 0f
                            state.scale.target = 1f
                            view.postInvalidateOnAnimation()
                        }
                    }
                    return chain.proceed() 
                }
            })

            // 4. Draw Split Physics (Volume-Preserving Circle + Static Text)
            val onDrawMethod = findMethod(keyClass, "onDraw", Canvas::class.java)
            module.hook(onDrawMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val view = chain.thisObject as View
                    val canvas = chain.args[0] as Canvas
                    val state = stateMap[view] ?: return chain.proceed()

                    val s1 = state.rawDx.update(60f, 4.5f)
                    val s2 = state.rawDy.update(60f, 4.5f)
                    val s3 = state.scale.update(300f, 18f) 
                    val s4 = state.alpha.update(150f, 20f)
                    if (s1 || s2 || s3 || s4) {
                        view.postInvalidateOnAnimation()
                    }

                    val cx = view.width / 2f
                    val cy = view.height / 2f
                    val radius = view.width / 2f
                    
                    val maxOffset = view.width * 1.5f
                    val boundedDx = maxOffset * tanh(state.rawDx.current / maxOffset).toFloat()
                    val boundedDy = maxOffset * tanh(state.rawDy.current / maxOffset).toFloat()
                    val dragDist = hypot(boundedDx.toDouble(), boundedDy.toDouble()).toFloat()
                    
                    // True Volume Preservation: 35% max stretch amplitude
                    val stretchAmplitude = 0.35f * (dragDist / maxOffset).coerceAtMost(1f)
                    val stretchFactor = 1.0f + stretchAmplitude
                    
                    val angleRadians = atan2(boundedDy.toDouble(), boundedDx.toDouble())
                    val angleDegrees = Math.toDegrees(angleRadians).toFloat()

                    // --- DRAW BACKGROUND CIRCLE ---
                    canvas.save()
                    canvas.translate(cx, cy)
                    if (dragDist > 1f) canvas.rotate(angleDegrees)
                    
                    // Math: Scale X up, Scale Y down inversely to maintain mass
                    val currentBaseScale = state.scale.current
                    canvas.scale(currentBaseScale * stretchFactor, currentBaseScale / stretchFactor)

                    val baseAlpha = if (state.isPressed) 64 else 35 
                    circlePaint.color = Color.WHITE
                    circlePaint.alpha = (baseAlpha * state.alpha.current.coerceIn(0f, 1f)).toInt()
                    canvas.drawCircle(0f, 0f, radius, circlePaint)
                    canvas.restore()

                    // --- DRAW STATIC TEXT ---
                    canvas.save()
                    canvas.translate(cx, cy)
                    canvas.scale(currentBaseScale, currentBaseScale) // Text never distorts

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
