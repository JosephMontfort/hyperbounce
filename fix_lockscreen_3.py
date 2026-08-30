with open("app/src/main/java/dev/joseph/hyperbounce/systemui/LockscreenHook.java", "r") as f:
    content = f.read()

# Replace Config with DiagnosticConfig
content = content.replace("private static final Config config = new Config();", "private static DiagnosticConfig config;")

with open("app/src/main/java/dev/joseph/hyperbounce/systemui/LockscreenHook.java", "w") as f:
    f.write(content)
