package dev.joseph.hyperbounce.controlcenter

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import io.github.libxposed.api.XposedModule
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Flashlight Brightness Control for low-end Xiaomi / HyperOS devices.
 * Replaces unavailable driver-level Camera2 strength APIs with direct Qualcomm PMIC sysfs latching,
 * unlocks the 1-100% brightness slider in the expanded panel, and unifies CC quick-tile toggling.
 */
object FlashlightBrightnessHook {
    private const val TAG = "HyperBounce_Flashlight"

    @Volatile private var isTorchOn: Boolean = false
    @Volatile private var lastWriteTime: Long = 0L
    @Volatile private var bonusZoneWarned: Boolean = false

    private val initializedLoaders = Collections.newSetFromMap(ConcurrentHashMap<ClassLoader, Boolean>())

    fun init(module: XposedModule, classLoader: ClassLoader) {
        if (initializedLoaders.contains(classLoader)) return
        initializedLoaders.add(classLoader)

        Log.i(TAG, "Initializing Low-End Flashlight Brightness Hooks on ClassLoader: $classLoader")

        hookMiFlashlightManager(module, classLoader)
        hookMiFlashlightOnSystemUiReceiver(module, classLoader)
        hookMiFlashlightController(module, classLoader)
        hookToggleChangeReceiver(module, classLoader)
    }

