package org.openbor.engine;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Base64E additions layered on top of the original OpenBOR GameActivity.
 * The upstream GameActivity remains intact: SDL, native libraries, wake lock,
 * vibration and Android lifecycle handling continue to come from OpenBOR.
 */
public final class Base64EGameActivity extends GameActivity {
    @Override
    protected String[] getArguments() {
        Intent intent = getIntent();
        String name = intent == null ? null : intent.getStringExtra("pak_name");
        if (name == null || name.isEmpty() || name.contains("/") ||
                name.contains("\\") || name.equals(".") || name.equals("..")) {
            Log.e("Base64E", "No valid PAK filename was passed to the game activity");
            return new String[0];
        }

        File root = getExternalFilesDir(null);
        if (root == null) return new String[0];
        File pak = new File(new File(root, "Paks"), name);
        if (!pak.isFile() || pak.length() <= 0) {
            Log.e("Base64E", "PAK missing or empty: " + pak.getAbsolutePath());
            return new String[0];
        }

        // sdlport.c changes its working directory to the app's external-files root.
        return new String[] { "Paks/" + name };
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        installEditorButton();
        Log.i("Base64E", "Base64E game activity started");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private File settingsFile() {
        File root = getExternalFilesDir(null);
        return root == null ? null : new File(new File(root, "Saves"), "touch_settings.txt");
    }

    private void installEditorButton() {
        Button edit = new Button(this);
        edit.setText("⚙");
        edit.setTextSize(20);
        edit.setTextColor(Color.WHITE);
        edit.setAllCaps(false);
        edit.setAlpha(0.82f);
        edit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(25, 35, 49)));
        edit.setOnClickListener(v -> showTouchEditor());

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(48), dp(48), Gravity.RIGHT | Gravity.TOP);
        params.setMargins(0, dp(8), dp(8), 0);
        addContentView(edit, params);
    }

    private void showTouchEditor() {
        int[] values = readSettings(settingsFile());
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(4), dp(16), dp(4));

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(list);

        String[] names = {
                "Button size", "Opacity", "D-pad X", "D-pad Y",
                "Action buttons X", "Action buttons Y"
        };
        int[] min = {50, 20, -25, -25, -25, -25};
        int[] max = {150, 100, 25, 25, 25, 25};
        SeekBar[] bars = new SeekBar[values.length];
        TextView[] labels = new TextView[values.length];

        for (int i = 0; i < values.length; i++) {
            final int index = i;
            labels[i] = new TextView(this);
            labels[i].setTextColor(Color.WHITE);
            bars[i] = new SeekBar(this);
            bars[i].setMax(max[i] - min[i]);
            bars[i].setProgress(Math.max(0, Math.min(max[i] - min[i], values[i] - min[i])));
            list.addView(labels[i]);
            list.addView(bars[i]);
            bars[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    int value = progress + min[index];
                    labels[index].setText(names[index] + ": " + value +
                            (index < 2 ? "%" : "% screen"));
                }
                @Override public void onStartTrackingTouch(SeekBar bar) {}
                @Override public void onStopTrackingTouch(SeekBar bar) {}
            });
        }

        for (int i = 0; i < values.length; i++) {
            labels[i].setText(names[i] + ": " + values[i] +
                    (i < 2 ? "%" : "% screen"));
        }

        new AlertDialog.Builder(this)
                .setTitle("Touch controls / Басқару")
                .setView(scroll)
                .setPositiveButton("Save / Сақтау", (dialog, which) -> {
                    int[] output = new int[values.length];
                    for (int i = 0; i < output.length; i++) {
                        output[i] = bars[i].getProgress() + min[i];
                    }
                    writeSettings(settingsFile(), output);
                })
                .setNegativeButton("Cancel / Болдырмау", null)
                .show();
    }

    private int[] readSettings(File file) {
        int[] values = {100, 65, 0, 0, 0, 0};
        if (file == null || !file.isFile()) return values;
        try (java.util.Scanner scanner = new java.util.Scanner(file)) {
            for (int i = 0; i < values.length && scanner.hasNextInt(); i++) {
                values[i] = scanner.nextInt();
            }
        } catch (Exception e) {
            Log.w("Base64E", "Could not read touch settings", e);
        }
        values[0] = clamp(values[0], 50, 150);
        values[1] = clamp(values[1], 20, 100);
        for (int i = 2; i < values.length; i++) values[i] = clamp(values[i], -25, 25);
        return values;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void writeSettings(File file, int[] values) {
        if (file == null) {
            Toast.makeText(this, "App storage unavailable", Toast.LENGTH_LONG).show();
            return;
        }
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("Cannot create Saves directory");
            }
            try (FileOutputStream output = new FileOutputStream(file)) {
                StringBuilder text = new StringBuilder();
                for (int value : values) text.append(value).append('\n');
                output.write(text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            Toast.makeText(this, "Controls saved", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e("Base64E", "Could not save touch settings", e);
            Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
        }
    }
}
