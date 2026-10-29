package dev.tiggo.screenmirror;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.View;
import androidx.car.app.connection.CarConnection;
import androidx.core.content.FileProvider;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class MirrorActivity extends Activity {
    private static final int CAPTURE = 301;
    private static final int NOTIFICATIONS = 302;
    static final String REPOSITORY = "https://github.com/hossein-aghaie/android-auto-screen-mirror-tiggo7-phev";
    private final Handler handler = new Handler(Looper.getMainLooper());
    MirrorBridge bridge;
    MirrorSettings settings;
    MirrorUi ui;
    int tab;
    int testTaps;
    boolean parked;
    boolean requesting;
    private boolean requestedWhole;
    private long pendingServiceUntil;
    private boolean lastCapture;
    private LiveData<Integer> carConnection;
    private Integer connectionType;
    private final Observer<Integer> connectionObserver = type -> { connectionType = type; if (ui != null) ui.update(); };
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            if (lastCapture != bridge.capturing) {
                lastCapture = bridge.capturing;
                if (lastCapture) pendingServiceUntil = 0;
                ui.render(tab);
            }
            ui.update();
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        settings = new MirrorSettings(this);
        setTheme(settings.lightTheme() ? android.R.style.Theme_Material_Light_NoActionBar : android.R.style.Theme_Material_NoActionBar);
        super.onCreate(saved);
        bridge = ((MirrorApplication) getApplication()).mirror();
        settings = bridge.settings;
        if (saved != null) {
            tab = saved.getInt("tab"); parked = saved.getBoolean("parked");
            requesting = saved.getBoolean("requesting"); requestedWhole = saved.getBoolean("requestedWhole");
            testTaps = saved.getInt("testTaps"); pendingServiceUntil = saved.getLong("pendingServiceUntil");
        }
        lastCapture = bridge.capturing;
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        int flags = settings.lightTheme() ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        ui = new MirrorUi(this);
        ui.render(tab);
        try { carConnection = new CarConnection(getApplicationContext()).getType(); }
        catch (RuntimeException ignored) { }
    }

    void showTab(int selected) { tab = Math.max(0,Math.min(3,selected)); ui.render(tab); }
    boolean pending() { return requesting || SystemClock.elapsedRealtime() < pendingServiceUntil; }
    boolean projectionConnected() { return connectionType != null && connectionType == CarConnection.CONNECTION_TYPE_PROJECTION; }
    void startOrStop() {
        if (bridge.capturing || pendingServiceUntil > SystemClock.elapsedRealtime()) { stopCapture(); return; }
        if (pending()) return;
        if (!parked) { toast("ابتدا تأیید کن خودرو متوقف است"); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requesting = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS);
        } else requestCapture();
    }
    private void requestCapture() {
        if (!parked) { requesting = false; return; }
        requestedWhole = !settings.singleApp();
        requesting = true;
        bridge.error = "";
        try {
            MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
            Intent capture = Build.VERSION.SDK_INT >= 34 && requestedWhole
                    ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                    : manager.createScreenCaptureIntent();
            startActivityForResult(capture,CAPTURE);
        } catch (RuntimeException error) { requesting = false; bridge.error = "CONSENT: " + error.getClass().getSimpleName(); toast("پنجرهٔ اشتراک صفحه باز نشد"); }
        ui.update();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(code,permissions,grants);
        if (code == NOTIFICATIONS) { requesting = false; requestCapture(); }
    }
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        if (code != CAPTURE) return;
        requesting = false;
        if (result != RESULT_OK || data == null) { bridge.status = "اشتراک صفحه تأیید نشد؛ هر وقت خواستی دوباره شروع کن"; ui.update(); return; }
        if (!parked) return;
        pendingServiceUntil = SystemClock.elapsedRealtime() + 5000;
        try {
            startForegroundService(new Intent(this,MirrorCaptureService.class).setAction(MirrorCaptureService.START)
                    .putExtra(MirrorCaptureService.CONSENT,data).putExtra(MirrorCaptureService.WHOLE_DISPLAY,requestedWhole));
        } catch (RuntimeException error) { pendingServiceUntil = 0; bridge.error = "SERVICE: " + error.getClass().getSimpleName(); }
        ui.update();
    }
    void stopCapture() { pendingServiceUntil = 0; stopService(new Intent(this,MirrorCaptureService.class)); ui.update(); }

    void setTouch(boolean enabled) {
        if (!enabled) { settings.flag("touch",false); ui.update(); return; }
        new AlertDialog.Builder(this).setTitle("کنترل گوشی از مانیتور")
                .setMessage("این قابلیت با سرویس دسترس‌پذیری، لمس و کشیدن خودت روی مانیتور را روی گوشی اجرا می‌کند و دکمه‌های خانه و برگشت را می‌فرستد. سرویس متن یا ساختار پنجره‌ها را نمی‌خواند؛ اطلاعاتی ارسال نمی‌شود. فقط در نشست کل صفحه و خودروی متوقف فعال است.\n\nاختیاری است؛ برای دیدن تصویر به آن نیازی نداری. هر زمان می‌توانی گزینه یا سرویس را خاموش کنی.")
                .setNegativeButton("فعلاً نه",(d,w) -> ui.render(tab))
                .setOnCancelListener(d -> ui.render(tab))
                .setPositiveButton("فعال‌کردن کنترل لمس",(d,w) -> {
                    settings.flag("touch",true); ui.render(tab);
                    if (!MirrorTouchService.connected()) openAccessibility();
                }).show();
    }
    void openAccessibility() { open(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .putExtra(Intent.EXTRA_COMPONENT_NAME,new ComponentName(this,MirrorTouchService.class))); }
    void openAppInfo() { open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:" + getPackageName()))); }
    void openRotationPermission() {
        new AlertDialog.Builder(this).setTitle("اجازهٔ تغییر چرخش گوشی")
                .setMessage("فقط تنظیم عمومی چرخش برای افقی شدن هنگام میرور تغییر می‌کند؛ بعد از توقف تلاش می‌کنیم مقدار قبلی را برگردانیم. بعضی برنامه‌ها جهت صفحه را قفل می‌کنند. این دسترسی اختیاری است.")
                .setNegativeButton("انصراف",null).setPositiveButton("رفتن به تنظیمات",(d,w) ->
                        open(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,Uri.parse("package:" + getPackageName())))).show();
    }
    void restoreRotation() {
        stopCapture();
        boolean restored = RotationSession.restore(this);
        bridge.rotationStatus = restored ? "تنظیم چرخش قبلی بازگردانی شد" : "برای بازگردانی اجازهٔ تغییر تنظیمات لازم است";
        toast(bridge.rotationStatus);
    }
    void setThemeChoice(boolean light) {
        if (settings.lightTheme() != light) { settings.flag("light_theme",light); recreate(); }
    }
    void copyReport() {
        try {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("My Screen Mirror",MirrorReport.json(this,bridge)));
            toast("گزارش فنی کپی شد");
        } catch (Exception error) { toast("گزارش آماده نشد"); }
    }
    void shareReport() {
        new AlertDialog.Builder(this).setTitle("اشتراک گزارش فنی")
                .setMessage("گزارش فقط نسخهٔ اپ و اندروید، ابعاد، وضعیت مجوزها و خطای فنی دارد. تصویر، متن صفحه، شناسهٔ گوشی و لاگ کامل در آن نیست. مقصد را در مرحلهٔ بعد خودت انتخاب می‌کنی.")
                .setNegativeButton("انصراف",null).setPositiveButton("انتخاب مقصد",(d,w) -> {
                    try {
                        File directory = new File(getCacheDir(),"support");
                        if (!directory.exists() && !directory.mkdirs()) throw new java.io.IOException();
                        File report = new File(directory,"my-screen-mirror-report.json");
                        Files.write(report.toPath(),MirrorReport.json(this,bridge).getBytes(StandardCharsets.UTF_8));
                        Uri uri = FileProvider.getUriForFile(this,getPackageName() + ".support",report);
                        Intent intent = new Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM,uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setClipData(ClipData.newRawUri("Mirror report",uri));
                        startActivity(Intent.createChooser(intent,"اشتراک گزارش فنی"));
                    } catch (Exception error) { toast("اشتراک گزارش باز نشد"); }
                }).show();
    }
    void openUrl(String suffix) { open(new Intent(Intent.ACTION_VIEW,Uri.parse(REPOSITORY + suffix))); }
    void open(Intent intent) {
        try { startActivity(intent); }
        catch (RuntimeException error) { toast("این صفحه در گوشی باز نشد"); }
    }
    void toast(String message) { android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show(); }
    @Override protected void onStart() {
        super.onStart();
        if (carConnection != null) carConnection.observeForever(connectionObserver);
        handler.post(refresh);
    }
    @Override protected void onResume() { super.onResume(); if (ui != null) ui.update(); }
    @Override protected void onStop() {
        handler.removeCallbacksAndMessages(null);
        if (carConnection != null) carConnection.removeObserver(connectionObserver);
        super.onStop();
    }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("tab",tab); out.putBoolean("parked",parked); out.putBoolean("requesting",requesting);
        out.putBoolean("requestedWhole",requestedWhole); out.putInt("testTaps",testTaps); out.putLong("pendingServiceUntil",pendingServiceUntil);
        super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() { if (tab != 0) showTab(0); else super.onBackPressed(); }
}
