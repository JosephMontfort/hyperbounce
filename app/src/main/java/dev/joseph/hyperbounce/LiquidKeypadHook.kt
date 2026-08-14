package dev.joseph.hyperbounce

import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule

object LiquidKeypadHook {
    private const val TAG = "HyperBounce_Spacing"

    fun apply(module: XposedModule, classLoader: ClassLoader) {
        val clazz = try {
            Class.forName("com.android.keyguard.widget.MiuiNumPadKey", false, classLoader)
        } catch (e: Throwable) {
            // If it's not in this classloader (e.g. plugin vs systemui), exit silently
            return 
        }

        Log.e(TAG, "SUCCESS: Found MiuiNumPadKey class. Applying hooks...")

        // 1. Hook Constructors directly to intercept View Creation
        try {
            val constructors = clazz.declaredConstructors
            for (constructor in constructors) {
                constructor.isAccessible = true
                module.hook(constructor).intercept(object : XposedInterface.Hooker {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        val result = chain.proceed() // Execute original constructor
                        val view = chain.thisObject as View
                        
                        Log.e(TAG, "MiuiNumPadKey constructed. Injecting native LayoutListener.")

                        // Kill native animations
                        try {
                            val setAnimMethod = view.javaClass.getMethod("setAnimationEnabled", Boolean::class.javaPrimitiveType)
                            setAnimMethod.invoke(view, false)
                        } catch (e: Throwable) {}

                        // Apply Perfect Circles
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

                        // Attach native Android layout listener to hijack the dimensions frame-by-frame
                        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ ->
                            val parent = v.parent as? ViewGroup
                            val lp = v.layoutParams

                            // Hide the Klondike Letters safely
                            try {
                                var currentClass: Class<*>? = v.javaClass
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
                                val klondikeText = field?.get(v) as? TextView
                                if (klondikeText != null && klondikeText.visibility != View.GONE) {
                                    klondikeText.visibility = View.GONE
                                }
                            } catch (e: Throwable) {
                                Log.e(TAG, "Failed to hide klondike text", e)
                            }

                            // HIJACK 1: Push the Grandparent Rows Apart
                            if (parent is LinearLayout) {
                                val parentLp = parent.layoutParams
                                if (parentLp is ViewGroup.MarginLayoutParams) {
                                    if (parentLp.bottomMargin != 60) { // Force 60px gap between rows
                                        Log.e(TAG, "=> HIJACKING SPACING: Pushing parent LinearLayout bottomMargin from ${parentLp.bottomMargin} to 60")
                                        parentLp.bottomMargin = 60
                                        parent.layoutParams = parentLp
                                    }
                                }
                            }

                            // HIJACK 2: Strip vertical constraints off the child to allow perfect circular expansion
                            if (lp is LinearLayout.LayoutParams) {
                                if (lp.topMargin != 0 || lp.bottomMargin != 0) {
                                    val density = v.resources.displayMetrics.density
                                    val hMargin = (18f * density).toInt() 
                                    lp.setMargins(hMargin, 0, hMargin, 0)
                                    v.layoutParams = lp
                                }
                            }
                        }
                        return result
                    }
                })
            }
        } catch (e: Throwable) {
            Log.e(TAG, "FATAL: Failed to hook constructors", e)
        }

        // 2. Touch Physics Hook
        try {
            // Using getDeclaredMethod to avoid crawling the parent tree
            val onTouchMethod = clazz.getDeclaredMethod("onTouchEvent", MotionEvent::class.java)
            onTouchMethod.isAccessible = true
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
            Log.e(TAG, "FATAL: Failed to hook onTouchEvent", e)
        }
    }
}
