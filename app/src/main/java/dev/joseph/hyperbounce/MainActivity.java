package dev.joseph.hyperbounce;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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

        // Core App Title
        TextView appTitle = new TextView(this);
        appTitle.setText("HyperBounce Physics Engine");
        appTitle.setTextSize(24);
        appTitle.setTextColor(Color.BLACK);
        appTitle.setPadding(0, 0, 0, 40);
        rootLayout.addView(appTitle);

        // Advanced Scripting Button
        Button scriptBtn = new Button(this);
        scriptBtn.setText("Advanced: Inject Custom Physics Script");
        scriptBtn.setTextColor(Color.parseColor("#D81B60"));
        scriptBtn.setOnClickListener(v -> openScriptEditor());
        rootLayout.addView(scriptBtn);
        
        TextView spacer = new TextView(this);
        spacer.setPadding(0,0,0,40);
        rootLayout.addView(spacer);

        // UI Sliders with Detailed Descriptions
        addSlider("animationDuration", "Animation Duration", "Scales the overall speed of the animation. 1.0 is default. 1.5 is slow motion.", 0.1f, 3.0f, 1.0f);
        addSlider("gridDeadzoneX", "Center Deadzone (%)", "Forces perfect symmetry. 0.125 treats both middle columns of a 4-column grid as dead center.", 0.0f, 0.4f, 0.125f);
        
        addSlider("centerDamping", "Center Damping", "Controls the bounce of the apps near the center. Lower value = More aggressive bouncing.", 0.1f, 1.0f, 0.52f);
        addSlider("centerResponse", "Center Response (s)", "Controls how long center apps take to settle into place.", 0.1f, 1.5f, 0.48f);
        
        addSlider("edgeDampingDrop", "Edge Damping Reduction", "How much bouncier the outer edges are compared to the center. Creates the iOS ripple effect.", 0.0f, 0.5f, 0.10f);
        addSlider("edgeResponseDelay", "Edge Response Delay (s)", "How much slower the outer edges settle compared to the center.", 0.0f, 1.0f, 0.22f);
        
        addSlider("minDamping", "Minimum Damping Bound", "Safety clamp to prevent infinite physics glitching.", 0.1f, 1.0f, 0.40f);
        addSlider("maxDamping", "Maximum Damping Bound", "Safety clamp.", 0.1f, 1.0f, 0.55f);
        addSlider("minResponse", "Minimum Response Bound", "Safety clamp.", 0.1f, 1.5f, 0.45f);
        addSlider("maxResponse", "Maximum Response Bound", "Safety clamp.", 0.1f, 1.5f, 0.75f);
        
        addSlider("dockDamping", "Dock Damping", "Bounce parameter exclusively for the bottom dock icons.", 0.1f, 1.0f, 0.70f);
        addSlider("dockResponse", "Dock Response (s)", "Speed parameter exclusively for the bottom dock icons.", 0.1f, 1.5f, 0.40f);
        
        addSlider("zDepthMultiplier", "3D Z-Depth Scale", "Multiplies the deep 3D pop-out effect when unlocking the device.", 1.0f, 4.0f, 1.60f);
    }

    private void openScriptEditor() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("JavaScript Physics Override");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 0);

        Switch enableSwitch = new Switch(this);
        enableSwitch.setText("Enable Dynamic Script Injection");
        enableSwitch.setChecked(currentConfig.optBoolean("useCustomScript", false));
        layout.addView(enableSwitch);

        TextView info = new TextView(this);
        info.setText("Write raw JavaScript. Returns [damping, response].\nVars: isDockView, dist, vx, vy, screenW, screenH");
        info.setPadding(0, 10, 0, 10);
        info.setTextColor(Color.GRAY);
        layout.addView(info);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setTypeface(Typeface.MONOSPACE);
        input.setLines(12);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.LEFT);
        
        String defScript = "// Available Variables:\n// isDockView (boolean), dist (float 0.0 to 1.0)\n// vx, vy (absolute icon coordinates)\n// screenW, screenH (screen dimensions)\n\nfunction getPhysics() {\n    if (isDockView) {\n        return [0.70, 0.40];\n    }\n    var damping = 0.52 - (dist * 0.10);\n    var response = 0.48 + (dist * 0.22);\n    return [damping, response];\n}\ngetPhysics();";
        input.setText(currentConfig.optString("customScript", defScript));
        layout.addView(input);

        builder.setView(layout);
        builder.setPositiveButton("Validate & Save", (dialog, which) -> {
            try {
                // Try validating via Rhino Engine
                org.mozilla.javascript.Context rhino = org.mozilla.javascript.Context.enter();
                rhino.setOptimizationLevel(-1);
                rhino.compileString(input.getText().toString(), "TestScript", 1, null);
                
                currentConfig.put("useCustomScript", enableSwitch.isChecked());
                currentConfig.put("customScript", input.getText().toString());
                saveConfig();
                Toast.makeText(this, "Script Validated & Injected!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Syntax Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            } finally {
                org.mozilla.javascript.Context.exit();
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
                fis.read(data);
                fis.close();
                currentConfig = new JSONObject(new String(data, "UTF-8"));
            }
        } catch (Exception e) {}
    }

    private void saveConfig() {
        new Thread(() -> {
            try {
                File file = new File(getFilesDir(), "config.json");
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(currentConfig.toString().getBytes("UTF-8"));
                fos.flush();
                fos.close();
                
                Runtime.getRuntime().exec("su -c cp " + file.getAbsolutePath() + " /data/local/tmp/hyperbounce.json").waitFor();
                Runtime.getRuntime().exec("su -c chmod 777 /data/local/tmp/hyperbounce.json").waitFor();
            } catch (Exception e) {}
        }).start();
    }

    private void addSlider(String key, String label, String desc, float min, float max, float defVal) {
        float savedVal = (float) currentConfig.optDouble(key, defVal);
        
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 10, 0, 40);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(16);
        title.setTextColor(Color.BLACK);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        TextView valText = new TextView(this);
        valText.setText(String.format("%.3f", savedVal));
        valText.setTextColor(Color.parseColor("#007AFF"));
        valText.setTextSize(16);

        header.addView(title);
        header.addView(valText);
        row.addView(header);

        // Add info description
        TextView descText = new TextView(this);
        descText.setText(desc);
        descText.setTextSize(12);
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

        rootLayout.addView(row);
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
