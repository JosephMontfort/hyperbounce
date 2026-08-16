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
        val scale = Spring(0f, 1f) 
        val alpha = Spring(0f, 1f)
        var isPressed = false
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
            // 1. KILL STOCK ANIMATIONS & TRIGGER SIMULTANEOUS ENTRANCE
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
                        (containerField.get(view) as? View)?.let { v ->
                            v.alpha = 1f
                            // FIX: Shift the entire container UP by 60dp to clear the fingerprint sensor
                            v.translationY = -60f * view.resources.displayMetrics.density
                        }
                        
                        // FIX: Added "mRow0Container" to restore the 4 PIN indicator dots
                        arrayOf("mRow0Container", "mBouncerMessageAreaTitle", "mBouncerMessageArea", "mEmergencyCall", "mCancelButton").forEach { fieldName ->
                            try {
                                val field = view.javaClass.getDeclaredField(fieldName)
                                field.isAccessible = true
                                (field.get(view) as? View)?.let { v ->
                                    v.alpha = 0f
                                    v.translationY = 30f
                                    v.animate().alpha(1f).translationY(0f).setDuration(500).setInterpolator(android.view.animation.DecelerateInterpolator()).start()
                                }
                            } catch (e: Throwable) {}
                        }
                    } catch (e: Throwable) { Log.e(TAG, "Failed to reflect SystemUI states", e) }

                    // Trigger simultaneous, snappy pop-in
                    stateMap.forEach { (keyView, state) ->
                        state.scale.snap(0.3f)
                        state.alpha.snap(0f)
                        state.scale.target = 1f
                        state.alpha.target = 1f
                        keyView.postInvalidateOnAnimation()
                    }
                    return null 
                }
            })

            val startBackMethod = findMethod(pinViewClass, "startBackAnimation", java.lang.Runnable::class.java)
            module.hook(startBackMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val view = chain.thisObject as View
                    val runnable = chain.args[0] as? java.lang.Runnable
                    
                    stateMap.forEach { (keyView, state) ->
                        state.scale.target = 0.5f
                        state.alpha.target = 0f
                        keyView.postInvalidateOnAnimation()
                    }
                    view.postDelayed({ runnable?.run() }, 200)
                    return null
                }
            })

            val cancelAppearMethod = findMethod(pinViewClass, "cancelAppearAnim")
            module.hook(cancelAppearMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    stateMap.forEach { (keyView, state) ->
                        state.scale.target = 0.5f
                        state.alpha.target = 0f
                        keyView.postInvalidateOnAnimation()
                    }
                    return null
                }
            })

            // 2. SURGICAL LAYOUT HACK & HIDE TEXT
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
                            
                            stateMap[view] = KeyState(digitStr)

                            try {
                                val setAnimMethod = view.javaClass.getMethod("setAnimationEnabled", Boolean::class.javaPrimitiveType)
                                setAnimMethod.invoke(view, false)
                            } catch (e: Throwable) {}

                            // SURGICAL LAYOUT
                            val keySizePx = (82f * density).toInt()     
                            val vMarginPx = (14f * density).toInt()     
                            val hMarginPx = (14f * density).toInt()     

                            val row = view.parent as? LinearLayout
                            if (row != null) {
                                row.gravity = Gravity.CENTER_HORIZONTAL
                                val rowLp = row.layoutParams as? LinearLayout.LayoutParams
                                if (rowLp != null) {
                                    rowLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    rowLp.weight = 0f
                                    rowLp.bottomMargin = vMarginPx
                                    row.layoutParams = rowLp
                                }

                                for (i in 0 until row.childCount) {
                                    val sibling = row.getChildAt(i)
                                    val sibLp = sibling.layoutParams as? LinearLayout.LayoutParams
                                    if (sibLp != null) {
                                        sibLp.width = keySizePx
                                        sibLp.height = keySizePx
                                        sibLp.weight = 0f
                                        sibLp.leftMargin = hMarginPx
                                        sibLp.rightMargin = hMarginPx
                                        sibling.layoutParams = sibLp
                                    }
                                }
                            }

                        } catch (e: Throwable) {
                            Log.e(TAG, "Error configuring key constraints", e)
                        }
                    }
                    return result
                }
            })

            // 3. TRUE TOUCH INTERCEPTION & SQUISH (NO JELLY)
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
                            state.scale.target = 0.88f // Slight depth down on press
                            view.postInvalidateOnAnimation()
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            state.isPressed = false
                            
                            if (event.actionMasked == MotionEvent.ACTION_UP) {
                                // Deliberate Click Pop
                                state.scale.current = 0.75f // Compress down deeply
                            }
                            
                            state.scale.target = 1f
                            view.postInvalidateOnAnimation()
                        }
                    }
                    return chain.proceed() 
                }
            })

            // 4. DRAW UNIFORM BOUNCE (NO JELLY STRETCH)
            val onDrawMethod = findMethod(keyClass, "onDraw", Canvas::class.java)
            module.hook(onDrawMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val view = chain.thisObject as View
                    val canvas = chain.args[0] as Canvas
                    val state = stateMap[view] ?: return chain.proceed()

                    // Snappy, clean bounce (No wobble/jelly)
                    val s3 = state.scale.update(200f, 12f) 
                    val s4 = state.alpha.update(100f, 15f)
                    if (s3 || s4) {
                        view.postInvalidateOnAnimation()
                    }

                    val cx = view.width / 2f
                    val cy = view.height / 2f
                    val radius = view.width / 2f

                    canvas.save()
                    canvas.translate(cx, cy)
                    canvas.scale(state.scale.current, state.scale.current)

                    val baseAlpha = if (state.isPressed) 64 else 35 
                    circlePaint.color = Color.WHITE
                    circlePaint.alpha = (baseAlpha * state.alpha.current.coerceIn(0f, 1f)).toInt()
                    canvas.drawCircle(0f, 0f, radius, circlePaint)

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
