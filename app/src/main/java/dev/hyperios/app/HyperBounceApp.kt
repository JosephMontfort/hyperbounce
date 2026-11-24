package dev.hyperios.app

import android.app.Application
import android.util.Log
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.state.XposedServiceBridge

class HyperBounceApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("HyperiOS", "HyperBounceApp: Process started, initializing XposedServiceBridge")
        // Initialize early at process boot so LSPosed binds remote preferences before UI or hooks query it
        XposedServiceBridge.init()
        XposedServiceBridge.onBind {
            Log.i("HyperiOS", "HyperBounceApp: XposedService bound, triggering pending push flush")
            SettingsRepository.flushPendingPush(this)
        }
    }
}
