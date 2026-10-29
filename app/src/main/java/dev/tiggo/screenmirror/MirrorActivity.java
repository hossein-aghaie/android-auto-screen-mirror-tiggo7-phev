package dev.tiggo.screenmirror;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.projection.MediaProjectionConfig;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public final class MirrorActivity extends Activity {
    private static final int CAPTURE = 301;
    private static final int NOTIFICATIONS = 302;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MirrorBridge bridge;
    private MirrorSettings settings;
    private TextView state;
    private TextView permissions;
    private TextView clock;
    private Button start;
    private Spinner quality;
    private CheckBox parked;
    private CheckBox landscape;
    private boolean requesting;
    private int taps;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            state.setText(bridge.describe());
            permissions.setText("لمس مانیتور: " + (MirrorTouchService.connected() ? "دسترسی داده شده" : "دسترسی اختیاری لازم است")
                    + "\nچرخش خودکار: " + (Settings.System.canWrite(MirrorActivity.this) ? "اجازه داده شده" : "اجازهٔ اختیاری لازم است"));
            clock.setText(new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(new java.util.Date()));
            start.setEnabled(parked.isChecked() && !requesting && !bridge.capturing);
            quality.setEnabled(!bridge.capturing && !requesting);
            landscape.setEnabled(!bridge.capturing && !requesting);
            handler.postDelayed(this, 700);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        bridge = ((MirrorApplication) getApplication()).mirror();
        settings = bridge.settings;
        if (saved != null) { requesting = saved.getBoolean("requesting"); taps = saved.getInt("taps"); }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(11,22,32));
        getWindow().setNavigationBarColor(Color.rgb(11,22,32));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(11,22,32));
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(22),dp(16),dp(22),dp(30));
        page.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(page);
        TextView title = text(page,"My Screen Mirror",28);
        title.setTextColor(Color.rgb(105,225,188));
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        text(page,"تصویر گوشی روی Android Auto",16);
        LinearLayout control = card(page);
        text(control,"گوشی را به Android Auto وصل کن و My Screen Mirror را روی مانیتور باز کن؛ سپس اینجا اشتراک صفحه را شروع کن.",16);
        parked = new CheckBox(this);
        parked.setText("خودرو متوقف است؛ فقط در توقف استفاده می‌کنم");
        parked.setTextColor(Color.WHITE);
        parked.setChecked(saved != null && saved.getBoolean("parked"));
        control.addView(parked);
        start = button(control,"شروع اشتراک صفحه",this::begin);
        start.setEnabled(false);
        parked.setOnCheckedChangeListener((v,checked) -> { if (!checked) stopCapture(); });
        button(control,"توقف میرور",this::stopCapture);
        clock = text(control,"",23);
        clock.setTextColor(Color.rgb(105,225,188));
        Button test = button(control,"لمس آزمایشی: " + taps,() -> { });
        test.setOnClickListener(v -> test.setText("لمس آزمایشی: " + (++taps)));
        text(control,"برای تست لمس، همین دکمه را روی مانیتور ماشین بزن؛ عدد باید زیاد شود.",14);

        LinearLayout options = card(page);
        text(options,"تصویر و چرخش",21);
        text(options,"کیفیت — تغییر در شروع بعدی اعمال می‌شود",14);
        quality = new Spinner(this);
        quality.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,MirrorSettings.QUALITY_LABELS));
        quality.setSelection(settings.quality());
        quality.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                settings.prefs.edit().putInt("quality",position).apply();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        options.addView(quality,new LinearLayout.LayoutParams(-1,dp(52)));
        landscape = check(options,"افقی شدن خودکار گوشی هنگام میرور",settings.landscape(),value -> settings.flag("landscape",value));
        button(options,"اجازهٔ افقی شدن خودکار",() -> openSettings(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName()))));
        text(options,"تنظیم چرخش بعد از توقف برگردانده می‌شود. بعضی اپ‌ها یا صفحهٔ اصلی گوشی فقط عمودی اجرا می‌شوند.",14);
        check(options,"پرکردن قاب؛ ممکن است کناره‌های تصویر بریده شود",settings.fill(),value -> {
            settings.flag("fill",value);
            bridge.resetMapping();
        });
        button(options,"بازگردانی تنظیم چرخش قبلی",() -> {
            stopCapture();
            bridge.rotationStatus = RotationSession.restore(this) ? "تنظیم چرخش بازگردانی شد" : "بازگردانی به اجازهٔ تغییر تنظیمات نیاز دارد";
        });

        LinearLayout touch = card(page);
        text(touch,"کنترل از مانیتور",21);
        check(touch,"انتقال لمس، کشیدن، خانه و برگشت به گوشی",settings.touch(),value -> settings.flag("touch",value));
        text(touch,"برای لمس، اشتراک کل صفحه و اجازهٔ دسترس‌پذیری لازم است. سرویس فقط فرمان‌های خودت را منتقل می‌کند و متن پنجره‌ها را نمی‌خواند.",14);
        button(touch,"فعال‌کردن دسترسی لمس",() -> {
            Intent details = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .putExtra(Intent.EXTRA_COMPONENT_NAME,new ComponentName(this,MirrorTouchService.class));
            try { startActivity(details); }
            catch (RuntimeException error) { openSettings(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        });
        text(touch,"در تنظیمات دسترس‌پذیری، سرویس My Screen Mirror را انتخاب کن. اگر کشیدن کار نکرد، دکمهٔ جابه‌جایی/فلش چهارسو در صفحهٔ ماشین را بزن. لمس بلند، چندانگشتی و کیبورد در این نسخه نیست.",14);
        permissions = text(touch,"",14);

        LinearLayout info = card(page);
        state = text(info,"",14);
        button(info,"کپی وضعیت",() -> {
            getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("My Screen Mirror",
                    BuildConfig.VERSION_NAME + "\n" + bridge.describe()));
            Toast.makeText(this,"وضعیت کپی شد",Toast.LENGTH_SHORT).show();
        });
        text(info,"نسخهٔ " + BuildConfig.VERSION_NAME + " • تصویر ذخیره یا آپلود نمی‌شود. گوشی روشن بماند؛ محتوای محافظت‌شده ممکن است سیاه شود. صدا به مسیر معمول صوت گوشی/Android Auto بستگی دارد.",14);
        setContentView(scroll);
    }

    private void begin() {
        if (!parked.isChecked() || requesting || bridge.capturing) return;
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requesting = true;
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATIONS);
        } else requestCapture();
    }

    private void requestCapture() {
        requesting = true;
        MediaProjectionManager manager = getSystemService(MediaProjectionManager.class);
        try {
            Intent capture = Build.VERSION.SDK_INT >= 34
                    ? manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
                    : manager.createScreenCaptureIntent();
            startActivityForResult(capture,CAPTURE);
        } catch (RuntimeException error) { requesting = false; bridge.error = "CONSENT: " + error.getClass().getSimpleName(); }
    }

    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) {
        super.onRequestPermissionsResult(code,permissions,grants);
        if (code == NOTIFICATIONS) { requesting = false; if (parked.isChecked()) requestCapture(); }
    }
    @Override protected void onActivityResult(int code,int result,Intent data) {
        super.onActivityResult(code,result,data);
        if (code != CAPTURE) return;
        requesting = false;
        if (result != RESULT_OK || data == null) { bridge.status = "اشتراک صفحه تأیید نشد"; return; }
        try { startForegroundService(new Intent(this,MirrorCaptureService.class).setAction(MirrorCaptureService.START)
                .putExtra(MirrorCaptureService.CONSENT,data)); }
        catch (RuntimeException error) { bridge.error = "SERVICE: " + error.getClass().getSimpleName(); }
    }
    private void stopCapture() { stopService(new Intent(this,MirrorCaptureService.class)); }
    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch (RuntimeException error) { Toast.makeText(this,"این تنظیم در گوشی باز نشد",Toast.LENGTH_LONG).show(); }
    }
    @Override protected void onStart() { super.onStart(); handler.post(refresh); }
    @Override protected void onStop() { handler.removeCallbacksAndMessages(null); super.onStop(); }
    @Override protected void onSaveInstanceState(Bundle out) {
        out.putBoolean("requesting",requesting); out.putBoolean("parked",parked.isChecked()); out.putInt("taps",taps);
        super.onSaveInstanceState(out);
    }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16),dp(10),dp(16),dp(12));
        card.setBackgroundColor(Color.rgb(23,42,56));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2);
        params.setMargins(0,dp(14),0,0);
        parent.addView(card,params);
        return card;
    }
    private TextView text(LinearLayout parent,String value,int size) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size);
        text.setTextColor(Color.rgb(231,239,245)); text.setPadding(0,dp(8),0,dp(8)); parent.addView(text); return text;
    }
    private Button button(LinearLayout parent,String label,Runnable action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false);
        button.setOnClickListener(v -> action.run()); parent.addView(button,new LinearLayout.LayoutParams(-1,-2)); return button;
    }
    private CheckBox check(LinearLayout parent,String label,boolean value,java.util.function.Consumer<Boolean> action) {
        CheckBox box = new CheckBox(this); box.setText(label); box.setTextColor(Color.WHITE); box.setChecked(value);
        box.setOnCheckedChangeListener((v,checked) -> action.accept(checked)); parent.addView(box); return box;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
