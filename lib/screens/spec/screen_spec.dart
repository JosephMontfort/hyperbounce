import 'package:flutter/cupertino.dart';

enum ItemSpecType {
  toggle,
  slider,
  dropdown,
  nav,
  action,
}

class ItemSpec {
  final ItemSpecType type;
  final String title;
  final String? subtitle;
  final String? key;
  final String? enabledKey;
  final Widget? leading;

  // Slider specifics
  final double min;
  final double max;
  final int? divisions;
  final String? unit;

  // Dropdown specifics
  final List<String> options;
  final List<dynamic>? optionValues; // Enum strings or values corresponding to options

  // Navigation specifics
  final WidgetBuilder? destinationBuilder;
  final ScreenSpec? destinationSpec;

  // Action specifics
  final VoidCallback? onTap;
  final Color? titleColor;

  // Conditionals
  final bool Function()? enabledWhen;

  const ItemSpec.toggle({
    required this.title,
    this.subtitle,
    required this.key,
    this.enabledKey,
    this.leading,
    this.enabledWhen,
  })  : type = ItemSpecType.toggle,
        min = 0,
        max = 1,
        divisions = null,
        unit = null,
        options = const [],
        optionValues = null,
        destinationBuilder = null,
        destinationSpec = null,
        onTap = null,
        titleColor = null;

  const ItemSpec.slider({
    required this.title,
    this.subtitle,
    required this.key,
    required this.min,
    required this.max,
    this.divisions,
    this.unit,
    this.enabledKey,
    this.leading,
    this.enabledWhen,
  })  : type = ItemSpecType.slider,
        options = const [],
        optionValues = null,
        destinationBuilder = null,
        destinationSpec = null,
        onTap = null,
        titleColor = null;

  const ItemSpec.dropdown({
    required this.title,
    this.subtitle,
    required this.key,
    required this.options,
    this.optionValues,
    this.enabledKey,
    this.leading,
    this.enabledWhen,
  })  : type = ItemSpecType.dropdown,
        min = 0,
        max = 1,
        divisions = null,
        unit = null,
        destinationBuilder = null,
        destinationSpec = null,
        onTap = null,
        titleColor = null;

  const ItemSpec.nav({
    required this.title,
    this.subtitle,
    this.enabledKey,
    this.leading,
    this.destinationBuilder,
    this.destinationSpec,
    this.enabledWhen,
  })  : type = ItemSpecType.nav,
        key = null,
        min = 0,
        max = 1,
        divisions = null,
        unit = null,
        options = const [],
        optionValues = null,
        onTap = null,
        titleColor = null;

  const ItemSpec.action({
    required this.title,
    this.subtitle,
    this.enabledKey,
    this.leading,
    required this.onTap,
    this.titleColor,
    this.enabledWhen,
  })  : type = ItemSpecType.action,
        key = null,
        min = 0,
        max = 1,
        divisions = null,
        unit = null,
        options = const [],
        optionValues = null,
        destinationBuilder = null,
        destinationSpec = null;
}

class SectionSpec {
  final String? header;
  final String? footer;
  final List<ItemSpec> items;

  const SectionSpec({
    this.header,
    this.footer,
    required this.items,
  });
}

class ScreenSpec {
  final String id;
  final String title;
  final String? subtitle;
  final List<SectionSpec> sections;

  const ScreenSpec({
    required this.id,
    required this.title,
    this.subtitle,
    required this.sections,
  });
}
