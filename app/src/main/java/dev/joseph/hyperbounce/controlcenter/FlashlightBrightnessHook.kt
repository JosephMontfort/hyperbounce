package dev.joseph.hyperbounce.controlcenter

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import io.github.libxposed.api.XposedModule
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Flashlight Brightness Control for low-end Xiaomi / HyperOS devices.
 * Replaces unavailable driver-level Camera2 strength APIs with direct Qualcomm PMIC sysfs latching,
 * unlocks the 1-100% brightness slider in the expanded panel, and unifies CC quick-tile toggling.
 */
object FlashlightBrightnessHook {
    private const val TAG = "HyperBounce_Flashlight"
    private const val SPLIT_NOTCH_TAG = "hyperbounce_split_notch"

    @Volatile private var isTorchOn: Boolean = false
    @Volatile private var lastWriteTime: Long = 0L
    @Volatile private var bonusZoneWarned: Boolean = false
    @Volatile private var lastPercentage: Float = -1f

    @Volatile private var cachedSwitchNode: String? = null
    @Volatile private var cachedTorch0Node: String? = null
    @Volatile private var cachedTorch1Node: String? = null
    @Volatile private var cachedHardwareMax: Int = 0
    @Volatile private var detectedStockBrightness: Int = 70
    @Volatile private var stockBrightnessDetected: Boolean = false

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
     * getLogicStrength returns 0.50f (when bonus is enabled) or 1.0f (when bonus is disabled),
     * representing 100% of stock default brightness.
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

