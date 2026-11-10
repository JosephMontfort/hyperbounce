import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';

class SafeModeScreen extends StatelessWidget {
  const SafeModeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;

    return AppScaffold(
      title: 'Safe Mode & Diagnostics',
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          const GlassSectionHeader('Safe Mode Overrides'),
          GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Global Safe Mode',
                subtitle:
                    'Disables all hooks immediately across every application',
                settingKey: 'prefs_key_global_safe_mode',
                onChanged: (val) => store.setSafeMode('global', val),
              ),
              GlassSwitchTile(
                title: 'System UI Safe Mode',
                subtitle: 'Bypasses hooks inside com.android.systemui',
                settingKey: 'prefs_key_systemui_safe_mode_enable',
                onChanged: (val) => store.setSafeMode('systemui', val),
              ),
              GlassSwitchTile(
                title: 'Launcher Safe Mode',
                subtitle: 'Bypasses hooks inside com.miui.home',
                settingKey: 'prefs_key_home_safe_mode_enable',
                onChanged: (val) => store.setSafeMode('launcher', val),
              ),
              GlassSwitchTile(
                title: 'Settings Safe Mode',
                subtitle: 'Bypasses hooks inside com.android.settings',
                settingKey: 'prefs_key_system_settings_safe_mode_enable',
                onChanged: (val) => store.setSafeMode('settings', val),
              ),
            ],
          ),
          const GlassSectionFooter(
            'When Safe Mode is enabled for a target package, HyperBounce unhooks all reflection engines and lets the stock OS process start natively without intervention.',
          ),

          const GlassSectionHeader('Crash Recovery Diagnostics'),
          GlassGroupedCard(
            children: [
              GlassActionTile(
                title: 'Reset All Crashing Aliases',
                subtitle:
                    'Clears automated crash loop protection counters and blacklists',
                titleColor: CupertinoColors.systemRed,
                onTap: () {
                  GlassDialog.show(
                    context: context,
                    title: 'Reset Crash Aliases?',
                    message:
                        'This will re-enable all previously disabled feature aliases that crashed repeatedly.',
                    actions: [
                      GlassDialogAction(
                        label: 'Cancel',
                        onPressed: () => Navigator.of(context).pop(),
                      ),
                      GlassDialogAction(
                        label: 'Reset',
                        isDestructive: true,
                        onPressed: () {
                          Navigator.of(context).pop();
                          store.clearAllSafeModes();
                        },
                      ),
                    ],
                  );
                },
              ),
            ],
          ),
        ],
      ),
    );
  }
}
