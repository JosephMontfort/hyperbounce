package dev.joseph.hyperbounce.gesture

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Linux input subsystem event definitions and 64-bit `struct input_event` parser.
 *
 * Binary layout for 64-bit Linux (aarch64):
 * - bytes 0..7:   tv_sec  (Long, 64-bit)
 * - bytes 8..15:  tv_usec (Long, 64-bit)
 * - bytes 16..17: type    (Short, unsigned 16-bit)
 * - bytes 18..19: code    (Short, unsigned 16-bit)
 * - bytes 20..23: value   (Int, signed 32-bit)
 * Total size: 24 bytes.
 */
object InputEventConstants {
    const val STRUCT_SIZE = 24

    // Event Types
    const val EV_SYN = 0x0000
    const val EV_KEY = 0x0001
    const val EV_REL = 0x0002
    const val EV_ABS = 0x0003

    // Synchronization Codes
    const val SYN_REPORT = 0x0000

    // Key Codes
    const val BTN_TOUCH = 0x014a // 330

    // Absolute Axis Codes
    const val ABS_MT_SLOT = 0x002f        // 47
    const val ABS_MT_TOUCH_MAJOR = 0x0030 // 48
    const val ABS_MT_TOUCH_MINOR = 0x0031 // 49
    const val ABS_MT_WIDTH_MAJOR = 0x0032 // 50
    const val ABS_MT_WIDTH_MINOR = 0x0033 // 51
    const val ABS_MT_POSITION_X = 0x0035  // 53
    const val ABS_MT_POSITION_Y = 0x0036  // 54
    const val ABS_MT_TRACKING_ID = 0x0039 // 57
}

/**
 * Reusable mutable struct to prevent heap object allocations during high-frequency
 * input streaming (e.g. 120Hz touch scanning).
 */
class MutableInputEvent {
    var sec: Long = 0L
    var usec: Long = 0L
    var type: Int = 0
    var code: Int = 0
    var value: Int = 0

    fun parseFrom(buffer: ByteBuffer, offset: Int) {
        sec = buffer.getLong(offset)
        usec = buffer.getLong(offset + 8)
        type = buffer.getShort(offset + 16).toInt() and 0xFFFF
        code = buffer.getShort(offset + 18).toInt() and 0xFFFF
        value = buffer.getInt(offset + 20)
    }
}
