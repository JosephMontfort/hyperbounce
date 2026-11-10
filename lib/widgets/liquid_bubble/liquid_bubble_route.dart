import 'dart:math' as math;
import 'dart:ui' as ui;
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'squircle.dart';

/// Authentic Notes app Glass Style liquid bubble dropdown menu route.
/// Implements the 4-layer architecture:
/// 1. Scrim Barrier (`menudim`)
/// 2. Outer Liquid Glass Morph Panel (`liquid_glass.menu.panel`)
/// 3. Dynamic Wavefront Mask (`liquid_glass.menu.masked`)
/// 4. Decoupled Menu Action Contents (`liquid_glass.menu.contents`)
class LiquidBubbleMenuRoute<T> extends PopupRoute<T> {
  final Rect sourceRect;
  final Widget menuContent;
  final Size menuSize;

  LiquidBubbleMenuRoute({
    required this.sourceRect,
    required this.menuContent,
    required this.menuSize,
  });

  @override
  Duration get transitionDuration => const Duration(milliseconds: 380);

  @override
  Duration get reverseTransitionDuration => const Duration(milliseconds: 260);

  @override
  bool get barrierDismissible => true;

  @override
  Color? get barrierColor => Colors.transparent;

  @override
  String? get barrierLabel => 'Dismiss Menu';

  @override
  Widget buildPage(
    BuildContext context,
    Animation<double> animation,
    Animation<double> secondaryAnimation,
  ) {
    final mediaQuery = MediaQuery.of(context);
    final screenSize = mediaQuery.size;
    final padding = mediaQuery.padding;

    // Anchor calculation: align top-right corner of menu to sourceRect right edge
    const double margin = 12.0;
    double targetLeft = sourceRect.right - menuSize.width;
    double targetTop = sourceRect.bottom + 8.0;

    // Clamp horizontally to screen bounds
    if (targetLeft < margin) {
      targetLeft = margin;
    }
    if (targetLeft + menuSize.width > screenSize.width - margin) {
      targetLeft = screenSize.width - menuSize.width - margin;
    }

    // Flip upwards if running out of vertical room below
    if (targetTop + menuSize.height > screenSize.height - padding.bottom - margin) {
      targetTop = sourceRect.top - menuSize.height - 8.0;
    }

    final Rect targetRect = Rect.fromLTWH(
      targetLeft,
      targetTop,
      menuSize.width,
      menuSize.height,
    );

    // Forward: Under-damped spring curve recovered from Notes app AOT snapshot
    // Reverse: Smooth ease-in cubic for clean liquid retraction
    final CurvedAnimation curvedAnimation = CurvedAnimation(
      parent: animation,
      curve: const Cubic(0.075, 0.82, 0.4, 1.065),
      reverseCurve: const Cubic(0.32, 0.0, 0.67, 0.0),
    );

    final isDark = CupertinoTheme.brightnessOf(context) == Brightness.dark;

    final borderGradient = LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: isDark
          ? const [
              Color(0x80FFFFFF),
              Color(0x28FFFFFF),
              Color(0x0CFFFFFF),
              Color(0x38FFFFFF),
            ]
          : const [
              Color(0xB0FFFFFF),
              Color(0x50FFFFFF),
              Color(0x20FFFFFF),
              Color(0x60FFFFFF),
            ],
      stops: const [0.0, 0.35, 0.65, 1.0],
    );

    final defaultBg = isDark
        ? const Color(0xCC1A1A22)
        : const Color(0xE0F6F6F9);

    return AnimatedBuilder(
      animation: curvedAnimation,
      builder: (context, _) {
        final double t = curvedAnimation.value;

        // Origin button 3-dots icon dissolve (1.0 -> 0.0 during initial 25% of morph)
        final double buttonIconOpacity = (1.0 - (t / 0.25)).clamp(0.0, 1.0);
        final double buttonScale = 1.0 - (t / 0.25).clamp(0.0, 1.0) * 0.25;

        // Content reveal progress (fade & slide begins at t = 0.22 and settles at t = 0.85)
        final double contentOpacity = const Interval(0.22, 0.80, curve: Curves.easeOut)
            .transform(t.clamp(0.0, 1.0));
        final double contentProgress = const Interval(0.22, 0.95, curve: Curves.easeOutCubic)
            .transform(t.clamp(0.0, 1.0));
        final double contentSlide = (1.0 - contentProgress) * 12.0;
        final double contentScale = 0.90 + 0.10 * contentProgress;

        return Stack(
          fit: StackFit.expand,
          children: [
            // Layer 1: Barrier Scrim (menudim)
            GestureDetector(
              behavior: HitTestBehavior.opaque,
              onTap: () => Navigator.of(context).pop(),
              child: Container(
                color: CupertinoColors.black.withOpacity(t.clamp(0.0, 1.0) * 0.22),
              ),
            ),

            // Layer 2: Liquid Glass Morph Panel (liquid_glass.menu.panel)
            CustomPaint(
              painter: LiquidMetaballBorderPainter(
                sourceRect: sourceRect,
                targetRect: targetRect,
                progress: t,
                cornerRadius: 22.0,
                borderGradient: borderGradient,
                strokeWidth: 1.0,
                isDark: isDark,
              ),
              child: ClipPath(
                clipper: LiquidMetaballClipper(
                  sourceRect: sourceRect,
                  targetRect: targetRect,
                  progress: t,
                  cornerRadius: 22.0,
                ),
                child: BackdropFilter(
                  filter: ui.ImageFilter.blur(sigmaX: 32.0, sigmaY: 32.0),
                  child: Container(
                    color: defaultBg,
                    child: Stack(
                      fit: StackFit.expand,
                      children: [
                        // Layer 3A: Origin 3-dots icon dissolving inside the liquid root
                        if (buttonIconOpacity > 0.01)
                          Positioned(
                            left: sourceRect.left,
                            top: sourceRect.top,
                            width: sourceRect.width,
                            height: sourceRect.height,
                            child: Center(
                              child: Opacity(
                                opacity: buttonIconOpacity,
                                child: Transform.scale(
                                  scale: buttonScale,
                                  child: const Icon(
                                    CupertinoIcons.ellipsis,
                                    size: 20,
                                    color: CupertinoColors.white,
                                  ),
                                ),
                              ),
                            ),
                          ),

                        // Layer 3B & 4: Decoupled Menu Action Contents (liquid_glass.menu.contents)
                        // Clipped by the outer LiquidMetaballClipper so it naturally reveals
                        // as the liquid bubble wavefront expands
                        if (contentOpacity > 0.01)
                          Positioned(
                            left: targetRect.left,
                            top: targetRect.top,
                            width: targetRect.width,
                            height: targetRect.height,
                            child: Opacity(
                              opacity: contentOpacity,
                              child: Transform.translate(
                                offset: Offset(0, contentSlide),
                                child: Transform.scale(
                                  scale: contentScale,
                                  alignment: Alignment.topRight,
                                  child: menuContent,
                                ),
                              ),
                            ),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}
