import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../../state/settings_store.dart';

class CallerScreen extends StatelessWidget {
  const CallerScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;

    return AppScaffold(
      title: 'Caller & Dialer',
      showTopMenu: false,
      body: ListenableBuilder(
        listenable: store,
        builder: (context, _) {
          final callerEnabled = store.getBool('caller_screen_enabled', true);
          final buttonSize = store.getDouble('caller_button_size', 70.0);
          final bottomOffset = store.getDouble('caller_bottom_offset', 80.0);
          final recentsSwipe = store.getBool('dialer_recents_swipe_enabled', true);
          final dialerHaptics = store.getBool('dialer_haptics_enabled', true);

          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              const GlassSectionHeader('iOS Incoming Call Interface'),
              GlassGroupedSection(
                children: [
                  GlassSwitchTile(
                    title: 'Enable iOS Call Screen',
                    subtitle: 'Replace MIUI in-call UI with iOS liquid slider & circular accept buttons',
                    value: callerEnabled,
                    onChanged: (v) => store.setBool('caller_screen_enabled', v),
                  ),
                ],
              ),
              GlassSliderTile(
                title: 'Call Button Diameter',
                subtitle: 'Size of Accept and Decline glass buttons',
                value: buttonSize,
                min: 50.0,
                max: 95.0,
                unit: 'dp',
                onChanged: (v) => store.setDouble('caller_button_size', v),
              ),
              GlassSliderTile(
                title: 'Bottom Padding Offset',
                subtitle: 'Distance from bottom navigation / gesture pill',
                value: bottomOffset,
                min: 20.0,
                max: 140.0,
                unit: 'dp',
                onChanged: (v) => store.setDouble('caller_bottom_offset', v),
              ),

              const GlassSectionHeader('Dialer Call Log Gestures'),
              GlassGroupedSection(
                children: [
                  GlassSwitchTile(
                    title: 'Swipe Recents Action',
                    subtitle: 'Swipe left to delete, swipe right to call directly',
                    value: recentsSwipe,
                    onChanged: (v) => store.setBool('dialer_recents_swipe_enabled', v),
                  ),
                  GlassSwitchTile(
                    title: 'Dialer Keypad Haptics',
                    subtitle: 'Tactile mechanical vibration on number press',
                    value: dialerHaptics,
                    onChanged: (v) => store.setBool('dialer_haptics_enabled', v),
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
}
