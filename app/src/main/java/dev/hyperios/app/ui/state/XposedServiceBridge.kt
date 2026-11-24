package dev.hyperios.app.ui.state

/*
 * The write half of the cross-process settings mechanism (see
 * prefs/HyperBouncePrefs.kt for the hook-side read half and the full explanation of why
 * this app uses libxposed's "remote preferences" instead of the classic
 * XSharedPreferences approach).
 *
 * XposedServiceHelper.registerListener(...) asks the Xposed framework (LSPosed) to hand
 * this app an XposedService once it's available — this happens asynchronously and only
 * when the module is actually active for this app in LSPosed's manager, so `service` may
 * stay null forever in an ordinary (non-rooted, or module-disabled) install. Every write
 * in this app already goes to local SharedPreferences first (SettingsRepository's normal,
 * always-available behavior), so nothing here is on the critical path for the UI itself —
 * this only mirrors those same writes out to the hooks, best-effort.
 */

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

object XposedServiceBridge {
    private const val TAG = "HyperiOS"

    @Volatile private var service: XposedService? = null
    val isBoundState = androidx.compose.runtime.mutableStateOf(false)
    private var initialized = false
    private val callbacksLock = Any()
    private val onBindCallbacks = mutableListOf<() -> Unit>()

    /** Call once, as early as possible (SettingsRepository's init block does this). */
    fun init() {
        if (initialized) return
        initialized = true
        try {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(xposedService: XposedService) {
                    service = xposedService
                    isBoundState.value = true
                    Log.i(TAG, "Xposed remote preferences service bound")
                    val pending: List<() -> Unit>
                    synchronized(callbacksLock) {
                        pending = onBindCallbacks.toList()
                    }
                    pending.forEach { it() }
                }

                override fun onServiceDied(xposedService: XposedService) {
                    service = null
                    isBoundState.value = false
                    Log.w(TAG, "Xposed remote preferences service died")
                }
            })
        } catch (t: Throwable) {
            // No Xposed framework present at all (e.g. running unrooted for UI testing) —
            // the app still works fully on local preferences alone.
            Log.w(TAG, "Xposed service unavailable: ${t.message}")
        }
    }

    val isBound: Boolean get() = service != null

    /** Runs [callback] once the service is bound — immediately if it already is. */
    fun onBind(callback: () -> Unit) {
        var shouldExecuteNow = false
        synchronized(callbacksLock) {
            onBindCallbacks.add(callback)
            if (isBound) {
                shouldExecuteNow = true
            }
        }
        if (shouldExecuteNow) callback()
    }

    fun remotePreferencesOrNull(group: String): SharedPreferences? =
        try {
            service?.getRemotePreferences(group)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to get remote preferences: ${t.message}")
            null
        }

    fun getScopedPackages(context: android.content.Context): List<String> {
        val dynamicScope = try {
            service?.scope
        } catch (t: Throwable) {
            null
        }
        if (!dynamicScope.isNullOrEmpty()) {
            return dynamicScope
        }
        return try {
            val resId = context.resources.getIdentifier("xposed_scope", "array", context.packageName)
            if (resId != 0) context.resources.getStringArray(resId).toList() else emptyList()
        } catch (t: Throwable) {
            emptyList()
        }
    }
}
