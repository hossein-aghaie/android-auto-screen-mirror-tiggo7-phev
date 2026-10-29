package dev.tiggo.screenmirror;

import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import org.json.JSONObject;

/** User-shared technical state only. No screen, UI tree, VIN, serial, SSID or logs. */
final class MirrorReport {
    static String json(Context context, MirrorBridge bridge) throws org.json.JSONException {
        MirrorSettings settings = bridge.settings;
        JSONObject report = new JSONObject();
        report.put("schema", "my-screen-mirror-support/1");
        report.put("appVersion", BuildConfig.VERSION_NAME);
        report.put("androidApi", Build.VERSION.SDK_INT);
        report.put("captureActive", bridge.capturing);
        report.put("hostSurfaceAvailable", bridge.hasSurface());
        report.put("hostSize", bridge.hostSize());
        report.put("captureWidth", bridge.sourceWidth);
        report.put("captureHeight", bridge.sourceHeight);
        report.put("sessionMode", bridge.sessionWholeDisplay ? "whole-display" : "single-app");
        report.put("configuredMode", settings.singleApp() ? "single-app" : "whole-display");
        report.put("configuredQuality", settings.quality());
        report.put("framesSent", bridge.framesShown);
        report.put("durationMs", bridge.metrics.durationMs(SystemClock.elapsedRealtime()));
        report.put("touchServiceConnected", MirrorTouchService.connected());
        report.put("touchOptionEnabled", settings.touch());
        report.put("rotationPermission", android.provider.Settings.System.canWrite(context));
        report.put("status", bridge.status);
        report.put("error", bridge.error);
        report.put("touchStatus", bridge.inputStatus);
        report.put("rotationStatus", bridge.rotationStatus);
        report.put("note", "No images, screen text, identifiers or raw logs. Frame count means local sends, not verified car display refresh.");
        return report.toString(2);
    }
}
