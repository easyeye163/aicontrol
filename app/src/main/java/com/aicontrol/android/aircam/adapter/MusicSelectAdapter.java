package com.aicontrol.android.aircam.adapter;

import android.content.Context;
import android.content.res.AssetManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.bean.Music;
import com.aicontrol.android.aircam.interfaces.OnMusicItemClickListener;
import com.aicontrol.android.aircam.utils.CoverUtils;
import com.aicontrol.android.aircam.utils.MusicUtils;
import com.aicontrol.android.aircam.utils.StringUtils;
import com.aicontrol.android.aircam.utils.TimeFormater;
import com.aicontrol.android.aircam.model.yuan.FileUtils;
import java.util.List;

/* loaded from: classes5.dex */
public class MusicSelectAdapter extends RecyclerView.Adapter<MusicSelectAdapter.ViewHolder> {
    private AssetManager assetManager;
    public ImageView imPlay;
    public int lastPosition;
    private List<Music> mDatas;
    public MusicUtils mMusicUtils;
    public OnMusicItemClickListener onMusicItemClickListener;
    public int type;

    public class ViewHolder extends RecyclerView.ViewHolder {
        public final Button confirm;
        public final TextView duration;
        public final ImageView imageView;
        public final ImageView play;
        public final TextView position;
        public final TextView song;

        ViewHolder(View view) {
            super(view);
            if (MusicSelectAdapter.this.type == 3) {
                this.position = (TextView) view.findViewById(R.id.item_music_list_position);
                this.song = (TextView) view.findViewById(R.id.item_music_list_song);
                this.duration = (TextView) view.findViewById(R.id.item_music_list_duration);
                this.play = (ImageView) view.findViewById(R.id.item_music_list_start);
                Button button = (Button) view.findViewById(R.id.item_music_list_confirm);
                this.confirm = button;
                this.imageView = (ImageView) view.findViewById(R.id.item_music_icon);
                Context context = view.getContext();
                if (button != null) {
                    button.setText(StringUtils.getInstance(context).strConfirm);
                    return;
                }
                return;
            }
            this.position = (TextView) view.findViewById(R.id.item_popular_music_list_position);
            this.song = (TextView) view.findViewById(R.id.item_popular_music_list_song);
            this.duration = (TextView) view.findViewById(R.id.item_popular_music_list_duration);
            this.play = (ImageView) view.findViewById(R.id.item_popular_music_list_start);
            this.imageView = (ImageView) view.findViewById(R.id.item_popular_music_list_imageview);
            Button button2 = (Button) view.findViewById(R.id.item_popular_music_list_confirm);
            this.confirm = button2;
            Context context2 = view.getContext();
            if (button2 != null) {
                button2.setText(StringUtils.getInstance(context2).strConfirm);
            }
        }
    }

    public void setOnItemClickListener(OnMusicItemClickListener onMusicItemClickListener) {
        this.onMusicItemClickListener = onMusicItemClickListener;
    }

    public MusicSelectAdapter(List<Music> list, MusicUtils musicUtils, int i, AssetManager assetManager) {
        this.mDatas = list;
        this.mMusicUtils = musicUtils;
        this.type = i;
        this.assetManager = assetManager;
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View inflate;
        if (this.type == 3) {
            inflate = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_music_list, viewGroup, false);
        } else {
            inflate = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.item_popular_music_list, viewGroup, false);
        }
        return new ViewHolder(inflate);
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public void onBindViewHolder(ViewHolder viewHolder, final int i) {
        Music music = this.mDatas.get(i);
        if (viewHolder.position != null) {
            viewHolder.position.setText(String.valueOf(i + 1));
        }
        String song = music.getSong();
        if (song != null && song.contains(FileUtils.FILE_EXTENSION_SEPARATOR)) {
            song = song.substring(0, song.lastIndexOf(FileUtils.FILE_EXTENSION_SEPARATOR));
        }
        viewHolder.song.setText(song);
        if (!TextUtils.isEmpty(music.getSinger())) {
            viewHolder.song.append("-" + this.mDatas.get(i).getSinger());
        }
        if (viewHolder.imageView != null) {
            int i2 = this.type;
            if (i2 == 1 || i2 == 2) {
                AssetManager assetManager = this.assetManager;
            }
            int i3 = i2 == 3 ? 120 : 200;
            viewHolder.imageView.setImageBitmap(CoverUtils.generateGradientCover(song, i3, i3));
        }
        viewHolder.duration.setText(TimeFormater.showDurationFormat(music.getDuration()));
        if (viewHolder.play != null) {
            viewHolder.play.setOnClickListener(new View.OnClickListener() { // from class: com.tzh.wifi.wificam.adapter.MusicSelectAdapter.1
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    MusicSelectAdapter.this.handlePlayClick(view, i);
                }
            });
        }
        if (viewHolder.confirm != null) {
            viewHolder.confirm.setOnClickListener(new View.OnClickListener() { // from class: com.tzh.wifi.wificam.adapter.MusicSelectAdapter.2
                @Override // android.view.View.OnClickListener
                public void onClick(View view) {
                    if (MusicSelectAdapter.this.onMusicItemClickListener != null) {
                        MusicSelectAdapter.this.onMusicItemClickListener.onConfirm(i);
                    }
                }
            });
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handlePlayClick(View view, int i) {
        if (this.mMusicUtils.isPlaying()) {
            ImageView imageView = this.imPlay;
            if (imageView != null) {
                imageView.setImageResource(R.drawable.drawable_btn_play);
            }
            if (this.lastPosition != i) {
                this.lastPosition = i;
                ImageView imageView2 = (ImageView) view;
                this.imPlay = imageView2;
                imageView2.setImageResource(R.drawable.drawable_btn_pause);
                OnMusicItemClickListener onMusicItemClickListener = this.onMusicItemClickListener;
                if (onMusicItemClickListener != null) {
                    onMusicItemClickListener.onPlay(i, this.imPlay);
                    return;
                }
                return;
            }
            OnMusicItemClickListener onMusicItemClickListener2 = this.onMusicItemClickListener;
            if (onMusicItemClickListener2 != null) {
                onMusicItemClickListener2.onPausePlay();
                return;
            }
            return;
        }
        this.lastPosition = i;
        ImageView imageView3 = (ImageView) view;
        this.imPlay = imageView3;
        imageView3.setImageResource(R.drawable.drawable_btn_pause);
        OnMusicItemClickListener onMusicItemClickListener3 = this.onMusicItemClickListener;
        if (onMusicItemClickListener3 != null) {
            onMusicItemClickListener3.onPlay(i, this.imPlay);
        }
    }

    @Override // androidx.recyclerview.widget.RecyclerView.Adapter
    public int getItemCount() {
        return this.mDatas.size();
    }
}
