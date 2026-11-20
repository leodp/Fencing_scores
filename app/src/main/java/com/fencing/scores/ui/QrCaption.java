package com.fencing.scores.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

final class QrCaption {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss", Locale.ROOT);
    private final String title;
    private final String timestamp = LocalDateTime.now().format(DATE);

    QrCaption(String title) { this.title = title; }

    Bitmap beside(Bitmap qr) {
        int side = qr.getWidth();
        int strip = Math.max(1, side / 7);
        Bitmap result = Bitmap.createBitmap(side + strip, side, Bitmap.Config.ARGB_8888);
        draw(new Canvas(result), qr, 0, 0, strip, side);
        return result;
    }

    void draw(Canvas canvas, Bitmap qr, int left, int top, int strip, int side) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        canvas.drawRect(left, top, left + strip, top + side, p);
        p.setColor(Color.DKGRAY);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(32);
        float lineHeight = p.descent() - p.ascent();
        float longest = Math.max(p.measureText(title), p.measureText(timestamp));
        p.setTextSize(32 * Math.min(side * 0.94f / longest, strip * 0.44f / lineHeight));
        canvas.save();
        canvas.translate(left + strip / 2f, top + side / 2f);
        canvas.rotate(-90);
        canvas.drawText(title, 0, -p.descent(), p);
        canvas.drawText(timestamp, 0, -p.ascent(), p);
        canvas.restore();
        int x = left + strip;
        Paint pixels = new Paint();
        pixels.setFilterBitmap(false);
        canvas.drawBitmap(qr, null, new Rect(x, top, x + side, top + side), pixels);
    }
}
