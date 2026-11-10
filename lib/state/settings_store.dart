import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/scheduler.dart';
import '../bridge/native_bridge.dart';

/// Central reactive state store for Hyper iOS settings and module status.
class SettingsStore extends ChangeNotifier {
  static final SettingsStore instance = SettingsStore._internal();
  factory SettingsStore() => instance;

  SettingsStore._internal();

  Map<String, dynamic> _settings = {};
  Map<String, dynamic> _schema = {};
  Map<String, dynamic> _moduleStatus = {
    'isBound': false,
    'scopedPackages': <String>[],
    'versionName': '1.0.0',
    'sdkInt': 34,
    'isRootAvailable': false,
    'deviceModel': 'Device',
    'hyperOsVersion': 'HyperOS',
    'androidRelease': '14',
  };
  Map<String, dynamic> _safeModeStatus = {};

  final Map<String, ValueNotifier<dynamic>> _keyNotifiers = {};
  final Map<String, Timer> _throttles = {};
  final Map<String, dynamic> _pendingWrites = {};

  bool _initialized = false;
  bool get isInitialized => _initialized;

  Map<String, dynamic> get schema => _schema;
  bool get isBound => _moduleStatus['isBound'] as bool? ?? false;
  List<String> get scopedPackages =>
      List<String>.from(_moduleStatus['scopedPackages'] as List? ?? []);
  String get versionName => _moduleStatus['versionName'] as String? ?? '1.0.0';
  int get sdkInt => _moduleStatus['sdkInt'] as int? ?? 34;
  bool get isRootAvailable => _moduleStatus['isRootAvailable'] as bool? ?? false;
  String get deviceModel => _moduleStatus['deviceModel'] as String? ?? 'Device';
  String get hyperOsVersion => _moduleStatus['hyperOsVersion'] as String? ?? 'HyperOS';
  String get androidRelease => _moduleStatus['androidRelease'] as String? ?? '14';

  List<String> get crashingAliases =>
      List<String>.from(_safeModeStatus['crashingAliases'] as List? ?? []);

  /// Loads initial settings, schema, and status from native backend.
  Future<void> initialize() async {
    final results = await Future.wait([
      NativeBridge.getSettings(),
      NativeBridge.getModuleStatus(),
      NativeBridge.getSchema(),
      NativeBridge.getSafeModeStatus(),
    ]);

    _settings = Map<String, dynamic>.from(results[0]);
    _moduleStatus = Map<String, dynamic>.from(results[1]);
    _schema = Map<String, dynamic>.from(results[2]);
    _safeModeStatus = Map<String, dynamic>.from(results[3]);

    // Populate key notifiers with initial values
    for (final entry in _settings.entries) {
      if (_keyNotifiers.containsKey(entry.key)) {
        _keyNotifiers[entry.key]!.value = entry.value;
      }
    }

    _initialized = true;
    notifyListeners();
  }

  /// Refreshes all values from the backend.
  Future<void> refresh() async {
    final results = await Future.wait([
      NativeBridge.getSettings(),
      NativeBridge.getModuleStatus(),
      NativeBridge.getSchema(),
      NativeBridge.getSafeModeStatus(),
    ]);

    _settings = Map<String, dynamic>.from(results[0]);
    _moduleStatus = Map<String, dynamic>.from(results[1]);
    _schema = Map<String, dynamic>.from(results[2]);
    _safeModeStatus = Map<String, dynamic>.from(results[3]);

    for (final entry in _settings.entries) {
      if (_keyNotifiers.containsKey(entry.key)) {
        _keyNotifiers[entry.key]!.value = entry.value;
      }
    }

    notifyListeners();
  }

  /// Listen to a single setting key's value without rebuilding the whole screen.
  ValueListenable<dynamic> listenKey(String key) {
    return _keyNotifiers.putIfAbsent(
      key,
      () => ValueNotifier<dynamic>(_settings[key]),
    );
  }

  void _assertKnownKey(String key, String typeName, dynamic value) {
    if (kDebugMode && _schema.isNotEmpty && !_schema.containsKey(key)) {
      debugPrint('[SettingsStore WARN] Unknown key written: \'$key\' (type: $typeName, value: $value)');
    }
  }

