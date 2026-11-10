import 'package:flutter/cupertino.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../spec/all_specs.dart';
import '../spec/spec_screen.dart';

class SystemLauncherStuffsScreen extends StatelessWidget {
  const SystemLauncherStuffsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: 'System Launcher Stuffs',
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          GlassGroupedCard(
            children: [
              GlassNavTile(
                title: 'App Transitions',
                subtitle: 'App Open, close animations tuning',
                leading: Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: const Color(0xFF007AFF).withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(CupertinoIcons.arrow_up_right_square_fill,
                      size: 18, color: Color(0xFF007AFF)),
                ),
                destination: (_) => const AppTransitionsScreen(),
              ),
              GlassNavTile(
                title: 'Screen Unlock Animation',
                subtitle:
                    'Velocity coupled screen unlock animation & tuning',
                leading: Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: const Color(0xFF34C759).withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(CupertinoIcons.lock_open_fill,
                      size: 18, color: Color(0xFF34C759)),
                ),
                destination: (_) => const ScreenUnlockScreen(),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class AppTransitionsScreen extends StatelessWidget {
  const AppTransitionsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return SpecScreen(spec: appTransitionsSpec);
  }
}

class ScreenUnlockScreen extends StatelessWidget {
  const ScreenUnlockScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: 'Screen Unlock Animation',
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Enable Hook',
                settingKey: 'unlock_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('Animation Mode'),
          const GlassGroupedCard(
            children: [
              GlassDropdownTile(
                title: 'Unlock Animation Mode',
                settingKey: 'unlock_anim_mode',
                options: [
                  'Mode 0: iOS Radial (Outward dispersion)',
                  'Mode 1: Y-Axis Cascade (Velocity kinetics)',
                ],
                enabledKey: 'unlock_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('Grid Detection'),
          const GlassSliderTile(
            title: 'Grid Columns',
            subtitle: 'Number of icon columns detected on home screen',
            settingKey: 'unlock_grid_columns',
            min: 2,
            max: 8,
            divisions: 6,
            unit: 'Cols',
            enabledKey: 'unlock_enabled',
          ),
          const GlassSliderTile(
            title: 'Grid Rows',
            subtitle:
                'Number of horizontal icon rows detected on home screen',
            settingKey: 'unlock_grid_rows',
            min: 3,
            max: 12,
            divisions: 9,
            unit: 'Rows',
            enabledKey: 'unlock_enabled',
          ),

          SettingListener<int>(
            settingKey: 'unlock_anim_mode',
            getter: (s) => s.getInt('unlock_anim_mode', 1),
            builder: (ctx, animMode) {
              if (animMode == 0) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const GlassSectionHeader('Radial Fine Tuning'),
                    GlassGroupedCard(
                      children: [
                        GlassNavTile(
                          title: 'Radial Spring Kinetics',
                          subtitle:
                              'Center damping, edge delay & 3D perspective',
                          enabledKey: 'unlock_enabled',
                          destination: (_) => SpecScreen(
                            spec: screenUnlockRadialKineticsSpec,
                          ),
                        ),
                        GlassNavTile(
                          title: 'Radial Dock Settling',
                          subtitle: 'Dock icons bounce & arrival settling',
                          enabledKey: 'unlock_enabled',
                          destination: (_) => SpecScreen(
                            spec: screenUnlockRadialDockSpec,
                          ),
                        ),
                      ],
                    ),
                    const GlassSectionFooter(
                      'Mode 0 applies an iOS-style radial bloom dispersion radiating outward from the lock screen touch point.',
                    ),
                  ],
                );
              } else {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const GlassSectionHeader('Cascade Fine Tuning'),
                    GlassGroupedCard(
                      children: [
                        GlassNavTile(
                          title: 'Global Kinetics & Velocity',
                          subtitle:
                              'Duration scale, velocity influence & resting damping',
                          enabledKey: 'unlock_enabled',
                          destination: (_) => SpecScreen(
                            spec: screenUnlockCascadeGlobalsSpec,
                          ),
                        ),
                        GlassNavTile(
                          title: 'Kinetic Dock Isolation',
                          subtitle:
                              'Dock damping, arrival response & 3D depth',
                          enabledKey: 'unlock_enabled',
                          destination: (_) => SpecScreen(
                            spec: screenUnlockCascadeDockSpec,
                          ),
                        ),
                        GlassNavTile(
                          title: 'Row 3D Perspective (Z-Depth)',
                          subtitle: 'Row-by-row Z-axis camera elevation',
                          enabledKey: 'unlock_enabled',
                          destination: (_) => SpecScreen(
                            spec: screenUnlockCascadeZDepthSpec,
                          ),
                        ),
                        GlassNavTile(
                          title: 'Row Velocity Bounce',
                          subtitle:
                              'Center vs outer dynamic overshoot ratios',
                          enabledKey: 'unlock_enabled',
                          destination: (_) => SpecScreen(
                            spec: screenUnlockCascadeBounceSpec,
                          ),
                        ),
                        GlassNavTile(
                          title: 'Row Settling Dynamics',
                          subtitle:
                              'Center vs outer dynamic deceleration timing',
                          enabledKey: 'unlock_enabled',
                          destination: (_) => SpecScreen(
                            spec: screenUnlockCascadeSettleSpec,
                          ),
                        ),
                      ],
                    ),
                    const GlassSectionFooter(
                      'Mode 1 cascades rows downwards along the Y-axis with velocity-coupled spring dynamics and 3D depth perspective.',
                    ),
                  ],
                );
              }
            },
          ),
        ],
      ),
    );
  }
}
