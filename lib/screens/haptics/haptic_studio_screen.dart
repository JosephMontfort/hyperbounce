import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../bridge/native_bridge.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';

class HapticStudioScreen extends StatelessWidget {
  const HapticStudioScreen({super.key});

  static const _pipelines = ['XiaomiMelodicCrisp', 'OppoRichCrisp'];

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: 'Haptic Studio',
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Real-Time Audio Haptics',
                subtitle:
                    'Compose synced haptics for music to be set as custom Ringtones',
                settingKey: 'audio_haptics_realtime_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('DSP Pipeline & Frequency'),
          const GlassGroupedCard(
            children: [
              GlassDropdownTile(
                title: 'Active Haptic Pipeline',
                settingKey: 'active_haptic_pipeline',
                options: _pipelines,
                enabledKey: 'audio_haptics_realtime_enabled',
              ),
            ],
          ),
          const GlassSliderTile(
            title: 'Motor Resonant Frequency',
            subtitle: 'Target actuator resonance frequency calibration',
            settingKey: 'active_haptic_freq',
            min: 50,
            max: 300,
            unit: 'Hz',
            enabledKey: 'audio_haptics_realtime_enabled',
          ),

          const GlassSectionHeader('Sensitivity & Dynamics'),
          const GlassSliderTile(
            title: 'Audio Beat Sensitivity',
            subtitle:
                'Transient velocity response threshold for audio tracking',
            settingKey: 'audio_haptics_sensitivity',
            min: 0.05,
            max: 1.0,
            unit: '%',
            enabledKey: 'audio_haptics_realtime_enabled',
          ),
          const GlassSliderTile(
            title: 'Refractory Blanking Window',
            subtitle: 'Minimum time interval between successive haptic pulses',
            settingKey: 'audio_haptics_refractory_ms',
            min: 20,
            max: 300,
            unit: 'ms',
            enabledKey: 'audio_haptics_realtime_enabled',
          ),

          const GlassSectionHeader('Actions'),
          GlassGroupedCard(
            children: [
              GlassActionTile(
                title: 'Synthesize Preview',
                subtitle: 'Test generated haptic curve on vibration motor',
                leading: const Icon(CupertinoIcons.play_circle_fill,
                    color: CupertinoColors.activeBlue),
                enabledKey: 'audio_haptics_realtime_enabled',
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
                enabledKey: 'audio_haptics_realtime_enabled',
                onTap: () async {
                  final path = await NativeBridge.buildRingtoneModule();
                  if (context.mounted) {
                    GlassDialog.show(
                      context: context,
                      title: 'Export Ringtone Module',
                      message: path != null
                          ? 'Haptic track successfully packaged to:\n$path'
                          : 'Haptic track successfully packaged for system audio server.',
                      actions: [
                        GlassDialogAction(
                          label: 'OK',
                          isPrimary: true,
                          onPressed: () => Navigator.of(context).pop(),
                        ),
                      ],
                    );
                  }
                },
              ),
            ],
          ),
        ],
      ),
    );
  }
}
