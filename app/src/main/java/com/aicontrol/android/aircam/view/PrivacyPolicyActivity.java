package com.aicontrol.android.aircam.view;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.base.BaseActivity;
import com.aicontrol.android.aircam.utils.ConfigUtils;
import com.aicontrol.android.aircam.utils.LogUtils;

/* loaded from: classes5.dex */
public class PrivacyPolicyActivity extends BaseActivity implements View.OnClickListener {
    LogUtils logUtils = LogUtils.setLogger(PrivacyPolicyActivity.class);
    private TextView tvAddDeviceFailedBack = null;
    private TextView clauseTitle = null;
    private TextView clauseContent = null;
    private int languate = 1;

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        this.languate = ConfigUtils.getLan(this);
        requestWindowFeature(1);
        Window window = getWindow();
        window.addFlags(Integer.MIN_VALUE);
        window.setStatusBarColor(getResources().getColor(R.color.color_all_bg));
        setContentView(R.layout.privacy_policy_info);
        widget_init();
        Intent intent = getIntent();
        int i = this.languate;
        if (i == 1) {
            if (intent.getStringExtra("type").equals("Privacy Policy")) {
                this.clauseTitle.setText(getResources().getString(R.string.privacy_policy_title_txt_en));
                this.clauseContent.setText(getResources().getString(R.string.privacy_policy_content_en));
                return;
            } else {
                this.clauseTitle.setText(getResources().getString(R.string.user_agreement_title_txt_en));
                this.clauseContent.setText(getResources().getString(R.string.user_agreement_content_en));
                return;
            }
        }
        if (i == 0) {
            if (intent.getStringExtra("type").equals("Privacy Policy")) {
                this.clauseTitle.setText(getResources().getString(R.string.privacy_policy_title_txt));
                this.clauseContent.setText(getResources().getString(R.string.privacy_policy_content));
            } else {
                this.clauseTitle.setText(getResources().getString(R.string.user_agreement_title_txt));
                this.clauseContent.setText(getResources().getString(R.string.user_agreement_content));
            }
        }
    }

    private void widget_init() {
        this.tvAddDeviceFailedBack = (TextView) findViewById(R.id.tvAddDeviceFailedBack);
        this.clauseTitle = (TextView) findViewById(R.id.clauseTitle);
        this.clauseContent = (TextView) findViewById(R.id.clauseContent);
        this.tvAddDeviceFailedBack.setOnClickListener(this);
    }

    @Override // com.tzh.wifi.wificam.base.BaseActivity, android.view.View.OnClickListener
    public void onClick(View view) {
        if (view.getId() != R.id.tvAddDeviceFailedBack) {
            return;
        }
        super.onBackPressed();
        finish();
    }

    @Override // com.tzh.wifi.wificam.base.BaseActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
    }

    @Override // com.tzh.wifi.wificam.base.BaseActivity, androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onStop() {
        super.onStop();
    }

    @Override // com.tzh.wifi.wificam.base.BaseActivity, androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override // androidx.activity.ComponentActivity, android.app.Activity
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }
}
