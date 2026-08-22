package dev.joseph.hyperbounce;

public class Config {
    public float animationDuration = 1.0f;
    public float gridDeadzoneX = 0.125f;
    public float dockDamping = 0.70f;
    public float dockResponse = 0.40f;
    public float centerResponse = 0.48f;
    public float centerDamping = 0.52f;
    public float edgeResponseDelay = 0.22f;
    public float edgeDampingDrop = 0.10f;
    public float minResponse = 0.45f;
    public float maxResponse = 0.75f;
    public float minDamping = 0.40f;
    public float maxDamping = 0.55f;
    public float zDepthMultiplier = 1.6f;
    
    // Dynamic Code Engine
    public boolean useCustomScript = false;
    public String customScript = 
        "// Available Variables:\n" +
        "// isDockView (boolean), dist (float 0.0 to 1.0)\n" +
        "// vx, vy (absolute icon coordinates)\n" +
        "// screenW, screenH (screen dimensions)\n\n" +
        "function getPhysics() {\n" +
        "    if (isDockView) {\n" +
        "        return [0.70, 0.40];\n" +
        "    }\n" +
        "    var damping = 0.52 - (dist * 0.10);\n" +
        "    var response = 0.48 + (dist * 0.22);\n" +
        "    return [damping, response];\n" +
        "}\n" +
        "getPhysics();";
}
