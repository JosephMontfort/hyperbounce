import 'dart:math' as math;
import 'dart:ui' show lerpDouble;
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

/// Builds an authentic Glass style liquid metaball morph Path.
/// Connects the origin button capsule and the expanding destination card
/// via an organic surface-tension liquid neck with concave Bézier fillets.
Path buildLiquidMorphPath({
  required Rect sourceRect,
  required Rect targetRect,
  required double progress,
  required double cornerRadius,
}) {
  final double p = progress.clamp(0.0, 1.0);
  final double sourceRadius = sourceRect.height / 2.0;

  if (p <= 0.005) {
    return buildSquirclePath(sourceRect, sourceRadius);
  }
  if (p >= 0.995) {
    return buildSquirclePath(targetRect, cornerRadius);
  }

  // Interpolated card bounds
  final Rect startCard = Rect.fromCenter(
    center: sourceRect.center,
    width: sourceRect.width,
    height: sourceRect.height,
  );
  final Rect currentCard = Rect.lerp(startCard, targetRect, p)!;
  final double currentCardRadius =
      lerpDouble(sourceRadius, cornerRadius, p)!;

  final Path pathSource = buildSquirclePath(sourceRect, sourceRadius);
  final Path pathCard = buildSquirclePath(currentCard, currentCardRadius);

  // While progress < 0.82, an organic fluid metaball neck connects source and card
  if (p < 0.82) {
    final double tension =
        math.pow((1.0 - (p / 0.82)).clamp(0.0, 1.0), 1.25).toDouble();
    final double neckHalfWidth = (sourceRect.width / 2.0) * tension;

    if (neckHalfWidth > 1.2) {
      final double sx = sourceRect.center.dx;
      final double sy = sourceRect.bottom;
      final double cardTop = currentCard.top;

      final Offset ptLeft = Offset(sx - neckHalfWidth, sy - 2.0);
      final Offset ptRight = Offset(sx + neckHalfWidth, sy - 2.0);

      final double cardAnchorHw = (neckHalfWidth * 1.35 + 4.0)
          .clamp(6.0, currentCard.width / 2.0);
      final double bottomCenterX = sx.clamp(
        currentCard.left + cardAnchorHw + 4.0,
        currentCard.right - cardAnchorHw - 4.0,
      );

      final Offset pbLeft = Offset(bottomCenterX - cardAnchorHw, cardTop + 2.0);
      final Offset pbRight = Offset(bottomCenterX + cardAnchorHw, cardTop + 2.0);

      final double spanY = (cardTop - (sy - 2.0)).abs();
      final double pinch = math.max(2.0, 7.5 * tension);

      final Offset c1Left = Offset(ptLeft.dx + pinch, sy + spanY * 0.35);
      final Offset c2Left = Offset(pbLeft.dx + pinch, cardTop - spanY * 0.35);

      final Offset c1Right = Offset(ptRight.dx - pinch, sy + spanY * 0.35);
      final Offset c2Right = Offset(pbRight.dx - pinch, cardTop - spanY * 0.35);

      final Path bridgePath = Path()
        ..moveTo(ptLeft.dx, ptLeft.dy)
        ..cubicTo(c1Left.dx, c1Left.dy, c2Left.dx, c2Left.dy, pbLeft.dx, pbLeft.dy)
        ..lineTo(pbRight.dx, pbRight.dy)
        ..cubicTo(c2Right.dx, c2Right.dy, c1Right.dx, c1Right.dy, ptRight.dx, ptRight.dy)
        ..close();

      try {
        final Path merged = Path.combine(PathOperation.union, pathSource, pathCard);
        return Path.combine(PathOperation.union, merged, bridgePath);
      } catch (_) {
        return pathCard;
      }
    }
  }

  // Once the neck pinches off (p >= 0.82), the card is the primary liquid container
  try {
    return Path.combine(PathOperation.union, pathSource, pathCard);
  } catch (_) {
    return pathCard;
  }
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

/// A CustomClipper that clips children into the dynamic liquid metaball morph contour.
class LiquidMetaballClipper extends CustomClipper<Path> {
  final Rect sourceRect;
  final Rect targetRect;
  final double progress;
  final double cornerRadius;

  const LiquidMetaballClipper({
    required this.sourceRect,
    required this.targetRect,
    required this.progress,
    required this.cornerRadius,
  });

  @override
  Path getClip(Size size) {
    return buildLiquidMorphPath(
      sourceRect: sourceRect,
      targetRect: targetRect,
      progress: progress,
      cornerRadius: cornerRadius,
    );
  }

  @override
  bool shouldReclip(covariant LiquidMetaballClipper oldClipper) {
    return oldClipper.progress != progress ||
        oldClipper.sourceRect != sourceRect ||
        oldClipper.targetRect != targetRect ||
        oldClipper.cornerRadius != cornerRadius;
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

/// A CustomPainter that paints specular Fresnel illuminated rim strokes
/// and soft drop shadows along the dynamic liquid metaball contour.
class LiquidMetaballBorderPainter extends CustomPainter {
  final Rect sourceRect;
  final Rect targetRect;
  final double progress;
  final double cornerRadius;
  final Gradient borderGradient;
  final double strokeWidth;
  final bool isDark;

  const LiquidMetaballBorderPainter({
    required this.sourceRect,
    required this.targetRect,
    required this.progress,
    required this.cornerRadius,
    required this.borderGradient,
    this.strokeWidth = 1.0,
    this.isDark = true,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final Path liquidPath = buildLiquidMorphPath(
      sourceRect: sourceRect,
      targetRect: targetRect,
      progress: progress,
      cornerRadius: cornerRadius,
    );

    final Rect bounds = liquidPath.getBounds();

    // 1. Soft liquid ambient shadow
    final double shadowOpacity =
        (progress.clamp(0.0, 1.0) * (isDark ? 0.45 : 0.18));
    final Paint shadowPaint = Paint()
      ..color = const Color(0xFF000000).withOpacity(shadowOpacity)
      ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 24.0);
    canvas.save();
    canvas.translate(0, 10.0 * progress);
    canvas.drawPath(liquidPath, shadowPaint);
    canvas.restore();

    // 2. Specular Fresnel perimeter border
    if (strokeWidth > 0) {
      final Paint borderPaint = Paint()
        ..shader = borderGradient.createShader(bounds)
        ..style = PaintingStyle.stroke
        ..strokeWidth = strokeWidth
        ..isAntiAlias = true;
      canvas.drawPath(liquidPath, borderPaint);
    }

    // 3. Crisp specular glint along the top meniscus
    if (bounds.width > 20) {
      final Rect topArc = Rect.fromLTWH(
        bounds.left + 8,
        bounds.top,
        math.max(10.0, bounds.width - 16),
        3.0,
      );
      final Paint glintPaint = Paint()
        ..shader = LinearGradient(
          colors: [
            const Color(0x00FFFFFF),
            isDark ? const Color(0x60FFFFFF) : const Color(0x90FFFFFF),
            const Color(0x00FFFFFF),
          ],
        ).createShader(topArc)
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.2
        ..isAntiAlias = true;
      canvas.drawLine(
        Offset(topArc.left, topArc.top + 0.5),
        Offset(topArc.right, topArc.top + 0.5),
        glintPaint,
      );
    }
  }

  @override
  bool shouldRepaint(covariant LiquidMetaballBorderPainter oldDelegate) {
    return oldDelegate.progress != progress ||
        oldDelegate.sourceRect != sourceRect ||
        oldDelegate.targetRect != targetRect ||
        oldDelegate.strokeWidth != strokeWidth ||
        oldDelegate.isDark != isDark;
  }
}
