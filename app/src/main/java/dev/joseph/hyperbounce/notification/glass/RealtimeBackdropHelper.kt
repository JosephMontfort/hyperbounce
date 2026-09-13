package dev.joseph.hyperbounce.notification.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import dev.joseph.hyperbounce.notification.NotificationSheetConfigHolder
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.ArrayList
import java.util.concurrent.Executors

/**
 * Captures real-time screen content underneath the notification shade (e.g. YouTube playing,
 * active apps, homescreen) for optical liquid glass refraction.
 *
 * Uses Android 14 / HyperOS ScreenCapture APIs excluding the NotificationShade layer so that
 * underlying apps and homescreen are sampled continuously in real time without blocking
 * the UI thread.
 */
object RealtimeBackdropHelper {
    private const val TAG = "HyperBounce_Backdrop"

    @Volatile private var latestBitmap: Bitmap? = null
    @Volatile private var latestBlurredBitmap: Bitmap? = null
    @Volatile private var isRefreshing = false
    @Volatile private var refreshSessionId = 0

    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var initializedReflection = false
    private var wmsInstance: Any? = null
    private var captureDisplayMethod: Method? = null
    private var createSyncListenerMethod: Method? = null
    private var getBufferMethod: Method? = null
    private var asBitmapMethod: Method? = null
    private var builderConstructor: Constructor<*>? = null
    private var setSourceCropMethod: Method? = null
    private var setCaptureSecureLayersMethod: Method? = null
    private var setAllowProtectedMethod: Method? = null
    private var setCaptureModeMethod: Method? = null
    private var setExcludeLayersMethod: Method? = null
    private var setFrameScaleMethod: Method? = null
    private var buildCaptureArgsMethod: Method? = null
    private var setCaptureBlurRadiusMethod: Method? = null
    private var setCaptureBlendsMethod: Method? = null

    private fun initReflection() {
        if (initializedReflection) return
        initializedReflection = true
        try {
            val wmgClass = Class.forName("android.view.WindowManagerGlobal")
            val getWmsMethod = wmgClass.getMethod("getWindowManagerService")
            wmsInstance = getWmsMethod.invoke(null)

            val screenCaptureClass = Class.forName("android.window.ScreenCapture")
            createSyncListenerMethod = screenCaptureClass.getMethod("createSyncCaptureListener")

            val syncListenerClass = Class.forName("android.window.ScreenCapture\$SynchronousScreenCaptureListener")
            getBufferMethod = syncListenerClass.getMethod("getBuffer")

            val hardwareBufferClass = Class.forName("android.window.ScreenCapture\$ScreenshotHardwareBuffer")
            asBitmapMethod = hardwareBufferClass.getMethod("asBitmap")

            val builderClass = Class.forName("android.window.ScreenCapture\$CaptureArgs\$Builder")
            builderConstructor = builderClass.getConstructor()
            setSourceCropMethod = builderClass.getMethod("setSourceCrop", Rect::class.java)
            setCaptureSecureLayersMethod = builderClass.getMethod("setCaptureSecureLayers", Boolean::class.javaPrimitiveType)
            setAllowProtectedMethod = builderClass.getMethod("setAllowProtected", Boolean::class.javaPrimitiveType)
            setCaptureModeMethod = builderClass.getMethod("setCaptureMode", Int::class.javaPrimitiveType)
            setExcludeLayersMethod = builderClass.getMethod("setExcludeOrIncludeLayerNames", Array<String>::class.java)
            setFrameScaleMethod = builderClass.getMethod("setFrameScale", Float::class.javaPrimitiveType)
            buildCaptureArgsMethod = builderClass.getMethod("build")

            try {
                setCaptureBlurRadiusMethod = builderClass.getDeclaredMethod("setCaptureBlurRadius", Int::class.javaPrimitiveType)
                setCaptureBlurRadiusMethod?.isAccessible = true
            } catch (_: Throwable) {}

            try {
                setCaptureBlendsMethod = builderClass.getDeclaredMethod("setCaptureBlends", ArrayList::class.java)
                setCaptureBlendsMethod?.isAccessible = true
            } catch (_: Throwable) {}

            if (wmsInstance != null) {
                for (m in wmsInstance!!.javaClass.methods) {
                    if (m.name == "captureDisplay" && m.parameterTypes.size == 3 &&
                        m.parameterTypes[0] == Int::class.javaPrimitiveType
                    ) {
                        captureDisplayMethod = m
                        break
                    }
                }
            }
            Log.d(TAG, "ScreenCapture reflection initialized successfully (hasBlurRadiusMethod=${setCaptureBlurRadiusMethod != null})")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize ScreenCapture reflection", t)
        }
    }

