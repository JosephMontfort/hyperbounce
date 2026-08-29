import re

with open("app/src/main/java/dev/joseph/hyperbounce/ui/state/SettingsRepository.kt", "r") as f:
    content = f.read()

# First remove my botched 'private val _diagnostics' code block from before if it exists
if "// Diagnostics flow" in content:
    content = re.sub(r'    // Diagnostics flow.*?    private fun loadSettings\(\): AppSettings \{', '    private fun loadSettings(): AppSettings {', content, flags=re.DOTALL)
    
if "var ccLowEndAnim" not in content:
    diag_props = """
    // Diagnostics Primitive Delegates
    var ccLowEndAnim by prefsBooleanState("diag_ccLowEndAnim", false); private set
    @JvmName("setCcLowEndAnim") fun setCcLowEndAnim(v: Boolean) { ccLowEndAnim = v }

    var ccAnimEnabled by prefsBooleanState("diag_ccAnimEnabled", true); private set
    @JvmName("setCcAnimEnabled") fun setCcAnimEnabled(v: Boolean) { ccAnimEnabled = v }

    var ccModularEditEnabled by prefsBooleanState("diag_ccModularEditEnabled", true); private set
    @JvmName("setCcModularEditEnabled") fun setCcModularEditEnabled(v: Boolean) { ccModularEditEnabled = v }

    var ccJellyEnabled by prefsBooleanState("diag_ccJellyEnabled", false); private set
    @JvmName("setCcJellyEnabled") fun setCcJellyEnabled(v: Boolean) { ccJellyEnabled = v }

    var lockCardBorderWidth by prefsFloatState("diag_lockCardBorderWidth", 1.0f); private set
    @JvmName("setLockCardBorderWidth") fun setLockCardBorderWidth(v: Float) { lockCardBorderWidth = v }

    var lockCardBorderAlpha by prefsFloatState("diag_lockCardBorderAlpha", 0.1f); private set
    @JvmName("setLockCardBorderAlpha") fun setLockCardBorderAlpha(v: Float) { lockCardBorderAlpha = v }

    var lockCardRefractionHeight by prefsFloatState("diag_lockCardRefractionHeight", 10.0f); private set
    @JvmName("setLockCardRefractionHeight") fun setLockCardRefractionHeight(v: Float) { lockCardRefractionHeight = v }

    var lockCardRefractionAmount by prefsFloatState("diag_lockCardRefractionAmount", 16.0f); private set
    @JvmName("setLockCardRefractionAmount") fun setLockCardRefractionAmount(v: Float) { lockCardRefractionAmount = v }

    var lockCardDepthEffect by prefsFloatState("diag_lockCardDepthEffect", 0.5f); private set
    @JvmName("setLockCardDepthEffect") fun setLockCardDepthEffect(v: Float) { lockCardDepthEffect = v }

    var lockPillWidth by prefsFloatState("diag_lockPillWidth", 40.0f); private set
    @JvmName("setLockPillWidth") fun setLockPillWidth(v: Float) { lockPillWidth = v }

    var lockPillHeight by prefsFloatState("diag_lockPillHeight", 2.0f); private set
    @JvmName("setLockPillHeight") fun setLockPillHeight(v: Float) { lockPillHeight = v }

    var lockPillBottomMargin by prefsFloatState("diag_lockPillBottomMargin", 9.0f); private set
    @JvmName("setLockPillBottomMargin") fun setLockPillBottomMargin(v: Float) { lockPillBottomMargin = v }

    var lockLipHeight by prefsFloatState("diag_lockLipHeight", 10.0f); private set
    @JvmName("setLockLipHeight") fun setLockLipHeight(v: Float) { lockLipHeight = v }

    var pinDigitTextSize by prefsFloatState("diag_pinDigitTextSize", 28.0f); private set
    @JvmName("setPinDigitTextSize") fun setPinDigitTextSize(v: Float) { pinDigitTextSize = v }

    var pinRow1Offset by prefsFloatState("diag_pinRow1Offset", 0.0f); private set
    @JvmName("setPinRow1Offset") fun setPinRow1Offset(v: Float) { pinRow1Offset = v }

    var pinRow2Offset by prefsFloatState("diag_pinRow2Offset", 0.0f); private set
    @JvmName("setPinRow2Offset") fun setPinRow2Offset(v: Float) { pinRow2Offset = v }

    var pinRow3Offset by prefsFloatState("diag_pinRow3Offset", 0.0f); private set
    @JvmName("setPinRow3Offset") fun setPinRow3Offset(v: Float) { pinRow3Offset = v }

    var pinRow0Offset by prefsFloatState("diag_pinRow0Offset", 0.0f); private set
    @JvmName("setPinRow0Offset") fun setPinRow0Offset(v: Float) { pinRow0Offset = v }

    var pinTapSquishScale by prefsFloatState("diag_pinTapSquishScale", 0.85f); private set
    @JvmName("setPinTapSquishScale") fun setPinTapSquishScale(v: Float) { pinTapSquishScale = v }

    var pinReleasePlunge by prefsFloatState("diag_pinReleasePlunge", 0.95f); private set
    @JvmName("setPinReleasePlunge") fun setPinReleasePlunge(v: Float) { pinReleasePlunge = v }

    var pinShowKlondike by prefsBooleanState("diag_pinShowKlondike", true); private set
    @JvmName("setPinShowKlondike") fun setPinShowKlondike(v: Boolean) { pinShowKlondike = v }

    var pinEntryTranslationY by prefsFloatState("diag_pinEntryTranslationY", 100.0f); private set
    @JvmName("setPinEntryTranslationY") fun setPinEntryTranslationY(v: Float) { pinEntryTranslationY = v }

    var pinEntryInitialScale by prefsFloatState("diag_pinEntryInitialScale", 0.8f); private set
    @JvmName("setPinEntryInitialScale") fun setPinEntryInitialScale(v: Float) { pinEntryInitialScale = v }

    var pinEntryDuration by prefsFloatState("diag_pinEntryDuration", 500.0f); private set
    @JvmName("setPinEntryDuration") fun setPinEntryDuration(v: Float) { pinEntryDuration = v }

    var pinRowDelayStep by prefsFloatState("diag_pinRowDelayStep", 50.0f); private set
    @JvmName("setPinRowDelayStep") fun setPinRowDelayStep(v: Float) { pinRowDelayStep = v }

    var pinColDelayStep by prefsFloatState("diag_pinColDelayStep", 20.0f); private set
    @JvmName("setPinColDelayStep") fun setPinColDelayStep(v: Float) { pinColDelayStep = v }

    var pinEntryOvershoot by prefsFloatState("diag_pinEntryOvershoot", 1.1f); private set
    @JvmName("setPinEntryOvershoot") fun setPinEntryOvershoot(v: Float) { pinEntryOvershoot = v }
"""
    content = content.replace("    // --- per-app nav bar", diag_props + "\n    // --- per-app nav bar")

with open("app/src/main/java/dev/joseph/hyperbounce/ui/state/SettingsRepository.kt", "w") as f:
    f.write(content)

