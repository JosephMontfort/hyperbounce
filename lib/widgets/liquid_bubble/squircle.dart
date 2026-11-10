import 'dart:math' as math;
import 'package:flutter/widgets.dart';

/// Polynomial table decompiled from Notes app (com.inotesapp.inotes.SquircleFrameLayout).
/// Replicates Apple's authentic G2 continuous curvature superellipse squircle.
const List<double> kSquircleG = [
  1.26288, 0.0,
  1.15836, 0.00916,
  1.05402, 0.01278,
  0.95357, 0.01798,
  0.85046, 0.02603,
  0.75188, 0.04286,
  0.65557, 0.06992,
  0.56266, 0.10689,
  0.47411, 0.15339,
  0.39093, 0.20889,
  0.31394, 0.27286,
  0.29289, 0.29289,
];

const double kSquircleDivisor = 2.52576;

/// Builds a continuous curvature squircle Path matching Apple iOS 26 G2 superellipse.
Path buildSquirclePath(Rect rect, double cornerRadius) {
  final path = Path();
  final width = rect.width;
  final height = rect.height;
  if (width <= 0 || height <= 0) return path;

  final double maxAllowed = math.min(width, height) / kSquircleDivisor;
  final double fMin = cornerRadius <= 0.0
      ? 0.0
      : (cornerRadius < maxAllowed ? cornerRadius : maxAllowed);

  if (fMin <= 0.01) {
    path.addRect(rect);
    return path;
  }

  final double left = rect.left;
  final double top = rect.top;

  // Start at top edge right before top-right curvature initiates
  path.moveTo(left + width - kSquircleG[0] * fMin, top);

  // Corner 1: Top-Right
  for (int i = 0; i < 12; i++) {
    final x = kSquircleG[i * 2] * fMin;
    final y = kSquircleG[i * 2 + 1] * fMin;
    path.lineTo(left + width - x, top + y);
  }
  for (int i = 10; i >= 0; i--) {
    final x = kSquircleG[i * 2 + 1] * fMin;
    final y = kSquircleG[i * 2] * fMin;
    path.lineTo(left + width - x, top + y);
  }

  // Corner 2: Bottom-Right
  for (int i = 0; i < 12; i++) {
    final x = kSquircleG[i * 2 + 1] * fMin;
    final y = kSquircleG[i * 2] * fMin;
    path.lineTo(left + width - x, top + height - y);
  }
  for (int i = 10; i >= 0; i--) {
    final x = kSquircleG[i * 2] * fMin;
    final y = kSquircleG[i * 2 + 1] * fMin;
    path.lineTo(left + width - x, top + height - y);
  }

  // Corner 3: Bottom-Left
  for (int i = 0; i < 12; i++) {
    final x = kSquircleG[i * 2] * fMin;
    final y = kSquircleG[i * 2 + 1] * fMin;
    path.lineTo(left + x, top + height - y);
  }
  for (int i = 10; i >= 0; i--) {
    final x = kSquircleG[i * 2 + 1] * fMin;
    final y = kSquircleG[i * 2] * fMin;
    path.lineTo(left + x, top + height - y);
  }

  // Corner 4: Top-Left
  for (int i = 0; i < 12; i++) {
    final x = kSquircleG[i * 2 + 1] * fMin;
    final y = kSquircleG[i * 2] * fMin;
    path.lineTo(left + x, top + y);
  }
  for (int i = 10; i >= 0; i--) {
    final x = kSquircleG[i * 2] * fMin;
    final y = kSquircleG[i * 2 + 1] * fMin;
    path.lineTo(left + x, top + y);
  }

  path.close();
  return path;
}

/// A CustomClipper that clips children into Apple G2 continuous curvature squircle.
class SquircleClipper extends CustomClipper<Path> {
  final double radius;

  const SquircleClipper({required this.radius});

  @override
  Path getClip(Size size) {
    return buildSquirclePath(Offset.zero & size, radius);
  }

  @override
  bool shouldReclip(covariant SquircleClipper oldClipper) {
    return oldClipper.radius != radius;
  }
}

/// A CustomPainter that paints a smooth specular gradient stroke along the squircle perimeter.
class SquircleBorderPainter extends CustomPainter {
  final double radius;
  final Gradient gradient;
  final double strokeWidth;

  const SquircleBorderPainter({
    required this.radius,
    required this.gradient,
    this.strokeWidth = 1.0,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (strokeWidth <= 0) return;
    final rect = Offset.zero & size;
    final path = buildSquirclePath(rect, radius);
    final paint = Paint()
      ..shader = gradient.createShader(rect)
      ..style = PaintingStyle.stroke
      ..strokeWidth = strokeWidth
      ..isAntiAlias = true;
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant SquircleBorderPainter oldDelegate) {
    return oldDelegate.radius != radius ||
        oldDelegate.strokeWidth != strokeWidth ||
        oldDelegate.gradient != gradient;
  }
}
