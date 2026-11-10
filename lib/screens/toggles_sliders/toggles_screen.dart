import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';

class TogglesScreen extends StatefulWidget {
  const TogglesScreen({super.key});

  @override
  State<TogglesScreen> createState() => _TogglesScreenState();
}

class _TogglesScreenState extends State<TogglesScreen> {
  bool _previewToggle = true;

  static const _presets = [
    MapEntry('Green', Color(0xFF34C759)),
    MapEntry('Blue', Color(0xFF007AFF)),
    MapEntry('Pink', Color(0xFFFF2D55)),
    MapEntry('Purple', Color(0xFFAF52DE)),
    MapEntry('Orange', Color(0xFFFF9500)),
  ];

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;

    return AppScaffold(
      title: 'Toggles Appearance',
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          // Live Interactive Preview
          SettingListener<String>(
            settingKey: 'toggle_accent',
            getter: (s) => s.getString('toggle_accent', 'Green'),
            builder: (ctx, accentStr) {
              final activeColor = _presets
                  .firstWhere((p) => p.key == accentStr,
                      orElse: () => _presets.first)
                  .value;

              return Padding(
                padding:
                    const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: GlassContainer.card(
                  surfaceStyle: GlassSurfaceStyle.solid,
                  padding: const EdgeInsets.symmetric(vertical: 24),
                  child: Center(
                    child: Column(
                      children: [
                        const Text(
                          'INTERACTIVE PREVIEW',
                          style: TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.w600,
                            letterSpacing: 0.8,
                            color: CupertinoColors.secondaryLabel,
                          ),
                        ),
                        const SizedBox(height: 18),
                        GlassSwitch(
                          value: _previewToggle,
                          activeColor: activeColor,
                          onChanged: (val) =>
                              setState(() => _previewToggle = val),
                        ),
                      ],
                    ),
                  ),
                ),
              );
            },
          ),

          const GlassSectionHeader('Accent Color'),
          SettingListener<String>(
            settingKey: 'toggle_accent',
            getter: (s) => s.getString('toggle_accent', 'Green'),
            builder: (ctx, selectedStr) {
              return GlassGroupedCard(
                children: [
                  Padding(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 16, vertical: 14),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceAround,
                      children: _presets.map((entry) {
                        final isSelected = selectedStr == entry.key;

                        return GestureDetector(
                          onTap: () =>
                              store.setString('toggle_accent', entry.key),
                          child: Container(
                            width: 44,
                            height: 44,
                            decoration: BoxDecoration(
                              color: entry.value,
                              shape: BoxShape.circle,
                              border: isSelected
                                  ? Border.all(
                                      color: CupertinoColors.white, width: 3.5)
                                  : null,
                            ),
                          ),
                        );
                      }).toList(),
                    ),
                  ),
                ],
              );
            },
          ),

          const GlassSectionHeader('Liquid Glass Optics'),
          const GlassSliderTile(
            title: 'Blur',
            subtitle: 'Backdrop gaussian blur radius',
            settingKey: 'toggle_glass_blur',
            min: 0,
            max: 30,
            unit: 'dp',
          ),
          const GlassSliderTile(
            title: 'Lens Size',
            subtitle: 'Curvature radius of refraction lens',
            settingKey: 'toggle_glass_lens_size',
            min: 0,
            max: 24,
            unit: 'dp',
          ),
          const GlassSliderTile(
            title: 'Lens Depth',
            subtitle: 'Optical refraction extrusion depth',
            settingKey: 'toggle_glass_lens_depth',
            min: 0,
            max: 16,
            unit: 'dp',
          ),
          const GlassSliderTile(
            title: 'Highlight Strength',
            subtitle: 'Specular Fresnel rim highlight intensity',
            settingKey: 'toggle_glass_highlight',
            min: 0,
            max: 1.0,
            unit: '%',
          ),
          const GlassSliderTile(
            title: 'Shadow Radius',
            subtitle: 'Diffusion radius of glass drop shadow',
            settingKey: 'toggle_glass_shadow',
            min: 0,
            max: 24,
            unit: 'dp',
          ),
        ],
      ),
    );
  }
}

class TogglePhysicsScreen extends StatefulWidget {
  const TogglePhysicsScreen({super.key});

  @override
  State<TogglePhysicsScreen> createState() => _TogglePhysicsScreenState();
}

class _TogglePhysicsScreenState extends State<TogglePhysicsScreen> {
  bool _previewToggle = false;

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: 'Toggles Drag Physics',
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: GlassContainer.card(
              surfaceStyle: GlassSurfaceStyle.solid,
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Center(
                child: GlassSwitch(
                  value: _previewToggle,
                  onChanged: (val) => setState(() => _previewToggle = val),
                ),
              ),
            ),
          ),

          const GlassSectionHeader('Drag Physics'),
          const GlassSliderTile(
            title: 'Drag Sensitivity',
            subtitle: 'Touch tracking movement ratio',
            settingKey: 'toggle_glass_drag_sensitivity',
            min: 0.2,
            max: 2.5,
          ),
          const GlassSliderTile(
            title: 'Spring Bounciness',
            subtitle:
                'Lower = more overshoot bounce, Higher = stiffer vibration damping',
            settingKey: 'toggle_glass_spring_damping',
            min: 0.3,
            max: 1.5,
          ),
          const GlassSliderTile(
            title: 'Spring Stiffness',
            subtitle:
                'Lower = slower return oscillation, Higher = rapid snap settling',
            settingKey: 'toggle_glass_spring_stiffness',
            min: 50,
            max: 800,
          ),
        ],
      ),
    );
  }
}
