import 'package:flutter/cupertino.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';

class NotificationCenterScreen extends StatelessWidget {
  const NotificationCenterScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final masterEnabled = store.getBool('notif_master_enabled', false);
        final hunAnim =
            store.getBool('notif_ios_hun_animation_enabled', true);
        final hunDuration =
            store.getDouble('notif_ios_hun_duration_ms', 380.0);
        final hunStartScale =
            store.getDouble('notif_ios_hun_start_scale', 0.65);
        final hunSqueeze =
            store.getDouble('notif_ios_hun_squeeze_strength', 0.45);

        final sheetEnabled = store.getBool('notif_sheet_enabled', false);
        final sheetActive = masterEnabled && sheetEnabled;

        final lockWallpaper =
            store.getBool('notif_lock_wallpaper_enabled', true);
        final tracking =
            store.getBool('notif_one_to_one_tracking_enabled', true);
        final homescreenOnly =
            store.getBool('notif_lock_wallpaper_homescreen_only', false);
        final customWallpaper =
            store.getBool('notif_custom_wallpaper_enabled', false);

        final glassEffects =
            store.getBool('notif_glass_effects_enabled', false);
        final leftBevel = store.getDouble('notif_left_bevel_width', 18.0);
        final bottomBevel = store.getDouble('notif_bottom_bevel_width', 22.0);
        final cornerRadius = store.getDouble('notif_corner_radius', 32.0);

        final sideMargin = store.getDouble('notif_side_margin', 12.0);
        final topMargin = store.getDouble('notif_start_margin_top', 0.0);
        final clockScale = store.getDouble('notif_clock_scale', 1.0);
        final stackEnabled = store.getBool('notif_stack_enabled', false);
        final launcherDismiss =
            store.getBool('notif_launcher_unlock_on_dismiss', false);

        return AppScaffold(
          title: 'Notification Centre',
          body: ListView(
            padding: const EdgeInsets.only(top: 12, bottom: 60),
            children: [
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Enable Hook',
                    value: masterEnabled,
                    onChanged: (val) =>
                        store.setBool('notif_master_enabled', val),
                  ),
                ],
              ),

