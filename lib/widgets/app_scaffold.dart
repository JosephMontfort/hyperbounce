import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../bridge/native_bridge.dart';
import '../state/settings_store.dart';

/// Top-level Screen Scaffold with iOS 26 Glass Navigation Chrome and Morphing Dropdown Menu
class AppScaffold extends StatelessWidget {
  final String title;
  final Widget body;
  final bool showTopMenu;
  final Widget? bottomBar;
  final ScrollController? scrollController;

  const AppScaffold({
    super.key,
    required this.title,
    required this.body,
    this.showTopMenu = true,
    this.bottomBar,
    this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    final actions = <GlassBarItem>[];

    if (showTopMenu) {
      actions.add(
        GlassBarItem.menu(
          icon: const Icon(CupertinoIcons.ellipsis),
          id: 'root_overflow_menu',
          label: 'More actions',
          menuWidth: 230,
          menuItems: [
            GlassMenuItem(
              title: 'Restart Scoped Apps',
              icon: const Icon(CupertinoIcons.arrow_clockwise),
              onTap: () {},
              submenu: [
                GlassMenuItem(
                  title: 'System UI',
                  icon: const Icon(CupertinoIcons.sparkles),
                  onTap: () => NativeBridge.restartSystemUI(),
                ),
                GlassMenuItem(
                  title: 'System Launcher',
                  icon: const Icon(CupertinoIcons.square_grid_2x2),
                  onTap: () => NativeBridge.restartLauncher(),
                ),
                GlassMenuItem(
                  title: 'Instagram',
                  icon: const Icon(CupertinoIcons.camera),
                  onTap: () => NativeBridge.restartPackage('com.instagram.android', 'Instagram'),
                ),
                GlassMenuItem(
                  title: 'WhatsApp',
                  icon: const Icon(CupertinoIcons.chat_bubble_2),
                  onTap: () => NativeBridge.restartPackage('com.whatsapp', 'WhatsApp'),
                ),
                GlassMenuItem(
                  title: 'YouTube',
                  icon: const Icon(CupertinoIcons.play_rectangle),
                  onTap: () => NativeBridge.restartPackage('com.google.android.youtube', 'YouTube'),
                ),
                const GlassMenuDivider(),
                GlassMenuItem(
                  title: 'Restart All Scoped',
                  icon: const Icon(CupertinoIcons.arrow_clockwise_circle_fill),
                  onTap: () {
                    final scoped = SettingsStore.instance.scopedPackages;
                    NativeBridge.restartScopedApps(scoped.isNotEmpty
                        ? scoped
                        : [
                            'com.android.systemui',
                            'com.miui.home',
                            'com.instagram.android',
                            'com.whatsapp',
                            'com.google.android.youtube',
                          ]);
                  },
                ),
              ],
            ),
            GlassMenuItem(
              title: 'Restart System UI',
              icon: const Icon(CupertinoIcons.bolt),
              onTap: () => NativeBridge.restartSystemUI(),
            ),
            GlassMenuItem(
              title: 'Restart Launcher',
              icon: const Icon(CupertinoIcons.house),
              onTap: () => NativeBridge.restartLauncher(),
            ),
            GlassMenuItem(
              title: 'Reboot Device',
              icon: const Icon(CupertinoIcons.power),
              onTap: () => NativeBridge.rebootDevice(),
            ),
            const GlassMenuDivider(),
            GlassMenuItem(
              title: 'Copy Debug Info',
              icon: const Icon(CupertinoIcons.doc_on_clipboard),
              onTap: () {
                final store = SettingsStore.instance;
                final info = StringBuffer()
                  ..writeln('Hyper iOS v${store.versionName}')
                  ..writeln('Xposed Bound: ${store.isBound}')
                  ..writeln('Scoped Apps: ${store.scopedPackages.length}')
                  ..writeln('Hot Reload: ${store.getBool('hot_reload_enabled')}')
                  ..writeln('Android SDK: ${store.sdkInt}');
                Clipboard.setData(ClipboardData(text: info.toString()));
              },
            ),
            GlassMenuItem(
              title: 'Reset to Defaults',
              icon: const Icon(CupertinoIcons.arrow_counterclockwise),
              isDestructive: true,
              onTap: () => SettingsStore.instance.resetToDefaults(),
            ),
          ],
        ),
      );
    }

    return GlassScaffold(
      headerScrollController: scrollController,
      appBar: GlassAppBar.pinned(
        title: Text(title),
        actions: actions,
      ),
      bottomBar: bottomBar,
      body: body,
    );
  }
}
