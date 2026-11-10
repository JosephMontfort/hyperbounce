import 'package:flutter/widgets.dart';

/// Global navigator key ensuring dialogs, sheets, and liquid bubble menus can be
/// pushed reliably from any layer of the app chrome without tree-lookup failures.
final GlobalKey<NavigatorState> liquidNavigatorKey = GlobalKey<NavigatorState>();
