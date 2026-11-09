import 'package:flutter/cupertino.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';

class ControlCenterScreen extends StatelessWidget {
  const ControlCenterScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final masterEnabled = store.getBool('cc_master_enabled', false);
        final modularEdit = store.getBool('cc_modular_edit_enabled', true);
        final landscapeRows =
            store.getInt('cc_landscape_right_max_rows', 3);
        final fluidResize = store.getBool('cc_fluid_resize_enabled', true);

        final animMode = store.getInt('cc_anim_engine_mode', 0);
        final centerBounce = store.getBool('cc_anim_enabled', true);
        final jellyEnabled = store.getBool('cc_jelly_enabled', true);

        final stretchThreshold =
            store.getDouble('cc_stretch_threshold', 1.35);
        final spreadRatio = store.getDouble('cc_spread_ratio', 1.25);
        final releaseDamping =
            store.getDouble('cc_spread_release_damping', 0.82);
        final settlingTime = store.getDouble('cc_settling_time', 0.65);
        final tileDelay = store.getDouble('cc_tile_delay_scale', 1.0);

        final flashlightUnlock =
            store.getBool('cc_low_end_flashlight_enabled', false);
        final flashlightBrightness =
            store.getDouble('cc_flashlight_max_brightness', 500.0);

        return AppScaffold(
          title: 'Control Centre',
          body: ListView(
            padding: const EdgeInsets.only(top: 12, bottom: 60),
            children: [
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Enable Hook',
                    value: masterEnabled,
                    onChanged: (val) =>
                        store.setBool('cc_master_enabled', val),
                  ),
                ],
              ),

