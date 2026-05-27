package com.aicontrol.android.aircam.view.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.method.ScrollingMovementMethod;
import android.text.style.ClickableSpan;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.utils.ConfigUtils;
import com.aicontrol.android.aircam.view.PrivacyPolicyActivity;

/* loaded from: classes5.dex */
public class PriAlartDialog extends Dialog {
    private AlartDialogClick alartDialogClick;
    private Button btnAgree;
    private Button btnDisagree;
    private CheckBox checkButton;
    private TextView checkButtonText;
    private String content;
    private Context context;
    private RelativeLayout frgCancel;
    private RelativeLayout frgConfirm;
    private boolean isSingle;
    private int languageIndex;
    private int languate;
    private int screenHeight;
    private int screenWidth;
    private String title;
    private TextView tvContent;
    private TextView tvTitle;

    public interface AlartDialogClick {
        void OnCancelClick();

        void OnConfirmClick();
    }

    public PriAlartDialog(Context context, int i) {
        super(context, i);
        this.context = null;
        this.title = null;
        this.content = null;
        this.btnAgree = null;
        this.btnDisagree = null;
        this.tvTitle = null;
        this.tvContent = null;
        this.checkButton = null;
        this.checkButtonText = null;
        this.frgConfirm = null;
        this.frgCancel = null;
        this.alartDialogClick = null;
        this.isSingle = false;
        this.screenWidth = 0;
        this.screenHeight = 0;
        this.languageIndex = 0;
        this.languate = 1;
        this.languate = ConfigUtils.getLan(context);
        this.context = context;
        setContentView(R.layout.location_alartdialog);
        LayoutInflater.from(context).inflate(R.layout.location_alartdialog, (ViewGroup) null);
        Window window = getWindow();
        window.setFlags(1024, 1024);
        getWindow().setFlags(32, 32);
        getWindow().setFlags(262144, 262144);
        WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.width = (int) context.getResources().getDimension(R.dimen.y560);
        attributes.height = -2;
        attributes.gravity = 17;
        window.setAttributes(attributes);
        widget_init();
    }

