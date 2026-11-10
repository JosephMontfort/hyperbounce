import 'package:flutter/cupertino.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../spec/all_specs.dart';
import '../spec/spec_screen.dart';

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
                destination: (_) => const LockScreenSheetScreen(),
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
                destination: (_) => const KeypadScreen(),
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
    return AppScaffold(
      title: 'Lock Screen Sheet',
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Lock Screen Liquid Glass Sheet',
                settingKey: 'lock_sheet_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('Edge Glass Thickness'),
          const GlassSliderTile(
            title: 'Side Edges Glass Bevel',
            subtitle:
                'Thickness of glass refraction band on left & right edges',
            settingKey: 'lock_card_left_bevel',
            enabledKey: 'lock_sheet_enabled',
            min: 0,
            max: 120,
            unit: 'dp',
          ),
          const GlassSliderTile(
            title: 'Bottom Edge Glass Bevel',
            subtitle: 'Thickness of glass refraction band on bottom edge',
            settingKey: 'lock_card_bottom_bevel',
            enabledKey: 'lock_sheet_enabled',
            min: 0,
            max: 120,
            unit: 'dp',
          ),
          const GlassSectionFooter(
            'Side and bottom glass thickness can be adjusted or turned completely off (0 dp). Center of the screen remains 100% transparent wallpaper.',
          ),

          const GlassSectionHeader('Optics & Components'),
          GlassGroupedCard(
            children: [
              GlassNavTile(
                title: 'Liquid Glass Tuning',
                subtitle:
                    'Refraction, chromatic dispersion & corner curvature',
                enabledKey: 'lock_sheet_enabled',
                destination: (_) => SpecScreen(
                  spec: lockScreenSheetGlassTuningSpec,
                ),
              ),
              GlassNavTile(
                title: 'Glass Lighting & Highlights',
                subtitle: 'Specular rims, perimeter hairline & inner glow',
                enabledKey: 'lock_sheet_enabled',
                destination: (_) => SpecScreen(
                  spec: lockScreenSheetLightingSpec,
                ),
              ),
              GlassNavTile(
                title: 'Home Indicator Pill',
                subtitle: 'Pill dimensions, height & bottom margin',
                enabledKey: 'lock_sheet_enabled',
                destination: (_) => SpecScreen(
                  spec: lockScreenSheetHomeIndicatorSpec,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class KeypadScreen extends StatelessWidget {
  const KeypadScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: 'PIN Pad',
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Liquid Glass PIN Pad',
                settingKey: 'engine_keypad_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('Physics & Layout'),
          GlassGroupedCard(
            children: [
              GlassNavTile(
                title: 'iOS 26 Radial Bloom Physics',
                subtitle:
                    'Cluster origin, radial spring damping & bloom expansion',
                enabledKey: 'engine_keypad_enabled',
                destination: (_) => SpecScreen(
                  spec: keypadIosBloomPhysicsSpec,
                ),
              ),
              GlassNavTile(
                title: 'Liquid Glass Tuning',
                subtitle:
                    'Refraction, bevel thickness, dispersion & specular rims',
                enabledKey: 'engine_keypad_enabled',
                destination: (_) => SpecScreen(
                  spec: keypadGlassTuningSpec,
                ),
              ),
              GlassNavTile(
                title: 'Buttons Layout',
                subtitle:
                    'Key circle diameter, liquid press scale & typography',
                enabledKey: 'engine_keypad_enabled',
                destination: (_) => SpecScreen(
                  spec: keypadButtonsLayoutSpec,
                ),
              ),
              GlassNavTile(
                title: 'PIN Pad Layout',
                subtitle: 'Keypad lift, PIN dots position & grid spacing',
                enabledKey: 'engine_keypad_enabled',
                destination: (_) => SpecScreen(
                  spec: keypadPadLayoutSpec,
                ),
              ),
              GlassNavTile(
                title: 'Liquid Dynamics',
                subtitle:
                    'Stretch elasticity, snapback wobbles & delay offsets',
                enabledKey: 'engine_keypad_enabled',
                destination: (_) => SpecScreen(
                  spec: keypadPhysicsSpec,
                ),
              ),
            ],
          ),
          const GlassSectionFooter(
            'Configure radial bloom kinetics, real-time glass optics, button dimensions, and lockscreen layout coordinates.',
          ),
        ],
      ),
    );
  }
}