            // Hook getLogicStrength to return 0.50f (bonus mode) or 1.0f (normal mode)
            for (m in managerClass.declaredMethods) {
                if (m.name == "getLogicStrength" && m.returnType == java.lang.Float.TYPE) {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        if (!ControlCenterConfigHolder.config.lowEndFlashlightEnabled) {
                            return@intercept chain.proceed()
                        }
                        if (ControlCenterConfigHolder.config.flashlightBonusEnabled) 0.50f else 1.0f
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
     * Hardware driver logic replicating Qualcomm PMIC latching with zero flicker and dynamic scaling.
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
            val torch0Node = getTorch0BrightnessNode()
            val torch1Node = getTorch1BrightnessNode()

            if (strength == 0) {
                writeToFile(switchNode, "0")
                writeToFile(torch0Node, "0")
                writeToFile(torch1Node, "0")
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
                if (now - lastWriteTime < 25L) {
                    return
                }
                lastWriteTime = now

                // Dynamically detect stock brightness on first torch write before overwriting
                detectStockBrightnessIfNeeded()

                val hwOn = isHardwareOn()

                if (!hwOn && !isTorchOn) {
                    try {
                        cameraManager.setTorchMode(cameraId, true)
                        isTorchOn = true
                    } catch (t: Throwable) {
                        Log.w(TAG, "cameraManager.setTorchMode true failed", t)
                    }
                }

                val targetBrightness = calculateSysfsBrightness(strength)
                val strVal = targetBrightness.toString()

                // Zero-flicker write: update torch_0 and torch_1 brightness directly.
                // When already on, do NOT pulse switch to 0! This eliminates stroboscopic flicker.
                writeToFile(torch0Node, strVal)
                writeToFile(torch1Node, strVal)

                if (!hwOn) {
                    writeToFile(switchNode, "1")
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in handleHardwareOperateCamera", t)
        }
    }

    /**
     * Parts 4 & 5: MiFlashlightController status handling, bonus zone Toast, visual split notch, and panel-open resync.
     */
    private fun hookMiFlashlightController(module: XposedModule, classLoader: ClassLoader) {
        try {
            val controllerClass = Class.forName("miui.systemui.flashlight.MiFlashlightController", false, classLoader)

            // Part 4: Hook setFlashlightStatus(float) to warn on bonus zone, haptic tick, direct start from OFF, and skip persistent storage
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

                        val bonusEnabled = ControlCenterConfigHolder.config.flashlightBonusEnabled

                        // Overheat warning toast when entering bonus zone (> 0.50f)
                        if (bonusEnabled) {
                            if (percentage > 0.50f) {
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

                            // Tactile haptic feedback when crossing 50% split mark
                            if (lastPercentage >= 0f) {
                                val crossedUp = lastPercentage < 0.50f && percentage >= 0.50f
                                val crossedDown = lastPercentage >= 0.50f && percentage < 0.50f
                                if (crossedUp || crossedDown) {
                                    triggerHapticTick(controller, controllerClass)
                                }
                            }
                            lastPercentage = percentage
                        } else {
                            bonusZoneWarned = false
                        }

                        // Get switch status
                        val switchStatus = getControllerSwitchStatus(controllerClass, controller)

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
                                    setControllerSwitchStatus(controllerClass, controller, false)
                                } else {
                                    invokeAsyncToggle(mgrClass, manager, percentage)
                                }
                            } else { // switchStatus == false (OFF)
                                if (percentage > 0f) {
                                    // Fix initial 75% jump on touch from OFF: directly toggle to the touch percentage!
                                    invokeAsyncToggle(mgrClass, manager, percentage)
                                    setControllerSwitchStatus(controllerClass, controller, true)
                                }
                            }
                        }

                        // Do not proceed: bypasses saveLogicStrength
                        null
                    }
                }
            }

            // Part 5: Resync switch status from hardware and setup visual split notch whenever flashlight UI is configured
            for (m in controllerClass.declaredMethods) {
                if (m.name == "setSwitchLottieView") {
                    m.isAccessible = true
                    module.hook(m).intercept { chain ->
                        val res = chain.proceed()
                        if (ControlCenterConfigHolder.config.lowEndFlashlightEnabled) {
                            val controller = chain.thisObject
                            if (controller != null) {
                                resyncSwitchStatus(controllerClass, controller)
                                setupSplitNotchView(controllerClass, controller)
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
     * Part 6: Hook ToggleChangeReceiver.onToggleChange to dispatch operateCamera at stock brightness on quick-tile tap.
     * When bonus enabled: 50% maps to stock brightness.
     * When bonus disabled: 100% maps to stock brightness.
     * Hardware is already at stock brightness from setTorchMode, eliminating the double-brightness jump completely!
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
                                val bonusEnabled = ControlCenterConfigHolder.config.flashlightBonusEnabled
                                val strength = if (isOpen) {
                                    if (bonusEnabled) 50 else 100
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
     * - Bonus mode: 1..50 maps to 1..stockDefaultBrightness (50 is 100% stock).
     *   51..100 maps to stockDefaultBrightness..flashlightMaxBrightness (bonus overdrive).
     * - Linear mode: 1..100 maps linearly to 1..stockDefaultBrightness.
     */
    fun calculateSysfsBrightness(strength: Int): Int {
        if (strength <= 0) return 0
        val stock = getStockBrightness()
        val bonusEnabled = ControlCenterConfigHolder.config.flashlightBonusEnabled
        val maxLimit = ControlCenterConfigHolder.config.flashlightMaxBrightness.coerceIn(stock, getHardwareMaxBrightness())

        return if (bonusEnabled) {
            if (strength <= 50) {
                val fraction = strength / 50.0f
                (fraction * stock).roundToInt().coerceIn(1, stock)
            } else {
                val fraction = (strength - 50) / 50.0f
                (stock + fraction * (maxLimit - stock)).roundToInt().coerceIn(stock, maxLimit)
            }
        } else {
            val fraction = strength / 100.0f
            (fraction * stock).roundToInt().coerceIn(1, stock)
        }
    }

    /**
     * Dynamically detects stock torch brightness from sysfs if set by camera HAL.
     */
    private fun detectStockBrightnessIfNeeded() {
        if (stockBrightnessDetected) return
        try {
            val torchFile = File(getTorch0BrightnessNode())
            if (torchFile.exists()) {
                val v = torchFile.bufferedReader().use { it.readLine() }?.trim()?.toIntOrNull()
                if (v != null && v in 10..200) {
                    detectedStockBrightness = v
                    stockBrightnessDetected = true
                    Log.i(TAG, "Dynamically detected stock default brightness: $v")
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to detect stock brightness from sysfs", t)
        }
    }

    fun getStockBrightness(): Int {
        val cfgVal = ControlCenterConfigHolder.config.flashlightStockBrightness
        if (cfgVal > 0) return cfgVal
        return detectedStockBrightness
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

    /**
     * Adds or updates the visual split notch in mi_seekbar_container at 50% height.
     */
    private fun setupSplitNotchView(controllerClass: Class<*>, controller: Any) {
        try {
            var seekbar: View? = null
            for (m in controllerClass.declaredMethods) {
                if (m.name == "getSeekbar" && m.parameterTypes.isEmpty()) {
                    m.isAccessible = true
                    seekbar = m.invoke(controller) as? View
                    break
                }
            }
            val container = (seekbar?.parent as? ViewGroup) ?: return

            val existingNotch = container.findViewWithTag<View>(SPLIT_NOTCH_TAG)
            val bonusEnabled = ControlCenterConfigHolder.config.flashlightBonusEnabled

            if (!bonusEnabled) {
                existingNotch?.visibility = View.GONE
                return
            }

            if (existingNotch != null) {
                existingNotch.visibility = View.VISIBLE
                return
            }

            val context = container.context
            val dp = context.resources.displayMetrics.density
            val heightPx = (2 * dp).toInt().coerceAtLeast(2)
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                heightPx
            ).apply {
                gravity = Gravity.CENTER
                leftMargin = (8 * dp).toInt()
                rightMargin = (8 * dp).toInt()
            }
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 1 * dp
                setColor(0x80FFFFFF.toInt()) // 50% translucent white frosted notch
            }
            val notch = View(context).apply {
                tag = SPLIT_NOTCH_TAG
                layoutParams = lp
                background = shape
                elevation = 20f
            }
            container.addView(notch)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to setup split notch view", t)
        }
    }

    /**
     * Triggers tactile haptic feedback when crossing the 50% mark.
     */
    private fun triggerHapticTick(controller: Any, controllerClass: Class<*>) {
        try {
            for (f in controllerClass.declaredFields) {
                if (f.name == "hapticFeedback") {
                    f.isAccessible = true
                    val hapticObj = f.get(controller) ?: continue
                    for (m in hapticObj.javaClass.declaredMethods) {
                        if (m.name == "click" && m.parameterTypes.isEmpty()) {
                            m.isAccessible = true
                            m.invoke(hapticObj)
                            return
                        }
                    }
                }
            }
        } catch (_: Throwable) {}

        try {
            for (m in controllerClass.declaredMethods) {
                if (m.name == "getSeekbar" && m.parameterTypes.isEmpty()) {
                    m.isAccessible = true
                    val view = m.invoke(controller) as? View
                    view?.performHapticFeedback(
                        HapticFeedbackConstants.CLOCK_TICK,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                    )
                    return
                }
            }
        } catch (_: Throwable) {}
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
            // Quietly ignore IO/permission errors
        }
    }
}
