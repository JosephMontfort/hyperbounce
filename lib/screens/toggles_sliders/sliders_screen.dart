import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../../state/settings_store.dart';

class SlidersScreen extends StatefulWidget {
  const SlidersScreen({super.key});

  @override
  State<SlidersScreen> createState() => _SlidersScreenState();
}

class _SlidersScreenState extends State<SlidersScreen> {
  final _store = SettingsStore.instance;
  double _previewSliderValue = 0.65;

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: 'Liquid Sliders',
      showTopMenu: false,
      body: ListenableBuilder(
        listenable: _store,
        builder: (context, _) {
          final enabled = _store.getBool('slider_engine_enabled', true);
          final response = _store.getDouble('slider_spring_response', 0.32);
          final damping = _store.getDouble('slider_spring_damping', 0.70);
          final squashStretch = _store.getDouble('slider_squash_stretch', 1.35);
          final thickness = _store.getDouble('slider_glass_thickness', 20.0);
          final blur = _store.getDouble('slider_glass_blur', 4.0);
          final refraction = _store.getDouble('slider_glass_refraction', 1.25);
          final specular = _store.getDouble('slider_glass_specular', 0.85);
          final haptics = _store.getBool('slider_haptics_enabled', true);

          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              const GlassSectionHeader('Interactive Preview'),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: GlassCard(
                  padding: const EdgeInsets.all(20),
                  child: Column(
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text(
                            'Volume Level',
                            style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
                          ),
                          Text(
                            '${(_previewSliderValue * 100).toInt()}%',
                            style: const TextStyle(
                              fontSize: 15,
                              fontWeight: FontWeight.bold,
                              color: CupertinoColors.activeBlue,
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 14),
                      GlassSlider(
                        value: _previewSliderValue,
                        onChanged: (v) => setState(() => _previewSliderValue = v),
                      ),
                    ],
                  ),
                ),
              ),

              const GlassSectionHeader('Engine & SystemUI Injection'),
              GlassGroupedSection(
                children: [
                  GlassSwitchTile(
                    title: 'Liquid Slider Engine',
                    subtitle: 'Inject liquid glass volume & brightness bars into HyperOS',
                    value: enabled,
                    onChanged: (v) => _store.setBool('slider_engine_enabled', v),
                  ),
                  GlassSwitchTile(
                    title: 'Haptic Feedback',
                    subtitle: 'Tactile tick pulses while dragging thumb across levels',
                    value: haptics,
                    onChanged: (v) => _store.setBool('slider_haptics_enabled', v),
                  ),
                ],
              ),

              const GlassSectionHeader('Jelly Thumb Squash & Stretch'),
              GlassSliderTile(
                title: 'Squash & Stretch Ratio',
                subtitle: 'Organic inertia deformation factor on drag velocity',
                value: squashStretch,
                min: 0.8,
                max: 2.2,
                onChanged: (v) => _store.setDouble('slider_squash_stretch', v),
              ),
              GlassSliderTile(
                title: 'Spring Response',
                subtitle: 'Elastic snap return duration when thumb is released',
                value: response,
                min: 0.1,
                max: 0.8,
                unit: 's',
                onChanged: (v) => _store.setDouble('slider_spring_response', v),
              ),
              GlassSliderTile(
                title: 'Damping Ratio (ζ)',
                subtitle: 'Settling bounce suppression factor',
                value: damping,
                min: 0.3,
                max: 1.2,
                onChanged: (v) => _store.setDouble('slider_spring_damping', v),
              ),

              const GlassSectionHeader('Glass Shading & Optics'),
              GlassSliderTile(
                title: 'Glass Thickness',
                subtitle: 'Refractive platter depth in logical pt',
                value: thickness,
                min: 5.0,
                max: 45.0,
                unit: 'pt',
                onChanged: (v) => _store.setDouble('slider_glass_thickness', v),
              ),
              GlassSliderTile(
                title: 'Gaussian Blur Radius',
                subtitle: 'Backdrop diffusion through active track',
                value: blur,
                min: 0.0,
                max: 16.0,
                unit: 'px',
                onChanged: (v) => _store.setDouble('slider_glass_blur', v),
              ),
              GlassSliderTile(
                title: 'Refractive Index (IOR)',
                subtitle: 'Refractive lensing intensity',
                value: refraction,
                min: 0.8,
                max: 2.0,
                onChanged: (v) => _store.setDouble('slider_glass_refraction', v),
              ),
              GlassSliderTile(
                title: 'Specular Rim Strength',
                subtitle: 'Highlight gleam brightness on thumb edge',
                value: specular,
                min: 0.0,
                max: 1.5,
                onChanged: (v) => _store.setDouble('slider_glass_specular', v),
              ),
              const SizedBox(height: 100),
            ],
          );
        },
      ),
    );
  }
}
