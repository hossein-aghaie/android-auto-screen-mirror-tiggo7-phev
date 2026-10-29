package dev.tiggo.screenmirror;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Native, RTL, four-page interface. No browser, network or additional UI dependency. */
final class MirrorUi {
    private final MirrorActivity activity;
    private final MirrorBridge bridge;
    private final MirrorSettings settings;
    private final boolean light;
    private final int background;
    private final int cardColor;
    private final int textColor;
    private final int muted;
    private final int accent;
    private final int line;
    private TextView statusTitle;
    private TextView statusDetail;
    private TextView connection;
    private TextView duration;
    private TextView fps;
    private TextView resolution;
    private TextView details;
    private TextView liveClock;
    private TextView touchState;
    private TextView rotationState;
    private TextView notificationState;
    private Button start;
    private final List<View> lockDuringCapture = new ArrayList<>();

    MirrorUi(MirrorActivity activity) {
        this.activity = activity;
        bridge = activity.bridge;
        settings = activity.settings;
        light = settings.lightTheme();
        background = color(light ? "#F2F6F8" : "#0A121A");
        cardColor = color(light ? "#FFFFFF" : "#14212D");
        textColor = color(light ? "#142E3D" : "#F1F6FA");
        muted = color(light ? "#526979" : "#A4B7C6");
        accent = color(light ? "#087A6B" : "#6BE3C7");
        line = color(light ? "#DBE6EC" : "#293C4B");
    }

