/*
 * OpenBOR - http://www.chronocrash.com
 * -----------------------------------------------------------------------
 * All rights reserved, see LICENSE in OpenBOR root for details.
 *
 * Copyright (c) OpenBOR Team
 *
 * Moved from SDLActivity.java here for more flexibility.
 * IMPORTANT: DON'T EDIT SDLActivity.java anymore, but this file!
 *
 * The following from SDLActivity.java migration, and kept intact for respect to authors
 * as well as specific lines inside this source file is kept intact although moved / rearranged /
 * removed / modified as part from migration process.
 * --------------------------------------------------------
 * SDLActivity.java - Main code for Android build.
 * Original by UTunnels (utunnels@hotmail.com).
 * Modifications by CRxTRDude, White Dragon and msmalik681.
 * --------------------------------------------------------
 */

package org.openbor.engine;

import org.libsdl.app.SDLActivity;

import android.util.Log;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.os.Bundle;
import android.content.Context;
import android.os.Build;
import android.content.pm.ApplicationInfo;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock; // Explicitly import WakeLock if you want to be specific, or keep PowerManager.*
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager;
import android.os.Vibrator;
import android.os.VibrationEffect;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import org.jetbrains.annotations.Nullable;

/**
 * Extended functionality from SDLActivity.
 *
 * Separated for ease of updating both for dependency and this support functionality later.
 */
public class GameActivity extends SDLActivity {

  //White Dragon: added statics
  protected static WakeLock wakeLock;
  protected static View decorView;

//needed to fix sdk 34+ crashing
@Override
public Intent registerReceiver(@Nullable BroadcastReceiver receiver, IntentFilter filter) {
    if (Build.VERSION.SDK_INT >= 34 && getApplicationInfo().targetSdkVersion >= 34) {
        return super.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
    } else {
        return super.registerReceiver(receiver, filter);
    }
}
  //note: White Dragon's vibrator is moved into C code for 2 reasons
  // - avoid modifying SDLActivity.java as it's platform support
  // - reduce round-trip cost/time in call C-function to check for touch-area and whether
  //   vibration is enabled or not
  //   (for reference: SDL finally registers event/action/x/y/etc into its C-code from Java code
  //   in onTouch() call, thus we do this logic in C code for efficient then provide vibration code
  //   in Java when we really need to vibrate the device)
  
  // -- section of Java native solutions provided to be called from C code -- //
  /**
   * This will vibrate device if there's vibrator service.
   * Otherwise it will do nothing.
   *
   * Modified version from original by White Dragon
   */
  public static void jni_vibrate() {
    Vibrator vibrator = (Vibrator)getContext().getSystemService(Context.VIBRATOR_SERVICE);

    if (vibrator.hasVibrator())
    {

      // wait for 3 ms, vibrate for 250 ms, then off for 1000 ms
      // note: consult api at two links below, it has two different meanings but in this case,
      // use case is the same
      long[] pattern = {16, 250};

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      {
        // API 26 and above
        // look for its api at https://developer.android.com/reference/android/os/VibrationEffect.html
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
      }
      else
      {
        // below API 26
        // look for its api at https://developer.android.com/reference/android/os/Vibrator.html#vibrate(long%5B%5D,%2520int)
        vibrator.vibrate(pattern, -1);
      }
    }
  } 
  // ------------------------------------------------------------------------ //

  /**
   * Also load "openbor" as shared library to run the game in which
   * inside there's main function entry for the program.
   */
  @Override
  protected String[] getLibraries() {
    return new String[] {
      "SDL2",
      "openbor"
    };
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // call parent's implementation
    super.onCreate(savedInstanceState);
    installEditorButton();
    Log.v("OpenBOR", "onCreate called");
    //msmalik681 copy pak for custom apk and notify is paks folder empty
   // CopyPak();

    //CRxTRDude - Added FLAG_KEEP_SCREEN_ON to prevent screen timeout.
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    //CRxTRDude - Created a wakelock to prevent the app from being shut down upon screen lock.
    PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
    GameActivity.wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BOR");
    if (!GameActivity.wakeLock.isHeld())
    {
      GameActivity.wakeLock.acquire();
    }
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    Log.v("OpenBOR", "onLowMemory");

    //CRxTRDude - Release wakelock first before destroying.
    if (GameActivity.wakeLock.isHeld())
      GameActivity.wakeLock.release();
  }