              const GlassSectionHeader('Pop-Up Notification Animation'),
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'iOS 26 Pop-Up Animation',
                    subtitle: 'iOS 26 style animation for pop up notifications',
                    value: hunAnim,
                    enabled: masterEnabled,
                    onChanged: (val) =>
                        store.setBool('notif_ios_hun_animation_enabled', val),
                  ),
                ],
              ),
              if (hunAnim) ...[
                GlassSliderTile(
                  title: 'Animation Speed (Duration)',
                  value: hunDuration,
                  min: 100,
                  max: 800,
                  unit: 'ms',
                  enabled: masterEnabled,
                  onChanged: (val) =>
                      store.setDouble('notif_ios_hun_duration_ms', val),
                ),
                GlassSliderTile(
                  title: 'Emergence Start Scale',
                  subtitle:
                      'Initial size when emerging outward from the center top notch',
                  value: hunStartScale,
                  min: 0.2,
                  max: 1.0,
                  unit: '%',
                  enabled: masterEnabled,
                  onChanged: (val) =>
                      store.setDouble('notif_ios_hun_start_scale', val),
                ),
                GlassSliderTile(
                  title: 'Inertia Squeeze & Stretch',
                  subtitle:
                      'Physics velocity elongation during transit and lateral squash upon landing',
                  value: hunSqueeze,
                  min: 0,
                  max: 1.0,
                  unit: '%',
                  enabled: masterEnabled,
                  onChanged: (val) =>
                      store.setDouble('notif_ios_hun_squeeze_strength', val),
                ),
              ],
              const GlassSectionFooter(
                'Applies exclusively to incoming heads-up pop-up notifications after the screen is unlocked. Does not affect lock screen notifications or open notification shade.',
              ),

              const GlassSectionHeader('Notification Center Sheet'),
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Notification Center iOS Sheet',
                    value: sheetEnabled,
                    enabled: masterEnabled,
                    onChanged: (val) =>
                        store.setBool('notif_sheet_enabled', val),
                  ),
                ],
              ),

              const GlassSectionHeader('Sheet Wallpaper Background'),
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Lock Screen Wallpaper Background',
                    subtitle:
                        'Lockscreen wallpaper attached to the sheet, rolling down with it',
                    value: lockWallpaper,
                    enabled: sheetActive,
                    onChanged: (val) =>
                        store.setBool('notif_lock_wallpaper_enabled', val),
                  ),
                  GlassSwitchTile(
                    title: '1:1 Finger Tracking',
                    subtitle:
                        'Physical 1:1 finger tracking on notification shade pulldown',
                    value: tracking,
                    enabled: sheetActive && lockWallpaper,
                    onChanged: (val) => store.setBool(
                        'notif_one_to_one_tracking_enabled', val),
                  ),
                  GlassSwitchTile(
                    title: 'Homescreen Only',
                    subtitle:
                        'Active only when pulling down over homescreen; uses transparent glass over running apps',
                    value: homescreenOnly,
                    enabled: sheetActive && lockWallpaper,
                    onChanged: (val) => store.setBool(
                        'notif_lock_wallpaper_homescreen_only', val),
                  ),
                  GlassSwitchTile(
                    title: 'Custom Background Wallpaper',
                    subtitle:
                        'Custom wallpaper displayed behind notification sheet',
                    value: customWallpaper,
                    enabled: sheetActive && lockWallpaper,
                    onChanged: (val) => store.setBool(
                        'notif_custom_wallpaper_enabled', val),
                  ),
                ],
              ),

              const GlassSectionHeader('3-Edge Liquid Glass Effects'),
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: '3-Edge Liquid Glass Effects',
                    subtitle:
                        'Real-time optical refraction, dispersion, and specular highlight along edges',
                    value: glassEffects,
                    enabled: sheetActive && lockWallpaper,
                    onChanged: (val) =>
                        store.setBool('notif_glass_effects_enabled', val),
                  ),
                ],
              ),
              if (glassEffects) ...[
                GlassSliderTile(
                  title: 'Side Edges Bevel Width',
                  subtitle:
                      'Width of the left and right optical glass bevel slopes',
                  value: leftBevel,
                  min: 0,
                  max: 50,
                  unit: 'dp',
                  enabled: sheetActive && lockWallpaper,
                  onChanged: (val) =>
                      store.setDouble('notif_left_bevel_width', val),
                ),
                GlassSliderTile(
                  title: 'Bottom Edge Bevel Width',
                  subtitle: 'Width of the bottom optical glass bevel slope',
                  value: bottomBevel,
                  min: 0,
                  max: 50,
                  unit: 'dp',
                  enabled: sheetActive && lockWallpaper,
                  onChanged: (val) =>
                      store.setDouble('notif_bottom_bevel_width', val),
                ),
                GlassSliderTile(
                  title: 'Bottom Corner Radius',
                  subtitle:
                      'Curvature radius of bottom left/right sheet corners',
                  value: cornerRadius,
                  min: 0,
                  max: 60,
                  unit: 'dp',
                  enabled: sheetActive,
                  onChanged: (val) =>
                      store.setDouble('notif_corner_radius', val),
                ),
              ],

              const GlassSectionHeader('Layout & Spacing'),
              GlassSliderTile(
                title: 'Notification Inset Margin',
                subtitle:
                    'Horizontal margin reduction so glass bevels frame notifications',
                value: sideMargin,
                min: 0,
                max: 32,
                unit: 'dp',
                enabled: sheetActive,
                onChanged: (val) => store.setDouble('notif_side_margin', val),
              ),
              GlassSliderTile(
                title: 'Notification Top Clearance Margin',
                subtitle: 'Downward margin below clock (0 dp = stock position)',
                value: topMargin,
                min: 0,
                max: 120,
                unit: 'dp',
                enabled: sheetActive,
                onChanged: (val) =>
                    store.setDouble('notif_start_margin_top', val),
              ),
              GlassSliderTile(
                title: 'Clock Size Scale',
                subtitle:
                    'Scale factor of lock screen clock in notification center',
                value: clockScale,
                min: 0.5,
                max: 1.5,
                unit: 'x',
                enabled: sheetActive,
                onChanged: (val) => store.setDouble('notif_clock_scale', val),
              ),
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Enable Stacked Notifications',
                    subtitle:
                        'Compress notifications into an iOS-style bottom stack with fluid expansion',
                    value: stackEnabled,
                    enabled: sheetActive,
                    onChanged: (val) =>
                        store.setBool('notif_stack_enabled', val),
                  ),
                ],
              ),

              const GlassSectionHeader('Screen Unlock Animation'),
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Launcher Animation on dismiss',
                    subtitle:
                        'Play the screen unlock animation on notification center dismiss',
                    value: launcherDismiss,
                    enabled: sheetActive,
                    onChanged: (val) => store.setBool(
                        'notif_launcher_unlock_on_dismiss', val),
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
