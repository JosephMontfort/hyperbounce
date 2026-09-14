package dev.joseph.hyperbounce.controlcenter

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import io.github.libxposed.api.XposedModule
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Flashlight Brightness Control for low-end Xiaomi / HyperOS devices.
 * Uses Qualcomm PMIC sysfs latch sequence to enable hardware brightness scaling,
 * unlocks the single continuous 0-100% brightness slider, and unifies CC quick-tile toggling.
 */
object FlashlightBrightnessHook {
    private const val TAG = "HyperBounce_Flashlight"
    const val NORMAL_BRIGHTNESS = 100
    private const val PREFS_NAME = "hyperbounce_flashlight"
    private const val PREF_KEY_OVERHEAT_WARNED = "cc_flashlight_overheat_dialog_shown"

    @Volatile private var isTorchOn: Boolean = false
    @Volatile private var lastWriteTime: Long = 0L
    @Volatile private var lastTurnOnAttemptTime: Long = 0L

    @Volatile private var hasShownOverheatDialog: Boolean = false
    @Volatile private var hasCheckedOverheatPref: Boolean = false
    @Volatile private var isDialogShowing: Boolean = false
    @Volatile private var lastSafePercentage: Float = 0.50f

    @Volatile private var cachedSwitchNode: String? = null
    @Volatile private var cachedTorch0Node: String? = null
    @Volatile private var cachedTorch1Node: String? = null
    @Volatile private var cachedHardwareMax: Int = 0

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
     * Part 1: Unlock the slider in MiFlashlightManager and enforce maxStrength = 100.
     * getLogicStrength returns the exact fractional slider position that corresponds to normal brightness (100).
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

            // Hook getLogicStrength to return the exact position of normal brightness (100) within the user's max limit
            for (m in managerClass.declaredMethods) {
                if (m.name == "getLogicStrength" && m.returnType == java.lang.Float.TYPE) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        if (!ControlCenterConfigHolder.config.lowEndFlashlightEnabled) {
                            return@intercept chain.proceed()
                        }
                        getLogicStrength()
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
     * Hardware driver logic executing the proven Qualcomm PMIC latch sequence.
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

            val switchNode = getSwitchBrightnessNode()
            val switchTriggerNode = getSwitchTriggerNode()
            val torch0Node = getTorch0BrightnessNode()
            val torch1Node = getTorch1BrightnessNode()
            val torch0Trigger = getTorch0TriggerNode()
            val torch1Trigger = getTorch1TriggerNode()

            if (strength == 0) {
                writeToFile(switchTriggerNode, "none")
                writeToFile(switchNode, "0")
                try {
                    cameraManager.setTorchMode(cameraId, false)
                } catch (t: Throwable) {
                    Log.w(TAG, "cameraManager.setTorchMode false failed", t)
                }
                isTorchOn = false
                lastTurnOnAttemptTime = 0L
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

                val targetBrightness = calculateSysfsBrightness(strength)
                val strVal = targetBrightness.toString()

                // Hardware PMIC latch sequence (exact proven sequence required by Qualcomm PMIC driver)
                writeToFile(switchNode, "0")
                writeToFile(torch0Trigger, "torch0_trigger")
                writeToFile(torch1Trigger, "torch1_trigger")
                writeToFile(switchTriggerNode, "torch")
                writeToFile(torch0Node, strVal)
                writeToFile(torch1Node, strVal)
                writeToFile(switchNode, "1")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in handleHardwareOperateCamera", t)
        }
    }

