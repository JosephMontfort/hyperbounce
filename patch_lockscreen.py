with open("app/src/main/java/dev/joseph/hyperbounce/systemui/LockscreenHook.java", "r") as f:
    lines = f.readlines()

with open("/data/data/com.termux/files/home/scratch/clean_helpers.java", "r") as f:
    helpers = f.read()

output = []
for line in lines:
    if line.strip() == "private static boolean receiverRegistered = false;":
        output.append(helpers + "\n")
    if not "receiverRegistered" in line and not "syncMemory" in line:
        output.append(line)

with open("app/src/main/java/dev/joseph/hyperbounce/systemui/LockscreenHook.java", "w") as f:
    f.writelines(output)
