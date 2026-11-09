import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../../state/settings_store.dart';

class LockscreenScreen extends StatelessWidget {
  const LockscreenScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;

    return AppScaffold(
      title: 'Lockscreen & Pinpad',
      showTopMenu: false,
      body: ListenableBuilder(
        listenable: store,
        builder: (context, _) {
          final sheetEnabled = store.getBool('lock_sheet_enabled', true);
          final sheetBlur = store.getDouble('lock_sheet_blur', 30.0);
          final sheetLighting = store.getDouble('lock_sheet_lighting_power', 1.0);
          final homeIndicator = store.getBool('lock_sheet_home_indicator', true);

          final keypadEnabled = store.getBool('keypad_enabled', true);
          final keypadStretch = store.getDouble('keypad_stretch_factor', 1.25);
          final keypadBloom = store.getDouble('keypad_bloom_factor', 1.0);
          final keypadResponse = store.getDouble('keypad_spring_response', 0.28);
          final keypadDamping = store.getDouble('keypad_spring_damping', 0.72);

          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              const GlassSectionHeader('Lockscreen Sheet Platter'),
              GlassGroupedSection(
                children: [
                  GlassSwitchTile(
                    title: 'Liquid Glass Sheet',
                    subtitle: 'Glass notification and clock backdrop on lockscreen',
                    value: sheetEnabled,
                    onChanged: (v) => store.setBool('lock_sheet_enabled', v),
                  ),
                  GlassSwitchTile(
                    title: 'iOS Home Indicator',
                    subtitle: 'Floating glass bar capsule at bottom of lockscreen',
                    value: homeIndicator,
                    onChanged: (v) => store.setBool('lock_sheet_home_indicator', v),
                  ),
                ],
              ),

              const GlassSectionHeader('Sheet Optics'),
              GlassSliderTile(
                title: 'Backdrop Blur Radius',
                subtitle: 'Lockscreen wallpaper blur depth',
                value: sheetBlur,
                min: 5.0,
                max: 60.0,
                unit: 'px',
                onChanged: (v) => store.setDouble('lock_sheet_blur', v),
              ),
              GlassSliderTile(
                title: 'Specular Lighting Power',
                subtitle: 'Ambient light refraction gleam across sheet surface',
                value: sheetLighting,
                min: 0.1,
                max: 2.5,
                onChanged: (v) => store.setDouble('lock_sheet_lighting_power', v),
              ),

              const GlassSectionHeader('Pinpad Liquid Buttons'),
              GlassGroupedSection(
                children: [
                  GlassSwitchTile(
                    title: 'Liquid Pinpad Keypad',
                    subtitle: 'Apple style circular glass number buttons with press bloom',
                    value: keypadEnabled,
                    onChanged: (v) => store.setBool('keypad_enabled', v),
                  ),
                ],
              ),

              const GlassSectionHeader('Keypad Physics & Kinetics'),
              GlassSliderTile(
                title: 'Button Stretch Factor',
                subtitle: 'Radial expansion squash on finger touch down',
                value: keypadStretch,
                min: 0.5,
                max: 2.0,
                onChanged: (v) => store.setDouble('keypad_stretch_factor', v),
              ),
              GlassSliderTile(
                title: 'Touch Bloom Amplitude',
                subtitle: 'Phosphor glow radiance spreading out from button center',
                value: keypadBloom,
                min: 0.0,
                max: 2.0,
                onChanged: (v) => store.setDouble('keypad_bloom_factor', v),
              ),
              GlassSliderTile(
                title: 'Spring Response',
                subtitle: 'Snap release rebound duration',
                value: keypadResponse,
                min: 0.1,
                max: 0.6,
                unit: 's',
                onChanged: (v) => store.setDouble('keypad_spring_response', v),
              ),
              GlassSliderTile(
                title: 'Damping Ratio (ζ)',
                subtitle: 'Elastic settlement resistance',
                value: keypadDamping,
                min: 0.3,
                max: 1.1,
                onChanged: (v) => store.setDouble('keypad_spring_damping', v),
              ),
              const SizedBox(height: 100),
            ],
          );
        },
      ),
    );
  }
}