    /**
     * Parts 4 & 5: MiFlashlightController status handling and panel-open resync.
     */
    private fun hookMiFlashlightController(module: XposedModule, classLoader: ClassLoader) {
        try {
            val controllerClass = Class.forName("miui.systemui.flashlight.MiFlashlightController", false, classLoader)

            // Part 4: Hook setFlashlightStatus(float) following exact guide logic and skip persistent storage
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

                        if (isDialogShowing) {
                            return@intercept null
                        }

                        val switchStatus = getControllerSwitchStatus(controllerClass, controller)

                        val manager = try {
                            val mgrField = controllerClass.getDeclaredField("miFlashlightManager")
                            mgrField.isAccessible = true
                            mgrField.get(controller)
                        } catch (t: Throwable) {
                            null
                        }

                        val controllerContext = try {
                            val f = controllerClass.getDeclaredField("context")
                            f.isAccessible = true
                            f.get(controller) as? Context
                        } catch (_: Throwable) { null }

                        if (manager != null) {
                            val mgrClass = manager.javaClass
                            val userMax = getUserMaxBrightness()
                            val rawBrightness = percentage * userMax
                            val steppedBrightness = ((rawBrightness + 5f) / 10f).toInt() * 10
                            val targetBrightness = if (percentage <= 0f) 0 else steppedBrightness.coerceIn(10, userMax)
                            val quantizedPercentage = if (percentage <= 0f) 0f else (targetBrightness.toFloat() / userMax.toFloat()).coerceIn(0.01f, 1.0f)

                            if (switchStatus == true) {
                                if (percentage == 0f) {
                                    lastTurnOnAttemptTime = 0L
                                    invokeAsyncOperate(mgrClass, manager, false)
                                } else {
                                    if (targetBrightness <= 250) {
                                        lastSafePercentage = percentage
                                    } else {
                                        if (!hasCheckedOverheatPref && controllerContext != null) {
                                            hasCheckedOverheatPref = true
                                            hasShownOverheatDialog = controllerContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                                .getBoolean(PREF_KEY_OVERHEAT_WARNED, false)
                                        }

                                        if (!hasShownOverheatDialog) {
                                            isDialogShowing = true
                                            val pendingPercentage = percentage
                                            val safeFraction = lastSafePercentage

                                            // Snap slider and clamp hardware to safe fraction
                                            snapSliderToFraction(controllerClass, controller, safeFraction)
                                            invokeAsyncToggle(mgrClass, manager, safeFraction)

                                            showOverheatWarningDialog(
                                                controllerClass = controllerClass,
                                                controller = controller,
                                                mgrClass = mgrClass,
                                                manager = manager,
                                                context = controllerContext,
                                                safeFraction = safeFraction,
                                                pendingFraction = pendingPercentage
                                            )
                                            return@intercept null
                                        }
                                    }

                                    invokeAsyncToggle(mgrClass, manager, quantizedPercentage)
                                }
                            } else if (switchStatus == false) {
                                if (percentage > 0f) {
                                    val now = System.currentTimeMillis()
                                    if (now - lastTurnOnAttemptTime > 400L) {
                                        lastTurnOnAttemptTime = now
                                        invokeAsyncOperate(mgrClass, manager, true)
                                    }
                                }
                            }
                        }

                        // Do not proceed: bypasses saveLogicStrength
                        null
                    }
                }
            }

            // Part 5: Resync switch status from hardware whenever flashlight UI is configured
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
     * Part 6: Hook ToggleChangeReceiver.onToggleChange to dispatch operateCamera at normal brightness (100) on quick-tile tap.
     * Maps to exactly normal brightness (100) in sysfs, matching hardware torch state with zero jump or delay.
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
                                val strength = if (isOpen) {
                                    (getLogicStrength() * 100).roundToInt().coerceIn(1, 100)
                                } else {
                                    0
                                }
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

    /**
     * Calculates sysfs brightness:
     * Maps 1..100% slider linearly across 0 to userMax (e.g. 100..500).
     */
    fun calculateSysfsBrightness(strength: Int): Int {
        if (strength <= 0) return 0
        val userMax = getUserMaxBrightness()
        val fraction = strength / 100.0f
        val rawBrightness = fraction * userMax
        val stepped = ((rawBrightness + 5) / 10).toInt() * 10
        return stepped.coerceIn(10, userMax)
    }

    /**
     * Gets the slider position (0.01f..1.0f) that represents normal brightness (100) in the user's selected range.
     */
    fun getLogicStrength(): Float {
        val userMax = getUserMaxBrightness()
        return (NORMAL_BRIGHTNESS.toFloat() / userMax.toFloat()).coerceIn(0.01f, 1.0f)
    }

    fun getUserMaxBrightness(): Int {
        val hwMax = getHardwareMaxBrightness()
        return ControlCenterConfigHolder.config.flashlightMaxBrightness.coerceIn(NORMAL_BRIGHTNESS, hwMax)
    }

    fun getHardwareMaxBrightness(): Int {
        if (cachedHardwareMax > 0) return cachedHardwareMax
        try {
            val candidates = listOf(
                "/sys/class/leds/led:torch_0/max_brightness",
                "/sys/class/leds/torch_0/max_brightness"
            )
            for (path in candidates) {
                val file = File(path)
                if (file.exists()) {
                    val v = file.bufferedReader().use { it.readLine() }?.trim()?.toIntOrNull()
                    if (v != null && v > 0) {
                        cachedHardwareMax = v
                        return v
                    }
                }
            }
        } catch (_: Throwable) {}
        cachedHardwareMax = 500
        return 500
    }

    private fun getSwitchBrightnessNode(): String {
        cachedSwitchNode?.let { return it }
        val candidates = listOf(
            "/sys/class/leds/led:switch_0/brightness",
            "/sys/class/leds/led:switch_2/brightness",
            "/sys/class/leds/led:switch_1/brightness"
        )
        for (path in candidates) {
            if (File(path).exists()) {
                cachedSwitchNode = path
                return path
            }
        }
        return "/sys/class/leds/led:switch_0/brightness"
    }

    private fun getSwitchTriggerNode(): String {
        return getSwitchBrightnessNode().replace("brightness", "trigger")
    }

    private fun getTorch0BrightnessNode(): String {
        cachedTorch0Node?.let { return it }
        val candidates = listOf(
            "/sys/class/leds/led:torch_0/brightness",
            "/sys/class/leds/torch_0/brightness"
        )
        for (path in candidates) {
            if (File(path).exists()) {
                cachedTorch0Node = path
                return path
            }
        }
        return "/sys/class/leds/led:torch_0/brightness"
    }

    private fun getTorch0TriggerNode(): String {
        return getTorch0BrightnessNode().replace("brightness", "trigger")
    }

    private fun getTorch1BrightnessNode(): String {
        cachedTorch1Node?.let { return it }
        val candidates = listOf(
            "/sys/class/leds/led:torch_1/brightness",
            "/sys/class/leds/torch_1/brightness"
        )
        for (path in candidates) {
            if (File(path).exists()) {
                cachedTorch1Node = path
                return path
            }
        }
        return "/sys/class/leds/led:torch_1/brightness"
    }

    private fun getTorch1TriggerNode(): String {
        return getTorch1BrightnessNode().replace("brightness", "trigger")
    }

    private fun getControllerSwitchStatus(controllerClass: Class<*>, controller: Any): Boolean? {
        try {
            for (m in controllerClass.declaredMethods) {
                if (m.name == "getSwitchStatus" && m.parameterTypes.isEmpty()) {
                    m.isAccessible = true
                    return m.invoke(controller) as? Boolean
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    private fun setControllerSwitchStatus(controllerClass: Class<*>, controller: Any, status: Boolean) {
        try {
            for (m in controllerClass.declaredMethods) {
                if (m.name == "setSwitchStatus" && m.parameterTypes.size == 1) {
                    m.isAccessible = true
                    m.invoke(controller, java.lang.Boolean.valueOf(status))
                    return
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set switch status", t)
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
            setControllerSwitchStatus(controllerClass, controller, hwOn)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to resync switch status", t)
        }
    }

    fun isHardwareOn(): Boolean {
        return try {
            val path = getSwitchBrightnessNode()
            val file = File(path)
            if (!file.exists()) return false
            val line = file.bufferedReader().use { it.readLine() }
            line != null && line.trim() != "0"
        } catch (_: Throwable) {
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
            // Quietly ignore IO/permission errors
        }
    }

    private fun showOverheatWarningDialog(
        controllerClass: Class<*>,
        controller: Any,
        mgrClass: Class<*>,
        manager: Any,
        context: Context?,
        safeFraction: Float,
        pendingFraction: Float
    ) {
        Handler(Looper.getMainLooper()).post {
            try {
                val activity = findActivity(context) ?: run {
                    try {
                        val layoutF = controllerClass.getDeclaredField("miFlashlightLayout")
                        layoutF.isAccessible = true
                        val layout = layoutF.get(controller) as? View
                        findActivity(layout?.context)
                    } catch (_: Throwable) { null }
                }

                val dialogContext = activity ?: context ?: run {
                    isDialogShowing = false
                    return@post
                }

                val builder = if (activity != null && !activity.isFinishing && !activity.isDestroyed) {
                    AlertDialog.Builder(activity)
                } else {
                    AlertDialog.Builder(dialogContext, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                }

                builder.setTitle("High Brightness Warning")
                builder.setMessage("Operating the flashlight at high brightness for extended periods can cause overheating and potential hardware damage. Do you want to proceed?")
                builder.setCancelable(false)

                builder.setNegativeButton("Cancel") { dialog, _ ->
                    dialog.dismiss()
                    isDialogShowing = false
                    snapSliderToFraction(controllerClass, controller, safeFraction)
                    invokeAsyncToggle(mgrClass, manager, safeFraction)
                }

                builder.setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    isDialogShowing = false
                    hasShownOverheatDialog = true
                    markOverheatDialogShown(dialogContext)
                    snapSliderToFraction(controllerClass, controller, pendingFraction)
                    invokeAsyncToggle(mgrClass, manager, pendingFraction)
                }

                val dialog = builder.create()
                dialog.setOnDismissListener {
                    isDialogShowing = false
                }

                if (activity == null) {
                    dialog.window?.setType(2020) // WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG
                }

                try {
                    dialog.show()
                } catch (_: Throwable) {
                    try {
                        dialog.window?.setType(2020)
                        dialog.show()
                    } catch (t2: Throwable) {
                        Log.e(TAG, "Failed to display overheat dialog", t2)
                        isDialogShowing = false
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Error in showOverheatWarningDialog", t)
                isDialogShowing = false
            }
        }
    }

    private fun markOverheatDialogShown(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(PREF_KEY_OVERHEAT_WARNED, true).apply()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to persist overheat dialog shown state", t)
        }
    }

    private fun findActivity(ctx: Context?): Activity? {
        var current = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun snapSliderToFraction(controllerClass: Class<*>, controller: Any, fraction: Float) {
        try {
            for (m in controllerClass.declaredMethods) {
                if (m.name == "setUiLogicProgress" && m.parameterTypes.size == 2) {
                    m.isAccessible = true
                    m.invoke(controller, fraction, false)
                    break
                }
            }
        } catch (_: Throwable) {}
        try {
            for (m in controllerClass.declaredMethods) {
                if (m.name == "updateSeekBarProcess" && m.parameterTypes.size == 1) {
                    m.isAccessible = true
                    m.invoke(controller, fraction)
                    break
                }
            }
        } catch (_: Throwable) {}
        try {
            for (m in controllerClass.declaredMethods) {
                if (m.name == "updateFlashlightUi" && m.parameterTypes.size == 1) {
                    m.isAccessible = true
                    m.invoke(controller, fraction)
                    break
                }
            }
        } catch (_: Throwable) {}
    }
}
