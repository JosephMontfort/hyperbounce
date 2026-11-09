import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../state/settings_store.dart';
import '../widgets/setting_tiles.dart';

class AboutScreen extends StatelessWidget {
  const AboutScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;

    return ListView(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      children: [
        const SizedBox(height: 16),
        Center(
          child: Column(
            children: [
              Container(
                width: 88,
                height: 88,
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [Color(0xFF007AFF), Color(0xFF5856D6)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: BorderRadius.circular(22),
                  boxShadow: [
                    BoxShadow(
                      color: const Color(0xFF007AFF).withValues(alpha: 0.35),
                      blurRadius: 18,
                      offset: const Offset(0, 8),
                    ),
                  ],
                ),
                child: const Icon(
                  CupertinoIcons.sparkles,
                  color: CupertinoColors.white,
                  size: 44,
                ),
              ),
              const SizedBox(height: 16),
              const Text(
                'Hyper iOS',
                style: TextStyle(fontSize: 26, fontWeight: FontWeight.bold),
              ),
              const SizedBox(height: 4),
              Text(
                'Version ${store.versionName} • Pure Liquid Glass',
                style: const TextStyle(fontSize: 14, color: CupertinoColors.systemGrey),
              ),
            ],
          ),
        ),
        const SizedBox(height: 28),

        const GlassSectionHeader('Architecture'),
        GlassGroupedSection(
          children: [
            const GlassValueTile(
              title: 'LSPosed API',
              value: 'Modern API 102.0.0',
            ),
            const GlassValueTile(
              title: 'Frontend Framework',
              value: 'Flutter + Impeller',
            ),
            const GlassValueTile(
              title: 'UI Component Kit',
              value: 'liquid_glass_widgets',
            ),
            const GlassValueTile(
              title: 'Shaders',
              value: 'Custom GLSL ES Refraction',
            ),
          ],
        ),

        const GlassSectionHeader('System Environment'),
        GlassGroupedSection(
          children: [
            GlassValueTile(
              title: 'Android SDK',
              value: 'API ${store.sdkInt}',
            ),
            GlassValueTile(
              title: 'Root Privileges',
              value: store.isRootAvailable ? 'Available (su)' : 'Unavailable',
            ),
            GlassValueTile(
              title: 'Xposed Service',
              value: store.isBound ? 'Connected' : 'Disconnected',
            ),
          ],
        ),

        const GlassSectionHeader('Repository & Credits'),
        GlassGroupedSection(
          children: [
            const GlassValueTile(
              title: 'Developer',
              value: '@JosephMontfort',
            ),
            const GlassValueTile(
              title: 'Liquid Glass Engine',
              value: 'iOS 26 Liquid Morph',
            ),
            const GlassValueTile(
              title: 'License',
              value: 'GPL-3.0 / MIT',
            ),
          ],
        ),
        const SizedBox(height: 100),
      ],
    );
  }
}
