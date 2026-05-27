package com.aicontrol.android.aircam.fragment;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.aicontrol.android.aircam.activity.PlayActivity;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.adapter.MusicSelectAdapter;
import com.aicontrol.android.aircam.bean.MessageWrap;
import com.aicontrol.android.aircam.bean.Music;
import com.aicontrol.android.aircam.interfaces.OnMusicItemClickListener;
import com.aicontrol.android.aircam.interfaces.OnMusicPlayListener;
import com.aicontrol.android.aircam.utils.MusicUtils;
import java.util.List;
import org.greenrobot.eventbus.EventBus;

/* loaded from: classes5.dex */
public class LocalMusicFragment extends Fragment implements OnMusicItemClickListener, OnMusicPlayListener {
    private AssetManager am;
    private ImageView imLastPlay;
    private MusicSelectAdapter mAdapter;
    private List<Music> mMusicList;
    private MusicUtils mMusicUtils;
    private RecyclerView mRecyclerView;
    private int type = 3;

    public LocalMusicFragment(MusicUtils musicUtils) {
        this.mMusicUtils = musicUtils;
    }

    @Override // androidx.fragment.app.Fragment
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
    }

    @Override // androidx.fragment.app.Fragment
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        View inflate = layoutInflater.inflate(R.layout.fragment_local_music, viewGroup, false);
        RecyclerView recyclerView = (RecyclerView) inflate.findViewById(R.id.music_select_local_music_recyclerview);
        this.mRecyclerView = recyclerView;
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        this.mMusicUtils.setOnMusicPlayListener(this);
        this.mMusicList = this.mMusicUtils.getMusic();
        StringBuilder sb = new StringBuilder("Music list size: ");
        List<Music> list = this.mMusicList;
        sb.append(list != null ? list.size() : 0);
        Log.d("LocalMusicFragment", sb.toString());
        List<Music> list2 = this.mMusicList;
        if (list2 == null || list2.isEmpty()) {
            Log.w("LocalMusicFragment", "No local music found");
        }
        MusicSelectAdapter musicSelectAdapter = new MusicSelectAdapter(this.mMusicList, this.mMusicUtils, this.type, null);
        this.mAdapter = musicSelectAdapter;
        this.mRecyclerView.setAdapter(musicSelectAdapter);
        this.mAdapter.setOnItemClickListener(this);
        return inflate;
    }

    @Override // androidx.fragment.app.Fragment
    public void onDetach() {
        super.onDetach();
    }

    @Override // com.tzh.wifi.wificam.interfaces.OnMusicItemClickListener
    public void onPlay(int i, ImageView imageView) {
        this.mMusicUtils.play(this.mMusicList.get(i).getPath());
        this.imLastPlay = imageView;
    }

    @Override // com.tzh.wifi.wificam.interfaces.OnMusicItemClickListener
    public void onPausePlay() {
        this.mMusicUtils.pause();
    }

    @Override // com.tzh.wifi.wificam.interfaces.OnMusicItemClickListener
    public void onConfirm(int i) {
        PlayActivity.audio_str = this.mMusicList.get(i).getPath();
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
