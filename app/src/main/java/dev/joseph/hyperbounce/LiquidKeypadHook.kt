package dev.joseph.hyperbounce

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.tanh

object LiquidKeypadHook {
    private const val TAG = "HyperBounce_Keypad"
    private val startTouchMap = WeakHashMap<View, Pair<Float, Float>>()

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
        try {
            val keyClass = Class.forName("com.android.keyguard.widget.MiuiNumPadKey", false, classLoader)

            // =========================================================
            // 1. Layout, Spacing, Native Glass, and Entrance Animation
            // =========================================================
            val onAttachedMethod = findMethod(keyClass, "onAttachedToWindow")
            module.hook(onAttachedMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.thisObject as View

                    view.post {
                        try {
                            val density = view.resources.displayMetrics.density

                            // Hide Klondike text, Center Digits, and Extract the Number
                            var digitStr = ""
                            var cls: Class<*>? = view.javaClass
                            while (cls != null) {
                                try {
                                    val mDigitText = cls.getDeclaredField("mDigitText")
                                    mDigitText.isAccessible = true
                                    val digitText = mDigitText.get(view) as? TextView
                                    digitText?.let {
                                        it.gravity = Gravity.CENTER
                                        it.setPadding(0, 0, 0, 0)
                                        digitStr = it.text.toString()
                                    }

                                    val mKlondikeText = cls.getDeclaredField("mKlondikeText")
                                    mKlondikeText.isAccessible = true
                                    (mKlondikeText.get(view) as? TextView)?.visibility = View.GONE
                                    break
                                } catch (e: NoSuchFieldException) { cls = cls.superclass }
                            }

                            // Kill native animations to allow our physics engine to take over
                            try {
                                val setAnimMethod = view.javaClass.getMethod("setAnimationEnabled", Boolean::class.javaPrimitiveType)
                                setAnimMethod.invoke(view, false)
                            } catch (e: Throwable) {}

                            // --- GRID SPACING REWRITE ---
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

                            // --- NATIVE LIQUID GLASS BACKGROUND ---
                            view.setPadding(0, 0, 0, 0)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                view.setRenderEffect(RenderEffect.createBlurEffect(12f, 12f, Shader.TileMode.CLAMP))
                            }
                            
                            val circleBg = object : android.graphics.drawable.Drawable() {
                                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    color = Color.parseColor("#35FFFFFF") // Subtle glass tint
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

                            // --- iOS 26 STAGGERED ENTRANCE ANIMATION ---
                            val delayMap = mapOf("1" to 0L, "2" to 35L, "3" to 70L, "4" to 105L, "5" to 140L, "6" to 175L, "7" to 210L, "8" to 245L, "9" to 280L, "0" to 315L)
                            val enterDelay = delayMap[digitStr] ?: 0L

                            view.scaleX = 0f
                            view.scaleY = 0f
                            view.alpha = 0f
                            
                            view.animate()
                                .scaleX(1f).scaleY(1f).alpha(1f)
                                .setStartDelay(enterDelay)
                                .setDuration(450)
                                .setInterpolator(OvershootInterpolator(1.2f))
                                .start()

                        } catch (e: Throwable) {
                            Log.e(TAG, "Error configuring key", e)
                        }
                    }
                    return result
                }
            })

            // =========================================================
            // 2. iOS 26 Native Jelly Physics Engine
            // =========================================================
            val onTouchMethod = findMethod(keyClass, "onTouchEvent", MotionEvent::class.java)
            module.hook(onTouchMethod).intercept(object : XposedInterface.Hooker {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    val result = chain.proceed()
                    val view = chain.thisObject as View
                    val event = chain.args[0] as MotionEvent

                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            // Store initial touch coordinates
                            startTouchMap[view] = Pair(event.rawX, event.rawY)
                            
                            // Instantly shrink for depth effect
                            view.animate().cancel()
                            view.animate().scaleX(0.85f).scaleY(0.85f).setDuration(100).setInterpolator(DecelerateInterpolator()).start()
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val startPos = startTouchMap[view] ?: return result
                            
                            val dx = event.rawX - startPos.first
                            val dy = event.rawY - startPos.second
                            val maxOffset = view.width * 1.5f // Maximum stretching drag limit

                            // Tanh Asymptote Translation (Elastic drag)
                            val tx = maxOffset * tanh((dx / maxOffset).toDouble()).toFloat()
                            val ty = maxOffset * tanh((dy / maxOffset).toDouble()).toFloat()
                            view.translationX = tx
                            view.translationY = ty

                            // Trigonometric Squash and Stretch Deformation
                            val dragDist = hypot(tx.toDouble(), ty.toDouble()).toFloat()
                            val maxDragScale = 0.25f
                            val stretch = maxDragScale * (dragDist / maxOffset)
                            val angle = atan2(ty.toDouble(), tx.toDouble()).toFloat()
                            
                            val baseScale = 0.85f
                            view.scaleX = baseScale + stretch * abs(cos(angle.toDouble())).toFloat() - stretch * 0.5f * abs(sin(angle.toDouble())).toFloat()
                            view.scaleY = baseScale + stretch * abs(sin(angle.toDouble())).toFloat() - stretch * 0.5f * abs(cos(angle.toDouble())).toFloat()
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            startTouchMap.remove(view)
                            
                            // Bouncy Snap Back to Center
                            view.animate().cancel()
                            view.animate()
                                .translationX(0f).translationY(0f)
                                .scaleX(1f).scaleY(1f)
                                .setDuration(500)
                                .setInterpolator(OvershootInterpolator(3.0f))
                                .start()
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
