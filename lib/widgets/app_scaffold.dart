import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../bridge/native_bridge.dart';
import '../screens/about_screen.dart';

/// App Scaffold wrapping screens with iOS 26 Liquid Glass Chrome
class AppScaffold extends StatelessWidget {
  final String title;
  final Widget body;
  final bool isRoot;
  final bool showTopMenu;
  final Widget? bottomBar;
  final ScrollController? scrollController;

  const AppScaffold({
    super.key,
    required this.title,
    required this.body,
    this.isRoot = false,
    this.showTopMenu = true,
    this.bottomBar,
    this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    final actions = <GlassBarItem>[];

    if (showTopMenu) {
      actions.add(
        GlassBarItem.custom(
          id: 'overflow_morph_menu',
          child: GlassMenu(
            menuWidth: 250,
            menuBorderRadius: 28,
            quality: GlassQuality.premium,
            triggerBuilder: (context, toggleMenu) {
              return GlassButton(
                onTap: toggleMenu,
                width: 38,
                height: 38,
                shape: const LiquidOval(),
                icon: const Icon(
                  CupertinoIcons.ellipsis,
                  size: 20,
                  color: CupertinoColors.white,
                ),
              );
            },
            items: [
              GlassMenuItem(
                title: 'Restart System UI',
                icon: const Icon(CupertinoIcons.sparkles),
                onTap: () => NativeBridge.restartSystemUI(),
              ),
              GlassMenuItem(
                title: 'Restart System Launcher',
                icon: const Icon(CupertinoIcons.square_grid_2x2),
                onTap: () => NativeBridge.restartLauncher(),
              ),
              GlassMenuItem(
                title: 'System Reboot',
                icon: const Icon(CupertinoIcons.power),
                isDestructive: true,
                onTap: () => NativeBridge.rebootDevice(),
              ),
              const GlassMenuDivider(),
              GlassMenuItem(
                title: 'About',
                icon: const Icon(CupertinoIcons.info_circle),
                onTap: () {
                  Navigator.of(context).push(
                    CupertinoPageRoute(builder: (_) => const AboutScreen()),
                  );
                },
              ),
            ],
          ),
        ),
      );
    }

    return GlassScaffold(
      headerScrollController: scrollController,
      background: Container(
        decoration: const BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              Color(0xFF14151C),
              Color(0xFF090A0D),
              Color(0xFF000000),
            ],
          ),
        ),
      ),
      appBar: GlassAppBar.pinned(
        leading: isRoot
            ? const []
            : [
                GlassBarItem.icon(
                  icon: const Icon(CupertinoIcons.chevron_left, size: 22),
                  onTap: () => Navigator.of(context).pop(),
                ),
              ],
        title: Text(
          title,
          style: const TextStyle(
            fontSize: 17,
            fontWeight: FontWeight.w600,
            letterSpacing: -0.4,
          ),
        ),
        actions: actions,
      ),
      bottomBar: bottomBar,
      body: body,
    );
  }
}
