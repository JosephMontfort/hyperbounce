import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

/// Interop bridge connecting the Flutter frontend to the LSPosed Java/Kotlin backend.
/// Communicates through MethodChannel with MainActivity in dev.hyperios.app.ui.
class NativeBridge {
  static const MethodChannel _channel = MethodChannel('dev.hyperios.app/bridge');

  /// Fetches all active settings from the SharedPreferences and LSPosed remote preferences.
  static Future<Map<String, dynamic>> getSettings() async {
    try {
      final res = await _channel.invokeMethod<Map<dynamic, dynamic>>('getSettings');
      if (res == null) return {};
      return Map<String, dynamic>.from(res);
    } catch (e) {
      debugPrint('[NativeBridge] getSettings error: $e');
      return {};
    }
  }

  /// Fetches the native settings schema defining keys, types, defaults, and enum options.
  static Future<Map<String, dynamic>> getSchema() async {
    try {
      final res = await _channel.invokeMethod<Map<dynamic, dynamic>>('getSchema');
      if (res == null) return {};
      return Map<String, dynamic>.from(res);
    } catch (e) {
      debugPrint('[NativeBridge] getSchema error: $e');
      return {};
    }
  }

  /// Writes a setting value to SharedPreferences, LSPosed remote preferences,
  /// and optionally broadcasts hot-reload to hooked applications.
  static Future<bool> setSetting(String key, dynamic value, {bool broadcast = true}) async {
    try {
      await _channel.invokeMethod('setSetting', {
        'key': key,
        'value': value,
        'broadcast': broadcast,
      });
      return true;
    } catch (e) {
      debugPrint('[NativeBridge] setSetting error for $key: $e');
      return false;
    }
  }

  /// Retrieves module status: LSPosed service bound, scoped packages, root availability, SDK.
  static Future<Map<String, dynamic>> getModuleStatus() async {
    try {
      final res = await _channel.invokeMethod<Map<dynamic, dynamic>>('getModuleStatus');
      if (res == null) return {};
      return Map<String, dynamic>.from(res);
    } catch (e) {
      debugPrint('[NativeBridge] getModuleStatus error: $e');
      return {
        'isBound': false,
        'scopedPackages': <String>[],
        'versionName': '1.0.0',
        'sdkInt': 34,
        'isRootAvailable': false,
      };
    }
  }

  /// Restarts all or specific scoped packages via root privileges.
  static Future<void> restartScopedApps(List<String> packages) async {
    try {
      await _channel.invokeMethod('restartScopedApps', {'packages': packages});
    } catch (e) {
      debugPrint('[NativeBridge] restartScopedApps error: $e');
    }
  }

  /// Restarts a single package with user-visible Toast.
  static Future<void> restartPackage(String packageName, [String? label]) async {
    try {
      await _channel.invokeMethod('restartPackage', {
        'package': packageName,
        'label': label ?? packageName,
      });
    } catch (e) {
      debugPrint('[NativeBridge] restartPackage error: $e');
    }
  }

  /// Restarts System UI process (com.android.systemui).
  static Future<void> restartSystemUI() async {
    try {
      await _channel.invokeMethod('restartSystemUI');
    } catch (e) {
      debugPrint('[NativeBridge] restartSystemUI error: $e');
    }
  }

  /// Restarts System Launcher process (com.miui.home).
  static Future<void> restartLauncher() async {
    try {
      await _channel.invokeMethod('restartLauncher');
    } catch (e) {
      debugPrint('[NativeBridge] restartLauncher error: $e');
    }
  }

  /// Reboots the device via root.
  static Future<void> rebootDevice() async {
    try {
      await _channel.invokeMethod('rebootDevice');
    } catch (e) {
      debugPrint('[NativeBridge] rebootDevice error: $e');
    }
  }

  /// Exports all module preferences to a formatted JSON string.
  static Future<String> exportSettings() async {
    try {
      final res = await _channel.invokeMethod<String>('exportSettings');
      return res ?? '{}';
    } catch (e) {
      debugPrint('[NativeBridge] exportSettings error: $e');
      return '{}';
    }
  }

  /// Imports settings from JSON string and updates LSPosed remote preferences.
  static Future<bool> importSettings(String jsonStr) async {
    try {
      final res = await _channel.invokeMethod<bool>('importSettings', {'json': jsonStr});
      return res ?? false;
    } catch (e) {
      debugPrint('[NativeBridge] importSettings error: $e');
      return false;
    }
  }

  /// Resets all preferences back to factory defaults across local and remote storage.
  static Future<void> resetToDefaults() async {
    try {
      await _channel.invokeMethod('resetToDefaults');
    } catch (e) {
      debugPrint('[NativeBridge] resetToDefaults error: $e');
    }
  }

  /// Sets launcher icon style (Flat vs Monet) via PackageManager activity-alias switching.
  static Future<void> setAppIconStyle(String style) async {
    try {
      await _channel.invokeMethod('setAppIconStyle', {'style': style});
    } catch (e) {
      debugPrint('[NativeBridge] setAppIconStyle error: $e');
    }
  }

  /// Safe Mode bridge methods
  static Future<Map<String, dynamic>> getSafeModeStatus() async {
    try {
      final res = await _channel.invokeMethod<Map<dynamic, dynamic>>('getSafeModeStatus');
      if (res == null) return {};
      return Map<String, dynamic>.from(res);
    } catch (e) {
      debugPrint('[NativeBridge] getSafeModeStatus error: $e');
      return {};
    }
  }

  static Future<void> setSafeMode(String scope, bool enabled) async {
    try {
      await _channel.invokeMethod('setSafeMode', {
        'scope': scope,
        'enabled': enabled,
      });
    } catch (e) {
      debugPrint('[NativeBridge] setSafeMode error: $e');
    }
  }

  static Future<void> clearCrashingAliases() async {
    try {
      await _channel.invokeMethod('clearCrashingAliases');
    } catch (e) {
      debugPrint('[NativeBridge] clearCrashingAliases error: $e');
    }
  }

  static Future<String?> buildRingtoneModule() async {
    try {
      return await _channel.invokeMethod<String>('buildRingtoneModule');
    } catch (e) {
      debugPrint('[NativeBridge] buildRingtoneModule error: $e');
      return null;
    }
  }
}
