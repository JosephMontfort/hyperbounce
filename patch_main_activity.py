import re

with open("app/src/main/java/dev/joseph/hyperbounce/ui/MainActivity.kt", "r") as f:
    content = f.read()

if "Destination.Diagnostics" not in content:
    content = content.replace(
        "is Destination.Keypad ->",
        """is Destination.Diagnostics -> dev.joseph.hyperbounce.ui.screens.DiagnosticsScreen(
                                repository = repository,
                                colors = colors,
                                backdrop = backdrop,
                                onBack = { navController.popBackStack() }
                            )
                            is Destination.Keypad ->"""
    )
    with open("app/src/main/java/dev/joseph/hyperbounce/ui/MainActivity.kt", "w") as f:
        f.write(content)

