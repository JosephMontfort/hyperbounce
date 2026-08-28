package dev.joseph.hyperbounce;
import android.view.Menu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private JSONObject currentConfig = new JSONObject();

    private LinearLayout rootLayout;
    private LinearLayout menuLayout;
    private LinearLayout unlockPageLayout;
    private LinearLayout appAnimPageLayout;
    private LinearLayout lockPinpadPageLayout;
    private LinearLayout controlCenterPageLayout;

    private LinearLayout unlockSlidersContainer;
    private LinearLayout appAnimSlidersContainer;
    private LinearLayout lockPinpadSlidersContainer;

    private EditText gridInput;
    private Spinner unlockModeSpinner;
    private Switch unlockMasterSwitch;
    private Switch appAnimMasterSwitch;
    private Switch lockPinpadMasterSwitch;
    private Switch ccCenterBounceSwitch;
    private Switch ccLowEndSwitch;
    private Switch ccJellySwitch;
    private Switch ccModularEditSwitch;
    private Switch ccRestartOnEditExitSwitch;

    private enum Page { MAIN, UNLOCK, APP_ANIM, LOCK_PINPAD, CONTROL_CENTER }
    private Page currentPage = Page.MAIN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        initializeOptimizedPreset();
        loadConfig();
        normalizeGrid();

        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setPadding(40, 40, 40, 40);

        setupHeader(mainContainer);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(0, 30, 0, 40);
        scroll.addView(rootLayout);
        mainContainer.addView(scroll);
        setContentView(mainContainer);

        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);

        unlockPageLayout = new LinearLayout(this);
        unlockPageLayout.setOrientation(LinearLayout.VERTICAL);
        unlockPageLayout.setVisibility(View.GONE);

        appAnimPageLayout = new LinearLayout(this);
        appAnimPageLayout.setOrientation(LinearLayout.VERTICAL);
        appAnimPageLayout.setVisibility(View.GONE);

        lockPinpadPageLayout = new LinearLayout(this);
        lockPinpadPageLayout.setOrientation(LinearLayout.VERTICAL);
        lockPinpadPageLayout.setVisibility(View.GONE);

        controlCenterPageLayout = new LinearLayout(this);
        controlCenterPageLayout.setOrientation(LinearLayout.VERTICAL);
        controlCenterPageLayout.setVisibility(View.GONE);

        rootLayout.addView(menuLayout);
        rootLayout.addView(controlCenterPageLayout);
        rootLayout.addView(lockPinpadPageLayout);
        rootLayout.addView(unlockPageLayout);
        rootLayout.addView(appAnimPageLayout);

        setupMainMenu();
        setupControlCenterPage();
        setupLockPinpadPage();
        setupUnlockPage();
        setupAppAnimPage();
        setupBackHandling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkSafeModeStatus();
    }

    private void checkSafeModeStatus() {
        try {
            File f = new File(getFilesDir(), "safemode.flag");
            if (f.exists()) {
                new AlertDialog.Builder(this)
                    .setTitle("⚠️ Safe Mode Active")
                    .setMessage("HyperBounce entered Safe Mode due to previous unexpected termination. Custom Control Center physics and modular features are temporarily paused.\n\nWould you like to deactivate Safe Mode and resume custom physics?")
                    .setCancelable(false)
                    .setPositiveButton("Deactivate Safe Mode & Resume", (dialog, which) -> {
                        try {
                            if (f.exists()) f.delete();
                            getContentResolver().call(Uri.parse("content://dev.joseph.hyperbounce.provider"), "setSafeMode", "false", null);
                            Toast.makeText(this, "Safe Mode deactivated! Restart System UI to apply.", Toast.LENGTH_LONG).show();
                        } catch (Exception e) {}
                    })
                    .setNegativeButton("Keep Safe Mode", null)
                    .show();
            }
        } catch (Exception ignored) {}
    }

    private void setupHeader(LinearLayout parentContainer) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, 20);

        TextView appTitle = new TextView(this);
        appTitle.setText("HyperBounce");
        appTitle.setTextSize(28);
        appTitle.setTypeface(null, Typeface.BOLD);
        appTitle.setTextColor(Color.BLACK);
        appTitle.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(appTitle);

        Button menuButton = new Button(this);
        menuButton.setText("\u22ee");
        menuButton.setTextSize(24);
        menuButton.setContentDescription("More options");
        menuButton.setMinWidth(0);
        menuButton.setMinHeight(0);
        menuButton.setPadding(18, 0, 18, 0);
        menuButton.setOnClickListener(v -> showActionMenu(menuButton));
        header.addView(menuButton);

        parentContainer.addView(header);

        TextView subtitle = new TextView(this);
        subtitle.setText("HyperOS animation physics, spring dynamics & transition tuning");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, 0, 0, 30);
        parentContainer.addView(subtitle);
    }

    private void showActionMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        menu.add("Restart Launcher");
        menu.add("Restart System UI");
        menu.add("Reset All Sliders to Stock");
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Restart Launcher".equals(title)) {
                try { Runtime.getRuntime().exec("su -c killall com.miui.home"); Toast.makeText(this, "Launcher Restarted", Toast.LENGTH_SHORT).show(); } catch (Exception e) { Toast.makeText(this, "Unable to restart Launcher", Toast.LENGTH_SHORT).show(); }
            } else if ("Restart System UI".equals(title)) {
                try { Runtime.getRuntime().exec("su -c killall com.android.systemui"); Toast.makeText(this, "System UI Restarted", Toast.LENGTH_SHORT).show(); } catch (Exception e) { Toast.makeText(this, "Unable to restart System UI", Toast.LENGTH_SHORT).show(); }
            } else if ("Reset All Sliders to Stock".equals(title)) {
                resetAllToStock();
            }
            return true;
        });
        popup.show();
    }
    
    private void resetAllToStock() {
        currentConfig = new JSONObject();
        try {
            currentConfig.put("unlockEnabled", false);
            currentConfig.put("appAnimEnabled", false);
            currentConfig.put("ccAnimEnabled", false);
            currentConfig.put("ccLowEndAnim", false);
            currentConfig.put("ccJellyEnabled", false);
            currentConfig.put("ccModularEditEnabled", false);
            saveConfig();
        } catch(Exception e){}
        Toast.makeText(this, "All physics reset to absolute stock. Restart System UI / Launcher to apply.", Toast.LENGTH_LONG).show();
        if (unlockSlidersContainer != null) renderUnlockSliders(currentConfig.optInt("animMode", 0));
        if (appAnimSlidersContainer != null) renderAppAnimSliders();
        if (lockPinpadSlidersContainer != null) renderLockPinpadSliders();
        if (unlockMasterSwitch != null) unlockMasterSwitch.setChecked(false);
        if (appAnimMasterSwitch != null) appAnimMasterSwitch.setChecked(false);
        if (lockPinpadMasterSwitch != null) lockPinpadMasterSwitch.setChecked(false);
        if (ccCenterBounceSwitch != null) ccCenterBounceSwitch.setChecked(false);
        if (ccLowEndSwitch != null) ccLowEndSwitch.setChecked(false);
        if (ccJellySwitch != null) ccJellySwitch.setChecked(false);
        if (ccModularEditSwitch != null) ccModularEditSwitch.setChecked(false);
    }

    private void setupMainMenu() {
        TextView desc = new TextView(this);
        desc.setText("Choose a tuning module. All controls save immediately and apply instantly over Xposed.");
        desc.setTextSize(14);
        desc.setTextColor(Color.DKGRAY);
        desc.setPadding(0, 0, 0, 28);
        menuLayout.addView(desc);

        Button btnCC = new Button(this);
        btnCC.setText("Control & Notification Centre");
        btnCC.setTextSize(16);
        btnCC.setAllCaps(false);
        btnCC.setPadding(0, 34, 0, 34);
        btnCC.setOnClickListener(v -> showPage(Page.CONTROL_CENTER));

        Button btnLockPinpad = new Button(this);
        btnLockPinpad.setText("Lock Screen Sheet & PIN Pad (iOS)");
        btnLockPinpad.setTextSize(16);
        btnLockPinpad.setAllCaps(false);
        btnLockPinpad.setPadding(0, 34, 0, 34);
        btnLockPinpad.setOnClickListener(v -> showPage(Page.LOCK_PINPAD));

        Button btnUnlock = new Button(this);
        btnUnlock.setText("Screen Unlock Animations");
        btnUnlock.setTextSize(16);
        btnUnlock.setAllCaps(false);
        btnUnlock.setPadding(0, 34, 0, 34);
        btnUnlock.setOnClickListener(v -> showPage(Page.UNLOCK));

        Button btnAppAnim = new Button(this);
        btnAppAnim.setText("App Open / Close & Recents");
        btnAppAnim.setTextSize(16);
        btnAppAnim.setAllCaps(false);
        btnAppAnim.setPadding(0, 34, 0, 34);
        btnAppAnim.setOnClickListener(v -> showPage(Page.APP_ANIM));

        menuLayout.addView(btnCC);
        menuLayout.addView(btnLockPinpad);
        menuLayout.addView(btnUnlock);
        menuLayout.addView(btnAppAnim);

        refreshPresetList();
    }

    private void setupControlCenterPage() {
        addPageTitle(controlCenterPageLayout, "Control & Notification Centre");

        TextView desc = new TextView(this);
        desc.setText("Motion dynamics, iOS 26 jelly physics, modular component customizer, and low-end animation engine switches for Xiaomi HyperOS.");
        desc.setTextSize(14);
        desc.setTextColor(Color.DKGRAY);
        desc.setPadding(0, 0, 0, 24);
        controlCenterPageLayout.addView(desc);

        // Switch 1: Modular Component Customization (iOS Edit Engine)
        LinearLayout rowModular = new LinearLayout(this);
        rowModular.setOrientation(LinearLayout.VERTICAL);
        rowModular.setPadding(0, 8, 0, 18);

        ccModularEditSwitch = new Switch(this);
        ccModularEditSwitch.setText("Enable Modular Component Customizer (iOS Edit Mode)");
        ccModularEditSwitch.setTextSize(16);
        ccModularEditSwitch.setTypeface(null, Typeface.BOLD);
        ccModularEditSwitch.setChecked(currentConfig.optBoolean("ccModularEditEnabled", true));
        ccModularEditSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            try { currentConfig.put("ccModularEditEnabled", isChecked); saveConfig(); } catch (Exception ignored) {}
        });
        rowModular.addView(ccModularEditSwitch);

        TextView modularDesc = new TextView(this);
        modularDesc.setText("• Unlocks Wi-Fi, Mobile Data, Media Player, and Sliders in the Edit Page.\n• Full drag-and-drop reordering of all major modules and circular tiles.\n• Fluid dynamic grid reflow and persistent custom layout positions.");
        modularDesc.setTextSize(12);
        modularDesc.setTextColor(Color.DKGRAY);
        modularDesc.setPadding(0, 4, 0, 0);
        rowModular.addView(modularDesc);
        controlCenterPageLayout.addView(rowModular);

        // Switch 1b: Auto-Restart System UI on Edit Confirmation
        LinearLayout rowRestartOnEdit = new LinearLayout(this);
        rowRestartOnEdit.setOrientation(LinearLayout.VERTICAL);
        rowRestartOnEdit.setPadding(0, 8, 0, 18);

        ccRestartOnEditExitSwitch = new Switch(this);
        ccRestartOnEditExitSwitch.setText("Auto-Restart System UI on Edit Exit");
        ccRestartOnEditExitSwitch.setTextSize(16);
        ccRestartOnEditExitSwitch.setTypeface(null, Typeface.BOLD);
        ccRestartOnEditExitSwitch.setChecked(currentConfig.optBoolean("ccRestartOnEditExit", true));
        ccRestartOnEditExitSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            try { currentConfig.put("ccRestartOnEditExit", isChecked); saveConfig(); } catch (Exception ignored) {}
        });
        rowRestartOnEdit.addView(ccRestartOnEditExitSwitch);

        TextView restartOnEditDesc = new TextView(this);
        restartOnEditDesc.setText("• Automatically restarts System UI when confirming and exiting edit mode to ensure 100% clean touch target binding on new tile containers.");
        restartOnEditDesc.setTextSize(12);
        restartOnEditDesc.setTextColor(Color.DKGRAY);
        restartOnEditDesc.setPadding(0, 4, 0, 0);
        rowRestartOnEdit.addView(restartOnEditDesc);
        controlCenterPageLayout.addView(rowRestartOnEdit);

        // Switch 2: Center Bounce Animation (Control Center)
        LinearLayout rowBounce = new LinearLayout(this);
        rowBounce.setOrientation(LinearLayout.VERTICAL);
        rowBounce.setPadding(0, 8, 0, 18);

        ccCenterBounceSwitch = new Switch(this);
        ccCenterBounceSwitch.setText("Enable Center Bounce Animation (Control Center)");
        ccCenterBounceSwitch.setTextSize(16);
        ccCenterBounceSwitch.setTypeface(null, Typeface.BOLD);
        ccCenterBounceSwitch.setChecked(currentConfig.optBoolean("ccAnimEnabled", true));
        ccCenterBounceSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            try { currentConfig.put("ccAnimEnabled", isChecked); saveConfig(); } catch (Exception ignored) {}
        });
        rowBounce.addView(ccCenterBounceSwitch);

        TextView bounceDesc = new TextView(this);
        bounceDesc.setText("• All tiles emerge outward from the center of the screen with fluid harmonic spring bounce physics.\n• Eliminates left/right horizontal row lag (Wi-Fi & Mobile Data emerge synchronously).\n• Preserves full pull-down overscroll stretch and rubber-band bounce.");
        bounceDesc.setTextSize(12);
        bounceDesc.setTextColor(Color.DKGRAY);
        bounceDesc.setPadding(0, 4, 0, 0);
        rowBounce.addView(bounceDesc);
        controlCenterPageLayout.addView(rowBounce);

        // Switch 3: iOS 26 Jelly Physics Animation
        LinearLayout rowJelly = new LinearLayout(this);
        rowJelly.setOrientation(LinearLayout.VERTICAL);
        rowJelly.setPadding(0, 8, 0, 18);

        ccJellySwitch = new Switch(this);
        ccJellySwitch.setText("Enable Jelly Animation (iOS 26 Physics)");
        ccJellySwitch.setTextSize(16);
        ccJellySwitch.setTypeface(null, Typeface.BOLD);
        ccJellySwitch.setChecked(currentConfig.optBoolean("ccJellyEnabled", true));
        ccJellySwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            try { currentConfig.put("ccJellyEnabled", isChecked); saveConfig(); } catch (Exception ignored) {}
        });
        rowJelly.addView(ccJellySwitch);

        TextView jellyDesc = new TextView(this);
        jellyDesc.setText("• Fluid asymmetric squish & stretch on all tiles based on pull-down velocity & displacement.\n• Volume-conserving Poisson lateral contraction and top-anchored gelatinous spring rebound.\n• Works seamlessly with Low-End animation mode and standard mode.");
        jellyDesc.setTextSize(12);
        jellyDesc.setTextColor(Color.DKGRAY);
        jellyDesc.setPadding(0, 4, 0, 0);
        rowJelly.addView(jellyDesc);
        controlCenterPageLayout.addView(rowJelly);

        // Switch 4: Xiaomi Native Low-End Animation
        LinearLayout rowLowEnd = new LinearLayout(this);
        rowLowEnd.setOrientation(LinearLayout.VERTICAL);
        rowLowEnd.setPadding(0, 8, 0, 18);

        ccLowEndSwitch = new Switch(this);
        ccLowEndSwitch.setText("Enable Low-End Animation (Xiaomi Stock)");
        ccLowEndSwitch.setTextSize(16);
        ccLowEndSwitch.setTypeface(null, Typeface.BOLD);
        ccLowEndSwitch.setChecked(currentConfig.optBoolean("ccLowEndAnim", false));
        ccLowEndSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            try { currentConfig.put("ccLowEndAnim", isChecked); saveConfig(); } catch (Exception ignored) {}
        });
        rowLowEnd.addView(ccLowEndSwitch);

        TextView lowEndDesc = new TextView(this);
        lowEndDesc.setText("• Activates Xiaomi's native built-in Low-End animation engine for both Control Center and Notification Center.\n• Disables all diagonal unrolls, 3D Z-axis zoom, and complex lateral shifts using pure stock system logic.");
        lowEndDesc.setTextSize(12);
        lowEndDesc.setTextColor(Color.DKGRAY);
        lowEndDesc.setPadding(0, 4, 0, 0);
        rowLowEnd.addView(lowEndDesc);
        controlCenterPageLayout.addView(rowLowEnd);

        // Reset Control Center Layout Button Card
        LinearLayout rowReset = new LinearLayout(this);
        rowReset.setOrientation(LinearLayout.VERTICAL);
        rowReset.setPadding(0, 16, 0, 18);

        Button resetLayoutBtn = new Button(this);
        resetLayoutBtn.setText("🔄 Reset Control Center Layout to Stock");
        resetLayoutBtn.setTextSize(14);
        resetLayoutBtn.setTypeface(null, Typeface.BOLD);
        resetLayoutBtn.setTextColor(Color.WHITE);
        resetLayoutBtn.setBackgroundColor(Color.parseColor("#B00020"));
        resetLayoutBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Reset Layout to Stock?")
                .setMessage("This will dissolve all 2x2, 2x1, and 1x2 tile groups, restore stock Wi-Fi/Mobile Data expansion, and reset all component positions to stock.")
                .setPositiveButton("Reset", (dialog, which) -> {
                    try {
                        sendBroadcast(new Intent("dev.joseph.hyperbounce.RESET_CC_LAYOUT"));
                        getContentResolver().call(android.net.Uri.parse("content://dev.joseph.hyperbounce.provider"), "resetCCLayout", null, null);
                        Toast.makeText(this, "Control Center layout reset to stock!", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Failed to reset layout: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
        rowReset.addView(resetLayoutBtn);

        TextView resetDesc = new TextView(this);
        resetDesc.setText("• Reverts all modular customizations and tile groupings to pure Xiaomi HyperOS defaults.");
        resetDesc.setTextSize(12);
        resetDesc.setTextColor(Color.DKGRAY);
        resetDesc.setPadding(0, 4, 0, 0);
        rowReset.addView(resetDesc);
        controlCenterPageLayout.addView(rowReset);
    }

    private void setupUnlockPage() {
        addPageTitle(unlockPageLayout, "Screen Unlock Animations");
        
        LinearLayout presetLayout = new LinearLayout(this);
        presetLayout.setOrientation(LinearLayout.HORIZONTAL);
        presetLayout.setPadding(0, 0, 0, 16);
        Button loadPresetBtn = new Button(this);
        loadPresetBtn.setText("📂 Load Preset");
        loadPresetBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        loadPresetBtn.setOnClickListener(v -> loadPresetAction("unlock"));
        Button savePresetBtn = new Button(this);
        savePresetBtn.setText("💾 Save Preset");
        savePresetBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        savePresetBtn.setOnClickListener(v -> savePresetAction("unlock"));
        presetLayout.addView(loadPresetBtn);
        presetLayout.addView(savePresetBtn);
        unlockPageLayout.addView(presetLayout);

        unlockMasterSwitch = new Switch(this);
        unlockMasterSwitch.setText("Enable Unlock Animation Hook");
        unlockMasterSwitch.setTextSize(18);
        unlockMasterSwitch.setTypeface(null, Typeface.BOLD);
        unlockMasterSwitch.setChecked(currentConfig.optBoolean("unlockEnabled", true));
        unlockMasterSwitch.setPadding(0, 0, 0, 24);
        unlockMasterSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            try { currentConfig.put("unlockEnabled", isChecked); saveConfig(); } catch (Exception ignored) {}
        });
        unlockPageLayout.addView(unlockMasterSwitch);

        addSectionHeader(unlockPageLayout, "GRID & MODE");
        LinearLayout gridLayout = new LinearLayout(this);
        gridLayout.setOrientation(LinearLayout.HORIZONTAL);
        gridLayout.setGravity(Gravity.CENTER_VERTICAL);

        gridInput = new EditText(this);
        gridInput.setHint("Grid e.g. 4x7");
        gridInput.setSingleLine(true);
        gridInput.setText(getGridText());
        gridInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button updateGridBtn = new Button(this);
        updateGridBtn.setText("Apply Grid");
        updateGridBtn.setAllCaps(false);
        updateGridBtn.setOnClickListener(v -> applyGrid());

        gridLayout.addView(gridInput);
        gridLayout.addView(updateGridBtn);
        unlockPageLayout.addView(gridLayout);

        unlockModeSpinner = new Spinner(this);
        String[] modes = { "Mode 0: iOS Radial Dispersion", "Mode 1: Kinetic Y-Axis Cascade" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        unlockModeSpinner.setAdapter(adapter);
        int safeMode = clamp(currentConfig.optInt("animMode", 1), 0, modes.length - 1);
        unlockModeSpinner.setSelection(safeMode);
        unlockModeSpinner.setPadding(0, 10, 0, 24);
        unlockPageLayout.addView(unlockModeSpinner);

        unlockSlidersContainer = new LinearLayout(this);
        unlockSlidersContainer.setOrientation(LinearLayout.VERTICAL);
        unlockPageLayout.addView(unlockSlidersContainer);

        unlockModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    if (currentConfig.optInt("animMode", 1) != position) {
                        currentConfig.put("animMode", position);
                        saveConfig();
                    }
                    renderUnlockSliders(position);
                } catch (Exception ignored) {}
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        renderUnlockSliders(safeMode);
    }

    private void setupLockPinpadPage() {
        addPageTitle(lockPinpadPageLayout, "Lock Screen Sheet & PIN Pad");

        LinearLayout presetLayout = new LinearLayout(this);
        presetLayout.setOrientation(LinearLayout.HORIZONTAL);
        presetLayout.setPadding(0, 0, 0, 16);
        Button loadPresetBtn = new Button(this);
        loadPresetBtn.setText("📂 Load Preset");
        loadPresetBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        loadPresetBtn.setOnClickListener(v -> loadPresetAction("lockPinpad"));
        Button savePresetBtn = new Button(this);
        savePresetBtn.setText("💾 Save Preset");
        savePresetBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        savePresetBtn.setOnClickListener(v -> savePresetAction("lockPinpad"));
        presetLayout.addView(loadPresetBtn);
        presetLayout.addView(savePresetBtn);
        lockPinpadPageLayout.addView(presetLayout);

        lockPinpadMasterSwitch = new Switch(this);
        lockPinpadMasterSwitch.setText("Enable Lockscreen & Keypad Physics");
        lockPinpadMasterSwitch.setTextSize(18);
        lockPinpadMasterSwitch.setTypeface(null, Typeface.BOLD);
        lockPinpadMasterSwitch.setChecked(currentConfig.optBoolean("unlockEnabled", true));
        lockPinpadMasterSwitch.setPadding(0, 0, 0, 24);
        lockPinpadMasterSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            try { currentConfig.put("unlockEnabled", isChecked); saveConfig(); } catch (Exception ignored) {}
        });
        lockPinpadPageLayout.addView(lockPinpadMasterSwitch);

        lockPinpadSlidersContainer = new LinearLayout(this);
        lockPinpadSlidersContainer.setOrientation(LinearLayout.VERTICAL);
        lockPinpadPageLayout.addView(lockPinpadSlidersContainer);
        renderLockPinpadSliders();
    }

    private void renderLockPinpadSliders() {
        lockPinpadSlidersContainer.removeAllViews();

        addSectionHeader(lockPinpadSlidersContainer, "LOCK SCREEN SHEET & BOTTOM PILL");
        addSlider(lockPinpadSlidersContainer, "lockCardBorderWidth", "Sheet Border Width (dp)", "Thickness of Left, Right, and Bottom specular frame outline", 0.5f, 8.0f, 2.5f);
        addSlider(lockPinpadSlidersContainer, "lockCardBorderAlpha", "Sheet Border Glow / Opacity", "Glow intensity of the glass outline (0.0 to 1.0)", 0.1f, 1.0f, 0.47f);
        addSlider(lockPinpadSlidersContainer, "lockPillWidth", "Home Indicator Pill Width (dp)", "Length of the bottom iOS home indicator bar", 80.0f, 280.0f, 156.0f);
        addSlider(lockPinpadSlidersContainer, "lockPillHeight", "Home Indicator Pill Height (dp)", "Thickness of the bottom iOS home indicator bar", 2.0f, 16.0f, 6.0f);
        addSlider(lockPinpadSlidersContainer, "lockPillBottomMargin", "Pill Bottom Distance (dp)", "Distance between the pill and bottom of the display", 0.0f, 35.0f, 9.0f);
        addSlider(lockPinpadSlidersContainer, "lockLipHeight", "Bottom Glass Lip Height (dp)", "Height of the specular curved bottom glass layer", 10.0f, 70.0f, 28.0f);

        addSectionHeader(lockPinpadSlidersContainer, "PIN PAD GEOMETRY & TYPOGRAPHY");
        addSlider(lockPinpadSlidersContainer, "pinCircleDiameter", "Button Circle Diameter (dp)", "Size of each circular numeric key (does not overlap neighbors)", 60.0f, 94.0f, 82.0f);
        addSlider(lockPinpadSlidersContainer, "pinCircleFillAlpha", "Frosted Glass Fill Opacity", "Inner frosted opacity of circular buttons (0.0 to 1.0)", 0.0f, 0.90f, 0.20f);
        addSlider(lockPinpadSlidersContainer, "pinCircleStrokeWidth", "Specular Rim Width (dp)", "Thickness of circular border outline", 0.5f, 4.0f, 1.5f);
        addSlider(lockPinpadSlidersContainer, "pinCircleStrokeAlpha", "Specular Rim Opacity", "Glow intensity of circular border outline (0.0 to 1.0)", 0.0f, 1.0f, 0.47f);
        addSwitch(lockPinpadSlidersContainer, "pinShowKlondike", "Show Subtitle Letters (ABC, DEF)", "Toggle letters below numeric digits", false);
        addSlider(lockPinpadSlidersContainer, "pinDigitTextSize", "Digit Font Size (sp)", "Font size of centered PIN numbers", 22.0f, 48.0f, 36.0f);

        addSectionHeader(lockPinpadSlidersContainer, "PIN PAD VERTICAL ROW SPACING");
        addSlider(lockPinpadSlidersContainer, "pinRow0Offset", "PIN Dots Offset (dp)", "Vertical translation of PIN entry dots", -180.0f, 0.0f, -115.0f);
        addSlider(lockPinpadSlidersContainer, "pinRow1Offset", "Row 1 (1, 2, 3) Offset (dp)", "Vertical translation of first row", -160.0f, 0.0f, -90.0f);
        addSlider(lockPinpadSlidersContainer, "pinRow2Offset", "Row 2 (4, 5, 6) Offset (dp)", "Vertical translation of second row", -140.0f, 0.0f, -60.0f);
        addSlider(lockPinpadSlidersContainer, "pinRow3Offset", "Row 3 (7, 8, 9) Offset (dp)", "Vertical translation of third row", -120.0f, 0.0f, -30.0f);

        addSectionHeader(lockPinpadSlidersContainer, "SPRING SQUISH & GLOW PHYSICS (iOS HARMONIC)");
        addSlider(lockPinpadSlidersContainer, "pinTapSquishScale", "Tap Compression Scale", "Deformation scale when finger touches down", 0.65f, 0.98f, 0.88f);
        addSlider(lockPinpadSlidersContainer, "pinReleasePlunge", "Release Extra Plunge", "Extra downward squeeze before rebounding on release", 0.0f, 0.25f, 0.10f);
        addSlider(lockPinpadSlidersContainer, "pinSpringStiffness", "Spring Bounce Stiffness", "Tension of the recovery spring (higher = faster snap)", 15.0f, 150.0f, 50.0f);
        addSlider(lockPinpadSlidersContainer, "pinSpringDamping", "Spring Bounce Damping", "Friction of the recovery spring (lower = bouncier wobble, higher = stiffer snap)", 1.0f, 15.0f, 5.5f);
        addSlider(lockPinpadSlidersContainer, "pinPressGlowAlpha", "Tap Glow Highlight Opacity", "Subtle brightness highlight/glow intensity when tapping a key", 0.0f, 0.80f, 0.35f);

        addSectionHeader(lockPinpadSlidersContainer, "SEQUENTIAL ENTRY ANIMATION (Y-AXIS GLIDE)");
        addSlider(lockPinpadSlidersContainer, "pinEntryTranslationY", "Initial Y-Axis Drop (dp)", "Distance keys glide up from below into their resting position", 20.0f, 300.0f, 90.0f);
        addSlider(lockPinpadSlidersContainer, "pinEntryInitialScale", "Initial Entry Scale", "Starting scale of keys during entry cascade", 0.20f, 1.0f, 0.50f);
        addSlider(lockPinpadSlidersContainer, "pinEntryDuration", "Entry Glide Duration (ms)", "Duration of the upward entry glide", 150.0f, 850.0f, 420.0f);
        addSlider(lockPinpadSlidersContainer, "pinRowDelayStep", "Row Stagger Step (ms)", "Delay increment per row", 0.0f, 150.0f, 55.0f);
        addSlider(lockPinpadSlidersContainer, "pinColDelayStep", "Column Stagger Step (ms)", "Delay increment per column", 0.0f, 100.0f, 25.0f);
        addSlider(lockPinpadSlidersContainer, "pinEntryOvershoot", "Entry Y-Axis Bounce Overshoot", "Ball-like bounce factor when keys glide up past resting slot (0.0 = smooth, 1.8 = iOS bounce)", 0.0f, 5.0f, 1.8f);

        addSectionHeader(lockPinpadSlidersContainer, "PER-KEY INDIVIDUAL DELAY TUNING (ms)");
        addSlider(lockPinpadSlidersContainer, "pin_delay_key1", "Key 1 Delay", "Custom start delay for digit 1", 0.0f, 500.0f, 0.0f);
        addSlider(lockPinpadSlidersContainer, "pin_delay_key2", "Key 2 Delay", "Custom start delay for digit 2", 0.0f, 500.0f, 25.0f);
        addSlider(lockPinpadSlidersContainer, "pin_delay_key3", "Key 3 Delay", "Custom start delay for digit 3", 0.0f, 500.0f, 50.0f);
        addSlider(lockPinpadSlidersContainer, "pin_delay_key4", "Key 4 Delay", "Custom start delay for digit 4", 0.0f, 500.0f, 55.0f);
        addSlider(lockPinpadSlidersContainer, "pin_delay_key5", "Key 5 Delay", "Custom start delay for digit 5", 0.0f, 500.0f, 80.0f);
        addSlider(lockPinpadSlidersContainer, "pin_delay_key6", "Key 6 Delay", "Custom start delay for digit 6", 0.0f, 500.0f, 105.0f);
        addSlider(lockPinpadSlidersContainer, "pin_delay_key7", "Key 7 Delay", "Custom start delay for digit 7", 0.0f, 500.0f, 110.0f);
        addSlider(lockPinpadSlidersContainer, "pin_delay_key8", "Key 8 Delay", "Custom start delay for digit 8", 0.0f, 500.0f, 135.0f);
        addSlider(lockPinpadSlidersContainer, "pin_delay_key9", "Key 9 Delay", "Custom start delay for digit 9", 0.0f, 500.0f, 160.0f);
        addSlider(lockPinpadSlidersContainer, "pin_delay_key0", "Key 0 Delay", "Custom start delay for digit 0", 0.0f, 500.0f, 190.0f);
        addSlider(lockPinpadSlidersContainer, "pin_delay_emergency", "Emergency Button Delay", "Custom start delay for Emergency call", 0.0f, 500.0f, 240.0f);
        addSlider(lockPinpadSlidersContainer, "pin_delay_cancel", "Cancel / Delete Button Delay", "Custom start delay for Delete/Cancel", 0.0f, 500.0f, 220.0f);
    }

    private void setupAppAnimPage() {
        addPageTitle(appAnimPageLayout, "App Open / Close & Recents");
        
        LinearLayout presetLayout = new LinearLayout(this);
        presetLayout.setOrientation(LinearLayout.HORIZONTAL);
        presetLayout.setPadding(0, 0, 0, 16);
        Button loadPresetBtn = new Button(this);
        loadPresetBtn.setText("📂 Load Preset");
        loadPresetBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        loadPresetBtn.setOnClickListener(v -> loadPresetAction("appAnim"));
        Button savePresetBtn = new Button(this);
        savePresetBtn.setText("💾 Save Preset");
        savePresetBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        savePresetBtn.setOnClickListener(v -> savePresetAction("appAnim"));
        presetLayout.addView(loadPresetBtn);
        presetLayout.addView(savePresetBtn);
        appAnimPageLayout.addView(presetLayout);

        appAnimMasterSwitch = new Switch(this);
        appAnimMasterSwitch.setText("Enable App Transition Hooks");
        appAnimMasterSwitch.setTextSize(18);
        appAnimMasterSwitch.setTypeface(null, Typeface.BOLD);
        appAnimMasterSwitch.setChecked(currentConfig.optBoolean("appAnimEnabled", true));
        appAnimMasterSwitch.setPadding(0, 0, 0, 24);
        appAnimMasterSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            try { currentConfig.put("appAnimEnabled", isChecked); saveConfig(); } catch (Exception ignored) {}
        });
        appAnimPageLayout.addView(appAnimMasterSwitch);

        appAnimSlidersContainer = new LinearLayout(this);
        appAnimSlidersContainer.setOrientation(LinearLayout.VERTICAL);
        appAnimPageLayout.addView(appAnimSlidersContainer);
        renderAppAnimSliders();
    }

    private void renderUnlockSliders(int mode) {
        unlockSlidersContainer.removeAllViews();
        if (mode == 0) {
            addSectionHeader(unlockSlidersContainer, "CLASSIC RADIAL UNLOCK");
            addSlider(unlockSlidersContainer, "centerDamping", "Center Damping", "Lower = rubber-band bouncy | Higher = stiff / less bounce", 0.1f, 1.0f, 0.58f);
            addSlider(unlockSlidersContainer, "centerResponse", "Center Response", "Lower = snaps faster | Higher = settles more slowly", 0.1f, 1.5f, 0.48f);
            addSlider(unlockSlidersContainer, "edgeDampingDrop", "Edge Damping Drop", "Higher = edges become more bouncy than the center", 0.0f, 0.5f, 0.10f);
            addSlider(unlockSlidersContainer, "edgeResponseDelay", "Edge Response Delay", "Higher = outer edges lag behind the center more", 0.0f, 1.0f, 0.22f);
            addSlider(unlockSlidersContainer, "minDamping", "Min Damping Bound", "Lower safety floor for the calculated damping", 0.1f, 1.0f, 0.42f);
            addSlider(unlockSlidersContainer, "maxDamping", "Max Damping Bound", "Upper safety ceiling for the calculated damping", 0.1f, 1.0f, 0.65f);
            addSlider(unlockSlidersContainer, "minResponse", "Min Response Bound", "Lower safety floor for the calculated response", 0.1f, 1.5f, 0.45f);
            addSlider(unlockSlidersContainer, "maxResponse", "Max Response Bound", "Upper safety ceiling for the calculated response", 0.1f, 1.5f, 0.75f);
            addSlider(unlockSlidersContainer, "zDepthMultiplier", "Z-Depth Scale", "Higher = icons begin deeper in 3D perspective", 1.0f, 4.0f, 1.60f);
        } else {
            addSectionHeader(unlockSlidersContainer, "KINETIC Y-AXIS UNLOCK GLOBALS");
            addSlider(unlockSlidersContainer, "durationScale", "Global Anim Duration", "Lower = faster animation | Higher = slower settling", 0.1f, 3.0f, 1.0f);
            addSlider(unlockSlidersContainer, "velocityInfluence", "Velocity Impact", "Lower = ignores swipe speed | Higher = stronger flick response", 0.0f, 2.0f, 1.0f);
            addSlider(unlockSlidersContainer, "maxVelocity", "Max Swipe Normalizer", "Lower = max response reached with a softer swipe", 5000.0f, 30000.0f, 16000.0f);
            addSlider(unlockSlidersContainer, "cascadeDamping", "Resting Damping", "Lower = loose/bouncy | Higher = firmer resting state", 0.5f, 1.0f, 0.95f);

            addSectionHeader(unlockSlidersContainer, "DOCK ISOLATION");
            addSlider(unlockSlidersContainer, "kineticDockDamping", "Dock Damping", "Lower = bouncier dock | Higher = stiffer dock", 0.1f, 1.0f, 0.85f);
            addSlider(unlockSlidersContainer, "kineticDockResponse", "Dock Response", "Lower = snaps faster | Higher = floats into place", 0.1f, 1.5f, 0.38f);
            addSlider(unlockSlidersContainer, "kineticDockZDepth", "Dock Z-Depth", "Higher = dock begins deeper in 3D space", 0.0f, 4000.0f, 800.0f);

            int rows = clamp(currentConfig.optInt("gridRows", 7), 1, 32);
            addSectionHeader(unlockSlidersContainer, "ROW-BY-ROW Z-DEPTH (PERSPECTIVE TRAVEL)");
            for (int i = 0; i < rows; i++) {
                float def = Math.max(100.0f, 2000.0f - (i * 200.0f));
                addSlider(unlockSlidersContainer, "r" + i + "_z", "Row " + i + " Z-Depth", "Lower = starts closer | Higher = starts deeper in 3D", 100.0f, 4000.0f, def);
            }

            addSectionHeader(unlockSlidersContainer, "DYNAMIC VELOCITY BOUNCE (CENTER VS OUTER)");
            for (int i = 0; i < rows; i++) {
                float def = Math.max(0.0f, 0.45f - (i * 0.05f));
                addSlider(unlockSlidersContainer, "r" + i + "_center_bounce", "Row " + i + " Center Bounce", "Higher = more rubber-band overshoot for center columns", 0.0f, 1.0f, def);
                addSlider(unlockSlidersContainer, "r" + i + "_outer_bounce", "Row " + i + " Outer Bounce", "Higher = more rubber-band overshoot at screen edges", 0.0f, 1.0f, def);
            }

            addSectionHeader(unlockSlidersContainer, "DYNAMIC SETTLING TIME (CENTER VS OUTER)");
            for (int i = 0; i < rows; i++) {
                float def = 0.55f + (i * 0.04f);
                addSlider(unlockSlidersContainer, "r" + i + "_center_settle", "Row " + i + " Center Settling Time", "Higher = center rows stay in motion longer", 0.1f, 1.5f, def);
                addSlider(unlockSlidersContainer, "r" + i + "_outer_settle", "Row " + i + " Outer Settling Time", "Higher = edge rows stay in motion longer", 0.1f, 1.5f, def);
            }
        }
    }

    private void renderAppAnimSliders() {
        appAnimSlidersContainer.removeAllViews();
        addSectionHeader(appAnimSlidersContainer, "SPATIAL TRAJECTORY & 3D DEPTH");
        addSlider(appAnimSlidersContainer, "appYArc", "Vertical Parabolic Arc", "Vertical lift/arc applied through the transition envelope", -200.0f, 300.0f, 0.0f);
        addSlider(appAnimSlidersContainer, "appBottomZWarp", "3D Bottom-Edge Compression", "Compresses the lower edge during the transition", 0.0f, 0.70f, 0.0f);
        addSlider(appAnimSlidersContainer, "appXSway", "Horizontal Sway Curve", "Adds a lateral flight offset through the transition", -150.0f, 150.0f, 0.0f);
        addSlider(appAnimSlidersContainer, "appInterruptSmooth", "Gesture Interrupt Stabilizer", "Controls how sharply the geometry effect rises and falls", 0.1f, 1.5f, 1.0f);

        addSectionHeader(appAnimSlidersContainer, "SPATIAL FLIGHT & MASS DYNAMICS");
        addSlider(appAnimSlidersContainer, "appTravelDamping", "Flight Path Damping", "Damping for center X/Y spring channels", 0.30f, 1.0f, 0.80f);
        addSlider(appAnimSlidersContainer, "appTravelStiffness", "Flight Path Stiffness", "Stiffness for center X/Y spring channels", 40.0f, 600.0f, 200.0f);

        addSectionHeader(appAnimSlidersContainer, "WINDOW SIZE EXPANSION");
        addSlider(appAnimSlidersContainer, "appSizeDamping", "Window Width/Height Damping", "Damping for Width and Height channels", 0.30f, 1.0f, 0.80f);
        addSlider(appAnimSlidersContainer, "appSizeStiffness", "Window Width/Height Stiffness", "Stiffness for Width and Height channels", 40.0f, 600.0f, 200.0f);
        addSlider(appAnimSlidersContainer, "appRatioDamping", "Aspect Ratio Damping", "Damping for Ratio channel", 0.30f, 1.0f, 0.80f);
        addSlider(appAnimSlidersContainer, "appRatioStiffness", "Aspect Ratio Stiffness", "Stiffness for Ratio channel", 40.0f, 800.0f, 400.0f);

        addSectionHeader(appAnimSlidersContainer, "SURFACE MORPH & 3D PITCH");
        addSlider(appAnimSlidersContainer, "appCornerDamping", "Corner Morph Damping", "Damping for Radius / RadiusRatio channels", 0.30f, 1.0f, 0.80f);
        addSlider(appAnimSlidersContainer, "appCornerStiffness", "Corner Morph Stiffness", "Stiffness for Radius / RadiusRatio channels", 50.0f, 800.0f, 200.0f);
        addSlider(appAnimSlidersContainer, "appAlphaDamping", "Surface Fade Damping", "Damping for Alpha / surface-extension channels", 0.40f, 1.0f, 0.99f);
        addSlider(appAnimSlidersContainer, "appAlphaStiffness", "Surface Fade Stiffness", "Stiffness for Alpha / surface-extension channels", 50.0f, 800.0f, 400.0f);
        addSlider(appAnimSlidersContainer, "appIconPitchDeg", "Ghost Icon 3D Tilt Angle", "3D tilt of the recents ghost icon", 0.0f, 45.0f, 0.0f);
        addSlider(appAnimSlidersContainer, "appIconCameraDist", "3D Camera Distance", "Perspective distance used by the ghost icon", 2000.0f, 15000.0f, 8000.0f);
    }

    private void addPageTitle(LinearLayout parent, String titleText) {
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(0, 0, 0, 20);

        Button backButton = new Button(this);
        backButton.setText("Back");
        backButton.setAllCaps(false);
        backButton.setOnClickListener(v -> showPage(Page.MAIN));

        TextView title = new TextView(this);
        title.setText(titleText);
        title.setTextSize(22);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(18, 0, 0, 0);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        titleRow.addView(backButton);
        titleRow.addView(title);
        parent.addView(titleRow);
    }

    private void showPage(Page page) {
        currentPage = page;
        menuLayout.setVisibility(page == Page.MAIN ? View.VISIBLE : View.GONE);
        unlockPageLayout.setVisibility(page == Page.UNLOCK ? View.VISIBLE : View.GONE);
        appAnimPageLayout.setVisibility(page == Page.APP_ANIM ? View.VISIBLE : View.GONE);
        lockPinpadPageLayout.setVisibility(page == Page.LOCK_PINPAD ? View.VISIBLE : View.GONE);
        controlCenterPageLayout.setVisibility(page == Page.CONTROL_CENTER ? View.VISIBLE : View.GONE);
    }

    private void setupBackHandling() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                () -> {
                    if (currentPage != Page.MAIN) {
                        showPage(Page.MAIN);
                    } else {
                        finish();
                    }
                }
            );
        }
    }

    @Override
    public void onBackPressed() {
        if (currentPage != Page.MAIN) {
            showPage(Page.MAIN);
        } else {
            super.onBackPressed();
        }
    }

    private void applyGrid() {
        String[] parts = gridInput.getText().toString().trim().toLowerCase().split("x");
        if (parts.length != 2) {
            Toast.makeText(this, "Use format like 4x7", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            int cols = Integer.parseInt(parts[0].trim());
            int rows = Integer.parseInt(parts[1].trim());
            if (cols < 1 || rows < 1 || cols > 32 || rows > 32) throw new NumberFormatException();
            currentConfig.put("gridColumns", cols);
            currentConfig.put("gridRows", rows);
            saveConfig();
            renderUnlockSliders(clamp(currentConfig.optInt("animMode", 1), 0, 1));
            Toast.makeText(this, "Grid updated to " + cols + "x" + rows, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Invalid grid. Use 1–32 x 1–32", Toast.LENGTH_SHORT).show();
        }
    }

    private void normalizeGrid() {
        int cols = clamp(currentConfig.optInt("gridColumns", 4), 1, 32);
        int rows = clamp(currentConfig.optInt("gridRows", 7), 1, 32);
        try {
            currentConfig.put("gridColumns", cols);
            currentConfig.put("gridRows", rows);
        } catch (Exception ignored) {}
    }

    private String getGridText() {
        return currentConfig.optInt("gridColumns", 4) + "x" + currentConfig.optInt("gridRows", 7);
    }

    private void initializeOptimizedPreset() {
        try {
            File unlockDir = new File(getFilesDir(), "presets_unlock");
            if (!unlockDir.exists()) unlockDir.mkdirs();
            File appDir = new File(getFilesDir(), "presets_app");
            if (!appDir.exists()) appDir.mkdirs();
            File lockDir = new File(getFilesDir(), "presets_lock");
            if (!lockDir.exists()) lockDir.mkdirs();

            // Preset 1: Optimized_iOS (Smooth Cascade)
            File optUnlock1 = new File(unlockDir, "Optimized_iOS.json");
            JSONObject jsonU1 = new JSONObject();
            jsonU1.put("unlockEnabled", true);
            jsonU1.put("appAnimEnabled", true);
            jsonU1.put("animMode", 1);
            jsonU1.put("gridColumns", 4);
            jsonU1.put("gridRows", 7);
            jsonU1.put("durationScale", 0.950);
            jsonU1.put("velocityInfluence", 0.850);
            jsonU1.put("maxVelocity", 12000.0);
            jsonU1.put("cascadeDamping", 0.900);
            jsonU1.put("kineticDockDamping", 0.900);
            jsonU1.put("kineticDockResponse", 0.400);
            jsonU1.put("kineticDockZDepth", 50.0);
            double[] z1 = {1800.0, 1650.0, 1500.0, 1350.0, 1200.0, 1050.0, 900.0};
            for (int i = 0; i < 7; i++) {
                jsonU1.put("r" + i + "_z", z1[i]);
                jsonU1.put("r" + i + "_center_bounce", 0.900);
                jsonU1.put("r" + i + "_outer_bounce", 0.900);
                double centerSettle = 0.450 + ((6 - i) * 0.065);
                double outerSettle = centerSettle + ((6 - i) * 0.010) + 0.005;
                jsonU1.put("r" + i + "_center_settle", centerSettle);
                jsonU1.put("r" + i + "_outer_settle", outerSettle);
            }
            FileOutputStream fos1 = new FileOutputStream(optUnlock1);
            fos1.write(jsonU1.toString().getBytes("UTF-8"));
            fos1.close();

            // Preset 2: iOS_Optimized_2
            File optUnlock2 = new File(unlockDir, "iOS_Optimized_2.json");
            JSONObject jsonU2 = new JSONObject();
            jsonU2.put("unlockEnabled", true);
            jsonU2.put("appAnimEnabled", true);
            jsonU2.put("animMode", 1);
            jsonU2.put("gridColumns", 4);
            jsonU2.put("gridRows", 7);
            jsonU2.put("durationScale", 0.90);
            jsonU2.put("velocityInfluence", 1.45);
            jsonU2.put("maxVelocity", 9500.0);
            jsonU2.put("cascadeDamping", 0.88);
            jsonU2.put("kineticDockDamping", 0.88);
            jsonU2.put("kineticDockResponse", 0.32);
            jsonU2.put("kineticDockZDepth", 120.0);
            double[] z2 = {2200.0, 1950.0, 1700.0, 1450.0, 1200.0, 950.0, 700.0};
            double[] cb = {0.35, 0.30, 0.25, 0.20, 0.15, 0.12, 0.10};
            double[] ob = {0.40, 0.35, 0.30, 0.25, 0.20, 0.15, 0.12};
            double[] cs = {0.62, 0.56, 0.50, 0.44, 0.38, 0.34, 0.30};
            double[] os = {0.68, 0.61, 0.55, 0.49, 0.43, 0.38, 0.33};
            for (int i = 0; i < 7; i++) {
                jsonU2.put("r" + i + "_z", z2[i]);
                jsonU2.put("r" + i + "_center_bounce", cb[i]);
                jsonU2.put("r" + i + "_outer_bounce", ob[i]);
                jsonU2.put("r" + i + "_center_settle", cs[i]);
                jsonU2.put("r" + i + "_outer_settle", os[i]);
            }
            FileOutputStream fos2 = new FileOutputStream(optUnlock2);
            fos2.write(jsonU2.toString().getBytes("UTF-8"));
            fos2.close();

            // Preset App: Optimized_Fluid
            File optApp = new File(appDir, "Optimized_Fluid.json");
            JSONObject jsonA = new JSONObject();
            jsonA.put("appPosDamping", 0.85);
            jsonA.put("appPosResponse", 0.45);
            jsonA.put("appScaleDamping", 0.85);
            jsonA.put("appScaleResponse", 0.50);
            jsonA.put("appCornerDamping", 1.0);
            jsonA.put("appCornerResponse", 0.35);
            jsonA.put("appAlphaDamping", 1.0);
            jsonA.put("appAlphaResponse", 0.30);
            FileOutputStream fosA = new FileOutputStream(optApp);
            fosA.write(jsonA.toString().getBytes("UTF-8"));
            fosA.close();

            // Preset Lock/PINPad: iOS_26_Glass
            File optLock = new File(lockDir, "iOS_26_Glass.json");
            JSONObject jsonL = new JSONObject();
            jsonL.put("lockCardBorderWidth", 2.5);
            jsonL.put("lockCardBorderAlpha", 0.47);
            jsonL.put("lockPillWidth", 156.0);
            jsonL.put("lockPillHeight", 6.0);
            jsonL.put("lockPillBottomMargin", 9.0);
            jsonL.put("lockLipHeight", 28.0);
            jsonL.put("pinCircleDiameter", 82.0);
            jsonL.put("pinCircleFillAlpha", 0.20);
            jsonL.put("pinCircleStrokeWidth", 1.5);
            jsonL.put("pinCircleStrokeAlpha", 0.47);
            jsonL.put("pinShowKlondike", false);
            jsonL.put("pinDigitTextSize", 36.0);
            jsonL.put("pinRow1Offset", -90.0);
            jsonL.put("pinRow2Offset", -60.0);
            jsonL.put("pinRow3Offset", -30.0);
            jsonL.put("pinRow0Offset", -115.0);
            jsonL.put("pinTapSquishScale", 0.88);
            jsonL.put("pinReleasePlunge", 0.10);
            jsonL.put("pinSpringStiffness", 50.0);
            jsonL.put("pinSpringDamping", 5.5);
            jsonL.put("pinPressGlowAlpha", 0.35);
            jsonL.put("pinEntryTranslationY", 90.0);
            jsonL.put("pinEntryInitialScale", 0.50);
            jsonL.put("pinEntryDuration", 420.0);
            jsonL.put("pinRowDelayStep", 55.0);
            jsonL.put("pinColDelayStep", 25.0);
            jsonL.put("pinEntryOvershoot", 1.8);
            FileOutputStream fosL = new FileOutputStream(optLock);
            fosL.write(jsonL.toString().getBytes("UTF-8"));
            fosL.close();

        } catch (Exception ignored) {}
    }

    private void refreshPresetList() {
        initializeOptimizedPreset();
    }

    private void loadPresetAction(String type) {
        try {
            String dirName;
            if (type.equals("unlock")) dirName = "presets_unlock";
            else if (type.equals("lockPinpad")) dirName = "presets_lock";
            else dirName = "presets_app";

            File dir = new File(getFilesDir(), dirName);
            if (!dir.exists()) dir.mkdirs();
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null || files.length == 0) {
                Toast.makeText(this, "No saved presets found in " + dirName, Toast.LENGTH_SHORT).show();
                return;
            }
            final String[] names = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                names[i] = files[i].getName().replace(".json", "");
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Select Preset to Load");
            builder.setItems(names, (dialog, which) -> {
                String chosen = names[which];
                try {
                    File file = new File(dir, chosen + ".json");
                    if (file.exists()) {
                        FileInputStream fis = new FileInputStream(file);
                        byte[] data = new byte[(int) file.length()];
                        fis.read(data);
                        fis.close();

                        JSONObject loaded = new JSONObject(new String(data, "UTF-8"));
                        java.util.Iterator<String> keys = loaded.keys();
                        while (keys.hasNext()) {
                            String k = keys.next();
                            currentConfig.put(k, loaded.get(k));
                        }
                        if (type.equals("unlock")) currentConfig.put("unlockEnabled", true);
                        if (type.equals("appAnim")) currentConfig.put("appAnimEnabled", true);
                        if (type.equals("lockPinpad")) currentConfig.put("unlockEnabled", true);
                        saveConfig();

                        if (unlockSlidersContainer != null) renderUnlockSliders(currentConfig.optInt("animMode", 1));
                        if (appAnimSlidersContainer != null) renderAppAnimSliders();
                        if (lockPinpadSlidersContainer != null) renderLockPinpadSliders();
                        if (unlockMasterSwitch != null) unlockMasterSwitch.setChecked(currentConfig.optBoolean("unlockEnabled", true));
                        if (appAnimMasterSwitch != null) appAnimMasterSwitch.setChecked(currentConfig.optBoolean("appAnimEnabled", true));
                        if (lockPinpadMasterSwitch != null) lockPinpadMasterSwitch.setChecked(currentConfig.optBoolean("unlockEnabled", true));

                        Toast.makeText(this, "Loaded Preset: " + chosen, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, "Failed to load preset", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
        } catch (Exception ignored) {}
    }

    private void savePresetAction(String type) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save Current Setup as Preset");

        final EditText input = new EditText(this);
        input.setHint("Preset Name (e.g. My_Custom_Setup)");
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (!name.isEmpty()) {
                try {
                    String dirName;
                    if (type.equals("unlock")) dirName = "presets_unlock";
                    else if (type.equals("lockPinpad")) dirName = "presets_lock";
                    else dirName = "presets_app";

                    File dir = new File(getFilesDir(), dirName);
                    if (!dir.exists()) dir.mkdirs();
                    File file = new File(dir, name + ".json");
                    FileOutputStream fos = new FileOutputStream(file);

                    JSONObject out = new JSONObject();
                    java.util.Iterator<String> keys = currentConfig.keys();
                    while (keys.hasNext()) {
                        String k = keys.next();
                        if (type.equals("unlock") && !k.startsWith("app") && !k.startsWith("lock") && !k.startsWith("pin") && !k.startsWith("cc") && !k.startsWith("notif")) {
                            out.put(k, currentConfig.get(k));
                        } else if (type.equals("appAnim") && k.startsWith("app")) {
                            out.put(k, currentConfig.get(k));
                        } else if (type.equals("lockPinpad") && (k.startsWith("lock") || k.startsWith("pin"))) {
                            out.put(k, currentConfig.get(k));
                        }
                    }

                    fos.write(out.toString().getBytes("UTF-8"));
                    fos.close();
                    Toast.makeText(this, "Saved preset: " + name, Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {}
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void loadConfig() {
        try {
            File file = new File(getFilesDir(), "config.json");
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                byte[] data = new byte[(int) file.length()];
                int read = fis.read(data);
                fis.close();
                if (read > 0) currentConfig = new JSONObject(new String(data, "UTF-8"));
            }
        } catch (Exception ignored) {}
    }

    private void saveConfig() {
        try {
            File file = new File(getFilesDir(), "config.json");
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(currentConfig.toString().getBytes("UTF-8"));
            fos.flush();
            fos.close();
            Intent intent = new Intent("dev.joseph.hyperbounce.ACTION_CONFIG_CHANGED");
            intent.putExtra("config_json", currentConfig.toString());
            sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    private void addSectionHeader(LinearLayout parent, String titleText) {
        TextView header = new TextView(this);
        header.setText(titleText);
        header.setTextSize(13);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(Color.WHITE);
        header.setBackgroundColor(Color.parseColor("#1E88E5"));
        header.setPadding(20, 15, 20, 15);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 26, 0, 16);
        header.setLayoutParams(params);
        parent.addView(header);
    }

    private void addSwitch(LinearLayout parent, String key, String label, String desc, boolean defVal) {
        boolean currentVal = currentConfig.optBoolean(key, defVal);
        
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 8, 0, 16);
        
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextSize(15);
        s.setTypeface(null, Typeface.BOLD);
        s.setChecked(currentVal);
        s.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                currentConfig.put(key, isChecked);
                saveConfig();
            } catch (Exception ignored) {}
        });
        row.addView(s);
        
        if (!desc.isEmpty()) {
            TextView descText = new TextView(this);
            descText.setText(desc);
            descText.setTextSize(11);
            descText.setTextColor(Color.DKGRAY);
            descText.setPadding(0, 2, 0, 8);
            row.addView(descText);
        }
        
        parent.addView(row);
    }

    private void addSlider(LinearLayout parent, String key, String label, String desc, float min, float max, float defVal) {
        float savedVal = (float) currentConfig.optDouble(key, defVal);
        float displayVal = Math.max(min, Math.min(max, savedVal));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 8, 0, 24);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(15);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(Color.BLACK);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView valText = new TextView(this);
        valText.setText(String.format("%.3f", savedVal));
        valText.setTextColor(Color.parseColor("#D81B60"));
        valText.setTextSize(15);

        header.addView(title);
        header.addView(valText);
        row.addView(header);

        if (!desc.isEmpty()) {
            TextView descText = new TextView(this);
            descText.setText(desc);
            descText.setTextSize(11);
            descText.setTextColor(Color.DKGRAY);
            descText.setPadding(0, 2, 0, 8);
            row.addView(descText);
        }

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        seekBar.setProgress((int) (((displayVal - min) / (max - min)) * 1000));

        Button resetBtn = new Button(this);
        resetBtn.setText("Rst");
        resetBtn.setAllCaps(false);
        controls.addView(seekBar);
        controls.addView(resetBtn);
        row.addView(controls);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (!fromUser) return;
                float newVal = min + ((progress / 1000f) * (max - min));
                valText.setText(String.format("%.3f", newVal));
                try { currentConfig.put(key, newVal); } catch (Exception ignored) {}
                saveConfig();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        resetBtn.setOnClickListener(v -> {
            try { currentConfig.put(key, defVal); } catch (Exception ignored) {}
            saveConfig();
            valText.setText(String.format("%.3f", defVal));
            seekBar.setProgress((int) (((defVal - min) / (max - min)) * 1000));
        });

        View.OnLongClickListener manualInput = v -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
            builder.setTitle("Enter custom value");
            final EditText input = new EditText(MainActivity.this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            input.setText(String.valueOf((float) currentConfig.optDouble(key, defVal)));
            builder.setView(input);
            builder.setPositiveButton("Set", (dialog, which) -> {
                try {
                    float newVal = Float.parseFloat(input.getText().toString());
                    currentConfig.put(key, newVal);
                    saveConfig();
                    valText.setText(String.format("%.3f", newVal));
                    float clamped = Math.max(min, Math.min(max, newVal));
                    seekBar.setProgress((int) (((clamped - min) / (max - min)) * 1000));
                } catch (Exception ignored) {}
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
            return true;
        };

        row.setOnLongClickListener(manualInput);
        valText.setOnLongClickListener(manualInput);
        parent.addView(row);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