    private fun captureDisplayInternal(context: Context, needBlur: Boolean = false): Pair<Bitmap?, Boolean> {
        initReflection()
        val wms = wmsInstance ?: return Pair(null, false)
        val capMethod = captureDisplayMethod ?: return Pair(null, false)
        val syncMethod = createSyncListenerMethod ?: return Pair(null, false)
        val bConstructor = builderConstructor ?: return Pair(null, false)
        val buildMethod = buildCaptureArgsMethod ?: return Pair(null, false)

        val dm = context.resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels
        val bounds = Rect(0, 0, screenW, screenH)

        val builder = bConstructor.newInstance()
        setSourceCropMethod?.invoke(builder, bounds)
        setCaptureSecureLayersMethod?.invoke(builder, true)
        setAllowProtectedMethod?.invoke(builder, true)
        setCaptureModeMethod?.invoke(builder, 1) // 1 = exclude layers
        setExcludeLayersMethod?.invoke(
            builder,
            arrayOf("StatusBar#", "NavigationBar0#", "DynamicIslandWindow#", "NotificationShade#")
        )
        // High fidelity frame scale: 1.0f on 1080p, capped at 1080px wide on higher res screens
        val captureScale = if (screenW <= 1080) 1.0f else (1080f / screenW.toFloat()).coerceIn(0.75f, 1.0f)
        setFrameScaleMethod?.invoke(builder, captureScale)

        var wasHardwareBlurred = false
        if (needBlur && setCaptureBlurRadiusMethod != null) {
            try {
                val blurRadius = (150f * captureScale).toInt().coerceAtLeast(30)
                setCaptureBlurRadiusMethod?.invoke(builder, blurRadius)
                if (setCaptureBlendsMethod != null) {
                    val blendList = ArrayList<Point>()
                    blendList.add(Point(BlendMode.SRC_ATOP.ordinal, 1297438037))
                    setCaptureBlendsMethod?.invoke(builder, blendList)
                }
                wasHardwareBlurred = true
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to apply hardware blur via setCaptureBlurRadius", t)
            }
        }

        val captureArgs = buildMethod.invoke(builder)
        val syncListener = syncMethod.invoke(null)
        capMethod.invoke(wms, 0, captureArgs, syncListener)

        val buffer = getBufferMethod?.invoke(syncListener) ?: return Pair(null, false)
        val bmp = asBitmapMethod?.invoke(buffer) as? Bitmap
        return Pair(bmp, wasHardwareBlurred)
    }

