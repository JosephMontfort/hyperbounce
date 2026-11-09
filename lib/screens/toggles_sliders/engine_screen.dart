import 'package:flutter/cupertino.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import 'toggles_screen.dart';
import 'sliders_screen.dart';

class EngineScreen extends StatelessWidget {
  const EngineScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final hookEnabled =
            store.getBool('toggles_sliders_hook_enabled', false);
        final toggleEngine = store.getInt('toggle_engine', 1); // 0=Native, 1=Glass, 2=Disabled
        final sliderEngine = store.getInt('slider_engine', 1);
        final miuixHooking =
            store.getBool('miuix_apps_hooking_enabled', false);

        final togglesEnabled = hookEnabled && toggleEngine != 2;
        final slidersEnabled = hookEnabled && sliderEngine != 2;

        return AppScaffold(
          title: 'Toggles & Sliders',
          body: ListView(
            padding: const EdgeInsets.only(top: 12, bottom: 60),
            children: [
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Enable Hook',
                    value: hookEnabled,
                    onChanged: (val) =>
                        store.setBool('toggles_sliders_hook_enabled', val),
                  ),
                ],
              ),

              const GlassSectionHeader('Rendering Engine'),
              GlassGroupedCard(
                children: [
                  GlassDropdownTile(
                    title: 'Toggles',
                    options: const ['Native Canvas', 'Liquid Glass', 'Disabled'],
                    selectedIndex: toggleEngine.clamp(0, 2),
                    enabled: hookEnabled,
                    onSelect: (idx) => store.setInt('toggle_engine', idx),
                  ),
                  GlassDropdownTile(
                    title: 'Sliders',
                    options: const ['Native Canvas', 'Liquid Glass', 'Disabled'],
                    selectedIndex: sliderEngine.clamp(0, 2),
                    enabled: hookEnabled,
                    onSelect: (idx) => store.setInt('slider_engine', idx),
                  ),
                ],
              ),

              const GlassSectionHeader('Toggles'),
              GlassGroupedCard(
                children: [
                  GlassNavTile(
                    title: 'Toggles Appearance',
                    subtitle: 'Accent color & Liquid Glass optics',
                    enabled: togglesEnabled,
                    onTap: () {
                      Navigator.of(context).push(
                        CupertinoPageRoute(
                            builder: (_) => const TogglesScreen()),
                      );
                    },
                  ),
                  GlassNavTile(
                    title: 'Toggles Drag Physics',
                    subtitle: 'Touch tracking & spring bounce dynamics',
                    enabled: togglesEnabled,
                    onTap: () {
                      Navigator.of(context).push(
                        CupertinoPageRoute(
                            builder: (_) => const TogglePhysicsScreen()),
                      );
                    },
                  ),
                ],
              ),

              const GlassSectionHeader('Sliders'),
              GlassGroupedCard(
                children: [
                  GlassNavTile(
                    title: 'Sliders Appearance',
                    subtitle: 'Track accent & Liquid Glass optics',
                    enabled: slidersEnabled,
                    onTap: () {
                      Navigator.of(context).push(
                        CupertinoPageRoute(
                            builder: (_) => const SlidersScreen()),
                      );
                    },
                  ),
                  GlassNavTile(
                    title: 'Sliders Drag Physics',
                    subtitle: 'Touch tracking & spring bounce dynamics',
                    enabled: slidersEnabled,
                    onTap: () {
                      Navigator.of(context).push(
                        CupertinoPageRoute(
                            builder: (_) => const SliderPhysicsScreen()),
                      );
                    },
                  ),
                ],
              ),

              const GlassSectionHeader('Third-Party Apps'),
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'MIUIX App Hooking',
                    subtitle:
                        'Auto-detect and replace toggles and sliders in third-party apps using miuix-kmp library',
                    value: miuixHooking,
                    enabled: hookEnabled,
                    onChanged: (val) =>
                        store.setBool('miuix_apps_hooking_enabled', val),
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
