package dev.joseph.hyperbounce

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.shapes.OvalShape
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

object LiquidKeypadHook {

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
        val clazz = try {
            Class.forName("com.android.keyguard.widget.MiuiNumPadKey", false, classLoader)
        } catch (e: Throwable) {
            return 
        }

        try {
            val onAttachedMethod = findMethod(clazz, "onAttachedToWindow")
            
            module.hook(onAttachedMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.thisObject as View
                    
                    // Collapse the Klondike Text layout space completely 
                    try {
                        var currentClass: Class<*>? = view.javaClass
                        var field: java.lang.reflect.Field? = null
                        while (currentClass != null) {
                            try {
                                field = currentClass.getDeclaredField("mKlondikeText")
                                break
                            } catch (e: NoSuchFieldException) {
                                currentClass = currentClass.superclass
                            }
                        }
                        field?.isAccessible = true
                        val klondikeText = field?.get(view) as? TextView
                        klondikeText?.visibility = View.GONE
                    } catch (e: Throwable) {}

                    // Kill native MIUI animations
                    try {
                        val setAnimMethod = findMethod(view.javaClass, "setAnimationEnabled", java.lang.Boolean.TYPE)
                        setAnimMethod.invoke(view, false)
                    } catch (e: Throwable) {}

                    // Custom Centered Circular Background
                    val circleBg = object : android.graphics.drawable.Drawable() {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#40FFFFFF")
                            style = Paint.Style.FILL
                        }
                        override fun draw(canvas: android.graphics.Canvas) {
                            val size = minOf(bounds.width(), bounds.height()).toFloat()
                            val radius = (size / 2f) * 0.85f // 85% of available reclaimed square
                            canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, paint)
                        }
                        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
                        @Deprecated("Deprecated in Java")
                        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
                    }
                    view.background = circleBg

                    // Aggressive Layout Listener to fight MIUI's margin overrides
                    view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                        val lp = v.layoutParams
                        if (lp is LinearLayout.LayoutParams) {
                            // If MIUI forces margins, instantly strip them to reclaim height
                            if (lp.topMargin != 0 || lp.bottomMargin != 0) {
                                val density = v.resources.displayMetrics.density
                                val hMargin = (18f * density).toInt() 
                                
                                // 0 vertical margin forces view to expand to full row height
                                lp.setMargins(hMargin, 0, hMargin, 0)
                                v.layoutParams = lp
                            }
                        }
                    }
                    
                    return result
                }
            })
        } catch (e: Throwable) {}

        try {
            val onTouchMethod = findMethod(clazz, "onTouchEvent", MotionEvent::class.java)
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
        } catch (e: Throwable) {}
    }
}
