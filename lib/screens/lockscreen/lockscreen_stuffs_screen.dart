import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';

class LockscreenStuffsScreen extends StatelessWidget {
  const LockscreenStuffsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: 'LockScreen Stuffs',
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          GlassGroupedCard(
            children: [
              GlassNavTile(
                title: 'Lock Screen Sheet',
                subtitle:
                    'Curvature refraction, bevel dispersion & home bar pill',
                leading: Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: const Color(0xFFAF52DE).withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(CupertinoIcons.rectangle_stack_fill,
                      size: 18, color: Color(0xFFAF52DE)),
                ),
                onTap: () {
                  Navigator.of(context).push(
                    CupertinoPageRoute(
                        builder: (_) => const LockScreenSheetScreen()),
                  );
                },
              ),
              GlassNavTile(
                title: 'PIN Pad',
                subtitle:
                    'Radial bloom kinetics, lens keycaps & custom grid lift',
                leading: Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: const Color(0xFF5856D6).withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(CupertinoIcons.circle_grid_3x3_fill,
                      size: 18, color: Color(0xFF5856D6)),
                ),
                onTap: () {
                  Navigator.of(context).push(
                    CupertinoPageRoute(builder: (_) => const KeypadScreen()),
                  );
                },
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class LockScreenSheetScreen extends StatelessWidget {
  const LockScreenSheetScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final enabled = store.getBool('lock_sheet_enabled', false);
        final glassEffects =
            store.getBool('lock_sheet_glass_effects_enabled', true);
        final bevelWidth = store.getDouble('lock_sheet_bevel_width', 16.0);
        final cornerRadius = store.getDouble('lock_sheet_corner_radius', 28.0);
        final homePill = store.getBool('lock_sheet_home_pill_enabled', true);

        return AppScaffold(
          title: 'Lock Screen Sheet',
          body: ListView(
            padding: const EdgeInsets.only(top: 12, bottom: 60),
            children: [
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Enable Lockscreen Sheet',
                    value: enabled,
                    onChanged: (val) =>
                        store.setBool('lock_sheet_enabled', val),
                  ),
                ],
              ),

              const GlassSectionHeader('Optics & Bevels'),
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: '3-Edge Liquid Glass Effects',
                    subtitle:
                        'Optical refraction & specular highlights along edges',
                    value: glassEffects,
                    enabled: enabled,
                    onChanged: (val) => store.setBool(
                        'lock_sheet_glass_effects_enabled', val),
                  ),
                ],
              ),

              GlassSliderTile(
                title: 'Bevel Width',
                subtitle: 'Width of sheet edge refraction slopes',
                value: bevelWidth,
                min: 0,
                max: 40,
                unit: 'dp',
                enabled: enabled && glassEffects,
                onChanged: (val) =>
                    store.setDouble('lock_sheet_bevel_width', val),
              ),
              GlassSliderTile(
                title: 'Corner Radius',
                subtitle: 'Bottom sheet corner curvature',
                value: cornerRadius,
                min: 0,
                max: 50,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('lock_sheet_corner_radius', val),
              ),

              const GlassSectionHeader('Components'),
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Home Bar Pill',
                    subtitle: 'Display interactive home bar pill at bottom edge',
                    value: homePill,
                    enabled: enabled,
                    onChanged: (val) =>
                        store.setBool('lock_sheet_home_pill_enabled', val),
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

class KeypadScreen extends StatelessWidget {
  const KeypadScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final enabled = store.getBool('keypad_enabled', false);
        final bloomScale = store.getDouble('pin_radial_bloom_scale', 1.0);
        final damping = store.getDouble('pin_radial_spring_damping', 0.82);
        final glassBlur = store.getDouble('pin_glass_blur', 14.0);
        final bevelDepth = store.getDouble('pin_glass_bevel_depth', 6.0);
        final diameter = store.getDouble('pin_circle_diameter', 75.0);
        final pressScale = store.getDouble('pin_press_scale', 0.92);
        final keypadLift = store.getDouble('pin_keypad_lift', 0.0);

        return AppScaffold(
          title: 'PIN Pad',
          body: ListView(
            padding: const EdgeInsets.only(top: 12, bottom: 60),
            children: [
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Liquid Glass PIN Pad',
                    value: enabled,
                    onChanged: (val) => store.setBool('keypad_enabled', val),
                  ),
                ],
              ),

              const GlassSectionHeader('Radial Bloom Kinetics'),
              GlassSliderTile(
                title: 'Bloom Expansion Scale',
                subtitle: 'Radial outward velocity when pinpad activates',
                value: bloomScale,
                min: 0.5,
                max: 2.0,
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('pin_radial_bloom_scale', val),
              ),
              GlassSliderTile(
                title: 'Radial Spring Damping',
                subtitle: 'Spring oscillation absorption for pin buttons',
                value: damping,
                min: 0.3,
                max: 1.2,
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('pin_radial_spring_damping', val),
              ),

              const GlassSectionHeader('Keycap Optics'),
              GlassSliderTile(
                title: 'Keycap Glass Blur',
                subtitle: 'Internal button refraction blur radius',
                value: glassBlur,
                min: 0,
                max: 30,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) => store.setDouble('pin_glass_blur', val),
              ),
              GlassSliderTile(
                title: 'Keycap Bevel Depth',
                subtitle: 'Curvature extrusion depth on button edges',
                value: bevelDepth,
                min: 0,
                max: 15,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('pin_glass_bevel_depth', val),
              ),

              const GlassSectionHeader('Buttons Layout'),
              GlassSliderTile(
                title: 'Key Circle Diameter',
                subtitle: 'Physical diameter of numeric button circles',
                value: diameter,
                min: 50,
                max: 100,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) => store.setDouble('pin_circle_diameter', val),
              ),
              GlassSliderTile(
                title: 'Liquid Press Scale',
                subtitle: 'Button shrinkage factor upon fingertip touch-down',
                value: pressScale,
                min: 0.7,
                max: 1.0,
                enabled: enabled,
                onChanged: (val) => store.setDouble('pin_press_scale', val),
              ),
              GlassSliderTile(
                title: 'PIN Pad Vertical Lift',
                subtitle: 'Vertical offset adjustment from bottom edge',
                value: keypadLift,
                min: -50,
                max: 150,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) => store.setDouble('pin_keypad_lift', val),
              ),
            ],
          ),
        );
      },
    );
  }
}
