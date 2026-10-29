package dev.tiggo.screenmirror;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

/** Gesture-only service: no accessibility event subscriptions or window-content retrieval. */
public final class MirrorTouchService extends AccessibilityService {
    private static MirrorTouchService current;
    private boolean busy;

    static boolean connected() { return current != null; }
    @Override protected void onServiceConnected() { current = this; }
    @Override public void onAccessibilityEvent(AccessibilityEvent ignored) { }
    @Override public void onInterrupt() { busy = false; }
    @Override public void onDestroy() { if (current == this) current = null; super.onDestroy(); }

    static void tap(MirrorBridge bridge, float x, float y) {
        float[] point = bridge.mapPoint(x, y);
        if (point != null) gesture(bridge, point[0], point[1], point[0], point[1], 60);
    }

    static void scroll(MirrorBridge bridge, float dx, float dy) {
        float[] line = bridge.scrollVector(dx, dy);
        if (line == null || Math.hypot(line[2] - line[0], line[3] - line[1]) < 4) return;
        gesture(bridge, line[0], line[1], line[2], line[3], 280);
    }

    static void global(MirrorBridge bridge, int action) {
        if (!bridge.canControl()) return;
        MirrorTouchService service = current;
        if (service == null) { bridge.inputStatus = "اجازهٔ دسترس‌پذیری فعال نیست"; return; }
        boolean ok = service.performGlobalAction(action);
        bridge.inputStatus = ok ? "فرمان ارسال شد" : "فرمان پذیرفته نشد";
    }

    private static void gesture(MirrorBridge bridge, float x1, float y1, float x2, float y2, long duration) {
        if (!bridge.canControl()) return;
        MirrorTouchService service = current;
        if (service == null) { bridge.inputStatus = "اجازهٔ دسترس‌پذیری فعال نیست"; return; }
        if (service.busy) return;
        Path path = new Path();
        path.moveTo(x1, y1);
        if (x1 != x2 || y1 != y2) path.lineTo(x2, y2);
        GestureDescription description = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, duration)).build();
        service.busy = true;
        try {
            boolean accepted = service.dispatchGesture(description, new GestureResultCallback() {
                @Override public void onCompleted(GestureDescription gesture) { service.busy = false; bridge.inputStatus = "لمس ارسال شد"; }
                @Override public void onCancelled(GestureDescription gesture) { service.busy = false; bridge.inputStatus = "لمس لغو شد"; }
            }, null);
            if (!accepted) { service.busy = false; bridge.inputStatus = "لمس پذیرفته نشد"; }
        } catch (RuntimeException error) {
            service.busy = false;
            bridge.inputStatus = "TOUCH: " + error.getClass().getSimpleName();
        }
    }
}
