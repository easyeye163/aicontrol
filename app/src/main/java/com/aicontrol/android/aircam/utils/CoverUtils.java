package com.aicontrol.android.aircam.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import java.util.Random;

/* loaded from: classes5.dex */
public class CoverUtils {
    private static final int[] COLORS = {-769226, -1499549, -6543440, -10011977, -12627531, -14575885, -16537100, -16728876, -16738680, -11751600, -7617718, -3285959, -5317, -16121, -26624, -43230, -8825528, -6381922, -10453621};

    public static Bitmap generateGradientCover(String str, int i, int i2) {
        String str2 = str == null ? "Music" : str;
        Random random = new Random(str2.hashCode());
        int[] iArr = COLORS;
        int i3 = iArr[random.nextInt(iArr.length)];
        int i4 = iArr[random.nextInt(iArr.length)];
        while (i3 == i4) {
            int[] iArr2 = COLORS;
            i4 = iArr2[random.nextInt(iArr2.length)];
        }
        Bitmap createBitmap = Bitmap.createBitmap(i, i2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(createBitmap);
        Paint paint = new Paint(1);
        float f = i;
        float f2 = i2;
        paint.setShader(new LinearGradient(0.0f, 0.0f, f, f2, i3, i4, Shader.TileMode.CLAMP));
        canvas.drawRect(0.0f, 0.0f, f, f2, paint);
        String upperCase = str2.trim().isEmpty() ? "M" : str2.trim().substring(0, 1).toUpperCase();
        paint.setShader(null);
        paint.setColor(-1);
        paint.setTextSize(Math.min(i, i2) * 0.5f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fontMetrics = paint.getFontMetrics();
        canvas.drawText(upperCase, i / 2, (int) (((i2 / 2) - (fontMetrics.top / 2.0f)) - (fontMetrics.bottom / 2.0f)), paint);
        return createBitmap;
    }
}
