import 'package:flutter/cupertino.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';

class CallerScreen extends StatelessWidget {
  const CallerScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return ListenableBuilder(
      listenable: SettingsStore.instance,
      builder: (context, _) {
        final store = SettingsStore.instance;

        final enabled = store.getBool('caller_screen_enabled', false);
        final width = store.getDouble('caller_slider_width', 300.0);
        final height = store.getDouble('caller_slider_height', 72.0);
        final bottomOffset =
            store.getDouble('caller_slider_bottom_offset', 64.0);
        final cornerRadius =
            store.getDouble('caller_track_corner_radius', 36.0);
        final dialerSwipe =
            store.getBool('dialer_recents_swipe_enabled', false);

        return AppScaffold(
          title: 'Dialer & Caller Screen',
          body: ListView(
            padding: const EdgeInsets.only(top: 12, bottom: 60),
            children: [
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Enable iOS Caller Screen',
                    subtitle: 'Dual sliders for full-screen incoming calls',
                    value: enabled,
                    onChanged: (val) =>
                        store.setBool('caller_screen_enabled', val),
                  ),
                ],
              ),

              const GlassSectionHeader('Dimensions & Position'),
              GlassSliderTile(
                title: 'Slider Width',
                subtitle: 'Horizontal width of incoming call slider capsule',
                value: width,
                min: 200,
                max: 400,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('caller_slider_width', val),
              ),
              GlassSliderTile(
                title: 'Slider Height',
                subtitle: 'Vertical height of incoming call slider capsule',
                value: height,
                min: 50,
                max: 100,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('caller_slider_height', val),
              ),
              GlassSliderTile(
                title: 'Bottom Offset',
                subtitle: 'Clearance from bottom screen edge',
                value: bottomOffset,
                min: 20,
                max: 150,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('caller_slider_bottom_offset', val),
              ),
              GlassSliderTile(
                title: 'Track Corner Radius',
                subtitle: 'Curvature radius of slider track ends',
                value: cornerRadius,
                min: 15,
                max: 50,
                unit: 'dp',
                enabled: enabled,
                onChanged: (val) =>
                    store.setDouble('caller_track_corner_radius', val),
              ),

              const GlassSectionHeader('Swipe Gestures'),
              GlassGroupedCard(
                children: [
                  GlassSwitchTile(
                    title: 'Dialer Recents Swipe Actions',
                    subtitle:
                        'iOS-style left/right swipe gestures on dialer recents list',
                    value: dialerSwipe,
                    onChanged: (val) =>
                        store.setBool('dialer_recents_swipe_enabled', val),
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
