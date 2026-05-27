package com.aicontrol.android.aircam.view.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.utils.StringUtils;

/* loaded from: classes5.dex */
public class AlartDialog extends Dialog {
    private AlartDialogClick alartDialogClick;
    private Button btnCancel;
    private Button btnConfirm;
    private String content;
    private Context context;
    private boolean isSingle;
    private String title;
    private TextView tvContent;
    private TextView tvTitle;

    public interface AlartDialogClick {
        void OnCancelClick();

        void OnConfirmClick();
    }

    public AlartDialog(Context context, int i) {
        super(context, i);
        this.isSingle = false;
        this.context = context;
    }

    @Override // android.app.Dialog
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.dialog_ios_alert);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setAttributes(window.getAttributes());
        }
        widget_init();
    }

    private void widget_init() {
        this.btnConfirm = (Button) findViewById(R.id.alart_btn_confirm);
        this.btnCancel = (Button) findViewById(R.id.alart_btn_cancel);
        this.tvTitle = (TextView) findViewById(R.id.alart_title);
        this.tvContent = (TextView) findViewById(R.id.alart_content);
        if (this.isSingle) {
            this.btnCancel.setVisibility(8);
        }
        this.btnCancel.setOnClickListener(new ClickListener());
        this.btnConfirm.setOnClickListener(new ClickListener());
        this.title = StringUtils.getInstance(this.context).strDeleteTitle;
        this.content = StringUtils.getInstance(this.context).strDeleteContent;
        String str = StringUtils.getInstance(this.context).strDeleteCancel;
        String str2 = StringUtils.getInstance(this.context).strDeleteConfirm;
        if (this.title == null) {
            this.title = "Alert";
        }
        if (this.content == null) {
            this.content = "Are you sure?";
        }
        if (str == null) {
            str = "Cancel";
        }
        if (str2 == null) {
            str2 = "OK";
        }
        this.tvTitle.setText(this.title);
        this.tvContent.setText(this.content);
        this.btnCancel.setText(str);
        this.btnConfirm.setText(str2);
    }

    public void setAlartClickListener(AlartDialogClick alartDialogClick) {
        this.alartDialogClick = alartDialogClick;
    }

    public void setTitle(String str) {
        this.title = str;
        TextView textView = this.tvTitle;
        if (textView != null) {
            textView.setText(str);
        }
    }

    public void setContent(String str) {
        this.content = str;
        TextView textView = this.tvContent;
        if (textView != null) {
            textView.setText(str);
        }
    }

    public void setSingleButton() {
        this.isSingle = true;
        Button button = this.btnCancel;
        if (button != null) {
            button.setVisibility(8);
        }
    }

    private class ClickListener implements View.OnClickListener {
        private ClickListener() {
        }

        @Override // android.view.View.OnClickListener
        public void onClick(View view) {
            int id = view.getId();
            if (id == R.id.alart_btn_cancel) {
                if (AlartDialog.this.alartDialogClick != null) {
                    AlartDialog.this.alartDialogClick.OnCancelClick();
                }
                AlartDialog.this.dismiss();
            } else if (id == R.id.alart_btn_confirm) {
                if (AlartDialog.this.alartDialogClick != null) {
                    AlartDialog.this.alartDialogClick.OnConfirmClick();
                }
                AlartDialog.this.dismiss();
            }
        }
    }
}
