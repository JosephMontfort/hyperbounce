import re

with open("/data/data/com.termux/files/home/scratch/helpers.java", "r") as f:
    content = f.read()

# Remove 'public class PhysicsHook extends XposedModule {' and trailing '}'
content = re.sub(r'public class PhysicsHook extends XposedModule \{', '', content)
content = re.sub(r'\s*@Override\s*$', '', content)
content = re.sub(r'public static synchronized void syncMemory[\s\S]*$', '', content)

# Write out to final string
with open("/data/data/com.termux/files/home/scratch/clean_helpers.java", "w") as f:
    f.write(content)
