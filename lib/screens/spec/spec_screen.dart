import 'package:flutter/cupertino.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart';
import '../../state/settings_store.dart';
import '../../widgets/app_scaffold.dart';
import '../../widgets/setting_tiles.dart';
import 'screen_spec.dart';

class SpecScreen extends StatelessWidget {
  final ScreenSpec spec;

  const SpecScreen({super.key, required this.spec});

  @override
  Widget build(BuildContext context) {
    return AppScaffold(
      title: spec.title,
      body: ListView(
        padding: const EdgeInsets.only(top: 12, bottom: 80),
        children: [
          for (final section in spec.sections) ...[
            if (section.header != null) GlassSectionHeader(section.header!),
            GlassGroupedCard(
              children: section.items.map((item) => _buildItem(context, item)).toList(),
            ),
            if (section.footer != null) GlassSectionFooter(section.footer!),
          ],
        ],
      ),
    );
  }

  Widget _buildItem(BuildContext context, ItemSpec item) {
    if (item.enabledKey != null) {
      return SettingListener<bool>(
        settingKey: item.enabledKey!,
        getter: (s) => s.getBool(item.enabledKey!, true),
        builder: (ctx, parentEnabled) =>
            _buildItemInternal(context, item, parentEnabled),
      );
    }
    return _buildItemInternal(context, item, true);
  }

  Widget _buildItemInternal(
      BuildContext context, ItemSpec item, bool parentEnabled) {
    final isEnabled = parentEnabled && (item.enabledWhen?.call() ?? true);

    switch (item.type) {
      case ItemSpecType.toggle:
        return GlassSwitchTile(
          title: item.title,
          subtitle: item.subtitle,
          leading: item.leading,
          settingKey: item.key,
          enabled: isEnabled,
        );

      case ItemSpecType.slider:
        return GlassSliderTile(
          title: item.title,
          subtitle: item.subtitle,
          leading: item.leading,
          settingKey: item.key,
          min: item.min,
          max: item.max,
          divisions: item.divisions,
          unit: item.unit,
          enabled: isEnabled,
        );

      case ItemSpecType.dropdown:
        return GlassDropdownTile(
          title: item.title,
          subtitle: item.subtitle,
          options: item.options,
          optionValues: item.optionValues,
          settingKey: item.key,
          enabled: isEnabled,
        );

      case ItemSpecType.nav:
        return GlassNavTile(
          title: item.title,
          subtitle: item.subtitle,
          leading: item.leading,
          enabled: isEnabled,
          onTap: () {
            if (item.destinationSpec != null) {
              Navigator.of(context).push(
                GlassPageRoute(
                  builder: (_) => SpecScreen(spec: item.destinationSpec!),
                ),
              );
            } else if (item.destinationBuilder != null) {
              Navigator.of(context).push(
                GlassPageRoute(builder: item.destinationBuilder!),
              );
            }
          },
        );

      case ItemSpecType.action:
        return GlassActionTile(
          title: item.title,
          subtitle: item.subtitle,
          leading: item.leading,
          titleColor: item.titleColor,
          enabled: isEnabled,
          onTap: item.onTap ?? () {},
        );
    }
  }
}
