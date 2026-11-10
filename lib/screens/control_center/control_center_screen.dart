import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';

class ControlCenterScreen extends StatelessWidget {
  const ControlCenterScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: 'Control Centre',
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Enable Hook',
                settingKey: 'cc_master_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('Modular Control Center Edit'),
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Unlock Control Center Edit Page',
                subtitle:
                    'Unlocks all control center tiles to be resized and grouped dynamically',
                settingKey: 'cc_modular_edit_enabled',
                enabledKey: 'cc_master_enabled',
              ),
            ],
          ),
          const GlassSliderTile(
            title: 'Landscape Right Panel Rows',
            subtitle:
                'Maximum row height allocated to the right side in landscape mode (Divider position in Edit Mode)',
            settingKey: 'cc_landscape_right_max_rows',
            min: 1,
            max: 8,
            divisions: 7,
            unit: 'Rows',
            enabledKey: 'cc_modular_edit_enabled',
          ),
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Enhanced Tile Resize Handling',
                subtitle:
                    'Real-time tile expansion with dynamic neighbour displacement during resize drag',
                settingKey: 'cc_fluid_resize_enabled',
                enabledKey: 'cc_modular_edit_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('Animations'),
          const GlassGroupedCard(
            children: [
              GlassDropdownTile(
                title: 'Animation Engine',
                settingKey: 'cc_anim_engine_mode',
                options: [
                  'Spring Dynamics',
                  'Linear Interpolation',
                  'Cascading Wave',
                ],
                enabledKey: 'cc_master_enabled',
              ),
              GlassSwitchTile(
                title: 'Center Cascade Bounce',
                subtitle: 'Dynamic bounce upon pulling down Control Center',
                settingKey: 'cc_anim_enabled',
                enabledKey: 'cc_master_enabled',
              ),
              GlassSwitchTile(
                title: 'Liquid Jelly Physics',
                subtitle:
                    'Tiles respond organically to drag inertia and overshoot',
                settingKey: 'cc_jelly_enabled',
                enabledKey: 'cc_master_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('Physics Kinetics'),
          const GlassSliderTile(
            title: 'Touch Drag Stretch Ratio',
            subtitle:
                'Vertical displacement elastic coefficient during pull gesture',
            settingKey: 'cc_stretch_threshold',
            min: 1.0,
            max: 2.0,
            unit: 'x',
            enabledKey: 'cc_master_enabled',
          ),
          const GlassSliderTile(
            title: 'Spread Cascade Spacing',
            subtitle:
                'Distance gap between cascading cards as they spread into view',
            settingKey: 'cc_spread_ratio',
            min: 0.8,
            max: 2.0,
            unit: 'x',
            enabledKey: 'cc_master_enabled',
          ),
          const GlassSliderTile(
            title: 'Release Spring Damping',
            subtitle:
                'Resistance ratio when snapping cards into resting alignment',
            settingKey: 'cc_spread_release_damping',
            min: 0.4,
            max: 1.2,
            enabledKey: 'cc_master_enabled',
          ),
          const GlassSliderTile(
            title: 'Cascade Settling Duration',
            subtitle: 'Total settling period for whole tile grid layout',
            settingKey: 'cc_settling_time',
            min: 0.2,
            max: 1.5,
            unit: 's',
            enabledKey: 'cc_master_enabled',
          ),
          const GlassSliderTile(
            title: 'Tile Emergence Delay Scale',
            subtitle:
                'Stagger interval offset between sequential tile arrivals',
            settingKey: 'cc_tile_delay_scale',
            min: 0.5,
            max: 2.5,
            unit: 'x',
            enabledKey: 'cc_master_enabled',
          ),

          const GlassSectionHeader('Low-End Device Flashlight'),
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Unlock Flashlight Slider',
                subtitle:
                    'Forces multi-level brightness slider support on entry-level devices',
                settingKey: 'cc_low_end_flashlight_enabled',
                enabledKey: 'cc_master_enabled',
              ),
            ],
          ),
          const GlassSliderTile(
            title: 'Max Brightness Level',
            subtitle: 'Upper current clamp ceiling for torch diode',
            settingKey: 'cc_flashlight_max_brightness',
            min: 100,
            max: 1000,
            divisions: 9,
            unit: 'Lvl',
            enabledKey: 'cc_low_end_flashlight_enabled',
          ),

          const GlassSectionHeader('Reset Options'),
          GlassGroupedCard(
            children: [
              GlassActionTile(
                title: 'Reset Modular Grid Layout',
                subtitle:
                    'Reverts tile dimensions and order back to system defaults',
                titleColor: CupertinoColors.systemRed,
                enabledKey: 'cc_master_enabled',
                onTap: () {
                  GlassDialog.show(
                    context: context,
                    title: 'Reset Control Center Layout?',
                    message:
                        'This will reset all tiles to default positions and sizes.',
                    actions: [
                      GlassDialogAction(
                        label: 'Cancel',
                        onPressed: () => Navigator.of(context).pop(),
                      ),
                      GlassDialogAction(
                        label: 'Reset',
                        isDestructive: true,
                        onPressed: () {
                          SettingsStore.instance
                              .setBool('cc_pending_reset_layout', true);
                          Navigator.of(context).pop();
                        },
                      ),
                    ],
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
