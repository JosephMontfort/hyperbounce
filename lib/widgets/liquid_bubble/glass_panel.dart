import 'dart:ui';
import 'package:flutter/cupertino.dart';
import 'package:flutter/material.dart';
import 'squircle.dart';

/// High-fidelity liquid glass panel container with hardware-accelerated backdrop blur,
/// authentic Apple G2 continuous squircle curvature, and dynamic specular rim lighting.
class GlassPanel extends StatelessWidget {
  final Widget child;
  final double radius;
  final double blurSigma;
  final Color? backgroundColor;
  final bool enableBorder;
  final double borderWidth;
  final List<BoxShadow>? shadows;

  const GlassPanel({
    super.key,
    required this.child,
    this.radius = 24.0,
    this.blurSigma = 28.0,
    this.backgroundColor,
    this.enableBorder = true,
    this.borderWidth = 0.9,
    this.shadows,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = CupertinoTheme.brightnessOf(context) == Brightness.dark;

    final defaultBg = isDark
        ? const Color(0xCC18181D)
        : const Color(0xD8F8F8FC);

    final borderGradient = LinearGradient(
      begin: Alignment.topLeft,
      end: Alignment.bottomRight,
      colors: isDark
          ? const [
              Color(0x55FFFFFF),
              Color(0x18FFFFFF),
              Color(0x06FFFFFF),
              Color(0x24FFFFFF),
            ]
          : const [
              Color(0x99FFFFFF),
              Color(0x40FFFFFF),
              Color(0x18FFFFFF),
              Color(0x50FFFFFF),
            ],
      stops: const [0.0, 0.35, 0.65, 1.0],
    );

    final defaultShadows = [
      BoxShadow(
        color: isDark ? const Color(0x66000000) : const Color(0x1F000000),
        blurRadius: 36,
        spreadRadius: -2,
        offset: const Offset(0, 16),
      ),
      BoxShadow(
        color: isDark ? const Color(0x2E000000) : const Color(0x0F000000),
        blurRadius: 12,
        offset: const Offset(0, 4),
      ),
    ];

    return Container(
      decoration: BoxDecoration(
        boxShadow: shadows ?? defaultShadows,
      ),
      child: ClipPath(
        clipper: SquircleClipper(radius: radius),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: blurSigma, sigmaY: blurSigma),
          child: CustomPaint(
            foregroundPainter: enableBorder
                ? SquircleBorderPainter(
                    radius: radius,
                    gradient: borderGradient,
                    strokeWidth: borderWidth,
                  )
                : null,
            child: Container(
              color: backgroundColor ?? defaultBg,
              child: child,
            ),
          ),
        ),
      ),
    );
  }
}
