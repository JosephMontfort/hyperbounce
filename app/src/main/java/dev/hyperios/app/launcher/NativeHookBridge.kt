package dev.hyperios.app.launcher

import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Native Hook Bridge for HyperOS 4 & LibXposed API 102.
 * Synchronizes runtime Compose UI unlock configuration with the
 * native hooking layer (libhyperbounce_native.so in SystemUI) and
 * the native agent (libhyperbounce_agent.so injected into com.miui.home).
 */
object NativeHookBridge {
    private const val TAG = "HyperBounce_NativeBridge"
    private const val SOCKET_NAME = "hyperbounce.launcher.control"
    private var isLoaded = false

    private const val CMD_PING: Byte = 0x00
    private const val CMD_UPDATE_CONFIG: Byte = 0x01
    private const val CMD_TRIGGER_UNLOCK: Byte = 0x02

    init {
        try {
            System.loadLibrary("hyperbounce_native")
            isLoaded = true
            Log.d(TAG, "Loaded libhyperbounce_native.so successfully via JNI")
        } catch (_: Throwable) {
            isLoaded = true
        }
    }

    fun syncConfig(enabled: Boolean, mode: Int, damping: Float, response: Float, velocity: Float) {
        // 1. Update in-process native library (SystemUI)
        if (isLoaded) {
            try {
                updateUnlockConfig(enabled, mode, damping, response, velocity)
            } catch (t: Throwable) {
                Log.d(TAG, "In-process native hook update bypassed: ${t.message}")
            }
        }

        // 2. Dispatch IPC config update to native agent in com.miui.home (HyperOS 4)
        if (dev.hyperios.app.common.DeviceUtils.isHyperOS4) {
            Thread({
                try {
                    val socket = LocalSocket()
                    socket.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
                    socket.soTimeout = 1000

                    val buf = ByteBuffer.allocate(18).order(ByteOrder.LITTLE_ENDIAN)
                    buf.put(CMD_UPDATE_CONFIG)
                    buf.putFloat(damping)
                    buf.putFloat(response)
                    buf.putInt(mode)
                    buf.putFloat(velocity)
                    buf.put(if (enabled) 1.toByte() else 0.toByte())

                    socket.outputStream.write(buf.array())
                    socket.outputStream.flush()

                    val ack = socket.inputStream.read()
                    Log.d(TAG, "Synced config with native agent in com.miui.home (ack=$ack, mode=$mode, damp=$damping, resp=$response)")
                    socket.close()
                } catch (t: Throwable) {
                    Log.d(TAG, "Launcher agent socket not yet available: ${t.message}")
                }
            }, "HyperBounce-ConfigSync").start()
        }
    }

    fun triggerUnlockInLauncher() {
        if (!dev.hyperios.app.common.DeviceUtils.isHyperOS4) return
        Thread({
            try {
                val socket = LocalSocket()
                socket.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
                socket.soTimeout = 1000

                socket.outputStream.write(byteArrayOf(CMD_TRIGGER_UNLOCK))
                socket.outputStream.flush()

                val ack = socket.inputStream.read()
                Log.d(TAG, "Sent CMD_TRIGGER_UNLOCK to launcher agent (ack=$ack)")
                socket.close()
            } catch (t: Throwable) {
                Log.d(TAG, "Failed to send CMD_TRIGGER_UNLOCK to launcher agent: ${t.message}")
            }
        }, "HyperBounce-UnlockTrigger").start()
    }

    @JvmStatic
    private external fun updateUnlockConfig(
        enabled: Boolean,
        mode: Int,
        damping: Float,
        response: Float,
        velocity: Float
    )
}
