package dev.joseph.hyperbounce.notification.lockscreen

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import dev.joseph.hyperbounce.notification.NotificationSheetHook

/**
 * Bottom Quick Affordance Widgets for Lock Screen & Notification Center:
 * - Left: Flashlight Torch toggle with real-time hardware state reflection.
 * - Right: Camera quick launcher.
 * - Pop-in spring physics driven by shade expansion fraction (0.85 -> 1.0).
 * - Tactile haptic feedback and dynamic frosted-glass circular pills.
 */
class LockScreenBottomAreaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "HyperBounce_LSBottom"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var cameraManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var isTorchOn: Boolean = false
    private var hasFiredHapticDetent = false

    private val leftTorchButton: AffordanceButton
    private val rightCameraButton: AffordanceButton

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(camId: String, enabled: Boolean) {
            if (camId == torchCameraId) {
                isTorchOn = enabled
                leftTorchButton.isActive = enabled
                leftTorchButton.invalidate()
            }
        }

        override fun onTorchModeUnavailable(camId: String) {
            if (camId == torchCameraId) {
                isTorchOn = false
                leftTorchButton.isActive = false
                leftTorchButton.invalidate()
            }
        }
    }

    init {
        clipChildren = false
        clipToPadding = false

        initCamera()

        val density = resources.displayMetrics.density
        val btnSize = (50f * density).toInt()

        leftTorchButton = AffordanceButton(context, AffordanceType.TORCH).apply {
            layoutParams = LayoutParams(btnSize, btnSize).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
                leftMargin = (32f * density).toInt()
                bottomMargin = (48f * density).toInt()
            }
            onClickListener = {
                toggleTorch()
            }
        }

        rightCameraButton = AffordanceButton(context, AffordanceType.CAMERA).apply {
            layoutParams = LayoutParams(btnSize, btnSize).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                rightMargin = (32f * density).toInt()
                bottomMargin = (48f * density).toInt()
            }
            onClickListener = {
                launchCamera()
            }
        }

        addView(leftTorchButton)
        addView(rightCameraButton)
    }

    private fun initCamera() {
        try {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cm = cameraManager ?: return
            for (id in cm.cameraIdList) {
                val chars = cm.getCameraCharacteristics(id)
                val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                val facing = chars.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    torchCameraId = id
                    break
                }
            }
            if (torchCameraId == null && cm.cameraIdList.isNotEmpty()) {
                torchCameraId = cm.cameraIdList[0]
            }
            cm.registerTorchCallback(torchCallback, mainHandler)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize CameraManager for torch", t)
        }
    }

    fun setExpansionFraction(fraction: Float) {
        if (fraction < 0.85f) {
            visibility = View.GONE
            alpha = 0f
            leftTorchButton.scaleX = 0f
            leftTorchButton.scaleY = 0f
            rightCameraButton.scaleX = 0f
            rightCameraButton.scaleY = 0f
            hasFiredHapticDetent = false
            return
        }

        visibility = View.VISIBLE
        val t = ((fraction - 0.85f) / 0.15f).coerceIn(0f, 1f)
        alpha = t

        // Spring overshoot easing: f(t) = 1 + (t-1)^3 + 0.4*(1-t)*(t-1)
        val p = t - 1.0f
        val springScale = (1.0f + p * p * (3.0f * p + 2.0f)).coerceIn(0f, 1.15f)

        leftTorchButton.scaleX = springScale
        leftTorchButton.scaleY = springScale
        rightCameraButton.scaleX = springScale
        rightCameraButton.scaleY = springScale

        if (fraction >= 0.98f && !hasFiredHapticDetent) {
            hasFiredHapticDetent = true
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    private fun toggleTorch() {
        val cm = cameraManager ?: return
        val camId = torchCameraId ?: return
        try {
            val targetState = !isTorchOn
            cm.setTorchMode(camId, targetState)
            isTorchOn = targetState
            leftTorchButton.isActive = targetState
            leftTorchButton.invalidate()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to toggle torch mode", t)
        }
    }

    private fun launchCamera() {
        try {
            NotificationSheetHook.isLaunchingNotificationActivity = true
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch camera intent", t)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        try {
            cameraManager?.unregisterTorchCallback(torchCallback)
        } catch (_: Throwable) {}
    }

    enum class AffordanceType {
        TORCH,
        CAMERA
    }

    class AffordanceButton(
        context: Context,
        private val type: AffordanceType
    ) : View(context) {

        var isActive: Boolean = false
        var onClickListener: (() -> Unit)? = null

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = 0x55FFFFFF.toInt()
        }
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL_AND_STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val iconPath = Path()
        private val tempRect = RectF()

        init {
            isClickable = true
            isFocusable = false
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    animate().scaleX(0.88f).scaleY(0.88f).setDuration(100).start()
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    animate().scaleX(1.0f).scaleY(1.0f).setDuration(220)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2.0f)).start()
                    performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    onClickListener?.invoke()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                    return true
                }
            }
            return super.onTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return

            val cx = w / 2f
            val cy = h / 2f
            val radius = cx.coerceAtMost(cy)
            val density = resources.displayMetrics.density

            // Circular frosted pill background
            if (isActive) {
                bgPaint.color = Color.WHITE
                canvas.drawCircle(cx, cy, radius, bgPaint)
                iconPaint.color = Color.BLACK
            } else {
                bgPaint.color = 0x55000000.toInt()
                canvas.drawCircle(cx, cy, radius, bgPaint)
                strokePaint.strokeWidth = 1.0f * density
                canvas.drawCircle(cx, cy, radius - 0.5f * density, strokePaint)
                iconPaint.color = Color.WHITE
            }

            iconPaint.strokeWidth = 2.0f * density

            if (type == AffordanceType.TORCH) {
                drawTorchIcon(canvas, cx, cy, density)
            } else {
                drawCameraIcon(canvas, cx, cy, density)
            }
        }

        private fun drawTorchIcon(canvas: Canvas, cx: Float, cy: Float, density: Float) {
            val s = density * 1.1f
            iconPath.reset()
            // Torch head
            iconPath.moveTo(cx - 7f * s, cy - 9f * s)
            iconPath.lineTo(cx + 7f * s, cy - 9f * s)
            iconPath.lineTo(cx + 4.5f * s, cy - 3f * s)
            iconPath.lineTo(cx - 4.5f * s, cy - 3f * s)
            iconPath.close()

            // Torch handle
            tempRect.set(cx - 3.5f * s, cy - 3f * s, cx + 3.5f * s, cy + 9f * s)
            iconPath.addRoundRect(tempRect, 2f * s, 2f * s, Path.Direction.CW)

            canvas.drawPath(iconPath, iconPaint)

            // Switch pip
            iconPaint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy + 1f * s, 1.6f * s, if (isActive) bgPaint else strokePaint)
            iconPaint.style = Paint.Style.FILL_AND_STROKE
        }

        private fun drawCameraIcon(canvas: Canvas, cx: Float, cy: Float, density: Float) {
            val s = density * 1.1f
            iconPath.reset()

            // Camera body
            tempRect.set(cx - 9f * s, cy - 5.5f * s, cx + 9f * s, cy + 7f * s)
            iconPath.addRoundRect(tempRect, 2.5f * s, 2.5f * s, Path.Direction.CW)

            // Camera top notch
            tempRect.set(cx - 4f * s, cy - 8f * s, cx + 4f * s, cy - 5.5f * s)
            iconPath.addRoundRect(tempRect, 1.5f * s, 1.5f * s, Path.Direction.CW)

            canvas.drawPath(iconPath, iconPaint)

            // Lens circle
            val oldStyle = iconPaint.style
            iconPaint.style = Paint.Style.STROKE
            canvas.drawCircle(cx, cy + 0.8f * s, 3.8f * s, if (isActive) bgPaint else iconPaint)
            iconPaint.style = oldStyle
        }
    }
}
