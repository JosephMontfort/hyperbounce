import re

with open("/data/data/com.termux/files/home/scratch/clean_helpers.java", "r") as f:
    content = f.read()

# Stop exactly where ThreadLocals start
idx = content.find("private static final ThreadLocal")
if idx != -1:
    content = content[:idx]

with open("/data/data/com.termux/files/home/scratch/clean_helpers.java", "w") as f:
    f.write(content)
