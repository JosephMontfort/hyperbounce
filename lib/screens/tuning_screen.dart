import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../widgets/setting_tiles.dart';
import 'nav_bars/apps_list_screen.dart';
import 'toggles_sliders/toggles_screen.dart';
import 'toggles_sliders/sliders_screen.dart';
import 'lockscreen/lockscreen_screen.dart';
import 'launcher/launcher_screen.dart';
import 'control_center/control_center_screen.dart';
import 'caller/caller_screen.dart';
import 'haptics/haptic_studio_screen.dart';
import 'safemode/safe_mode_screen.dart';

class TuningScreen extends StatelessWidget {
  const TuningScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      children: [
        const GlassSectionHeader('Interface Modules'),
        GlassGroupedSection(
          children: [
            GlassNavTile(
              leading: const Icon(CupertinoIcons.compass, color: CupertinoColors.activeBlue),
              title: 'Floating Navigation Bars',
              subtitle: 'Instagram, WhatsApp, YouTube liquid glass tabs',
              onTap: () {
                Navigator.of(context).push(
                  CupertinoPageRoute(builder: (_) => const AppsListScreen()),
                );
              },
            ),
            GlassNavTile(
              leading: const Icon(CupertinoIcons.switch_camera, color: CupertinoColors.activeGreen),
              title: 'Liquid Toggles',
              subtitle: 'iOS jump animation, spring physics, glass rim',
              onTap: () {
                Navigator.of(context).push(
                  CupertinoPageRoute(builder: (_) => const TogglesScreen()),
                );
              },
            ),
            GlassNavTile(
              leading: const Icon(CupertinoIcons.slider_horizontal_3, color: CupertinoColors.systemOrange),
              title: 'Liquid Sliders',
              subtitle: 'Jelly squash/stretch thumb, refraction, haptic ticks',
              onTap: () {
                Navigator.of(context).push(
                  CupertinoPageRoute(builder: (_) => const SlidersScreen()),
                );
              },
            ),
          ],
        ),

        const GlassSectionHeader('System & Lockscreen'),
        GlassGroupedSection(
          children: [
            GlassNavTile(
              leading: const Icon(CupertinoIcons.lock_shield, color: CupertinoColors.systemPurple),
              title: 'Lockscreen & Pinpad',
              subtitle: 'Liquid sheet, lighting, bloom, button stretch',
              onTap: () {
                Navigator.of(context).push(
                  CupertinoPageRoute(builder: (_) => const LockscreenScreen()),
                );
              },
            ),
            GlassNavTile(
              leading: const Icon(CupertinoIcons.circle_grid_hex, color: CupertinoColors.systemIndigo),
              title: 'Launcher & Unlock Kinetics',
              subtitle: 'Cascade bounce, radial kinetics, 3D z-depth',
              onTap: () {
                Navigator.of(context).push(
                  CupertinoPageRoute(builder: (_) => const LauncherScreen()),
                );
              },
            ),
            GlassNavTile(
              leading: const Icon(CupertinoIcons.rectangle_grid_2x2, color: CupertinoColors.systemPink),
              title: 'Control Center & Flashlight',
              subtitle: 'Tile grouping, fluid resize, 2D grid engine',
              onTap: () {
                Navigator.of(context).push(
                  CupertinoPageRoute(builder: (_) => const ControlCenterScreen()),
                );
              },
            ),
          ],
        ),

        const GlassSectionHeader('Calling & Feedback'),
        GlassGroupedSection(
          children: [
            GlassNavTile(
              leading: const Icon(CupertinoIcons.phone_fill, color: CupertinoColors.systemTeal),
              title: 'Caller Screen & Dialer',
              subtitle: 'iOS incoming call slider, swipe gestures',
              onTap: () {
                Navigator.of(context).push(
                  CupertinoPageRoute(builder: (_) => const CallerScreen()),
                );
              },
            ),
            GlassNavTile(
              leading: const Icon(CupertinoIcons.waveform, color: CupertinoColors.systemYellow),
              title: 'Haptic Studio',
              subtitle: 'DSP synthesizer, synchronized ringtone haptics',
              onTap: () {
                Navigator.of(context).push(
                  CupertinoPageRoute(builder: (_) => const HapticStudioScreen()),
                );
              },
            ),
            GlassNavTile(
              leading: const Icon(CupertinoIcons.shield_lefthalf_fill, color: CupertinoColors.systemRed),
              title: 'Safe Mode & Diagnostics',
              subtitle: 'Crash protection, loop detection, overrides',
              onTap: () {
                Navigator.of(context).push(
                  CupertinoPageRoute(builder: (_) => const SafeModeScreen()),
                );
              },
            ),
          ],
        ),
        const SizedBox(height: 100),
      ],
    );
  }
}
