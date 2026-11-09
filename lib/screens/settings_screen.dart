import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../state/settings_store.dart';
import '../widgets/setting_tiles.dart';
import 'safemode/safe_mode_screen.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final appTheme = store.getInt('app_theme', 0);
        final componentStyle = store.getInt('component_style', 0);
        final miuixBottomBar = store.getBool('miuix_liquid_glass_bottom_bar', true);
        final appIconStyle = store.getInt('app_icon_style', 0);
        final hapticsEnabled = store.getBool('haptics_enabled', true);

        final hotReload = store.getBool('hot_reload_enabled', false);
        final settingsAppEntry = store.getBool('settings_app_entry_enabled', true);

        return ListView(
          padding: const EdgeInsets.only(top: 8, bottom: 120),
          children: [
            // --- Card 1: Appearance ---
            const GlassSectionHeader('Appearance'),
            GlassGroupedCard(
              children: [
                GlassDropdownTile(
                  title: 'App Theme',
                  options: const ['Follow System', 'Light', 'Dark'],
                  selectedIndex: appTheme.clamp(0, 2),
                  onSelect: (index) => store.setInt('app_theme', index),
                ),
                GlassDropdownTile(
                  title: 'Component Style',
                  options: const ['Liquid Glass', 'MIUIX'],
                  selectedIndex: componentStyle.clamp(0, 1),
                  onSelect: (index) => store.setInt('component_style', index),
                ),
                if (componentStyle == 1)
                  GlassSwitchTile(
                    title: 'Liquid Glass Bottom Bar',
                    subtitle: 'Use Liquid Glass style for the floating nav bar',
                    value: miuixBottomBar,
                    onChanged: (val) =>
                        store.setBool('miuix_liquid_glass_bottom_bar', val),
                  ),
                GlassDropdownTile(
                  title: 'App Icon Style',
                  options: const ['Monet', 'Flat'],
                  selectedIndex: appIconStyle.clamp(0, 1),
                  onSelect: (index) => store.setInt('app_icon_style', index),
                ),
                GlassSwitchTile(
                  title: 'Haptic Feedback',
                  value: hapticsEnabled,
                  onChanged: (val) => store.setBool('haptics_enabled', val),
                ),
              ],
            ),

            // --- Card 2: System Integration ---
            const GlassSectionHeader('System Integration'),
            GlassGroupedCard(
              children: [
                GlassSwitchTile(
                  title: 'Hot Reloading',
                  subtitle:
                      'Apply visual changes immediately without restarting apps. Turning off saves battery (apps require restart to apply changes).',
                  value: hotReload,
                  onChanged: (val) => store.setBool('hot_reload_enabled', val),
                ),
                GlassSwitchTile(
                  title: 'Add entry in Settings',
                  subtitle: 'Display HyperiOS in system Settings app',
                  value: settingsAppEntry,
                  onChanged: (val) =>
                      store.setBool('settings_app_entry_enabled', val),
                ),
              ],
            ),

            // --- Card 3: Backup & Restore ---
            const GlassSectionHeader('Backup & Restore'),
            GlassGroupedCard(
              children: [
                GlassActionTile(
                  title: 'Backup Module Settings',
                  subtitle: 'Export current settings configuration as JSON',
                  leading: const Icon(CupertinoIcons.arrow_down_doc,
                      color: CupertinoColors.activeBlue),
                  onTap: () async {
                    try {
                      final json = await store.exportJson();
                      await Clipboard.setData(ClipboardData(text: json));
                      if (context.mounted) {
                        showCupertinoDialog(
                          context: context,
                          builder: (ctx) => CupertinoAlertDialog(
                            title: const Text('Backup Exported'),
                            content: const Text(
                                'Settings JSON has been copied to your clipboard.'),
                            actions: [
                              CupertinoDialogAction(
                                child: const Text('OK'),
                                onPressed: () => Navigator.of(ctx).pop(),
                              ),
                            ],
                          ),
                        );
                      }
                    } catch (e) {
                      if (context.mounted) {
                        showCupertinoDialog(
                          context: context,
                          builder: (ctx) => CupertinoAlertDialog(
                            title: const Text('Backup Failed'),
                            content: Text('$e'),
                            actions: [
                              CupertinoDialogAction(
                                child: const Text('OK'),
                                onPressed: () => Navigator.of(ctx).pop(),
                              ),
                            ],
                          ),
                        );
                      }
                    }
                  },
                ),
                GlassActionTile(
                  title: 'Restore Module Settings',
                  subtitle: 'Import settings from clipboard JSON',
                  leading: const Icon(CupertinoIcons.arrow_up_doc,
                      color: CupertinoColors.activeGreen),
                  onTap: () async {
                    try {
                      final data = await Clipboard.getData(Clipboard.kTextPlain);
                      final text = data?.text ?? '';
                      if (text.isEmpty || !text.trim().startsWith('{')) {
                        throw Exception(
                            'Clipboard does not contain valid settings JSON.');
                      }
                      final ok = await store.importJson(text);
                      if (context.mounted) {
                        showCupertinoDialog(
                          context: context,
                          builder: (ctx) => CupertinoAlertDialog(
                            title: Text(ok ? 'Success' : 'Invalid Backup'),
                            content: Text(ok
                                ? 'Settings successfully restored.'
                                : 'Failed to apply settings JSON.'),
                            actions: [
                              CupertinoDialogAction(
                                child: const Text('OK'),
                                onPressed: () => Navigator.of(ctx).pop(),
                              ),
                            ],
                          ),
                        );
                      }
                    } catch (e) {
                      if (context.mounted) {
                        showCupertinoDialog(
                          context: context,
                          builder: (ctx) => CupertinoAlertDialog(
                            title: const Text('Restore Failed'),
                            content: Text('$e'),
                            actions: [
                              CupertinoDialogAction(
                                child: const Text('OK'),
                                onPressed: () => Navigator.of(ctx).pop(),
                              ),
                            ],
                          ),
                        );
                      }
                    }
                  },
                ),
              ],
            ),

            // --- Card 4: Diagnostics & Safety ---
            const GlassSectionHeader('Diagnostics & Safety'),
            GlassGroupedCard(
              children: [
                GlassNavTile(
                  leading: const Icon(CupertinoIcons.shield_lefthalf_fill,
                      color: CupertinoColors.systemRed),
                  title: 'Safe Mode & Crash Recovery',
                  subtitle: 'Module overrides, rescue loop mitigation',
                  onTap: () {
                    Navigator.of(context).push(
                      CupertinoPageRoute(
                          builder: (_) => const SafeModeScreen()),
                    );
                  },
                ),
              ],
            ),
          ],
        );
      },
    );
  }
}
