package dev.joseph.hyperbounce;

import org.json.JSONObject;

public class Config {
    public int animMode = 1; // 0 = Radial Classic, 1 = Kinetic Y-Axis Cascade
    
    // Mode 0: Radial Dispersion (Original Classic)
    public float dockDamping = 0.70f;
    public float dockResponse = 0.40f;
    public float centerResponse = 0.48f;
    public float centerDamping = 0.58f;
    public float edgeResponseDelay = 0.22f;
    public float edgeDampingDrop = 0.10f;
    public float minResponse = 0.45f;
    public float maxResponse = 0.75f;
    public float minDamping = 0.42f;
    public float maxDamping = 0.65f;
    public float zDepthMultiplier = 1.6f;

    // Mode 1: Kinetic Y-Axis Cascade
    public float cascadeDamping = 0.92f;
    public float cascadeBaseResponse = 0.50f;
    public float cascadeRowStagger = 0.035f;
    public float maxVelocity = 16000.0f;

    public void loadFromJson(String jsonStr) {
        try {
            JSONObject json = new JSONObject(jsonStr);
            animMode = json.optInt("animMode", animMode);
            dockDamping = (float) json.optDouble("dockDamping", dockDamping);
            dockResponse = (float) json.optDouble("dockResponse", dockResponse);
            centerResponse = (float) json.optDouble("centerResponse", centerResponse);
            centerDamping = (float) json.optDouble("centerDamping", centerDamping);
            edgeResponseDelay = (float) json.optDouble("edgeResponseDelay", edgeResponseDelay);
            edgeDampingDrop = (float) json.optDouble("edgeDampingDrop", edgeDampingDrop);
            minResponse = (float) json.optDouble("minResponse", minResponse);
            maxResponse = (float) json.optDouble("maxResponse", maxResponse);
            minDamping = (float) json.optDouble("minDamping", minDamping);
            maxDamping = (float) json.optDouble("maxDamping", maxDamping);
            zDepthMultiplier = (float) json.optDouble("zDepthMultiplier", zDepthMultiplier);

            cascadeDamping = (float) json.optDouble("cascadeDamping", cascadeDamping);
            cascadeBaseResponse = (float) json.optDouble("cascadeBaseResponse", cascadeBaseResponse);
            cascadeRowStagger = (float) json.optDouble("cascadeRowStagger", cascadeRowStagger);
            maxVelocity = (float) json.optDouble("maxVelocity", maxVelocity);
        } catch (Exception e) {}
    }
}
