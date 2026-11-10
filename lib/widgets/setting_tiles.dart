import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../state/settings_store.dart';

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

/// A reactive setting builder that listens only to a single key in SettingsStore.
class SettingListener<T> extends StatelessWidget {
  final String settingKey;
  final T Function(SettingsStore store) getter;
  final Widget Function(BuildContext context, T value) builder;

  const SettingListener({
    super.key,
    required this.settingKey,
    required this.getter,
    required this.builder,
  });

  @override
  Widget build(BuildContext context) {
    final store = SettingsStore.instance;
    return ValueListenableBuilder<dynamic>(
      valueListenable: store.listenKey(settingKey),
      builder: (context, _, __) {
        return builder(context, getter(store));
      },
    );
  }
}

/// A GlassListTile with a trailing GlassSwitch
class GlassSwitchTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final Widget? leading;
  final bool? value;
  final String? settingKey;
  final String? enabledKey;
  final bool enabled;
  final ValueChanged<bool>? onChanged;

  const GlassSwitchTile({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    this.value,
    this.settingKey,
    this.enabledKey,
    this.enabled = true,
    this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    if (enabledKey != null) {
      return SettingListener<bool>(
        settingKey: enabledKey!,
        getter: (store) => store.getBool(enabledKey!, true),
        builder: (ctx, isParentEnabled) =>
            _buildWithEnabled(context, isParentEnabled && enabled),
      );
    }
    return _buildWithEnabled(context, enabled);
  }

  Widget _buildWithEnabled(BuildContext context, bool isEffectiveEnabled) {
    if (settingKey != null && value == null) {
      return SettingListener<bool>(
        settingKey: settingKey!,
        getter: (store) => store.getBool(settingKey!),
        builder: (ctx, val) => _buildRow(ctx, val, isEffectiveEnabled),
      );
    }
    return _buildRow(context, value ?? false, isEffectiveEnabled);
  }

  Widget _buildRow(
      BuildContext context, bool currentValue, bool isEffectiveEnabled) {
    final store = SettingsStore.instance;
    final handler = onChanged ??
        (settingKey != null
            ? (val) => store.setBool(settingKey!, val)
            : (bool _) {});

    return Opacity(
      opacity: isEffectiveEnabled ? 1.0 : 0.45,
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
          value: currentValue,
          onChanged: isEffectiveEnabled ? handler : (_) {},
        ),
        onTap: isEffectiveEnabled ? () => handler(!currentValue) : null,
      ),
    );
  }
}

