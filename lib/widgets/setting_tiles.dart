import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

/// Section Header with uppercase title matching iOS Settings design
class GlassSectionHeader extends StatelessWidget {
  final String title;
  const GlassSectionHeader(this.title, {super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 20, right: 20, top: 22, bottom: 8),
      child: Text(
        title.toUpperCase(),
        style: const TextStyle(
          fontSize: 12.5,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.8,
          color: CupertinoColors.systemGrey,
        ),
      ),
    );
  }
}

/// A GlassListTile with a trailing GlassSwitch
class GlassSwitchTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final Widget? leading;
  final bool value;
  final ValueChanged<bool> onChanged;

  const GlassSwitchTile({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return GlassListTile(
      leading: leading,
      title: Text(
        title,
        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
      ),
      subtitle: subtitle != null
          ? Text(
              subtitle!,
              style: const TextStyle(
                fontSize: 13,
                color: CupertinoColors.systemGrey,
              ),
            )
          : null,
      trailing: GlassSwitch(
        value: value,
        onChanged: onChanged,
      ),
      onTap: () => onChanged(!value),
    );
  }
}

/// A standalone GlassCard containing a title, current value readout, and a GlassSlider
class GlassSliderTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final double value;
  final double min;
  final double max;
  final int? divisions;
  final String? unit;
  final ValueChanged<double> onChanged;

  const GlassSliderTile({
    super.key,
    required this.title,
    this.subtitle,
    required this.value,
    required this.min,
    required this.max,
    this.divisions,
    this.unit,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final displayValue = (value % 1 == 0)
        ? value.toInt().toString()
        : value.toStringAsFixed(2);
    final unitStr = unit != null ? ' $unit' : '';

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
      child: GlassCard(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Text(
                    title,
                    style: const TextStyle(
                      fontSize: 15.5,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                Text(
                  '$displayValue$unitStr',
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    color: CupertinoColors.activeBlue,
                  ),
                ),
              ],
            ),
            if (subtitle != null) ...[
              const SizedBox(height: 3),
              Text(
                subtitle!,
                style: const TextStyle(
                  fontSize: 12.5,
                  color: CupertinoColors.systemGrey,
                ),
              ),
            ],
            const SizedBox(height: 12),
            GlassSlider(
              value: value.clamp(min, max),
              min: min,
              max: max,
              divisions: divisions,
              onChanged: onChanged,
            ),
          ],
        ),
      ),
    );
  }
}

/// Navigation Tile pointing to subpage
class GlassNavTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final Widget? leading;
  final VoidCallback onTap;

  const GlassNavTile({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GlassListTile(
      leading: leading,
      title: Text(
        title,
        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
      ),
      subtitle: subtitle != null
          ? Text(
              subtitle!,
              style: const TextStyle(
                fontSize: 13,
                color: CupertinoColors.systemGrey,
              ),
            )
          : null,
      trailing: GlassListTile.chevron,
      onTap: onTap,
    );
  }
}

/// Static or action tile displaying value with optional tap
class GlassValueTile extends StatelessWidget {
  final String title;
  final String value;
  final Widget? leading;
  final VoidCallback? onTap;

  const GlassValueTile({
    super.key,
    required this.title,
    required this.value,
    this.leading,
    this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return GlassListTile(
      leading: leading,
      title: Text(
        title,
        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
      ),
      trailing: Text(
        value,
        style: const TextStyle(
          fontSize: 15,
          color: CupertinoColors.systemGrey,
        ),
      ),
      onTap: onTap,
    );
  }
}