              const GlassSectionHeader('Modular Control Center Edit'),
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Unlock Control Center Edit Page',
                    subtitle:
                        'Unlocks all control center tiles to be resized and grouped dynamically',
                    value: modularEdit,
                    enabled: masterEnabled,
                    onChanged: (val) =>
                        store.setBool('cc_modular_edit_enabled', val),
                  ),
                ],
              ),
              if (modularEdit) ...[
                GlassSliderTile(
                  title: 'Landscape Right Panel Rows',
                  subtitle:
                      'Maximum row height allocated to the right side in landscape mode (Divider position in Edit Mode)',
                  value: landscapeRows.toDouble(),
                  min: 1,
                  max: 8,
                  divisions: 7,
                  unit: 'Rows',
                  enabled: masterEnabled,
                  onChanged: (val) =>
                      store.setInt('cc_landscape_right_max_rows', val.toInt()),
                ),
                GlassGroupedCard(
                  children: [
                    GlassSwitchTile(
                      title: 'Enhanced Tile Resize Handling',
                      subtitle:
                          'Real-time tile expansion with dynamic neighbour displacement during resize drag',
                      value: fluidResize,
                      enabled: masterEnabled,
                      onChanged: (val) =>
                          store.setBool('cc_fluid_resize_enabled', val),
                    ),
                  ],
                ),
              ],

              const GlassSectionHeader('Animations'),
              GlassGroupedCard(
                children: [
                  GlassDropdownTile(
                    title: 'Animation Engine',
                    subtitle: 'Control center & shade motion',
                    options: const ['High-End', 'Low-End'],
                    selectedIndex: animMode.clamp(0, 1),
                    enabled: masterEnabled,
                    onSelect: (idx) =>
                        store.setInt('cc_anim_engine_mode', idx),
                  ),
                  if (animMode == 0)
                    GlassSwitchTile(
                      title: 'Center Bounce Animation',
                      subtitle:
                          'Emerge outward from screen center with harmonic spring physics',
                      value: centerBounce,
                      enabled: masterEnabled,
                      onChanged: (val) =>
                          store.setBool('cc_anim_enabled', val),
                    ),
                  GlassSwitchTile(
                    title: 'Jelly Physics Engine',
                    subtitle:
                        'Velocity-coupled Poisson lateral squish & top-anchored stretch',
                    value: jellyEnabled,
                    enabled: masterEnabled,
                    onChanged: (val) =>
                        store.setBool('cc_jelly_enabled', val),
                  ),
                ],
              ),
              if (jellyEnabled) ...[
                GlassSliderTile(
                  title: 'Pull Stretch Maximum Threshold',
                  subtitle:
                      'Caps maximum elongation of components during downward overstretch pull',
                  value: stretchThreshold,
                  min: 1.0,
                  max: 2.5,
                  enabled: masterEnabled,
                  onChanged: (val) =>
                      store.setDouble('cc_stretch_threshold', val),
                ),
                GlassSliderTile(
                  title: 'Rebound Squish & Bounce Intensity',
                  subtitle:
                      'Multiplies physical deformation amplitude of Poisson lateral squish and settling bounce',
                  value: spreadRatio,
                  min: 0.5,
                  max: 3.0,
                  unit: 'x',
                  enabled: masterEnabled,
                  onChanged: (val) => store.setDouble('cc_spread_ratio', val),
                ),
                GlassSliderTile(
                  title: 'Release Rebound Damping',
                  subtitle:
                      'Elasticity and settling bounce of tiles upon pull-down and overstretch release',
                  value: releaseDamping,
                  min: 0.2,
                  max: 1.5,
                  enabled: masterEnabled,
                  onChanged: (val) =>
                      store.setDouble('cc_spread_release_damping', val),
                ),
                GlassSliderTile(
                  title: 'Rebound Settling Time',
                  subtitle:
                      'Duration for release rebound oscillations and wave ripples to settle to rest',
                  value: settlingTime,
                  min: 0.2,
                  max: 2.5,
                  unit: 's',
                  enabled: masterEnabled,
                  onChanged: (val) => store.setDouble('cc_settling_time', val),
                ),
                GlassSliderTile(
                  title: 'Row Ripple Wave Propagation',
                  subtitle:
                      'Cascading delay between rows during overstretch and release rebound waves',
                  value: tileDelay,
                  min: 0.2,
                  max: 3.0,
                  unit: 'x',
                  enabled: masterEnabled,
                  onChanged: (val) =>
                      store.setDouble('cc_tile_delay_scale', val),
                ),
              ],

              const GlassSectionHeader('Flashlight Unlock'),
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Unlock Flashlight Brightness Control',
                    subtitle:
                        'Unlocks flashlight brightness control for low-end devices',
                    value: flashlightUnlock,
                    enabled: masterEnabled,
                    onChanged: (val) => store.setBool(
                        'cc_low_end_flashlight_enabled', val),
                  ),
                ],
              ),
              if (flashlightUnlock)
                GlassSliderTile(
                  title: 'Maximum Brightness Limit',
                  subtitle: 'Caps maximum brightness across the slider',
                  value: flashlightBrightness,
                  min: 100,
                  max: 1000,
                  divisions: 9,
                  enabled: masterEnabled,
                  onChanged: (val) =>
                      store.setDouble('cc_flashlight_max_brightness', val),
                ),

              const GlassSectionHeader('Layout Actions'),
              GlassGroupedCard(
                children: [
                  GlassActionTile(
                    title: 'Reset Layout to Stock',
                    subtitle:
                        'Dissolves all tile groups and restores stock component positions',
                    titleColor: CupertinoColors.systemRed,
                    enabled: masterEnabled,
                    onTap: () {
                      showCupertinoDialog(
                        context: context,
                        builder: (ctx) => CupertinoAlertDialog(
                          title: const Text('Reset Layout to Stock?'),
                          message: const Text(
                              'This will reset the custom sizing of all tiles, media player and sliders to stock, and dissolve all dynamic groups.'),
                          actions: [
                            CupertinoDialogAction(
                              child: const Text('Cancel'),
                              onPressed: () => Navigator.of(ctx).pop(),
                            ),
                            CupertinoDialogAction(
                              isDestructiveAction: true,
                              child: const Text('Reset'),
                              onPressed: () {
                                Navigator.of(ctx).pop();
                                store.setBool('cc_pending_reset_layout', true);
                              },
                            ),
                          ],
                        ),
                      );
                    },
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
