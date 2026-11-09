import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../../state/settings_store.dart';

class LauncherScreen extends StatelessWidget {
  const LauncherScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;

    return AppScaffold(
      title: 'Launcher & Unlock',
      showTopMenu: false,
      body: ListenableBuilder(
        listenable: store,
        builder: (context, _) {
          final mode = store.getString('screen_unlock_mode', 'Cascade');
          final cascadeBounce = store.getDouble('unlock_cascade_bounce', 1.15);
          final cascadeSettle = store.getDouble('unlock_cascade_settle', 0.85);
          final zDepth = store.getDouble('unlock_cascade_z_depth', 350.0);
          final radialVelocity = store.getDouble('unlock_radial_velocity', 1.4);
          final radialDock = store.getBool('unlock_radial_dock_enabled', true);
          final appTransitions = store.getBool('app_transitions_enabled', true);

          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              const GlassSectionHeader('Screen Unlock Animation Mode'),
              GlassGroupedSection(
                children: [
                  GlassListTile(
                    title: const Text('Kinetics Profile'),
                    subtitle: Text('Current: $mode Mode'),
                    trailing: CupertinoSlidingSegmentedControl<String>(
                      groupValue: mode,
                      children: const {
                        'Cascade': Padding(
                          padding: EdgeInsets.symmetric(horizontal: 10),
                          child: Text('Cascade', style: TextStyle(fontSize: 13)),
                        ),
                        'Radial': Padding(
                          padding: EdgeInsets.symmetric(horizontal: 10),
                          child: Text('Radial', style: TextStyle(fontSize: 13)),
                        ),
                      },
                      onValueChanged: (v) {
                        if (v != null) store.setString('screen_unlock_mode', v);
                      },
                    ),
                  ),
                  GlassSwitchTile(
                    title: 'App Open/Close Transitions',
                    subtitle: 'Fluid iOS spring scale-in from launcher icon coordinates',
                    value: appTransitions,
                    onChanged: (v) => store.setBool('app_transitions_enabled', v),
                  ),
                ],
              ),

              const GlassSectionHeader('Cascade Kinetics (Row by Row)'),
              GlassSliderTile(
                title: 'Row Cascade Bounce',
                subtitle: 'Overshoot amplitude for icon grid row bounce',
                value: cascadeBounce,
                min: 0.5,
                max: 2.0,
                onChanged: (v) => store.setDouble('unlock_cascade_bounce', v),
              ),
              GlassSliderTile(
                title: 'Row Settle Stiffness',
                subtitle: 'Damping settlement rate across rows',
                value: cascadeSettle,
                min: 0.3,
                max: 1.5,
                onChanged: (v) => store.setDouble('unlock_cascade_settle', v),
              ),
              GlassSliderTile(
                title: '3D Z-Depth Fly-In',
                subtitle: 'Virtual perspective distance for icons flying in from depth',
                value: zDepth,
                min: 100.0,
                max: 800.0,
                unit: 'px',
                onChanged: (v) => store.setDouble('unlock_cascade_z_depth', v),
              ),

              const GlassSectionHeader('Radial Kinetics (From Biometric Sensor)'),
              GlassGroupedSection(
                children: [
                  GlassSwitchTile(
                    title: 'Include Dock in Wave',
                    subtitle: 'Propagate radial shockwave down into the launcher dock',
                    value: radialDock,
                    onChanged: (v) => store.setBool('unlock_radial_dock_enabled', v),
                  ),
                ],
              ),
              GlassSliderTile(
                title: 'Radial Wave Velocity',
                subtitle: 'Speed of shockwave propagating from fingerprint point',
                value: radialVelocity,
                min: 0.5,
                max: 3.0,
                onChanged: (v) => store.setDouble('unlock_radial_velocity', v),
              ),
              const SizedBox(height: 100),
            ],
          );
        },
      ),
    );
  }
}
