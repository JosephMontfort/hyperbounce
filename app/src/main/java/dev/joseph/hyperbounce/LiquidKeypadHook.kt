package dev.joseph.hyperbounce

import android.graphics.Color
import android.graphics.Paint
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
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

            // Hook onAttachedToWindow to apply permanent translations & styling
            val onAttachedMethod = findMethod(keyClass, "onAttachedToWindow")
            module.hook(onAttachedMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.thisObject as View

                    view.post {
                        try {
                            // 1. Disable clipping so massive circles don't get chopped off vertically
                            view.clipToOutline = false
                            (view.parent as? ViewGroup)?.clipChildren = false
                            (view.parent?.parent as? ViewGroup)?.clipChildren = false

                            // 2. Extract Digit Text to determine exact keypad position
                            var digitStr = ""
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
                            if (digitText != null) {
                                digitText.gravity = Gravity.CENTER
                                digitText.setPadding(0, 0, 0, 0)
                                digitStr = digitText.text.toString()
                            }

                            // 3. Hide Klondike text
                            var kCls: Class<*>? = view.javaClass
                            var kField: java.lang.reflect.Field? = null
                            while (kCls != null) {
                                try {
                                    kField = kCls.getDeclaredField("mKlondikeText")
                                    break
                                } catch (e: NoSuchFieldException) { kCls = kCls.superclass }
                            }
                            kField?.isAccessible = true
                            (kField?.get(view) as? TextView)?.visibility = View.GONE

                            // =========================================================
                            // 4. THE MATRIX TRANSLATION HACK (Massive Spacing)
                            // =========================================================
                            val density = view.resources.displayMetrics.density
                            val xOffset = 25f * density // Horizontal push outwards
                            val yOffset = 30f * density // Vertical push apart

                            when (digitStr) {
                                "1" -> { view.translationX = -xOffset; view.translationY = -yOffset * 1.5f }
                                "2" -> { view.translationX = 0f;       view.translationY = -yOffset * 1.5f }
                                "3" -> { view.translationX = xOffset;  view.translationY = -yOffset * 1.5f }

                                "4" -> { view.translationX = -xOffset; view.translationY = -yOffset * 0.5f }
                                "5" -> { view.translationX = 0f;       view.translationY = -yOffset * 0.5f }
                                "6" -> { view.translationX = xOffset;  view.translationY = -yOffset * 0.5f }

                                "7" -> { view.translationX = -xOffset; view.translationY = yOffset * 0.5f }
                                "8" -> { view.translationX = 0f;       view.translationY = yOffset * 0.5f }
                                "9" -> { view.translationX = xOffset;  view.translationY = yOffset * 0.5f }

                                "0" -> { view.translationX = 0f;       view.translationY = yOffset * 1.5f }
                            }

                            // Kill native animations
                            try {
                                val setAnimMethod = view.javaClass.getMethod("setAnimationEnabled", Boolean::class.javaPrimitiveType)
                                setAnimMethod.invoke(view, false)
                            } catch (e: Throwable) {}

                            // 5. Massive Circle Background
                            view.setPadding(0, 0, 0, 0)
                            val circleBg = object : android.graphics.drawable.Drawable() {
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = Color.parseColor("#40FFFFFF")
                                    style = Paint.Style.FILL
                                }
                                override fun draw(canvas: android.graphics.Canvas) {
                                    // By multiplying by 1.55, the radius expands way beyond the 99px height boundary.
                                    val radius = (bounds.height() / 2f) * 1.55f
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
