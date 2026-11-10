import 'dart:math' as math;
import 'dart:ui' show lerpDouble;
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'glass_panel.dart';
import 'liquid_spring.dart';

/// Modal popup route that morphs an origin pill button into an expanded glass menu card
/// using under-damped spring physics and decoupled content masking.
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
  Duration get transitionDuration => const Duration(milliseconds: 400);

  @override
  Duration get reverseTransitionDuration => const Duration(milliseconds: 260);

  @override
  bool get barrierDismissible => true;

  @override
  Color? get barrierColor => const Color(0x38000000);

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

    // Anchor calculation: prefer aligning top-right corner of menu to sourceRect right edge
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

    final targetRect = Rect.fromLTWH(
      targetLeft,
      targetTop,
      menuSize.width,
      menuSize.height,
    );

    final springCurve = LiquidSpringCurve();

    return AnimatedBuilder(
      animation: animation,
      builder: (context, _) {
        final double t = animation.value;

        // Use spring physics for forward "bubble pop", smooth ease-in for reverse collapse
        final double morphProgress = animation.status == AnimationStatus.reverse
            ? Curves.easeInCubic.transform(t)
            : springCurve.transform(t);

        // 1. Interpolate Bounding Geometry
        final currentRect = Rect.lerp(sourceRect, targetRect, morphProgress)!;

        // 2. Interpolate Corner Radius (Capsule Circle -> Apple Squircle)
        final double sourceRadius = math.min(sourceRect.width, sourceRect.height) / 2.0;
        const double targetRadius = 24.0;
        final double currentRadius = lerpDouble(sourceRadius, targetRadius, t.clamp(0.0, 1.0))!;

        // 3. Staggered Decoupled Content Reveal
        final double buttonOpacity = (1.0 - (t / 0.22)).clamp(0.0, 1.0);
        final double buttonScale = 1.0 - (t / 0.22).clamp(0.0, 1.0) * 0.25;

        final double contentOpacity = const Interval(0.20, 0.70, curve: Curves.easeOut)
            .transform(t.clamp(0.0, 1.0));
        final double contentProgress = const Interval(0.20, 0.95, curve: Curves.easeOutCubic)
            .transform(t.clamp(0.0, 1.0));
        final double contentSlide = (1.0 - contentProgress) * 10.0;

        return Stack(
          children: [
            Positioned(
              left: currentRect.left,
              top: currentRect.top,
              width: currentRect.width,
              height: currentRect.height,
              child: GlassPanel(
                radius: currentRadius,
                child: Stack(
                  fit: StackFit.expand,
                  children: [
                    // Layer A: Origin 3-dots icon fades out & shrinks in initial expansion
                    if (buttonOpacity > 0.01)
                      Center(
                        child: Opacity(
                          opacity: buttonOpacity,
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

                    // Layer B: Menu Content masked without squishing via OverflowBox
                    if (contentOpacity > 0.01)
                      OverflowBox(
                        alignment: Alignment.topRight,
                        minWidth: menuSize.width,
                        maxWidth: menuSize.width,
                        minHeight: menuSize.height,
                        maxHeight: menuSize.height,
                        child: Opacity(
                          opacity: contentOpacity,
                          child: Transform.translate(
                            offset: Offset(0, contentSlide),
                            child: menuContent,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}
