import 'package:flutter/cupertino.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../spec/all_specs.dart';
import '../spec/spec_screen.dart';

class AppDetailScreen extends StatelessWidget {
  final String displayName;
  final String packageName;

  const AppDetailScreen({
    super.key,
    required this.displayName,
    required this.packageName,
  });

  String get _appKey {
    if (packageName == 'com.instagram.android') return 'instagram';
    if (packageName == 'com.whatsapp') return 'whatsapp';
    if (packageName == 'com.google.android.youtube') return 'youtube';
    return displayName.toLowerCase();
  }

  @override
  Widget build(BuildContext context) {
    final appKey = _appKey;
    final isInstagram = appKey == 'instagram';
    final isYouTube = appKey == 'youtube';

    return AppScaffold(
      title: displayName,
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Floating Bottom Bar',
                settingKey: 'app_${appKey}_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('Configuration'),
          GlassGroupedCard(
            children: [
              GlassNavTile(
                title: 'Appearance',
                subtitle: 'Glass engine, opacity, theme & accent',
                enabledKey: 'app_${appKey}_enabled',
                destination: (_) => SpecScreen(
                  spec: createAppAppearanceSpec(displayName, appKey),
                ),
              ),
              GlassNavTile(
                title: 'Layout',
                subtitle: 'Corner radius, bar height, tab width & margin',
                enabledKey: 'app_${appKey}_enabled',
                destination: (_) => SpecScreen(
                  spec: createAppLayoutSpec(displayName, appKey),
                ),
              ),
            ],
          ),

          if (isInstagram || isYouTube) ...[
            const GlassSectionHeader('Feed Behavior'),
            GlassGroupedCard(
              children: [
                GlassSwitchTile(
                  title: 'Auto-Shrink on Scroll',
                  subtitle: 'Shrinks and docks down when scrolling feeds',
                  settingKey: 'app_${appKey}_auto_shrink',
                  enabledKey: 'app_${appKey}_enabled',
                ),
              ],
            ),
            GlassSliderTile(
              title: 'Shrunk Scale',
              subtitle: 'Percentage size when auto-shrunken on scroll',
              settingKey: 'app_${appKey}_auto_shrink_scale',
              min: 0.50,
              max: 1.00,
              unit: '%',
              enabledKey: 'app_${appKey}_auto_shrink',
            ),
          ],

          if (isInstagram) ...[
            const GlassSectionHeader('Reels Optics'),
            GlassGroupedCard(
              children: [
                GlassSwitchTile(
                  title: 'Reels Fullscreen Zoom (Not Recommended)',
                  subtitle:
                      'Expands Reels container to match parent. May crop 16:9 videos.',
                  settingKey: 'app_${appKey}_reels_fullscreen_zoom',
                  enabledKey: 'app_${appKey}_enabled',
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
