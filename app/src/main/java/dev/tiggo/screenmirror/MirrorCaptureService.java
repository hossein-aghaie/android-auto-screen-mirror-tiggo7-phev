package dev.tiggo.screenmirror;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/** Consent-based, in-memory, image-only capture. No network, audio or file output. */
public final class MirrorCaptureService extends Service {
    static final String START = "dev.tiggo.screenmirror.MIRROR_START";
    static final String STOP = "dev.tiggo.screenmirror.MIRROR_STOP";
    static final String CONSENT = "captureConsent";
    static final String WHOLE_DISPLAY = "wholeDisplayRequested";
    private static final String CHANNEL = "mirror_capture";
    private static final int NOTIFICATION = 44;
    private int maxEdge;
    private long frameIntervalMs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MirrorBridge bridge;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private Bitmap pixels;
    private final MirrorFrameBuffer frameBuffer = new MirrorFrameBuffer();
    private long lastFrame;
    private long lastSurfaceSeen;
    private int captureWidth;
    private int captureHeight;
    private boolean shuttingDown;
    private final MediaProjection.Callback callback = new MediaProjection.Callback() {
        @Override public void onStop() { finishCapture("اشتراک تصویر توسط سیستم متوقف شد"); }

        @Override public void onCapturedContentResize(int width, int height) {
            if (projection == null || display == null || shuttingDown) return;
            try {
                boolean whole = false;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Rect bounds = getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
                    whole = width == bounds.width() && height == bounds.height();
                }
                bridge.setSource(width, height, whole);
                resizeCapture(width, height);
            }
            catch (RuntimeException error) { fail("RESIZE", error); }
        }
    };
    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (projection == null) return;
            long now = SystemClock.elapsedRealtime();
            if (bridge.hasSurface()) lastSurfaceSeen = now;
            if (now - lastSurfaceSeen >= 90_000) {
                finishCapture("صفحهٔ میرور روی ماشین باز نشد؛ اشتراک متوقف شد");
                return;
            }
            handler.postDelayed(this, 2000);
        }
    };
    private final Runnable timeLimit = () -> finishCapture("نشست طبق زمان انتخاب‌شده پایان یافت");

    @Override public void onCreate() {
        super.onCreate();
        bridge = ((MirrorApplication) getApplication()).mirror();
        maxEdge = bridge.settings.edge();
        frameIntervalMs = Math.max(1, 1000 / bridge.settings.fps());
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || STOP.equals(intent.getAction())) {
            finishCapture("اشتراک تصویر متوقف شد");
            return START_NOT_STICKY;
        }
        // Never recover/replay a projection consent after process death or reuse a token.
        if (!START.equals(intent.getAction()) || projection != null) return START_NOT_STICKY;
        Intent consent = intent.getParcelableExtra(CONSENT);
        if (consent == null) {
            finishCapture("مجوز اشتراک صفحه دریافت نشد");
            return START_NOT_STICKY;
        }
        bridge.sessionWholeDisplay = intent.getBooleanExtra(WHOLE_DISPLAY, false);
        try {
            NotificationManager notifications = getSystemService(NotificationManager.class);
            notifications.createNotificationChannel(new NotificationChannel(CHANNEL,
                    "اشتراک تصویر گوشی", NotificationManager.IMPORTANCE_LOW));
            PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MirrorActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            PendingIntent stop = PendingIntent.getService(this, 1,
                    new Intent(this, MirrorCaptureService.class).setAction(STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setContentTitle("تصویر گوشی در حال اشتراک است")
                    .setContentText("برای توقف اینجا بزنید یا از صفحهٔ میرور ماشین خارج شوید")
                    .setContentIntent(open).setOngoing(true)
                    .addAction(new Notification.Action.Builder(null, "توقف", stop).build()).build();
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            else startForeground(NOTIFICATION, notification);
            projection = getSystemService(MediaProjectionManager.class)
                    .getMediaProjection(Activity.RESULT_OK, consent);
            if (projection == null) throw new IllegalStateException("No projection granted");
            projection.registerCallback(callback, handler);
            int width;
            int height;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Rect bounds = getSystemService(WindowManager.class).getMaximumWindowMetrics().getBounds();
                width = bounds.width();
                height = bounds.height();
            } else {
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                width = metrics.widthPixels;
                height = metrics.heightPixels;
            }
            bridge.setSource(width, height, Build.VERSION.SDK_INT < 34);
            int[] size = MirrorGeometry.captureSize(width, height, maxEdge);
            captureWidth = size[0];
            captureHeight = size[1];
            reader = newReader(captureWidth, captureHeight);
            // Exactly ONE virtual display per user consent; subsequent rotations only resize it.
            display = projection.createVirtualDisplay("MyScreenMirror", captureWidth, captureHeight,
                    Math.max(1, getResources().getConfiguration().densityDpi),
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, handler);
            if (display == null) throw new IllegalStateException("No virtual display");
            bridge.capturing = true;
            bridge.framesShown = 0;
            bridge.metrics.start(SystemClock.elapsedRealtime());
            bridge.error = "";
            bridge.status = "اشتراک تصویر روشن است؛ صفحهٔ میرور را روی ماشین باز نگه دارید";
            bridge.rotationStatus = bridge.settings.landscape() && bridge.sessionWholeDisplay
                    ? (RotationSession.begin(this) ? "حالت افقی درخواست شد" : "اجازهٔ تغییر تنظیم چرخش فعال نیست")
                    : "چرخش طبق تنظیم خود گوشی";
            lastSurfaceSeen = SystemClock.elapsedRealtime();
            handler.post(watchdog);
            int stopMinutes = bridge.settings.stopMinutes();
            if (stopMinutes > 0) handler.postDelayed(timeLimit, stopMinutes * 60_000L);
        } catch (RuntimeException error) { fail("CAPTURE_START", error); }
        return START_NOT_STICKY;
    }

    private ImageReader newReader(int width, int height) {
        ImageReader result = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        result.setOnImageAvailableListener(this::onFrame, handler);
        return result;
    }

    private void resizeCapture(int width, int height) {
        int[] size = MirrorGeometry.captureSize(width, height, maxEdge);
        if (size[0] == captureWidth && size[1] == captureHeight) return;
        ImageReader next = newReader(size[0], size[1]);
        try {
            display.resize(size[0], size[1], Math.max(1, getResources().getConfiguration().densityDpi));
            display.setSurface(next.getSurface());
        } catch (RuntimeException error) {
            next.close();
            throw error;
        }
        ImageReader previous = reader;
        reader = next;
        captureWidth = size[0];
        captureHeight = size[1];
        previous.setOnImageAvailableListener(null, null);
        previous.close();
        recyclePixels();
    }

    private void onFrame(ImageReader source) {
        if (source != reader || shuttingDown) return;
        try (Image image = source.acquireLatestImage()) {
            if (image == null || !bridge.capturing || !bridge.hasSurface()) return;
            long now = SystemClock.elapsedRealtime();
            if (now - lastFrame < frameIntervalMs) return;
            lastFrame = now;
            Image.Plane plane = image.getPlanes()[0];
            if (plane.getPixelStride() != 4 || plane.getRowStride() % 4 != 0)
                throw new IllegalStateException("Unsupported RGBA stride");
            int paddedWidth = plane.getRowStride() / 4;
            if (pixels == null || pixels.getWidth() != paddedWidth || pixels.getHeight() != image.getHeight()) {
                recyclePixels();
                pixels = Bitmap.createBitmap(paddedWidth, image.getHeight(), Bitmap.Config.ARGB_8888);
            }
            pixels.copyPixelsFromBuffer(frameBuffer.forBitmap(plane.getBuffer(),
                    plane.getRowStride(), image.getWidth(), image.getHeight()));
            bridge.drawFrame(pixels, image.getWidth(), image.getHeight());
        } catch (RuntimeException error) { fail("FRAME", error); }
    }

    private void fail(String stage, RuntimeException error) {
        bridge.error = stage + ": " + error.getClass().getSimpleName();
        finishCapture("میرور شروع نشد یا متوقف شد؛ وضعیت زیر را گزارش کنید");
    }

    private void recyclePixels() {
        if (pixels != null) pixels.recycle();
        pixels = null;
        frameBuffer.clear();
    }

    private void releaseCapture() {
        shuttingDown = true;
        handler.removeCallbacksAndMessages(null);
        if (display != null) display.release();
        display = null;
        if (reader != null) {
            reader.setOnImageAvailableListener(null, null);
            reader.close();
        }
        reader = null;
        recyclePixels();
        if (projection != null) {
            projection.unregisterCallback(callback);
            projection.stop();
        }
        projection = null;
        if (!RotationSession.restore(this)) bridge.rotationStatus = "تنظیم چرخش برنگشت؛ دکمهٔ بازگردانی را روی گوشی بزنید";
        else bridge.rotationStatus = "تنظیم چرخش قبلی حفظ/بازگردانی شد";
    }

    private void finishCapture(String message) {
        releaseCapture();
        bridge.stopped(message);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    @Override public void onDestroy() {
        releaseCapture();
        if (bridge.capturing) bridge.stopped("اشتراک تصویر متوقف شد");
        super.onDestroy();
    }

    @Override public void onTaskRemoved(Intent rootIntent) { finishCapture("اشتراک تصویر متوقف شد"); }

    @Override public IBinder onBind(Intent intent) { return null; }
}
