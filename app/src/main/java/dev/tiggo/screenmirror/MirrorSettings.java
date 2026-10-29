package dev.tiggo.screenmirror;

import android.content.Context;
import android.content.SharedPreferences;

final class MirrorSettings {
    static final String[] QUALITY_LABELS = {"سبک — 720 / 8 fps", "متعادل — 960 / 15 fps", "بالا — 1280 / 24 fps"};
    static final int[] EDGES = {720, 960, 1280};
    static final int[] FPS = {8, 15, 24};
    final SharedPreferences prefs;
    MirrorSettings(Context context) { prefs = context.getSharedPreferences("mirror", Context.MODE_PRIVATE); }
    int quality() { return Math.max(0, Math.min(2, prefs.getInt("quality", 1))); }
    int edge() { return EDGES[quality()]; }
    int fps() { return FPS[quality()]; }
    boolean touch() { return prefs.getBoolean("touch", false); }
    boolean landscape() { return prefs.getBoolean("landscape", false); }
    boolean fill() { return prefs.getBoolean("fill", false); }
    void flag(String key, boolean value) { prefs.edit().putBoolean(key, value).apply(); }
}
