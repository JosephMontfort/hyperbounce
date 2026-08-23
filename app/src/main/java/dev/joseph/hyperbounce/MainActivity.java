package dev.joseph.hyperbounce;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private JSONObject currentConfig = new JSONObject();

    private LinearLayout rootLayout;
    private LinearLayout menuLayout;
    private LinearLayout unlockPageLayout;
    private LinearLayout appAnimPageLayout;
    private LinearLayout unlockSlidersContainer;
    private LinearLayout appAnimSlidersContainer;

    private EditText gridInput;
    private Spinner presetSpinner;
    private Spinner unlockModeSpinner;
    private Switch unlockMasterSwitch;
    private Switch appAnimMasterSwitch;

    private enum Page { MAIN, UNLOCK, APP_ANIM }
    private Page currentPage = Page.MAIN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        initializeOptimizedPreset();
        loadConfig();
        normalizeGrid();

        ScrollView scroll = new ScrollView(this);
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(40, 40, 40, 60);
        scroll.addView(rootLayout);
        setContentView(scroll);

        setupHeader();

        menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);

        unlockPageLayout = new LinearLayout(this);
        unlockPageLayout.setOrientation(LinearLayout.VERTICAL);
        unlockPageLayout.setVisibility(View.GONE);

        appAnimPageLayout = new LinearLayout(this);
        appAnimPageLayout.setOrientation(LinearLayout.VERTICAL);
        appAnimPageLayout.setVisibility(View.GONE);

        rootLayout.addView(menuLayout);
        rootLayout.addView(unlockPageLayout);
        rootLayout.addView(appAnimPageLayout);

        setupMainMenu();
        setupUnlockPage();
        setupAppAnimPage();
        setupBackHandling();
    }

    private void setupHeader() {
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

        rootLayout.addView(header);

        TextView subtitle = new TextView(this);
        subtitle.setText("HyperOS animation physics and transition tuning");
        subtitle.setTextSize(13);
        subtitle.setTextColor(Color.DKGRAY);
        subtitle.setPadding(0, 0, 0, 30);
        rootLayout.addView(subtitle);
    }

    private void showActionMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        Menu menu = popup.getMenu();
        menu.add("Restart Launcher");
        menu.add("Restart System UI");
        menu.add("Save Current Preset");
        menu.add("Load Preset");
        popup.setOnMenuItemClickListener(item -> {
            String title = item.getTitle().toString();
            if ("Restart Launcher".equals(title)) {
                try { Runtime.getRuntime().exec("su -c killall com.miui.home"); Toast.makeText(this, "Launcher Restarted", Toast.LENGTH_SHORT).show(); } catch (Exception e) { Toast.makeText(this, "Unable to restart Launcher", Toast.LENGTH_SHORT).show(); }
            } else if ("Restart System UI".equals(title)) {
                try { Runtime.getRuntime().exec("su -c killall com.android.systemui"); Toast.makeText(this, "System UI Restarted", Toast.LENGTH_SHORT).show(); } catch (Exception e) { Toast.makeText(this, "Unable to restart System UI", Toast.LENGTH_SHORT).show(); }
            } else if ("Save Current Preset".equals(title)) {
                savePresetDialog();
            } else if ("Load Preset".equals(title)) {
                loadPresetDialog();
            }
            return true;
        });
        popup.show();
    }

    private void setupMainMenu() {
        TextView desc = new TextView(this);
        desc.setText("Choose a tuning module. All controls save immediately and are shared with the Xposed backend.");
        desc.setTextSize(14);
        desc.setTextColor(Color.DKGRAY);
        desc.setPadding(0, 0, 0, 28);
        menuLayout.addView(desc);

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

        menuLayout.addView(btnUnlock);
        menuLayout.addView(btnAppAnim);

        addSectionHeader(menuLayout, "PROFILES");

        LinearLayout presetLayout = new LinearLayout(this);
        presetLayout.setOrientation(LinearLayout.HORIZONTAL);
        presetLayout.setGravity(Gravity.CENTER_VERTICAL);

        presetSpinner = new Spinner(this);
        presetSpinner.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        presetSpinner.setContentDescription("Saved profiles");

        Button loadPresetBtn = new Button(this);
        loadPresetBtn.setText("Load");
        loadPresetBtn.setAllCaps(false);
        loadPresetBtn.setOnClickListener(v -> loadSelectedPreset());

        Button savePresetBtn = new Button(this);
        savePresetBtn.setText("Save");
        savePresetBtn.setAllCaps(false);
        savePresetBtn.setOnClickListener(v -> savePresetDialog());

        presetLayout.addView(presetSpinner);
        presetLayout.addView(loadPresetBtn);
        presetLayout.addView(savePresetBtn);
        menuLayout.addView(presetLayout);

        refreshPresets();
    }

    private void setupUnlockPage() {
        addPageTitle(unlockPageLayout, "Screen Unlock Animations");

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

    private void setupAppAnimPage() {
        addPageTitle(appAnimPageLayout, "App Open / Close & Recents");

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
    }

    private void setupBackHandling() {
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBack
            );
        }
    }

    private void handleBack() {
        if (currentPage != Page.MAIN) {
            showPage(Page.MAIN);
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        handleBack();
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
            File dir = new File(getFilesDir(), "presets");
            if (!dir.exists()) dir.mkdirs();
            File opt = new File(dir, "Optimized.json");
            if (!opt.exists()) {
                JSONObject json = new JSONObject();
                json.put("unlockEnabled", true);
                json.put("appAnimEnabled", true);
                json.put("animMode", 1);
                json.put("gridColumns", 4);
                json.put("gridRows", 7);
                json.put("durationScale", 0.782);
                json.put("velocityInfluence", 1.526);
                json.put("maxVelocity", 14850.0);
                json.put("cascadeDamping", 0.920);
                json.put("kineticDockDamping", 0.850);
                json.put("kineticDockResponse", 0.281);
                json.put("kineticDockZDepth", 0.985);

                double[] z = {3157.6, 3032.8, 2849.5, 2599.9, 2350.3, 2170.9, 2057.8};
                double[] bounces = {0.450, 0.473, 0.474, 0.469, 0.471, 0.459, 0.463};
                for (int i = 0; i < 7; i++) {
                    json.put("r" + i + "_z", z[i]);
                    json.put("r" + i + "_center_bounce", bounces[i]);
                    json.put("r" + i + "_outer_bounce", bounces[i]);
                    double settle = 0.531 + ((6 - i) * 0.075);
                    json.put("r" + i + "_center_settle", settle);
                    json.put("r" + i + "_outer_settle", settle);
                }
                FileOutputStream fos = new FileOutputStream(opt);
                fos.write(json.toString().getBytes("UTF-8"));
                fos.close();
            }
        } catch (Exception ignored) {}
    }

    private void refreshPresets() {
        if (presetSpinner == null) return;
        File dir = new File(getFilesDir(), "presets");
        if (!dir.exists()) dir.mkdirs();
        File[] files = dir.listFiles();
        List<String> names = new ArrayList<>();
        if (files != null) {
            for (File f : files) if (f.isFile() && f.getName().endsWith(".json")) names.add(f.getName().replace(".json", ""));
        }
        Collections.sort(names, String.CASE_INSENSITIVE_ORDER);
        if (names.isEmpty()) names.add("No saved profiles");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(adapter);
    }

    private void savePresetDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Save Preset As");
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Profile name");
        b.setView(input);
        b.setPositiveButton("Save", (dialog, which) -> {
            String name = input.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter a profile name", Toast.LENGTH_SHORT).show();
                return;
            }
            name = name.replaceAll("[^A-Za-z0-9._-]", "_");
            try {
                File dir = new File(getFilesDir(), "presets");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name + ".json");
                FileOutputStream fos = new FileOutputStream(f);
                fos.write(currentConfig.toString().getBytes("UTF-8"));
                fos.close();
                refreshPresets();
                Toast.makeText(this, "Preset Saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to save preset", Toast.LENGTH_SHORT).show();
            }
        });
        b.setNegativeButton("Cancel", null);
        b.show();
    }

    private void loadPresetDialog() {
        if (presetSpinner != null) {
            loadSelectedPreset();
            return;
        }
        refreshPresets();
    }

    private void loadSelectedPreset() {
        if (presetSpinner == null || presetSpinner.getSelectedItem() == null) return;
        String selected = presetSpinner.getSelectedItem().toString();
        if ("No saved profiles".equals(selected)) {
            Toast.makeText(this, "No presets found", Toast.LENGTH_SHORT).show();
            return;
        }
        loadPresetByName(selected);
    }

    private void loadPresetByName(String name) {
        try {
            File dir = new File(getFilesDir(), "presets");
            File f = new File(dir, name + ".json");
            if (!f.exists()) throw new IllegalStateException("missing");
            FileInputStream fis = new FileInputStream(f);
            byte[] data = new byte[(int) f.length()];
            int read = fis.read(data);
            fis.close();
            if (read <= 0) throw new IllegalStateException("empty");
            currentConfig = new JSONObject(new String(data, "UTF-8"));
            normalizeGrid();
            saveConfig();
            refreshVisibleControls();
            Toast.makeText(this, "Preset Loaded", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to load preset", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshVisibleControls() {
        if (gridInput != null) gridInput.setText(getGridText());
        if (unlockMasterSwitch != null) unlockMasterSwitch.setChecked(currentConfig.optBoolean("unlockEnabled", true));
        if (appAnimMasterSwitch != null) appAnimMasterSwitch.setChecked(currentConfig.optBoolean("appAnimEnabled", true));
        int mode = clamp(currentConfig.optInt("animMode", 1), 0, 1);
        if (unlockModeSpinner != null) unlockModeSpinner.setSelection(mode);
        if (unlockSlidersContainer != null) renderUnlockSliders(mode);
        if (appAnimSlidersContainer != null) renderAppAnimSliders();
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
