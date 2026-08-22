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

        // TOP BUTTONS
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
        appTitle.setText("HyperBounce Engine V2");
        appTitle.setTextSize(26);
        appTitle.setTypeface(null, Typeface.BOLD);
        appTitle.setTextColor(Color.BLACK);
        appTitle.setPadding(0, 0, 0, 20);
        rootLayout.addView(appTitle);

        // GRID INPUT SYSTEM
        LinearLayout gridLayout = new LinearLayout(this);
        gridLayout.setOrientation(LinearLayout.HORIZONTAL);
        gridLayout.setPadding(0, 0, 0, 40);

        EditText gridInput = new EditText(this);
        gridInput.setHint("Grid e.g. 4x7");
        gridInput.setText(currentConfig.optInt("gridColumns", 4) + "x" + currentConfig.optInt("gridRows", 7));
        gridInput.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button updateGridBtn = new Button(this);
        updateGridBtn.setText("Apply Grid");
        updateGridBtn.setOnClickListener(v -> {
            String[] parts = gridInput.getText().toString().toLowerCase().split("x");
            if (parts.length == 2) {
                try {
                    currentConfig.put("gridColumns", Integer.parseInt(parts[0].trim()));
                    currentConfig.put("gridRows", Integer.parseInt(parts[1].trim()));
                    saveConfig();
                    renderSliders(currentConfig.optInt("animMode", 1));
                    Toast.makeText(this, "Grid Updated", Toast.LENGTH_SHORT).show();
                } catch (Exception e) { Toast.makeText(this, "Invalid format", Toast.LENGTH_SHORT).show(); }
            }
        });
        gridLayout.addView(gridInput);
        gridLayout.addView(updateGridBtn);
        rootLayout.addView(gridLayout);

        // PRESET SYSTEM
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
                    gridInput.setText(currentConfig.optInt("gridColumns", 4) + "x" + currentConfig.optInt("gridRows", 7));
                    saveConfig();
                    renderSliders(currentConfig.optInt("animMode", 1));
                    Toast.makeText(this, "Preset Loaded", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {}
            }
        });

        Spinner modeSpinner = new Spinner(this);
        String[] modes = { "Mode 0: iOS Radial Dispersion", "Mode 1: Kinetic Y-Axis Cascade (Granular)" };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        modeSpinner.setAdapter(adapter);
        modeSpinner.setSelection(currentConfig.optInt("animMode", 1));
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
            addSectionHeader("CLASSIC SETTINGS");
            addSlider("centerDamping", "Center Damping", "", 0.1f, 1.0f, 0.58f);
            addSlider("centerResponse", "Center Response", "", 0.1f, 1.5f, 0.48f);
            addSlider("edgeDampingDrop", "Edge Damping Drop", "", 0.0f, 0.5f, 0.10f);
            addSlider("edgeResponseDelay", "Edge Response Delay", "", 0.0f, 1.0f, 0.22f);
            addSlider("minDamping", "Min Damping Bound", "", 0.1f, 1.0f, 0.42f);
            addSlider("maxDamping", "Max Damping Bound", "", 0.1f, 1.0f, 0.65f);
            addSlider("zDepthMultiplier", "Z-Depth Scale", "", 1.0f, 4.0f, 1.60f);
        } else {
            addSectionHeader("GLOBAL MULTIPLIERS");
            addSlider("durationScale", "Global Anim Duration", "", 0.1f, 3.0f, 1.0f);
            addSlider("velocityInfluence", "Velocity Impact", "", 0.0f, 2.0f, 1.0f);
            addSlider("maxVelocity", "Max Swipe Normalizer", "", 5000.0f, 30000.0f, 16000.0f);
            addSlider("cascadeDamping", "Resting Damping", "", 0.5f, 1.0f, 0.95f);

            addSectionHeader("DOCK ISOLATION");
            addSlider("kineticDockDamping", "Dock Damping", "", 0.1f, 1.0f, 0.85f);
            addSlider("kineticDockResponse", "Dock Response", "", 0.1f, 1.5f, 0.38f);
            addSlider("kineticDockZDepth", "Dock Z-Depth", "", 0.0f, 4000.0f, 800.0f);

            int rows = currentConfig.optInt("gridRows", 7);
            
            addSectionHeader("DYNAMIC ROW-BY-ROW Z-DEPTH");
            for (int i = 0; i < rows; i++) addSlider("r" + i + "_z", "Row " + i + " Z-Depth", "", 100.0f, 4000.0f, 2000.0f - (i * 200));

            addSectionHeader("DYNAMIC VELOCITY BOUNCE (CENTER VS OUTER)");
            for (int i = 0; i < rows; i++) {
                addSlider("r" + i + "_center_bounce", "Row " + i + " Center Bounce", "", 0.0f, 1.0f, 0.45f - (i * 0.05f));
                addSlider("r" + i + "_outer_bounce", "Row " + i + " Outer Bounce", "", 0.0f, 1.0f, 0.45f - (i * 0.05f));
            }

            addSectionHeader("DYNAMIC SETTLING TIME / RESPONSE (CENTER VS OUTER)");
            for (int i = 0; i < rows; i++) {
                addSlider("r" + i + "_center_settle", "Row " + i + " Center Settling Time", "", 0.1f, 1.5f, 0.55f + (i * 0.04f));
                addSlider("r" + i + "_outer_settle", "Row " + i + " Outer Settling Time", "", 0.1f, 1.5f, 0.55f + (i * 0.04f));
            }
        }
    }

    private void initializeOptimizedPreset() {
        try {
            File dir = new File(getFilesDir(), "presets");
            if (!dir.exists()) dir.mkdirs();
            File opt = new File(dir, "Optimized.json");
            if (!opt.exists()) {
                JSONObject json = new JSONObject();
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

                json.put("r0_z", 3157.6); json.put("r1_z", 3032.8); json.put("r2_z", 2849.5);
                json.put("r3_z", 2599.9); json.put("r4_z", 2350.3); json.put("r5_z", 2170.9); json.put("r6_z", 2057.8);

                double[] bounces = {0.450, 0.473, 0.474, 0.469, 0.471, 0.459, 0.463};
                for (int i = 0; i < 7; i++) {
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
        } catch (Exception e) {}
    }

    private void addSectionHeader(String titleText) {
        TextView header = new TextView(this);
        header.setText(titleText);
        header.setTextSize(14);
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
        } catch (Exception e) {}
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
        } catch (Exception e) {}
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
                    try { currentConfig.put(key, newVal); } catch (Exception e) {}
                    saveConfig();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        resetBtn.setOnClickListener(v -> {
            try { currentConfig.put(key, defVal); } catch (Exception e) {}
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
                } catch (Exception e) {}
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
