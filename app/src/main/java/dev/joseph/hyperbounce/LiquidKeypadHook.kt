package dev.joseph.hyperbounce

import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method

object LiquidKeypadHook {
    private const val TAG = "HyperBounce_Log"

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

        // 1. Hook onMeasure to catch exact dimensional constraints assigned by MIUI
        try {
            val onMeasureMethod = findMethod(clazz, "onMeasure", Int::class.java, Int::class.java)
            module.hook(onMeasureMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.thisObject as View
                    Log.e(TAG, "[onMeasure] ${view.javaClass.simpleName} Measured: ${view.measuredWidth}x${view.measuredHeight}")
                    return result
                }
            })
        } catch (e: Throwable) {}

        // 2. Hook onAttachedToWindow for Drawing and PreDraw Tree traversal
        try {
            val onAttachedMethod = findMethod(clazz, "onAttachedToWindow")
            module.hook(onAttachedMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.thisObject as View
                    
                    Log.e(TAG, "[onAttachedToWindow] Hooked view: ${view.javaClass.simpleName}")

                    // Use PreDrawListener instead of post() to guarantee execution right before rendering
                    view.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            view.viewTreeObserver.removeOnPreDrawListener(this)
                            
                            var current: View? = view
                            var depth = 0
                            while (current != null && depth <= 3) {
                                val lp = current.layoutParams
                                var info = ""
                                if (lp is ViewGroup.MarginLayoutParams) {
                                    info += "Margins(L:${lp.leftMargin} T:${lp.topMargin} R:${lp.rightMargin} B:${lp.bottomMargin}) "
                                }
                                Log.e(TAG, "[PreDraw Depth $depth] ${current.javaClass.name} [${current.width}x${current.height}] $info")

                                // Hijack attempt inside PreDraw (before frame renders)
                                if (depth == 1 && current is LinearLayout) {
                                    if (lp is ViewGroup.MarginLayoutParams && lp.bottomMargin != 60) {
                                        Log.e(TAG, "=> HIJACKING ROW SPACING: Changing bottomMargin to 60")
                                        lp.bottomMargin = 60
                                        current.layoutParams = lp
                                        // We do not call requestLayout() here to avoid infinite PreDraw loops
                                    }
                                }
                                current = current.parent as? View
                                depth++
                            }
                            return true
                        }
                    })

                    // Hide Letters
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

                    // Kill MIUI Animations
                    try {
                        val setAnimMethod = findMethod(view.javaClass, "setAnimationEnabled", java.lang.Boolean.TYPE)
                        setAnimMethod.invoke(view, false)
                    } catch (e: Throwable) {}

                    // Custom Circle Background
                    val circleBg = object : android.graphics.drawable.Drawable() {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = Color.parseColor("#40FFFFFF")
                            style = Paint.Style.FILL
                        }
                        override fun draw(canvas: android.graphics.Canvas) {
                            val size = minOf(bounds.width(), bounds.height()).toFloat()
                            val radius = (size / 2f) * 0.85f 
                            canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, paint)
                        }
                        override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
                        @Deprecated("Deprecated in Java")
                        override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
                    }
                    view.background = circleBg
                    
                    return result
                }
            })
        } catch (e: Throwable) {}

        // 3. Touch Event Physics
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
