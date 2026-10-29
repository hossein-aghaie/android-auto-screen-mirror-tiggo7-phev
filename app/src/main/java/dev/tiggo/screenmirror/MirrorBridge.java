package dev.tiggo.screenmirror;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Surface;
import android.os.SystemClock;

/** In-process state only. Called exclusively on the Android main thread. No frame storage. */
final class MirrorBridge {
    final Context context;
    final MirrorSettings settings;
    private Surface surface;
    private int width;
    private int height;
    private boolean screenOpen;
    boolean capturing;
    long framesShown;
    String status = "اشتراک تصویر خاموش است";
    String error = "";
    String inputStatus = "لمس خاموش است";
    String rotationStatus = "";
    int sourceWidth;
    int sourceHeight;
    boolean wholeDisplayVerified;
    boolean sessionWholeDisplay;
    final SessionMetrics metrics = new SessionMetrics();
    private float[] lastPlacement;
    private long lastPresented;
    private final Rect visible = new Rect();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    MirrorBridge(Context context) {
        this.context = context.getApplicationContext();
        settings = new MirrorSettings(context);
    }

    void open() { screenOpen = true; }

    void attach(Surface value, int w, int h) {
        surface = value;
        width = w;
        height = h;
        visible.set(0, 0, w, h);
        lastPlacement = null;
        if (!capturing) drawTest();
    }

    void detach(Surface expected) {
        if (surface != expected) return;
        surface = null;
        width = 0;
        height = 0;
        lastPlacement = null;
    }

    void close(Context context) {
        screenOpen = false;
        surface = null;
        width = 0;
        height = 0;
        lastPlacement = null;
        context.stopService(new Intent(context, MirrorCaptureService.class));
    }

    boolean hasSurface() { return screenOpen && surface != null && surface.isValid() && width > 0 && height > 0; }

    void stopped(String message) {
        metrics.stop(SystemClock.elapsedRealtime());
        capturing = false;
        wholeDisplayVerified = false;
        lastPlacement = null;
        status = message;
        drawTest();
    }

    String describe() {
        return status + "\nنمایشگر ماشین: " + (hasSurface() ? width + " × " + height : "در انتظار صفحهٔ میرور روی ماشین")
                + "\nفریم‌های ارسال‌شده: " + framesShown
                + "\nمحدودهٔ اشتراک: " + sourceWidth + " × " + sourceHeight
                + (wholeDisplayVerified ? " — کل صفحه تأیید شد" : " — لمس منتظر تأیید کل صفحه")
                + "\n" + inputStatus + "\n" + rotationStatus
                + (error.isEmpty() ? "" : "\n" + error);
    }

    void drawFrame(Bitmap bitmap, int captureWidth, int captureHeight) {
        if (!hasSurface()) return;
        Canvas canvas = null;
        try {
            canvas = surface.lockCanvas(null);
            canvas.drawColor(Color.BLACK);
            Rect area = visible.isEmpty() ? new Rect(0, 0, width, height) : visible;
            float[] box = MirrorGeometry.placement(captureWidth, captureHeight, area.width(), area.height(), settings.fill());
            box[0] += area.left;
            box[2] += area.left;
            box[1] += area.top;
            box[3] += area.top;
            canvas.drawBitmap(bitmap, new Rect(0, 0, captureWidth, captureHeight),
                    new RectF(box[0], box[1], box[2], box[3]), paint);
            lastPlacement = box;
        } catch (RuntimeException failure) {
            error = "CAR_SURFACE: " + failure.getClass().getSimpleName();
        } finally {
            if (canvas != null) {
                try {
                    surface.unlockCanvasAndPost(canvas);
                    framesShown++;
                    lastPresented = SystemClock.elapsedRealtime();
                }
                catch (RuntimeException failure) { error = "CAR_POST: " + failure.getClass().getSimpleName(); }
            }
        }
    }

    void setVisible(Rect area) {
        visible.set(area);
        if (!visible.intersect(0, 0, width, height)) visible.set(0, 0, width, height);
        lastPlacement = null;
        if (!capturing) drawTest();
    }

    void setSource(int w, int h, boolean whole) {
        sourceWidth = w;
        sourceHeight = h;
        wholeDisplayVerified = whole;
        lastPlacement = null;
    }

    void resetMapping() { lastPlacement = null; }

    boolean canControl() {
        boolean ready = SessionMetrics.canForwardInput(capturing, hasSurface(), settings.touch(), sessionWholeDisplay,
                wholeDisplayVerified, context.getSystemService(android.app.KeyguardManager.class).isKeyguardLocked(),
                lastPlacement != null, SystemClock.elapsedRealtime() - lastPresented);
        if (!ready) inputStatus = !sessionWholeDisplay && capturing ? "حالت تک‌برنامه؛ کنترل لمس خاموش است"
                : settings.touch() ? "لمس منتظر تصویر تازهٔ کل صفحه است" : "لمس خاموش است";
        return ready;
    }

    String hostSize() { return hasSurface() ? width + " × " + height : "—"; }

    float[] mapPoint(float x, float y) {
        if (!canControl() || x < 0 || y < 0 || x >= width || y >= height
                || (!visible.isEmpty() && !visible.contains((int) x, (int) y))) return null;
        return MirrorGeometry.mapPoint(x, y, lastPlacement, sourceWidth, sourceHeight);
    }

    float[] scrollVector(float dx, float dy) {
        if (!canControl() || !Float.isFinite(dx) || !Float.isFinite(dy)) return null;
        float cx = sourceWidth / 2f;
        float cy = sourceHeight / 2f;
        float x = cx - dx * sourceWidth / (lastPlacement[2] - lastPlacement[0]);
        float y = cy - dy * sourceHeight / (lastPlacement[3] - lastPlacement[1]);
        return new float[] {cx, cy, Math.max(1, Math.min(sourceWidth - 2, x)),
                Math.max(1, Math.min(sourceHeight - 2, y))};
    }

    void drawTest() {
        if (!hasSurface()) return;
        Canvas canvas = null;
        try {
            canvas = surface.lockCanvas(null);
            canvas.drawColor(Color.rgb(11, 22, 32));
            paint.setColor(Color.rgb(105, 225, 188));
            canvas.drawRect(width * .08f, height * .15f, width * .92f, height * .22f, paint);
            paint.setTextSize(Math.max(18, Math.min(width / 25f, height / 10f)));
            canvas.drawText("MY SCREEN MIRROR", width * .09f, height * .43f, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(Math.max(14, Math.min(width / 38f, height / 15f)));
            canvas.drawText("Start screen sharing on your phone", width * .09f, height * .60f, paint);
            canvas.drawText("Parked use | Touch requires phone permission", width * .09f, height * .75f, paint);
        } catch (RuntimeException failure) {
            error = "TEST_SURFACE: " + failure.getClass().getSimpleName();
        } finally {
            if (canvas != null) {
                try { surface.unlockCanvasAndPost(canvas); }
                catch (RuntimeException failure) { error = "TEST_POST: " + failure.getClass().getSimpleName(); }
            }
        }
    }
}
