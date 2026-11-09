import 'package:flutter/cupertino.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';

class AppDetailScreen extends StatelessWidget {
  final String displayName;
  final String packageName;

  const AppDetailScreen({
    super.key,
    required this.displayName,
    required this.packageName,
  });

  @override
  Widget build(BuildContext context) {
    final isInstagram = packageName == 'com.instagram.android';
    final isWhatsApp = packageName == 'com.whatsapp';
    final isYouTube = packageName == 'com.google.android.youtube';

    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final enabled = store.getBool('app_${packageName}_enabled', false);
        final liquidGlass =
            store.getBool('app_${packageName}_liquid_glass', true);
        final intensity =
            store.getDouble('app_${packageName}_glass_intensity', 1.0);
        final barBlur =
            store.getDouble('app_${packageName}_bar_blur_intensity', 1.0);
        final containerOpacity =
            store.getDouble('app_${packageName}_container_opacity', 0.65);
        final cornerRadius =
            store.getDouble('app_${packageName}_corner_radius', 28.0);
        final barHeight =
            store.getDouble('app_${packageName}_bar_height', 52.0);
        final tabWidth =
            store.getDouble('app_${packageName}_tab_width', 64.0);
        final bottomMargin =
            store.getDouble('app_${packageName}_bottom_margin', 16.0);

        final reelsZoom = isInstagram
            ? store.getBool('app_${packageName}_reels_fullscreen_zoom', false)
            : false;
        final autoShrink = isYouTube
            ? store.getBool('app_${packageName}_auto_shrink', true)
            : false;
        final autoShrinkScale = isYouTube
            ? store.getDouble('app_${packageName}_auto_shrink_scale', 0.85)
            : 0.85;
        final pillIntensity = isWhatsApp
            ? store.getDouble('app_${packageName}_pill_glass_intensity', 1.0)
            : 1.0;

        return AppScaffold(
          title: displayName,
          body: ListView(
            padding: const EdgeInsets.only(top: 12, bottom: 60),
            children: [
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Enable Floating Nav Bar',
                    value: enabled,
                    onChanged: (val) =>
                        store.setBool('app_${packageName}_enabled', val),
                  ),
                  GlassSwitchTile(
                    title: 'Liquid Glass',
                    subtitle: 'Shader refraction & specular highlights',
                    value: liquidGlass,
                    enabled: enabled,
                    onChanged: (val) =>
                        store.setBool('app_${packageName}_liquid_glass', val),
                  ),
                ],
              ),

              const GlassSectionHeader('Optics & Blur'),
              GlassSliderTile(
                title: 'Glass Intensity',
                subtitle: 'Refraction magnitude of the floating bar',
                value: intensity,
                min: 0.2,
                max: 2.5,
                unit: 'x',
                enabled: enabled,
                onChanged: (val) => store.setDouble(
                    'app_${packageName}_glass_intensity', val),
              ),
              GlassSliderTile(
                title: 'Bar Blur Intensity',
                subtitle: 'Backdrop blur radius behind navigation items',
                value: barBlur,
                min: 0,
                max: 2.5,
                unit: 'x',
                enabled: enabled,
                onChanged: (val) => store.setDouble(
                    'app_${packageName}_bar_blur_intensity', val),
              ),
              GlassSliderTile(
                title: 'Container Opacity',
                subtitle: 'Dark tint transmission through the bar platter',
                value: containerOpacity,
                min: 0,
                max: 1.0,
                unit: '%',
                enabled: enabled,
                onChanged: (val) => store.setDouble(
                    'app_${packageName}_container_opacity', val),
              ),

              const GlassSectionHeader('Geometry & Spacing'),
              GlassSliderTile(
                title: 'Corner Radius',
                subtitle: 'Curvature radius of navigation capsule ends',
                value: cornerRadius,
                min: 10,
                max: 45,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) => store.setDouble(
                    'app_${packageName}_corner_radius', val),
              ),
              GlassSliderTile(
                title: 'Bar Height',
                subtitle: 'Total vertical height of floating capsule',
                value: barHeight,
                min: 40,
                max: 70,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('app_${packageName}_bar_height', val),
              ),
              GlassSliderTile(
                title: 'Tab Width',
                subtitle: 'Horizontal spacing allocation for each icon item',
                value: tabWidth,
                min: 40,
                max: 90,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('app_${packageName}_tab_width', val),
              ),
              GlassSliderTile(
                title: 'Bottom Margin',
                subtitle: 'Elevation height above screen edge or gesture bar',
                value: bottomMargin,
                min: 0,
                max: 50,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) => store.setDouble(
                    'app_${packageName}_bottom_margin', val),
              ),

              if (isInstagram) ...[
                const GlassSectionHeader('Instagram Features'),
                GlassGroupedCard(
                  children: [
                    GlassSwitchTile(
                      title: 'Reels Fullscreen Zoom',
                      subtitle:
                          'Hide navigation bar during Reels playback and expand viewport',
                      value: reelsZoom,
                      enabled: enabled,
                      onChanged: (val) => store.setBool(
                          'app_${packageName}_reels_fullscreen_zoom', val),
                    ),
                  ],
                ),
              ],

              if (isWhatsApp) ...[
                const GlassSectionHeader('WhatsApp Features'),
                GlassSliderTile(
                  title: 'Pill Glass Intensity',
                  subtitle: 'Refraction intensity of active conversation tab pill',
                  value: pillIntensity,
                  min: 0.2,
                  max: 2.5,
                  unit: 'x',
                  enabled: enabled,
                  onChanged: (val) => store.setDouble(
                      'app_${packageName}_pill_glass_intensity', val),
                ),
              ],

              if (isYouTube) ...[
                const GlassSectionHeader('YouTube Features'),
                GlassGroupedCard(
                  children: [
                    GlassSwitchTile(
                      title: 'Auto-Shrink',
                      subtitle:
                          'Spring-collapse navigation bar while scrolling feeds',
                      value: autoShrink,
                      enabled: enabled,
                      onChanged: (val) => store.setBool(
                          'app_${packageName}_auto_shrink', val),
                    ),
                  ],
                ),
                if (autoShrink)
                  GlassSliderTile(
                    title: 'Auto-Shrink Scale',
                    subtitle: 'Minimum scale factor when bar is minimized',
                    value: autoShrinkScale,
                    min: 0.4,
                    max: 1.0,
                    unit: 'x',
                    enabled: enabled,
                    onChanged: (val) => store.setDouble(
                        'app_${packageName}_auto_shrink_scale', val),
                  ),
              ],
            ],
          ),
        );
      },
    );
  }
}
