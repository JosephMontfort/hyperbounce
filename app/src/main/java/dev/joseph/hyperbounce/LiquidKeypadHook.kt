package dev.joseph.hyperbounce

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
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
import java.util.WeakHashMap
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

object LiquidKeypadHook {
    private const val TAG = "HyperBounce_Keypad"

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    /**
     * Small spring integrator.
     *
     * The jelly drag uses a moderately damped spring.
     * The release squish uses a softer spring so the return is slower
     * and visibly settles instead of snapping back.
     */
    class Spring(
        var current: Float = 0f,
        var target: Float = 0f,
        var velocity: Float = 0f
    ) {
        fun update(stiffness: Float, damping: Float): Boolean {
            val displacement = current - target

            val acceleration =
                (-stiffness * displacement) +
                (-damping * velocity)

            velocity += acceleration * 0.016f
            current += velocity * 0.016f

            if (abs(current - target) < 0.0005f &&
                abs(velocity) < 0.0005f
            ) {
                current = target
                velocity = 0f
            }

            return abs(current - target) > 0.0005f ||
                abs(velocity) > 0.0005f
        }
    }

    class KeyState(val digit: String) {
        // Raw drag springs. These are NOT view translation.
        val rawDx = Spring()
        val rawDy = Spring()

        // Entrance / press alpha-scale.
        val scaleX = Spring(0f, 1f)
        val scaleY = Spring(0f, 1f)
        val alpha = Spring(0f, 1f)

        /**
         * Extra jelly compression during the return phase.
         *
         * Positive value = stronger overall squish.
         */
        val returnSquish = Spring()

        /**
         * Side-to-side wobble after release.
         */
        val returnWobble = Spring()

        var isPressed = false

        var startTouchX = 0f
        var startTouchY = 0f

        var lastTouchX = 0f
        var lastTouchY = 0f

        var lastDragDx = 0f
        var lastDragDy = 0f

        /**
         * Direction of the most recent drag/release.
         * This is intentionally retained after release so the rebound
         * deformation follows the direction from which the key returned.
         */
        var releaseAngle = 0f
    }

    private val stateMap = WeakHashMap<View, KeyState>()

    private fun findMethod(
        clazz: Class<*>,
        name: String,
        vararg parameterTypes: Class<*>
    ): Method {
        var current: Class<*>? = clazz

        while (current != null) {
            try {
                val method = current.getDeclaredMethod(name, *parameterTypes)
                method.isAccessible = true
                return method
            } catch (_: NoSuchMethodException) {
                current = current.superclass
            }
        }

        throw NoSuchMethodException(name)
    }

