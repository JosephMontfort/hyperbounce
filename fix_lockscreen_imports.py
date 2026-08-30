with open("app/src/main/java/dev/joseph/hyperbounce/systemui/LockscreenHook.java", "r") as f:
    content = f.read()

imports = """
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;
import android.os.Build;
import android.widget.FrameLayout;
import android.graphics.RuntimeShader;
import android.graphics.RenderEffect;
import android.graphics.RectF;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
"""

# Remove old imports
new_lines = []
for line in content.split('\n'):
    if not line.startswith("import "):
        new_lines.append(line)

# Insert new imports after package
for i, line in enumerate(new_lines):
    if line.startswith("package "):
        new_lines.insert(i+1, imports)
        break

with open("app/src/main/java/dev/joseph/hyperbounce/systemui/LockscreenHook.java", "w") as f:
    f.write('\n'.join(new_lines))
