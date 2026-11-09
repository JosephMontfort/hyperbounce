import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../../state/settings_store.dart';

class ControlCenterScreen extends StatelessWidget {
  const ControlCenterScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;

    return AppScaffold(
      title: 'Control Center',
      showTopMenu: false,
      body: ListenableBuilder(
        listenable: store,
        builder: (context, _) {
          final fullUnlock = store.getBool('cc_full_unlock_enabled', true);
          final fluidResize = store.getBool('cc_fluid_resize_enabled', true);
          final grouping = store.getBool('cc_grouping_enabled', true);
          final flashlightHook = store.getBool('flashlight_brightness_hook_enabled', true);
          final flashlightSteps = store.getDouble('flashlight_steps', 4.0);

          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              const GlassSectionHeader('Control Center Customization'),
              GlassGroupedSection(
                children: [
                  GlassSwitchTile(
                    title: 'Full Tile Grid Unlock',
                    subtitle: 'Allow free rearrangement and placement of all CC tiles',
                    value: fullUnlock,
                    onChanged: (v) => store.setBool('cc_full_unlock_enabled', v),
                  ),
                  GlassSwitchTile(
                    title: 'Fluid Tile Resize',
                    subtitle: '1x1, 1x2, 2x2 dynamic tile transformation with spring settles',
                    value: fluidResize,
                    onChanged: (v) => store.setBool('cc_fluid_resize_enabled', v),
                  ),
                  GlassSwitchTile(
                    title: 'iOS Tile Grouping Folders',
                    subtitle: 'Organize related toggles into collapsible glass folder blocks',
                    value: grouping,
                    onChanged: (v) => store.setBool('cc_grouping_enabled', v),
                  ),
                ],
              ),

              const GlassSectionHeader('Flashlight Multi-Step Brightness'),
              GlassGroupedSection(
                children: [
                  GlassSwitchTile(
                    title: 'Flashlight Brightness Hook',
                    subtitle: 'Enable long-press iOS-style 4-level vertical slider',
                    value: flashlightHook,
                    onChanged: (v) => store.setBool('flashlight_brightness_hook_enabled', v),
                  ),
                ],
              ),
              GlassSliderTile(
                title: 'Brightness Step Divisions',
                subtitle: 'Number of discrete brightness detents',
                value: flashlightSteps,
                min: 3.0,
                max: 10.0,
                divisions: 7,
                onChanged: (v) => store.setDouble('flashlight_steps', v),
              ),
              const SizedBox(height: 100),
            ],
          );
        },
      ),
    );
  }
}
