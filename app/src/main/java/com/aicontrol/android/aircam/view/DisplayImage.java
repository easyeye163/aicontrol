package com.aicontrol.android.aircam.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;
import com.aicontrol.android.aircam.utils.LogUtils;

/* loaded from: classes5.dex */
public class DisplayImage extends ImageView {
    LogUtils logUtils;
    private Bitmap mBitmap;
    private float scaleVal;

    public DisplayImage(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.scaleVal = 1.0f;
        this.mBitmap = null;
        this.logUtils = LogUtils.setLogger(DisplayImage.class);
    }

    public void setScaleVal(float f) {
        this.scaleVal = f;
    }

    @Override // android.widget.ImageView, android.view.View
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getDrawable() == null) {
            this.logUtils.d("###get drawable failed!");
        }
    }

    public void setShader(Canvas canvas) {
        Bitmap bitmap;
        Drawable drawable = getDrawable();
        if (!(drawable instanceof BitmapDrawable) || (bitmap = ((BitmapDrawable) drawable).getBitmap()) == null) {
            return;
        }
        int width = getWidth();
        int height = getHeight();
        Matrix matrix = new Matrix();
        float f = this.scaleVal;
        matrix.setScale(f, f);
        Bitmap createBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        if (createBitmap == null) {
            return;
        }
        this.logUtils.e("###width:" + width + " height:" + height + " " + createBitmap.getWidth() + "  " + createBitmap.getHeight());
    }
}
