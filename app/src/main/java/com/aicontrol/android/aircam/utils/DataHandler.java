package com.aicontrol.android.aircam.utils;

import android.content.Context;
import android.graphics.Bitmap;
import com.aicontrol.android.aircam.bean.FilterEffect;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// TODO: GPUImage filters disabled - needs jp.co.cyberagent.android:gpuimage library
// Re-enable when GPUImage dependency is properly configured

/* loaded from: classes5.dex */
public class DataHandler {
    public static final float DIFF_DEGREE = 30.0f;
    public static final float DIFF_DEGREE_INNER = 35.0f;
    public static final double UNIT = 0.017453292519943295d;
    public static List<FilterEffect> filters = new ArrayList();

    public static void initFilters(Context context) {
        filters.clear();
        // TODO: Re-enable GPUImage filters
        // String str = "zh".equals(context.getResources().getConfiguration().locale.getLanguage()) ? "_cn" : "_en";
        // filters.add(new FilterEffect("Normal", 0, 0));
    }

    public static List<Bitmap> getSmallPic(Context context, Bitmap bitmap) {
        // TODO: Re-enable GPUImage filter preview
        ArrayList<Bitmap> arrayList = new ArrayList();
        arrayList.add(bitmap);
        return arrayList;
    }

    public static void recycleBitmap(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        bitmap.recycle();
    }

    public static boolean collectionNotEmpty(Collection<?> collection) {
        return collection != null && collection.size() >= 0;
    }
}
