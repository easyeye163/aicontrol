package com.aicontrol.android.aircam.presenter.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.BaseRequestOptions;
import com.bumptech.glide.request.RequestOptions;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.utils.LogUtils;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/* loaded from: classes5.dex */
public class FilesAdapter extends BaseAdapter {
    private Context context;
    private LayoutInflater mInflater;
    private int mediaType;
    private OnFileDelete onFileDelete;
    private OnFileUpload onFileUpload;
    private int screenHeight;
    private int screenWidth;
    private List<File> videos;
    public ViewHolder mViewHolder = null;
    private int count = 0;
    LogUtils logEX = LogUtils.setLogger(FilesAdapter.class);

    public interface OnFileDelete {
        void onFileDelete(int i);
    }

    public interface OnFileUpload {
        void onFileUpload(int i);
    }

    @Override // android.widget.Adapter
    public long getItemId(int i) {
        return i;
    }

    public FilesAdapter(Context context, List<File> list, OnFileDelete onFileDelete, OnFileUpload onFileUpload, int i) {
        this.videos = new ArrayList();
        this.mInflater = null;
        this.onFileDelete = null;
        this.onFileUpload = null;
        this.screenWidth = 0;
        this.screenHeight = 0;
        this.context = context;
        this.onFileDelete = onFileDelete;
        this.onFileUpload = onFileUpload;
        this.videos = list;
        this.mediaType = i;
        DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
        this.screenWidth = displayMetrics.widthPixels;
        this.screenHeight = displayMetrics.heightPixels;
        this.mInflater = LayoutInflater.from(context);
    }

    @Override // android.widget.Adapter
    public int getCount() {
        return this.videos.size();
    }

    @Override // android.widget.Adapter
    public Object getItem(int i) {
        return this.videos.get(i);
    }

    @Override // android.widget.Adapter
    public View getView(int i, View view, ViewGroup viewGroup) {
        int i2;
        if (view == null) {
            this.mViewHolder = new ViewHolder();
            view = this.mInflater.inflate(R.layout.filesadapter, (ViewGroup) null);
            this.mViewHolder.imgPic = (ImageView) view.findViewById(R.id.fileImagesIcon);
            this.mViewHolder.imgDel = (ImageView) view.findViewById(R.id.fileDeleteIcon);
            this.mViewHolder.imgUpload = (ImageView) view.findViewById(R.id.fileUploadIcon);
            this.mViewHolder.tvName = (TextView) view.findViewById(R.id.fileNameTxt);
            view.setLayoutParams(new AbsListView.LayoutParams((this.screenWidth * 200) / 960, (this.screenHeight * 200) / 640));
            this.mViewHolder.imgDel.setOnClickListener(new ClickListener(i));
            this.mViewHolder.imgUpload.setOnClickListener(new ClickUploadListener(i));
            view.setTag(this.mViewHolder);
        } else {
            this.mViewHolder = (ViewHolder) view.getTag();
        }
        File file = this.videos.get(i);
        String name = file.getName();
        if (this.mediaType == 0) {
            this.mViewHolder.tvName.setText(name);
            this.mViewHolder.imgUpload.setVisibility(8);
        } else {
            this.mViewHolder.tvName.setText(name);
        }
        if (viewGroup.getChildCount() == 0 && (i2 = this.count) == 0) {
            this.count = i2 + 1;
            setImage(file);
        } else {
            if (viewGroup.getChildCount() == 0 && this.count > 0) {
                setImage(file);
                return view;
            }
            this.count++;
            setImage(file);
        }
        return view;
    }

    private class ClickListener implements View.OnClickListener {
        private int index;

        public ClickListener(int i) {
            this.index = i;
        }

        @Override // android.view.View.OnClickListener
        public void onClick(View view) {
            if (FilesAdapter.this.onFileDelete != null) {
                FilesAdapter.this.onFileDelete.onFileDelete(this.index);
            }
        }
    }

    private class ClickUploadListener implements View.OnClickListener {
        private int index;

        public ClickUploadListener(int i) {
            this.index = i;
        }

