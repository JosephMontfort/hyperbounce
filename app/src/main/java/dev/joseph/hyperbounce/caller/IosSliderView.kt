package dev.joseph.hyperbounce.caller

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.max
import kotlin.math.min

enum class SliderType {
    ANSWER,
    DECLINE
}

/**
 * Authentic iOS-style capsule slider view.
 * Features:
 * 1. Frosted pill-shaped slider track with subtle glass border.
 * 2. Animated metallic text shimmer ("slide to answer" / "slide to decline").
 * 3. Fluid drag physics with spring snapback on release.
 * 4. On answer completion: Morphing animation that rotates the phone glyph 135° and
 *    transitions the knob color from iOS Green (#34C759) to iOS Red (#FF3B30).
 */
class IosSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var sliderType: SliderType = SliderType.ANSWER
        set(value) {
            field = value
            updateColors()
            postInvalidate()
        }

    var sliderLabel: String = "slide to answer"
        set(value) {
            field = value
            postInvalidate()
        }

    var onCompleted: (() -> Unit)? = null

    // Colors
    private val colorGreen = Color.parseColor("#34C759")
    private val colorRed = Color.parseColor("#FF3B30")
    private var currentKnobColor: Int = colorGreen

    // Track Styling
    private val trackFillColor = Color.argb(40, 255, 255, 255)
    private val trackStrokeColor = Color.argb(60, 255, 255, 255)

    // Paints
    private val trackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = trackFillColor
    }

    private val trackStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = trackStrokeColor
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val knobShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(60, 0, 0, 0)
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    // Geometry
    private val trackRect = RectF()
    private val density = context.resources.displayMetrics.density
    private val knobPadding = 4f * density
    private var knobX = 0f
    private var maxTravel = 0f
    private var knobRadius = 0f

    // Touch tracking
    private var initialTouchX = 0f
    private var initialKnobX = 0f
    private var isDragging = false
    private var isCompleted = false

    // Morph transition state for Answer slider
    private var morphProgress = 0f
    private var phoneRotation = 0f
    private val argbEvaluator = ArgbEvaluator()

    // Shimmer effect
    private var shimmerOffset = 0f
    private var shimmerAnimator: ValueAnimator? = null
    private var snapbackAnimator: ValueAnimator? = null
    private var morphAnimator: ValueAnimator? = null

    // Phone handset vector path (standard 24x24 scale)
    private val phonePath = Path().apply {
        moveTo(6.62f, 10.79f)
        cubicTo(8.06f, 13.62f, 10.38f, 15.94f, 13.21f, 17.38f)
        lineTo(15.41f, 15.18f)
        cubicTo(15.68f, 14.91f, 16.08f, 14.82f, 16.43f, 14.94f)
        cubicTo(17.55f, 15.31f, 18.76f, 15.51f, 20f, 15.51f)
        cubicTo(20.55f, 15.51f, 21f, 15.96f, 21f, 16.51f)
        lineTo(21f, 20f)
        cubicTo(21f, 20.55f, 20.55f, 21f, 20f, 21f)
        cubicTo(10.61f, 21f, 3f, 13.39f, 3f, 4f)
        cubicTo(3f, 3.45f, 3.45f, 3f, 4f, 3f)
        lineTo(7.5f, 3f)
        cubicTo(8.05f, 3f, 8.5f, 3.45f, 8.5f, 4f)
        cubicTo(8.5f, 5.25f, 8.7f, 6.45f, 9.07f, 7.57f)
        cubicTo(9.18f, 7.92f, 9.1f, 8.31f, 8.82f, 8.59f)
        close()
    }

    init {
        updateColors()
        textPaint.textSize = 16f * density
        trackStrokePaint.strokeWidth = 1.5f * density
    }

    private fun updateColors() {
        if (sliderType == SliderType.ANSWER) {
            currentKnobColor = colorGreen
            phoneRotation = 0f
        } else {
            currentKnobColor = colorRed
            phoneRotation = 135f // Default decline orientation (handset down)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startShimmer()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shimmerAnimator?.cancel()
        snapbackAnimator?.cancel()
        morphAnimator?.cancel()
    }

    private fun startShimmer() {
        if (shimmerAnimator?.isRunning == true) return
        shimmerAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                val w = width.toFloat()
                if (w > 0) {
                    shimmerOffset = (w * 2f) * f - (w * 0.5f)
                    postInvalidateOnAnimation()
                }
            }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = (56 * density).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        trackRect.set(
            trackStrokePaint.strokeWidth,
            trackStrokePaint.strokeWidth,
            w.toFloat() - trackStrokePaint.strokeWidth,
            h.toFloat() - trackStrokePaint.strokeWidth
        )
        knobRadius = (h.toFloat() - (knobPadding * 2f)) / 2f
        maxTravel = w.toFloat() - (knobRadius * 2f) - (knobPadding * 2f)
        if (maxTravel < 0f) maxTravel = 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val cornerRadius = h / 2f
        val alphaMultiplier = 1f - morphProgress

        // 1. Draw Pill Track Background & Border
        if (alphaMultiplier > 0.01f) {
            trackFillPaint.alpha = (40 * alphaMultiplier).toInt()
            trackStrokePaint.alpha = (60 * alphaMultiplier).toInt()
            canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackFillPaint)
            canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackStrokePaint)

            // 2. Draw Shimmer Text
            drawShimmerText(canvas, w, h, alphaMultiplier)
        }

        // 3. Draw Slider Knob
        val knobCenterX = knobPadding + knobRadius + knobX
        val knobCenterY = h / 2f

        // Knob Shadow
        canvas.drawCircle(knobCenterX, knobCenterY + 2f * density, knobRadius, knobShadowPaint)

        // Knob Background
        knobPaint.color = currentKnobColor
        canvas.drawCircle(knobCenterX, knobCenterY, knobRadius, knobPaint)

        // 4. Draw Phone Handset Icon
        drawPhoneIcon(canvas, knobCenterX, knobCenterY)
    }

    private fun drawShimmerText(canvas: Canvas, w: Float, h: Float, alphaMultiplier: Float) {
        val textY = (h / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
        val shimmerWidth = 140f * density

        val shader = LinearGradient(
            shimmerOffset, 0f,
            shimmerOffset + shimmerWidth, 0f,
            intArrayOf(
                Color.argb((120 * alphaMultiplier).toInt(), 255, 255, 255),
                Color.argb((255 * alphaMultiplier).toInt(), 255, 255, 255),
                Color.argb((120 * alphaMultiplier).toInt(), 255, 255, 255)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        textPaint.shader = shader

        // Slight text clipping if knob overlaps text
        val textX = w / 2f
        canvas.drawText(sliderLabel, textX, textY, textPaint)
    }

    private fun drawPhoneIcon(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(phoneRotation)

        // Handset is drawn in 24x24 bounds; normalize center to (12, 12)
        val iconSize = knobRadius * 1.05f
        val scale = iconSize / 24f
        canvas.scale(scale, scale)
        canvas.translate(-12f, -12f)

        canvas.drawPath(phonePath, iconPaint)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isCompleted) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val knobCenterX = knobPadding + knobRadius + knobX
                val touchRadius = knobRadius * 1.5f
                if (Math.hypot((event.x - knobCenterX).toDouble(), (event.y - (height / 2f)).toDouble()) <= touchRadius) {
                    snapbackAnimator?.cancel()
                    isDragging = true
                    initialTouchX = event.rawX
                    initialKnobX = knobX
                    performHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) return false
                val dx = event.rawX - initialTouchX
                var newKnobX = initialKnobX + dx

                // Rubber-band resistance on negative drag
                if (newKnobX < 0f) {
                    newKnobX = 0f
                } else if (newKnobX > maxTravel) {
                    newKnobX = maxTravel + (newKnobX - maxTravel) * 0.2f
                }

                knobX = min(newKnobX, maxTravel)
                postInvalidateOnAnimation()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!isDragging) return false
                isDragging = false
                parent?.requestDisallowInterceptTouchEvent(false)

                val threshold = maxTravel * IosCallerConfigHolder.config.slideThreshold
                if (knobX >= threshold) {
                    triggerCompletion()
                } else {
                    springSnapback()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun springSnapback() {
        snapbackAnimator?.cancel()
        snapbackAnimator = ValueAnimator.ofFloat(knobX, 0f).apply {
            duration = 320
            interpolator = OvershootInterpolator(1.05f)
            addUpdateListener { anim ->
                knobX = anim.animatedValue as Float
                postInvalidateOnAnimation()
            }
            start()
        }
    }

    private fun triggerCompletion() {
        isCompleted = true
        knobX = maxTravel
        performHaptic(HapticFeedbackConstants.CONFIRM)

        if (sliderType == SliderType.ANSWER) {
            // iOS Morphing animation:
            // The green call attend knob rotates 135° and turns upside down with red color
            // to smoothly form the call end button while the track fades out!
            morphAnimator?.cancel()
            morphAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 280
                addUpdateListener { anim ->
                    morphProgress = anim.animatedValue as Float
                    // Interpolate color from Green to Red
                    currentKnobColor = argbEvaluator.evaluate(morphProgress, colorGreen, colorRed) as Int
                    // Rotate phone icon from 0° to 135°
                    phoneRotation = morphProgress * 135f
                    postInvalidateOnAnimation()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        onCompleted?.invoke()
                    }
                })
                start()
            }
        } else {
            // Decline slider completed
            onCompleted?.invoke()
        }
    }

    private fun performHaptic(constant: Int) {
        if (!IosCallerConfigHolder.config.hapticsEnabled) return
        try {
            if (!performHapticFeedback(constant)) {
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    v?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(20)
                }
            }
        } catch (_: Throwable) {}
    }
}
