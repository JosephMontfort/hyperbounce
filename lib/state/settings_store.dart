import 'package:flutter/foundation.dart';
import '../bridge/native_bridge.dart';

/// Central reactive state store for Hyper iOS settings and module status.
class SettingsStore extends ChangeNotifier {
  static final SettingsStore instance = SettingsStore._internal();
  factory SettingsStore() => instance;

  SettingsStore._internal();

  Map<String, dynamic> _settings = {};
  Map<String, dynamic> _moduleStatus = {
    'isBound': false,
    'scopedPackages': <String>[],
    'versionName': '1.0.0',
    'sdkInt': 34,
    'isRootAvailable': false,
  };

  bool _initialized = false;
  bool get isInitialized => _initialized;

  bool get isBound => _moduleStatus['isBound'] as bool? ?? false;
  List<String> get scopedPackages =>
      List<String>.from(_moduleStatus['scopedPackages'] as List? ?? []);
  String get versionName => _moduleStatus['versionName'] as String? ?? '1.0.0';
  int get sdkInt => _moduleStatus['sdkInt'] as int? ?? 34;
  bool get isRootAvailable => _moduleStatus['isRootAvailable'] as bool? ?? false;

  /// Loads initial settings and status from native backend.
  Future<void> initialize() async {
    final results = await Future.wait([
      NativeBridge.getSettings(),
      NativeBridge.getModuleStatus(),
    ]);

    _settings = Map<String, dynamic>.from(results[0]);
    _moduleStatus = Map<String, dynamic>.from(results[1]);
    _initialized = true;
    notifyListeners();
  }

  /// Refreshes all values from the backend.
  Future<void> refresh() async {
    final results = await Future.wait([
      NativeBridge.getSettings(),
      NativeBridge.getModuleStatus(),
    ]);

    _settings = Map<String, dynamic>.from(results[0]);
    _moduleStatus = Map<String, dynamic>.from(results[1]);
    notifyListeners();
  }

  // --- Type-safe Getters -----------------------------------------------------

  bool getBool(String key, [bool defaultValue = false]) {
    final v = _settings[key];
    if (v is bool) return v;
    if (v is num) return v != 0;
    if (v is String) return v.toLowerCase() == 'true';
    return defaultValue;
  }

  double getDouble(String key, [double defaultValue = 0.0]) {
    final v = _settings[key];
    if (v is num) return v.toDouble();
    if (v is String) return double.tryParse(v) ?? defaultValue;
    return defaultValue;
  }

  int getInt(String key, [int defaultValue = 0]) {
    final v = _settings[key];
    if (v is num) return v.toInt();
    if (v is String) return int.tryParse(v) ?? defaultValue;
    return defaultValue;
  }

  String getString(String key, [String defaultValue = '']) {
    final v = _settings[key];
    if (v != null) return v.toString();
    return defaultValue;
  }

  // --- Reactive Setters with optimistic updates ------------------------------

  void setBool(String key, bool value) {
    if (_settings[key] == value) return;
    _settings[key] = value;
    notifyListeners();
    NativeBridge.setSetting(key, value);
  }

  void setDouble(String key, double value) {
    if (_settings[key] == value) return;
    _settings[key] = value;
    notifyListeners();
    NativeBridge.setSetting(key, value);
  }

  void setInt(String key, int value) {
    if (_settings[key] == value) return;
    _settings[key] = value;
    notifyListeners();
    NativeBridge.setSetting(key, value);
  }

  void setString(String key, String value) {
    if (_settings[key] == value) return;
    _settings[key] = value;
    notifyListeners();
    NativeBridge.setSetting(key, value);
  }

  /// Batch update or reset to defaults.
  Future<void> resetToDefaults() async {
    await NativeBridge.resetToDefaults();
    await refresh();
  }

  /// Exports current settings as JSON.
  Future<String> exportJson() async {
    return await NativeBridge.exportSettings();
  }

  /// Imports settings from JSON.
  Future<bool> importJson(String jsonStr) async {
    final ok = await NativeBridge.importSettings(jsonStr);
    if (ok) {
      await refresh();
    }
    return ok;
  }
}
