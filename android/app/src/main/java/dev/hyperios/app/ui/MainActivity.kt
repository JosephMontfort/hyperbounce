package dev.hyperios.app.ui

import android.os.Build
import android.os.Bundle
import android.util.Log
import dev.hyperios.app.ui.state.AppIconStyle
import dev.hyperios.app.ui.state.SettingsRepository
import dev.hyperios.app.ui.state.XposedServiceBridge
import dev.hyperios.app.ui.utils.AppIconManager
import dev.hyperios.app.ui.utils.RootUtils
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    companion object {
        private const val CHANNEL = "dev.hyperios.app/bridge"
        private const val TAG = "HyperiOS-MainActivity"
    }

    private lateinit var repository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = SettingsRepository(this)
        XposedServiceBridge.init()
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call: MethodCall, result: MethodChannel.Result ->
                when (call.method) {
                    "getSettings" -> {
                        try {
                            val map = repository.getAllSettingsMap()
                            result.success(map)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error getting settings", t)
                            result.error("ERROR", t.message, null)
                        }
                    }
                    "setSetting" -> {
                        val key = call.argument<String>("key")
                        val value = call.argument<Any?>("value")
                        if (key != null) {
                            repository.setSettingDynamic(key, value)
                            result.success(true)
                        } else {
                            result.error("INVALID_ARG", "Key cannot be null", null)
                        }
                    }
                    "getModuleStatus" -> {
                        val status = mapOf(
                            "isBound" to XposedServiceBridge.isBound,
                            "scopedPackages" to XposedServiceBridge.getScopedPackages(this),
                            "versionName" to "1.0.0",
                            "sdkInt" to Build.VERSION.SDK_INT,
                            "isRootAvailable" to true
                        )
                        result.success(status)
                    }
                    "restartScopedApps" -> {
                        val packages = call.argument<List<String>>("packages") ?: emptyList()
                        RootUtils.restartScopedApps(this, packages)
                        result.success(true)
                    }
                    "restartPackage" -> {
                        val pkg = call.argument<String>("package") ?: ""
                        val label = call.argument<String>("label") ?: pkg
                        RootUtils.restartApp(this, pkg, label)
                        result.success(true)
                    }
                    "restartSystemUI" -> {
                        RootUtils.restartSystemUI(this)
                        result.success(true)
                    }
                    "restartLauncher" -> {
                        RootUtils.restartLauncher(this)
                        result.success(true)
                    }
                    "rebootDevice" -> {
                        RootUtils.rebootDevice(this)
                        result.success(true)
                    }
                    "exportSettings" -> {
                        val json = repository.exportSettingsToJson()
                        result.success(json)
                    }
                    "importSettings" -> {
                        val json = call.argument<String>("json") ?: ""
                        val ok = repository.importSettingsFromJson(json)
                        result.success(ok)
                    }
                    "resetToDefaults" -> {
                        repository.resetAllToDefaults()
                        result.success(true)
                    }
                    "setAppIconStyle" -> {
                        val styleStr = call.argument<String>("style") ?: "Flat"
                        val style = try {
                            AppIconStyle.valueOf(styleStr)
                        } catch (_: Throwable) {
                            AppIconStyle.Flat
                        }
                        repository.setAppIconStyle(style)
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }
}