    /**
     * Part 1: Unlock the slider in MiFlashlightManager and enforce maxStrength = 100 and default 75% logic strength.
     */
    private fun hookMiFlashlightManager(module: XposedModule, classLoader: ClassLoader) {
        try {
            val managerClass = Class.forName("miui.systemui.flashlight.MiFlashlightManager", false, classLoader)

            // Force maxStrength = 100 in constructors
            for (c in managerClass.declaredConstructors) {
                c.isAccessible = true
                module.hook(c).intercept { chain ->
                    val res = chain.proceed()
                    if (ControlCenterConfigHolder.config.lowEndFlashlightEnabled) {
                        try {
                            val maxField = managerClass.getDeclaredField("maxStrength")
                            maxField.isAccessible = true
                            maxField.setInt(chain.thisObject, 100)
                        } catch (_: Throwable) {}
                    }
                    res
                }
            }

            // Hook isSupportStrengthLevel to return true and set maxStrength = 100
            for (m in managerClass.declaredMethods) {
                if (m.name == "isSupportStrengthLevel") {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        if (!ControlCenterConfigHolder.config.lowEndFlashlightEnabled) {
                            return@intercept chain.proceed()
                        }
                        val thisObj = chain.thisObject
                        if (thisObj != null) {
                            try {
                                val maxField = managerClass.getDeclaredField("maxStrength")
                                maxField.isAccessible = true
                                maxField.setInt(thisObj, 100)
                            } catch (_: Throwable) {}
                        }
                        java.lang.Boolean.TRUE
                    }
                }
            }

            // Hook getLogicStrength to return 0.75f (Part 1b: slider opens at 75% on each session)
            for (m in managerClass.declaredMethods) {
                if (m.name == "getLogicStrength" && m.returnType == java.lang.Float.TYPE) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        if (!ControlCenterConfigHolder.config.lowEndFlashlightEnabled) {
                            return@intercept chain.proceed()
                        }
                        0.75f
                    }
                }
            }

            // Hook getCameraId to preserve maxStrength = 100 if reset by background characteristics lookup
            for (m in managerClass.declaredMethods) {
                if (m.name == "getCameraId") {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        val res = chain.proceed()
                        if (ControlCenterConfigHolder.config.lowEndFlashlightEnabled) {
                            val thisObj = chain.thisObject
                            if (thisObj != null) {
                                try {
                                    val maxField = managerClass.getDeclaredField("maxStrength")
                                    maxField.isAccessible = true
                                    maxField.setInt(thisObj, 100)
                                } catch (_: Throwable) {}
                            }
                        }
                        res
                    }
                }
            }

            // Hook asyncToggleFlashLight to guarantee maxStrength = 100 for calculation
            for (m in managerClass.declaredMethods) {
                if (m.name == "asyncToggleFlashLight") {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        if (ControlCenterConfigHolder.config.lowEndFlashlightEnabled) {
                            val thisObj = chain.thisObject
                            if (thisObj != null) {
                                try {
                                    val maxField = managerClass.getDeclaredField("maxStrength")
                                    maxField.isAccessible = true
                                    maxField.setInt(thisObj, 100)
                                } catch (_: Throwable) {}
                            }
                        }
                        chain.proceed()
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook MiFlashlightManager", t)
        }
    }

    /**
     * Parts 2 & 3: MiFlashlightOnSystemUiReceiver hardware backend and Companion.operateCamera timestamp extra.
     */
    private fun hookMiFlashlightOnSystemUiReceiver(module: XposedModule, classLoader: ClassLoader) {
        try {
            val receiverClass = Class.forName("miui.systemui.flashlight.MiFlashlightOnSystemUiReceiver", false, classLoader)

            // Part 3: Inject send timestamp in Companion.operateCamera
            try {
                val companionClass = Class.forName("miui.systemui.flashlight.MiFlashlightOnSystemUiReceiver\$Companion", false, classLoader)
                for (m in companionClass.declaredMethods) {
                    if (m.name == "operateCamera" && m.parameterTypes.size == 4) {
                        m.isAccessible = true
                        module.hook(m).intercept { chain ->
                            if (!ControlCenterConfigHolder.config.lowEndFlashlightEnabled) {
                                return@intercept chain.proceed()
                            }
                            val rawArgs = chain.args
                            val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                            val context = args?.getOrNull(0) as? Context
                            val cameraId = args?.getOrNull(1) as? String ?: "0"
                            val strength = (args?.getOrNull(2) as? Number)?.toInt() ?: -1
                            val isCanUseStrengthLevel = (args?.getOrNull(3) as? Boolean) ?: true

                            if (context != null) {
                                val intent = Intent("miui.systemui.action.ACTION_OPERATE_FLASHLIGHT").apply {
                                    putExtra("miui_flashlight_camera_id", cameraId)
                                    putExtra("miui_flashlight_strength", strength)
                                    putExtra("miui_flashlight_is_can_use_strength_level", isCanUseStrengthLevel)
                                    putExtra("miui_flashlight_send_time", System.currentTimeMillis())
                                    setPackage("com.android.systemui")
                                }
                                context.sendBroadcast(intent)
                                return@intercept null
                            }
                            chain.proceed()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to hook MiFlashlightOnSystemUiReceiver.Companion", t)
            }

            // Part 2: Intercept operateCamera(Context, Intent) to perform the PMIC sysfs latch sequence
            for (m in receiverClass.declaredMethods) {
                if (m.name == "operateCamera" && m.parameterTypes.size == 2) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        if (!ControlCenterConfigHolder.config.lowEndFlashlightEnabled) {
                            return@intercept chain.proceed()
                        }
                        val rawArgs = chain.args
                        val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                        val context = args?.getOrNull(0) as? Context ?: return@intercept null
                        val intent = args?.getOrNull(1) as? Intent ?: return@intercept null

                        handleHardwareOperateCamera(context, intent)
                        null
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook MiFlashlightOnSystemUiReceiver", t)
        }
    }

    /**
     * Hardware driver logic replicating the proven latching sequence.
     */
    private fun handleHardwareOperateCamera(context: Context, intent: Intent) {
        try {
            val sendTime = intent.getLongExtra("miui_flashlight_send_time", -1L)
            if (sendTime != -1L && (System.currentTimeMillis() - sendTime > 1500L)) {
                return
            }

            val cameraId = intent.getStringExtra("miui_flashlight_camera_id") ?: "0"
            val strength = intent.getIntExtra("miui_flashlight_strength", -1)

            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return

            if (strength == 0) {
                writeToFile("/sys/class/leds/led:switch_0/trigger", "none")
                writeToFile("/sys/class/leds/led:switch_0/brightness", "0")
                try {
                    cameraManager.setTorchMode(cameraId, false)
                } catch (t: Throwable) {
                    Log.w(TAG, "cameraManager.setTorchMode false failed", t)
                }
                isTorchOn = false
                return
            }

            if (strength in 1..100) {
                val now = System.currentTimeMillis()
                if (now - lastWriteTime < 40L) {
                    return
                }
                lastWriteTime = now

                if (!isHardwareOn()) {
                    try {
                        cameraManager.setTorchMode(cameraId, true)
                        isTorchOn = true
                    } catch (t: Throwable) {
                        Log.w(TAG, "cameraManager.setTorchMode true failed", t)
                    }
                }

                val sysfsBrightness = if (strength <= 75) {
                    strength * 2
                } else {
                    (strength - 75) * 4 + 150
                }
                val strVal = sysfsBrightness.toString()

                // Hardware PMIC latch sequence
                writeToFile("/sys/class/leds/led:switch_0/brightness", "0")
                writeToFile("/sys/class/leds/led:torch_0/trigger", "torch0_trigger")
                writeToFile("/sys/class/leds/led:torch_1/trigger", "torch1_trigger")
                writeToFile("/sys/class/leds/led:switch_0/trigger", "torch")
                writeToFile("/sys/class/leds/led:torch_0/brightness", strVal)
                writeToFile("/sys/class/leds/led:torch_1/brightness", strVal)
                writeToFile("/sys/class/leds/led:switch_0/brightness", "1")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in handleHardwareOperateCamera", t)
        }
    }

    /**
     * Parts 4 & 5: MiFlashlightController status handling, bonus zone Toast, and panel-open resync.
     */
    private fun hookMiFlashlightController(module: XposedModule, classLoader: ClassLoader) {
        try {
            val controllerClass = Class.forName("miui.systemui.flashlight.MiFlashlightController", false, classLoader)

            // Part 4: Hook setFlashlightStatus(float) to warn on bonus zone and skip persistent storage
            for (m in controllerClass.declaredMethods) {
                if (m.name == "setFlashlightStatus" && m.parameterTypes.size == 1 && m.parameterTypes[0] == java.lang.Float.TYPE) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        if (!ControlCenterConfigHolder.config.lowEndFlashlightEnabled) {
                            return@intercept chain.proceed()
                        }
                        val rawArgs = chain.args
                        val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                        val percentage = (args?.getOrNull(0) as? Number)?.toFloat() ?: 0f
                        val controller = chain.thisObject ?: return@intercept chain.proceed()

                        // Bonus zone warning
                        if (percentage > 0.75f) {
                            if (!bonusZoneWarned) {
                                bonusZoneWarned = true
                                try {
                                    val ctxField = controllerClass.getDeclaredField("context")
                                    ctxField.isAccessible = true
                                    val ctx = ctxField.get(controller) as? Context
                                    if (ctx != null) {
                                        Handler(Looper.getMainLooper()).post {
                                            Toast.makeText(ctx, "Warning: High brightness. Risk of overheating!", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (_: Throwable) {}
                            }
                        } else {
                            bonusZoneWarned = false
                        }

                        // Get switch status
                        val switchStatus = try {
                            val getSwitchStatusMethod = controllerClass.getDeclaredMethod("getSwitchStatus")
                            getSwitchStatusMethod.isAccessible = true
                            getSwitchStatusMethod.invoke(controller) as? Boolean
                        } catch (t: Throwable) {
                            null
                        }

                        // Get miFlashlightManager
                        val manager = try {
                            val mgrField = controllerClass.getDeclaredField("miFlashlightManager")
                            mgrField.isAccessible = true
                            mgrField.get(controller)
                        } catch (t: Throwable) {
                            null
                        }

                        if (manager != null) {
                            val mgrClass = manager.javaClass
                            if (switchStatus == true) {
                                if (percentage == 0f) {
                                    invokeAsyncOperate(mgrClass, manager, false)
                                } else {
                                    invokeAsyncToggle(mgrClass, manager, percentage)
                                }
                            } else if (switchStatus == false) {
                                if (percentage > 0f) {
                                    invokeAsyncOperate(mgrClass, manager, true)
                                }
                            }
                        }

                        // Do not proceed: bypasses saveLogicStrength
                        null
                    }
                }
            }

            // Part 5: Resync switch status from hardware whenever the flashlight UI view is set up
            for (m in controllerClass.declaredMethods) {
                if (m.name == "setSwitchLottieView") {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        val res = chain.proceed()
                        if (ControlCenterConfigHolder.config.lowEndFlashlightEnabled) {
                            val controller = chain.thisObject
                            if (controller != null) {
                                resyncSwitchStatus(controllerClass, controller)
                            }
                        }
                        res
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook MiFlashlightController", t)
        }
    }

    /**
     * Part 6: Hook ToggleChangeReceiver.onToggleChange to dispatch operateCamera with 75% on quick-tile tap.
     */
    private fun hookToggleChangeReceiver(module: XposedModule, classLoader: ClassLoader) {
        try {
            val receiverClass = Class.forName("miui.systemui.flashlight.ToggleChangeReceiver", false, classLoader)
            for (m in receiverClass.declaredMethods) {
                if (m.name == "onToggleChange" && m.parameterTypes.size == 2) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        val res = chain.proceed()
                        if (ControlCenterConfigHolder.config.lowEndFlashlightEnabled) {
                            val rawArgs = chain.args
                            val args = if (rawArgs is List<*>) rawArgs.toTypedArray() else rawArgs as? Array<*>
                            val context = args?.getOrNull(0) as? Context
                            val intent = args?.getOrNull(1) as? Intent
                            if (context != null && intent != null) {
                                val isOpen = intent.getBooleanExtra("flashlight_is_open_or_close", false)
                                val strength = if (isOpen) 75 else 0
                                try {
                                    val companionClass = Class.forName("miui.systemui.flashlight.MiFlashlightOnSystemUiReceiver\$Companion", false, classLoader)
                                    val companionField = Class.forName("miui.systemui.flashlight.MiFlashlightOnSystemUiReceiver", false, classLoader).getDeclaredField("Companion")
                                    companionField.isAccessible = true
                                    val companionObj = companionField.get(null)
                                    for (cm in companionClass.declaredMethods) {
                                        if (cm.name == "operateCamera" && cm.parameterTypes.size == 4) {
                                            cm.isAccessible = true
                                            cm.invoke(companionObj, context, "0", strength, true)
                                            break
                                        }
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Failed to dispatch operateCamera from ToggleChangeReceiver", t)
                                }
                            }
                        }
                        res
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to hook ToggleChangeReceiver", t)
        }
    }

    private fun invokeAsyncOperate(mgrClass: Class<*>, manager: Any, on: Boolean) {
        try {
            for (m in mgrClass.declaredMethods) {
                if (m.name == "asyncOperate" && m.parameterTypes.size == 2) {
                    m.isAccessible = true
                    m.invoke(manager, on, null)
                    return
                }
            }
            for (m in mgrClass.declaredMethods) {
                if (m.name == "asyncOperate\$default") {
                    m.isAccessible = true
                    m.invoke(null, manager, on, null, 2, null)
                    return
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to invoke asyncOperate", t)
        }
    }

    private fun invokeAsyncToggle(mgrClass: Class<*>, manager: Any, percentage: Float) {
        try {
            for (m in mgrClass.declaredMethods) {
                if (m.name == "asyncToggleFlashLight" && m.parameterTypes.size == 2) {
                    m.isAccessible = true
                    m.invoke(manager, percentage, null)
                    return
                }
            }
            for (m in mgrClass.declaredMethods) {
                if (m.name == "asyncToggleFlashLight\$default") {
                    m.isAccessible = true
                    m.invoke(null, manager, percentage, null, 2, null)
                    return
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to invoke asyncToggleFlashLight", t)
        }
    }

    private fun resyncSwitchStatus(controllerClass: Class<*>, controller: Any) {
        try {
            val hwOn = isHardwareOn()
            for (m in controllerClass.declaredMethods) {
                if (m.name == "setSwitchStatus" && m.parameterTypes.size == 1) {
                    m.isAccessible = true
                    m.invoke(controller, java.lang.Boolean.valueOf(hwOn))
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to resync switch status", t)
        }
    }

    fun isHardwareOn(): Boolean {
        return try {
            val file = File("/sys/class/leds/led:switch_0/brightness")
            if (!file.exists()) return false
            val line = file.bufferedReader().use { it.readLine() }
            line != null && line.trim() != "0"
        } catch (t: Throwable) {
            false
        }
    }

    private fun writeToFile(path: String, value: String) {
        try {
            val file = File(path)
            if (file.exists()) {
                file.outputStream().use { it.write(value.toByteArray()) }
            }
        } catch (_: Throwable) {
            // Quietly ignore IO/permission errors, matching MT Manager try-catch
        }
    }
}
