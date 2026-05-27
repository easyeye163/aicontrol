package com.aicontrol.android.aircam.utils;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.MediaPlayer;
import android.net.Uri;
import com.aicontrol.android.aircam.bean.Music;
import com.aicontrol.android.aircam.interfaces.OnMusicPlayListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/* loaded from: classes5.dex */
public class MusicUtils {
    private List<Music> list;
    private Context mContext;
    private MediaPlayer mediaPlayer;
    public List<OnMusicPlayListener> onMusicPlayListeners = new ArrayList();
    private Music song;

    public MusicUtils(Context context) {
        this.mContext = context;
    }

    public void setOnMusicPlayListener(OnMusicPlayListener onMusicPlayListener) {
        if (onMusicPlayListener != null) {
            this.onMusicPlayListeners.add(onMusicPlayListener);
        }
    }

    public List<Music> getMusic() {
        List<Music> musicList = new ArrayList<>();
        try {
            String[] files = this.mContext.getAssets().list("musics");
            if (files != null) {
                for (String file : files) {
                    if (file.endsWith(".mp3") || file.endsWith(".ogg") || file.endsWith(".wav") || file.endsWith(".m4a")) {
                        String name = file.replace(".mp3", "").replace(".ogg", "").replace(".wav", "").replace(".m4a", "");
                        Music music = new Music();
                        music.setSong(name);
                        music.setPath("musics/" + file);
                        musicList.add(music);
                    }
                }
            }
            this.list = musicList;
            android.util.Log.d("MusicUtils", "Total music loaded: " + this.list.size());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return musicList;
    }

    public void play(String str) {
        List<OnMusicPlayListener> list;
        if (this.mediaPlayer == null) {
            this.mediaPlayer = new MediaPlayer();
        }
        if (isPlaying() && (list = this.onMusicPlayListeners) != null && list.size() > 0) {
            Iterator<OnMusicPlayListener> it2 = this.onMusicPlayListeners.iterator();
            while (it2.hasNext()) {
                it2.next().onPlay();
            }
        }
        try {
            this.mediaPlayer.reset();
            this.mediaPlayer.setDataSource(str);
            this.mediaPlayer.prepareAsync();
            this.mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { // from class: com.tzh.wifi.wificam.utils.MusicUtils.1
                @Override // android.media.MediaPlayer.OnCompletionListener
                public void onCompletion(MediaPlayer mediaPlayer) {
                    mediaPlayer.stop();
                    if (MusicUtils.this.onMusicPlayListeners == null || MusicUtils.this.onMusicPlayListeners.size() <= 0) {
                        return;
                    }
                    Iterator<OnMusicPlayListener> it3 = MusicUtils.this.onMusicPlayListeners.iterator();
                    while (it3.hasNext()) {
                        it3.next().onCompleted();
                    }
                }
            });
            this.mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { // from class: com.tzh.wifi.wificam.utils.MusicUtils.2
                @Override // android.media.MediaPlayer.OnPreparedListener
                public void onPrepared(MediaPlayer mediaPlayer) {
                    mediaPlayer.start();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void play(Uri uri) {
        List<OnMusicPlayListener> list;
        if (this.mediaPlayer == null) {
            this.mediaPlayer = new MediaPlayer();
        }
        if (isPlaying() && (list = this.onMusicPlayListeners) != null && list.size() > 0) {
            Iterator<OnMusicPlayListener> it2 = this.onMusicPlayListeners.iterator();
            while (it2.hasNext()) {
                it2.next().onPlay();
            }
        }
        try {
            this.mediaPlayer.reset();
            this.mediaPlayer.setDataSource(this.mContext, uri);
            this.mediaPlayer.prepareAsync();
            this.mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { // from class: com.tzh.wifi.wificam.utils.MusicUtils.3
                @Override // android.media.MediaPlayer.OnCompletionListener
                public void onCompletion(MediaPlayer mediaPlayer) {
                    mediaPlayer.stop();
                    if (MusicUtils.this.onMusicPlayListeners == null || MusicUtils.this.onMusicPlayListeners.size() <= 0) {
                        return;
                    }
                    Iterator<OnMusicPlayListener> it3 = MusicUtils.this.onMusicPlayListeners.iterator();
                    while (it3.hasNext()) {
                        it3.next().onCompleted();
                    }
                }
            });
            this.mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { // from class: com.tzh.wifi.wificam.utils.MusicUtils.4
                @Override // android.media.MediaPlayer.OnPreparedListener
                public void onPrepared(MediaPlayer mediaPlayer) {
                    mediaPlayer.start();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void play(AssetManager assetManager, int i, String str) {
        List<OnMusicPlayListener> list;
        if (this.mediaPlayer == null) {
            this.mediaPlayer = new MediaPlayer();
        }
        if (isPlaying() && (list = this.onMusicPlayListeners) != null && list.size() > 0) {
            Iterator<OnMusicPlayListener> it2 = this.onMusicPlayListeners.iterator();
            while (it2.hasNext()) {
                it2.next().onPlay();
            }
        }
        try {
            this.mediaPlayer.reset();
            AssetFileDescriptor openFd = assetManager.openFd("musics/" + str);
            this.mediaPlayer.setDataSource(openFd.getFileDescriptor(), openFd.getStartOffset(), openFd.getLength());
            this.mediaPlayer.prepareAsync();
            this.mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { // from class: com.tzh.wifi.wificam.utils.MusicUtils.5
                @Override // android.media.MediaPlayer.OnCompletionListener
                public void onCompletion(MediaPlayer mediaPlayer) {
                    mediaPlayer.stop();
                    if (MusicUtils.this.onMusicPlayListeners == null || MusicUtils.this.onMusicPlayListeners.size() <= 0) {
                        return;
                    }
                    Iterator<OnMusicPlayListener> it3 = MusicUtils.this.onMusicPlayListeners.iterator();
                    while (it3.hasNext()) {
                        it3.next().onCompleted();
                    }
                }
            });
            this.mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() { // from class: com.tzh.wifi.wificam.utils.MusicUtils.6
                @Override // android.media.MediaPlayer.OnPreparedListener
                public void onPrepared(MediaPlayer mediaPlayer) {
                    mediaPlayer.start();
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setComposeMusic(String str) {
        if (this.mediaPlayer == null) {
            this.mediaPlayer = new MediaPlayer();
        }
        this.mediaPlayer.reset();
        try {
            this.mediaPlayer.setDataSource(str);
            this.mediaPlayer.setAudioStreamType(3);
            this.mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setComposeMusic(AssetManager assetManager, int i, String str) {
        if (this.mediaPlayer == null) {
            this.mediaPlayer = new MediaPlayer();
        }
        this.mediaPlayer.reset();
        try {
            AssetFileDescriptor openFd = assetManager.openFd("musics/" + str);
            this.mediaPlayer.setDataSource(openFd.getFileDescriptor(), openFd.getStartOffset(), openFd.getLength());
            this.mediaPlayer.setAudioStreamType(3);
            this.mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void pause() {
        if (isPlaying()) {
            this.mediaPlayer.pause();
        }
    }

    public boolean isPlaying() {
        MediaPlayer mediaPlayer = this.mediaPlayer;
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }

    public int getDuration() {
        MediaPlayer mediaPlayer = this.mediaPlayer;
        if (mediaPlayer != null) {
            return mediaPlayer.getDuration();
        }
        return 0;
    }

    public int getCurrentPosition() {
        if (isPlaying()) {
            return this.mediaPlayer.getCurrentPosition();
        }
        return 0;
    }

    public void seekToPosition(int i) {
        if (isPlaying()) {
            this.mediaPlayer.seekTo(i);
        }
    }

    public void restart() {
        MediaPlayer mediaPlayer = this.mediaPlayer;
        if (mediaPlayer != null) {
            mediaPlayer.start();
            List<OnMusicPlayListener> list = this.onMusicPlayListeners;
            if (list == null || list.size() <= 0) {
                return;
            }
            Iterator<OnMusicPlayListener> it2 = this.onMusicPlayListeners.iterator();
            while (it2.hasNext()) {
                it2.next().onPlay();
            }
        }
    }

    public void stop() {
        MediaPlayer mediaPlayer = this.mediaPlayer;
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            this.mediaPlayer.release();
        }
    }

    public static String formatTime(int i) {
        int i2 = i / 1000;
        int i3 = i2 % 60;
        if (i3 < 10) {
            return (i2 / 60) + ":0" + i3;
        }
        return (i2 / 60) + ":" + i3;
    }
}