    fun apply(module: XposedModule, classLoader: ClassLoader) {
        val keyClass = try {
            Class.forName(
                "com.android.keyguard.widget.MiuiNumPadKey",
                false,
                classLoader
            )
        } catch (_: ClassNotFoundException) {
            return
        }

        try {
            // ============================================================
            // 1. ATTACHMENT / EXISTING LAYOUT + PINNING
            // ============================================================
            val onAttachedKeyMethod =
                findMethod(keyClass, "onAttachedToWindow")

            module.hook(onAttachedKeyMethod).intercept(
                object : XposedInterface.Hooker {

                    override fun intercept(
                        chain: XposedInterface.Chain
                    ): Any? {
                        val result = chain.proceed()
                        val view = chain.thisObject as View

                        view.post {
                            try {
                                val density =
                                    view.resources.displayMetrics.density

                                // Keep existing clipping/layout behavior.
                                view.clipToOutline = false

                                (view.parent as? ViewGroup)?.clipChildren = false
                                (view.parent?.parent as? ViewGroup)?.clipChildren = false

                                view.setWillNotDraw(false)
                                view.background = null

                                var digitStr = ""

                                var cls: Class<*>? = view.javaClass

                                while (cls != null) {
                                    try {
                                        val mDigitText =
                                            cls.getDeclaredField("mDigitText")
                                        mDigitText.isAccessible = true

                                        (mDigitText.get(view) as? TextView)?.let {
                                            digitStr = it.text.toString()
                                            it.visibility = View.INVISIBLE
                                        }

                                        val mKlondikeText =
                                            cls.getDeclaredField("mKlondikeText")
                                        mKlondikeText.isAccessible = true

                                        (mKlondikeText.get(view) as? TextView)?.visibility =
                                            View.GONE

                                        break
                                    } catch (_: NoSuchFieldException) {
                                        cls = cls.superclass
                                    }
                                }

                                val state = KeyState(digitStr)
                                stateMap[view] = state

                                // ====================================================
                                // EXISTING ENTRANCE ANIMATION — UNCHANGED
                                // ====================================================
                                val numericVal = digitStr.toIntOrNull() ?: 10

                                val delayMs =
                                    if (numericVal == 0) {
                                        315L
                                    } else {
                                        (numericVal - 1) * 35L
                                    }

                                view.postDelayed({
                                    state.scaleX.target = 1f
                                    state.scaleY.target = 1f
                                    state.alpha.target = 1f
                                    view.postInvalidateOnAnimation()
                                }, delayMs)

                                try {
                                    val setAnimMethod =
                                        view.javaClass.getMethod(
                                            "setAnimationEnabled",
                                            Boolean::class.javaPrimitiveType
                                        )

                                    setAnimMethod.invoke(view, false)
                                } catch (_: Throwable) {
                                }

                                // ====================================================
                                // EXISTING PINNED LAYOUT — DO NOT CHANGE
                                // ====================================================
                                val keySizePx = (82f * density).toInt()
                                val vMarginPx = (28f * density).toInt()
                                val hMarginPx = (10f * density).toInt()
                                val bottomLiftPx = (115f * density).toInt()
                                val topPullPx = (-50f * density).toInt()

                                val row = view.parent as? LinearLayout
                                val grid = row?.parent as? LinearLayout

                                grid?.let {
                                    val gridLp =
                                        it.layoutParams as? ViewGroup.MarginLayoutParams

                                    if (gridLp != null) {
                                        var changed = false

                                        if (
                                            gridLp.height !=
                                            ViewGroup.LayoutParams.WRAP_CONTENT
                                        ) {
                                            gridLp.height =
                                                ViewGroup.LayoutParams.WRAP_CONTENT
                                            changed = true
                                        }

                                        if (gridLp.topMargin != topPullPx) {
                                            gridLp.topMargin = topPullPx
                                            changed = true
                                        }

                                        if (changed) {
                                            it.layoutParams = gridLp
                                        }
                                    }
                                }

                                row?.let {
                                    it.gravity = Gravity.CENTER_HORIZONTAL

                                    val rowLp =
                                        it.layoutParams as? LinearLayout.LayoutParams

                                    if (rowLp != null) {
                                        rowLp.height =
                                            ViewGroup.LayoutParams.WRAP_CONTENT

                                        rowLp.weight = 0f

                                        rowLp.bottomMargin =
                                            if (digitStr == "0") {
                                                bottomLiftPx
                                            } else {
                                                vMarginPx
                                            }

                                        it.layoutParams = rowLp
                                    }
                                }

                                val keyLp =
                                    view.layoutParams as? LinearLayout.LayoutParams

                                if (keyLp != null) {
                                    keyLp.width = keySizePx
                                    keyLp.height = keySizePx
                                    keyLp.weight = 0f
                                    keyLp.leftMargin = hMarginPx
                                    keyLp.rightMargin = hMarginPx
                                    view.layoutParams = keyLp
                                }

                            } catch (e: Throwable) {
                                Log.e(
                                    TAG,
                                    "Error configuring key constraints",
                                    e
                                )
                            }
                        }

                        return result
                    }
                }
            )

            // ============================================================
            // 2. TOUCH INPUT
            // ============================================================
            val onTouchMethod =
                findMethod(
                    keyClass,
                    "onTouchEvent",
                    MotionEvent::class.java
                )

            module.hook(onTouchMethod).intercept(
                object : XposedInterface.Hooker {

                    override fun intercept(
                        chain: XposedInterface.Chain
                    ): Any? {
                        val view = chain.thisObject as View
                        val event = chain.args[0] as MotionEvent

                        val state =
                            stateMap[view] ?: return chain.proceed()

                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                state.isPressed = true

                                state.startTouchX = event.rawX
                                state.startTouchY = event.rawY

                                state.lastTouchX = event.rawX
                                state.lastTouchY = event.rawY

                                state.lastDragDx = 0f
                                state.lastDragDy = 0f

                                state.returnSquish.target = 0f
                                state.returnWobble.target = 0f

                                // Deep tactile press.
                                state.scaleX.target = 0.88f
                                state.scaleY.target = 0.88f

                                view.postInvalidateOnAnimation()
                            }

                            MotionEvent.ACTION_MOVE -> {
                                if (state.isPressed) {
                                    val dx =
                                        event.rawX - state.startTouchX

                                    val dy =
                                        event.rawY - state.startTouchY

                                    state.lastDragDx = dx
                                    state.lastDragDy = dy

                                    state.lastTouchX = event.rawX
                                    state.lastTouchY = event.rawY

                                    // Directly drive deformation.
                                    //
                                    // IMPORTANT:
                                    // Negative dx/dy remain negative.
                                    // Therefore bottom -> top (negative dy)
                                    // is now a real directional jelly pull.
                                    state.rawDx.target = dx
                                    state.rawDy.target = dy

                                    view.postInvalidateOnAnimation()
                                }
                            }

                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> {
                                state.isPressed = false

                                val releaseDx = state.lastDragDx
                                val releaseDy = state.lastDragDy

                                val maxOffset =
                                    max(1f, view.width * 1.5f)

                                val releaseDistance =
                                    hypot(
                                        releaseDx.toDouble(),
                                        releaseDy.toDouble()
                                    ).toFloat()

                                if (releaseDistance > 0.5f) {
                                    state.releaseAngle =
                                        kotlin.math.atan2(
                                            releaseDy,
                                            releaseDx
                                        )
                                }

                                /**
                                 * Generate an explicit release squish.
                                 *
                                 * More displacement = stronger jelly rebound,
                                 * but clamp it to prevent extreme deformation.
                                 */
                                val releaseStrength =
                                    (
                                        releaseDistance / maxOffset
                                    ).coerceIn(0f, 1f)

                                state.returnSquish.current =
                                    0.08f +
                                    releaseStrength * 0.25f

                                state.returnSquish.target = 0f

                                /**
                                 * Initial wobble follows release velocity.
                                 * This makes the button look like soft material
                                 * instead of a rubber ellipse.
                                 */
                                state.returnWobble.current =
                                    releaseStrength * 0.16f

                                state.returnWobble.target = 0f

                                // Return to pinned center.
                                state.rawDx.target = 0f
                                state.rawDy.target = 0f

                                state.scaleX.target = 1f
                                state.scaleY.target = 1f

                                view.postInvalidateOnAnimation()
                            }
                        }

                        return chain.proceed()
                    }
                }
            )