    void render(int tab) {
        statusTitle = statusDetail = connection = duration = fps = resolution = details = liveClock = null;
        touchState = rotationState = notificationState = null;
        start = null;
        lockDuringCapture.clear();
        LinearLayout root = column();
        root.setBackgroundColor(background);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });
        LinearLayout header = row();
        header.setPadding(dp(22),dp(14),dp(22),dp(12));
        LinearLayout brand = column();
        TextView title = label(brand,"My Screen Mirror",21,textColor,true);
        title.setTextDirection(View.TEXT_DIRECTION_LTR);
        label(brand,"گوشی تو، روی نمایشگر خودرو",12,muted,false);
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        TextView version = new TextView(activity);
        version.setText("v" + BuildConfig.VERSION_NAME);
        version.setTextSize(11);
        version.setTextColor(accent);
        version.setPadding(dp(10),dp(7),dp(10),dp(7));
        version.setBackground(shape(light ? color("#E6F5EF") : color("#1D3B37"),12,0));
        version.setTextDirection(View.TEXT_DIRECTION_LTR);
        header.addView(version);
        root.addView(header);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout body = column();
        body.setPadding(dp(20),dp(5),dp(20),dp(20));
        scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if (tab == 0) home(body);
        else if (tab == 1) preferences(body);
        else if (tab == 2) access(body);
        else help(body);

        LinearLayout navigation = row();
        navigation.setPadding(dp(10),dp(8),dp(10),dp(8));
        navigation.setBackground(shape(cardColor,0,0));
        String[] names = {"خانه", "تنظیمات", "دسترسی‌ها", "راهنما"};
        String[] symbols = {"◈", "☷", "◇", "؟"};
        for (int i=0;i<names.length;i++) {
            final int selected = i;
            TextView item = new TextView(activity);
            item.setText(symbols[i] + "\n" + names[i]);
            item.setTextSize(13);
            item.setGravity(Gravity.CENTER);
            item.setMinHeight(dp(58));
            item.setTextColor(i == tab ? accent : muted);
            item.setTypeface(Typeface.DEFAULT,i == tab ? Typeface.BOLD : Typeface.NORMAL);
            if (i == tab) item.setBackground(shape(light ? color("#E6F5EF") : color("#1B3936"),16,0));
            item.setContentDescription(names[i]);
            item.setSelected(i == tab);
            item.setFocusable(true);
            item.setOnClickListener(v -> activity.showTab(selected));
            navigation.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(navigation);
        activity.setContentView(root);
        update();
    }

    private void home(LinearLayout body) {
        LinearLayout hero = card(body);
        connection = label(hero,"",12,accent,true);
        hero.addView(new MirrorArtwork(activity,light),new LinearLayout.LayoutParams(-1,dp(135)));
        statusTitle = label(hero,"",25,textColor,true);
        statusDetail = label(hero,"",14,muted,false);
        LinearLayout stats = row();
        stats.setPadding(0,dp(12),0,dp(6));
        duration = metric(stats,"مدت نشست");
        fps = metric(stats,"فریم ارسالی / ثانیه");
        resolution = metric(stats,"نمایشگر خودرو");
        hero.addView(stats);
        CheckBox parked = new CheckBox(activity);
        parked.setText("خودرو متوقف است");
        parked.setTextSize(14);
        parked.setTextColor(textColor);
        parked.setMinHeight(dp(48));
        parked.setButtonTintList(ColorStateList.valueOf(accent));
        parked.setChecked(activity.parked);
        parked.setOnCheckedChangeListener((view,checked) -> {
            activity.parked = checked;
            if (!checked) activity.stopCapture();
            update();
        });
        hero.addView(parked);
        start = button(hero,"شروع میرور",true,activity::startOrStop);
        TextView privacy = label(hero,"هر بار با اجازهٔ تو  •  تصویر ذخیره نمی‌شود",11,muted,false);
        privacy.setGravity(Gravity.CENTER);

        section(body,"چه چیزی را نمایش بدهیم؟");
        LinearLayout modes = row();
        Button all = compactChoice("کل گوشی",!settings.singleApp(),() -> selectMode(false));
        Button single = compactChoice("فقط یک برنامه",settings.singleApp(),() -> selectMode(true));
        modes.addView(all,new LinearLayout.LayoutParams(0,dp(52),1));
        LinearLayout.LayoutParams second = new LinearLayout.LayoutParams(0,dp(52),1);
        second.setMarginStart(dp(8));
        modes.addView(single,second);
        lockDuringCapture.add(all); lockDuringCapture.add(single);
        body.addView(modes);
        label(body,settings.singleApp() ? "یک برنامه را در پنجرهٔ اندروید انتخاب می‌کنی؛ کنترل لمس این حالت خاموش است."
                : "همهٔ صفحه و احتمالاً اعلان‌ها دیده می‌شوند؛ کنترل لمس اختیاری است.",12,muted,false);
        LinearLayout tip = card(body);
        label(tip,"یک شروع ساده",17,textColor,true);
        label(tip,"۱. گوشی را به Android Auto وصل کن.\n۲. My Screen Mirror را روی مانیتور باز کن.\n۳. اینجا شروع را بزن و اشتراک صفحه را تأیید کن.",14,muted,false);
        button(tip,"بررسی دسترسی‌های اختیاری",false,() -> activity.showTab(2));
    }

    private void preferences(LinearLayout body) {
        pageTitle(body,"تصویر، به سلیقهٔ تو","تنظیم کیفیت و حالت اشتراک در شروع بعدی اعمال می‌شود.");
        LinearLayout quality = card(body);
        label(quality,"کیفیت تصویر",18,textColor,true);
        String[] titles = {"سبک", "متعادل", "کیفیت بالا"};
        String[] descriptions = {"۷۲۰ پیکسل • سقف ۸ فریم؛ پردازش کمتر", "۹۶۰ پیکسل • سقف ۱۵ فریم؛ انتخاب پیش‌فرض", "۱۲۸۰ پیکسل • سقف ۲۴ فریم؛ پردازش بیشتر"};
        for (int i=0;i<3;i++) {
            final int index=i;
            Button item = option(quality,titles[i],descriptions[i],settings.quality()==i,() -> {
                settings.prefs.edit().putInt("quality",index).apply(); render(activity.tab);
            });
            lockDuringCapture.add(item);
        }
        label(quality,"اعداد، سقف ارسال‌اند؛ نرخ واقعی به گوشی و Android Auto بستگی دارد.",12,muted,false);
        LinearLayout display = card(body);
        label(display,"قاب و جهت تصویر",18,textColor,true);
        toggle(display,"پرکردن قاب","کناره‌های تصویر ممکن است بریده شوند؛ نسبت تصویر تغییر نمی‌کند.",settings.fill(),false,value -> {
            settings.flag("fill",value); bridge.resetMapping();
        });
        Switch landscape = toggle(display,"افقی شدن خودکار","فقط برای اشتراک کل گوشی؛ بعضی اپ‌ها همچنان عمودی می‌مانند.",settings.landscape(),true,value -> {
            settings.flag("landscape",value);
            if (value && !Settings.System.canWrite(activity)) activity.openRotationPermission();
        });
        landscape.setEnabled(!settings.singleApp());
        landscape.setTag("whole-only");
        if (settings.singleApp()) label(display,"برای این گزینه، از خانه حالت «کل گوشی» را انتخاب کن.",12,muted,false);
        button(display,"بازگرداندن چرخش قبلی گوشی",false,activity::restoreRotation);
        LinearLayout session = card(body);
        label(session,"توقف خودکار",18,textColor,true);
        label(session,"پس از زمان انتخاب‌شده نشست متوقف می‌شود؛ برای شروع دوباره اجازهٔ تازه لازم است.",13,muted,false);
        int[] minutes = {0,15,30};
        for (int i : minutes) {
            Button option = option(session,i == 0 ? "بدون زمان‌بندی" : i + " دقیقه", "", settings.stopMinutes()==i,() -> {
                settings.prefs.edit().putInt("stop_minutes",i).apply(); render(activity.tab);
            });
            lockDuringCapture.add(option);
        }
        LinearLayout appearance = card(body);
        label(appearance,"ظاهر برنامه",18,textColor,true);
        LinearLayout row = row();
        row.addView(compactChoice("تیره",!light,() -> activity.setThemeChoice(false)),new LinearLayout.LayoutParams(0,dp(50),1));
        row.addView(compactChoice("روشن",light,() -> activity.setThemeChoice(true)),new LinearLayout.LayoutParams(0,dp(50),1));
        appearance.addView(row);
    }

    private void access(LinearLayout body) {
        pageTitle(body,"کنترل دست خودت است","برای نمایش تصویر، فقط اجازهٔ اشتراک صفحه لازم است. بقیه اختیاری‌اند.");
        LinearLayout capture = card(body);
        label(capture,"۰۱  اشتراک صفحه",18,textColor,true);
        label(capture,"اندروید در شروع هر نشست از خودت اجازه می‌خواهد. اپ نمی‌تواند این مرحله را به‌جای تو تأیید کند.",14,muted,false);
        button(capture,"رفتن به شروع میرور",false,() -> activity.showTab(0));
        LinearLayout touch = card(body);
        label(touch,"۰۲  لمس مانیتور",18,textColor,true);
        touchState = label(touch,"",13,accent,true);
        Switch control = toggle(touch,"انتقال لمس به گوشی","کلیک، کشیدن، خانه و برگشت؛ فقط هنگام اشتراک کل گوشی.",settings.touch(),false,activity::setTouch);
        if (settings.singleApp()) label(touch,"در حالت تک‌برنامه، لمس حتی با مجوز فعال فرستاده نمی‌شود.",12,muted,false);
        button(touch,"باز کردن تنظیمات دسترس‌پذیری",false,activity::openAccessibility);
        label(touch,"پیام «دسترسی رد شد» دیدی؟ اطلاعات اپ را باز کن، از سه‌نقطه Allow restricted settings را بزن؛ سپس دوباره سرویس را روشن کن.",13,muted,false);
        button(touch,"اطلاعات اپ و رفع محدودیت",false,activity::openAppInfo);
        LinearLayout rotation = card(body);
        label(rotation,"۰۳  چرخش افقی",18,textColor,true);
        rotationState = label(rotation,"",13,accent,true);
        label(rotation,"اجازهٔ تغییر تنظیمات فقط برای جهت صفحه استفاده می‌شود؛ تنظیم قبلی بعد از توقف بازگردانی می‌شود.",14,muted,false);
        button(rotation,"تنظیم اجازهٔ چرخش",false,activity::openRotationPermission);
        LinearLayout notifications = card(body);
        label(notifications,"۰۴  اعلان نشست",18,textColor,true);
        notificationState = label(notifications,"",13,accent,true);
        label(notifications,"برای دیدن وضعیت اشتراک و توقف سریع از اعلان. رد کردن اعلان، اجازهٔ اشتراک صفحه نیست و آن را فعال نمی‌کند.",14,muted,false);
        button(notifications,"تنظیمات اعلان اپ",false,() -> activity.open(new android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE,activity.getPackageName())));
    }

    private void help(LinearLayout body) {
        pageTitle(body,"راهنما و پشتیبانی","اولین تست را با دکمهٔ خود برنامه انجام بده.");
        LinearLayout test = card(body);
        label(test,"آزمایش لمس",18,textColor,true);
        label(test,"میرور کل گوشی و کنترل لمس را روشن کن؛ همین دکمه را از مانیتور خودرو بزن. عدد باید روی گوشی زیاد شود.",14,muted,false);
        Button testButton = button(test,"لمس آزمایشی: " + activity.testTaps,false,() -> { });
        testButton.setOnClickListener(v -> testButton.setText("لمس آزمایشی: " + (++activity.testTaps)));
        liveClock = label(test,"",20,accent,true);
        liveClock.setTextDirection(View.TEXT_DIRECTION_LTR);
        LinearLayout faq = card(body);
        label(faq,"سؤال‌های رایج",18,textColor,true);
        answer(faq,"اپ در فهرست ماشین نیست؟","نصب عادی APK ممکن است برای Android Auto کافی نباشد. راهنمای نصب GitHub را بخوان؛ گزینهٔ Unknown sources برای این نوع اپ به‌تنهایی کافی نیست.");
        answer(faq,"تصویر حاشیه دارد یا عمودی است؟","اول حالت افقی داخل پخش‌کننده را امتحان کن. تنظیم افقی خودکار همهٔ اپ‌ها را مجبور به چرخش نمی‌کند. پرکردن قاب هم کناره‌ها را می‌بُرد و جای چرخش واقعی نیست.");
        answer(faq,"فقط یک برنامه را می‌خواهم","در خانه «فقط یک برنامه» را انتخاب کن. پنجرهٔ انتخاب اندروید باز می‌شود؛ در این حالت لمس و چرخش خودکار خاموش‌اند.");
        answer(faq,"تصویر سیاه است یا صدا ندارم؟","برنامه‌های حساس یا محتوای DRM ممکن است تصویر سیاه بدهند؛ این حفاظت دور زده نمی‌شود. صدای مستقل در این نسخه ضبط نمی‌شود؛ مسیر عادی صدا در گوشی و Android Auto را بررسی کن.");
        answer(faq,"کشیدن روی مانیتور عمل نمی‌کند؟","دسترسی لمس و اشتراک کل گوشی لازم‌اند. اگر میزبان دکمهٔ جابه‌جایی/فلش چهارسو نشان می‌دهد، آن را فعال کن. همهٔ خودروها همهٔ ژست‌ها را نمی‌فرستند.");
        LinearLayout support = card(body);
        label(support,"گزارش فنی",18,textColor,true);
        details = label(support,"",12,muted,false);
        button(support,"کپی گزارش",false,activity::copyReport);
        button(support,"اشتراک فایل گزارش",false,activity::shareReport);
        label(support,"گزارش شامل تصویر، متن صفحه، VIN یا شناسهٔ گوشی نیست. مقصد اشتراک را خودت انتخاب می‌کنی.",12,muted,false);
        LinearLayout about = card(body);
        label(about,"دربارهٔ My Screen Mirror",18,textColor,true);
        label(about,"ساخته‌شده برای آزمایش روی تیگو ۷ PHEV. این نسخه از GitHub منتشر می‌شود و محصول رسمی گوگل یا سازندهٔ خودرو نیست. لمس و چرخش هنوز به تست روی ترکیب گوشی و ماشین تو نیاز دارند.",14,muted,false);
        button(about,"حریم خصوصی و دسترسی‌ها",false,() -> new AlertDialog.Builder(activity).setTitle("حریم خصوصی")
                .setMessage("تصویر و ویدیو در حافظه پردازش می‌شوند و ذخیره یا آپلود نمی‌شوند. اپ دسترسی اینترنت، میکروفن، دوربین یا دادهٔ خودرو ندارد. سرویس لمس، متن و ساختار پنجره‌ها را نمی‌خواند.\n\nگزارش فنی فقط با انتخاب تو به اشتراک گذاشته می‌شود و یک نسخه در cache اپ باقی می‌ماند؛ از تنظیمات گوشی می‌توانی cache را پاک کنی.\n\nکل صفحه می‌تواند اعلان‌ها را نشان دهد. برای محدودکردن تصویر، حالت تک‌برنامه را انتخاب کن. اشتراک را از اعلان، گوشی یا خروج از اپ ماشین متوقف کن.")
                .setPositiveButton("متوجه شدم",null).show());
        button(about,"نسخه‌ها و دانلود در GitHub",false,() -> activity.openUrl("/releases"));
        button(about,"گزارش مشکل در GitHub",false,() -> activity.openUrl("/issues"));
        label(about,"نسخهٔ " + BuildConfig.VERSION_NAME + " • فقط در خودروی متوقف استفاده کن",12,muted,false);
    }

    void update() {
        boolean active = bridge.capturing;
        boolean pending = activity.pending();
        if (connection != null) set(connection,bridge.hasSurface() ? "● صفحهٔ اپ روی خودرو باز است"
                : activity.projectionConnected() ? "● Android Auto متصل است" : "○ در انتظار اتصال Android Auto");
        if (statusTitle != null) set(statusTitle,active ? "تصویرت در حال اشتراک است" : pending ? "منتظر تأیید اندروید" : "آمادهٔ یک نمایش بزرگ‌تر؟");
        if (statusDetail != null) set(statusDetail,!bridge.error.isEmpty() ? "ارسال تصویر با مشکل روبه‌رو شد؛ گزارش فنی در راهنماست."
                : active ? (bridge.hasSurface() ? "برای پایان، توقف را بزن یا از صفحهٔ اپ روی ماشین خارج شو." : "اشتراک روشن است؛ My Screen Mirror را روی مانیتور باز کن.")
                : pending ? "پنجرهٔ سیستم را روی گوشی تأیید کن؛ تصویر خودکار شروع نمی‌شود."
                : "تصویر گوشی را با اجازهٔ خودت روی نمایشگر خودرو ببین.");
        long now = SystemClock.elapsedRealtime();
        if (duration != null) set(duration,SessionMetrics.clock(bridge.metrics.durationMs(now)));
        if (fps != null) set(fps,String.format(Locale.US,"%.1f",bridge.metrics.sample(now,bridge.framesShown)));
        if (resolution != null) set(resolution,bridge.hostSize());
        if (start != null) {
            set(start,active ? "توقف میرور" : pending ? "در انتظار اجازه…" : "شروع میرور");
            start.setEnabled(active || (!pending && activity.parked));
            start.setAlpha(start.isEnabled() ? 1f : .55f);
        }
        for (View view : lockDuringCapture) {
            view.setEnabled(!active && !pending && !("whole-only".equals(view.getTag()) && settings.singleApp()));
            view.setAlpha(view.isEnabled() ? 1f : .5f);
        }
        if (touchState != null) set(touchState,MirrorTouchService.connected() ? "سرویس لمس متصل است؛ استفاده با انتخاب خودت" : "دسترسی هنوز فعال نشده؛ نمایش تصویر بدون آن ممکن است");
        if (rotationState != null) set(rotationState,Settings.System.canWrite(activity) ? "اجازه داده شده" : "اجازه داده نشده؛ اختیاری");
        if (notificationState != null) set(notificationState,Build.VERSION.SDK_INT < 33
                || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED ? "اعلان مجاز است" : "اعلان مجاز نیست؛ از داخل اپ می‌توانی توقف کنی");
        if (details != null) set(details,bridge.describe());
        if (liveClock != null) set(liveClock,new java.text.SimpleDateFormat("HH:mm:ss",Locale.US).format(new java.util.Date()));
        boolean keepAwake = active && !activity.isFinishing();
        if (keepAwake) activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void selectMode(boolean single) {
        if (bridge.capturing || activity.pending()) return;
        settings.flag("single_app",single); render(activity.tab);
    }
    private void answer(LinearLayout parent,String title,String answer) {
        button(parent,title,false,() -> new AlertDialog.Builder(activity).setTitle(title).setMessage(answer).setPositiveButton("متوجه شدم",null).show());
    }
    private void pageTitle(LinearLayout body,String title,String subtitle) {
        label(body,title,25,textColor,true); label(body,subtitle,14,muted,false);
    }
    private TextView metric(LinearLayout parent,String title) {
        LinearLayout item = column();
        item.setGravity(Gravity.CENTER);
        TextView value = label(item,"—",16,textColor,true);
        value.setTextDirection(View.TEXT_DIRECTION_LTR); value.setGravity(Gravity.CENTER);
        TextView caption = label(item,title,10,muted,false); caption.setGravity(Gravity.CENTER);
        parent.addView(item,new LinearLayout.LayoutParams(0,-2,1));
        return value;
    }
    private void section(LinearLayout parent,String title) { TextView text=label(parent,title,16,textColor,true); text.setPadding(0,dp(20),0,dp(10)); }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout card=column(); card.setPadding(dp(18),dp(14),dp(18),dp(14));
        card.setBackground(shape(cardColor,22,line));
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.setMargins(0,dp(12),0,0);
        parent.addView(card,params); return card;
    }
    private TextView label(LinearLayout parent,String text,int size,int color,boolean bold) {
        TextView view=new TextView(activity); view.setText(text); view.setTextSize(size); view.setTextColor(color);
        view.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        view.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG); view.setGravity(Gravity.START);
        view.setLineSpacing(dp(3),1.05f); view.setPadding(0,dp(4),0,dp(4));
        parent.addView(view,new LinearLayout.LayoutParams(-1,-2)); return view;
    }
    private Button button(LinearLayout parent,String label,boolean primary,Runnable action) {
        Button button=baseButton(label,primary);
        button.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2); params.setMargins(0,dp(8),0,0);
        parent.addView(button,params); return button;
    }
    private Button compactChoice(String label,boolean selected,Runnable action) {
        Button button=baseButton(label,selected); button.setOnClickListener(v -> action.run()); return button;
    }
    private Button option(LinearLayout parent,String title,String detail,boolean selected,Runnable action) {
        return button(parent,(selected?"●  ":"○  ") + title + (detail.isEmpty()?"":"\n"+detail),false,action);
    }
    private Button baseButton(String label,boolean primary) {
        Button button=new Button(activity); button.setText(label); button.setAllCaps(false); button.setTextSize(14);
        button.setMinHeight(dp(50)); button.setMinimumHeight(dp(50)); button.setPadding(dp(12),dp(12),dp(12),dp(12));
        button.setTextColor(primary ? (light?Color.WHITE:color("#0B2725")) : textColor);
        button.setTypeface(Typeface.DEFAULT,primary?Typeface.BOLD:Typeface.NORMAL);
        int fill=primary?accent:(light?color("#F1F6F8"):color("#1C2D3B"));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(light?color("#22000000"):color("#22FFFFFF")),shape(fill,14,primary?0:line),null));
        return button;
    }
    private Switch toggle(LinearLayout parent,String title,String detail,boolean selected,boolean locked,java.util.function.Consumer<Boolean> action) {
        Switch control=new Switch(activity); control.setText(title); control.setTextSize(14); control.setTextColor(textColor);
        control.setMinHeight(dp(52)); control.setChecked(selected); control.setSwitchPadding(dp(12));
        control.setOnCheckedChangeListener((view,value) -> action.accept(value));
        parent.addView(control,new LinearLayout.LayoutParams(-1,-2));
        label(parent,detail,12,muted,false);
        if (locked) lockDuringCapture.add(control);
        return control;
    }
    private LinearLayout column() { LinearLayout view=new LinearLayout(activity); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view=new LinearLayout(activity); view.setOrientation(LinearLayout.HORIZONTAL); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private GradientDrawable shape(int fill,int radius,int stroke) {
        GradientDrawable drawable=new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radius));
        if (stroke!=0) drawable.setStroke(dp(1),stroke); return drawable;
    }
    private void set(TextView view,String value) { if (!value.contentEquals(view.getText())) view.setText(value); }
    private int dp(int value) { return Math.round(value*activity.getResources().getDisplayMetrics().density); }
    private static int color(String hex) { return Color.parseColor(hex); }
}
