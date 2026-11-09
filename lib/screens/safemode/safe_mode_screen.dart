import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../../state/settings_store.dart';
import '../../bridge/native_bridge.dart';

class SafeModeScreen extends StatelessWidget {
  const SafeModeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;

    return AppScaffold(
      title: 'Safe Mode & Diagnostics',
      showTopMenu: false,
      body: ListenableBuilder(
        listenable: store,
        builder: (context, _) {
          final bypass = store.getBool('safemode_bypass_emergency', false);

          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              const GlassSectionHeader('Crash Loop Protection'),
              GlassGroupedSection(
                children: [
                  const GlassListTile(
                    leading: Icon(CupertinoIcons.shield_fill, color: CupertinoColors.activeGreen),
                    title: Text('Automated Recovery Guardian'),
                    subtitle: Text('Monitors SystemUI boot crashes. If 5 consecutive crashes happen within 5s, all hooks self-disable to prevent bootloops.'),
                  ),
                  GlassSwitchTile(
                    title: 'Emergency Bypass All Hooks',
                    subtitle: 'Forcefully turn off all hook injections globally',
                    value: bypass,
                    onChanged: (v) => store.setBool('safemode_bypass_emergency', v),
                  ),
                ],
              ),

              const GlassSectionHeader('System Rescue Actions'),
              GlassGroupedSection(
                children: [
                  GlassListTile(
                    leading: const Icon(CupertinoIcons.refresh_circled, color: CupertinoColors.activeBlue),
                    title: const Text('Clear Safe Mode Flag'),
                    subtitle: const Text('Reset crash loop counter and restore hook injections'),
                    onTap: () {
                      store.setBool('safemode_bypass_emergency', false);
                      showCupertinoDialog(
                        context: context,
                        builder: (_) => CupertinoAlertDialog(
                          title: const Text('Flags Cleared'),
                          content: const Text('Crash loop state reset to 0.'),
                          actions: [
                            CupertinoDialogAction(
                              child: const Text('OK'),
                              onPressed: () => Navigator.pop(context),
                            ),
                          ],
                        ),
                      );
                    },
                  ),
                  GlassListTile(
                    leading: const Icon(CupertinoIcons.arrow_clockwise_circle, color: CupertinoColors.systemOrange),
                    title: const Text('Soft Reboot System UI'),
                    subtitle: const Text('Restart SystemUI to test clean startup'),
                    onTap: () => NativeBridge.restartSystemUI(),
                  ),
                  GlassListTile(
                    leading: const Icon(CupertinoIcons.power, color: CupertinoColors.systemRed),
                    title: const Text('Reboot Device'),
                    subtitle: const Text('Execute root reboot command'),
                    onTap: () => NativeBridge.rebootDevice(),
                  ),
                ],
              ),
              const SizedBox(height: 100),
            ],
          );
        },
      ),
    );
  }
}