  void _setInternal(String key, dynamic value) {
    if (_settings[key] == value) return;
    _settings[key] = value;
    final notifier = _keyNotifiers[key];
    if (notifier != null) {
      notifier.value = value;
    }
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

  // --- Reactive Setters with optimistic updates and per-key throttling -------

  void setBool(String key, bool value) {
    _assertKnownKey(key, 'boolean', value);
    if (kDebugMode) {
      debugPrint('[SettingsStore] setBool: key=$key, value=$value');
    }
    _setInternal(key, value);
    NativeBridge.setSetting(key, value, broadcast: true);
  }

  /// Throttled slider setter (at most one native IPC write per 50ms per key during drags).
  void setDoubleThrottled(String key, double value) {
    final type = _schema[key]?['type'];
    final isInt = type == 'int' || type == 'long';
    final dynamic storeVal = isInt ? value.round() : value;
    _assertKnownKey(key, isInt ? (type ?? 'int') : 'float', storeVal);
    _setInternal(key, storeVal);

    if (_throttles[key] == null || !_throttles[key]!.isActive) {
      NativeBridge.setSetting(key, storeVal, broadcast: false);
      _throttles[key] = Timer(const Duration(milliseconds: 50), () {
        if (_pendingWrites.containsKey(key)) {
          final pending = _pendingWrites.remove(key);
          final dynamic pendingVal =
              isInt ? (pending is num ? pending.round() : pending) : pending;
          NativeBridge.setSetting(key, pendingVal, broadcast: false);
        }
      });
    } else {
      _pendingWrites[key] = storeVal;
    }
  }

  /// Flushes final slider value immediately upon drag release.
  void flushDouble(String key, double value) {
    _throttles[key]?.cancel();
    _throttles.remove(key);
    _pendingWrites.remove(key);
    final type = _schema[key]?['type'];
    final isInt = type == 'int' || type == 'long';
    final dynamic storeVal = isInt ? value.round() : value;
    _assertKnownKey(key, isInt ? (type ?? 'int') : 'float', storeVal);
    if (kDebugMode) {
      debugPrint('[SettingsStore] flushDouble: key=$key, value=$storeVal');
    }
    _setInternal(key, storeVal);
    NativeBridge.setSetting(key, storeVal, broadcast: true);
  }

  void setDouble(String key, double value) {
    flushDouble(key, value);
  }

  void setInt(String key, int value) {
    _assertKnownKey(key, 'int', value);
    if (kDebugMode) {
      debugPrint('[SettingsStore] setInt: key=$key, value=$value');
    }
    _setInternal(key, value);
    NativeBridge.setSetting(key, value, broadcast: true);
  }

  void setString(String key, String value) {
    _assertKnownKey(key, 'string/enum', value);
    if (kDebugMode) {
      debugPrint('[SettingsStore] setString: key=$key, value=$value');
    }
    _setInternal(key, value);
    NativeBridge.setSetting(key, value, broadcast: true);
  }

  // --- Safe Mode operations --------------------------------------------------

  Future<void> setSafeMode(String scope, bool enabled) async {
    final key = switch (scope) {
      'global' => 'prefs_key_global_safe_mode',
      'systemui' => 'prefs_key_systemui_safe_mode_enable',
      'launcher' => 'prefs_key_home_safe_mode_enable',
      'settings' => 'prefs_key_system_settings_safe_mode_enable',
      _ => null,
    };
    if (key != null) {
      _setInternal(key, enabled);
    }
    await NativeBridge.setSafeMode(scope, enabled);
    _safeModeStatus = await NativeBridge.getSafeModeStatus();
    notifyListeners();
  }

  Future<void> clearAllSafeModes() async {
    await NativeBridge.clearCrashingAliases();
    _setInternal('prefs_key_global_safe_mode', false);
    _setInternal('prefs_key_systemui_safe_mode_enable', false);
    _setInternal('prefs_key_home_safe_mode_enable', false);
    _setInternal('prefs_key_system_settings_safe_mode_enable', false);
    _safeModeStatus = await NativeBridge.getSafeModeStatus();
    notifyListeners();
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

/// Helper to monitor frame timings and catch frame drops during active slider drags.
class FrameTimingMonitor {
  static bool _active = false;
  static void start() {
    if (_active) return;
    _active = true;
    SchedulerBinding.instance.addTimingsCallback(_onTimings);
  }

  static void stop() {
    if (!_active) return;
    _active = false;
    SchedulerBinding.instance.removeTimingsCallback(_onTimings);
  }

  static void _onTimings(List<FrameTiming> timings) {
    if (!kDebugMode) return;
    for (final timing in timings) {
      final totalMs = timing.totalSpan.inMicroseconds / 1000.0;
      if (totalMs > 16.0) {
        final buildMs = timing.buildDuration.inMicroseconds / 1000.0;
        final rasterMs = timing.rasterDuration.inMicroseconds / 1000.0;
        debugPrint(
          '[FrameTiming WARN] Frame jank during slider drag: total=${totalMs.toStringAsFixed(1)}ms '
          '(build=${buildMs.toStringAsFixed(1)}ms, raster=${rasterMs.toStringAsFixed(1)}ms)',
        );
      }
    }
  }
}
