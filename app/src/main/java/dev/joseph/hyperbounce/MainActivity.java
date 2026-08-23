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
    private LinearLayout rootLayout;
    private LinearLayout slidersLayout;
    private Spinner presetSpinner;
    private Runnable refreshPresets;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        initializeOptimizedPreset();
        loadConfig();

        ScrollView scroll = new ScrollView(this);
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(40, 60, 40, 60);
        scroll.addView(rootLayout);
        setContentView(scroll);

        // TOP RESTART BUTTONS
        LinearLayout topButtons = new LinearLayout(this);
        topButtons.setOrientation(LinearLayout.HORIZONTAL);
        topButtons.setPadding(0, 0, 0, 40);
        
        Button btnRestartLauncher = new Button(this);
        btnRestartLauncher.setText("Restart Launcher");
        btnRestartLauncher.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        btnRestartLauncher.setOnClickListener(v -> {
            try { Runtime.getRuntime().exec("su -c killall com.miui.home"); Toast.makeText(this, "Restarted Launcher", Toast.LENGTH_SHORT).show(); } catch (Exception e) {}
        });

        Button btnRestartSystemUI = new Button(this);
        btnRestartSystemUI.setText("Restart System UI");
        btnRestartSystemUI.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        btnRestartSystemUI.setOnClickListener(v -> {
            try { Runtime.getRuntime().exec("su -c killall com.android.systemui"); Toast.makeText(this, "Restarted System UI", Toast.LENGTH_SHORT).show(); } catch (Exception e) {}
        });

        topButtons.addView(btnRestartLauncher);
        topButtons.addView(btnRestartSystemUI);
        rootLayout.addView(topButtons);

        TextView appTitle = new TextView(this);
        appTitle.setText("HyperBounce Physics Engine");
        appTitle.setTextSize(26);
        appTitle.setTypeface(null, Typeface.BOLD);
        appTitle.setTextColor(Color.BLACK);
        appTitle.setPadding(0, 0, 0, 20);
        rootLayout.addView(appTitle);

        // PRESET MANAGEMENT
        LinearLayout presetLayout = new LinearLayout(this);
        presetLayout.setOrientation(LinearLayout.HORIZONTAL);
        presetLayout.setPadding(0, 0, 0, 40);

        presetSpinner = new Spinner(this);
        presetSpinner.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button loadPresetBtn = new Button(this);
        loadPresetBtn.setText("Load");

        Button savePresetBtn = new Button(this);
        savePresetBtn.setText("Save");

        presetLayout.addView(presetSpinner);
        presetLayout.addView(loadPresetBtn);
        presetLayout.addView(savePresetBtn);
        rootLayout.addView(presetLayout);

        refreshPresets = () -> {
            File dir = new File(getFilesDir(), "presets");
            if (!dir.exists()) dir.mkdirs();
            File[] files = dir.listFiles();
            List<String> names = new ArrayList<>();
            if (files != null) for (File f : files) if (f.getName().endsWith(".json")) names.add(f.getName().replace(".json", ""));
            ArrayAdapter<String> presetAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names);
            presetSpinner.setAdapter(presetAdapter);
        };
        refreshPresets.run();

        savePresetBtn.setOnClickListener(v -> {
            AlertDialog.Builder b = new AlertDialog.Builder(this);
            b.setTitle("Save Preset As");
            final EditText input = new EditText(this);
            b.setView(input);
            b.setPositiveButton("Save", (dialog, which) -> {
                try {
                    File f = new File(getFilesDir() + "/presets", input.getText().toString() + ".json");
                    FileOutputStream fos = new FileOutputStream(f);
                    fos.write(currentConfig.toString().getBytes("UTF-8"));
                    fos.close();
                    refreshPresets.run();
                    Toast.makeText(this, "Preset Saved", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {}
            });
            b.setNegativeButton("Cancel", null);
            b.show();
        });

        loadPresetBtn.setOnClickListener(v -> {
            if (presetSpinner.getSelectedItem() != null) {
                try {
                    File f = new File(getFilesDir() + "/presets", presetSpinner.getSelectedItem().toString() + ".json");
                    FileInputStream fis = new FileInputStream(f);
                    byte[] data = new byte[(int) f.length()];
                    fis.read(data);
                    fis.close();
                    currentConfig = new JSONObject(new String(data, "UTF-8"));
                    saveConfig();
                    renderSliders(currentConfig.optInt("animMode", 2));
                    Toast.makeText(this, "Preset Loaded", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {}
            }
        });

        Spinner modeSpinner = new Spinner(this);
        String[] modes = { 
            "Mode 0: Unlock - iOS Radial Dispersion", 
            "Mode 1: Unlock - Kinetic Y-Axis Cascade",
            "Mode 2: App Transitions & 3D Spatial Trajectory" 
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        modeSpinner.setAdapter(adapter);
        modeSpinner.setSelection(currentConfig.optInt("animMode", 2));
        modeSpinner.setPadding(0, 10, 0, 40);
        rootLayout.addView(modeSpinner);

        slidersLayout = new LinearLayout(this);
        slidersLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(slidersLayout);

        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try { currentConfig.put("animMode", position); saveConfig(); renderSliders(position); } catch (Exception e) {}
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void renderSliders(int mode) {
        slidersLayout.removeAllViews();
        
        if (mode == 0) {
            addSectionHeader("CLASSIC RADIAL UNLOCK");
            addSlider("centerDamping", "Center Damping", "(Lower = High rubber-band bounce | Higher = Stiff snap)", 0.1f, 1.0f, 0.58f);
            addSlider("centerResponse", "Center Response", "(Lower = Fast snap | Higher = Slow float)", 0.1f, 1.5f, 0.48f);
            addSlider("edgeDampingDrop", "Edge Damping Drop", "(Higher = Edges significantly bouncier than center)", 0.0f, 0.5f, 0.10f);
            addSlider("edgeResponseDelay", "Edge Response Delay", "(Higher = Outer edges lag behind center)", 0.0f, 1.0f, 0.22f);
            addSlider("zDepthMultiplier", "Z-Depth Scale", "(Higher = Icons spawn deeper in 3D space)", 1.0f, 4.0f, 1.60f);
        } else if (mode == 1) {
            addSectionHeader("KINETIC Y-AXIS UNLOCK GLOBALS");
            addSlider("durationScale", "Global Anim Duration", "(Lower = Fast action | Higher = Cinematic slow-motion)", 0.1f, 3.0f, 1.0f);
            addSlider("velocityInfluence", "Velocity Impact", "(Lower = Ignores swipe flick | Higher = Massive kinetic bounce)", 0.0f, 2.0f, 1.0f);
            addSlider("cascadeDamping", "Resting Damping", "(Lower = Loose bounce | Higher = Rigid iOS stop)", 0.5f, 1.0f, 0.95f);

            addSectionHeader("DOCK ISOLATION");
            addSlider("kineticDockDamping", "Dock Damping", "(Lower = Bouncy dock | Higher = Solid dock)", 0.1f, 1.0f, 0.85f);
            addSlider("kineticDockResponse", "Dock Response", "(Lower = Faster snap | Higher = Slower float)", 0.1f, 1.5f, 0.38f);
            addSlider("kineticDockZDepth", "Dock Z-Depth", "(Higher = Dock travels from deeper Z)", 0.0f, 4000.0f, 800.0f);

            int rows = currentConfig.optInt("gridRows", 7);
            addSectionHeader("ROW Z-DEPTH (PERSPECTIVE TRAVEL)");
            for (int i = 0; i < rows; i++) {
                addSlider("r" + i + "_z", "Row " + i + " Z-Depth", "(Lower = Flat | Higher = Deep 3D origin)", 100.0f, 4000.0f, 2000.0f - (i * 200));
            }
        } else {
            // MODE 2: APP OPEN, CLOSE & 3D SPATIAL TRAJECTORY
            addSectionHeader("SPATIAL TRAJECTORY & 3D DEPTH");
            addSlider("appYArc", "Vertical Parabolic Arc", "(Higher = App floats upward into air before diving into icon | Zero = Straight line)", -200.0f, 300.0f, 120.0f);
            addSlider("appBottomZWarp", "3D Bottom-Edge Compression", "(Higher = Bottom card compresses fast, creating 3D backward tilt | Zero = Flat 2D)", 0.0f, 0.70f, 0.28f);
            addSlider("appXSway", "Horizontal Sway Curve", "(Non-zero = App arcs laterally towards center during flight)", -150.0f, 150.0f, 0.0f);
            addSlider("appInterruptSmooth", "Gesture Interrupt Stabilizer", "(Lower = Hard cutoff | Higher = Smooth recovery on fast flicks / reversals)", 0.1f, 1.5f, 0.85f);

            addSectionHeader("SPATIAL FLIGHT & MASS DYNAMICS");
            addSlider("appTravelDamping", "Flight Path Damping", "(Lower = Window overshoots landing spot and bounces | Higher = Solid snap)", 0.30f, 1.0f, 0.60f);
            addSlider("appTravelStiffness", "Flight Path Stiffness", "(Lower = Heavy, fluid, floating mass | Higher = High-velocity snap)", 40.0f, 600.0f, 140.0f);

            addSectionHeader("WINDOW SIZE & EXPANSION BOUNCE");
            addSlider("appSizeDamping", "Window Scale Damping", "(Lower = App over-expands past screen on open & bounces back | Higher = Rigid)", 0.30f, 1.0f, 0.58f);
            addSlider("appSizeStiffness", "Window Scale Stiffness", "(Lower = Slow, heavy expansion | Higher = Instant resize)", 40.0f, 600.0f, 150.0f);

            addSectionHeader("SURFACE MORPH & 3D PITCH");
            addSlider("appCornerDamping", "Corner Morph Damping", "(Lower = Corner radius rubber-bands | Higher = Clean squircle morph)", 0.30f, 1.0f, 0.75f);
            addSlider("appCornerStiffness", "Corner Morph Stiffness", "(Lower = Corners round slowly | Higher = Snaps instantly)", 50.0f, 800.0f, 200.0f);
            addSlider("appIconPitchDeg", "Ghost Icon 3D Tilt Angle", "(Higher = Icon card physically tilts along X-axis during transit | Zero = Flat)", 0.0f, 45.0f, 18.0f);
            addSlider("appIconCameraDist", "3D Camera Distance", "(Lower = Dramatic fish-eye perspective | Higher = Subtle orthographic tilt)", 2000.0f, 15000.0f, 8000.0f);
            addSlider("appAlphaDamping", "Surface Fade Damping", "(Lower = Icon ghost visible longer | Higher = Fast crossfade)", 0.40f, 1.0f, 0.92f);
        }
    }

    private void initializeOptimizedPreset() {
        try {
            File dir = new File(getFilesDir(), "presets");
            if (!dir.exists()) dir.mkdirs();
            File opt = new File(dir, "Optimized.json");
            if (!opt.exists()) {
                JSONObject json = new JSONObject();
                json.put("animMode", 2);
                json.put("appYArc", 120.0);
                json.put("appBottomZWarp", 0.28);
                json.put("appXSway", 0.0);
                json.put("appInterruptSmooth", 0.85);
                json.put("appTravelDamping", 0.60);
                json.put("appTravelStiffness", 140.0);
                json.put("appSizeDamping", 0.58);
                json.put("appSizeStiffness", 150.0);
                json.put("appCornerDamping", 0.75);
                json.put("appCornerStiffness", 200.0);
                json.put("appIconPitchDeg", 18.0);
                json.put("appIconCameraDist", 8000.0);
                json.put("appAlphaDamping", 0.92);

                FileOutputStream fos = new FileOutputStream(opt);
                fos.write(json.toString().getBytes("UTF-8"));
                fos.close();
            }
        } catch (Exception ignored) {}
    }

    private void addSectionHeader(String titleText) {
        TextView header = new TextView(this);
        header.setText(titleText);
        header.setTextSize(13);
        header.setTypeface(null, Typeface.BOLD);
        header.setTextColor(Color.WHITE);
        header.setBackgroundColor(Color.parseColor("#1E88E5"));
        header.setPadding(20, 15, 20, 15);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 40, 0, 20);
        header.setLayoutParams(params);
        slidersLayout.addView(header);
    }

    private void loadConfig() {
        try {
            File file = new File(getFilesDir(), "config.json");
            if (file.exists()) {
                FileInputStream fis = new FileInputStream(file);
                byte[] data = new byte[(int) file.length()];
                fis.read(data);
                fis.close();
                currentConfig = new JSONObject(new String(data, "UTF-8"));
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

    private void addSlider(String key, String label, String desc, float min, float max, float defVal) {
        float savedVal = (float) currentConfig.optDouble(key, defVal);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 10, 0, 30);

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
            descText.setPadding(0, 0, 0, 10);
            row.addView(descText);
        }

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        if (savedVal > max) seekBar.setProgress(1000);
        else if (savedVal < min) seekBar.setProgress(0);
        else seekBar.setProgress((int) (((savedVal - min) / (max - min)) * 1000));

        Button resetBtn = new Button(this);
        resetBtn.setText("Rst");
        controls.addView(seekBar);
        controls.addView(resetBtn);
        row.addView(controls);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                if (fromUser) {
                    float newVal = min + ((progress / 1000f) * (max - min));
                    valText.setText(String.format("%.3f", newVal));
                    try { currentConfig.put(key, newVal); } catch (Exception ignored) {}
                    saveConfig();
                }
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
                    seekBar.setProgress((int) (((newVal - min) / (max - min)) * 1000));
                } catch (Exception ignored) {}
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
            return true;
        };

        row.setOnLongClickListener(manualInput);
        valText.setOnLongClickListener(manualInput);
        slidersLayout.addView(row);
    }
}
