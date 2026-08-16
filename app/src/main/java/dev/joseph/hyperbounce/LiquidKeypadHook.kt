package dev.joseph.hyperbounce

import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import dev.joseph.hyperbounce.liquidglass.JellyOverlayView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.WeakHashMap

object LiquidKeypadHook {
    private const val TAG = "HyperBounce_Keypad"
    private var overlayView: JellyOverlayView? = null
    private val keyDigitMap = WeakHashMap<View, String>()

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
            // 1. Inject the Full-Screen Canvas safely into the Parent FrameLayout
            val onAttachedRootMethod = findMethod(pinViewClass, "onAttachedToWindow")
            module.hook(onAttachedRootMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val container = chain.thisObject as ViewGroup
                    
                    container.post {
                        val parent = container.parent as? ViewGroup
                        if (parent != null) {
                            if (overlayView == null || overlayView?.parent == null) {
                                val overlay = JellyOverlayView(container.context)
                                overlay.layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT, 
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                // Clear existing to prevent duplicate stacking
                                (overlay.parent as? ViewGroup)?.removeView(overlay)
                                
                                parent.addView(overlay)
                                overlayView = overlay
                            }
                        }
                    }
                    return result
                }
            })

            // 2. Hide Native Visuals and Register Keys dynamically
            val onAttachedKeyMethod = findMethod(keyClass, "onAttachedToWindow")
            module.hook(onAttachedKeyMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.thisObject as View

                    view.post {
                        try {
                            val density = view.resources.displayMetrics.density

                            var digitStr = ""
                            var cls: Class<*>? = view.javaClass
                            while (cls != null) {
                                try {
                                    val mDigitText = cls.getDeclaredField("mDigitText")
                                    mDigitText.isAccessible = true
                                    (mDigitText.get(view) as? TextView)?.let {
                                        digitStr = it.text.toString()
                                        it.alpha = 0f // Make perfectly invisible
                                    }

                                    val mKlondikeText = cls.getDeclaredField("mKlondikeText")
                                    mKlondikeText.isAccessible = true
                                    (mKlondikeText.get(view) as? TextView)?.visibility = View.GONE
                                    break
                                } catch (e: NoSuchFieldException) { cls = cls.superclass }
                            }
                            
                            keyDigitMap[view] = digitStr
                            overlayView?.registerNode(digitStr, view) // Pass raw view for dynamic tracking

                            try {
                                val setAnimMethod = view.javaClass.getMethod("setAnimationEnabled", Boolean::class.javaPrimitiveType)
                                setAnimMethod.invoke(view, false)
                            } catch (e: Throwable) {}
                            
                            view.background = null

                            // Tuned Spacing XML Rewrite
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

            // 3. Forward Touch Events to Canvas Physics Engine
            val dispatchTouchMethod = findMethod(keyClass, "onTouchEvent", MotionEvent::class.java)
            module.hook(dispatchTouchMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val view = chain.thisObject as View
                    val event = chain.args[0] as MotionEvent
                    val digit = keyDigitMap[view]
                    
                    if (digit != null) {
                        overlayView?.handleTouch(digit, event.actionMasked, event.rawX, event.rawY)
                    }
                    
                    return chain.proceed() // Always let the system process the actual PIN click
                }
            })

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook Keypad", e)
        }
    }
}
