import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../state/settings_store.dart';
import '../bridge/native_bridge.dart';
import '../widgets/setting_tiles.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  final _store = SettingsStore.instance;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: _store,
      builder: (context, _) {
        final isBound = _store.isBound;
        final hotReload = _store.getBool('hot_reload_enabled', false);
        final settingsAppEntry = _store.getBool('settings_app_entry_enabled', false);
        final iconStyle = _store.getString('app_icon_style', 'Flat');
        final scoped = _store.scopedPackages;

        return ListView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
          children: [
            const GlassSectionHeader('Module Status'),
            GlassGroupedSection(
              children: [
                GlassListTile(
                  leading: Icon(
                    isBound ? CupertinoIcons.checkmark_seal_fill : CupertinoIcons.exclamationmark_shield_fill,
                    color: isBound ? CupertinoColors.activeGreen : CupertinoColors.systemRed,
                  ),
                  title: const Text('LSPosed Framework'),
                  subtitle: Text(isBound
                      ? 'Remote Preferences Service Active'
                      : 'Service Unbound (Local Preferences Mode)'),
                  trailing: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                    decoration: BoxDecoration(
                      color: (isBound ? CupertinoColors.activeGreen : CupertinoColors.systemRed)
                          .withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      isBound ? 'BOUND' : 'INACTIVE',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.w700,
                        color: isBound ? CupertinoColors.activeGreen : CupertinoColors.systemRed,
                      ),
                    ),
                  ),
                ),
                GlassListTile(
                  leading: const Icon(CupertinoIcons.square_stack_3d_up, color: CupertinoColors.activeBlue),
                  title: const Text('Active Scope'),
                  subtitle: Text(scoped.isNotEmpty
                      ? '${scoped.length} scoped packages attached'
                      : 'No scope configured in LSPosed manager'),
                ),
              ],
            ),

            const GlassSectionHeader('Preferences Synchronization'),
            GlassGroupedSection(
              children: [
                GlassSwitchTile(
                  leading: const Icon(CupertinoIcons.bolt_horizontal_fill, color: CupertinoColors.systemOrange),
                  title: 'Instant Hot Reload',
                  subtitle: 'Broadcast setting edits directly to active processes',
                  value: hotReload,
                  onChanged: (v) => _store.setBool('hot_reload_enabled', v),
                ),
                GlassSwitchTile(
                  leading: const Icon(CupertinoIcons.slider_horizontal_below_rectangle, color: CupertinoColors.systemPurple),
                  title: 'Settings App Entry',
                  subtitle: 'Embed Hyper iOS launcher directly in System Settings',
                  value: settingsAppEntry,
                  onChanged: (v) => _store.setBool('settings_app_entry_enabled', v),
                ),
              ],
            ),

            const GlassSectionHeader('Appearance & Launcher Icon'),
            GlassGroupedSection(
              children: [
                GlassListTile(
                  leading: const Icon(CupertinoIcons.app_badge, color: CupertinoColors.systemIndigo),
                  title: const Text('App Launcher Icon'),
                  subtitle: Text('Current: $iconStyle'),
                  trailing: CupertinoSlidingSegmentedControl<String>(
                    groupValue: iconStyle,
                    children: const {
                      'Flat': Padding(
                        padding: EdgeInsets.symmetric(horizontal: 10),
                        child: Text('Flat', style: TextStyle(fontSize: 13)),
                      ),
                      'Monet': Padding(
                        padding: EdgeInsets.symmetric(horizontal: 10),
                        child: Text('Monet', style: TextStyle(fontSize: 13)),
                      ),
                    },
                    onValueChanged: (val) {
                      if (val != null) {
                        _store.setString('app_icon_style', val);
                        NativeBridge.setAppIconStyle(val);
                      }
                    },
                  ),
                ),
              ],
            ),

            const GlassSectionHeader('Backup & Restore'),
            GlassGroupedSection(
              children: [
                GlassListTile(
                  leading: const Icon(CupertinoIcons.square_arrow_up, color: CupertinoColors.activeBlue),
                  title: const Text('Export Settings (JSON)'),
                  subtitle: const Text('Copy full JSON snapshot to clipboard'),
                  onTap: () async {
                    final json = await _store.exportJson();
                    await Clipboard.setData(ClipboardData(text: json));
                    if (context.mounted) {
                      showCupertinoDialog(
                        context: context,
                        builder: (_) => CupertinoAlertDialog(
                          title: const Text('Exported'),
                          content: const Text('Settings JSON copied to clipboard.'),
                          actions: [
                            CupertinoDialogAction(
                              child: const Text('OK'),
                              onPressed: () => Navigator.pop(context),
                            ),
                          ],
                        ),
                      );
                    }
                  },
                ),
                GlassListTile(
                  leading: const Icon(CupertinoIcons.square_arrow_down, color: CupertinoColors.activeGreen),
                  title: const Text('Import Settings (JSON)'),
                  subtitle: const Text('Paste and restore JSON snapshot from clipboard'),
                  onTap: () async {
                    final data = await Clipboard.getData('text/plain');
                    if (data?.text == null || data!.text!.isEmpty) {
                      if (context.mounted) {
                        showCupertinoDialog(
                          context: context,
                          builder: (_) => CupertinoAlertDialog(
                            title: const Text('Clipboard Empty'),
                            content: const Text('Please copy a valid settings JSON before importing.'),
                            actions: [
                              CupertinoDialogAction(
                                child: const Text('OK'),
                                onPressed: () => Navigator.pop(context),
                              ),
                            ],
                          ),
                        );
                      }
                      return;
                    }
                    final ok = await _store.importJson(data.text!);
                    if (context.mounted) {
                      showCupertinoDialog(
                        context: context,
                        builder: (_) => CupertinoAlertDialog(
                          title: Text(ok ? 'Success' : 'Import Failed'),
                          content: Text(ok
                              ? 'Settings restored successfully!'
                              : 'Could not parse JSON. Check format.'),
                          actions: [
                            CupertinoDialogAction(
                              child: const Text('OK'),
                              onPressed: () => Navigator.pop(context),
                            ),
                          ],
                        ),
                      );
                    }
                  },
                ),
                GlassListTile(
                  leading: const Icon(CupertinoIcons.arrow_counterclockwise, color: CupertinoColors.systemRed),
                  title: const Text('Reset All Settings to Defaults'),
                  subtitle: const Text('Restore factory values across local and remote prefs'),
                  onTap: () {
                    showCupertinoDialog(
                      context: context,
                      builder: (_) => CupertinoAlertDialog(
                        title: const Text('Reset to Defaults?'),
                        content: const Text('This will reset all your tuning and preferences to initial values.'),
                        actions: [
                          CupertinoDialogAction(
                            isDestructiveAction: true,
                            child: const Text('Reset'),
                            onPressed: () async {
                              Navigator.pop(context);
                              await _store.resetToDefaults();
                            },
                          ),
                          CupertinoDialogAction(
                            isDefaultAction: true,
                            child: const Text('Cancel'),
                            onPressed: () => Navigator.pop(context),
                          ),
                        ],
                      ),
                    );
                  },
                ),
              ],
            ),
            const SizedBox(height: 100),
          ],
        );
      },
    );
  }
}
