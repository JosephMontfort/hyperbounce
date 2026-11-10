import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import 'app_detail_screen.dart';

class AppsListScreen extends StatelessWidget {
  const AppsListScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: 'Floating Navigation Bar',
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 60),
        children: [
          GlassGroupedCard(
            children: [
              GlassNavTile(
                title: 'Instagram',
                subtitle: 'Liquid Glass Floating Bottom Bar in Instagram',
                leading: Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: const Color(0xFFFF2D55).withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(CupertinoIcons.camera_fill,
                      size: 18, color: Color(0xFFFF2D55)),
                ),
                onTap: () {
                  Navigator.of(context).push(
                    GlassPageRoute(
                      builder: (_) => const AppDetailScreen(
                        displayName: 'Instagram',
                        packageName: 'com.instagram.android',
                      ),
                    ),
                  );
                },
              ),
              GlassNavTile(
                title: 'WhatsApp',
                subtitle: 'Liquid Glass Floating Bottom Bar in WhatsApp',
                leading: Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: const Color(0xFF34C759).withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(CupertinoIcons.chat_bubble_2_fill,
                      size: 18, color: Color(0xFF34C759)),
                ),
                onTap: () {
                  Navigator.of(context).push(
                    GlassPageRoute(
                      builder: (_) => const AppDetailScreen(
                        displayName: 'WhatsApp',
                        packageName: 'com.whatsapp',
                      ),
                    ),
                  );
                },
              ),
              GlassNavTile(
                title: 'YouTube',
                subtitle: 'Liquid Glass Floating Bottom Bar in YouTube',
                leading: Container(
                  width: 32,
                  height: 32,
                  decoration: BoxDecoration(
                    color: const Color(0xFFFF3B30).withValues(alpha: 0.18),
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: const Icon(CupertinoIcons.play_rectangle_fill,
                      size: 18, color: Color(0xFFFF3B30)),
                ),
                onTap: () {
                  Navigator.of(context).push(
                    GlassPageRoute(
                      builder: (_) => const AppDetailScreen(
                        displayName: 'YouTube',
                        packageName: 'com.google.android.youtube',
                      ),
                    ),
                  );
                },
              ),
            ],
          ),
        ],
      ),
    );
  }
}
