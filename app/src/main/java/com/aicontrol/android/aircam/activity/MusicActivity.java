package com.aicontrol.android.aircam.activity;

import com.aicontrol.android.R;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import com.aicontrol.android.aircam.fragment.LocalMusicFragment;
import com.aicontrol.android.aircam.fragment.PopularMusicFragment;
import com.aicontrol.android.aircam.utils.AppUtils;
import com.aicontrol.android.aircam.utils.IConstants;
import com.aicontrol.android.aircam.utils.LocalUtil;
import com.aicontrol.android.aircam.utils.MusicUtils;
import com.aicontrol.android.aircam.utils.PreferencesHelper;
import com.aicontrol.android.aircam.utils.StringUtils;
import java.util.Locale;

/* loaded from: classes5.dex */
public class MusicActivity extends FragmentActivity implements View.OnClickListener {
    private static final int REQUEST_PERMISSION_CODE = 100;
    private Fragment currentFragment;
    private MusicUtils mMusicUtils;
    TextView tvLocalMusic;
    TextView tvPopularMusicChinese;
    private final String TAG = getClass().getSimpleName();
    private boolean isPermissionGranted = false;

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_music);
        getWindow().addFlags(128);
        checkAndRequestPermissions();
        initView();
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.READ_MEDIA_AUDIO") != 0) {
                ActivityCompat.requestPermissions(this, new String[]{"android.permission.READ_MEDIA_AUDIO"}, 100);
                return;
            } else {
                this.isPermissionGranted = true;
                return;
            }
        }
        if (ContextCompat.checkSelfPermission(this, "android.permission.READ_EXTERNAL_STORAGE") != 0) {
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.READ_EXTERNAL_STORAGE"}, 100);
        } else {
            this.isPermissionGranted = true;
        }
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, android.app.Activity
    public void onRequestPermissionsResult(int i, String[] strArr, int[] iArr) {
        super.onRequestPermissionsResult(i, strArr, iArr);
        if (i == 100) {
            if (iArr.length > 0 && iArr[0] == 0) {
                this.isPermissionGranted = true;
                Toast.makeText(this, StringUtils.getInstance(this).strPermissionGranted, 0).show();
                if (this.currentFragment instanceof LocalMusicFragment) {
                    replaceFragment(IConstants.FRAGMENT_TAG_MUSIC_LOCAL);
                    return;
                }
                return;
            }
            this.isPermissionGranted = false;
            Toast.makeText(this, StringUtils.getInstance(this).strPermissionDenied, 1).show();
        }
    }

    private void initView() {
        this.tvLocalMusic = (TextView) findViewById(R.id.music_select_local_music);
        TextView textView = (TextView) findViewById(R.id.music_select_popular_chinese_music);
        this.tvPopularMusicChinese = textView;
        textView.setText(StringUtils.getInstance(this).strPopularMusic);
        this.tvLocalMusic.setText(StringUtils.getInstance(this).strLocalMusic);
        ((ImageView) findViewById(R.id.music_select_back_btn)).setOnClickListener(this);
        this.tvPopularMusicChinese.setOnClickListener(this);
        this.tvLocalMusic.setOnClickListener(this);
        this.mMusicUtils = new MusicUtils(this);
        setLanguage();
        replaceFragment(IConstants.FRAGMENT_TAG_MUSIC_POPULAR_CHINESE);
        updateTabSelection(false);
    }

    private void replaceFragment(String str) {
        if (this.currentFragment != null) {
            getSupportFragmentManager().beginTransaction().hide(this.currentFragment).commit();
        }
        Fragment findFragmentByTag = getSupportFragmentManager().findFragmentByTag(str);
        this.currentFragment = findFragmentByTag;
        if (findFragmentByTag == null) {
            str.hashCode();
            if (str.equals(IConstants.FRAGMENT_TAG_MUSIC_LOCAL)) {
                this.currentFragment = new LocalMusicFragment(this.mMusicUtils);
            } else if (str.equals(IConstants.FRAGMENT_TAG_MUSIC_POPULAR_CHINESE)) {
                this.currentFragment = new PopularMusicFragment(this.mMusicUtils, str);
            }
            getSupportFragmentManager().beginTransaction().add(R.id.music_select_center_layout, this.currentFragment, str).commit();
            return;
        }
        getSupportFragmentManager().beginTransaction().show(this.currentFragment).commit();
    }

    @Override // android.view.View.OnClickListener
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.music_select_back_btn) {
            finish();
            return;
        }
        if (id != R.id.music_select_local_music) {
            if (id != R.id.music_select_popular_chinese_music) {
                return;
            }
            updateTabSelection(false);
            replaceFragment(IConstants.FRAGMENT_TAG_MUSIC_POPULAR_CHINESE);
            return;
        }
        if (!this.isPermissionGranted) {
            Toast.makeText(this, StringUtils.getInstance(this).strPermissionRequired, 1).show();
            checkAndRequestPermissions();
        } else {
            updateTabSelection(true);
            replaceFragment(IConstants.FRAGMENT_TAG_MUSIC_LOCAL);
        }
    }

    private void updateTabSelection(boolean z) {
        if (z) {
            this.tvLocalMusic.setBackgroundResource(R.drawable.bg_ios_segment_selected);
            this.tvLocalMusic.setTextColor(getResources().getColor(android.R.color.black));
            this.tvLocalMusic.setTypeface(null, 1);
            this.tvPopularMusicChinese.setBackgroundResource(0);
            this.tvPopularMusicChinese.setTextColor(Color.parseColor("#8E8E93"));
            this.tvPopularMusicChinese.setTypeface(null, 0);
            return;
        }
        this.tvPopularMusicChinese.setBackgroundResource(R.drawable.bg_ios_segment_selected);
        this.tvPopularMusicChinese.setTextColor(getResources().getColor(android.R.color.black));
        this.tvPopularMusicChinese.setTypeface(null, 1);
        this.tvLocalMusic.setBackgroundResource(0);
        this.tvLocalMusic.setTextColor(Color.parseColor("#8E8E93"));
        this.tvLocalMusic.setTypeface(null, 0);
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    public void onDestroy() {
        super.onDestroy();
        this.mMusicUtils.stop();
    }

    @Override // android.app.Activity, android.view.ContextThemeWrapper, android.content.ContextWrapper
    public void attachBaseContext(Context context) {
        super.attachBaseContext(AppUtils.attachBaseContext(context));
    }

    public void setLanguage() {
        Locale language = AppUtils.getLanguage(PreferencesHelper.getSharedPreferences(getApplicationContext()).getInt(IConstants.KEY_LANGUAGE_FLAG, !LocalUtil.isZh(this) ? 1 : 0));
        if (language != null) {
            AppUtils.setLanguage(getApplicationContext(), language);
        }
    }
}
