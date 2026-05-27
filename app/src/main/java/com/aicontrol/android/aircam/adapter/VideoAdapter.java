package com.aicontrol.android.aircam.adapter;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.aicontrol.android.R;
import java.io.File;
import java.util.List;

/* loaded from: classes5.dex */
public class VideoAdapter extends BaseMediaAdapter<VideoAdapter.ViewHolder> {
    private Context context;
    private OnFileDelete onFileDelete;
    private OnFileUpload onFileUpload;
    private OnItemClickListener onItemClickListener;
    private OnItemLongClickListener onItemLongClickListener;
    private List<File> videos;

    public interface OnFileDelete {
        void onFileDelete(int i);
    }

    public interface OnFileUpload {
        void onFileUpload(int i);
    }

    public interface OnItemClickListener {
        void onItemClick(int i);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(int i);
    }

    public VideoAdapter(Context context, List<File> list, OnFileDelete onFileDelete, OnFileUpload onFileUpload, int i) {
        this.context = context;
        this.videos = list;
        this.onFileDelete = onFileDelete;
        this.onFileUpload = onFileUpload;
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        return new ViewHolder(LayoutInflater.from(this.context).inflate(R.layout.item_video, viewGroup, false));
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public void onBindViewHolder(ViewHolder viewHolder, final int i) {
        String replace;
        String absolutePath = this.videos.get(i).getAbsolutePath();
        if (absolutePath.endsWith(".dat")) {
            replace = absolutePath.replace(".dat", ".jpg").replace("/video/", "/.videoPic/");
        } else {
            replace = absolutePath.endsWith(".mp4") ? absolutePath.replace(".mp4", ".jpg").replace("/video/", "/.videoPic/") : "";
        }
        File file = new File(replace);
        if (file.exists()) {
            Glide.with(this.context).load(Uri.fromFile(file)).placeholder(R.mipmap.ic_stub).error(R.mipmap.ic_stub).into(viewHolder.imgPic);
        } else {
            viewHolder.imgPic.setImageResource(R.mipmap.ic_stub);
        }
        if (this.selectionMode) {
            viewHolder.checkBox.setVisibility(0);
            viewHolder.imgDel.setVisibility(8);
            viewHolder.overlay.setVisibility(this.selectedPositions.contains(Integer.valueOf(i)) ? 0 : 8);
            viewHolder.checkBox.setChecked(this.selectedPositions.contains(Integer.valueOf(i)));
        } else {
            viewHolder.checkBox.setVisibility(8);
            viewHolder.imgDel.setVisibility(8);
            viewHolder.overlay.setVisibility(8);
        }
        viewHolder.checkBox.setOnClickListener(new View.OnClickListener() { // from class: com.tzh.wifi.wificam.adapter.VideoAdapter$$ExternalSyntheticLambda0
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                VideoAdapter.this.m3890xfebaee38(i, view);
            }
        });
        viewHolder.itemView.setOnClickListener(new View.OnClickListener() { // from class: com.tzh.wifi.wificam.adapter.VideoAdapter$$ExternalSyntheticLambda1
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                VideoAdapter.this.m3891xe3fc5cf9(i, view);
            }
        });
        viewHolder.itemView.setOnLongClickListener(new View.OnLongClickListener() { // from class: com.tzh.wifi.wificam.adapter.VideoAdapter$$ExternalSyntheticLambda2
            @Override // android.view.View.OnLongClickListener
            public final boolean onLongClick(View view) {
                return VideoAdapter.this.m3892xc93dcbba(i, view);
            }
        });
    }

    /* renamed from: lambda$onBindViewHolder$0$com-tzh-wifi-wificam-adapter-VideoAdapter, reason: not valid java name */
    /* synthetic */ void m3890xfebaee38(int i, View view) {
        toggleSelection(i);
    }

    /* renamed from: lambda$onBindViewHolder$1$com-tzh-wifi-wificam-adapter-VideoAdapter, reason: not valid java name */
    /* synthetic */ void m3891xe3fc5cf9(int i, View view) {
        if (this.selectionMode) {
            toggleSelection(i);
            return;
        }
        OnItemClickListener onItemClickListener = this.onItemClickListener;
        if (onItemClickListener != null) {
            onItemClickListener.onItemClick(i);
        }
    }

    /* renamed from: lambda$onBindViewHolder$2$com-tzh-wifi-wificam-adapter-VideoAdapter, reason: not valid java name */
    /* synthetic */ boolean m3892xc93dcbba(int i, View view) {
        OnItemLongClickListener onItemLongClickListener = this.onItemLongClickListener;
        if (onItemLongClickListener == null) {
            return false;
        }
        onItemLongClickListener.onItemLongClick(i);
        return true;
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public int getItemCount() {
        return this.videos.size();
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    public void setOnItemLongClickListener(OnItemLongClickListener onItemLongClickListener) {
        this.onItemLongClickListener = onItemLongClickListener;
    }

    @Override // com.tzh.wifi.wificam.adapter.BaseMediaAdapter
    public void selectAll(int i) {
        super.selectAll(this.videos.size());
    }

    public void selectAll() {
        super.selectAll(this.videos.size());
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        public CheckBox checkBox;
        public ImageView imgDel;
        public ImageView imgPic;
        public View overlay;
        public TextView tvName;

        public ViewHolder(View view) {
            super(view);
            this.imgPic = (ImageView) view.findViewById(R.id.fileImagesIcon);
            this.imgDel = (ImageView) view.findViewById(R.id.fileDeleteIcon);
            this.tvName = (TextView) view.findViewById(R.id.fileNameTxt);
            this.checkBox = (CheckBox) view.findViewById(R.id.fileCheckBox);
            this.overlay = view.findViewById(R.id.overlay);
        }
    }
}
