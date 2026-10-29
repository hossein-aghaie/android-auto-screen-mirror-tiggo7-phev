package dev.tiggo.screenmirror;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import android.view.View;

/** Resolution-independent brand illustration without captured or simulated user data. */
final class MirrorArtwork extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final boolean light;
    public MirrorArtwork(Context context) { this(context,false); }
    MirrorArtwork(Context context, boolean light) { super(context); this.light = light; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        float scale = Math.min(getWidth() / 320f, getHeight() / 142f);
        canvas.translate((getWidth() - 320 * scale) / 2, (getHeight() - 142 * scale) / 2);
        canvas.scale(scale, scale);
        int accent = Color.rgb(99,223,196);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(light ? Color.rgb(224,245,240) : Color.rgb(21,51,51));
        canvas.drawOval(28,12,290,134,paint);
        paint.setColor(light ? Color.rgb(25,55,63) : Color.rgb(47,73,86));
        canvas.drawRoundRect(24,24,248,116,12,12,paint);
        paint.setColor(light ? Color.WHITE : Color.rgb(9,20,29));
        canvas.drawRoundRect(30,30,242,109,8,8,paint);
        paint.setColor(accent);
        canvas.drawRoundRect(43,42,151,47,2,2,paint);
        paint.setColor(light ? Color.rgb(214,229,237) : Color.rgb(37,58,73));
        canvas.drawRoundRect(43,57,195,84,5,5,paint);
        paint.setColor(accent);
        canvas.drawCircle(58,70,5,paint);
        canvas.drawRoundRect(72,65,153,69,2,2,paint);
        canvas.drawRoundRect(72,74,128,78,2,2,paint);
        paint.setColor(light ? Color.rgb(25,55,63) : Color.rgb(47,73,86));
        canvas.drawRoundRect(122,116,150,123,2,2,paint);
        canvas.drawRoundRect(102,123,171,128,2,2,paint);
        paint.setColor(accent);
        canvas.drawRoundRect(224,10,283,125,10,10,paint);
        paint.setColor(Color.rgb(11,28,36));
        canvas.drawRoundRect(229,16,278,119,6,6,paint);
        paint.setColor(accent);
        canvas.drawRoundRect(243,21,263,24,2,2,paint);
        canvas.drawRoundRect(237,44,269,81,6,6,paint);
        paint.setColor(Color.rgb(11,28,36));
        canvas.drawCircle(253,62,8,paint);
        paint.setColor(accent);
        canvas.drawCircle(192,98,3,paint);
        canvas.drawCircle(205,98,3,paint);
        canvas.drawCircle(218,98,3,paint);
        canvas.restore();
    }
}
