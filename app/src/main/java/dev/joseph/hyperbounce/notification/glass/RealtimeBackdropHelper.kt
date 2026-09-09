package dev.joseph.hyperbounce.notification.glass

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import java.lang.ref.WeakReference
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.util.concurrent.Executors

/**
 * Captures real-time screen content underneath the notification shade (e.g. YouTube playing,
 * active apps, homescreen) for optical liquid glass refraction.
 *
 * Uses Android 14 / HyperOS ScreenCapture APIs excluding the NotificationShade layer so that
 * underlying apps are sampled continuously without blocking the UI thread (120 FPS).
 */
object RealtimeBackdropHelper {
    private const val TAG = "HyperBounce_Backdrop"

    @Volatile private var latestBitmap: Bitmap? = null
    @Volatile private var isCapturing = false
    @Volatile private var lastCaptureTime = 0L

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

    private var isRefreshing = false
    private var refreshRunnable: Runnable? = null

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
            Log.d(TAG, "ScreenCapture reflection initialized successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to initialize ScreenCapture reflection", t)
        }
    }

    fun getLatestBackdropBitmap(): Bitmap? {
        val bmp = latestBitmap
        return if (bmp != null && !bmp.isRecycled) bmp else null
    }

    fun triggerCapture(context: Context, onCaptured: (() -> Unit)? = null) {
        val now = System.currentTimeMillis()
        if (now - lastCaptureTime < 100L && latestBitmap != null) {
            return
        }
        if (isCapturing) return
        isCapturing = true

        bgExecutor.execute {
            try {
                initReflection()
                val wms = wmsInstance
                val capMethod = captureDisplayMethod
                val syncMethod = createSyncListenerMethod
                val bConstructor = builderConstructor
                val buildMethod = buildCaptureArgsMethod

                if (wms != null && capMethod != null && syncMethod != null && bConstructor != null && buildMethod != null) {
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
                    // 0.35f scale gives crisp liquid refraction while consuming negligible RAM/GPU bandwidth
                    setFrameScaleMethod?.invoke(builder, 0.35f)

                    val captureArgs = buildMethod.invoke(builder)
                    val syncListener = syncMethod.invoke(null)
                    capMethod.invoke(wms, 0, captureArgs, syncListener)

                    val buffer = getBufferMethod?.invoke(syncListener)
                    if (buffer != null) {
                        val bmp = asBitmapMethod?.invoke(buffer) as? Bitmap
                        if (bmp != null) {
                            val old = latestBitmap
                            latestBitmap = bmp
                            lastCaptureTime = System.currentTimeMillis()
                            mainHandler.post {
                                onCaptured?.invoke()
                            }
                            if (old != null && old != bmp && !old.isRecycled) {
                                try {
                                    old.recycle()
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "performCapture failed", t)
            } finally {
                isCapturing = false
            }
        }
    }

    fun startLiveRefresh(context: Context, sheetView: View) {
        if (isRefreshing) return
        isRefreshing = true
        val weakSheet = WeakReference(sheetView)

        // Capture immediately for initial frame
        triggerCapture(context) {
            weakSheet.get()?.invalidate()
        }

        refreshRunnable = object : Runnable {
            override fun run() {
                if (!isRefreshing) return
                val sheet = weakSheet.get()
                if (sheet == null || sheet.visibility != View.VISIBLE) {
                    stopLiveRefresh()
                    return
                }
                triggerCapture(context) {
                    sheet.invalidate()
                }
                mainHandler.postDelayed(this, 180L)
            }
        }
        mainHandler.postDelayed(refreshRunnable!!, 180L)
    }

    fun stopLiveRefresh() {
        isRefreshing = false
        refreshRunnable?.let { mainHandler.removeCallbacks(it) }
        refreshRunnable = null
    }

    fun release() {
        stopLiveRefresh()
        bgExecutor.execute {
            latestBitmap?.recycle()
            latestBitmap = null
        }
    }
}
