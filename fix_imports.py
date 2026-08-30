with open("app/src/main/java/dev/joseph/hyperbounce/systemui/LockscreenHook.java", "r") as f:
    content = f.read()

imports_to_add = """
import android.os.Build;
import android.view.ViewGroup;
import android.widget.FrameLayout;
"""

# Insert right after the package declaration
lines = content.split('\n')
for i, line in enumerate(lines):
    if line.startswith("package "):
        lines.insert(i+1, imports_to_add)
        break

with open("app/src/main/java/dev/joseph/hyperbounce/systemui/LockscreenHook.java", "w") as f:
    f.write('\n'.join(lines))
