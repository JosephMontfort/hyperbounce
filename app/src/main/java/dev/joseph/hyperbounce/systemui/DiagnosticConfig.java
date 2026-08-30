package dev.joseph.hyperbounce.systemui;

import android.content.SharedPreferences;

public class DiagnosticConfig {
    // Control Center
    public boolean ccLowEndAnim = false;
    public boolean ccAnimEnabled = true;
    public boolean ccModularEditEnabled = true;
    public boolean ccJellyEnabled = false;
    public float ccTileDelayScale = 0.5f;

    // Lockscreen Card
    public float lockCardBorderWidth = 1.0f;
    public float lockCardBorderAlpha = 0.1f;
    public float lockCardRefractionHeight = 10.0f;
    public float lockCardRefractionAmount = 16.0f;
    public float lockCardDepthEffect = 0.5f;
    public float lockPillWidth = 40.0f;
    public float lockPillHeight = 2.0f;
    public float lockPillBottomMargin = 9.0f;
    public float lockLipHeight = 10.0f;

    // Pinpad
    public float pinDigitTextSize = 28.0f;
    public float pinRow1Offset = 0.0f;
    public float pinRow2Offset = 0.0f;
    public float pinRow3Offset = 0.0f;
    public float pinRow0Offset = 0.0f;
    public float pinTapSquishScale = 0.85f;
    public float pinReleasePlunge = 0.95f;
    public boolean pinShowKlondike = true;
    public float pinEntryTranslationY = 100.0f;
    public float pinEntryInitialScale = 0.8f;
    public float pinEntryDuration = 500.0f;
    public float pinRowDelayStep = 50.0f;
    public float pinColDelayStep = 20.0f;
    public float pinEntryOvershoot = 1.1f;


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

    public boolean unlockEnabled = true;

    public void load(SharedPreferences prefs) {
        ccLowEndAnim = prefs.getBoolean("diag_ccLowEndAnim", ccLowEndAnim);
        ccAnimEnabled = prefs.getBoolean("diag_ccAnimEnabled", ccAnimEnabled);
        ccModularEditEnabled = prefs.getBoolean("diag_ccModularEditEnabled", ccModularEditEnabled);
        ccJellyEnabled = prefs.getBoolean("diag_ccJellyEnabled", ccJellyEnabled);
        ccTileDelayScale = prefs.getFloat("diag_ccTileDelayScale", ccTileDelayScale);

        lockCardBorderWidth = prefs.getFloat("diag_lockCardBorderWidth", lockCardBorderWidth);
        lockCardBorderAlpha = prefs.getFloat("diag_lockCardBorderAlpha", lockCardBorderAlpha);
        lockCardRefractionHeight = prefs.getFloat("diag_lockCardRefractionHeight", lockCardRefractionHeight);
        lockCardRefractionAmount = prefs.getFloat("diag_lockCardRefractionAmount", lockCardRefractionAmount);
        lockCardDepthEffect = prefs.getFloat("diag_lockCardDepthEffect", lockCardDepthEffect);
        lockPillWidth = prefs.getFloat("diag_lockPillWidth", lockPillWidth);
        lockPillHeight = prefs.getFloat("diag_lockPillHeight", lockPillHeight);
        lockPillBottomMargin = prefs.getFloat("diag_lockPillBottomMargin", lockPillBottomMargin);
        lockLipHeight = prefs.getFloat("diag_lockLipHeight", lockLipHeight);

        pinDigitTextSize = prefs.getFloat("diag_pinDigitTextSize", pinDigitTextSize);
        pinRow1Offset = prefs.getFloat("diag_pinRow1Offset", pinRow1Offset);
        pinRow2Offset = prefs.getFloat("diag_pinRow2Offset", pinRow2Offset);
        pinRow3Offset = prefs.getFloat("diag_pinRow3Offset", pinRow3Offset);
        pinRow0Offset = prefs.getFloat("diag_pinRow0Offset", pinRow0Offset);
        pinTapSquishScale = prefs.getFloat("diag_pinTapSquishScale", pinTapSquishScale);
        pinReleasePlunge = prefs.getFloat("diag_pinReleasePlunge", pinReleasePlunge);
        pinShowKlondike = prefs.getBoolean("diag_pinShowKlondike", pinShowKlondike);
        pinEntryTranslationY = prefs.getFloat("diag_pinEntryTranslationY", pinEntryTranslationY);
        pinEntryInitialScale = prefs.getFloat("diag_pinEntryInitialScale", pinEntryInitialScale);
        pinEntryDuration = prefs.getFloat("diag_pinEntryDuration", pinEntryDuration);
        pinRowDelayStep = prefs.getFloat("diag_pinRowDelayStep", pinRowDelayStep);
        pinColDelayStep = prefs.getFloat("diag_pinColDelayStep", pinColDelayStep);
        pinEntryOvershoot = prefs.getFloat("diag_pinEntryOvershoot", pinEntryOvershoot);
        

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

        unlockEnabled = prefs.getBoolean("diag_unlockEnabled", unlockEnabled);
    }
}
