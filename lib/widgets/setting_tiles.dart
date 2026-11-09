import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';

/// Section Header matching iOS Inset Grouped design
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
          letterSpacing: 0.6,
          color: CupertinoColors.secondaryLabel,
        ),
      ),
    );
  }
}

/// Section Footer matching iOS Inset Grouped design notes
class GlassSectionFooter extends StatelessWidget {
  final String text;
  const GlassSectionFooter(this.text, {super.key});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(left: 20, right: 20, top: 6, bottom: 14),
      child: Text(
        text,
        style: const TextStyle(
          fontSize: 12.5,
          color: CupertinoColors.tertiaryLabel,
          height: 1.35,
        ),
      ),
    );
  }
}

/// Inset Grouped Section for iOS settings lists
class GlassGroupedCard extends StatelessWidget {
  final List<Widget> children;
  final Widget? header;
  final Widget? footer;

  const GlassGroupedCard({
    super.key,
    required this.children,
    this.header,
    this.footer,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: GlassGroupedSection(
        surfaceStyle: GlassSurfaceStyle.solid,
        header: header,
        footer: footer,
        children: children,
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
  final bool enabled;
  final ValueChanged<bool> onChanged;

  const GlassSwitchTile({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    required this.value,
    this.enabled = true,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return Opacity(
      opacity: enabled ? 1.0 : 0.45,
      child: GlassListTile(
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
                  color: CupertinoColors.secondaryLabel,
                ),
              )
            : null,
        trailing: GlassSwitch(
          value: value,
          onChanged: enabled ? onChanged : (_) {},
        ),
        onTap: enabled ? () => onChanged(!value) : null,
      ),
    );
  }
}

/// A standalone Inset Solid Card containing a title, current value readout, and a GlassSlider
class GlassSliderTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final double value;
  final double min;
  final double max;
  final int? divisions;
  final String? unit;
  final bool enabled;
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
    this.enabled = true,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    final displayValue = (value % 1 == 0)
        ? value.toInt().toString()
        : value.toStringAsFixed(2);
    final unitStr = unit != null ? ' $unit' : '';

    return Opacity(
      opacity: enabled ? 1.0 : 0.45,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
        child: GlassContainer.card(
          surfaceStyle: GlassSurfaceStyle.solid,
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
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
                    color: CupertinoColors.secondaryLabel,
                  ),
                ),
              ],
              const SizedBox(height: 12),
              GlassSlider(
                value: value.clamp(min, max),
                min: min,
                max: max,
                divisions: divisions,
                onChanged: enabled ? onChanged : (_) {},
              ),
            ],
          ),
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
  final bool enabled;
  final VoidCallback onTap;

  const GlassNavTile({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    this.enabled = true,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Opacity(
      opacity: enabled ? 1.0 : 0.45,
      child: GlassListTile(
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
                  color: CupertinoColors.secondaryLabel,
                ),
              )
            : null,
        trailing: GlassListTile.chevron,
        onTap: enabled ? onTap : null,
      ),
    );
  }
}

/// Action Tile for buttons / triggers
class GlassActionTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final Widget? leading;
  final Color? titleColor;
  final bool enabled;
  final VoidCallback onTap;

  const GlassActionTile({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    this.titleColor,
    this.enabled = true,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Opacity(
      opacity: enabled ? 1.0 : 0.45,
      child: GlassListTile(
        leading: leading,
        title: Text(
          title,
          style: TextStyle(
            fontSize: 16,
            fontWeight: FontWeight.w500,
            color: titleColor ?? CupertinoColors.label,
          ),
        ),
        subtitle: subtitle != null
            ? Text(
                subtitle!,
                style: const TextStyle(
                  fontSize: 13,
                  color: CupertinoColors.secondaryLabel,
                ),
              )
            : null,
        trailing: GlassListTile.chevron,
        onTap: enabled ? onTap : null,
      ),
    );
  }
}

/// Dropdown / Option Selector Tile
class GlassDropdownTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final List<String> options;
  final int selectedIndex;
  final bool enabled;
  final ValueChanged<int> onSelect;

  const GlassDropdownTile({
    super.key,
    required this.title,
    this.subtitle,
    required this.options,
    required this.selectedIndex,
    this.enabled = true,
    required this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    final selectedOption = options.isNotEmpty && selectedIndex < options.length
        ? options[selectedIndex]
        : '';

    return Opacity(
      opacity: enabled ? 1.0 : 0.45,
      child: GlassListTile(
        title: Text(
          title,
          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w500),
        ),
        subtitle: subtitle != null
            ? Text(
                subtitle!,
                style: const TextStyle(
                  fontSize: 13,
                  color: CupertinoColors.secondaryLabel,
                ),
              )
            : null,
        trailing: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              selectedOption,
              style: const TextStyle(
                fontSize: 15,
                color: CupertinoColors.secondaryLabel,
              ),
            ),
            const SizedBox(width: 4),
            GlassListTile.chevron,
          ],
        ),
        onTap: enabled
            ? () {
                showCupertinoModalPopup(
                  context: context,
                  builder: (ctx) => CupertinoActionSheet(
                    title: Text(title),
                    message: subtitle != null ? Text(subtitle!) : null,
                    actions: options.asMap().entries.map((e) {
                      final isSelected = e.key == selectedIndex;
                      return CupertinoActionSheetAction(
                        onPressed: () {
                          Navigator.of(ctx).pop();
                          onSelect(e.key);
                        },
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Text(
                              e.value,
                              style: TextStyle(
                                fontWeight: isSelected
                                    ? FontWeight.bold
                                    : FontWeight.normal,
                              ),
                            ),
                            if (isSelected) ...[
                              const SizedBox(width: 8),
                              const Icon(CupertinoIcons.checkmark, size: 18),
                            ],
                          ],
                        ),
                      );
                    }).toList(),
                    cancelButton: CupertinoActionSheetAction(
                      isDefaultAction: true,
                      onPressed: () => Navigator.of(ctx).pop(),
                      child: const Text('Cancel'),
                    ),
                  ),
                );
              }
            : null,
      ),
    );
  }
}
