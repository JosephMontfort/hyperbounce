import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../../state/settings_store.dart';
import '../../bridge/native_bridge.dart';

class AppDetailScreen extends StatefulWidget {
  final String appKey;
  final String appName;
  final String package;

  const AppDetailScreen({
    super.key,
    required this.appKey,
    required this.appName,
    required this.package,
  });

  @override
  State<AppDetailScreen> createState() => _AppDetailScreenState();
}

class _AppDetailScreenState extends State<AppDetailScreen> {
  final _store = SettingsStore.instance;

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: widget.appName,
      showTopMenu: false,
      body: ListenableBuilder(
        listenable: _store,
        builder: (context, _) {
          final k = widget.appKey;
          final enabled = _store.getBool('app_${k}_enabled', false);
          final liquidGlass = _store.getBool('app_${k}_liquid_glass', true);
          final glassIntensity = _store.getDouble('app_${k}_glass_intensity', 1.0);
          final blurIntensity = _store.getDouble('app_${k}_bar_blur_intensity', 1.0);
          final opacity = _store.getDouble('app_${k}_container_opacity', 0.4);
          final cornerRadius = _store.getDouble('app_${k}_corner_radius', 36.0);
          final barHeight = _store.getDouble('app_${k}_bar_height', 50.0);
          final tabWidth = _store.getDouble('app_${k}_tab_width', 75.0);
          final bottomMargin = _store.getDouble('app_${k}_bottom_margin', 40.0);
          final themeMode = _store.getString('app_${k}_theme_mode', 'System');
          final accent = _store.getString('app_${k}_accent', 'Auto');

          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              const GlassSectionHeader('Master Control'),
              GlassGroupedSection(
                children: [
                  GlassSwitchTile(
                    title: 'Enable Floating Bar',
                    subtitle: 'Inject liquid floating navigation into ${widget.appName}',
                    value: enabled,
                    onChanged: (v) => _store.setBool('app_${k}_enabled', v),
                  ),
                  GlassSwitchTile(
                    title: 'Liquid Glass Shader',
                    subtitle: 'Real-time backdrop refraction and chromatic aberration',
                    value: liquidGlass,
                    onChanged: (v) => _store.setBool('app_${k}_liquid_glass', v),
                  ),
                  GlassListTile(
                    title: const Text('Restart App Process'),
                    subtitle: Text('Force stop ${widget.package} to apply fresh hooks'),
                    trailing: const Icon(CupertinoIcons.arrow_clockwise, color: CupertinoColors.activeBlue),
                    onTap: () => NativeBridge.restartPackage(widget.package, widget.appName),
                  ),
                ],
              ),

              const GlassSectionHeader('Glass Optics'),
              GlassSliderTile(
                title: 'Glass Intensity',
                subtitle: 'Specular highlights and refraction multiplier',
                value: glassIntensity,
                min: 0.1,
                max: 2.0,
                onChanged: (v) => _store.setDouble('app_${k}_glass_intensity', v),
              ),
              GlassSliderTile(
                title: 'Blur Intensity',
                subtitle: 'Backdrop Gaussian blur radius multiplier',
                value: blurIntensity,
                min: 0.0,
                max: 2.0,
                onChanged: (v) => _store.setDouble('app_${k}_bar_blur_intensity', v),
              ),
              GlassSliderTile(
                title: 'Container Opacity',
                subtitle: 'Base translucent platter fill alpha',
                value: opacity,
                min: 0.05,
                max: 1.0,
                onChanged: (v) => _store.setDouble('app_${k}_container_opacity', v),
              ),

              const GlassSectionHeader('Geometry & Positioning'),
              GlassSliderTile(
                title: 'Corner Radius',
                subtitle: 'Bar corner curvature in logical dp',
                value: cornerRadius,
                min: 10.0,
                max: 60.0,
                unit: 'dp',
                onChanged: (v) => _store.setDouble('app_${k}_corner_radius', v),
              ),
              GlassSliderTile(
                title: 'Bar Height',
                subtitle: 'Height of the capsule container',
                value: barHeight,
                min: 35.0,
                max: 85.0,
                unit: 'dp',
                onChanged: (v) => _store.setDouble('app_${k}_bar_height', v),
              ),
              GlassSliderTile(
                title: 'Tab Slot Width',
                subtitle: 'Width of each individual navigation item slot',
                value: tabWidth,
                min: 40.0,
                max: 120.0,
                unit: 'dp',
                onChanged: (v) => _store.setDouble('app_${k}_tab_width', v),
              ),
              GlassSliderTile(
                title: 'Bottom Floating Margin',
                subtitle: 'Elevation spacing from device screen bottom',
                value: bottomMargin,
                min: 0.0,
                max: 80.0,
                unit: 'dp',
                onChanged: (v) => _store.setDouble('app_${k}_bottom_margin', v),
              ),

              const GlassSectionHeader('Color & Theme'),
              GlassGroupedSection(
                children: [
                  GlassListTile(
                    title: const Text('Theme Mode'),
                    subtitle: Text('Current: $themeMode'),
                    trailing: CupertinoSlidingSegmentedControl<String>(
                      groupValue: themeMode,
                      children: const {
                        'System': Padding(
                          padding: EdgeInsets.symmetric(horizontal: 8),
                          child: Text('Auto', style: TextStyle(fontSize: 12)),
                        ),
                        'Light': Padding(
                          padding: EdgeInsets.symmetric(horizontal: 8),
                          child: Text('Light', style: TextStyle(fontSize: 12)),
                        ),
                        'Dark': Padding(
                          padding: EdgeInsets.symmetric(horizontal: 8),
                          child: Text('Dark', style: TextStyle(fontSize: 12)),
                        ),
                      },
                      onValueChanged: (v) {
                        if (v != null) _store.setString('app_${k}_theme_mode', v);
                      },
                    ),
                  ),
                  GlassListTile(
                    title: const Text('Accent Color Preset'),
                    subtitle: Text('Selected: $accent'),
                    trailing: const Icon(CupertinoIcons.circle_fill, color: CupertinoColors.activeBlue),
                    onTap: () {
                      _showAccentSheet(context, k, accent);
                    },
                  ),
                ],
              ),
              const SizedBox(height: 100),
            ],
          );
        },
      ),
    );
  }

  void _showAccentSheet(BuildContext context, String k, String current) {
    const accents = ['Auto', 'Blue', 'Pink', 'Purple', 'Orange', 'Green'];
    showCupertinoModalPopup(
      context: context,
      builder: (_) => CupertinoActionSheet(
        title: const Text('Select Accent Color'),
        actions: accents.map((acc) {
          return CupertinoActionSheetAction(
            isDefaultAction: acc == current,
            onPressed: () {
              Navigator.pop(context);
              _store.setString('app_${k}_accent', acc);
            },
            child: Text(acc),
          );
        }).toList(),
        cancelButton: CupertinoActionSheetAction(
          onPressed: () => Navigator.pop(context),
          child: const Text('Cancel'),
        ),
      ),
    );
  }
}
