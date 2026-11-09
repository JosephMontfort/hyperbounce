import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';

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
                onTap: () {
                  Navigator.of(context).push(
                    CupertinoPageRoute(
                        builder: (_) => const AppTransitionsScreen()),
                  );
                },
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
                onTap: () {
                  Navigator.of(context).push(
                    CupertinoPageRoute(
                        builder: (_) => const ScreenUnlockScreen()),
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

class AppTransitionsScreen extends StatelessWidget {
  const AppTransitionsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final enabled = store.getBool('ios_anim_enabled', false);
        final arcLift = store.getDouble('arc_apex_lift', 24.0);
        final surgeLift = store.getDouble('open_surge_lift', 18.0);
        final openSurge = store.getBool('open_uplift_surge', true);
        final closeSurge = store.getBool('close_uplift_surge', true);
        final keystone = store.getBool('keystone_squeeze', true);
        final speed = store.getDouble('anim_speed', 1.0);
        final openDamping = store.getDouble('open_spring_damping', 0.82);
        final closeDamping = store.getDouble('close_spring_damping', 0.86);
        final folderTracking = store.getBool('folder_tracking', true);
        final recentsCentering = store.getBool('recents_centering', true);

        return AppScaffold(
          title: 'App Transitions',
          body: ListView(
            padding: const EdgeInsets.only(top: 12, bottom: 60),
            children: [
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Enable Enhanced Animation Engine',
                    subtitle:
                        'Enhances app open and close animations (not recommended)',
                    value: enabled,
                    onChanged: (val) =>
                        store.setBool('ios_anim_enabled', val),
                  ),
                ],
              ),

              const GlassSectionHeader('Spring Dynamics & Speed'),
              GlassSliderTile(
                title: 'Animation Speed',
                subtitle: 'Global motion multiplier for transition timing',
                value: speed,
                min: 0.5,
                max: 2.0,
                unit: 'x',
                enabled: enabled,
                onChanged: (val) => store.setDouble('anim_speed', val),
              ),
              GlassSliderTile(
                title: 'Open Spring Damping',
                subtitle: 'Harmonic settling bounce when an app window opens',
                value: openDamping,
                min: 0.3,
                max: 1.2,
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('open_spring_damping', val),
              ),
              GlassSliderTile(
                title: 'Close Spring Damping',
                subtitle: 'Harmonic settling bounce when returning to homescreen',
                value: closeDamping,
                min: 0.3,
                max: 1.2,
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('close_spring_damping', val),
              ),

              const GlassSectionHeader('Arc & Surge Physics'),
              GlassSliderTile(
                title: 'Arc Apex Lift',
                subtitle: 'Curved parabolic lift height during gesture transit',
                value: arcLift,
                min: 0,
                max: 100,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) => store.setDouble('arc_apex_lift', val),
              ),
              GlassSliderTile(
                title: 'Open Surge Lift',
                subtitle: 'Momentum elevation surge upon opening application',
                value: surgeLift,
                min: 0,
                max: 100,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) => store.setDouble('open_surge_lift', val),
              ),

              const GlassSectionHeader('Motion Features'),
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Open Uplift Surge',
                    value: openSurge,
                    enabled: enabled,
                    onChanged: (val) =>
                        store.setBool('open_uplift_surge', val),
                  ),
                  GlassSwitchTile(
                    title: 'Close Uplift Surge',
                    value: closeSurge,
                    enabled: enabled,
                    onChanged: (val) =>
                        store.setBool('close_uplift_surge', val),
                  ),
                  GlassSwitchTile(
                    title: 'Keystone Squeeze',
                    subtitle: 'Perspective 3D deformation during swipe exit',
                    value: keystone,
                    enabled: enabled,
                    onChanged: (val) => store.setBool('keystone_squeeze', val),
                  ),
                  GlassSwitchTile(
                    title: 'Folder Tracking',
                    value: folderTracking,
                    enabled: enabled,
                    onChanged: (val) => store.setBool('folder_tracking', val),
                  ),
                  GlassSwitchTile(
                    title: 'Recents Centering',
                    value: recentsCentering,
                    enabled: enabled,
                    onChanged: (val) =>
                        store.setBool('recents_centering', val),
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

class ScreenUnlockScreen extends StatelessWidget {
  const ScreenUnlockScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final enabled = store.getBool('unlock_enabled', false);
        final cols = store.getDouble('unlock_grid_columns', 4.0);
        final rows = store.getDouble('unlock_grid_rows', 6.0);
        final velocityInfluence =
            store.getDouble('unlock_velocity_influence', 1.0);
        final maxVelocity =
            store.getDouble('unlock_max_velocity', 9000.0);
        final cascadeDamping =
            store.getDouble('unlock_cascade_damping', 0.86);
        final durationScale =
            store.getDouble('unlock_duration_scale', 1.0);
        final dockDamping =
            store.getDouble('unlock_kinetic_dock_damping', 1.0);
        final dockResponse =
            store.getDouble('unlock_kinetic_dock_response', 0.55);
        final dockZDepth =
            store.getDouble('unlock_kinetic_dock_z_depth', 800.0);

        return AppScaffold(
          title: 'Screen Unlock Animation',
          body: ListView(
            padding: const EdgeInsets.only(top: 12, bottom: 60),
            children: [
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Velocity Coupled Screen Unlock',
                    value: enabled,
                    onChanged: (val) => store.setBool('unlock_enabled', val),
                  ),
                ],
              ),

              const GlassSectionHeader('Grid Dimensions'),
              GlassSliderTile(
                title: 'Grid Columns',
                value: cols,
                min: 2,
                max: 8,
                divisions: 6,
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('unlock_grid_columns', val),
              ),
              GlassSliderTile(
                title: 'Grid Rows',
                value: rows,
                min: 3,
                max: 12,
                divisions: 9,
                enabled: enabled,
                onChanged: (val) => store.setDouble('unlock_grid_rows', val),
              ),

              const GlassSectionHeader('Kinetic Cascade'),
              GlassSliderTile(
                title: 'Velocity Influence',
                subtitle: 'Coupling ratio between swipe velocity and icon flight',
                value: velocityInfluence,
                min: 0,
                max: 5.0,
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('unlock_velocity_influence', val),
              ),
              GlassSliderTile(
                title: 'Maximum Velocity',
                value: maxVelocity,
                min: 1000,
                max: 25000,
                unit: 'px/s',
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('unlock_max_velocity', val),
              ),
              GlassSliderTile(
                title: 'Cascade Damping',
                subtitle: 'Harmonic vibration damping for cascading rows',
                value: cascadeDamping,
                min: 0.3,
                max: 1.5,
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('unlock_cascade_damping', val),
              ),
              GlassSliderTile(
                title: 'Duration Scale',
                value: durationScale,
                min: 0.1,
                max: 3.0,
                unit: 'x',
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('unlock_duration_scale', val),
              ),

              const GlassSectionHeader('Kinetic Dock Dynamics'),
              GlassSliderTile(
                title: 'Kinetic Dock Damping',
                value: dockDamping,
                min: 0.1,
                max: 1.5,
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('unlock_kinetic_dock_damping', val),
              ),
              GlassSliderTile(
                title: 'Kinetic Dock Response',
                value: dockResponse,
                min: 0.1,
                max: 1.5,
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('unlock_kinetic_dock_response', val),
              ),
              GlassSliderTile(
                title: 'Kinetic Dock Z-Depth',
                value: dockZDepth,
                min: 0,
                max: 2000,
                unit: 'px',
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('unlock_kinetic_dock_z_depth', val),
              ),
            ],
          ),
        );
      },
    );
  }
}
