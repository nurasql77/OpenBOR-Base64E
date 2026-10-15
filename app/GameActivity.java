package org.openbor.engine;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import org.libsdl.app.SDLActivity;

public class GameActivity extends SDLActivity {
    private int dp(int n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        installEditorButton();
    }

    private void installEditorButton() {
        Button edit = new Button(this);
        edit.setText("⚙");
        edit.setTextSize(20);
        edit.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        edit.setTextColor(Color.WHITE);
        edit.setAllCaps(false);
        edit.setAlpha(0.82f);
        edit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(25, 35, 49)));
        edit.setOnClickListener(v -> showEditor());
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.RIGHT | Gravity.TOP);
        p.setMargins(0, dp(8), dp(8), 0);
        addContentView(edit, p);
    }

    private void showEditor() {
        File dir = new File(getExternalFilesDir(null), "Saves");
        int[] v = readSettings(new File(dir, "touch_settings.txt"));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(6), dp(18), dp(6));
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(list);
        SeekBar[] bars = new SeekBar[6];
        TextView[] labels = new TextView[6];
        String[] names = {"Button size / Өлшем", "Opacity / Мөлдірлік",
            "D-pad X", "D-pad Y", "Action buttons X", "Action buttons Y"};
        int[] min = {50, 20, -25, -25, -25, -25};
        int[] max = {150, 100, 25, 25, 25, 25};
        for (int i = 0; i < 6; i++) {
            final int k = i;
            labels[i] = new TextView(this);
            labels[i].setTextColor(Color.WHITE);
            bars[i] = new SeekBar(this);
            bars[i].setMax(max[i] - min[i]);
            bars[i].setProgress(Math.max(0, Math.min(max[i] - min[i], v[i] - min[i])));
            list.addView(labels[i]);
            list.addView(bars[i]);
            bars[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                public void onProgressChanged(SeekBar b, int p, boolean fromUser) {
                    labels[k].setText(names[k] + ": " + (p + min[k]) + (k < 2 ? "%" : "% screen"));
                }
                public void onStartTrackingTouch(SeekBar b) {}
                public void onStopTrackingTouch(SeekBar b) {}
            });
        }
        for (int i = 0; i < 6; i++) {
            labels[i].setText(names[i] + ": " + v[i] + (i < 2 ? "%" : "% screen"));
        }
        new AlertDialog.Builder(this)
            .setTitle("Touch controls / Управление")
            .setView(scroll)
            .setPositiveButton("Save / Сақтау", (d, w) -> {
                int[] values = new int[6];
                for (int i = 0; i < 6; i++) values[i] = bars[i].getProgress() + min[i];
                try {
                    if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create Saves");
                    try (FileOutputStream out = new FileOutputStream(new File(dir, "touch_settings.txt"))) {
                        String data = values[0] + "\n" + values[1] + "\n" + values[2] + "\n" +
                            values[3] + "\n" + values[4] + "\n" + values[5] + "\n";
                        out.write(data.getBytes("UTF-8"));
                    }
                    Toast.makeText(this, "Controls updated", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton("Cancel / Болдырмау", null)
            .show();
    }

    private int[] readSettings(File file) {
        int[] defaults = {100, 65, 0, 0, 0, 0};
        if (!file.isFile()) return defaults;
        try {
            java.util.Scanner s = new java.util.Scanner(file);
            for (int i = 0; i < defaults.length && s.hasNextInt(); i++) defaults[i] = s.nextInt();
            s.close();
        } catch (Exception ignored) {}
        return defaults;
    }
}