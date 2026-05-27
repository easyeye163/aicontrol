package com.aicontrol.android.aircam.fragment;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.aicontrol.android.aircam.activity.PlayActivity;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.adapter.MusicSelectAdapter;
import com.aicontrol.android.aircam.bean.MessageWrap;
import com.aicontrol.android.aircam.bean.Music;
import com.aicontrol.android.aircam.interfaces.OnMusicItemClickListener;
import com.aicontrol.android.aircam.interfaces.OnMusicPlayListener;
import com.aicontrol.android.aircam.utils.MusicUtils;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.greenrobot.eventbus.EventBus;

/* loaded from: classes5.dex */
public class PopularMusicFragment extends Fragment implements OnMusicItemClickListener, OnMusicPlayListener {
    private AssetManager am;
    private String fragmentTag;
    private ImageView imLastPlay;
    private MusicSelectAdapter mAdapter;
    private List<Music> mMusicList;
    private MusicUtils mMusicUtils;
    private RecyclerView mRecyclerView;
    private int type = 1;

    public PopularMusicFragment(MusicUtils musicUtils, String str) {
        this.mMusicUtils = musicUtils;
        this.fragmentTag = str;
    }

    @Override // androidx.fragment.app.Fragment
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
    }

    @Override // androidx.fragment.app.Fragment
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        View inflate = layoutInflater.inflate(R.layout.fragment_popular_music, viewGroup, false);
        this.mRecyclerView = (RecyclerView) inflate.findViewById(R.id.music_select_popular_music_recyclerview);
        this.mRecyclerView.setLayoutManager(new GridLayoutManager(getActivity(), 2));
        this.mMusicUtils.setOnMusicPlayListener(this);
        this.mMusicList = getMusic();
        MusicSelectAdapter musicSelectAdapter = new MusicSelectAdapter(this.mMusicList, this.mMusicUtils, this.type, this.am);
        this.mAdapter = musicSelectAdapter;
        this.mRecyclerView.setAdapter(musicSelectAdapter);
        this.mAdapter.setOnItemClickListener(this);
        return inflate;
    }

    private List<Music> getMusic() {
        ArrayList arrayList = new ArrayList();
        AssetManager assets = getActivity().getAssets();
        this.am = assets;
        try {
            String[] list = assets.list("musics");
            if (list == null) {
                return arrayList;
            }
            for (String str : list) {
                if (str.endsWith(".mp3") || str.endsWith(".m4a") || str.endsWith(".aac")) {
                    MediaPlayer mediaPlayer = new MediaPlayer();
                    AssetFileDescriptor openFd = this.am.openFd("musics/" + str);
                    mediaPlayer.setDataSource(openFd.getFileDescriptor(), openFd.getStartOffset(), openFd.getLength());
                    mediaPlayer.setAudioStreamType(3);
                    mediaPlayer.prepare();
                    int duration = mediaPlayer.getDuration();
                    Music music = new Music();
                    music.setDuration(duration);
                    music.setPath("");
                    music.setSinger("");
                    music.setSize(0L);
                    music.setSong(str);
                    arrayList.add(music);
                }
            }
            return arrayList;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override // androidx.fragment.app.Fragment
    public void onDetach() {
        super.onDetach();
    }

    @Override // com.tzh.wifi.wificam.interfaces.OnMusicItemClickListener
    public void onPlay(int i, ImageView imageView) {
        this.mMusicUtils.play(this.am, this.type, this.mMusicList.get(i).getSong());
        this.imLastPlay = imageView;
    }

    @Override // com.tzh.wifi.wificam.interfaces.OnMusicItemClickListener
    public void onPausePlay() {
        this.mMusicUtils.pause();
    }

    @Override // com.tzh.wifi.wificam.interfaces.OnMusicItemClickListener
    public void onConfirm(int i) {
        String song = this.mMusicList.get(i).getSong();
        String str = getActivity().getFilesDir().getAbsolutePath() + '/' + song;
        File file = new File(str);
        if (!file.exists() || file.length() <= 0) {
            try {
                InputStream open = getActivity().getAssets().open("musics/" + song);
                FileOutputStream fileOutputStream = new FileOutputStream(file);
                byte[] bArr = new byte[4096];
                while (true) {
                    int read = open.read(bArr);
                    if (read == -1) {
                        break;
                    } else {
                        fileOutputStream.write(bArr, 0, read);
                    }
                }
                open.close();
                fileOutputStream.close();
                Log.d("PopularMusicFragment", "copy asset to files:" + str);
            } catch (IOException e) {
                Log.e("PopularMusicFragment", "copy asset failed:" + e);
            }
        }
        PlayActivity.audio_str = str;
        Log.d("2222", "audio_str:" + PlayActivity.audio_str);
        EventBus.getDefault().postSticky(MessageWrap.getInstance("music_on"));
        getActivity().finish();
    }

    @Override // com.tzh.wifi.wificam.interfaces.OnMusicPlayListener
    public void onPlay() {
        ImageView imageView = this.imLastPlay;
        if (imageView != null) {
            imageView.setImageResource(R.drawable.drawable_btn_play);
        }
    }

    @Override // com.tzh.wifi.wificam.interfaces.OnMusicPlayListener
    public void onCompleted() {
        ImageView imageView = this.imLastPlay;
        if (imageView != null) {
            imageView.setImageResource(R.drawable.drawable_btn_play);
        }
    }
}
