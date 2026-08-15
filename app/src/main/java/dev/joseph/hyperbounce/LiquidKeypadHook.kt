package dev.joseph.hyperbounce

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

                            // 1. Extract the digit and hide ALL native text securely
                            var digitStr = ""
                            var cls: Class<*>? = view.javaClass
                            while (cls != null) {
                                try {
                                    val mDigitText = cls.getDeclaredField("mDigitText")
                                    mDigitText.isAccessible = true
                                    val digitTextView = mDigitText.get(view) as? TextView
                                    if (digitTextView != null) {
                                        digitStr = digitTextView.text.toString()
                                        digitTextView.visibility = View.INVISIBLE // Hide native digit, Compose will draw it
                                    }

                                    val mKlondikeText = cls.getDeclaredField("mKlondikeText")
                                    mKlondikeText.isAccessible = true
                                    (mKlondikeText.get(view) as? TextView)?.visibility = View.GONE
                                    break
                                } catch (e: NoSuchFieldException) { cls = cls.superclass }
                            }

                            // 2. Kill native scaling animations
                            try {
                                val setAnimMethod = view.javaClass.getMethod("setAnimationEnabled", Boolean::class.javaPrimitiveType)
                                setAnimMethod.invoke(view, false)
                            } catch (e: Throwable) {}
                            
                            // Prevent native visual ripple/background to leave room for Compose
                            view.background = null

                            // =========================================================
                            // 3. THE TUNED XML REWRITE HACK
                            // =========================================================
                            val keySizePx = (82f * density).toInt()     
                            val vMarginPx = (28f * density).toInt()     // UNCHANGED
                            val hMarginPx = (10f * density).toInt()     // SLIGHTLY REDUCED horizontal gap
                            
                            val bottomLiftPx = (115f * density).toInt() // INCREASED: Lifts grid further up
                            val topPullPx = (-50f * density).toInt()    // INCREASED: Pulls grid much closer to PIN dots

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
                                    
                                    if (digitStr == "0") {
                                        rowLp.bottomMargin = bottomLiftPx
                                    } else {
                                        rowLp.bottomMargin = vMarginPx
                                    }
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
                            
                            view.translationX = 0f
                            view.translationY = 0f

                            // =========================================================
                            // 4. Inject Compose Jelly Button Engine
                            // =========================================================
                            LiquidKeypadHost.ensure(view, digitStr)

                        } catch (e: Throwable) {
                            Log.e(TAG, "Error configuring key", e)
                        }
                    }
                    return result
                }
            })

            // Pipe native touch events into the Compose host to drive the jelly physics,
            // while allowing the native view to process the actual PIN entry sequence.
            val onTouchMethod = findMethod(keyClass, "onTouchEvent", MotionEvent::class.java)
            module.hook(onTouchMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val view = chain.thisObject as View
                    val event = chain.args[0] as MotionEvent
                    LiquidKeypadHost.forwardTouchEvent(view, event)
                    return chain.proceed()
                }
            })

        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook MiuiNumPadKey", e)
        }
    }
}
