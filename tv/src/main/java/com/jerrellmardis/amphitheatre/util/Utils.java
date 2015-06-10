/*
 * Copyright (C) 2014 Jerrell Mardis
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jerrellmardis.amphitheatre.util;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.os.Environment;
import android.support.v7.graphics.Palette;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.github.mjdev.libaums.fs.UsbFile;
import com.jerrellmardis.amphitheatre.R;
import com.jerrellmardis.amphitheatre.model.SuperFile;
import com.jerrellmardis.amphitheatre.service.LibraryUpdateService;
import com.jerrellmardis.amphitheatre.service.RecommendationsService;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.List;

/**
 * A collection of utility methods, all static.
 */
public final class Utils {

    private static final int LIBRARY_UPDATE_REQUEST_CODE = 1;
    private static String TAG = "amp:Utils";
    /*
     * Making sure public utility methods remain static
     */
    private Utils() {
    }

    /**
     * Returns the screen/display size
     *
     * @param context
     * @return
     */
    public static Point getDisplaySize(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;
        return new Point(width, height);
    }

    /**
     * Shows an error dialog with a given text message.
     *
     * @param context
     * @param errorString
     */
    public static final void showErrorDialog(Context context, String errorString) {
        new AlertDialog.Builder(context).setTitle(R.string.error)
                .setMessage(errorString)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.cancel();
                    }
                })
                .create()
                .show();
    }

    /**
     * Shows a (long) toast
     *
     * @param context
     * @param msg
     */
    public static void showToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    /**
     * Shows a (long) toast.
     *
     * @param context
     * @param resourceId
     */
    public static void showToast(Context context, int resourceId) {
        Toast.makeText(context, context.getString(resourceId), Toast.LENGTH_LONG).show();
    }

    /**
     * Formats time in milliseconds to hh:mm:ss string format.
     *
     * @param millis
     * @return
     */
    public static String formatMillis(int millis) {
        String result = "";
        int hr = millis / 3600000;
        millis %= 3600000;
        int min = millis / 60000;
        millis %= 60000;
        int sec = millis / 1000;
        if (hr > 0) {
            result += hr + ":";
        }
        if (min >= 0) {
            if (min > 9) {
                result += min + ":";
            } else {
                result += "0" + min + ":";
            }
        }
        if (sec > 9) {
            result += sec;
        } else {
            result += "0" + sec;
        }
        return result;
    }

    public static int dpToPx(int dp, Context ctx) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round((float) dp * density);
    }

    public static void scheduleLibraryUpdateService(Context context) {
        Log.d("amp:Utils", "Checking if alarm is already set: "+isAlarmAlreadySet(context, LibraryUpdateService.class));
        //And now
        Log.d("amp:Utils", "Start rechecking the library");
        Intent intent = new Intent(context, LibraryUpdateService.class);
        intent.putExtra("ALARM", true);
        /*context.startService(intent);*/
        if (isAlarmAlreadySet(context, LibraryUpdateService.class)) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        //Intent intent = ...
        PendingIntent alarmIntent = PendingIntent.getService(context, LIBRARY_UPDATE_REQUEST_CODE,
                intent, 0);

        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                AlarmManager.INTERVAL_HALF_HOUR,
                AlarmManager.INTERVAL_HOUR*3, //Every three hours seems sufficient
                alarmIntent);

    }
    public static void scheduleRecommendations(Context context) {
        Log.d("amp:Utils", "Checking if alarm is already set: "+isAlarmAlreadySet(context, RecommendationsService.class));
        //And now
        Log.d("amp:Utils", "Start rechecking the recommendations");
        Intent intent = new Intent(context, RecommendationsService.class);
        intent.putExtra("ALARM", true);
        /*context.startService(intent);*/
        if (isAlarmAlreadySet(context, RecommendationsService.class)) return;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        //Intent intent = ...
        PendingIntent alarmIntent = PendingIntent.getService(context, LIBRARY_UPDATE_REQUEST_CODE,
                intent, 0);

        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                5000,
                AlarmManager.INTERVAL_HALF_HOUR, //Every half hour seems sufficient
                alarmIntent);

    }

    private static boolean isAlarmAlreadySet(Context context, Class c) {
        Intent intent = new Intent(context, c);
        PendingIntent pi = PendingIntent.getService(context, LIBRARY_UPDATE_REQUEST_CODE,
                intent, PendingIntent.FLAG_NO_CREATE);
        return pi != null;
    }

    public static void backupDatabase(Context ctx) {
        try {
            File sd = Environment.getExternalStorageDirectory();
            File data = Environment.getDataDirectory();

            if (sd.canWrite()) {
                String currentDBPath = "//data//" + ctx.getApplicationContext().getPackageName() +
                        "//databases//amphitheatre.db";
                String backupDBPath = "amphitheatre.db";
                File currentDB = new File(data, currentDBPath);
                File backupDB = new File(sd, backupDBPath);

                if (currentDB.exists()) {
                    FileChannel src = new FileInputStream(currentDB).getChannel();
                    FileChannel dst = new FileOutputStream(backupDB).getChannel();
                    dst.transferFrom(src, 0, src.size());
                    src.close();
                    dst.close();
                }
            }
        } catch (Exception e) {
            Log.i(Utils.class.getSimpleName(), "Unable to backup database");
        }
    }

    public static void animateColorChange(final View view, int colorFrom, int colorTo) {
        ValueAnimator valueAnimator = ValueAnimator.ofObject(new ArgbEvaluator(), colorFrom, colorTo);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animator) {
                view.setBackgroundColor((Integer)animator.getAnimatedValue());
            }
        });
        valueAnimator.start();
    }

    public static int getPaletteColor(Palette palette, String colorType, int defaultColor) {
        if (colorType.equals("")) {
            return defaultColor;
        }

        Method[] paletteMethods = palette.getClass().getDeclaredMethods();

        for(Method method : paletteMethods) {
            if (StringUtils.containsIgnoreCase(method.getName(), colorType)) {
                try {
                    Palette.Swatch item = (Palette.Swatch)method.invoke(palette);
                    if (item != null) {
                        return item.getRgb();
                    }
                    else {
                        return defaultColor;
                    }
                }
                catch(Exception ex) {
                    Log.d("getPaletteColor", ex.getMessage());
                    return defaultColor;
                }
            }
        }

        return defaultColor;
    }

    public static void checkPrefs(SharedPreferences sharedPrefs) {
        SharedPreferences.Editor editor = sharedPrefs.edit();

        if (!sharedPrefs.contains(Constants.PALETTE_BACKGROUND_VISIBLE)) {
            editor.putString(Constants.PALETTE_BACKGROUND_VISIBLE, Enums.PalettePresenterType.FOCUSEDCARD.name());
        }
        if (!sharedPrefs.contains(Constants.PALETTE_BACKGROUND_UNSELECTED)) {
            editor.putString(Constants.PALETTE_BACKGROUND_UNSELECTED, "");
        }
        if (!sharedPrefs.contains(Constants.PALETTE_BACKGROUND_SELECTED)) {
            editor.putString(Constants.PALETTE_BACKGROUND_SELECTED, Enums.PaletteColor.DARKMUTED.name());
        }
        if (!sharedPrefs.contains(Constants.PALETTE_TITLE_VISIBLE)) {
            editor.putString(Constants.PALETTE_TITLE_VISIBLE, Enums.PalettePresenterType.NOTHING.name());
        }
        if (!sharedPrefs.contains(Constants.PALETTE_TITLE_UNSELECTED)) {
            editor.putString(Constants.PALETTE_TITLE_UNSELECTED, "");
        }
        if (!sharedPrefs.contains(Constants.PALETTE_TITLE_SELECTED)) {
            editor.putString(Constants.PALETTE_TITLE_UNSELECTED, "");
        }
        if (!sharedPrefs.contains(Constants.PALETTE_CONTENT_VISIBLE)) {
            editor.putString(Constants.PALETTE_CONTENT_VISIBLE, Enums.PalettePresenterType.NOTHING.name());
        }
        if (!sharedPrefs.contains(Constants.PALETTE_CONTENT_UNSELECTED)) {
            editor.putString(Constants.PALETTE_CONTENT_UNSELECTED, "");
        }
        if (!sharedPrefs.contains(Constants.PALETTE_CONTENT_SELECTED)) {
            editor.putString(Constants.PALETTE_CONTENT_UNSELECTED, "");
        }
        if (!sharedPrefs.contains(Constants.BACKGROUND_BLUR)) {
            editor.putString(Constants.BACKGROUND_BLUR, Enums.BlurState.ON.name());
        }

        editor.apply();
    }

    public static UsbFile searchUsbFiles(UsbFile root, String path) throws IOException {
//        Log.d(TAG, "Start file search in " + new SuperFile(root).getPath() + " for " + path);
        Log.d(TAG, "Start file search in USB for " + path);
        if(!path.contains("usb://")) {
            //Add the false beginning
//            path = path.substring(6);
            path = "usb://"+path;
        }
        return searchUsbFiles(Arrays.asList(root.listFiles()), path);
    }
    private static UsbFile searchUsbFiles(List<UsbFile> files, String path) throws IOException {
        for(UsbFile f: files) {
            if(new SuperFile(f).getPath().contains(path)) {
                Log.d(TAG, "Found "+new SuperFile(f).getPath());
                return f;
            } else {
                if(f.isDirectory()) {
                    Log.d(TAG, "Folder "+new SuperFile(f).getPath()+" found, traversing");
                    searchUsbFiles(Arrays.asList(f.listFiles()), path);
                } else {
                    Log.d(TAG, "Ignore "+new SuperFile(f).getPath());
                }
            }
        }
//        Log.d(TAG, "Search ended in failure");
        return null;
    }
}
