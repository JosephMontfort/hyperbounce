package dev.joseph.hyperbounce.notification.lockscreen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Replicates the HyperOS Lock Screen Clock typography and layout:
 * - Direct Canvas rendering: sits behind the depth cutout and refracts naturally through glass bevels.
 * - Exact HyperOS styles: Classic, Rhombus, Oversize A/B, Magazine.
 * - Dynamic notification morphing: scales down to compact size when notifications are present.
 * - Auto-updating time ticks.
 */
object LockScreenClockHelper {

    private const val TAG = "HyperBounce_LSClock"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tickListeners = CopyOnWriteArrayList<() -> Unit>()
    private var isTimeReceiverRegistered = false

    private val typefaceCache: Typeface by lazy {
        try {
            val f = File("/system/fonts/MiSansVF.ttf")
            if (f.exists()) {
                Typeface.createFromFile(f)
            } else {
                Typeface.create("sans-serif", Typeface.BOLD)
            }
        } catch (_: Throwable) {
            Typeface.create("sans-serif", Typeface.BOLD)
        }
    }

    private val clockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = Color.WHITE
    }

    private val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        color = 0xEEFFFFFF.toInt()
    }

    private val textBounds = Rect()

    fun addTickListener(listener: () -> Unit) {
        if (!tickListeners.contains(listener)) {
            tickListeners.add(listener)
        }
    }

    fun removeTickListener(listener: () -> Unit) {
        tickListeners.remove(listener)
    }

    fun registerTimeTickReceiverIfNeeded(context: Context) {
        if (isTimeReceiverRegistered) return
        val appContext = context.applicationContext ?: context
        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
                addAction(Intent.ACTION_CONFIGURATION_CHANGED)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(c: Context?, intent: Intent?) {
                    mainHandler.post {
                        for (listener in tickListeners) {
                            try {
                                listener.invoke()
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
            appContext.registerReceiver(receiver, filter)
            isTimeReceiverRegistered = true
            Log.d(TAG, "Registered TimeTick BroadcastReceiver")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register TimeTick BroadcastReceiver", t)
        }
    }

    /**
     * Renders the replicated HyperOS lockscreen clock onto the provided canvas.
     * @param morphFraction 0.0 = full prominent lockscreen clock; 1.0 = compact clock with notifications.
     */
    fun drawClock(
        canvas: Canvas,
        context: Context,
        w: Int,
        h: Int,
        density: Float,
        config: LockScreenInfoReader.LockScreenClockConfig,
        morphFraction: Float,
        alpha: Float,
        translationY: Float = 0f
    ) {
        if (alpha <= 0.01f || w <= 0 || h <= 0) return

        registerTimeTickReceiverIfNeeded(context)

        val is24Hour = DateFormat.is24HourFormat(context)
        val now = Calendar.getInstance()
        val hourFormat = if (is24Hour) "HH" else "h"
        val hourStr = SimpleDateFormat(hourFormat, Locale.getDefault()).format(now.time)
        val minuteStr = SimpleDateFormat("mm", Locale.getDefault()).format(now.time)
        val timeFullStr = "$hourStr:$minuteStr"

        // Date string: e.g. "Friday, September 12"
        val dateFullPattern = if (Locale.getDefault().language == "zh") "M月d日 EEEE" else "EEEE, MMMM d"
        val dateCompactPattern = if (Locale.getDefault().language == "zh") "M月d日 EEE" else "EEE, MMM d"
        val dateStr = SimpleDateFormat(
            if (morphFraction >= 0.5f) dateCompactPattern else dateFullPattern,
            Locale.getDefault()
        ).format(now.time)

        val templateId = config.templateId.lowercase()
        val isOversize = templateId.startsWith("oversize")
        val isRhombus = templateId.startsWith("rhombus")
        val isMagazine = templateId.startsWith("magazine") || templateId.startsWith("signature")

        val baseColor = if (config.primaryColor != 0) config.primaryColor else Color.WHITE
        val dateColor = if (config.secondaryColor != 0) config.secondaryColor else baseColor

        val effectiveAlpha = (alpha * 255f).toInt().coerceIn(0, 255)
        clockPaint.color = (baseColor and 0x00FFFFFF) or (effectiveAlpha shl 24)
        datePaint.color = (dateColor and 0x00FFFFFF) or (((effectiveAlpha * 0.90f).toInt()) shl 24)

        // Subtle soft drop shadow for crystal-clear legibility against any background
        val shadowAlpha = ((alpha * 0.40f) * 255).toInt().coerceIn(0, 255)
        val shadowColor = shadowAlpha shl 24
        clockPaint.setShadowLayer(6f * density, 0f, 2f * density, shadowColor)
        datePaint.setShadowLayer(4f * density, 0f, 1f * density, shadowColor)

        val saveCount = canvas.save()
        canvas.translate(0f, translationY)

        if (isOversize) {
            drawOversizeClock(canvas, w, density, hourStr, minuteStr, dateStr, morphFraction)
        } else if (isRhombus) {
            drawRhombusClock(canvas, w, density, hourStr, minuteStr, dateStr, morphFraction)
        } else if (isMagazine) {
            drawMagazineClock(canvas, w, density, hourStr, minuteStr, dateStr, morphFraction)
        } else {
            drawClassicClock(canvas, w, density, timeFullStr, dateStr, morphFraction)
        }

        canvas.restoreToCount(saveCount)
    }

    private fun drawClassicClock(
        canvas: Canvas,
        w: Int,
        density: Float,
        timeStr: String,
        dateStr: String,
        morph: Float
    ) {
        val cx = w / 2f

        // When morph = 0.0 (Full): Top margin 92dp, time text ~80sp, date ~16sp
        // When morph = 1.0 (Compact): Top margin 42dp, time text ~56sp, date ~13sp
        val topMargin = (92f - 50f * morph) * density
        val timeSize = (80f - 24f * morph) * density
        val dateSize = (16f - 3f * morph) * density

        // Date on top
        datePaint.typeface = Typeface.create(typefaceCache, Typeface.NORMAL)
        datePaint.textSize = dateSize
        datePaint.textAlign = Paint.Align.CENTER
        canvas.drawText(dateStr, cx, topMargin, datePaint)

        // Time right below date
        clockPaint.typeface = Typeface.create(typefaceCache, Typeface.BOLD)
        clockPaint.textSize = timeSize
        clockPaint.textAlign = Paint.Align.CENTER
        val timeY = topMargin + timeSize * 0.88f + 8f * density
        canvas.drawText(timeStr, cx, timeY, clockPaint)
    }

    private fun drawOversizeClock(
        canvas: Canvas,
        w: Int,
        density: Float,
        hourStr: String,
        minuteStr: String,
        dateStr: String,
        morph: Float
    ) {
        if (morph >= 0.65f) {
            // Smoothly morph to compact classic layout when notifications occupy the panel
            drawClassicClock(canvas, w, density, "$hourStr:$minuteStr", dateStr, morph)
            return
        }

        // Oversize layout: Enormous bold stacked digits
        val scale = 1.0f - morph * 0.35f
        val startX = 36f * density
        val topMargin = (84f - 24f * morph) * density
        val digitSize = 104f * density * scale
        val dateSize = 15f * density * scale

        datePaint.typeface = Typeface.create(typefaceCache, Typeface.NORMAL)
        datePaint.textSize = dateSize
        datePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(dateStr, startX, topMargin, datePaint)

        clockPaint.typeface = Typeface.create(typefaceCache, Typeface.BOLD)
        clockPaint.textSize = digitSize
        clockPaint.textAlign = Paint.Align.LEFT

        val hourY = topMargin + digitSize * 0.85f + 4f * density
        canvas.drawText(hourStr, startX, hourY, clockPaint)

        val minuteY = hourY + digitSize * 0.85f - 4f * density
        canvas.drawText(minuteStr, startX, minuteY, clockPaint)
    }

    private fun drawRhombusClock(
        canvas: Canvas,
        w: Int,
        density: Float,
        hourStr: String,
        minuteStr: String,
        dateStr: String,
        morph: Float
    ) {
        if (morph >= 0.65f) {
            drawClassicClock(canvas, w, density, "$hourStr:$minuteStr", dateStr, morph)
            return
        }

        val cx = w / 2f
        val topMargin = (88f - 30f * morph) * density
        val digitSize = (88f - 26f * morph) * density
        val dateSize = (15f - 2f * morph) * density

        datePaint.typeface = Typeface.create(typefaceCache, Typeface.NORMAL)
        datePaint.textSize = dateSize
        datePaint.textAlign = Paint.Align.CENTER
        canvas.drawText(dateStr, cx, topMargin, datePaint)

        clockPaint.typeface = Typeface.create(typefaceCache, Typeface.BOLD)
        clockPaint.textSize = digitSize
        clockPaint.textAlign = Paint.Align.CENTER

        val hourY = topMargin + digitSize * 0.86f + 4f * density
        canvas.drawText(hourStr, cx, hourY, clockPaint)

        val minuteY = hourY + digitSize * 0.86f - 2f * density
        canvas.drawText(minuteStr, cx, minuteY, clockPaint)
    }

    private fun drawMagazineClock(
        canvas: Canvas,
        w: Int,
        density: Float,
        hourStr: String,
        minuteStr: String,
        dateStr: String,
        morph: Float
    ) {
        if (morph >= 0.65f) {
            drawClassicClock(canvas, w, density, "$hourStr:$minuteStr", dateStr, morph)
            return
        }

        val startX = 32f * density
        val topMargin = (85f - 25f * morph) * density
        val digitSize = (92f - 25f * morph) * density
        val dateSize = 14f * density

        datePaint.typeface = Typeface.create(typefaceCache, Typeface.NORMAL)
        datePaint.textSize = dateSize
        datePaint.textAlign = Paint.Align.LEFT
        canvas.drawText(dateStr, startX, topMargin, datePaint)

        clockPaint.typeface = Typeface.create(typefaceCache, Typeface.BOLD)
        clockPaint.textSize = digitSize
        clockPaint.textAlign = Paint.Align.LEFT

        val hourY = topMargin + digitSize * 0.88f
        canvas.drawText(hourStr, startX, hourY, clockPaint)

        val minuteY = hourY + digitSize * 0.88f
        canvas.drawText(minuteStr, startX, minuteY, clockPaint)
    }
}
