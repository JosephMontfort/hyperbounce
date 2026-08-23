package dev.joseph.hyperbounce;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
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
import java.util.List;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private JSONObject currentConfig = new JSONObject();
    
    // View Hierarchies
    private LinearLayout rootLayout;
    private LinearLayout menuLayout;
    private LinearLayout unlockPageLayout;
    private LinearLayout appAnimPageLayout;
    private LinearLayout unlockSlidersContainer;
    private LinearLayout appAnimSlidersContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        loadConfig();

        ScrollView scroll = new ScrollView(this);
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(40, 60, 40, 60);
        scroll.addView(rootLayout);
        setContentView(scroll);

        // Header & Quick Actions Dropdown
        setupHeader();

        // Initialize the 3 main views
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
    }

    private void setupHeader() {
        TextView appTitle = new TextView(this);
        appTitle.setText("HyperBounce");
        appTitle.setTextSize(28);
        appTitle.setTypeface(null, Typeface.BOLD);
        appTitle.setTextColor(Color.BLACK);
        appTitle.setPadding(0, 0, 0, 10);
        rootLayout.addView(appTitle);

        Spinner actionSpinner = new Spinner(this);
        String[] actions = { "⚡ Quick Actions...", "Restart Launcher", "Restart System UI", "Save Current Preset", "Load Preset" };
        ArrayAdapter<String> actionAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, actions);
        actionSpinner.setAdapter(actionAdapter);
        actionSpinner.setPadding(0, 0, 0, 40);
        
        actionSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 1) {
                    try { Runtime.getRuntime().exec("su -c killall com.miui.home"); Toast.makeText(MainActivity.this, "Launcher Restarted", Toast.LENGTH_SHORT).show(); } catch (Exception e) {}
                } else if (position == 2) {
                    try { Runtime.getRuntime().exec("su -c killall com.android.systemui"); Toast.makeText(MainActivity.this, "SystemUI Restarted", Toast.LENGTH_SHORT).show(); } catch (Exception e) {}
                } else if (position == 3) {
                    savePresetDialog();
                } else if (position == 4) {
                    loadPresetDialog();
                }
                actionSpinner.setSelection(0); // Reset
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        rootLayout.addView(actionSpinner);
    }

    private void setupMainMenu() {
        TextView desc = new TextView(this);
        desc.setText("Select a module below to configure physics parameters.");
        desc.setPadding(0, 0, 0, 40);
        menuLayout.addView(desc);

        Button btnUnlock = new Button(this);
        btnUnlock.setText("Screen Unlock Animations");
        btnUnlock.setTextSize(16);
        btnUnlock.setPadding(0, 40, 0, 40);
        btnUnlock.setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            unlockPageLayout.setVisibility(View.VISIBLE);
        });

        Button btnAppAnim = new Button(this);
        btnAppAnim.setText("App Open / Close & Recents");
        btnAppAnim.setTextSize(16);
        btnAppAnim.setPadding(0, 40, 0, 40);
        btnAppAnim.setOnClickListener(v -> {
            menuLayout.setVisibility(View.GONE);
            appAnimPageLayout.setVisibility(View.VISIBLE);
        });

        menuLayout.addView(btnUnlock);
        menuLayout.addView(btnAppAnim);
    }

    private void setupUnlockPage() {
        Switch masterSwitch = new Switch(this);
        masterSwitch.setText("Enable Unlock Animation Hook");
        masterSwitch.setTextSize(18);
        masterSwitch.setTypeface(null, Typeface.BOLD);
        masterSwitch.setChecked(currentConfig.optBoolean("unlockEnabled", true));
        masterSwitch.setPadding(0, 0, 0, 40);
        masterSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            try { currentConfig.put("unlockEnabled", isChecked); saveConfig(); } catch (Exception e) {}
        });
        unlockPageLayout.addView(masterSwitch);

        Spinner modeSpinner = new Spinner(this);
        String[] modes = { "Mode 0: iOS Radial Dispersion", "Mode 1: Kinetic Y-Axis Cascade" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        modeSpinner.setAdapter(adapter);
        modeSpinner.setSelection(currentConfig.optInt("animMode", 1));
        modeSpinner.setPadding(0, 10, 0, 40);
        unlockPageLayout.addView(modeSpinner);

        unlockSlidersContainer = new LinearLayout(this);
        unlockSlidersContainer.setOrientation(LinearLayout.VERTICAL);
        unlockPageLayout.addView(unlockSlidersContainer);

        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try { currentConfig.put("animMode", position); saveConfig(); renderUnlockSliders(position); } catch (Exception e) {}
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        renderUnlockSliders(currentConfig.optInt("animMode", 1));
    }

    private void setupAppAnimPage() {
        Switch masterSwitch = new Switch(this);
        masterSwitch.setText("Enable App Transition Hooks");
        masterSwitch.setTextSize(18);
        masterSwitch.setTypeface(null, Typeface.BOLD);
        masterSwitch.setChecked(currentConfig.optBoolean("appAnimEnabled", true));
        masterSwitch.setPadding(0, 0, 0, 40);
        masterSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
            try { currentConfig.put("appAnimEnabled", isChecked); saveConfig(); } catch (Exception e) {}
        });
        appAnimPageLayout.addView(masterSwitch);

        appAnimSlidersContainer = new LinearLayout(this);
        appAnimSlidersContainer.setOrientation(LinearLayout.VERTICAL);
        appAnimPageLayout.addView(appAnimSlidersContainer);

        renderAppAnimSliders();
    }

    private void renderUnlockSliders(int mode) {
        unlockSlidersContainer.removeAllViews();
        if (mode == 0) {
            addSectionHeader(unlockSlidersContainer, "CLASSIC RADIAL UNLOCK");
            addSlider(unlockSlidersContainer, "centerDamping", "Center Damping", "(Lower = High rubber-band bounce | Higher = Stiff snap)", 0.1f, 1.0f, 0.58f);
            addSlider(unlockSlidersContainer, "centerResponse", "Center Response", "(Lower = Fast snap | Higher = Slow float)", 0.1f, 1.5f, 0.48f);
            addSlider(unlockSlidersContainer, "edgeDampingDrop", "Edge Damping Drop", "(Higher = Edges significantly bouncier than center)", 0.0f, 0.5f, 0.10f);
            addSlider(unlockSlidersContainer, "edgeResponseDelay", "Edge Response Delay", "(Higher = Outer edges lag behind center)", 0.0f, 1.0f, 0.22f);
            addSlider(unlockSlidersContainer, "zDepthMultiplier", "Z-Depth Scale", "(Higher = Icons spawn deeper in 3D space)", 1.0f, 4.0f, 1.60f);
        } else {
            addSectionHeader(unlockSlidersContainer, "KINETIC Y-AXIS UNLOCK GLOBALS");
            addSlider(unlockSlidersContainer, "durationScale", "Global Anim Duration", "(Lower = Fast action | Higher = Cinematic slow-motion)", 0.1f, 3.0f, 1.0f);
            addSlider(unlockSlidersContainer, "velocityInfluence", "Velocity Impact", "(Lower = Ignores swipe flick | Higher = Massive kinetic bounce)", 0.0f, 2.0f, 1.0f);
            addSlider(unlockSlidersContainer, "cascadeDamping", "Resting Damping", "(Lower = Loose bounce | Higher = Rigid iOS stop)", 0.5f, 1.0f, 0.95f);

            addSectionHeader(unlockSlidersContainer, "DOCK ISOLATION");
            addSlider(unlockSlidersContainer, "kineticDockDamping", "Dock Damping", "(Lower = Bouncy dock | Higher = Solid dock)", 0.1f, 1.0f, 0.85f);
            addSlider(unlockSlidersContainer, "kineticDockResponse", "Dock Response", "(Lower = Faster snap | Higher = Slower float)", 0.1f, 1.5f, 0.38f);
            addSlider(unlockSlidersContainer, "kineticDockZDepth", "Dock Z-Depth", "(Higher = Dock travels from deeper Z)", 0.0f, 4000.0f, 800.0f);

            int rows = currentConfig.optInt("gridRows", 7);
            addSectionHeader(unlockSlidersContainer, "ROW Z-DEPTH (PERSPECTIVE TRAVEL)");
            for (int i = 0; i < rows; i++) {
                addSlider(unlockSlidersContainer, "r" + i + "_z", "Row " + i + " Z-Depth", "(Lower = Flat | Higher = Deep 3D origin)", 100.0f, 4000.0f, 2000.0f - (i * 200));
            }
        }
    }

    private void renderAppAnimSliders() {
        appAnimSlidersContainer.removeAllViews();
        addSectionHeader(appAnimSlidersContainer, "SPATIAL TRAJECTORY & 3D DEPTH");
        addSlider(appAnimSlidersContainer, "appYArc", "Vertical Parabolic Arc", "(Higher = App floats upward into air before diving into icon | Zero = Straight line)", -200.0f, 300.0f, 120.0f);
        addSlider(appAnimSlidersContainer, "appBottomZWarp", "3D Bottom-Edge Compression", "(Higher = Bottom card compresses fast, creating 3D backward tilt | Zero = Flat 2D)", 0.0f, 0.70f, 0.28f);
        addSlider(appAnimSlidersContainer, "appXSway", "Horizontal Sway Curve", "(Non-zero = App arcs laterally towards center during flight)", -150.0f, 150.0f, 0.0f);
        addSlider(appAnimSlidersContainer, "appInterruptSmooth", "Gesture Interrupt Stabilizer", "(Lower = Hard cutoff | Higher = Smooth recovery on fast flicks / reversals)", 0.1f, 1.5f, 0.85f);

        addSectionHeader(appAnimSlidersContainer, "SPATIAL FLIGHT & MASS DYNAMICS");
        addSlider(appAnimSlidersContainer, "appTravelDamping", "Flight Path Damping", "(Lower = Window overshoots landing spot and bounces | Higher = Solid snap)", 0.30f, 1.0f, 0.60f);
        addSlider(appAnimSlidersContainer, "appTravelStiffness", "Flight Path Stiffness", "(Lower = Heavy, fluid, floating mass | Higher = High-velocity snap)", 40.0f, 600.0f, 140.0f);

        addSectionHeader(appAnimSlidersContainer, "WINDOW SIZE & EXPANSION BOUNCE");
        addSlider(appAnimSlidersContainer, "appSizeDamping", "Window Scale Damping", "(Lower = App over-expands past screen on open & bounces back | Higher = Rigid)", 0.30f, 1.0f, 0.58f);
        addSlider(appAnimSlidersContainer, "appSizeStiffness", "Window Scale Stiffness", "(Lower = Slow, heavy expansion | Higher = Instant resize)", 40.0f, 600.0f, 150.0f);

        addSectionHeader(appAnimSlidersContainer, "SURFACE MORPH & 3D PITCH");
        addSlider(appAnimSlidersContainer, "appCornerDamping", "Corner Morph Damping", "(Lower = Corner radius rubber-bands | Higher = Clean squircle morph)", 0.30f, 1.0f, 0.75f);
        addSlider(appAnimSlidersContainer, "appCornerStiffness", "Corner Morph Stiffness", "(Lower = Corners round slowly | Higher = Snaps instantly)", 50.0f, 800.0f, 200.0f);
        addSlider(appAnimSlidersContainer, "appIconPitchDeg", "Ghost Icon 3D Tilt Angle", "(Higher = Icon card physically tilts along X-axis during transit | Zero = Flat)", 0.0f, 45.0f, 18.0f);
        addSlider(appAnimSlidersContainer, "appIconCameraDist", "3D Camera Distance", "(Lower = Dramatic fish-eye perspective | Higher = Subtle orthographic tilt)", 2000.0f, 15000.0f, 8000.0f);
    }

    @Override
    public void onBackPressed() {
        if (unlockPageLayout.getVisibility() == View.VISIBLE || appAnimPageLayout.getVisibility() == View.VISIBLE) {
            unlockPageLayout.setVisibility(View.GONE);
            appAnimPageLayout.setVisibility(View.GONE);
            menuLayout.setVisibility(View.VISIBLE);
        } else {
            super.onBackPressed();
        }
    }

    // Boilerplate config loading and save methods
    private void savePresetDialog() {
        AlertDialog.Builder b = new AlertDialog.Builder(this); b.setTitle("Save Preset As");
        final EditText input = new EditText(this); b.setView(input);
        b.setPositiveButton("Save", (dialog, which) -> {
            try {
                File dir = new File(getFilesDir(), "presets"); if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, input.getText().toString() + ".json");
                FileOutputStream fos = new FileOutputStream(f); fos.write(currentConfig.toString().getBytes("UTF-8")); fos.close();
                Toast.makeText(this, "Preset Saved", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {}
        }); b.setNegativeButton("Cancel", null); b.show();
    }

    private void loadPresetDialog() {
        File dir = new File(getFilesDir(), "presets"); if (!dir.exists()) dir.mkdirs();
        File[] files = dir.listFiles(); List<String> names = new ArrayList<>();
        if (files != null) for (File f : files) if (f.getName().endsWith(".json")) names.add(f.getName().replace(".json", ""));
        if (names.isEmpty()) { Toast.makeText(this, "No presets found", Toast.LENGTH_SHORT).show(); return; }
        AlertDialog.Builder b = new AlertDialog.Builder(this); b.setTitle("Select Preset");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, names);
        b.setAdapter(adapter, (dialog, which) -> {
            try {
                File f = new File(dir, names.get(which) + ".json");
                FileInputStream fis = new FileInputStream(f); byte[] data = new byte[(int) f.length()]; fis.read(data); fis.close();
                currentConfig = new JSONObject(new String(data, "UTF-8")); saveConfig();
                renderUnlockSliders(currentConfig.optInt("animMode", 1)); renderAppAnimSliders();
                Toast.makeText(this, "Preset Loaded", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {}
        }); b.show();
    }

    private void loadConfig() {
        try {
            File file = new File(getFilesDir(), "config.json");
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file); byte[] data = new byte[(int) file.length()];
                fis.read(data); fis.close(); currentConfig = new JSONObject(new String(data, "UTF-8"));
            }
        } catch (Exception ignored) {}
    }

    private void saveConfig() {
        try {
            File file = new File(getFilesDir(), "config.json"); FileOutputStream fos = new FileOutputStream(file);
            fos.write(currentConfig.toString().getBytes("UTF-8")); fos.flush(); fos.close();
            Intent intent = new Intent("dev.joseph.hyperbounce.ACTION_CONFIG_CHANGED");
            intent.putExtra("config_json", currentConfig.toString()); sendBroadcast(intent);
        } catch (Exception ignored) {}
    }

    private void addSectionHeader(LinearLayout parent, String titleText) {
        TextView header = new TextView(this); header.setText(titleText); header.setTextSize(13); header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(Color.WHITE); header.setBackgroundColor(Color.parseColor("#1E88E5")); header.setPadding(20, 15, 20, 15);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 40, 0, 20); header.setLayoutParams(params); parent.addView(header);
    }

    private void addSlider(LinearLayout parent, String key, String label, String desc, float min, float max, float defVal) {
        float savedVal = (float) currentConfig.optDouble(key, defVal);
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL); row.setPadding(0, 10, 0, 30);
        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(this); title.setText(label); title.setTextSize(15); title.setTypeface(null, Typeface.BOLD); title.setTextColor(Color.BLACK); title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView valText = new TextView(this); valText.setText(String.format("%.3f", savedVal)); valText.setTextColor(Color.parseColor("#D81B60")); valText.setTextSize(15);
        header.addView(title); header.addView(valText); row.addView(header);
        if (!desc.isEmpty()) { TextView descText = new TextView(this); descText.setText(desc); descText.setTextSize(11); descText.setTextColor(Color.DKGRAY); descText.setPadding(0, 0, 0, 10); row.addView(descText); }
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.HORIZONTAL); controls.setGravity(Gravity.CENTER_VERTICAL);
        SeekBar seekBar = new SeekBar(this); seekBar.setMax(1000); seekBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (savedVal > max) seekBar.setProgress(1000); else if (savedVal < min) seekBar.setProgress(0); else seekBar.setProgress((int) (((savedVal - min) / (max - min)) * 1000));
        Button resetBtn = new Button(this); resetBtn.setText("Rst"); controls.addView(seekBar); controls.addView(resetBtn); row.addView(controls);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) { float newVal = min + ((progress / 1000f) * (max - min)); valText.setText(String.format("%.3f", newVal));
                    try { currentConfig.put(key, newVal); } catch (Exception ignored) {} saveConfig(); }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {} @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        resetBtn.setOnClickListener(v -> { try { currentConfig.put(key, defVal); } catch (Exception e) {} saveConfig(); valText.setText(String.format("%.3f", defVal)); seekBar.setProgress((int) (((defVal - min) / (max - min)) * 1000)); });
        parent.addView(row);
    }
}
