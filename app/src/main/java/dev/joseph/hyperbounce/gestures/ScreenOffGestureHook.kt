package dev.joseph.hyperbounce.gestures

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import io.github.libxposed.api.XposedModule
import java.io.File
import java.lang.reflect.Method

/**
 * Screen-Off Gestures Hook for PhoneWindowManager (system_server).
 * Intercepts low-power hardware gesture key events reported by the FocalTech TDDI
 * controller (/dev/input/event7) when the screen is off and triggers actions
 * with zero delay:
 *
 * - Draw 'O' (KEYCODE_O) -> Toggle Flashlight
 * - Draw 'C' (KEYCODE_C) -> Launch Camera instantly
 * - Draw 'V' (KEYCODE_V) -> Toggle Flashlight (alternative)
 * - Draw 'M' (KEYCODE_M) -> Play / Pause Music
 * - Draw 'W' (KEYCODE_W) -> Next Music Track
 * - Swipe Left / Right   -> Previous / Next Track
 * - Swipe Up / Down      -> Volume Up / Down
 */
object ScreenOffGestureHook {
    private const val TAG = "HyperBounce_Gesture"

    // WindowManagerPolicy.FLAG_INTERACTIVE = 0x20000000
    private const val FLAG_INTERACTIVE = 0x20000000

    @Volatile private var isHooked = false
    @Volatile private var cameraManager: CameraManager? = null
    @Volatile private var rearCameraId: String? = null
    @Volatile private var isTorchOn: Boolean = false
    @Volatile private var vibrator: Vibrator? = null
    @Volatile private var powerManager: PowerManager? = null
    @Volatile private var audioManager: AudioManager? = null
    @Volatile private var appContext: Context? = null
    @Volatile private var mainHandler: Handler? = null

    fun init(module: XposedModule, classLoader: ClassLoader) {
        if (isHooked) return
        try {
            Log.i(TAG, "Initializing ScreenOffGestureHook for PhoneWindowManager...")
            val pwmClass = Class.forName("com.android.server.policy.PhoneWindowManager", false, classLoader)

            var interceptMethod: Method? = null
            var searchClass: Class<*>? = pwmClass
            while (searchClass != null && searchClass != Any::class.java) {
                for (m in searchClass.declaredMethods) {
                    if (m.name == "interceptKeyBeforeQueueing" &&
                        m.parameterTypes.size == 2 &&
                        m.parameterTypes[0] == KeyEvent::class.java &&
                        (m.parameterTypes[1] == Int::class.javaPrimitiveType || m.parameterTypes[1] == Integer::class.java)
                    ) {
                        m.isAccessible = true
                        interceptMethod = m
                        break
                    }
                }
                if (interceptMethod != null) break
                searchClass = searchClass.superclass
            }

            if (interceptMethod == null) {
                Log.e(TAG, "interceptKeyBeforeQueueing method not found in PhoneWindowManager or superclasses")
                return
            }

            module.hook(interceptMethod).intercept { chain ->
                val event = chain.args[0] as? KeyEvent
                val policyFlags = (chain.args[1] as? Number)?.toInt() ?: 0

                if (event != null && shouldHandleGesture(chain.thisObject, event, policyFlags)) {
                    val consumed = handleGestureEvent(event)
                    if (consumed) {
                        return@intercept 0 // Consume the event, preventing normal dispatch / wakeups
                    }
                }

                chain.proceed()
            }

            isHooked = true
            Log.i(TAG, "ScreenOffGestureHook successfully installed into PhoneWindowManager!")

            enableHardwareGestureNode()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook PhoneWindowManager in ScreenOffGestureHook", t)
        }
    }

