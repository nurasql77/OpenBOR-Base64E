package org.openbor.engine;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Locale;

public class LauncherActivity extends Activity {
    private static final int PICK_PAK_REQUEST = 1001;
    private boolean launching = false;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        showPickerScreen();
        openPakPicker();
    }

    private void showPickerScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(24, 24, 24, 24);
        root.setBackgroundColor(0xFF10131C);

        TextView title = new TextView(this);
        title.setText("OpenBOR");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Ойынды таңдау үшін .pak файлын таңдаңыз");
        hint.setTextColor(0xFFB8C7D9);
        hint.setTextSize(16);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint);

        setContentView(root);
    }

    private void openPakPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, PICK_PAK_REQUEST);
        } catch (Exception e) {
            showError("Файл таңдау терезесін ашу мүмкін болмады: " + e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_PAK_REQUEST) return;

        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            finish();
            return;
        }

        Uri uri = data.getData();
        String fileName = getFileName(uri);

        if (fileName == null || !fileName.toLowerCase(Locale.ROOT).endsWith(".pak")) {
            showError("Таңдалған файл .pak емес. Ойынның .pak файлын таңдаңыз.");
            return;
        }

        if (launching) return;
        launching = true;
        Toast.makeText(this, "Файл дайындалуда…", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                File external = getExternalFilesDir(null);
                if (external == null) throw new IOException("Android файл бумасына қол жеткізе алмады.");
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

                File destination = new File(paksDir, new File(fileName).getName());
                long total = 0;
                try (InputStream in = getContentResolver().openInputStream(uri);
                     FileOutputStream out = new FileOutputStream(destination)) {
                    if (in == null) throw new IOException("Таңдалған файлды оқу мүмкін емес.");
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
                    throw new IOException("PAK файлы бос немесе толық көшірілмеді.");
                }

                runOnUiThread(() -> {
                    try {
                        Toast.makeText(this, "Ойын іске қосылуда…", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(this, GameActivity.class));
                    } catch (Exception e) {
                        launching = false;
                        showError("Ойынды іске қосу қатесі: " + e);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    launching = false;
                    showError("PAK файлын дайындау қатесі:\n" + e.getMessage());
                });
            }
        }).start();
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) result = cursor.getString(index);
                }
            } catch (Exception ignored) {
            }
        }
        if (result == null) result = uri.getLastPathSegment();
        if (result == null) return null;
        int slash = result.lastIndexOf('/');
        if (slash >= 0) result = result.substring(slash + 1);
        return result;
    }

    private void showError(String message) {
        new AlertDialog.Builder(this)
            .setTitle("OpenBOR")
            .setMessage(message)
            .setPositiveButton("Қайта таңдау", (dialog, which) -> openPakPicker())
            .setNegativeButton("Шығу", (dialog, which) -> finish())
            .setOnCancelListener(dialog -> finish())
            .show();
    }
}
