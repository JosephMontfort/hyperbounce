import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../bridge/native_bridge.dart';
import '../screens/about_screen.dart';
import 'liquid_bubble/liquid_bubble_menu.dart';
import 'liquid_bubble/navigation_key.dart';

const bool kWallpaperTest =
    bool.fromEnvironment('WALLPAPER_TEST', defaultValue: false);

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
          child: LiquidBubbleMenuTrigger(
            menuWidth: 250,
            items: [
              LiquidBubbleMenuItem(
                title: 'Restart System UI',
                icon: CupertinoIcons.sparkles,
                onTap: () => NativeBridge.restartSystemUI(),
              ),
              LiquidBubbleMenuItem(
                title: 'Restart System Launcher',
                icon: CupertinoIcons.square_grid_2x2,
                onTap: () => NativeBridge.restartLauncher(),
              ),
              LiquidBubbleMenuItem(
                title: 'System Reboot',
                icon: CupertinoIcons.power,
                isDestructive: true,
                onTap: () => NativeBridge.rebootDevice(),
              ),
              const LiquidBubbleMenuDivider(),
              LiquidBubbleMenuItem(
                title: 'About',
                icon: CupertinoIcons.info_circle,
                onTap: () {
                  final nav = Navigator.maybeOf(context) ??
                      liquidNavigatorKey.currentState;
                  nav?.push(
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
      contentAwareBrightness: true,
      background: Container(
        decoration: BoxDecoration(
          gradient: kWallpaperTest
              ? const LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    Color(0xFF2E0854),
                    Color(0xFF0F3057),
                    Color(0xFF00587A),
                    Color(0xFF008891),
                  ],
                )
              : const LinearGradient(
                  begin: Alignment.topCenter,
                  end: Alignment.bottomCenter,
                  colors: [
                    Color(0xFF1E1035),
                    Color(0xFF141936),
                    Color(0xFF0D2137),
                    Color(0xFF09131C),
                    Color(0xFF04060A),
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
