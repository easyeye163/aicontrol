package com.aicontrol.android.aircam.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.AsyncTask;
// TODO: R references need updating - WiFiApp not available
import java.util.List;

/* loaded from: classes5.dex */
public abstract class ImageCreator {
    public Context context;

    public abstract void getSmallImage(List<Bitmap> list);

    public ImageCreator(Context context) {
        this.context = context;
    }

    public void loadImage(String str) {
        Bitmap decodeFile = BitmapFactory.decodeFile(str);
        int dip2px = dip2px(this.context, 80);
        new SmallPicTask().execute(ThumbnailUtils.extractThumbnail(decodeFile, dip2px, dip2px));
    }

    public static int dip2px(Context ctx, int i) {
        return (int) ((i * ctx.getResources().getDisplayMetrics().density) + 0.5f);
    }

    public static int dip2px(int i) {
        return (int) ((i * 2.0f) + 0.5f);
    }

    private class SmallPicTask extends AsyncTask<Bitmap, Void, List<Bitmap>> {
        private SmallPicTask() {
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public List<Bitmap> doInBackground(Bitmap[] bitmapArr) {
            return DataHandler.getSmallPic(ImageCreator.this.context, bitmapArr[0]);
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onPostExecute(List<Bitmap> list) {
            ImageCreator.this.getSmallImage(list);
        }
    }
}
