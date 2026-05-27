package com.aicontrol.android.aircam.base;

import android.content.Intent;
import android.util.Log;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.utils.LogUtils;

/* loaded from: classes5.dex */
public class BaseActivity extends AppCompatActivity implements View.OnClickListener {
    private static final int AUDIO_PERMISSION_REQUEST_CODE = 102;
    private static final int LOCATION_REQUEST_CODE = 100;
    private static final int STORE_PERMISSION_REQUEST_CODE = 101;
    LogUtils logUtils = LogUtils.setLogger(BaseActivity.class);
    private String[] audioPermissions = {"android.permission.RECORD_AUDIO"};
    private String[] restorePermissions = {"android.permission.READ_EXTERNAL_STORAGE", "android.permission.WRITE_EXTERNAL_STORAGE"};
    private String[] locationPermissions = {"android.permission.ACCESS_FINE_LOCATION", "android.permission.ACCESS_COARSE_LOCATION"};

    /**
     * Application-level settings holder, replacing the old WiFiApp singleton fields.
     */
    public static class AppSettings {
        public boolean bLockClick = false;
        public boolean bButtonClick = false;
        public boolean bfilter_playClick = false;
        public int nSpeed = 0;
        public int bRotate = 0;
    }

    private static final AppSettings appSettings = new AppSettings();

    public void onClick(View view) {
    }

    public void startService() {
    }

    private boolean lacksPermission(String[] strArr) {
        for (String str : strArr) {
            if (ContextCompat.checkSelfPermission(this, str) != 0) {
                this.logUtils.e("###lacksPermission true");
                return true;
            }
        }
        return false;
    }

    private boolean requestStorePermission() {
        if (!lacksPermission(this.restorePermissions)) {
            return true;
        }
        ActivityCompat.requestPermissions(this, this.restorePermissions, 101);
        return false;
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, android.app.Activity
    public void onRequestPermissionsResult(int i, String[] strArr, int[] iArr) {
        super.onRequestPermissionsResult(i, strArr, iArr);
        int i2 = 0;
        if (i == 101) {
            int length = strArr.length;
            while (i2 < length) {
                if (ContextCompat.checkSelfPermission(this, strArr[i2]) != 0) {
                    Log.e("LogoActivity", "###缺少存储权限");
                    return;
                }
                i2++;
            }
            this.logUtils.e("###申请完成保存权限开始申请定位权限");
            requestLocationPermission();
            return;
        }
        if (i == 100) {
            int length2 = strArr.length;
            while (i2 < length2) {
                if (ContextCompat.checkSelfPermission(this, strArr[i2]) != 0) {
                    Log.e("LogoActivity", "###缺少定位权限");
                    return;
                }
                i2++;
            }
            return;
        }
        if (i == 102) {
            int length3 = strArr.length;
            while (i2 < length3) {
                if (ContextCompat.checkSelfPermission(this, strArr[i2]) != 0) {
                    Log.e("LogoActivity", "###缺少麦克风访问权限");
                    return;
                }
                i2++;
            }
        }
    }

    private boolean requestLocationPermission() {
        if (!lacksPermission(this.locationPermissions)) {
            return true;
        }
        ActivityCompat.requestPermissions(this, this.locationPermissions, 100);
        return false;
    }

    private boolean requestAudioPermission() {
        if (!lacksPermission(this.audioPermissions)) {
            return true;
        }
        ActivityCompat.requestPermissions(this, this.audioPermissions, 102);
        return false;
    }

    public void requestPermission() {
        if (requestStorePermission()) {
            this.logUtils.e("###保存权限完成申请。开始定位权限");
            requestLocationPermission();
        } else {
            this.logUtils.e("###保存权限完成申请。开始定位权限");
        }
    }

    // Application-level settings, replacing the old WiFiApp singleton fields
    public AppSettings getApp() {
        return appSettings;
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onStop() {
        super.onStop();
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
    }

    protected void startActivity(Class<?> cls) {
        Intent intent = new Intent();
        intent.setClass(this, cls);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }
}
