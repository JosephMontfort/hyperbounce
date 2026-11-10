import 'package:flutter/animation.dart';
import 'package:flutter/physics.dart';

/// Authentic iOS 26 mass-spring simulation curve recovered from Notes app.
/// Produces the fluid overshoot ("bubble pop") before smoothly settling to 1.0.
class LiquidSpringCurve extends Curve {
  final SpringSimulation _sim;
  static const double _kNaturalSettlingScale = 0.545;

  LiquidSpringCurve({
    double mass = 1.0,
    double stiffness = 180.0,
    double damping = 18.0,
  }) : _sim = SpringSimulation(
          SpringDescription(
            mass: mass,
            stiffness: stiffness,
            damping: damping,
          ),
          0.0,
          1.0,
          0.0,
        );

  @override
  double transformInternal(double t) {
    if (t <= 0.0) return 0.0;
    if (t >= 1.0) return 1.0;
    // Evaluates the under-damped 2nd-order spring equation
    return _sim.x(t * _kNaturalSettlingScale).clamp(-0.1, 1.15);
  }
}
