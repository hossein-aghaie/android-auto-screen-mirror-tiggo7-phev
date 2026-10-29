package dev.tiggo.screenmirror;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.view.Surface;

/** Two reversible public phone settings; never uses shell, root, overlay or hidden APIs. */
final class RotationSession {
    static boolean begin(Context context) {
        if (!Settings.System.canWrite(context)) return false;
        SharedPreferences journal = context.getSharedPreferences("rotation_restore", Context.MODE_PRIVATE);
        if (journal.getBoolean("pending", false) && !restore(context)) return false;
        int auto = Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
        int rotation = Settings.System.getInt(context.getContentResolver(), Settings.System.USER_ROTATION, 0);
        if (!journal.edit().putInt("auto", auto).putInt("rotation", rotation).putBoolean("pending", true).commit()) return false;
        try {
            if (!Settings.System.putInt(context.getContentResolver(), Settings.System.USER_ROTATION, Surface.ROTATION_90)
                    || !Settings.System.putInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0)) {
                restore(context);
                return false;
            }
            return true;
        } catch (RuntimeException error) { restore(context); return false; }
    }

    static boolean restore(Context context) {
        SharedPreferences journal = context.getSharedPreferences("rotation_restore", Context.MODE_PRIVATE);
        if (!journal.getBoolean("pending", false)) return true;
        if (!Settings.System.canWrite(context)) return false;
        try {
            int currentAuto = Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 1);
            int currentRotation = Settings.System.getInt(context.getContentResolver(), Settings.System.USER_ROTATION, 0);
            // Do not overwrite a manual adjustment made by the user after our change.
            if (currentRotation == Surface.ROTATION_90) {
                if (!Settings.System.putInt(context.getContentResolver(), Settings.System.USER_ROTATION,
                        journal.getInt("rotation", 0))) return false;
            }
            if (currentAuto == 0 && currentRotation == Surface.ROTATION_90) {
                if (!Settings.System.putInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION,
                        journal.getInt("auto", 1))) return false;
            }
            return journal.edit().putBoolean("pending", false).commit();
        } catch (RuntimeException error) { return false; }
    }
}
