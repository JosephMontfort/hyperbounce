package dev.joseph.hyperbounce

import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

object LiquidKeypadHook {
    private const val TAG = "HyperBounce_Spacing"

    private fun findMethod(clazz: Class<*>, name: String, vararg parameterTypes: Class<*>): Method {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val method = current.getDeclaredMethod(name, *parameterTypes)
                method.isAccessible = true
                return method
            } catch (e: NoSuchMethodException) {
                current = current.superclass
            }
        }
        throw NoSuchMethodException(name)
    }

    fun apply(module: XposedModule, classLoader: ClassLoader) {
        // =========================================================
        // 1. Hook the Grid Container to Lock Spacing Permanently
        // =========================================================
        try {
            val pinViewClass = Class.forName("com.android.keyguard.widget.MiuiKeyguardPINView", false, classLoader)
            val onMeasureMethod = findMethod(pinViewClass, "onMeasure", Int::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!)
            
            module.hook(onMeasureMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.thisObject as ViewGroup

                    try {
                        // Find the grid rows container
                        var pinGrid: LinearLayout? = null
                        for (i in 0 until view.childCount) {
                            val c = view.getChildAt(i)
                            if (c is LinearLayout && c.childCount >= 4) {
                                pinGrid = c
                                break
                            }
                        }

                        pinGrid?.let { grid ->
                            val density = view.resources.displayMetrics.density
                            val vGapPx = (42f * density).toInt()  // Massive 42dp Row Spacing
                            val hMarginPx = (22f * density).toInt() // Massive 22dp Key Margin

                            for (r in 0 until grid.childCount) {
                                val row = grid.getChildAt(r) as? LinearLayout ?: continue

                                // Hijack Vertical Row Spacing
                                val rowLp = row.layoutParams
                                if (rowLp is ViewGroup.MarginLayoutParams) {
                                    rowLp.bottomMargin = vGapPx
                                    row.layoutParams = rowLp
                                }

                                // Hijack Horizontal Key Margins
                                for (k in 0 until row.childCount) {
                                    val key = row.getChildAt(k) ?: continue
                                    val keyLp = key.layoutParams
                                    if (keyLp is ViewGroup.MarginLayoutParams) {
                                        keyLp.leftMargin = hMarginPx
                                        keyLp.rightMargin = hMarginPx
                                        key.layoutParams = keyLp
                                    }
                                }
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to apply spacing in onMeasure", e)
                    }
                    return result
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook MiuiKeyguardPINView", e)
        }

        // =========================================================
        // 2. Hook Individual Keys for Shapes, Text, and Physics
        // =========================================================
        try {
            val keyClass = Class.forName("com.android.keyguard.widget.MiuiNumPadKey", false, classLoader)
            
            // Hook Constructors to apply visual styling natively and safely
            for (constructor in keyClass.declaredConstructors) {
                constructor.isAccessible = true
                module.hook(constructor).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed()
                        val view = chain.thisObject as View

                        view.post {
                            // Hide Klondike Letters
                            try {
                                var cls: Class<*>? = view.javaClass
                                var field: java.lang.reflect.Field? = null
                                while (cls != null) {
                                    try {
                                        field = cls.getDeclaredField("mKlondikeText")
                                        break
                                    } catch (e: NoSuchFieldException) { cls = cls.superclass }
                                }
                                field?.isAccessible = true
                                (field?.get(view) as? TextView)?.visibility = View.GONE
                            } catch (e: Throwable) {}

                            // Center Digits Perfectly
                            try {
                                var cls: Class<*>? = view.javaClass
                                var field: java.lang.reflect.Field? = null
                                while (cls != null) {
                                    try {
                                        field = cls.getDeclaredField("mDigitText")
                                        break
                                    } catch (e: NoSuchFieldException) { cls = cls.superclass }
                                }
                                field?.isAccessible = true
                                val digitText = field?.get(view) as? TextView
                                digitText?.let {
                                    it.gravity = Gravity.CENTER
                                    it.setPadding(0, 0, 0, 0)
                                }
                            } catch (e: Throwable) {}

                            // Kill native animations
                            try {
                                val setAnimMethod = view.javaClass.getMethod("setAnimationEnabled", Boolean::class.javaPrimitiveType)
                                setAnimMethod.invoke(view, false)
                            } catch (e: Throwable) {}

                            // Native Large Circle Background (No Frida crashes)
                            view.setPadding(0, 0, 0, 0)
                            val circleBg = object : android.graphics.drawable.Drawable() {
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = Color.parseColor("#40FFFFFF")
                                    style = Paint.Style.FILL
                                }
                                override fun draw(canvas: android.graphics.Canvas) {
                                    val size = minOf(bounds.width(), bounds.height()).toFloat()
                                    val radius = (size / 2f) * 0.98f // 98% scale for large centered circles
                                    canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, paint)
                                }
                                override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
                                @Deprecated("Deprecated in Java")
                                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
                            }
                            view.background = circleBg
                        }
                        return result
                    }
                })
            }

            // Hook Touch Event for iOS bouncy physics
            val onTouchMethod = findMethod(keyClass, "onTouchEvent", MotionEvent::class.java)
            val spring = OvershootInterpolator(2.5f)
            
            module.hook(onTouchMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.thisObject as View
                    val event = chain.args[0] as MotionEvent

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            view.animate().scaleX(0.78f).scaleY(0.78f).setDuration(120).setInterpolator(spring).start()
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(400).setInterpolator(spring).start()
                        }
                    }
                    return result
                }
            })
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook MiuiNumPadKey", e)
        }
    }
}
