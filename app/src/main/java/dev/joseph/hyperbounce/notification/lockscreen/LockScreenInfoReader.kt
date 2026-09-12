package dev.joseph.hyperbounce.notification.lockscreen

import android.content.Context
import android.database.ContentObserver
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Reads and observes HyperOS Lock Screen customization state persisted in Settings.Secure:
 * - "constant_template_editor_info" (HyperOS 2.0 / OS 3.0+)
 * - "constant_lockscreen_info" (HyperOS 1.0 / MIUI)
 */
object LockScreenInfoReader {

    private const val TAG = "HyperBounce_LSReader"

    data class LockScreenClockConfig(
        val templateId: String = "classic",
        val style: Int = 1,
        val clockWeight: Int = 400,
        val primaryColor: Int = Color.WHITE,
        val secondaryColor: Int = Color.WHITE,
        val isDark: Boolean = false
    )

    @Volatile
    private var cachedConfig: LockScreenClockConfig? = null

    @Volatile
    private var isObserverRegistered = false

    private val bgExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<(LockScreenClockConfig) -> Unit>()

    fun addListener(listener: (LockScreenClockConfig) -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: (LockScreenClockConfig) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners(config: LockScreenClockConfig) {
        mainHandler.post {
            for (listener in listeners) {
                try {
                    listener.invoke(config)
                } catch (_: Throwable) {}
            }
        }
    }

    fun getConfig(context: Context): LockScreenClockConfig {
        cachedConfig?.let { return it }
        registerObserverIfNeeded(context)
        val cfg = readConfigFromSettings(context)
        cachedConfig = cfg
        return cfg
    }

    fun registerObserverIfNeeded(context: Context) {
        if (isObserverRegistered) return
        val appContext = context.applicationContext ?: context
        try {
            val resolver = appContext.contentResolver
            val observer = object : ContentObserver(mainHandler) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    bgExecutor.execute {
                        try {
                            val newCfg = readConfigFromSettings(appContext)
                            cachedConfig = newCfg
                            notifyListeners(newCfg)
                            Log.d(TAG, "Lockscreen clock config updated: templateId=${newCfg.templateId}, primaryColor=${Integer.toHexString(newCfg.primaryColor)}")
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error reloading lockscreen config", t)
                        }
                    }
                }
            }

            val uri1 = Settings.Secure.getUriFor("constant_template_editor_info")
            val uri2 = Settings.Secure.getUriFor("constant_lockscreen_info")
            if (uri1 != null) resolver.registerContentObserver(uri1, false, observer)
            if (uri2 != null) resolver.registerContentObserver(uri2, false, observer)
            isObserverRegistered = true
            Log.d(TAG, "Registered ContentObserver for lockscreen templates")
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to register ContentObserver for lockscreen config", t)
        }
    }

    private fun readConfigFromSettings(context: Context): LockScreenClockConfig {
        try {
            val resolver = context.contentResolver
            var rawJson = Settings.Secure.getString(resolver, "constant_template_editor_info")
            if (rawJson.isNullOrBlank()) {
                rawJson = Settings.Secure.getString(resolver, "constant_lockscreen_info")
            }

            if (!rawJson.isNullOrBlank()) {
                val root = JSONObject(rawJson)
                val lockscreenInfo = root.optJSONObject("lockscreenInfo") ?: root
                val clockInfo = lockscreenInfo.optJSONObject("clockInfo")
                if (clockInfo != null) {
                    val templateId = clockInfo.optString("templateId", "classic").lowercase()
                    val style = clockInfo.optInt("style", 1)
                    val clockWeight = clockInfo.optInt("clockWeight", 400)
                    var primaryColor = clockInfo.optInt("primaryColor", Color.WHITE)
                    var secondaryColor = clockInfo.optInt("secondaryColor", Color.WHITE)
                    val isDark = clockInfo.optBoolean("isDarkClockArea", false)

                    if (primaryColor == 0) primaryColor = Color.WHITE
                    if (secondaryColor == 0) secondaryColor = primaryColor

                    return LockScreenClockConfig(
                        templateId = templateId,
                        style = style,
                        clockWeight = clockWeight,
                        primaryColor = primaryColor,
                        secondaryColor = secondaryColor,
                        isDark = isDark
                    )
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to parse lockscreen clock json", t)
        }

        return LockScreenClockConfig()
    }
}
