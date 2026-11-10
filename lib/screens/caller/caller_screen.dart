import 'package:flutter/cupertino.dart';
import '../spec/all_specs.dart';
import '../spec/spec_screen.dart';

class CallerScreen extends StatelessWidget {
  const CallerScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return SpecScreen(spec: callerScreenSettingsSpec);
  }
}
