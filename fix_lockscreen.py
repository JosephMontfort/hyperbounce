with open("app/src/main/java/dev/joseph/hyperbounce/systemui/LockscreenHook.java", "r") as f:
    lines = f.readlines()

new_lines = []
for i, line in enumerate(lines):
    if "param.getClassLoader()" in line:
        line = line.replace("param.getClassLoader()", "classLoader")
    
    # Strip rogue brace at 412: it is right after `View rootView = (View) rootViewField.get(kpvc);`
    if line.strip() == "}" and "View rootView" in lines[i-2]:
        continue
        
    # Strip rogue brace at 904: it is right after `View pinView = (View) pinViewObj;`
    if line.strip() == "}" and "View pinView = (View) pinViewObj;" in lines[i-1]:
        continue
        
    new_lines.append(line)

with open("app/src/main/java/dev/joseph/hyperbounce/systemui/LockscreenHook.java", "w") as f:
    f.writelines(new_lines)

