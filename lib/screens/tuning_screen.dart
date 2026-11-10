import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../state/settings_store.dart';
import '../widgets/setting_tiles.dart';
import 'spec/all_specs.dart';
import 'spec/spec_screen.dart';

import 'toggles_sliders/engine_screen.dart';
import 'lockscreen/lockscreen_stuffs_screen.dart';
import 'launcher/system_launcher_stuffs_screen.dart';
import 'notification/notification_center_screen.dart';
import 'control_center/control_center_screen.dart';
import 'nav_bars/apps_list_screen.dart';
import 'caller/caller_screen.dart';
import 'haptics/haptic_studio_screen.dart';

class TuningFeatureItem {
  final String title;
  final String description;
  final IconData icon;
  final Color iconColor;
  final Widget Function() destinationBuilder;

  const TuningFeatureItem({
    required this.title,
    required this.description,
    required this.icon,
    required this.iconColor,
    required this.destinationBuilder,
  });
}

class TuningScreen extends StatefulWidget {
  const TuningScreen({super.key});

  @override
  State<TuningScreen> createState() => _TuningScreenState();
}

class _TuningScreenState extends State<TuningScreen> {
  String _searchQuery = '';

  static final List<TuningFeatureItem> _features = [
    TuningFeatureItem(
      title: 'Toggles and Sliders',
      description: 'iOS 26 style toggles and sliders in system apps',
      icon: CupertinoIcons.slider_horizontal_3,
      iconColor: const Color(0xFF34C759),
      destinationBuilder: () => const EngineScreen(),
    ),
    TuningFeatureItem(
      title: 'LockScreen Stuffs',
      description: 'iOS style liquid glass aesthetics on lockscreen and Pinpad',
      icon: CupertinoIcons.lock_shield,
      iconColor: const Color(0xFFAF52DE),
      destinationBuilder: () => const LockscreenStuffsScreen(),
    ),
    TuningFeatureItem(
      title: 'System Launcher Stuffs',
      description: 'App transitions and Screen Unlock animation tuning',
      icon: CupertinoIcons.square_grid_2x2,
      iconColor: const Color(0xFF5856D6),
      destinationBuilder: () => const SystemLauncherStuffsScreen(),
    ),
    TuningFeatureItem(
      title: 'Notification Centre',
      description: 'iOS 26 inspired aesthetics on Notification centre',
      icon: CupertinoIcons.bell_fill,
      iconColor: const Color(0xFFFF9500),
      destinationBuilder: () => const NotificationCenterScreen(),
    ),
    TuningFeatureItem(
      title: 'Control Centre',
      description: 'Enhanced Control centre customisation',
      icon: CupertinoIcons.slider_horizontal_below_rectangle,
      iconColor: const Color(0xFFFF2D55),
      destinationBuilder: () => const ControlCenterScreen(),
    ),
    TuningFeatureItem(
      title: 'Floating Navigation Bar',
      description:
          'Liquid Glass Floating navigation bar in Instagram, WhatsApp and Youtube',
      icon: CupertinoIcons.compass,
      iconColor: const Color(0xFF007AFF),
      destinationBuilder: () => const AppsListScreen(),
    ),
    TuningFeatureItem(
      title: 'Dialer and Caller Screen',
      description:
          'Swipe gestures on dialer and iOS style full screen incoming calls',
      icon: CupertinoIcons.phone_fill,
      iconColor: const Color(0xFF30B0C7),
      destinationBuilder: () => const CallerScreen(),
    ),
    TuningFeatureItem(
      title: 'Haptic Studio',
      description:
          'Compose synced haptics for music to be set as custom Ringtones',
      icon: CupertinoIcons.waveform,
      iconColor: const Color(0xFFFFCC00),
      destinationBuilder: () => const HapticStudioScreen(),
    ),
  ];

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;
    final isModuleActivated = store.isBound;
    final q = _searchQuery.trim().toLowerCase();

