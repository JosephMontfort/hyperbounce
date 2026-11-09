import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import '../../state/settings_store.dart';
import 'app_detail_screen.dart';

class AppsListScreen extends StatelessWidget {
  const AppsListScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;

    return AppScaffold(
      title: 'Floating Navigation',
      showTopMenu: false,
      body: ListenableBuilder(
        listenable: store,
        builder: (context, _) {
          final igEnabled = store.getBool('app_instagram_enabled', false);
          final waEnabled = store.getBool('app_whatsapp_enabled', false);
          final ytEnabled = store.getBool('app_youtube_enabled', false);

          return ListView(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            children: [
              const GlassSectionHeader('Supported Applications'),
              GlassGroupedSection(
                children: [
                  GlassListTile(
                    leading: const Icon(CupertinoIcons.camera, color: Color(0xFFE1306C)),
                    title: const Text('Instagram'),
                    subtitle: Text(igEnabled ? 'Active • Liquid Glass' : 'Disabled'),
                    trailing: GlassListTile.chevron,
                    onTap: () {
                      Navigator.of(context).push(
                        CupertinoPageRoute(
                          builder: (_) => const AppDetailScreen(
                            appKey: 'instagram',
                            appName: 'Instagram',
                            package: 'com.instagram.android',
                          ),
                        ),
                      );
                    },
                  ),
                  GlassListTile(
                    leading: const Icon(CupertinoIcons.chat_bubble_2, color: Color(0xFF25D366)),
                    title: const Text('WhatsApp'),
                    subtitle: Text(waEnabled ? 'Active • Liquid Glass' : 'Disabled'),
                    trailing: GlassListTile.chevron,
                    onTap: () {
                      Navigator.of(context).push(
                        CupertinoPageRoute(
                          builder: (_) => const AppDetailScreen(
                            appKey: 'whatsapp',
                            appName: 'WhatsApp',
                            package: 'com.whatsapp',
                          ),
                        ),
                      );
                    },
                  ),
                  GlassListTile(
                    leading: const Icon(CupertinoIcons.play_rectangle, color: Color(0xFFFF0000)),
                    title: const Text('YouTube'),
                    subtitle: Text(ytEnabled ? 'Active • Liquid Glass' : 'Disabled'),
                    trailing: GlassListTile.chevron,
                    onTap: () {
                      Navigator.of(context).push(
                        CupertinoPageRoute(
                          builder: (_) => const AppDetailScreen(
                            appKey: 'youtube',
                            appName: 'YouTube',
                            package: 'com.google.android.youtube',
                          ),
                        ),
                      );
                    },
                  ),
                ],
              ),
              const SizedBox(height: 16),
              const Padding(
                padding: EdgeInsets.symmetric(horizontal: 8),
                child: Text(
                  'Floating navigation replaces stock tab bars with floating liquid glass capsules inside the selected applications.',
                  style: TextStyle(fontSize: 13, color: CupertinoColors.systemGrey),
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
