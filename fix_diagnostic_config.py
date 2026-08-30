with open("app/src/main/java/dev/joseph/hyperbounce/systemui/DiagnosticConfig.java", "r") as f:
    content = f.read()

fields_to_add = """
    public float pinCircleDiameter = 82.0f;
    public float pinCircleFillAlpha = 0.20f;
    public float pinCircleStrokeWidth = 1.5f;
    public float pinCircleStrokeAlpha = 0.47f;
    public float pinJellyMaxStretch = 0.16f;
    public float pinJellyElasticLimit = 0.40f;
    public float pinSpringStiffness = 50.0f;
    public float pinSpringDamping = 5.5f;
    public float pinPressGlowAlpha = 0.35f;
    public float pinGlowRadius = 1.4f;
"""

load_to_add = """
        pinCircleDiameter = prefs.getFloat("diag_pinCircleDiameter", pinCircleDiameter);
        pinCircleFillAlpha = prefs.getFloat("diag_pinCircleFillAlpha", pinCircleFillAlpha);
        pinCircleStrokeWidth = prefs.getFloat("diag_pinCircleStrokeWidth", pinCircleStrokeWidth);
        pinCircleStrokeAlpha = prefs.getFloat("diag_pinCircleStrokeAlpha", pinCircleStrokeAlpha);
        pinJellyMaxStretch = prefs.getFloat("diag_pinJellyMaxStretch", pinJellyMaxStretch);
        pinJellyElasticLimit = prefs.getFloat("diag_pinJellyElasticLimit", pinJellyElasticLimit);
        pinSpringStiffness = prefs.getFloat("diag_pinSpringStiffness", pinSpringStiffness);
        pinSpringDamping = prefs.getFloat("diag_pinSpringDamping", pinSpringDamping);
        pinPressGlowAlpha = prefs.getFloat("diag_pinPressGlowAlpha", pinPressGlowAlpha);
        pinGlowRadius = prefs.getFloat("diag_pinGlowRadius", pinGlowRadius);
"""

# Insert fields
lines = content.split('\n')
for i, line in enumerate(lines):
    if line.strip() == "public boolean unlockEnabled = true;":
        lines.insert(i, fields_to_add)
        break

content = '\n'.join(lines)
lines = content.split('\n')
for i, line in enumerate(lines):
    if line.strip() == "unlockEnabled = prefs.getBoolean(\"diag_unlockEnabled\", unlockEnabled);":
        lines.insert(i, load_to_add)
        break

with open("app/src/main/java/dev/joseph/hyperbounce/systemui/DiagnosticConfig.java", "w") as f:
    f.write('\n'.join(lines))