    final filteredFeatures = q.isEmpty
        ? _features
        : _features.where((f) {
            return f.title.toLowerCase().contains(q) ||
                f.description.toLowerCase().contains(q);
          }).toList();

    final matchingSpecs = q.isEmpty
        ? const <ScreenSpec>[]
        : allRegisteredSpecs.where((s) {
            if (s.title.toLowerCase().contains(q)) return true;
            if (s.subtitle?.toLowerCase().contains(q) ?? false) return true;
            for (final sec in s.sections) {
              if (sec.header?.toLowerCase().contains(q) ?? false) return true;
              for (final item in sec.items) {
                if (item.title.toLowerCase().contains(q)) return true;
                if (item.subtitle?.toLowerCase().contains(q) ?? false) return true;
              }
            }
            return false;
          }).toList();

    return ListView(
      padding: const EdgeInsets.only(top: 8, bottom: 120),
      children: [
        // LSPosed Module Binding Status Warning
        if (!isModuleActivated)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            child: GlassContainer.card(
              surfaceStyle: GlassSurfaceStyle.solid,
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: const [
                  Text(
                    'This module is not activated',
                    style: TextStyle(
                      fontSize: 16.5,
                      fontWeight: FontWeight.bold,
                      color: CupertinoColors.systemRed,
                    ),
                  ),
                  SizedBox(height: 4),
                  Text(
                    'Please enable this module in LSPosed and restart this app.',
                    style: TextStyle(
                      fontSize: 13.5,
                      color: CupertinoColors.secondaryLabel,
                    ),
                  ),
                ],
              ),
            ),
          ),

        // Search Bar
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          child: GlassSearchBar(
            placeholder: 'Search settings, optics & kinetics',
            surfaceStyle: GlassSurfaceStyle.solid,
            onChanged: (val) => setState(() => _searchQuery = val),
          ),
        ),

        const SizedBox(height: 4),

        // Matching Specs Section (when searching)
        if (matchingSpecs.isNotEmpty) ...[
          const GlassSectionHeader('Settings & Fine Tuning Pages'),
          GlassGroupedCard(
            children: matchingSpecs.map((spec) {
              return GlassListTile(
                leading: Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: const Color(0xFF007AFF).withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(CupertinoIcons.slider_horizontal_3,
                      size: 18, color: Color(0xFF007AFF)),
                ),
                title: Text(
                  spec.title,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                subtitle: spec.subtitle != null
                    ? Text(
                        spec.subtitle!,
                        style: const TextStyle(
                          fontSize: 13,
                          color: CupertinoColors.secondaryLabel,
                        ),
                      )
                    : null,
                trailing: GlassListTile.chevron,
                onTap: () {
                  Navigator.of(context).push(
                    GlassPageRoute(
                      builder: (_) => SpecScreen(spec: spec),
                    ),
                  );
                },
              );
            }).toList(),
          ),
          const SizedBox(height: 12),
        ],

        // Core Features
        if (filteredFeatures.isNotEmpty) ...[
          if (q.isNotEmpty) const GlassSectionHeader('Categories'),
          GlassGroupedCard(
            children: filteredFeatures.map((item) {
              return GlassListTile(
                leading: Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: item.iconColor.withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Icon(item.icon, size: 19, color: item.iconColor),
                ),
                title: Text(
                  item.title,
                  style: const TextStyle(
                    fontSize: 16,
                    fontWeight: FontWeight.w500,
                  ),
                ),
                subtitle: Text(
                  item.description,
                  style: const TextStyle(
                    fontSize: 13,
                    color: CupertinoColors.secondaryLabel,
                  ),
                ),
                trailing: GlassListTile.chevron,
                onTap: () {
                  Navigator.of(context).push(
                    GlassPageRoute(builder: (_) => item.destinationBuilder()),
                  );
                },
              );
            }).toList(),
          ),
        ],
      ],
    );
  }
}
