with open("app/src/main/java/dev/joseph/hyperbounce/systemui/LockscreenHook.java", "r") as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    if line.strip() == "}" and "View view = (View) chain.getThisObject();" in lines[i-1]:
        continue
    new_lines.append(line)

with open("app/src/main/java/dev/joseph/hyperbounce/systemui/LockscreenHook.java", "w") as f:
    f.writelines(new_lines)
