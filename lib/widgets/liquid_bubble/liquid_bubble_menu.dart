import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'glass_panel.dart';
import 'liquid_bubble_route.dart';
import 'navigation_key.dart';
import 'squircle.dart';

/// Interactive entry inside a [LiquidBubbleMenuCard].
class LiquidBubbleMenuItem extends StatefulWidget {
  final String title;
  final IconData icon;
  final VoidCallback onTap;
  final bool isDestructive;
  final double height;

  const LiquidBubbleMenuItem({
    super.key,
    required this.title,
    required this.icon,
    required this.onTap,
    this.isDestructive = false,
    this.height = 44.0,
  });

  @override
  State<LiquidBubbleMenuItem> createState() => _LiquidBubbleMenuItemState();
}

class _LiquidBubbleMenuItemState extends State<LiquidBubbleMenuItem> {
  bool _isPressed = false;

  @override
  Widget build(BuildContext context) {
    final highlightColor = widget.isDestructive
        ? const Color(0x33FF3B30)
        : const Color(0x24FFFFFF);

    final textColor = widget.isDestructive
        ? const Color(0xFFFF453A)
        : CupertinoColors.white;

    final iconColor = widget.isDestructive
        ? const Color(0xFFFF453A)
        : CupertinoColors.white.withOpacity(0.85);

    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: (_) {
        HapticFeedback.selectionClick();
        setState(() => _isPressed = true);
      },
      onTapUp: (_) {
        setState(() => _isPressed = false);
        final nav = Navigator.maybeOf(context) ?? liquidNavigatorKey.currentState;
        nav?.pop();
        widget.onTap();
      },
      onTapCancel: () => setState(() => _isPressed = false),
      child: Container(
        height: widget.height,
        margin: const EdgeInsets.symmetric(horizontal: 6, vertical: 1.5),
        padding: const EdgeInsets.symmetric(horizontal: 14),
        decoration: BoxDecoration(
          color: _isPressed ? highlightColor : Colors.transparent,
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          children: [
            Expanded(
              child: Text(
                widget.title,
                style: TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w400,
                  letterSpacing: -0.2,
                  color: textColor,
                ),
              ),
            ),
            Icon(
              widget.icon,
              size: 20,
              color: iconColor,
            ),
          ],
        ),
      ),
    );
  }
}

/// Frosted hairline separator between menu entries.
class LiquidBubbleMenuDivider extends StatelessWidget {
  const LiquidBubbleMenuDivider({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 0.5,
      margin: const EdgeInsets.symmetric(horizontal: 14, vertical: 4),
      color: const Color(0x20FFFFFF),
    );
  }
}

/// The inner menu card widget holding entries.
class LiquidBubbleMenuCard extends StatelessWidget {
  final List<Widget> items;

  const LiquidBubbleMenuCard({super.key, required this.items});

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: items,
        ),
      ),
    );
  }
}

/// The trigger button placed in the app bar that initiates the liquid bubble expansion.
class LiquidBubbleMenuTrigger extends StatefulWidget {
  final List<Widget> items;
  final double width;
  final double height;
  final double menuWidth;

  const LiquidBubbleMenuTrigger({
    super.key,
    required this.items,
    this.width = 38,
    this.height = 38,
    this.menuWidth = 250,
  });

  @override
  State<LiquidBubbleMenuTrigger> createState() => _LiquidBubbleMenuTriggerState();
}

class _LiquidBubbleMenuTriggerState extends State<LiquidBubbleMenuTrigger> {
  final GlobalKey _buttonKey = GlobalKey();
  bool _isPressed = false;
  bool _isMenuOpen = false;

  void _openMenu() {
    final renderBox =
        _buttonKey.currentContext?.findRenderObject() as RenderBox?;
    if (renderBox == null || !renderBox.hasSize) return;

    final sourceRect = renderBox.localToGlobal(Offset.zero) & renderBox.size;

    // Calculate approximate height from items
    double estimatedHeight = 16.0; // padding top + bottom
    for (final item in widget.items) {
      if (item is LiquidBubbleMenuItem) {
        estimatedHeight += item.height + 3.0; // height + margin
      } else if (item is LiquidBubbleMenuDivider) {
        estimatedHeight += 8.5; // hairline + margin
      } else {
        estimatedHeight += 44.0;
      }
    }

    final nav = Navigator.maybeOf(context, rootNavigator: true) ??
        Navigator.maybeOf(context) ??
        liquidNavigatorKey.currentState;

    if (nav == null) return;

    setState(() => _isMenuOpen = true);

    final route = LiquidBubbleMenuRoute<void>(
      sourceRect: sourceRect,
      menuSize: Size(widget.menuWidth, estimatedHeight),
      menuContent: LiquidBubbleMenuCard(items: widget.items),
    );

    try {
      nav.push(route).then((_) {
        if (mounted) {
          setState(() => _isMenuOpen = false);
        }
      }).catchError((_) {
        if (mounted) {
          setState(() => _isMenuOpen = false);
        }
      });
    } catch (_) {
      if (mounted) {
        setState(() => _isMenuOpen = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      key: _buttonKey,
      width: widget.width,
      height: widget.height,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTapDown: (_) {
          HapticFeedback.lightImpact();
          setState(() => _isPressed = true);
        },
        onTapUp: (_) {
          setState(() => _isPressed = false);
          _openMenu();
        },
        onTapCancel: () => setState(() => _isPressed = false),
        child: AnimatedScale(
          scale: _isPressed ? 0.90 : 1.0,
          duration: const Duration(milliseconds: 140),
          curve: Curves.easeOutCubic,
          child: Opacity(
            // Faded while morph route is active so route owns visual continuity
            opacity: _isMenuOpen ? 0.0 : 1.0,
            child: GlassPanel(
              radius: widget.height / 2.0,
              blurSigma: 24.0,
              borderWidth: 0.8,
              child: const Center(
                child: Icon(
                  CupertinoIcons.ellipsis,
                  size: 20,
                  color: CupertinoColors.white,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
