import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../state/settings_store.dart';
import '../widgets/app_scaffold.dart';
import '../widgets/setting_tiles.dart';

class AboutScreen extends StatelessWidget {
  const AboutScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;

    return AppScaffold(
      title: 'About',
      showTopMenu: false,
      body: ListView(
        padding: const EdgeInsets.only(top: 16, bottom: 60),
        children: [
          // Header with App Icon
          Center(
            child: Column(
              children: [
                Container(
                  width: 88,
                  height: 88,
                  decoration: BoxDecoration(
                    color: const Color(0xFF1C1C1E),
                    borderRadius: BorderRadius.circular(20),
                    boxShadow: [
                      BoxShadow(
                        color: CupertinoColors.black.withValues(alpha: 0.4),
                        blurRadius: 18,
                        offset: const Offset(0, 8),
                      ),
                    ],
                  ),
                  child: const Center(
                    child: Icon(
                      CupertinoIcons.sparkles,
                      size: 46,
                      color: CupertinoColors.activeBlue,
                    ),
                  ),
                ),
                const SizedBox(height: 14),
                const Text(
                  'HyperiOS',
                  style: TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.bold,
                    letterSpacing: -0.5,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Version ${store.versionName} • LibXposed 102',
                  style: const TextStyle(
                    fontSize: 14,
                    color: CupertinoColors.secondaryLabel,
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 24),

          // Developer info
          const GlassSectionHeader('Developer'),
          GlassGroupedCard(
            children: [
              GlassListTile(
                title: const Text('Developer'),
                trailing: const Text(
                  'JosephMontfort',
                  style: TextStyle(
                    fontSize: 15,
                    color: CupertinoColors.secondaryLabel,
                  ),
                ),
              ),
              GlassListTile(
                title: const Text('Repository'),
                trailing: const Text(
                  'GitHub / hyperbounce',
                  style: TextStyle(
                    fontSize: 15,
                    color: CupertinoColors.activeBlue,
                  ),
                ),
                onTap: () {
                  Clipboard.setData(const ClipboardData(
                      text: 'https://github.com/JosephMontfort/hyperbounce'));
                },
              ),
            ],
          ),

          // System info
          const GlassSectionHeader('System Environment'),
          GlassGroupedCard(
            children: [
              GlassListTile(
                title: const Text('LSPosed Status'),
                trailing: Text(
                  store.isBound ? 'Active & Bound' : 'Not Bound',
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w600,
                    color: store.isBound
                        ? CupertinoColors.activeGreen
                        : CupertinoColors.systemRed,
                  ),
                ),
              ),
              GlassListTile(
                title: const Text('Android SDK'),
                trailing: Text(
                  'API ${store.sdkInt}',
                  style: const TextStyle(
                    fontSize: 15,
                    color: CupertinoColors.secondaryLabel,
                  ),
                ),
              ),
              GlassListTile(
                title: const Text('Root Status'),
                trailing: Text(
                  store.isRootAvailable ? 'Available (KSU/Magisk)' : 'Not Available',
                  style: TextStyle(
                    fontSize: 15,
                    color: store.isRootAvailable
                        ? CupertinoColors.activeGreen
                        : CupertinoColors.secondaryLabel,
                  ),
                ),
              ),
            ],
          ),

          // Support & Donate
          const GlassSectionHeader('Support'),
          GlassGroupedCard(
            children: [
              GlassActionTile(
                leading: const Icon(CupertinoIcons.heart_fill,
                    color: CupertinoColors.systemPink),
                title: 'Donate to Developer',
                subtitle: 'https://donatr.ee/montfort_1607',
                onTap: () {
                  Clipboard.setData(const ClipboardData(
                      text: 'https://donatr.ee/montfort_1607'));
                },
              ),
            ],
          ),
        ],
      ),
    );
  }
}
