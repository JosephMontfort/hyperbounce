package dev.joseph.hyperbounce.notification.lockscreen

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import androidx.core.graphics.PathParser
import dev.joseph.hyperbounce.notification.NotificationSheetHook

/**
 * Bottom Quick Affordance Widgets for Lock Screen & Notification Center:
 * - Left: Flashlight Torch toggle with real-time HyperOS hardware state reflection.
 * - Right: Camera quick launcher (explicit com.android.camera intent without app chooser).
 * - Authentic Apple SF Symbol glyphs (flashlight and camera) with EVEN_ODD fills.
 * - Liquid Glass circular lens styling (optical bevel, specular highlight, active state transition, blur disabled).
 * - Pop-in spring physics driven by shade expansion fraction (0.85 -> 1.0).
 */
class LockScreenBottomAreaView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "HyperBounce_LSBottom"

        // SF Symbol flashlight.off.fill
        private const val FLASHLIGHT_PATH =
            "M1.2,0 H11.8 A1.2,1.2 0 0 1 13,1.2 V4.6 H0 V1.2 A1.2,1.2 0 0 1 1.2,0 Z " +
            "M0,5.6 H13 L9.7,11.5 V31.5 A1.5,1.5 0 0 1 8.2,33 H4.8 A1.5,1.5 0 0 1 3.3,31.5 V11.5 Z " +
            "M6.5,13.6 A1.4,1.9 0 1 1 6.5,17.4 A1.4,1.9 0 1 1 6.5,13.6 Z " +
            "M6.5,14.8 A0.7,0.7 0 1 1 6.5,16.2 A0.7,0.7 0 1 1 6.5,14.8 Z"

        // SF Symbol camera.fill
        private const val CAMERA_PATH =
            "M4.6,5 H11.6 L13.4,1.6 A2,2 0 0 1 15.2,0.5 H21.8 A2,2 0 0 1 23.6,1.6 L25.4,5 H32.4 A4.6,4.6 0 0 1 37,9.6 " +
            "V22.4 A4.6,4.6 0 0 1 32.4,27 H4.6 A4.6,4.6 0 0 1 0,22.4 V9.6 A4.6,4.6 0 0 1 4.6,5 Z " +
            "M18.5,8.6 A7,7 0 1 1 18.5,22.6 A7,7 0 1 1 18.5,8.6 Z " +
            "M18.5,11 A4.6,4.6 0 1 1 18.5,20.2 A4.6,4.6 0 1 1 18.5,11 Z " +
            "M30.2,9.2 A1.3,1.3 0 1 1 30.2,11.8 A1.3,1.3 0 1 1 30.2,9.2 Z"
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
                setTorchActive(enabled)
            }
        }

        override fun onTorchModeUnavailable(camId: String) {
            if (camId == torchCameraId) {
                setTorchActive(false)
            }
        }
    }

    init {
        clipChildren = false
        clipToPadding = false

        initCamera()

        val density = resources.displayMetrics.density
        val btnSize = (52f * density).toInt()

        leftTorchButton = AffordanceButton(context, AffordanceType.TORCH).apply {
            layoutParams = LayoutParams(btnSize, btnSize).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.START
                leftMargin = (36f * density).toInt()
                bottomMargin = (52f * density).toInt()
            }
            onClickListener = {
                toggleTorch()
            }
        }

        rightCameraButton = AffordanceButton(context, AffordanceType.CAMERA).apply {
            layoutParams = LayoutParams(btnSize, btnSize).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                rightMargin = (36f * density).toInt()
                bottomMargin = (52f * density).toInt()
            }
            onClickListener = {
                launchCamera()
            }
        }

        addView(leftTorchButton)
        addView(rightCameraButton)

        syncTorchState()
    }

    private val bgExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    private fun initCamera() {
        try {
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cm = cameraManager ?: return
            bgExecutor.execute {
                try {
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
                    mainHandler.post {
                        try {
                            cm.registerTorchCallback(torchCallback, mainHandler)
                        } catch (_: Throwable) {}
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error resolving camera for torch", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize CameraManager for torch", t)
        }
    }

    fun syncTorchState() {
        try {
            val enabled = NotificationSheetHook.isFlashlightEnabled()
            setTorchActive(enabled)
        } catch (_: Throwable) {}
    }

    fun setTorchActive(active: Boolean) {
        isTorchOn = active
        leftTorchButton.isActive = active
        leftTorchButton.postInvalidate()
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

        if (visibility != View.VISIBLE) {
            syncTorchState()
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
        try {
            // First attempt via live HyperOS MiuiFlashlightControllerImpl
            val targetState = !isTorchOn
            val handled = NotificationSheetHook.toggleFlashlight(context)
            setTorchActive(handled)

            // Fallback via CameraManager if controller is absent
            if (NotificationSheetHook.miuiFlashlightController == null) {
                val cm = cameraManager
                val camId = torchCameraId
                if (cm != null && camId != null) {
                    cm.setTorchMode(camId, targetState)
                    setTorchActive(targetState)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to toggle torch mode", t)
        }
    }

    private fun launchCamera() {
        try {
            NotificationSheetHook.isLaunchingNotificationActivity = true
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                setPackage("com.android.camera")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)

            try {
                val closeIntent = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
                context.sendBroadcast(closeIntent)
            } catch (_: Throwable) {}
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch com.android.camera, falling back", t)
            try {
                val fallbackIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (_: Throwable) {}
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

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
        private val specularPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }

        private var baseIconPath: Path? = null
        private val transformedIconPath = Path()
        private val transformMatrix = Matrix()
        private val arcRect = RectF()

        init {
            isClickable = true
            isFocusable = false

            try {
                val pathString = when (type) {
                    AffordanceType.TORCH -> FLASHLIGHT_PATH
                    AffordanceType.CAMERA -> CAMERA_PATH
                }
                baseIconPath = PathParser.createPathFromPathData(pathString)?.apply {
                    fillType = Path.FillType.EVEN_ODD
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to parse SF Symbol path data", t)
            }
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

            // Circular liquid glass styling (enableBackdropBlur = false)
            if (isActive) {
                // Illuminated solid white active state with outer glow
                bgPaint.color = Color.argb(60, 255, 255, 255)
                canvas.drawCircle(cx, cy, radius, bgPaint)

                bgPaint.color = Color.WHITE
                canvas.drawCircle(cx, cy, radius - 1.5f * density, bgPaint)

                iconPaint.color = Color.BLACK
            } else {
                // Frosted Liquid Glass lens backing
                bgPaint.color = Color.argb(85, 28, 28, 32)
                canvas.drawCircle(cx, cy, radius, bgPaint)

                // Concentric inner bevel highlight
                rimPaint.color = Color.argb(45, 255, 255, 255)
                rimPaint.strokeWidth = 1.2f * density
                canvas.drawCircle(cx, cy, radius - 0.8f * density, rimPaint)

                // Top optical specular reflection arc (210 deg -> 330 deg)
                val rimInset = 1.2f * density
                arcRect.set(cx - radius + rimInset, cy - radius + rimInset, cx + radius - rimInset, cy + radius - rimInset)
                specularPaint.strokeWidth = 1.5f * density
                specularPaint.color = Color.argb(125, 255, 255, 255)
                canvas.drawArc(arcRect, 215f, 110f, false, specularPaint)

                // Bottom inner refraction shadow (35 deg -> 145 deg)
                specularPaint.strokeWidth = 1.2f * density
                specularPaint.color = Color.argb(55, 0, 0, 0)
                canvas.drawArc(arcRect, 35f, 110f, false, specularPaint)

                iconPaint.color = Color.WHITE
            }

            // Draw authentic SF Symbol vector glyph
            val iconPath = baseIconPath
            if (iconPath != null) {
                transformMatrix.reset()
                when (type) {
                    AffordanceType.TORCH -> {
                        // Flashlight viewport: 13 x 33, target height ~ 24dp
                        val targetH = 24f * density
                        val scale = targetH / 33f
                        val scaledW = 13f * scale
                        transformMatrix.postScale(scale, scale)
                        transformMatrix.postTranslate(cx - scaledW / 2f, cy - targetH / 2f)
                    }
                    AffordanceType.CAMERA -> {
                        // Camera viewport: 37 x 27, target width ~ 26dp
                        val targetW = 26f * density
                        val scale = targetW / 37f
                        val scaledH = 27f * scale
                        transformMatrix.postScale(scale, scale)
                        transformMatrix.postTranslate(cx - targetW / 2f, cy - scaledH / 2f)
                    }
                }
                transformedIconPath.set(iconPath)
                transformedIconPath.transform(transformMatrix)
                canvas.drawPath(transformedIconPath, iconPaint)
            }
        }
    }
}
