import re

with open("app/src/main/java/dev/joseph/hyperbounce/ui/state/SettingsRepository.kt", "r") as f:
    content = f.read()

# Replace all @JvmName("set...") with @JvmName("set...Pref")
content = re.sub(r'@JvmName\("set([A-Za-z0-9_]+)"\)', r'@JvmName("set\1Pref")', content)

with open("app/src/main/java/dev/joseph/hyperbounce/ui/state/SettingsRepository.kt", "w") as f:
    f.write(content)