    @Override // android.app.Dialog
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
    }

    private void widget_init() {
        String str;
        int i;
        int i2;
        int i3;
        int i4;
        this.btnDisagree = (Button) findViewById(R.id.alart_btn_disagree);
        this.btnAgree = (Button) findViewById(R.id.alart_btn_agree);
        this.tvTitle = (TextView) findViewById(R.id.alart_title);
        this.tvContent = (TextView) findViewById(R.id.alart_content);
        this.checkButton = (CheckBox) findViewById(R.id.checkButton);
        this.checkButtonText = (TextView) findViewById(R.id.checkButtonText);
        this.tvContent.setMovementMethod(ScrollingMovementMethod.getInstance());
        int i5 = this.languate;
        if (i5 == 1) {
            this.tvTitle.setText(this.context.getResources().getString(R.string.location_alart_title_txt_en));
            this.tvContent.setText(this.context.getResources().getString(R.string.privacy_alart_content_txt_en));
            this.btnDisagree.setText(this.context.getResources().getString(R.string.alart_disagree_txt_en));
            this.btnAgree.setText(this.context.getResources().getString(R.string.alart_agree_txt_en));
            str = this.context.getResources().getString(R.string.privacy_alart_check_txt_en);
            i = str.indexOf(this.context.getResources().getString(R.string.privacy_policy_en));
            i2 = this.context.getResources().getString(R.string.privacy_policy_en).length();
            i3 = str.indexOf(this.context.getResources().getString(R.string.user_agreement_en));
            i4 = this.context.getResources().getString(R.string.user_agreement_en).length();
        } else if (i5 == 0) {
            this.tvTitle.setText(this.context.getResources().getString(R.string.location_alart_title_txt));
            this.tvContent.setText(this.context.getResources().getString(R.string.privacy_alart_content_txt));
            this.btnDisagree.setText(this.context.getResources().getString(R.string.alart_disagree_txt));
            this.btnAgree.setText(this.context.getResources().getString(R.string.alart_agree_txt));
            str = this.context.getResources().getString(R.string.privacy_alart_check_txt);
            i = str.indexOf(this.context.getResources().getString(R.string.privacy_policy));
            i2 = this.context.getResources().getString(R.string.privacy_policy).length();
            i3 = str.indexOf(this.context.getResources().getString(R.string.user_agreement));
            i4 = this.context.getResources().getString(R.string.user_agreement).length();
        } else {
            str = "";
            i = 0;
            i2 = 0;
            i3 = 0;
            i4 = 0;
        }
        SpannableString spannableString = new SpannableString(str);
        spannableString.setSpan(new ClickableSpan() { // from class: com.tzh.wifi.wificam.view.dialog.PriAlartDialog.1
            @Override // android.text.style.ClickableSpan
            public void onClick(View view) {
                Intent intent = new Intent(PriAlartDialog.this.context, (Class<?>) PrivacyPolicyActivity.class);
                intent.putExtra("type", "Privacy Policy");
                PriAlartDialog.this.context.startActivity(intent);
            }
        }, i, i2 + i, 33);
        spannableString.setSpan(new ClickableSpan() { // from class: com.tzh.wifi.wificam.view.dialog.PriAlartDialog.2
            @Override // android.text.style.ClickableSpan
            public void onClick(View view) {
                Intent intent = new Intent(PriAlartDialog.this.context, (Class<?>) PrivacyPolicyActivity.class);
                intent.putExtra("type", "User Agreement");
                PriAlartDialog.this.context.startActivity(intent);
            }
        }, i3, i4 + i3, 33);
        this.checkButtonText.setText(spannableString);
        this.checkButtonText.setMovementMethod(LinkMovementMethod.getInstance());
        this.frgConfirm = (RelativeLayout) findViewById(R.id.alart_confirm_layout);
        RelativeLayout relativeLayout = (RelativeLayout) findViewById(R.id.alart_cancel_layout);
        this.frgCancel = relativeLayout;
        if (this.isSingle) {
            relativeLayout.setVisibility(8);
        }
        this.btnDisagree.setOnClickListener(new ClickListener());
        this.btnAgree.setOnClickListener(new ClickListener());
        this.checkButton.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() { // from class: com.tzh.wifi.wificam.view.dialog.PriAlartDialog.3
            @Override // android.widget.CompoundButton.OnCheckedChangeListener
            public void onCheckedChanged(CompoundButton compoundButton, boolean z) {
                if (z) {
                    PriAlartDialog.this.btnAgree.setEnabled(true);
                } else {
                    PriAlartDialog.this.btnAgree.setEnabled(false);
                }
            }
        });
        LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-2, -2);
        layoutParams.width = -1;
        layoutParams.height = (int) this.context.getResources().getDimension(R.dimen.y300);
        layoutParams.gravity = 17;
        this.tvContent.setLayoutParams(layoutParams);
        new RelativeLayout.LayoutParams(-2, -2);
        RelativeLayout.LayoutParams layoutParams2 = new RelativeLayout.LayoutParams(-2, -2);
        layoutParams2.width = -1;
        layoutParams2.height = (int) this.context.getResources().getDimension(R.dimen.x80);
        layoutParams2.addRule(15);
        this.tvTitle.setGravity(17);
        this.tvTitle.setLayoutParams(layoutParams2);
    }

    public void setAlartClickListener(AlartDialogClick alartDialogClick) {
        this.alartDialogClick = alartDialogClick;
    }

    public void setTitle(String str) {
        this.tvTitle.setText(str);
    }

    public void setContent(String str) {
        this.tvContent.setText(str);
    }

    public void setSingleButton() {
        this.frgCancel.setVisibility(8);
    }

    private class ClickListener implements View.OnClickListener {
        private ClickListener() {
        }

        @Override // android.view.View.OnClickListener
        public void onClick(View view) {
            int id = view.getId();
            if (id == R.id.alart_btn_agree) {
                if (PriAlartDialog.this.alartDialogClick != null) {
                    PriAlartDialog.this.alartDialogClick.OnConfirmClick();
                }
                PriAlartDialog.this.dismiss();
            } else {
                if (id != R.id.alart_btn_disagree) {
                    return;
                }
                if (PriAlartDialog.this.alartDialogClick != null) {
                    PriAlartDialog.this.alartDialogClick.OnCancelClick();
                }
                PriAlartDialog.this.dismiss();
            }
        }
    }

    @Override // android.app.Dialog
    public boolean onTouchEvent(MotionEvent motionEvent) {
        motionEvent.getAction();
        return super.onTouchEvent(motionEvent);
    }
}
