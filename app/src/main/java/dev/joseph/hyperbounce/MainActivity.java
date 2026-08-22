package dev.joseph.hyperbounce;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
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
        
        // Grant launcher read access to this app's local files via root
        try {
            Runtime.getRuntime().exec("su -c chmod 777 /data/data/dev.joseph.hyperbounce").waitFor();
            Runtime.getRuntime().exec("su -c chmod 777 /data/data/dev.joseph.hyperbounce/files").waitFor();
        } catch (Exception e) {}

        loadConfig();

        ScrollView scroll = new ScrollView(this);
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(40, 40, 40, 40);
        scroll.addView(rootLayout);
        setContentView(scroll);

        // UI Sliders (Key, Name, Min, Max, Default)
        addSlider("centerDamping", "Center Damping", 0.1f, 1.0f, 0.52f);
        addSlider("centerResponse", "Center Response (s)", 0.1f, 1.5f, 0.48f);
        addSlider("edgeDampingDrop", "Edge Damping Reduction", 0.0f, 0.5f, 0.10f);
        addSlider("edgeResponseDelay", "Edge Response Delay (s)", 0.0f, 1.0f, 0.22f);
        addSlider("minDamping", "Minimum Damping Bound", 0.1f, 1.0f, 0.40f);
        addSlider("maxDamping", "Maximum Damping Bound", 0.1f, 1.0f, 0.55f);
        addSlider("minResponse", "Minimum Response Bound", 0.1f, 1.5f, 0.45f);
        addSlider("maxResponse", "Maximum Response Bound", 0.1f, 1.5f, 0.75f);
        addSlider("dockDamping", "Dock Damping", 0.1f, 1.0f, 0.70f);
        addSlider("dockResponse", "Dock Response (s)", 0.1f, 1.5f, 0.40f);
        addSlider("zDepthMultiplier", "3D Z-Depth Scale", 1.0f, 4.0f, 1.60f);
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
            file.setReadable(true, false); 
        } catch (Exception e) {}
    }

    private void addSlider(String key, String label, float min, float max, float defVal) {
        float savedVal = (float) currentConfig.optDouble(key, defVal);
        
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 20, 0, 40);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        
        TextView title = new TextView(this);
        title.setText(label);
        title.setTextSize(16);
        title.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        TextView valText = new TextView(this);
        valText.setText(String.format("%.3f", savedVal));
        valText.setTextColor(Color.parseColor("#007AFF"));
        valText.setTextSize(16);

        header.addView(title);
        header.addView(valText);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(1000);
        seekBar.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        
        // Setup initial position (clamping purely for the UI thumb)
        if (savedVal > max) seekBar.setProgress(1000);
        else if (savedVal < min) seekBar.setProgress(0);
        else seekBar.setProgress((int) (((savedVal - min) / (max - min)) * 1000));

        Button resetBtn = new Button(this);
        resetBtn.setText("Reset");
        
        controls.addView(seekBar);
        controls.addView(resetBtn);

        row.addView(header);
        row.addView(controls);

        // Handlers
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

        // Long press manual override
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
