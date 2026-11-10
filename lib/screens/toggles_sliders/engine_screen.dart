import 'package:flutter/cupertino.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import 'toggles_screen.dart';
import 'sliders_screen.dart';

class EngineScreen extends StatelessWidget {
  const EngineScreen({super.key});

  static const _engineValues = ['Native', 'LiquidGlass', 'Disabled'];

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: 'Toggles & Sliders',
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Enable Hook',
                settingKey: 'toggles_sliders_hook_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('Rendering Engine'),
          const GlassGroupedCard(
            children: [
              GlassDropdownTile(
                title: 'Toggles',
                settingKey: 'engine_toggle_render',
                options: ['Native Canvas', 'Liquid Glass', 'Disabled'],
                optionValues: _engineValues,
                enabledKey: 'toggles_sliders_hook_enabled',
              ),
              GlassDropdownTile(
                title: 'Sliders',
                settingKey: 'engine_slider_render',
                options: ['Native Canvas', 'Liquid Glass', 'Disabled'],
                optionValues: _engineValues,
                enabledKey: 'toggles_sliders_hook_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('Toggles'),
          GlassGroupedCard(
            children: [
              GlassNavTile(
                title: 'Toggles Appearance',
                subtitle: 'Accent color & Liquid Glass optics',
                enabledKey: 'toggles_sliders_hook_enabled',
                destination: (_) => const TogglesScreen(),
              ),
              GlassNavTile(
                title: 'Toggles Drag Physics',
                subtitle: 'Touch tracking & spring bounce dynamics',
                enabledKey: 'toggles_sliders_hook_enabled',
                destination: (_) => const TogglePhysicsScreen(),
              ),
            ],
          ),

          const GlassSectionHeader('Sliders'),
          GlassGroupedCard(
            children: [
              GlassNavTile(
                title: 'Sliders Appearance',
                subtitle: 'Track accent & Liquid Glass optics',
                enabledKey: 'toggles_sliders_hook_enabled',
                destination: (_) => const SlidersScreen(),
              ),
              GlassNavTile(
                title: 'Sliders Drag Physics',
                subtitle: 'Touch tracking & spring bounce dynamics',
                enabledKey: 'toggles_sliders_hook_enabled',
                destination: (_) => const SliderPhysicsScreen(),
              ),
            ],
          ),

          const GlassSectionHeader('Third-Party Apps'),
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'MIUIX App Hooking',
                subtitle:
                    'Auto-detect and replace toggles and sliders in third-party apps using miuix-kmp library',
                settingKey: 'miuix_apps_hooking_enabled',
                enabledKey: 'toggles_sliders_hook_enabled',
              ),
            ],
          ),
        ],
      ),
    );
  }
}
