import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

import 'state/settings_store.dart';
import 'widgets/app_scaffold.dart';
import 'screens/tuning_screen.dart';
import 'screens/settings_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // Initialize the Liquid Glass fragment shader engine & physics
  await LiquidGlassWidgets.initialize();

  // Initialize native SharedPreferences & LSPosed bridge
  final store = SettingsStore.instance;
  await store.initialize();

  // Set transparent system bars for edge-to-edge experience
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: CupertinoColors.transparent,
      systemNavigationBarColor: CupertinoColors.transparent,
      systemNavigationBarContrastEnforced: false,
    ),
  );

  runApp(
    LiquidGlassWidgets.wrap(
      adaptiveQuality: true,
      child: const HyperiOSApp(),
    ),
  );
}

class HyperiOSApp extends StatelessWidget {
  const HyperiOSApp({super.key});

  @override
  Widget build(BuildContext context) {
    return CupertinoApp(
      title: 'HyperiOS',
      debugShowCheckedModeBanner: false,
      theme: const CupertinoThemeData(
        brightness: Brightness.dark,
        primaryColor: CupertinoColors.activeBlue,
        scaffoldBackgroundColor: Color(0xFF000000),
        barBackgroundColor: Color(0xCC1A1A1A),
      ),
      // GlassNavigationShell provides iOS 26 pinned navigation chrome across route changes
      builder: (context, child) => GlassNavigationShell(child: child!),
      home: const RootNavigationScreen(),
    );
  }
}

class RootNavigationScreen extends StatefulWidget {
  const RootNavigationScreen({super.key});

  @override
  State<RootNavigationScreen> createState() => _RootNavigationScreenState();
}

class _RootNavigationScreenState extends State<RootNavigationScreen> {
  int _selectedTabIndex = 0;
  final PageController _pageController = PageController();

  final List<String> _titles = const ['HyperiOS', 'Settings'];

  void _onTabSelected(int index) {
    if (_selectedTabIndex == index) return;
    setState(() => _selectedTabIndex = index);
    _pageController.animateToPage(
      index,
      duration: const Duration(milliseconds: 320),
      curve: Curves.easeOutCubic,
    );
  }

  @override
  void dispose() {
    _pageController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      isRoot: true,
      title: _titles[_selectedTabIndex],
      showTopMenu: true,
      bottomBar: GlassTabBar.bottom(
        tabs: const [
          GlassTab(
            icon: Icon(CupertinoIcons.slider_horizontal_3),
            label: 'Tuning',
          ),
          GlassTab(
            icon: Icon(CupertinoIcons.gear_alt),
            label: 'Settings',
          ),
        ],
        selectedIndex: _selectedTabIndex,
        onTap: _onTabSelected,
      ),
      body: PageView(
        controller: _pageController,
        onPageChanged: (index) => setState(() => _selectedTabIndex = index),
        children: const [
          TuningScreen(),
          SettingsScreen(),
        ],
      ),
    );
  }
}
