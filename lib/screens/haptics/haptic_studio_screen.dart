import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../../state/settings_store.dart';

class HapticStudioScreen extends StatelessWidget {
  const HapticStudioScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;

    return AppScaffold(
      title: 'Haptic Studio',
      showTopMenu: false,
      body: ListenableBuilder(
        listenable: store,
        builder: (context, _) {
          final dspEnabled = store.getBool('haptics_dsp_enabled', true);
          final ringtoneSync = store.getBool('haptics_ringtone_sync_enabled', true);
          final xiaomiMotor = store.getBool('haptics_xiaomi_motor_enabled', true);
          final intensity = store.getDouble('haptics_intensity_mult', 1.0);
          final sharpness = store.getDouble('haptics_sharpness_mult', 1.0);

          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              const GlassSectionHeader('Real-Time DSP Vibration Engine'),
              GlassGroupedSection(
                children: [
                  GlassSwitchTile(
                    title: 'DSP Haptic Synthesizer',
                    subtitle: 'Real-time audio waveform to vibration synthesis',
                    value: dspEnabled,
                    onChanged: (v) => store.setBool('haptics_dsp_enabled', v),
                  ),
                  GlassSwitchTile(
                    title: 'Synchronized Ringtone Haptics',
                    subtitle: 'Extract haptic track from custom .m4r / .ogg ringtones',
                    value: ringtoneSync,
                    onChanged: (v) => store.setBool('haptics_ringtone_sync_enabled', v),
                  ),
                  GlassSwitchTile(
                    title: 'Xiaomi Linear Actuator Calibration',
                    subtitle: 'High-fidelity resonance frequency tuning for CyberEngine / AAC motors',
                    value: xiaomiMotor,
                    onChanged: (v) => store.setBool('haptics_xiaomi_motor_enabled', v),
                  ),
                ],
              ),

              const GlassSectionHeader('Actuator Tuning'),
              GlassSliderTile(
                title: 'Haptic Intensity Multiplier',
                subtitle: 'Overall vibration amplitude gain',
                value: intensity,
                min: 0.1,
                max: 2.0,
                onChanged: (v) => store.setDouble('haptics_intensity_mult', v),
              ),
              GlassSliderTile(
                title: 'Haptic Sharpness Multiplier',
                subtitle: 'Transient attack click crispness',
                value: sharpness,
                min: 0.1,
                max: 2.0,
                onChanged: (v) => store.setDouble('haptics_sharpness_mult', v),
              ),
              const SizedBox(height: 100),
            ],
          );
        },
      ),
    );
  }
}
