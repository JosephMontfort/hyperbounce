import 'package:flutter/cupertino.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../spec/all_specs.dart';
import '../spec/spec_screen.dart';

class NotificationCenterScreen extends StatelessWidget {
  const NotificationCenterScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: 'Notification Centre',
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Enable Hook',
                settingKey: 'notif_master_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('Pop-Up Notification Animation'),
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'iOS 26 Pop-Up Animation',
                subtitle: 'iOS 26 style animation for pop up notifications',
                settingKey: 'notif_ios_hun_animation_enabled',
                enabledKey: 'notif_master_enabled',
              ),
            ],
          ),
          const GlassSliderTile(
            title: 'Animation Speed (Duration)',
            settingKey: 'notif_ios_hun_duration_ms',
            min: 150,
            max: 1500,
            unit: 'ms',
            enabledKey: 'notif_ios_hun_animation_enabled',
          ),
          const GlassSliderTile(
            title: 'Emergence Start Scale',
            subtitle:
                'Initial size when emerging outward from the center top notch',
            settingKey: 'notif_ios_hun_start_scale',
            min: 0.05,
            max: 1.0,
            unit: '%',
            enabledKey: 'notif_ios_hun_animation_enabled',
          ),
          const GlassSliderTile(
            title: 'Inertia Squeeze & Stretch',
            subtitle:
                'Physics velocity elongation during transit and lateral squash upon landing',
            settingKey: 'notif_ios_hun_squeeze_strength',
            min: 0,
            max: 4.0,
            unit: 'x',
            enabledKey: 'notif_ios_hun_animation_enabled',
          ),
          const GlassSectionFooter(
            'Applies exclusively to incoming heads-up pop-up notifications after the screen is unlocked. Does not affect lock screen notifications or open notification shade.',
          ),

          const GlassSectionHeader('Notification Center Sheet'),
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Notification Center iOS Sheet',
                settingKey: 'notif_sheet_enabled',
                enabledKey: 'notif_master_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('Sheet Wallpaper Background'),
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Lock Screen Wallpaper Background',
                subtitle:
                    'Lockscreen wallpaper attached to the sheet, rolling down with it',
                settingKey: 'notif_sheet_lock_wallpaper_enabled',
                enabledKey: 'notif_sheet_enabled',
              ),
              GlassSwitchTile(
                title: '1:1 Finger Tracking',
                subtitle:
                    'Physical 1:1 finger tracking on notification shade pulldown',
                settingKey: 'notif_sheet_one_to_one_tracking',
                enabledKey: 'notif_sheet_enabled',
              ),
              GlassSwitchTile(
                title: 'Homescreen Only',
                subtitle:
                    'Active only when pulling down over homescreen; uses transparent glass over running apps',
                settingKey: 'notif_sheet_lock_wallpaper_homescreen_only',
                enabledKey: 'notif_sheet_enabled',
              ),
              GlassSwitchTile(
                title: 'Custom Background Wallpaper',
                subtitle:
                    'Custom wallpaper displayed behind notification sheet',
                settingKey: 'notif_sheet_custom_wallpaper_enabled',
                enabledKey: 'notif_sheet_enabled',
              ),
            ],
          ),

          const GlassSectionHeader('Optics & Components'),
          GlassGroupedCard(
            children: [
              GlassNavTile(
                title: 'Liquid Glass Tuning',
                subtitle: 'Refraction, dispersion, and blur filters',
                enabledKey: 'notif_sheet_enabled',
                destination: (_) => SpecScreen(
                  spec: notificationCenterSheetGlassTuningSpec,
                ),
              ),
              GlassNavTile(
                title: 'Glass Lighting & Highlights',
                subtitle: 'Specular rims, perimeter hairline & inner glow',
                enabledKey: 'notif_sheet_enabled',
                destination: (_) => SpecScreen(
                  spec: notificationCenterSheetLightingSpec,
                ),
              ),
              GlassNavTile(
                title: 'Home Indicator Pill',
                subtitle: 'Pill dimensions, height & bottom margin',
                enabledKey: 'notif_sheet_enabled',
                destination: (_) => SpecScreen(
                  spec: notificationCenterSheetHomeIndicatorSpec,
                ),
              ),
            ],
          ),

          const GlassSectionHeader('Dimensions & Margins'),
          const GlassSliderTile(
            title: 'Side Edges Bevel Width',
            subtitle:
                'Width of the left and right optical glass bevel slopes',
            settingKey: 'notif_card_left_bevel',
            min: 0,
            max: 80,
            unit: 'dp',
            enabledKey: 'notif_sheet_enabled',
          ),
          const GlassSliderTile(
            title: 'Bottom Edge Bevel Width',
            subtitle: 'Width of the bottom optical glass bevel slope',
            settingKey: 'notif_card_bottom_bevel',
            min: 0,
            max: 80,
            unit: 'dp',
            enabledKey: 'notif_sheet_enabled',
          ),
          const GlassSliderTile(
            title: 'Sheet Corner Radius',
            subtitle:
                'Continuous superellipse curvature corner rounding',
            settingKey: 'notif_card_corner_radius',
            min: 0,
            max: 60,
            unit: 'dp',
            enabledKey: 'notif_sheet_enabled',
          ),
          const GlassSliderTile(
            title: 'Side Padding Margin',
            subtitle:
                'Horizontal spacing inset margin from display boundaries',
            settingKey: 'notif_side_margin',
            min: 0,
            max: 30,
            unit: 'dp',
            enabledKey: 'notif_sheet_enabled',
          ),
          const GlassSliderTile(
            title: 'Header Clock Margin Top',
            subtitle:
                'Top offset margin where the lockscreen clock begins rolling down',
            settingKey: 'notif_start_margin_top',
            min: 50,
            max: 400,
            unit: 'dp',
            enabledKey: 'notif_sheet_enabled',
          ),
          const GlassSliderTile(
            title: 'Header Clock Scale',
            subtitle:
                'Size scaling multiplier for the expanded header clock',
            settingKey: 'notif_clock_scale',
            min: 0.2,
            max: 1.0,
            unit: '%',
            enabledKey: 'notif_sheet_enabled',
          ),

          const GlassSectionHeader('Behavior'),
          const GlassGroupedCard(
            children: [
              GlassSwitchTile(
                title: 'Notification Stacking Expansion',
                subtitle:
                    'Dynamic gesture swipe to separate grouped notifications',
                settingKey: 'notif_stack_enabled',
                enabledKey: 'notif_sheet_enabled',
              ),
              GlassSwitchTile(
                title: 'Unlock Device on Shade Dismiss',
                subtitle:
                    'Smoothly returns directly into home screen on upward fling dismiss',
                settingKey: 'notif_launcher_unlock_on_dismiss',
                enabledKey: 'notif_sheet_enabled',
              ),
            ],
          ),
        ],
      ),
    );
  }
}