  @Override
  protected void onPause() {
    super.onPause();
    Log.v("OpenBOR", "onPause");

    //White Dragon: wakelock release!
    if (GameActivity.wakeLock.isHeld())
      GameActivity.wakeLock.release();
  }

  @Override
  protected void onResume() {
    super.onResume();
    Log.v("OpenBOR", "onResume");

    //White Dragon: wakelock acquire!
    if (!GameActivity.wakeLock.isHeld())
      GameActivity.wakeLock.acquire();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    Log.v("OpenBOR", "onDestroy");

    //CRxTRDude - Release wakelock first before destroying.
    if (GameActivity.wakeLock.isHeld())
      GameActivity.wakeLock.release();
  }

  private int editorDp(int n) {
    return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
  }

  private void installEditorButton() {
    Button edit = new Button(this);
    edit.setText("⚙");
    edit.setTextSize(20);
    edit.setTextColor(android.graphics.Color.WHITE);
    edit.setAllCaps(false);
    edit.setAlpha(0.82f);
    edit.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.rgb(25, 35, 49)));
    edit.setOnClickListener(v -> showTouchEditor());
    FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(editorDp(48), editorDp(48), Gravity.RIGHT | Gravity.TOP);
    p.setMargins(0, editorDp(8), editorDp(8), 0);
    addContentView(edit, p);
  }

  private void showTouchEditor() {
    File dir = new File(getExternalFilesDir(null), "Saves");
    int[] values = readTouchSettings(new File(dir, "touch_settings.txt"));
    LinearLayout list = new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL);
    list.setPadding(editorDp(16), editorDp(4), editorDp(16), editorDp(4));
    android.widget.ScrollView scroll = new android.widget.ScrollView(this);
    scroll.addView(list);
    String[] names = {"Button size", "Opacity", "D-pad X", "D-pad Y", "Action buttons X", "Action buttons Y"};
    int[] min = {50, 20, -25, -25, -25, -25};
    int[] max = {150, 100, 25, 25, 25, 25};
    SeekBar[] bars = new SeekBar[6];
    TextView[] labels = new TextView[6];
    for (int i = 0; i < 6; i++) {
      final int k = i;
      labels[i] = new TextView(this);
      labels[i].setTextColor(android.graphics.Color.WHITE);
      bars[i] = new SeekBar(this);
      bars[i].setMax(max[i] - min[i]);
      bars[i].setProgress(Math.max(0, Math.min(max[i] - min[i], values[i] - min[i])));
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
    for (int i = 0; i < 6; i++) labels[i].setText(names[i] + ": " + values[i] + (i < 2 ? "%" : "% screen"));
    new AlertDialog.Builder(this).setTitle("Touch controls / Управление").setView(scroll)
      .setPositiveButton("Save / Сақтау", (d, w) -> {
        int[] outValues = new int[6];
        for (int i = 0; i < 6; i++) outValues[i] = bars[i].getProgress() + min[i];
        try {
          if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create Saves");
          try (FileOutputStream out = new FileOutputStream(new File(dir, "touch_settings.txt"))) {
            String data = outValues[0] + "\n" + outValues[1] + "\n" + outValues[2] + "\n" +
              outValues[3] + "\n" + outValues[4] + "\n" + outValues[5] + "\n";
            out.write(data.getBytes("UTF-8"));
          }
          Toast.makeText(this, "Controls updated", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
          Toast.makeText(this, e.toString(), Toast.LENGTH_LONG).show();
        }
      }).setNegativeButton("Cancel / Болдырмау", null).show();
  }

  private int[] readTouchSettings(File file) {
    int[] values = {100, 65, 0, 0, 0, 0};
    if (!file.isFile()) return values;
    try {
      java.util.Scanner s = new java.util.Scanner(file);
      for (int i = 0; i < values.length && s.hasNextInt(); i++) values[i] = s.nextInt();
      s.close();
    } catch (Exception ignored) {}
    return values;
  }

}