            // ============================================================
            // 3. TRUE JELLY RENDERER
            // ============================================================
            val onDrawMethod =
                findMethod(
                    keyClass,
                    "onDraw",
                    Canvas::class.java
                )

            module.hook(onDrawMethod).intercept(
                object : XposedInterface.Hooker {

                    override fun intercept(
                        chain: XposedInterface.Chain
                    ): Any? {
                        val view = chain.thisObject as View
                        val canvas = chain.args[0] as Canvas

                        val state =
                            stateMap[view]
                                ?: return chain.proceed()

                        // ------------------------------------------------
                        // Physics
                        // ------------------------------------------------

                        val dragActive1 =
                            state.rawDx.update(
                                205f,
                                18f
                            )

                        val dragActive2 =
                            state.rawDy.update(
                                205f,
                                18f
                            )

                        val scale1 =
                            state.scaleX.update(
                                175f,
                                16f
                            )

                        val scale2 =
                            state.scaleY.update(
                                175f,
                                16f
                            )

                        val alphaActive =
                            state.alpha.update(
                                150f,
                                20f
                            )

                        // Deliberately softer/slower return.
                        val returnSquishActive =
                            state.returnSquish.update(
                                72f,
                                9.5f
                            )

                        val returnWobbleActive =
                            state.returnWobble.update(
                                58f,
                                7.5f
                            )

                        if (
                            dragActive1 ||
                            dragActive2 ||
                            scale1 ||
                            scale2 ||
                            alphaActive ||
                            returnSquishActive ||
                            returnWobbleActive
                        ) {
                            view.postInvalidateOnAnimation()
                        }

                        // ------------------------------------------------
                        // Pinned center — NEVER translate the View itself.
                        // ------------------------------------------------
                        val cx = view.width / 2f
                        val cy = view.height / 2f
                        val radius = view.width / 2f

                        // ------------------------------------------------
                        // Bound drag while preserving sign.
                        // ------------------------------------------------
                        val maxOffset =
                            max(1f, view.width * 1.5f)

                        val dx =
                            maxOffset *
                                tanh(
                                    state.rawDx.current /
                                        maxOffset
                                )

                        val dy =
                            maxOffset *
                                tanh(
                                    state.rawDy.current /
                                        maxOffset
                                )

                        val dragDistance =
                            hypot(
                                dx.toDouble(),
                                dy.toDouble()
                            ).toFloat()

                        val normalizedDrag =
                            (
                                dragDistance /
                                    maxOffset
                            ).coerceIn(0f, 1f)

                        // ------------------------------------------------
                        // Direction.
                        //
                        // Preserve BOTH signs. This is what fixes:
                        //    bottom -> top
                        //    right -> left
                        //    diagonal pulls
                        // ------------------------------------------------
                        val dragAngle =
                            if (dragDistance > 0.001f) {
                                kotlin.math.atan2(dy, dx)
                            } else {
                                state.releaseAngle
                            }

                        val dirX = cos(dragAngle)
                        val dirY = sin(dragAngle)

                        val perpX = -dirY
                        val perpY = dirX

                        // ------------------------------------------------
                        // Jelly deformation amount.
                        // ------------------------------------------------

                        // Strong leading-edge extension.
                        val leadingStretch =
                            0.52f *
                                normalizedDrag

                        // Smaller rear contraction.
                        val trailingSquish =
                            0.24f *
                                normalizedDrag

                        // Soft sideways compression.
                        val sideSquish =
                            0.15f *
                                normalizedDrag

                        // Asymmetric sideways shear.
                        val shear =
                            0.075f *
                                normalizedDrag

                        // Extra release deformation.
                        val releaseSquish =
                            state.returnSquish.current

                        val releaseWobble =
                            state.returnWobble.current

                        // ------------------------------------------------
                        // Draw custom jelly path.
                        //
                        // 48 samples makes the outline smooth enough
                        // to look liquid instead of polygonal.
                        // ------------------------------------------------
                        val jellyPath = Path()

                        val segments = 48

                        for (i in 0 until segments) {
                            val theta =
                                (2.0 * PI * i / segments)
                                    .toFloat()

                            val ux = cos(theta)
                            val uy = sin(theta)

                            /**
                             * Projection of this surface direction onto
                             * the current finger direction.
                             *
                             * +1  = leading edge
                             * -1  = trailing edge
                             *  0  = side
                             */
                            val longitudinal =
                                ux * dirX +
                                uy * dirY

                            val longitudinalPositive =
                                max(
                                    longitudinal,
                                    0f
                                )

                            val longitudinalNegative =
                                max(
                                    -longitudinal,
                                    0f
                                )

                            /**
                             * Perpendicular component.
                             *
                             * This creates the side squeeze while keeping
                             * deformation directional.
                             */
                            val lateral =
                                ux * perpX +
                                uy * perpY

                            // Main forward stretch.
                            var radialScale =
                                1f +
                                    leadingStretch *
                                    longitudinalPositive *
                                    longitudinalPositive

                            // Rear compression.
                            radialScale -=
                                trailingSquish *
                                longitudinalNegative *
                                longitudinalNegative

                            // Side compression.
                            radialScale -=
                                sideSquish *
                                (1f - abs(longitudinal))

                            /**
                             * Extra compression during return.
                             * This is deliberately strongest around the
                             * sides, creating a "puff -> squeeze -> settle"
                             * jelly response.
                             */
                            radialScale -=
                                releaseSquish *
                                (
                                    0.65f +
                                        0.35f *
                                        (1f - abs(longitudinal))
                                )

                            /**
                             * Directional wobble.
                             * This subtly shifts the side walls so the
                             * rebound isn't perfectly symmetrical.
                             */
                            radialScale +=
                                releaseWobble *
                                lateral *
                                (0.65f + 0.35f * longitudinal)

                            radialScale =
                                radialScale.coerceIn(
                                    0.58f,
                                    1.70f
                                )

                            /**
                             * Tangential shear.
                             *
                             * This is the part that makes the deformation
                             * asymmetric rather than a boring ellipse.
                             */
                            val tangentOffset =
                                shear *
                                radius *
                                longitudinalPositive *
                                lateral *
                                0.9f

                            val finalUx =
                                ux * radius * radialScale

                            val finalUy =
                                uy * radius * radialScale

                            val px =
                                finalUx +
                                    perpX * tangentOffset

                            val py =
                                finalUy +
                                    perpY * tangentOffset

                            val x = px
                            val y = py

                            if (i == 0) {
                                jellyPath.moveTo(x, y)
                            } else {
                                jellyPath.lineTo(x, y)
                            }
                        }

                        jellyPath.close()

                        // ------------------------------------------------
                        // Very mild overall depth compression.
                        //
                        // Keep center pinned. This does not move the View.
                        // ------------------------------------------------
                        val baseScaleX =
                            state.scaleX.current.coerceIn(
                                0.80f,
                                1.05f
                            )

                        val baseScaleY =
                            state.scaleY.current.coerceIn(
                                0.80f,
                                1.05f
                            )

                        // Stronger release squish but slower settling.
                        val releaseScaleX =
                            1f -
                                releaseSquish *
                                0.34f

                        val releaseScaleY =
                            1f +
                                releaseSquish *
                                0.18f

                        canvas.save()

                        // ------------------------------------------------
                        // PINNED COORDINATE.
                        // Do not replace this with translation of the View.
                        // ------------------------------------------------
                        canvas.translate(cx, cy)

                        canvas.scale(
                            baseScaleX * releaseScaleX,
                            baseScaleY * releaseScaleY
                        )

                        // Soft white body.
                        val baseAlpha =
                            if (state.isPressed) {
                                64
                            } else {
                                30
                            }

                        circlePaint.color = Color.WHITE

                        circlePaint.alpha =
                            (
                                baseAlpha *
                                    state.alpha.current.coerceIn(
                                        0f,
                                        1f
                                    )
                            ).toInt()

                        canvas.drawPath(
                            jellyPath,
                            circlePaint
                        )

                        // ------------------------------------------------
                        // Text
                        //
                        // Give text only the overall press deformation,
                        // while the jelly body gets the full nonlinear
                        // deformation.
                        // ------------------------------------------------
                        textPaint.textSize =
                            34f *
                                view.resources.displayMetrics.scaledDensity

                        textPaint.alpha =
                            (
                                255 *
                                    state.alpha.current.coerceIn(
                                        0f,
                                        1f
                                    )
                            ).toInt()

                        val textOffset =
                            (
                                textPaint.descent() +
                                    textPaint.ascent()
                            ) / 2f

                        canvas.save()

                        canvas.scale(
                            baseScaleX,
                            baseScaleY
                        )

                        canvas.drawText(
                            state.digit,
                            0f,
                            -textOffset,
                            textPaint
                        )

                        canvas.restore()
                        canvas.restore()

                        return null
                    }
                }
            )

        } catch (e: Throwable) {
            Log.e(
                TAG,
                "Failed to hook Keypad",
                e
            )
        }
    }
}
