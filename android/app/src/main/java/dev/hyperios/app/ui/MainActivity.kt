package dev.hyperios.app.ui

import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
                        val broadcast = call.argument<Boolean>("broadcast") ?: true
                        if (key != null) {
                            val schema = repository.getSchema()
                            if (!schema.containsKey(key)) {
                                Log.w(TAG, "[WARN] Unknown setting key sent from Dart: '$key' (received type: ${value?.javaClass?.simpleName}, value: $value)")
                            } else {
                                Log.d(TAG, "[setSetting] key='$key', type=${value?.javaClass?.simpleName}, value=$value, broadcast=$broadcast")
                            }
                            repository.setSettingDynamic(key, value, broadcast)
                            result.success(true)
                        } else {
                            result.error("INVALID_ARG", "Key cannot be null", null)
                        }
                    }
                    "getSchema" -> {
                        try {
                            val schema = repository.getSchema()
                            result.success(schema)
                        } catch (t: Throwable) {
                            Log.e(TAG, "Error getting schema", t)
                            result.error("ERROR", t.message, null)
                        }
                    }
                    "getModuleStatus" -> {
                        val pm = packageManager
                        val pInfo = try {
                            pm.getPackageInfo(packageName, 0)
                        } catch (_: Throwable) { null }
                        val vName = pInfo?.versionName ?: "1.0.0"
                        val isRoot = RootUtils.isRootAvailable()
                        val marketName = dev.hyperios.app.safemode.SystemPropertiesHelper.get("ro.product.marketname", "").trim()
                        val model = dev.hyperios.app.safemode.SystemPropertiesHelper.get("ro.product.model", "").trim()
                        val devModel = if (marketName.isNotEmpty()) marketName else if (model.isNotEmpty()) model else Build.MODEL

                        val miOs = dev.hyperios.app.safemode.SystemPropertiesHelper.get("ro.mi.os.version.name", "").trim()
                        val miui = dev.hyperios.app.safemode.SystemPropertiesHelper.get("ro.miui.ui.version.name", "").trim()
                        val hyperOs = if (miOs.isNotEmpty()) miOs else if (miui.isNotEmpty()) miui else "HyperOS"

                        val status = mapOf(
                            "isBound" to XposedServiceBridge.isBound,
                            "scopedPackages" to XposedServiceBridge.getScopedPackages(this),
                            "versionName" to vName,
                            "sdkInt" to Build.VERSION.SDK_INT,
                            "isRootAvailable" to isRoot,
                            "deviceModel" to devModel,
                            "hyperOsVersion" to hyperOs,
                            "androidRelease" to Build.VERSION.RELEASE
                        )
                        result.success(status)
                    }
                    "getSafeModeStatus" -> {
                        val status = mapOf(
                            "global" to repository.safeMode.globalSafeMode,
                            "systemui" to repository.safeMode.systemUiSafeMode,
                            "launcher" to repository.safeMode.launcherSafeMode,
                            "settings" to repository.safeMode.settingsSafeMode,
                            "crashingAliases" to repository.safeMode.getCrashingAliases().toList()
                        )
                        result.success(status)
                    }
                    "setSafeMode" -> {
                        val scope = call.argument<String>("scope")
                        val enabled = call.argument<Boolean>("enabled") ?: false
                        when (scope) {
                            "global" -> repository.safeMode.setGlobalSafeMode(enabled)
                            "systemui" -> repository.safeMode.setSystemUiSafeMode(enabled)
                            "launcher" -> repository.safeMode.setLauncherSafeMode(enabled)
                            "settings" -> repository.safeMode.setSettingsSafeMode(enabled)
                            else -> Log.w(TAG, "Unknown safe mode scope: $scope")
                        }
                        result.success(true)
                    }
                    "clearCrashingAliases" -> {
                        dev.hyperios.app.safemode.SafeModeHandler.clearAllSafeModes()
                        result.success(true)
                    }
                    "buildRingtoneModule" -> {
                        lifecycleScope.launch {
                            try {
                                val presets = dev.hyperios.app.haptics.ringtone.HapticPresetStorage.listPresets(this@MainActivity)
                                val buildResult = dev.hyperios.app.haptics.ringtone.RingtoneModuleBuilder.buildModule(this@MainActivity, presets)
                                if (buildResult.success && buildResult.zipFile != null) {
                                    result.success(buildResult.zipFile.absolutePath)
                                } else {
                                    result.error("BUILD_FAILED", buildResult.errorMessage ?: "Build failed", null)
                                }
                            } catch (t: Throwable) {
                                Log.e(TAG, "Error building ringtone module", t)
                                result.error("ERROR", t.message, null)
                            }
                        }
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
