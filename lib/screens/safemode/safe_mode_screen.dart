import 'package:flutter/cupertino.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';

class SafeModeScreen extends StatelessWidget {
  const SafeModeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final global = store.getBool('safe_mode_global', false);
        final systemUi = store.getBool('safe_mode_systemui', false);
        final launcher = store.getBool('safe_mode_launcher', false);
        final settings = store.getBool('safe_mode_settings', false);

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
                    value: global,
                    onChanged: (val) =>
                        store.setBool('safe_mode_global', val),
                  ),
                  GlassSwitchTile(
                    title: 'System UI Safe Mode',
                    subtitle: 'Bypasses hooks inside com.android.systemui',
                    value: systemUi,
                    onChanged: (val) =>
                        store.setBool('safe_mode_systemui', val),
                  ),
                  GlassSwitchTile(
                    title: 'Launcher Safe Mode',
                    subtitle: 'Bypasses hooks inside com.miui.home',
                    value: launcher,
                    onChanged: (val) =>
                        store.setBool('safe_mode_launcher', val),
                  ),
                  GlassSwitchTile(
                    title: 'Settings Safe Mode',
                    subtitle: 'Bypasses hooks inside com.android.settings',
                    value: settings,
                    onChanged: (val) =>
                        store.setBool('safe_mode_settings', val),
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
                      showCupertinoDialog(
                        context: context,
                        builder: (ctx) => CupertinoAlertDialog(
                          title: const Text('Reset Crash Aliases?'),
                          content: const Text(
                              'This will re-enable all previously disabled feature aliases that crashed repeatedly.'),
                          actions: [
                            CupertinoDialogAction(
                              child: const Text('Cancel'),
                              onPressed: () => Navigator.of(ctx).pop(),
                            ),
                            CupertinoDialogAction(
                              isDestructiveAction: true,
                              child: const Text('Reset'),
                              onPressed: () {
                                Navigator.of(ctx).pop();
                                store.setString('crashing_aliases', '');
                              },
                            ),
                          ],
                        ),
                      );
                    },
                  ),
                ],
              ),
            ],
          ),
        );
      },
    );
  }
}
