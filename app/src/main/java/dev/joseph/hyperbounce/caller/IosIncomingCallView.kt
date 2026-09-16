package dev.joseph.hyperbounce.caller

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Modern iOS-style full screen incoming call overlay container.
 * Refinements:
 * 1. Matching 68dp button dimensions identical to stock ongoing CallOvalImageButton.
 * 2. 76dp track height with 38dp pill radius.
 * 3. Smooth fade-out of Decline slider & Message button when Answer knob glides to center.
 */
class IosIncomingCallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val density = context.resources.displayMetrics.density

    val messageButtonLayout: LinearLayout
    val messageCircleButton: MessageGlassButton
    val messageLabel: TextView

    val declineSlider: IosSliderView
    val answerSlider: IosSliderView

    var onAnswer: (() -> Unit)? = null
        set(value) {
            field = value
            answerSlider.onCompleted = value
        }

    var onReject: (() -> Unit)? = null
        set(value) {
            field = value
            declineSlider.onCompleted = value
        }

    var onMessage: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM

        val config = IosCallerConfigHolder.config
        val sideMargin = (20 * density).toInt()
        val bottomPadding = (config.bottomOffset * density).toInt()
        val trackHeight = ((config.buttonSize + 8f) * density).toInt()
        setPadding(sideMargin, 0, sideMargin, bottomPadding)

        // 1. Message Quick Reply Action
        messageButtonLayout = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (20 * density).toInt()
            }
        }

        messageCircleButton = MessageGlassButton(context).apply {
            val size = (56 * density).toInt()
            layoutParams = LayoutParams(size, size)
            setOnClickListener {
                animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).withEndAction {
                    animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    onMessage?.invoke()
                }.start()
            }
        }
        messageButtonLayout.addView(messageCircleButton)

        messageLabel = TextView(context).apply {
            text = "Message"
            textSize = 12.5f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * density).toInt()
            }
        }
        messageButtonLayout.addView(messageLabel)
        addView(messageButtonLayout)

        // 2. Decline Slider (Top Slider)
        declineSlider = IosSliderView(context).apply {
            sliderType = SliderType.DECLINE
            sliderLabel = "slide to decline"
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                trackHeight
            ).apply {
                bottomMargin = (16 * density).toInt()
            }
        }
        addView(declineSlider)

        // 3. Answer Slider (Bottom Slider)
        answerSlider = IosSliderView(context).apply {
            sliderType = SliderType.ANSWER
            sliderLabel = "slide to answer"
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                trackHeight
            )
            onGlideStarted = {
                // Record exact knob screen center X and Y for seamless handover to CallButtonFragment
                val loc = IntArray(2)
                getLocationOnScreen(loc)
                IosCallerScreenHook.lastAnswerKnobScreenX = loc[0] + (width / 2f)
                IosCallerScreenHook.lastAnswerKnobScreenY = loc[1] + (height / 2f)
                declineSlider.animate().alpha(0f).setDuration(220).start()
                messageButtonLayout.animate().alpha(0f).setDuration(220).start()
            }
        }
        addView(answerSlider)
    }

    fun setMessageButtonVisible(visible: Boolean) {
        messageButtonLayout.visibility = if (visible) View.VISIBLE else View.GONE
    }

    /**
     * Custom circular frosted glass button with speech bubble icon.
     */
    class MessageGlassButton(context: Context) : View(context) {
        private val density = context.resources.displayMetrics.density
        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(45, 255, 255, 255)
        }
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            color = Color.argb(65, 255, 255, 255)
        }
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(180, 255, 255, 255)
        }

        // 24x24 Speech bubble path
        private val bubblePath = Path().apply {
            moveTo(20f, 2f)
            lineTo(4f, 2f)
            cubicTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
            lineTo(2f, 22f)
            lineTo(6f, 18f)
            lineTo(20f, 18f)
            cubicTo(21.1f, 18f, 22f, 17.1f, 22f, 16f)
            lineTo(22f, 4f)
            cubicTo(22f, 2.9f, 21.1f, 2f, 20f, 2f)
            close()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val r = (minOf(width, height) / 2f) - strokePaint.strokeWidth

            // 1. Draw circular frosted glass background and border
            canvas.drawCircle(cx, cy, r, bgPaint)
            canvas.drawCircle(cx, cy, r, strokePaint)

            // 2. Draw speech bubble icon
            canvas.save()
            canvas.translate(cx, cy)
            val iconScale = (r * 1.05f) / 24f
            canvas.scale(iconScale, iconScale)
            canvas.translate(-12f, -12f)
            canvas.drawPath(bubblePath, iconPaint)

            // 3. Draw 3 message dots
            canvas.drawCircle(8f, 10f, 1.2f, dotPaint)
            canvas.drawCircle(12f, 10f, 1.2f, dotPaint)
            canvas.drawCircle(16f, 10f, 1.2f, dotPaint)
            canvas.restore()
        }
    }
}
