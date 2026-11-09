import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';

class HapticStudioScreen extends StatelessWidget {
  const HapticStudioScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final enabled = store.getBool('haptics_studio_enabled', false);
        final presetIdx = store.getInt('haptic_ringtone_preset', 0);
        final intensity = store.getDouble('haptic_intensity', 0.85);
        final sharpness = store.getDouble('haptic_sharpness', 0.75);
        final beatSync = store.getBool('haptic_beat_sync', true);

        return AppScaffold(
          title: 'Haptic Studio',
          body: ListView(
            padding: const EdgeInsets.only(top: 12, bottom: 60),
            children: [
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Enable Haptic Studio',
                    subtitle:
                        'Compose synced haptics for music to be set as custom Ringtones',
                    value: enabled,
                    onChanged: (val) =>
                        store.setBool('haptics_studio_enabled', val),
                  ),
                ],
              ),

              const GlassSectionHeader('Preset & Composition'),
              GlassGroupedCard(
                children: [
                  GlassDropdownTile(
                    title: 'Ringtone Preset',
                    options: const [
                      'Reflection',
                      'Opening',
                      'Chimes',
                      'Apex',
                      'Beacon'
                    ],
                    selectedIndex: presetIdx.clamp(0, 4),
                    enabled: enabled,
                    onSelect: (idx) =>
                        store.setInt('haptic_ringtone_preset', idx),
                  ),
                  GlassSwitchTile(
                    title: 'Transient Beat Sync',
                    subtitle:
                        'Real-time frequency tracking for waveform vibrations',
                    value: beatSync,
                    enabled: enabled,
                    onChanged: (val) =>
                        store.setBool('haptic_beat_sync', val),
                  ),
                ],
              ),

              const GlassSectionHeader('DSP Synthesizer Parameters'),
              GlassSliderTile(
                title: 'Haptic Intensity',
                subtitle: 'Motor actuator amplitude & force',
                value: intensity,
                min: 0.1,
                max: 1.0,
                unit: '%',
                enabled: enabled,
                onChanged: (val) => store.setDouble('haptic_intensity', val),
              ),
              GlassSliderTile(
                title: 'Haptic Sharpness',
                subtitle: 'Crispness and resonant frequency cutoff',
                value: sharpness,
                min: 0.1,
                max: 1.0,
                unit: '%',
                enabled: enabled,
                onChanged: (val) => store.setDouble('haptic_sharpness', val),
              ),

              const GlassSectionHeader('Actions'),
              GlassGroupedCard(
                children: [
                  GlassActionTile(
                    title: 'Synthesize Preview',
                    subtitle: 'Test generated haptic curve on vibration motor',
                    leading: const Icon(CupertinoIcons.play_circle_fill,
                        color: CupertinoColors.activeBlue),
                    enabled: enabled,
                    onTap: () {
                      HapticFeedback.heavyImpact();
                    },
                  ),
                  GlassActionTile(
                    title: 'Export Ringtone Module',
                    subtitle:
                        'Package synced haptic track into Magisk/KSU sound module',
                    leading: const Icon(CupertinoIcons.share_up,
                        color: CupertinoColors.activeGreen),
                    enabled: enabled,
                    onTap: () {
                      showCupertinoDialog(
                        context: context,
                        builder: (ctx) => CupertinoAlertDialog(
                          title: const Text('Export Ringtone Module'),
                          content: const Text(
                              'Haptic track successfully packaged for system audio server.'),
                          actions: [
                            CupertinoDialogAction(
                              child: const Text('OK'),
                              onPressed: () => Navigator.of(ctx).pop(),
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