        @Override // android.view.View.OnClickListener
        public void onClick(View view) {
            if (FilesAdapter.this.onFileUpload != null) {
                FilesAdapter.this.onFileUpload.onFileUpload(this.index);
            }
        }
    }

    private void setImage(File file) {
        RequestOptions transform = new RequestOptions().placeholder(R.mipmap.ic_stub).error(R.mipmap.ic_stub).transform(new RoundedCorners(15));
        if (file.getAbsolutePath().endsWith(".jpg")) {
            Glide.with(this.context).load(Uri.fromFile(file)).apply((BaseRequestOptions<?>) transform).into(this.mViewHolder.imgPic);
            return;
        }
        if (file.getAbsolutePath().endsWith(".dat")) {
            File file2 = new File(file.getAbsolutePath().replace(".dat", ".jpg").replace("/video/", "/.videoPic/"));
            if (file2.exists()) {
                Glide.with(this.context).load(Uri.fromFile(file2)).apply((BaseRequestOptions<?>) transform).into(this.mViewHolder.imgPic);
                return;
            } else {
                this.mViewHolder.imgPic.setImageResource(R.mipmap.ic_stub);
                return;
            }
        }
        if (file.getAbsolutePath().endsWith(".mp4")) {
            String replace = file.getAbsolutePath().replace(".mp4", ".jpg").replace("/video/", "/.videoPic/");
            File file3 = new File(replace);
            this.logEX.e("photo file exist! " + replace);
            if (file3.exists()) {
                this.logEX.e("photo file exist! " + replace);
                Glide.with(this.context).load(Uri.fromFile(file3)).apply((BaseRequestOptions<?>) transform).into(this.mViewHolder.imgPic);
                return;
            }
            this.mViewHolder.imgPic.setImageResource(R.mipmap.ic_stub);
        }
    }

    public class VideoThumb extends AsyncTask<File, Integer, Bitmap> {
        private ImageView iv_image;

        public VideoThumb(File file, ImageView imageView) {
            this.iv_image = imageView;
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public Bitmap doInBackground(File... fileArr) {
            return FilesAdapter.this.getVideoThumbnail(fileArr[0].getAbsolutePath());
        }

        @Override // android.os.AsyncTask
        protected void onPreExecute() {
            super.onPreExecute();
        }

        /* JADX INFO: Access modifiers changed from: protected */
        @Override // android.os.AsyncTask
        public void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            if (bitmap != null) {
                this.iv_image.setImageBitmap(bitmap);
            } else {
                this.iv_image.setImageResource(R.mipmap.ic_stub);
            }
        }
    }

    public Bitmap getVideoThumbnail(String str) {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        try {
        } catch (RuntimeException e) {
            e.printStackTrace();
        }
        try {
            try {
                mediaMetadataRetriever.setDataSource(str);
                Bitmap frameAtTime = mediaMetadataRetriever.getFrameAtTime();
                try {
                    mediaMetadataRetriever.release();
                    return frameAtTime;
                } catch (IOException e2) {
                    throw new RuntimeException(e2);
                } catch (RuntimeException e3) {
                    e3.printStackTrace();
                    return frameAtTime;
                }
            } catch (Throwable th) {
                try {
                    mediaMetadataRetriever.release();
                } catch (IOException e4) {
                    throw new RuntimeException(e4);
                } catch (RuntimeException e5) {
                    e5.printStackTrace();
                }
                throw th;
            }
        } catch (IllegalArgumentException e6) {
            e6.printStackTrace();
            try {
                mediaMetadataRetriever.release();
                return null;
            } catch (IOException e7) {
                throw new RuntimeException(e7);
            }
        } catch (RuntimeException e8) {
            e8.printStackTrace();
            try {
                mediaMetadataRetriever.release();
                return null;
            } catch (IOException e9) {
                throw new RuntimeException(e9);
            }
        }
    }

    private class ViewHolder {
        public ImageView imgDel;
        public ImageView imgPic;
        public ImageView imgUpload;
        public TextView tvName;

        private ViewHolder() {
            this.imgPic = null;
            this.imgDel = null;
            this.imgUpload = null;
            this.tvName = null;
        }
    }
}
