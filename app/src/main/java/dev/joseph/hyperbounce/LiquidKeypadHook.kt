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
        try {
            val keyClass = Class.forName("com.android.keyguard.widget.MiuiNumPadKey", false, classLoader)

            val onAttachedMethod = findMethod(keyClass, "onAttachedToWindow")
            module.hook(onAttachedMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.thisObject as View

                    view.post {
                        try {
                            val density = view.resources.displayMetrics.density

                            // 1. Hide Klondike text, Center Digits, and Extract the Number
                            var digitStr = ""
                            var digitText: TextView? = null
                            var cls: Class<*>? = view.javaClass
                            while (cls != null) {
                                try {
                                    val mDigitText = cls.getDeclaredField("mDigitText")
                                    mDigitText.isAccessible = true
                                    digitText = mDigitText.get(view) as? TextView

                                    val mKlondikeText = cls.getDeclaredField("mKlondikeText")
                                    mKlondikeText.isAccessible = true
                                    (mKlondikeText.get(view) as? TextView)?.visibility = View.GONE
                                    break
                                } catch (e: NoSuchFieldException) { cls = cls.superclass }
                            }
                            
                            digitText?.let {
                                it.gravity = Gravity.CENTER
                                it.setPadding(0, 0, 0, 0)
                                digitStr = it.text.toString()
                            }

                            // Kill native animations
                            try {
                                val setAnimMethod = view.javaClass.getMethod("setAnimationEnabled", Boolean::class.javaPrimitiveType)
                                setAnimMethod.invoke(view, false)
                            } catch (e: Throwable) {}

                            // =========================================================
                            // 2. THE XML REWRITE HACK (Vertical Gap & Grid Lift)
                            // =========================================================
                            val keySizePx = (82f * density).toInt()    // Perfect square button bounds
                            val vMarginPx = (32f * density).toInt()    // Increased vertical gap between rows
                            val hMarginPx = (14f * density).toInt()    // Keep the horizontal gap as is
                            val bottomLiftPx = (75f * density).toInt() // Massive lift for the 0 row

                            val row = view.parent as? LinearLayout
                            val grid = row?.parent as? LinearLayout

                            // Grandparent: Free it from fixed XML dimensions
                            grid?.let {
                                val gridLp = it.layoutParams
                                if (gridLp.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
                                    gridLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    it.layoutParams = gridLp
                                }
                            }

                            // Parent Row: Strip weight, apply margins
                            row?.let {
                                it.gravity = Gravity.CENTER_HORIZONTAL
                                val rowLp = it.layoutParams as? LinearLayout.LayoutParams
                                if (rowLp != null) {
                                    rowLp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                                    rowLp.weight = 0f
                                    
                                    // If this is the row with the "0", apply the lift. Otherwise, normal gap.
                                    if (digitStr == "0") {
                                        rowLp.bottomMargin = bottomLiftPx
                                    } else {
                                        rowLp.bottomMargin = vMarginPx
                                    }
                                    
                                    it.layoutParams = rowLp
                                }
                            }

                            // Self (Key): Fixed square, horizontal margins
                            val keyLp = view.layoutParams as? LinearLayout.LayoutParams
                            if (keyLp != null) {
                                keyLp.width = keySizePx
                                keyLp.height = keySizePx
                                keyLp.weight = 0f
                                keyLp.leftMargin = hMarginPx
                                keyLp.rightMargin = hMarginPx
                                view.layoutParams = keyLp
                            }
                            
                            view.translationX = 0f
                            view.translationY = 0f

                            // =========================================================
                            // 3. Perfect Circle Background
                            // =========================================================
                            view.setPadding(0, 0, 0, 0)
                            val circleBg = object : android.graphics.drawable.Drawable() {
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = Color.parseColor("#40FFFFFF")
                                    style = Paint.Style.FILL
                                }
                                override fun draw(canvas: android.graphics.Canvas) {
                                    val radius = (bounds.width() / 2f)
                                    canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, paint)
                                }
                                override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
                                @Deprecated("Deprecated in Java")
                                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
                            }
                            view.background = circleBg

                        } catch (e: Throwable) {
                            Log.e(TAG, "Error configuring key", e)
                        }
                    }
                    return result
                }
            })

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