/// A standalone Inset Solid Card containing a title, current value readout, and a GlassSlider
class GlassSliderTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final Widget? leading;
  final double? value;
  final String? settingKey;
  final String? enabledKey;
  final double min;
  final double max;
  final int? divisions;
  final String? unit;
  final bool enabled;
  final ValueChanged<double>? onChanged;
  final ValueChanged<double>? onChangeEnd;

  const GlassSliderTile({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    this.value,
    this.settingKey,
    this.enabledKey,
    required this.min,
    required this.max,
    this.divisions,
    this.unit,
    this.enabled = true,
    this.onChanged,
    this.onChangeEnd,
  });

  @override
  Widget build(BuildContext context) {
    if (enabledKey != null) {
      return SettingListener<bool>(
        settingKey: enabledKey!,
        getter: (store) => store.getBool(enabledKey!, true),
        builder: (ctx, isParentEnabled) =>
            _buildWithEnabled(context, isParentEnabled && enabled),
      );
    }
    return _buildWithEnabled(context, enabled);
  }

  Widget _buildWithEnabled(BuildContext context, bool isEffectiveEnabled) {
    if (settingKey != null && value == null) {
      return SettingListener<double>(
        settingKey: settingKey!,
        getter: (store) => store.getDouble(settingKey!, min),
        builder: (ctx, val) => _buildCard(ctx, val, isEffectiveEnabled),
      );
    }
    return _buildCard(context, value ?? min, isEffectiveEnabled);
  }

  Widget _buildCard(
      BuildContext context, double currentValue, bool isEffectiveEnabled) {
    final store = SettingsStore.instance;
    final clampedVal = currentValue.clamp(min, max);

    final displayValue = (clampedVal % 1 == 0)
        ? clampedVal.toInt().toString()
        : clampedVal.toStringAsFixed(2);
    final unitStr = unit != null ? ' $unit' : '';

    return Opacity(
      opacity: isEffectiveEnabled ? 1.0 : 0.45,
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
                value: clampedVal,
                min: min,
                max: max,
                divisions: divisions,
                onChanged: isEffectiveEnabled
                    ? (val) {
                        FrameTimingMonitor.start();
                        if (onChanged != null) {
                          onChanged!(val);
                        } else if (settingKey != null) {
                          store.setDoubleThrottled(settingKey!, val);
                        }
                      }
                    : (_) {},
                onChangeEnd: (val) {
                  FrameTimingMonitor.stop();
                  if (onChangeEnd != null) {
                    onChangeEnd!(val);
                  } else if (settingKey != null) {
                    store.flushDouble(settingKey!, val);
                  }
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Navigation Tile pointing to subpage using GlassPageRoute
class GlassNavTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final Widget? leading;
  final String? enabledKey;
  final bool enabled;
  final VoidCallback? onTap;
  final WidgetBuilder? destination;

  const GlassNavTile({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    this.enabledKey,
    this.enabled = true,
    this.onTap,
    this.destination,
  });

  @override
  Widget build(BuildContext context) {
    if (enabledKey != null) {
      return SettingListener<bool>(
        settingKey: enabledKey!,
        getter: (store) => store.getBool(enabledKey!, true),
        builder: (ctx, isParentEnabled) =>
            _buildTile(context, isParentEnabled && enabled),
      );
    }
    return _buildTile(context, enabled);
  }

  Widget _buildTile(BuildContext context, bool isEffectiveEnabled) {
    final handleTap = () {
      if (destination != null) {
        Navigator.of(context).push(
          GlassPageRoute(builder: destination!),
        );
      } else if (onTap != null) {
        onTap!();
      }
    };

    return Opacity(
      opacity: isEffectiveEnabled ? 1.0 : 0.45,
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
        onTap: isEffectiveEnabled ? handleTap : null,
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
  final String? enabledKey;
  final bool enabled;
  final VoidCallback onTap;

  const GlassActionTile({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    this.titleColor,
    this.enabledKey,
    this.enabled = true,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    if (enabledKey != null) {
      return SettingListener<bool>(
        settingKey: enabledKey!,
        getter: (store) => store.getBool(enabledKey!, true),
        builder: (ctx, isParentEnabled) =>
            _buildTile(context, isParentEnabled && enabled),
      );
    }
    return _buildTile(context, enabled);
  }

  Widget _buildTile(BuildContext context, bool isEffectiveEnabled) {
    return Opacity(
      opacity: isEffectiveEnabled ? 1.0 : 0.45,
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
        onTap: isEffectiveEnabled ? onTap : null,
      ),
    );
  }
}

/// Dropdown / Option Selector Tile using Liquid Glass Action Sheet
class GlassDropdownTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final List<String> options;
  final List<dynamic>? optionValues;
  final int? selectedIndex;
  final String? settingKey;
  final String? enabledKey;
  final bool enabled;
  final ValueChanged<int>? onSelect;

  const GlassDropdownTile({
    super.key,
    required this.title,
    this.subtitle,
    required this.options,
    this.optionValues,
    this.selectedIndex,
    this.settingKey,
    this.enabledKey,
    this.enabled = true,
    this.onSelect,
  });

  @override
  Widget build(BuildContext context) {
    if (enabledKey != null) {
      return SettingListener<bool>(
        settingKey: enabledKey!,
        getter: (store) => store.getBool(enabledKey!, true),
        builder: (ctx, isParentEnabled) =>
            _buildWithEnabled(context, isParentEnabled && enabled),
      );
    }
    return _buildWithEnabled(context, enabled);
  }

  Widget _buildWithEnabled(BuildContext context, bool isEffectiveEnabled) {
    if (settingKey != null && selectedIndex == null) {
      final store = SettingsStore.instance;
      return SettingListener<dynamic>(
        settingKey: settingKey!,
        getter: (s) {
          if (optionValues != null) {
            final raw = s.getString(settingKey!);
            final idx = optionValues!.indexOf(raw);
            return idx >= 0 ? idx : 0;
          }
          final val = s.getString(settingKey!);
          final idx = options.indexOf(val);
          if (idx >= 0) return idx;
          return s.getInt(settingKey!, 0);
        },
        builder: (ctx, selected) {
          final idx = (selected is int) ? selected : 0;
          return _buildTile(
            context,
            idx.clamp(0, options.length - 1),
            isEffectiveEnabled,
            (newIdx) {
              if (onSelect != null) {
                onSelect!(newIdx);
              } else if (optionValues != null) {
                store.setString(settingKey!, optionValues![newIdx].toString());
              } else {
                final isInt = store.schema[settingKey]?['type'] == 'int';
                if (isInt) {
                  store.setInt(settingKey!, newIdx);
                } else {
                  store.setString(settingKey!, options[newIdx]);
                }
              }
            },
          );
        },
      );
    }
    return _buildTile(
      context,
      selectedIndex ?? 0,
      isEffectiveEnabled,
      onSelect ?? (_) {},
    );
  }

  Widget _buildTile(
    BuildContext context,
    int activeIndex,
    bool isEffectiveEnabled,
    ValueChanged<int> handleSelect,
  ) {
    final selectedOption = options.isNotEmpty && activeIndex < options.length
        ? options[activeIndex]
        : '';

    return Opacity(
      opacity: isEffectiveEnabled ? 1.0 : 0.45,
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
        onTap: isEffectiveEnabled
            ? () {
                showGlassActionSheet<int>(
                  context: context,
                  title: title,
                  message: subtitle,
                  actions: options.asMap().entries.map((e) {
                    final isSelected = e.key == activeIndex;
                    return GlassActionSheetAction(
                      label: e.value,
                      icon: isSelected
                          ? const Icon(CupertinoIcons.checkmark, size: 18)
                          : null,
                      onPressed: () {
                        Navigator.of(context).pop();
                        handleSelect(e.key);
                      },
                    );
                  }).toList(),
                );
              }
            : null,
      ),
    );
  }
}
