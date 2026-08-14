package dev.joseph.hyperbounce

import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ShapeDrawable
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
            return // Target class not found in this classloader
        }

        // 1. Hook Initialization to Inject Spacing and Shapes
        try {
            val onAttachedMethod = findMethod(clazz, "onAttachedToWindow")
            
            module.hook(onAttachedMethod).intercept(object : XposedInterface.Hooker {
                override fun call(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.thisObject as View
                    
                    // Hide the Klondike Text safely without collapsing the layout height
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
                        klondikeText?.visibility = View.INVISIBLE
                    } catch (e: Throwable) {
                        Log.e("HyperBounce", "LiquidKeypadHook: Failed to hide klondike text", e)
                    }

                    // Kill native MIUI animations
                    try {
                        val setAnimMethod = findMethod(view.javaClass, "setAnimationEnabled", java.lang.Boolean.TYPE)
                        setAnimMethod.invoke(view, false)
                    } catch (e: Throwable) {}

                    // Inject Custom Circular Background
                    val ovalShape = ShapeDrawable(OvalShape()).apply {
                        paint.color = Color.parseColor("#40FFFFFF")
                        paint.style = Paint.Style.FILL
                    }
                    view.background = ovalShape

                    // Post layout execution to guarantee margins override XML
                    view.post {
                        val lp = view.layoutParams
                        if (lp is LinearLayout.LayoutParams) {
                            val density = view.resources.displayMetrics.density
                            val hMargin = (16f * density).toInt() 
                            val vMargin = (12f * density).toInt() 
                            
                            lp.setMargins(hMargin, vMargin, hMargin, vMargin)
                            view.layoutParams = lp
                            view.requestLayout()
                        }
                    }
                    
                    return result
                }
            })
        } catch (e: Throwable) {
            Log.e("HyperBounce", "LiquidKeypadHook: Failed to hook onAttachedToWindow", e)
        }

        // 2. Hook Touch Events for iOS Physics
        try {
            val onTouchMethod = findMethod(clazz, "onTouchEvent", MotionEvent::class.java)
            val spring = OvershootInterpolator(2.5f)
            
            module.hook(onTouchMethod).intercept(object : XposedInterface.Hooker {
                override fun call(chain: XposedInterface.Chain): Any? {
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
            Log.e("HyperBounce", "LiquidKeypadHook: Failed to hook onTouchEvent", e)
        }
    }
}
