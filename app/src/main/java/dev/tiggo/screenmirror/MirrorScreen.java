package dev.tiggo.screenmirror;

import android.view.Surface;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.accessibilityservice.AccessibilityService;

import androidx.annotation.NonNull;
import androidx.car.app.AppManager;
import androidx.car.app.CarContext;
import androidx.car.app.Screen;
import androidx.car.app.SurfaceCallback;
import androidx.car.app.SurfaceContainer;
import androidx.car.app.model.Action;
import androidx.car.app.model.ActionStrip;
import androidx.car.app.model.MessageTemplate;
import androidx.car.app.model.Template;
import androidx.car.app.model.CarIcon;
import androidx.core.graphics.drawable.IconCompat;
import androidx.car.app.navigation.model.NavigationTemplate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

/** Experimental surface use; not an approved navigation/mirroring product. */
final class MirrorScreen extends Screen implements DefaultLifecycleObserver {
    private final MirrorBridge bridge;
    private Surface currentSurface;
    private boolean started;
    private String failure;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float scrollX;
    private float scrollY;
    private long ignoreTapUntil;
    private final Runnable finishScroll = new Runnable() {
        @Override public void run() {
            if (started) MirrorTouchService.scroll(bridge, scrollX, scrollY);
            scrollX = scrollY = 0;
        }
    };
    private final SurfaceCallback callback = new SurfaceCallback() {
        @Override public void onSurfaceAvailable(@NonNull SurfaceContainer container) {
            Surface incoming = container.getSurface();
            if (!started) {
                if (incoming != null) incoming.release();
                return;
            }
            if (currentSurface != incoming) releaseSurface();
            currentSurface = incoming;
            bridge.attach(incoming, container.getWidth(), container.getHeight());
        }

        @Override public void onSurfaceDestroyed(@NonNull SurfaceContainer container) {
            releaseSurface();
            // Losing the host display ends this capture; a fresh phone consent is required.
            getCarContext().stopService(new android.content.Intent(getCarContext(), MirrorCaptureService.class));
        }

        @Override public void onVisibleAreaChanged(@NonNull Rect area) { bridge.setVisible(area); }
        @Override public void onClick(float x, float y) {
            if (started && SystemClock.elapsedRealtime() >= ignoreTapUntil) MirrorTouchService.tap(bridge, x, y);
        }
        @Override public void onScroll(float dx, float dy) {
            if (!started || !Float.isFinite(dx) || !Float.isFinite(dy)) return;
            scrollX = Math.max(-2000, Math.min(2000, scrollX + dx));
            scrollY = Math.max(-2000, Math.min(2000, scrollY + dy));
            ignoreTapUntil = SystemClock.elapsedRealtime() + 400;
            handler.removeCallbacks(finishScroll);
            handler.postDelayed(finishScroll, 120);
        }
    };

    MirrorScreen(CarContext context) {
        super(context);
        bridge = ((MirrorApplication) context.getApplicationContext()).mirror();
        getLifecycle().addObserver(this);
    }

    @Override public void onStart(@NonNull LifecycleOwner owner) {
        started = true;
        bridge.open();
        try { getCarContext().getCarService(AppManager.class).setSurfaceCallback(callback); }
        catch (RuntimeException error) {
            failure = "Android Auto صفحهٔ تصویر را نپذیرفت: " + error.getClass().getSimpleName();
            bridge.error = failure;
            bridge.close(getCarContext());
            invalidate();
        }
    }

    @Override public void onStop(@NonNull LifecycleOwner owner) {
        started = false;
        handler.removeCallbacksAndMessages(null);
        scrollX = scrollY = 0;
        try { getCarContext().getCarService(AppManager.class).setSurfaceCallback(null); }
        catch (RuntimeException ignored) { }
        bridge.close(getCarContext());
        releaseSurface();
    }

    private void releaseSurface() {
        if (currentSurface == null) return;
        bridge.detach(currentSurface);
        currentSurface.release();
        currentSurface = null;
    }

    @NonNull @Override public Template onGetTemplate() {
        if (failure != null) return new MessageTemplate.Builder(failure)
                .setTitle("My Screen Mirror").setHeaderAction(Action.BACK).build();
        Action stop = new Action.Builder().setTitle("خروج و توقف")
                .setOnClickListener(() -> {
                    bridge.close(getCarContext());
                    getCarContext().finishCarApp();
                }).build();
        Action home = new Action.Builder().setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), R.drawable.ic_home)).build())
                .setOnClickListener(() -> MirrorTouchService.global(bridge, AccessibilityService.GLOBAL_ACTION_HOME)).build();
        Action back = new Action.Builder().setIcon(new CarIcon.Builder(IconCompat.createWithResource(getCarContext(), R.drawable.ic_back)).build())
                .setOnClickListener(() -> MirrorTouchService.global(bridge, AccessibilityService.GLOBAL_ACTION_BACK)).build();
        NavigationTemplate.Builder template = new NavigationTemplate.Builder()
                .setActionStrip(new ActionStrip.Builder().addAction(stop).addAction(back).addAction(home).build());
        if (getCarContext().getCarAppApiLevel() >= 2) {
            template.setMapActionStrip(new ActionStrip.Builder().addAction(Action.PAN).build());
        }
        return template.build();
    }
}
