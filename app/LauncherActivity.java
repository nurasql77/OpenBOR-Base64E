package org.openbor.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

import java.io.*;
import java.util.Arrays;
import java.util.Locale;

public class LauncherActivity extends Activity {
    private SharedPreferences prefs;
    private String lang = "ru";
    private TextView title;
    private Button start, settings;
    private boolean waitingForGameReturn = false;

    private final String[] codes = {"ru", "kk", "en"};
    private final String[] names = {"Русский", "Қазақша", "English"};

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("base64e", MODE_PRIVATE);
        lang = prefs.getString("language", "ru");
        showMenu();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForGameReturn) {
            waitingForGameReturn = false;
            showEngineLogs();
        }
    }

    private void clearEngineLogs() {
        File dir = new File(getExternalFilesDir(null), "Logs");
        if (!dir.exists()) dir.mkdirs();
        new File(dir, "OpenBorLog.txt").delete();
        new File(dir, "ScriptLog.txt").delete();
    }

    private String readTail(File file, int maxChars) {
        if (!file.isFile()) return "(log file not created)";
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            long start = Math.max(0, raf.length() - maxChars);
            raf.seek(start);
            byte[] bytes = new byte[(int)(raf.length() - start)];
            raf.readFully(bytes);
            return new String(bytes, "UTF-8");
        } catch (Exception e) {
            return "Cannot read log: " + e;
        }
    }

    private void showEngineLogs() {
        File dir = new File(getExternalFilesDir(null), "Logs");
        String engine = readTail(new File(dir, "OpenBorLog.txt"), 4500);
        String script = readTail(new File(dir, "ScriptLog.txt"), 1800);
        String message = "OpenBOR жабылып қалды. Соңғы engine log:\n\n" + engine
            + "\n\n--- Script log ---\n" + script
            + "\n\nФайлдар: " + dir.getAbsolutePath();
        new AlertDialog.Builder(this)
            .setTitle("OpenBOR диагностикасы")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show();
    }

    private String tr(String key) {
        String[][] data = {
            {"start", "Начать игру", "Ойынды бастау", "Start game"},
            {"settings", "Настройки управления",
                "Басқару баптаулары", "Control settings"},
            {"size", "Размер кнопок",
                "Батырмалар өлшемі", "Button size"},
            {"opacity", "Прозрачность",
                "Мөлдірлік", "Opacity"},
            {"save", "Сохранить", "Сақтау", "Save"},
            {"cancel", "Отмена", "Болдырмау", "Cancel"},
            {"missing", "Не найден файл .pak в assets.",
                "assets бумасында .pak файлы жоқ.",
                "No .pak file found in assets."}
        };

        int column = lang.equals("kk") ? 2 :
                     lang.equals("en") ? 3 : 1;

        for (String[] row : data) {
            if (row[0].equals(key)) return row[column];
        }
        return key;
    }

    private void showMenu() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(Color.rgb(16, 19, 28));

        title = new TextView(this);
        title.setText("OpenBOR");
        title.setTextColor(Color.WHITE);
        title.setTextSize(32);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView credit = new TextView(this);
        credit.setText("port by base64E  •  @android_port");
        credit.setTextColor(Color.rgb(95, 190, 255));
        credit.setGravity(Gravity.CENTER);
        root.addView(credit);

        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, names
        );
        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );
        spinner.setAdapter(adapter);
        spinner.setSelection(Math.max(0, Arrays.asList(codes).indexOf(lang)));
        root.addView(spinner);

        spinner.setOnItemSelectedListener(
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(
                    android.widget.AdapterView<?> parent,
                    View view, int position, long id
                ) {
                    lang = codes[position];
                    prefs.edit().putString("language", lang).apply();
                    updateLabels();
                }

                @Override
                public void onNothingSelected(
                    android.widget.AdapterView<?> parent
                ) {}
            }
        );

        start = new Button(this);
        start.setAllCaps(false);
        start.setOnClickListener(v -> startGame());
        root.addView(start);

        settings = new Button(this);
        settings.setAllCaps(false);
        settings.setOnClickListener(v -> showSettings());
        root.addView(settings);

        setContentView(root);
        updateLabels();
    }

    private void updateLabels() {
        if (start != null) start.setText(tr("start"));
        if (settings != null) settings.setText(tr("settings"));
    }

    private void showSettings() {
        LinearLayout panel = new LinearLayout(this);
        panel.setPadding(20, 10, 20, 10);
        panel.setOrientation(LinearLayout.VERTICAL);

        TextView sizeLabel = new TextView(this);
        SeekBar size = new SeekBar(this);
        size.setMax(100);
        size.setProgress(prefs.getInt("size", 100) - 50);
        panel.addView(sizeLabel);
        panel.addView(size);

        TextView opacityLabel = new TextView(this);
        SeekBar opacity = new SeekBar(this);
        opacity.setMax(80);
        opacity.setProgress(prefs.getInt("opacity", 65) - 20);
        panel.addView(opacityLabel);
        panel.addView(opacity);

        Runnable update = () -> {
            sizeLabel.setText(tr("size") + ": "
                + (size.getProgress() + 50) + "%");
            opacityLabel.setText(tr("opacity") + ": "
                + (opacity.getProgress() + 20) + "%");
        };

        SeekBar.OnSeekBarChangeListener listener =
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(
                    SeekBar bar, int progress, boolean fromUser
                ) { update.run(); }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {}

                @Override
                public void onStopTrackingTouch(SeekBar bar) {}
            };

        size.setOnSeekBarChangeListener(listener);
        opacity.setOnSeekBarChangeListener(listener);
        update.run();

        new AlertDialog.Builder(this)
            .setTitle(tr("settings"))
            .setView(panel)
            .setPositiveButton(tr("save"), (dialog, which) -> {
                int s = size.getProgress() + 50;
                int a = opacity.getProgress() + 20;

                prefs.edit()
                    .putInt("size", s)
                    .putInt("opacity", a)
                    .apply();

                try {
                    File dir = new File(
                        getExternalFilesDir(null), "Saves"
                    );
                    if (!dir.exists() && !dir.mkdirs()) {
                        throw new IOException("Cannot create Saves");
                    }

                    try (FileOutputStream out = new FileOutputStream(
                        new File(dir, "touch_settings.txt")
                    )) {
                        out.write((s + "\n" + a + "\n")
                            .getBytes("UTF-8"));
                    }
                    Toast.makeText(this, tr("save"),
                        Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Toast.makeText(this, e.getMessage(),
                        Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton(tr("cancel"), null)
            .show();
    }

    private void startGame() {
        try {
            File paks = new File(getExternalFilesDir(null), "Paks");
            if (!paks.exists() && !paks.mkdirs()) {
                throw new IOException("Cannot create Paks folder");
            }

            String[] assets = getAssets().list("");
            String pak = null;

            if (assets != null) {
                Arrays.sort(assets, String.CASE_INSENSITIVE_ORDER);
                for (String name : assets) {
                    if (name.toLowerCase(Locale.ROOT).endsWith(".pak")) {
                        pak = name;
                        break;
                    }
                }
            }

            if (pak != null) {
                try (InputStream in = getAssets().open(pak);
                     OutputStream out = new FileOutputStream(
                         new File(paks, pak)
                     )) {
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        out.write(buffer, 0, n);
                    }
                }
            } else {
                File[] existing = paks.listFiles(
                    (dir, name) -> name.toLowerCase(Locale.ROOT)
                        .endsWith(".pak")
                );
                if (existing == null || existing.length == 0) {
                    new AlertDialog.Builder(this)
                        .setMessage(tr("missing"))
                        .setPositiveButton("OK", null)
                        .show();
                    return;
                }
            }

            clearEngineLogs();
            waitingForGameReturn = true;
            startActivity(new Intent(this, GameActivity.class));
        } catch (Exception e) {
            new AlertDialog.Builder(this)
                .setMessage(e.toString())
                .setPositiveButton("OK", null)
                .show();
        }
    }
}