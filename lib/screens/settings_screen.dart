import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../state/settings_store.dart';
import '../widgets/setting_tiles.dart';
import 'safemode/safe_mode_screen.dart';
import 'spec/all_specs.dart';
import 'spec/spec_screen.dart';

class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  static const _themeValues = ['FollowSystem', 'Light', 'Dark'];
  static const _componentValues = ['LiquidGlass', 'MIUIX'];
  static const _iconStyleValues = ['Flat', 'Monet'];

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;

    return ListView(
      padding: const EdgeInsets.only(top: 8, bottom: 120),
      children: [
        // --- Card 1: Appearance ---
        const GlassSectionHeader('Appearance'),
        const GlassGroupedCard(
          children: [
            GlassDropdownTile(
              title: 'App Theme',
              options: ['Follow System', 'Light', 'Dark'],
              optionValues: _themeValues,
              settingKey: 'app_theme',
            ),
            GlassDropdownTile(
              title: 'Component Style',
              options: ['Liquid Glass', 'MIUIX'],
              optionValues: _componentValues,
              settingKey: 'component_style',
            ),
            GlassSwitchTile(
              title: 'Liquid Glass Bottom Bar',
              subtitle: 'Use Liquid Glass style for the floating nav bar',
              settingKey: 'miuix_liquid_glass_bottom_bar',
            ),
            GlassDropdownTile(
              title: 'App Icon Style',
              options: ['Flat', 'Monet'],
              optionValues: _iconStyleValues,
              settingKey: 'app_icon_style',
            ),
            GlassSwitchTile(
              title: 'Haptic Feedback',
              subtitle: 'Tactile haptic response across glass controls',
              settingKey: 'global_glass_haptics',
            ),
          ],
        ),

        // --- Card 2: Advanced & System ---
        const GlassSectionHeader('Advanced & Diagnostics'),
        GlassGroupedCard(
          children: [
            const GlassSwitchTile(
              title: 'Hot Reload Broadcast',
              subtitle:
                  'Broadcast settings change intents in real-time to active hooked processes',
              settingKey: 'hot_reload_enabled',
            ),
            const GlassSwitchTile(
              title: 'Show Entry in Settings App',
              subtitle: 'Adds HyperBounce directly into Android Settings',
              settingKey: 'settings_app_entry_enabled',
            ),
            GlassNavTile(
              title: 'Morph Physics & Dynamics',
              subtitle: 'Dropdown menu to dialog fluid morph kinetics',
              destination: (_) => SpecScreen(spec: morphPhysicsSpec),
            ),
            GlassNavTile(
              title: 'Safe Mode & Diagnostics',
              subtitle: 'Crash loop protection and component bypasses',
              destination: (_) => const SafeModeScreen(),
            ),
          ],
        ),

        // --- Card 3: Backup & Restore ---
        const GlassSectionHeader('Backup & Restore'),
        GlassGroupedCard(
          children: [
            GlassActionTile(
              title: 'Export Settings to Clipboard',
              subtitle: 'Copy full module configuration as JSON',
              leading: const Icon(CupertinoIcons.share,
                  color: CupertinoColors.activeBlue),
              onTap: () async {
                final json = await store.exportJson();
                await Clipboard.setData(ClipboardData(text: json));
                if (context.mounted) {
                  GlassDialog.show(
                    context: context,
                    title: 'Export Complete',
                    message:
                        'Module configuration copied to clipboard as JSON.',
                    actions: [
                      GlassDialogAction(
                        label: 'OK',
                        isPrimary: true,
                        onPressed: () => Navigator.of(context).pop(),
                      ),
                    ],
                  );
                }
              },
            ),
            GlassActionTile(
              title: 'Import Settings from Clipboard',
              subtitle: 'Restore settings from JSON on clipboard',
              leading: const Icon(CupertinoIcons.arrow_down_doc,
                  color: CupertinoColors.activeGreen),
              onTap: () async {
                final data = await Clipboard.getData(Clipboard.kTextPlain);
                final text = data?.text ?? '';
                if (text.isEmpty) {
                  if (context.mounted) {
                    GlassDialog.show(
                      context: context,
                      title: 'Clipboard Empty',
                      message: 'No text found on clipboard to import.',
                      actions: [
                        GlassDialogAction(
                          label: 'OK',
                          onPressed: () => Navigator.of(context).pop(),
                        ),
                      ],
                    );
                  }
                  return;
                }
                final ok = await store.importJson(text);
                if (context.mounted) {
                  GlassDialog.show(
                    context: context,
                    title: ok ? 'Import Successful' : 'Import Failed',
                    message: ok
                        ? 'Settings successfully applied from clipboard.'
                        : 'Invalid JSON configuration.',
                    actions: [
                      GlassDialogAction(
                        label: 'OK',
                        isPrimary: true,
                        onPressed: () => Navigator.of(context).pop(),
                      ),
                    ],
                  );
                }
              },
            ),
            GlassActionTile(
              title: 'Reset All to Defaults',
              subtitle: 'Revert all preferences to stock out-of-the-box defaults',
              titleColor: CupertinoColors.systemRed,
              leading: const Icon(CupertinoIcons.arrow_counterclockwise,
                  color: CupertinoColors.systemRed),
              onTap: () {
                GlassDialog.show(
                  context: context,
                  title: 'Reset All Settings?',
                  message:
                      'All settings across all screens will be reverted to default values.',
                  actions: [
                    GlassDialogAction(
                      label: 'Cancel',
                      onPressed: () => Navigator.of(context).pop(),
                    ),
                    GlassDialogAction(
                      label: 'Reset',
                      isDestructive: true,
                      onPressed: () async {
                        await store.resetToDefaults();
                        if (context.mounted) {
                          Navigator.of(context).pop();
                        }
                      },
                    ),
                  ],
                );
              },
            ),
          ],
        ),
      ],
    );
  }
}
