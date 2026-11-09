import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';

class SlidersScreen extends StatefulWidget {
  const SlidersScreen({super.key});

  @override
  State<SlidersScreen> createState() => _SlidersScreenState();
}

class _SlidersScreenState extends State<SlidersScreen> {
  double _previewSlider = 0.65;

  static const _presets = [
    MapEntry('Blue', Color(0xFF007AFF)),
    MapEntry('Green', Color(0xFF34C759)),
    MapEntry('Pink', Color(0xFFFF2D55)),
    MapEntry('Purple', Color(0xFFAF52DE)),
    MapEntry('Orange', Color(0xFFFF9500)),
  ];

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final selectedPreset = store.getInt('slider_accent', 0);
        final blur = store.getDouble('slider_glass_blur', 14.0);
        final lensSize = store.getDouble('slider_glass_lens_size', 12.0);
        final lensDepth = store.getDouble('slider_glass_lens_depth', 8.0);
        final highlight = store.getDouble('slider_glass_highlight', 0.90);
        final shadow = store.getDouble('slider_glass_shadow', 10.0);

        final activeColor =
            _presets[selectedPreset.clamp(0, _presets.length - 1)].value;

        return AppScaffold(
          title: 'Sliders Appearance',
          body: ListView(
            padding: const EdgeInsets.only(top: 12, bottom: 60),
            children: [
              // Live Interactive Preview
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: GlassContainer.card(
                  surfaceStyle: GlassSurfaceStyle.solid,
                  padding: const EdgeInsets.all(20),
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
                      const SizedBox(height: 16),
                      GlassSlider(
                        value: _previewSlider,
                        activeColor: activeColor,
                        onChanged: (val) =>
                            setState(() => _previewSlider = val),
                      ),
                    ],
                  ),
                ),
              ),

              const GlassSectionHeader('Accent Color'),
              GlassGroupedCard(
                children: [
                  Padding(
                    padding: const EdgeInsets.symmetric(
                        horizontal: 16, vertical: 14),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.spaceAround,
                      children: _presets.asMap().entries.map((entry) {
                        final idx = entry.key;
                        final color = entry.value.value;
                        final isSelected = selectedPreset == idx;

                        return GestureDetector(
                          onTap: () => store.setInt('slider_accent', idx),
                          child: Container(
                            width: 44,
                            height: 44,
                            decoration: BoxDecoration(
                              color: color,
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
              ),

              const GlassSectionHeader('Liquid Glass Optics'),
              GlassSliderTile(
                title: 'Blur',
                subtitle: 'Backdrop gaussian blur radius',
                value: blur,
                min: 0,
                max: 30,
                unit: 'dp',
                onChanged: (val) => store.setDouble('slider_glass_blur', val),
              ),
              GlassSliderTile(
                title: 'Lens Size',
                subtitle: 'Curvature radius of refraction lens',
                value: lensSize,
                min: 0,
                max: 24,
                unit: 'dp',
                onChanged: (val) =>
                    store.setDouble('slider_glass_lens_size', val),
              ),
              GlassSliderTile(
                title: 'Lens Depth',
                subtitle: 'Optical refraction extrusion depth',
                value: lensDepth,
                min: 0,
                max: 16,
                unit: 'dp',
                onChanged: (val) =>
                    store.setDouble('slider_glass_lens_depth', val),
              ),
              GlassSliderTile(
                title: 'Highlight Strength',
                subtitle: 'Specular Fresnel rim highlight intensity',
                value: highlight,
                min: 0,
                max: 1.0,
                unit: '%',
                onChanged: (val) =>
                    store.setDouble('slider_glass_highlight', val),
              ),
              GlassSliderTile(
                title: 'Shadow Radius',
                subtitle: 'Diffusion radius of glass drop shadow',
                value: shadow,
                min: 0,
                max: 24,
                unit: 'dp',
                onChanged: (val) => store.setDouble('slider_glass_shadow', val),
              ),
            ],
          ),
        );
      },
    );
  }
}

class SliderPhysicsScreen extends StatefulWidget {
  const SliderPhysicsScreen({super.key});

  @override
  State<SliderPhysicsScreen> createState() => _SliderPhysicsScreenState();
}

class _SliderPhysicsScreenState extends State<SliderPhysicsScreen> {
  double _previewSlider = 0.5;

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final sensitivity =
            store.getDouble('slider_glass_drag_sensitivity', 1.0);
        final damping =
            store.getDouble('slider_glass_spring_damping', 0.86);
        final stiffness =
            store.getDouble('slider_glass_spring_stiffness', 380.0);

        return AppScaffold(
          title: 'Sliders Drag Physics',
          body: ListView(
            padding: const EdgeInsets.only(top: 12, bottom: 60),
            children: [
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: GlassContainer.card(
                  surfaceStyle: GlassSurfaceStyle.solid,
                  padding: const EdgeInsets.all(20),
                  child: GlassSlider(
                    value: _previewSlider,
                    onChanged: (val) => setState(() => _previewSlider = val),
                  ),
                ),
              ),

              const GlassSectionHeader('Drag Physics'),
              GlassSliderTile(
                title: 'Drag Sensitivity',
                subtitle: 'Touch tracking movement ratio',
                value: sensitivity,
                min: 0.2,
                max: 2.5,
                onChanged: (val) =>
                    store.setDouble('slider_glass_drag_sensitivity', val),
              ),
              GlassSliderTile(
                title: 'Spring Bounciness',
                subtitle:
                    'Lower = more overshoot bounce, Higher = stiffer vibration damping',
                value: damping,
                min: 0.3,
                max: 1.5,
                onChanged: (val) =>
                    store.setDouble('slider_glass_spring_damping', val),
              ),
              GlassSliderTile(
                title: 'Spring Stiffness',
                subtitle:
                    'Lower = slower return oscillation, Higher = rapid snap settling',
                value: stiffness,
                min: 50,
                max: 800,
                onChanged: (val) =>
                    store.setDouble('slider_glass_spring_stiffness', val),
              ),
            ],
          ),
        );
      },
    );
  }
}
