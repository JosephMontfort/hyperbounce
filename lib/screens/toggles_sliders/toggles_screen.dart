import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../../state/settings_store.dart';

class TogglesScreen extends StatefulWidget {
  const TogglesScreen({super.key});

  @override
  State<TogglesScreen> createState() => _TogglesScreenState();
}

class _TogglesScreenState extends State<TogglesScreen> {
  final _store = SettingsStore.instance;
  bool _previewToggle1 = true;
  bool _previewToggle2 = false;

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: 'Liquid Toggles',
      showTopMenu: false,
      body: ListenableBuilder(
        listenable: _store,
        builder: (context, _) {
          final enabled = _store.getBool('toggle_engine_enabled', true);
          final response = _store.getDouble('toggle_spring_response', 0.35);
          final damping = _store.getDouble('toggle_spring_damping', 0.65);
          final bounce = _store.getDouble('toggle_spring_bounce', 1.2);
          final thickness = _store.getDouble('toggle_glass_thickness', 18.0);
          final blur = _store.getDouble('toggle_glass_blur', 3.0);
          final refraction = _store.getDouble('toggle_glass_refraction', 1.2);
          final specular = _store.getDouble('toggle_glass_specular', 0.7);

          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              const GlassSectionHeader('Interactive Preview'),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                child: GlassCard(
                  padding: const EdgeInsets.all(20),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                    children: [
                      Column(
                        children: [
                          GlassSwitch(
                            value: _previewToggle1,
                            onChanged: (v) => setState(() => _previewToggle1 = v),
                          ),
                          const SizedBox(height: 8),
                          const Text('Active', style: TextStyle(fontSize: 13, color: CupertinoColors.systemGrey)),
                        ],
                      ),
                      Column(
                        children: [
                          GlassSwitch(
                            value: _previewToggle2,
                            onChanged: (v) => setState(() => _previewToggle2 = v),
                          ),
                          const SizedBox(height: 8),
                          const Text('Inactive', style: TextStyle(fontSize: 13, color: CupertinoColors.systemGrey)),
                        ],
                      ),
                    ],
                  ),
                ),
              ),

              const GlassSectionHeader('Engine & SystemUI Injection'),
              GlassGroupedSection(
                children: [
                  GlassSwitchTile(
                    title: 'Liquid Toggle Engine',
                    subtitle: 'Inject liquid glass switches into HyperOS System Settings & UI',
                    value: enabled,
                    onChanged: (v) => _store.setBool('toggle_engine_enabled', v),
                  ),
                ],
              ),

              const GlassSectionHeader('Spring Kinetics & Jump Physics'),
              GlassSliderTile(
                title: 'Spring Response',
                subtitle: 'Duration stiffness of the toggle flip',
                value: response,
                min: 0.1,
                max: 1.0,
                unit: 's',
                onChanged: (v) => _store.setDouble('toggle_spring_response', v),
              ),
              GlassSliderTile(
                title: 'Damping Ratio (ζ)',
                subtitle: 'Oscillation resistance (underdamped produces bounce)',
                value: damping,
                min: 0.2,
                max: 1.2,
                onChanged: (v) => _store.setDouble('toggle_spring_damping', v),
              ),
              GlassSliderTile(
                title: 'Jump Velocity Bounce',
                subtitle: 'Apple signature thumb lift amplitude on switch tap',
                value: bounce,
                min: 0.5,
                max: 2.5,
                onChanged: (v) => _store.setDouble('toggle_spring_bounce', v),
              ),

              const GlassSectionHeader('Glass Shading & Optics'),
              GlassSliderTile(
                title: 'Material Thickness',
                subtitle: 'Apparent physical glass depth in logical pt',
                value: thickness,
                min: 4.0,
                max: 40.0,
                unit: 'pt',
                onChanged: (v) => _store.setDouble('toggle_glass_thickness', v),
              ),
              GlassSliderTile(
                title: 'Gaussian Blur Radius',
                subtitle: 'Internal diffusion blur inside the switch body',
                value: blur,
                min: 0.0,
                max: 15.0,
                unit: 'px',
                onChanged: (v) => _store.setDouble('toggle_glass_blur', v),
              ),
              GlassSliderTile(
                title: 'Refractive Index (IOR)',
                subtitle: 'Light bending power across thumb rim',
                value: refraction,
                min: 0.8,
                max: 2.0,
                onChanged: (v) => _store.setDouble('toggle_glass_refraction', v),
              ),
              GlassSliderTile(
                title: 'Specular Rim Strength',
                subtitle: 'Fresnel rim highlight reflection luminosity',
                value: specular,
                min: 0.0,
                max: 1.5,
                onChanged: (v) => _store.setDouble('toggle_glass_specular', v),
              ),
              const SizedBox(height: 100),
            ],
          );
        },
      ),
    );
  }
}
