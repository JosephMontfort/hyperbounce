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
import org.json.JSONObject;

public class MainActivity extends Activity {
    private JSONObject currentConfig = new JSONObject();
    private LinearLayout rootLayout;
    private LinearLayout slidersLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // HIDE ANDROID ACTION BAR FOR CLEAN LOOK
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        loadConfig();

        ScrollView scroll = new ScrollView(this);
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(40, 60, 40, 60);
        scroll.addView(rootLayout);
        setContentView(scroll);

        // TOP RESTART BUTTONS (Split as requested)
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

        Spinner modeSpinner = new Spinner(this);
        String[] modes = {
            "Mode 0: iOS Radial Dispersion (Original)",
            "Mode 1: Kinetic Y-Axis Cascade (Granular)"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        modeSpinner.setAdapter(adapter);
        modeSpinner.setSelection(currentConfig.optInt("animMode", 1));
        modeSpinner.setPadding(0, 10, 0, 40);
        rootLayout.addView(modeSpinner);

        slidersLayout = new LinearLayout(this);
        slidersLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.addView(slidersLayout);

        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                try {
                    currentConfig.put("animMode", position);
                    saveConfig();
                    renderSliders(position);
                } catch (Exception e) {}
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void renderSliders(int mode) {
        slidersLayout.removeAllViews();
        
        if (mode == 0) {
            addSectionHeader("CLASSIC SETTINGS");
            addSlider("centerDamping", "Center Damping", "(Lower = Rubber-band bouncy | Higher = Stiff/No bounce)", 0.1f, 1.0f, 0.58f);
            addSlider("centerResponse", "Center Response", "(Lower = Snaps fast | Higher = Floats slowly)", 0.1f, 1.5f, 0.48f);
            addSlider("edgeDampingDrop", "Edge Damping Drop", "(Higher = Edges are bouncier than center)", 0.0f, 0.5f, 0.10f);
            addSlider("edgeResponseDelay", "Edge Response Delay", "(Higher = Edges lag behind center more)", 0.0f, 1.0f, 0.22f);
            addSlider("minDamping", "Min Damping Bound", "(Safety limit for extreme bouncing)", 0.1f, 1.0f, 0.42f);
            addSlider("maxDamping", "Max Damping Bound", "(Safety limit for extreme stiffness)", 0.1f, 1.0f, 0.65f);
            addSlider("zDepthMultiplier", "Z-Depth Scale", "(Higher = Apps fly in from deeper 3D space)", 1.0f, 4.0f, 1.60f);
        } else {
            addSectionHeader("GLOBAL MULTIPLIERS");
            addSlider("durationScale", "Global Anim Duration", "(Lower = Fast | 1.0 = Normal | Higher = Slow Motion)", 0.1f, 3.0f, 1.0f);
            addSlider("velocityInfluence", "Velocity Impact", "(Lower = Ignores swipe speed | Higher = Extreme speed reaction)", 0.0f, 2.0f, 1.0f);
            addSlider("maxVelocity", "Max Swipe Normalizer", "(Lower = Easily triggers max bounce | Higher = Requires violent flick)", 5000.0f, 30000.0f, 16000.0f);

            addSectionHeader("CASCADE TIMING (THE WAVE)");
            addSlider("cascadeDamping", "Resting Damping", "(Lower = Always bouncy | Higher = Solid iOS resting state)", 0.5f, 1.0f, 0.95f);
            addSlider("cascadeBaseResponse", "Bottom Row Base Speed", "(Lower = Bottom row settles fast | Higher = Bottom row settles slow)", 0.2f, 1.5f, 0.55f);
            addSlider("cascadeRowStagger", "Row Stagger Delay", "(Higher = More delay between rows. Creates the Y-Axis wave)", 0.0f, 0.10f, 0.040f);

            addSectionHeader("DOCK ISOLATION");
            addSlider("kineticDockDamping", "Dock Damping", "(Lower = Bouncy Dock | Higher = Stiff Dock)", 0.1f, 1.0f, 0.85f);
            addSlider("kineticDockResponse", "Dock Response", "(Lower = Snaps fast | Higher = Settles slow)", 0.1f, 1.5f, 0.38f);
            addSlider("kineticDockZDepth", "Dock Z-Depth", "(Higher = Dock flies in from deeper 3D space)", 100.0f, 2000.0f, 800.0f);

            addSectionHeader("ROW-BY-ROW Z-DEPTH (FLATNESS FIX)");
            TextView zInfo = new TextView(this);
            zInfo.setText("Higher Value = Icon spawns deeper in the screen. If bottom rows look 'flat', crank their Z-Depth up to 1500+.");
            zInfo.setTextColor(Color.DKGRAY);
            zInfo.setPadding(0, 0, 0, 20);
            slidersLayout.addView(zInfo);

            addSlider("r0Z", "Row 0 (Top) Z-Depth", "", 100.0f, 4000.0f, 2000.0f);
            addSlider("r1Z", "Row 1 Z-Depth", "", 100.0f, 4000.0f, 1600.0f);
            addSlider("r2Z", "Row 2 Z-Depth", "", 100.0f, 4000.0f, 1200.0f);
            addSlider("r3Z", "Row 3 Z-Depth", "", 100.0f, 4000.0f, 900.0f);
            addSlider("r4Z", "Row 4 Z-Depth", "", 100.0f, 4000.0f, 600.0f);
            addSlider("r5Z", "Row 5 Z-Depth", "", 100.0f, 4000.0f, 450.0f);
            addSlider("r6Z", "Row 6 (Bottom) Z-Depth", "", 100.0f, 4000.0f, 450.0f);

            addSectionHeader("ROW-BY-ROW VELOCITY BOUNCE");
            TextView bounceInfo = new TextView(this);
            bounceInfo.setText("Higher Value = More rubber-band bounce when you flick hard. Lower Value = Stays stiff even on fast flicks.");
            bounceInfo.setTextColor(Color.DKGRAY);
            bounceInfo.setPadding(0, 0, 0, 20);
            slidersLayout.addView(bounceInfo);

            addSlider("r0Bounce", "Row 0 (Top) Velocity Bounce", "", 0.0f, 1.0f, 0.45f);
            addSlider("r1Bounce", "Row 1 Velocity Bounce", "", 0.0f, 1.0f, 0.35f);
            addSlider("r2Bounce", "Row 2 Velocity Bounce", "", 0.0f, 1.0f, 0.25f);
            addSlider("r3Bounce", "Row 3 Velocity Bounce", "", 0.0f, 1.0f, 0.15f);
            addSlider("r4Bounce", "Row 4 Velocity Bounce", "", 0.0f, 1.0f, 0.10f);
            addSlider("r5Bounce", "Row 5 Velocity Bounce", "", 0.0f, 1.0f, 0.05f);
            addSlider("r6Bounce", "Row 6 (Bottom) Velocity Bounce", "", 0.0f, 1.0f, 0.05f);
        }
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
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
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
