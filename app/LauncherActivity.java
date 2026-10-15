package org.openbor.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.Gravity;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Locale;

public class LauncherActivity extends Activity {
    private boolean launching = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        showLoadingScreen();
        if (!launching) {
            launching = true;
            new Thread(this::installBundledPakAndLaunch).start();
        }
    }

    private void showLoadingScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(0xFF10131C);

        TextView title = new TextView(this);
        title.setText("Base64E");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Ойын жүктелуде…");
        hint.setTextColor(0xFFB8C7D9);
        hint.setTextSize(16);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint);
        setContentView(root);
    }

    private void installBundledPakAndLaunch() {
        try {
            File external = getExternalFilesDir(null);
            if (external == null) throw new IOException("OpenBOR файл бумасына қол жеткізе алмады.");

            File paksDir = new File(external, "Paks");
            if (!paksDir.exists() && !paksDir.mkdirs()) {
                throw new IOException("Paks бумасын жасау мүмкін болмады.");
            }

            File[] oldPaks = paksDir.listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".pak"));
            if (oldPaks != null) {
                for (File old : oldPaks) {
                    if (!old.delete()) {
                        throw new IOException("Ескі PAK файлын өшіру мүмкін болмады: " + old.getName());
                    }
                }
            }

            File destination = new File(paksDir, "base64e-game.pak");
            long total = 0;
            try (InputStream in = getAssets().open("base64e-game.pak");
                 FileOutputStream out = new FileOutputStream(destination)) {
                byte[] buffer = new byte[64 * 1024];
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    total += count;
                }
                out.getFD().sync();
            }

            if (total == 0 || !destination.isFile() || destination.length() != total) {
                destination.delete();
                throw new IOException("APK ішіндегі PAK файлы бос немесе толық көшірілмеді.");
            }

            runOnUiThread(() -> {
                try {
                    startActivity(new Intent(this, GameActivity.class));
                    finish();
                } catch (Exception e) {
                    launching = false;
                    showError("Ойынды іске қосу қатесі: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            runOnUiThread(() -> {
                launching = false;
                showError("APK ішіндегі Base64E PAK файлын дайындау қатесі:\n" + e.getMessage());
            });
        }
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
            .setTitle("Base64E / OpenBOR")
            .setMessage(message)
            .setPositiveButton("Қайталау", (dialog, which) -> {
                launching = true;
                new Thread(this::installBundledPakAndLaunch).start();
            })
            .setNegativeButton("Шығу", (dialog, which) -> finish())
            .setOnCancelListener(dialog -> finish())
            .show();
    }
}