    private fun updateBitmaps(bmp: Bitmap, wasHardwareBlurred: Boolean = false) {
        latestBitmap = bmp
        try {
            val cfg = NotificationSheetConfigHolder.sheet
            val isUnlocked = dev.joseph.hyperbounce.notification.NotificationSheetHook.currentStatusBarState == 0
            val isLandscape = bmp.width > bmp.height
            val isDepthActive = cfg.isDepthActive && !isLandscape && isUnlocked
            val shouldDrawWallpaper = !isDepthActive && cfg.isWallpaperActive && !isLandscape &&
                    (!cfg.lockWallpaperHomescreenOnly || dev.joseph.hyperbounce.notification.NotificationSheetHook.isHomeScreenCached)
            if (!isDepthActive && !shouldDrawWallpaper) {
                if (wasHardwareBlurred) {
                    latestBlurredBitmap = bmp
                } else {
                    latestBlurredBitmap = FastBlur.blur(bmp)
                }
            } else {
                latestBlurredBitmap = null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to blur backdrop bitmap", t)
        }
    }

    fun getLatestBackdropBitmap(needBlur: Boolean = false): Bitmap? {
        if (needBlur) {
            val blurred = latestBlurredBitmap
            if (blurred != null && !blurred.isRecycled) return blurred
            val raw = latestBitmap
            if (raw != null && !raw.isRecycled) {
                try {
                    val b = FastBlur.blur(raw)
                    latestBlurredBitmap = b
                    return b
                } catch (_: Throwable) {}
            }
        }
        val bmp = latestBitmap
        return if (bmp != null && !bmp.isRecycled) bmp else null
    }

    fun triggerCapture(context: Context, onCaptured: (() -> Unit)? = null) {
        val appContext = context.applicationContext ?: context
        bgExecutor.execute {
            try {
                val cfg = NotificationSheetConfigHolder.sheet
                val isUnlocked = dev.joseph.hyperbounce.notification.NotificationSheetHook.currentStatusBarState == 0
                val isLandscape = appContext.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                val isDepthActive = cfg.isDepthActive && !isLandscape && isUnlocked
                val shouldDrawWallpaper = !isDepthActive && cfg.isWallpaperActive && !isLandscape &&
                        (!cfg.lockWallpaperHomescreenOnly || dev.joseph.hyperbounce.notification.NotificationSheetHook.isHomeScreenCached)
                val needBlur = !isDepthActive && !shouldDrawWallpaper
                val (bmp, hwBlurred) = captureDisplayInternal(appContext, needBlur)
                if (bmp != null) {
                    updateBitmaps(bmp, hwBlurred)
                    mainHandler.post {
                        onCaptured?.invoke()
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "triggerCapture failed", t)
            }
        }
    }

    fun startLiveRefresh(context: Context, sheetView: View) {
        if (isRefreshing) return
        isRefreshing = true
        val sessionId = ++refreshSessionId
        val weakSheet = WeakReference(sheetView)
        val appContext = context.applicationContext ?: context

        bgExecutor.execute {
            while (isRefreshing && sessionId == refreshSessionId) {
                val sheet = weakSheet.get()
                if (sheet == null || sheet.visibility != View.VISIBLE) {
                    if (sessionId == refreshSessionId) {
                        isRefreshing = false
                    }
                    break
                }
                try {
                    val startTime = System.currentTimeMillis()
                    val cfg = NotificationSheetConfigHolder.sheet
                    val isUnlocked = dev.joseph.hyperbounce.notification.NotificationSheetHook.currentStatusBarState == 0
                    val isLandscape = appContext.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val isDepthActive = cfg.isDepthActive && !isLandscape && isUnlocked
                    val shouldDrawWallpaper = !isDepthActive && cfg.isWallpaperActive && !isLandscape &&
                            (!cfg.lockWallpaperHomescreenOnly || dev.joseph.hyperbounce.notification.NotificationSheetHook.isHomeScreenCached)
                    val needBlur = !isDepthActive && !shouldDrawWallpaper
                    val (bmp, hwBlurred) = captureDisplayInternal(appContext, needBlur)
                    if (bmp != null && sessionId == refreshSessionId) {
                        updateBitmaps(bmp, hwBlurred)
                        mainHandler.post {
                            if (sessionId == refreshSessionId) {
                                weakSheet.get()?.postInvalidateOnAnimation()
                            }
                        }
                    }
                    val elapsed = System.currentTimeMillis() - startTime
                    val sleepTime = (16L - elapsed).coerceIn(1L, 16L)
                    Thread.sleep(sleepTime)
                } catch (t: Throwable) {
                    if (t is InterruptedException) break
                    Log.e(TAG, "Live capture error", t)
                    try {
                        Thread.sleep(16L)
                    } catch (_: InterruptedException) {
                        break
                    }
                }
            }
        }
    }

    fun stopLiveRefresh() {
        isRefreshing = false
        refreshSessionId++
    }

    fun release() {
        stopLiveRefresh()
        bgExecutor.execute {
            latestBitmap = null
            latestBlurredBitmap = null
        }
    }

    private object FastBlur {
        fun blur(src: Bitmap, scale: Float = 0.125f, radius: Int = 6): Bitmap {
            val w = (src.width * scale).toInt().coerceAtLeast(16)
            val h = (src.height * scale).toInt().coerceAtLeast(16)
            val small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(small)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(src, null, Rect(0, 0, w, h), paint)

            val pixels = IntArray(w * h)
            small.getPixels(pixels, 0, w, 0, 0, w, h)

            boxBlur(pixels, w, h, radius)
            boxBlur(pixels, w, h, radius)

            small.setPixels(pixels, 0, w, 0, 0, w, h)
            return small
        }

        private fun boxBlur(pixels: IntArray, w: Int, h: Int, r: Int) {
            val temp = IntArray(w * h)
            val div = 2 * r + 1

            // Horizontal pass
            for (y in 0 until h) {
                val rowOffset = y * w
                var sumA = 0
                var sumR = 0
                var sumG = 0
                var sumB = 0

                for (i in -r..r) {
                    val clampedX = i.coerceIn(0, w - 1)
                    val px = pixels[rowOffset + clampedX]
                    sumA += (px ushr 24) and 0xff
                    sumR += (px ushr 16) and 0xff
                    sumG += (px ushr 8) and 0xff
                    sumB += px and 0xff
                }

                for (x in 0 until w) {
                    temp[rowOffset + x] = ((sumA / div) shl 24) or
                            ((sumR / div) shl 16) or
                            ((sumG / div) shl 8) or
                            (sumB / div)

                    val xRemove = (x - r).coerceIn(0, w - 1)
                    val xAdd = (x + r + 1).coerceIn(0, w - 1)
                    val pRem = pixels[rowOffset + xRemove]
                    val pAdd = pixels[rowOffset + xAdd]

                    sumA += ((pAdd ushr 24) and 0xff) - ((pRem ushr 24) and 0xff)
                    sumR += ((pAdd ushr 16) and 0xff) - ((pRem ushr 16) and 0xff)
                    sumG += ((pAdd ushr 8) and 0xff) - ((pRem ushr 8) and 0xff)
                    sumB += (pAdd and 0xff) - (pRem and 0xff)
                }
            }

            // Vertical pass
            for (x in 0 until w) {
                var sumA = 0
                var sumR = 0
                var sumG = 0
                var sumB = 0

                for (i in -r..r) {
                    val clampedY = i.coerceIn(0, h - 1)
                    val py = temp[clampedY * w + x]
                    sumA += (py ushr 24) and 0xff
                    sumR += (py ushr 16) and 0xff
                    sumG += (py ushr 8) and 0xff
                    sumB += py and 0xff
                }

                for (y in 0 until h) {
                    pixels[y * w + x] = ((sumA / div) shl 24) or
                            ((sumR / div) shl 16) or
                            ((sumG / div) shl 8) or
                            (sumB / div)

                    val yRemove = (y - r).coerceIn(0, h - 1)
                    val yAdd = (y + r + 1).coerceIn(0, h - 1)
                    val pRem = temp[yRemove * w + x]
                    val pAdd = temp[yAdd * w + x]

                    sumA += ((pAdd ushr 24) and 0xff) - ((pRem ushr 24) and 0xff)
                    sumR += ((pAdd ushr 16) and 0xff) - ((pRem ushr 16) and 0xff)
                    sumG += ((pAdd ushr 8) and 0xff) - ((pRem ushr 8) and 0xff)
                    sumB += (pAdd and 0xff) - (pRem and 0xff)
                }
            }
        }
    }
}
