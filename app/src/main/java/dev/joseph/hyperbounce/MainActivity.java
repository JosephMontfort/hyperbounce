package dev.joseph.hyperbounce;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
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
        loadConfig();

        ScrollView scroll = new ScrollView(this);
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(40, 40, 40, 40);
        scroll.addView(rootLayout);
        setContentView(scroll);

        // App Title
        TextView appTitle = new TextView(this);
        appTitle.setText("HyperBounce Physics Engine");
        appTitle.setTextSize(22);
        appTitle.setTextColor(Color.BLACK);
        appTitle.setPadding(0, 0, 0, 20);
        rootLayout.addView(appTitle);

        // Animation Mode Dropdown
        TextView modeTitle = new TextView(this);
        modeTitle.setText("Select Animation Engine Mode:");
        modeTitle.setTextSize(14);
        modeTitle.setTextColor(Color.DKGRAY);
        rootLayout.addView(modeTitle);

        Spinner modeSpinner = new Spinner(this);
        String[] modes = {
            "Mode 0: iOS Radial Dispersion (Original Classic)",
            "Mode 1: Kinetic Y-Axis Cascade (Velocity Dependent)"
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, modes);
        modeSpinner.setAdapter(adapter);
        modeSpinner.setSelection(currentConfig.optInt("animMode", 1));
        modeSpinner.setPadding(0, 10, 0, 30);

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
            // Mode 0 Sliders (Radial Classic)
            addSlider("centerDamping", "Center Damping", "Bounce of center apps. Lower = more bounce.", 0.1f, 1.0f, 0.58f);
            addSlider("centerResponse", "Center Response (s)", "Time for center apps to settle.", 0.1f, 1.5f, 0.48f);
            addSlider("edgeDampingDrop", "Edge Damping Reduction", "Extra bounce on screen edges.", 0.0f, 0.5f, 0.10f);
            addSlider("edgeResponseDelay", "Edge Response Delay (s)", "Lag delay for outer icons.", 0.0f, 1.0f, 0.22f);
            addSlider("minDamping", "Minimum Damping Bound", "Safety bound.", 0.1f, 1.0f, 0.42f);
            addSlider("maxDamping", "Maximum Damping Bound", "Safety bound.", 0.1f, 1.0f, 0.65f);
            addSlider("minResponse", "Minimum Response Bound", "Safety bound.", 0.1f, 1.5f, 0.45f);
            addSlider("maxResponse", "Maximum Response Bound", "Safety bound.", 0.1f, 1.5f, 0.75f);
            addSlider("dockDamping", "Dock Damping", "Bounce for dock icons.", 0.1f, 1.0f, 0.70f);
            addSlider("dockResponse", "Dock Response (s)", "Speed for dock icons.", 0.1f, 1.5f, 0.40f);
            addSlider("zDepthMultiplier", "3D Z-Depth Scale", "Multiplies initial 3D spawn distance.", 1.0f, 4.0f, 1.60f);
        } else {
            // Mode 1 Sliders (Kinetic Cascade)
            addSlider("cascadeDamping", "Cascade Base Damping", "Critically damped resting friction.", 0.5f, 1.0f, 0.92f);
            addSlider("cascadeBaseResponse", "Cascade Base Response (s)", "Speed of upper rows settling.", 0.2f, 1.0f, 0.50f);
            addSlider("cascadeRowStagger", "Row Stagger Delay Step (s)", "Delay gap between each row from bottom to top.", 0.01f, 0.10f, 0.035f);
            addSlider("maxVelocity", "Max Measured Swipe Velocity", "SystemUI normalizer for physical swipe speeds.", 5000.0f, 30000.0f, 16000.0f);
        }
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

            // Broadcast in-memory config update to Launcher
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
        title.setTextColor(Color.BLACK);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView valText = new TextView(this);
        valText.setText(String.format("%.3f", savedVal));
        valText.setTextColor(Color.parseColor("#007AFF"));
        valText.setTextSize(15);

        header.addView(title);
        header.addView(valText);
        row.addView(header);

        TextView descText = new TextView(this);
        descText.setText(desc);
        descText.setTextSize(11);
        descText.setTextColor(Color.DKGRAY);
        descText.setPadding(0, 0, 0, 10);
        row.addView(descText);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (savedVal > max) seekBar.setProgress(1000);
        else if (savedVal < min) seekBar.setProgress(0);
        else seekBar.setProgress((int) (((savedVal - min) / (max - min)) * 1000));

        Button resetBtn = new Button(this);
        resetBtn.setText("Reset");

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
            builder.setTitle("Enter custom " + label);
            final EditText input = new EditText(MainActivity.this);
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            float cv = (float) currentConfig.optDouble(key, defVal);
            input.setText(String.valueOf(cv));
            builder.setView(input);
            builder.setPositiveButton("Set", (dialog, which) -> {
                try {
                    float newVal = Float.parseFloat(input.getText().toString());
                    currentConfig.put(key, newVal);
                    saveConfig();
                    valText.setText(String.format("%.3f", newVal));
                    if (newVal > max) seekBar.setProgress(1000);
                    else if (newVal < min) seekBar.setProgress(0);
                    else seekBar.setProgress((int) (((newVal - min) / (max - min)) * 1000));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Invalid number", Toast.LENGTH_SHORT).show();
                }
            });
            builder.setNegativeButton("Cancel", null);
            builder.show();
            return true;
        };

        row.setOnLongClickListener(manualInput);
        valText.setOnLongClickListener(manualInput);

        slidersLayout.addView(row);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, "Restart Launcher").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) {
            try {
                Runtime.getRuntime().exec("su -c killall com.miui.home");
                Toast.makeText(this, "Launcher Restarted", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Failed to restart (Root missing?)", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