    private fun ensureServices(pwmInstance: Any?) {
        if (appContext != null && cameraManager != null && powerManager != null) return
        if (pwmInstance == null) return
        try {
            var searchClass: Class<*>? = pwmInstance.javaClass
            var context: Context? = null
            while (searchClass != null && searchClass != Any::class.java) {
                try {
                    val ctxField = searchClass.getDeclaredField("mContext").apply { isAccessible = true }
                    context = ctxField.get(pwmInstance) as? Context
                    if (context != null) break
                } catch (_: NoSuchFieldException) {
                    searchClass = searchClass.superclass
                }
            }
            if (context == null) {
                Log.w(TAG, "Could not find mContext in PhoneWindowManager class hierarchy")
                return
            }
            appContext = context
            mainHandler = Handler(Looper.getMainLooper())
            powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            initCamera(context)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to ensure services from PhoneWindowManager", t)
        }
    }

    private fun initCamera(context: Context) {
        if (cameraManager != null) return
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            cameraManager = cm

            cm.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == rearCameraId || rearCameraId == null) {
                        isTorchOn = enabled
                        Log.d(TAG, "Torch state updated: enabled=$enabled for camera=$cameraId")
                    }
                }
            }, Handler(Looper.getMainLooper()))

            // Detect rear-facing camera for flashlight
            for (id in cm.cameraIdList) {
                try {
                    val chars = cm.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                        rearCameraId = id
                        break
                    }
                } catch (_: Throwable) {}
            }
            if (rearCameraId == null && cm.cameraIdList.isNotEmpty()) {
                rearCameraId = cm.cameraIdList[0]
            }
            Log.i(TAG, "CameraManager initialized with rearCameraId=$rearCameraId")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize CameraManager for torch", t)
            if (rearCameraId == null) rearCameraId = "0"
        }
    }

    private fun shouldHandleGesture(pwmInstance: Any?, event: KeyEvent, policyFlags: Int): Boolean {
        if (!ScreenOffGestureConfigHolder.enabled) return false

        ensureServices(pwmInstance)

        // Zero-latency check: Screen MUST NOT be interactive (screen off)
        val isInteractive = (policyFlags and FLAG_INTERACTIVE) != 0 || (powerManager?.isInteractive == true)
        if (isInteractive) return false

        // Check if keycode is one of our supported hardware gestures
        val keyCode = event.keyCode
        return isGestureKeyCode(keyCode)
    }

    private fun isGestureKeyCode(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_O ||
               keyCode == KeyEvent.KEYCODE_C ||
               keyCode == KeyEvent.KEYCODE_V ||
               keyCode == KeyEvent.KEYCODE_M ||
               keyCode == KeyEvent.KEYCODE_W ||
               keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
               keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
               keyCode == KeyEvent.KEYCODE_DPAD_UP ||
               keyCode == KeyEvent.KEYCODE_DPAD_DOWN
    }

    private fun handleGestureEvent(event: KeyEvent): Boolean {
        // Only trigger action on ACTION_DOWN to avoid double execution on ACTION_UP
        if (event.action != KeyEvent.ACTION_DOWN) {
            return true // Consume UP event cleanly
        }

        val context = appContext ?: return false
        val keyCode = event.keyCode
        Log.i(TAG, "Processing Screen-Off Gesture: keyCode=$keyCode (${KeyEvent.keyCodeToString(keyCode)})")

        return when (keyCode) {
            // Draw 'O' -> Flashlight Toggle (User specific priority)
            KeyEvent.KEYCODE_O -> {
                if (ScreenOffGestureConfigHolder.gestureOEnabled) {
                    toggleFlashlight(context)
                    true
                } else false
            }

            // Draw 'C' -> Launch Camera instantly (User specific priority)
            KeyEvent.KEYCODE_C -> {
                if (ScreenOffGestureConfigHolder.gestureCEnabled) {
                    launchCamera(context)
                    true
                } else false
            }

            // Draw 'V' -> Flashlight Toggle (Alternative gesture)
            KeyEvent.KEYCODE_V -> {
                if (ScreenOffGestureConfigHolder.gestureVEnabled) {
                    toggleFlashlight(context)
                    true
                } else false
            }

            // Draw 'M' -> Play / Pause Music
            KeyEvent.KEYCODE_M -> {
                if (ScreenOffGestureConfigHolder.gestureMEnabled) {
                    dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    true
                } else false
            }

            // Draw 'W' -> Next Track
            KeyEvent.KEYCODE_W -> {
                if (ScreenOffGestureConfigHolder.gestureWEnabled) {
                    dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                    true
                } else false
            }

            // Swipe Left -> Previous Track
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (ScreenOffGestureConfigHolder.gestureSwipesEnabled) {
                    dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                    true
                } else false
            }

            // Swipe Right -> Next Track
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (ScreenOffGestureConfigHolder.gestureSwipesEnabled) {
                    dispatchMediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
                    true
                } else false
            }

            // Swipe Up -> Volume Raise
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (ScreenOffGestureConfigHolder.gestureSwipesEnabled) {
                    adjustVolume(context, AudioManager.ADJUST_RAISE)
                    true
                } else false
            }

            // Swipe Down -> Volume Lower
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (ScreenOffGestureConfigHolder.gestureSwipesEnabled) {
                    adjustVolume(context, AudioManager.ADJUST_LOWER)
                    true
                } else false
            }

            else -> false
        }
    }

    private fun toggleFlashlight(context: Context) {
        try {
            val cm = cameraManager ?: (context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager)
            val camId = rearCameraId ?: "0"
            val targetState = !isTorchOn
            cm?.setTorchMode(camId, targetState)
            isTorchOn = targetState
            vibrateHaptic(context, if (targetState) 40L else 20L)
            Log.i(TAG, "Flashlight toggled to: $targetState (camId=$camId)")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to toggle flashlight", t)
        }
    }

    private fun launchCamera(context: Context) {
        try {
            vibrateHaptic(context, 45L)

            // Wake screen with gesture reason so lockscreen / display turns on immediately
            try {
                val wakeUpMethod = powerManager?.javaClass?.getMethod(
                    "wakeUp",
                    Long::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java
                )
                wakeUpMethod?.invoke(powerManager, SystemClock.uptimeMillis(), 4 /* WAKE_REASON_GESTURE */, "HyperBounce:ScreenOffGesture")
            } catch (_: Throwable) {
                @Suppress("DEPRECATION")
                val wl = powerManager?.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "HyperBounce:GestureWake"
                )
                wl?.acquire(3000)
                wl?.release()
            }

            // Launch secure camera above keyguard with zero delay
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
            }
            context.startActivity(intent)
            Log.i(TAG, "Camera activity launched successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch secure camera, attempting standard camera", t)
            try {
                val fallback = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (t2: Throwable) {
                Log.e(TAG, "Failed to launch fallback camera", t2)
            }
        }
    }

    private fun dispatchMediaKey(context: Context, keyCode: Int) {
        try {
            vibrateHaptic(context, 25L)
            val am = audioManager ?: (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            am?.dispatchMediaKeyEvent(down)
            am?.dispatchMediaKeyEvent(up)
            Log.i(TAG, "Dispatched media key $keyCode")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to dispatch media key $keyCode", t)
        }
    }

    private fun adjustVolume(context: Context, direction: Int) {
        try {
            vibrateHaptic(context, 15L)
            val am = audioManager ?: (context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
            am?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            Log.i(TAG, "Adjusted music stream volume: direction=$direction")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to adjust volume", t)
        }
    }

    private fun vibrateHaptic(context: Context, durationMs: Long) {
        if (!ScreenOffGestureConfigHolder.hapticFeedback) return
        try {
            val vib = vibrator ?: (context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)?.also { vibrator = it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib?.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                vib?.vibrate(durationMs)
            }
        } catch (_: Throwable) {}
    }

    private fun enableHardwareGestureNode() {
        Thread {
            try {
                val node = File("/sys/devices/platform/soc/a8c000.spi/spi_master/spi1/spi1.0/fts_gesture_mode")
                if (node.exists() && node.canWrite()) {
                    node.writeText("1")
                    Log.i(TAG, "fts_gesture_mode set to 1 in sysfs")
                }
            } catch (_: Throwable) {}
        }.start()
    }
}
