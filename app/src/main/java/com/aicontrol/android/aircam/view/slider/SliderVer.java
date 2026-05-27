package com.aicontrol.android.aircam.view.slider;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.utils.LogUtils;
import com.aicontrol.android.aircam.view.slider.listener.ISliderListener;

/* loaded from: classes5.dex */
public class SliderVer extends LinearLayout implements View.OnClickListener {
    private int currentTune;
    private int defaultTune;
    private ImageView ivBar;
    private ImageView ivDown;
    private ImageView ivThumb;
    private ImageView ivUp;
    private ISliderListener listener;
    LogUtils logUtils;
    private LayoutInflater mInflater;
    private int maxTune;
    private int nBarHeight;
    private View view;

    public SliderVer(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.view = null;
        this.mInflater = null;
        this.ivUp = null;
        this.ivDown = null;
        this.ivThumb = null;
        this.ivBar = null;
        this.defaultTune = 16;
        this.maxTune = 31;
        this.currentTune = 16;
        this.nBarHeight = 0;
        this.logUtils = LogUtils.setLogger(SliderVer.class);
        this.listener = null;
        View inflate = LayoutInflater.from(context).inflate(R.layout.ly_vslider, (ViewGroup) null);
        this.view = inflate;
        addView(inflate);
        widget_init();
    }

    private void widget_init() {
        this.ivUp = (ImageView) findViewById(R.id.ivSliderUp);
        this.ivDown = (ImageView) findViewById(R.id.ivSliderDown);
        this.ivThumb = (ImageView) findViewById(R.id.ivSliderVThumb);
        this.ivBar = (ImageView) findViewById(R.id.ivSliderVBar);
        this.ivUp.setOnClickListener(this);
        this.ivDown.setOnClickListener(this);
        this.nBarHeight = (int) getResources().getDimension(R.dimen.y168);
    }

    public void addSliderListener(ISliderListener iSliderListener, int i) {
        this.listener = iSliderListener;
        ISliderCheckOut(i);
    }

    @Override // android.view.View.OnClickListener
    public void onClick(View view) {
        int id = view.getId();
        if (id == R.id.ivSliderDown) {
            ISliderNotifyDown();
        } else {
            if (id != R.id.ivSliderUp) {
                return;
            }
            ISliderNotifyUp();
        }
    }

    private void ISliderNotifyUp() {
        int i = this.currentTune;
        if (i == 31) {
            return;
        }
        this.currentTune = i + 1;
        float f = this.nBarHeight / this.maxTune;
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) this.ivThumb.getLayoutParams();
        layoutParams.topMargin = (int) (layoutParams.topMargin - f);
        this.ivThumb.setLayoutParams(layoutParams);
        ISliderListener iSliderListener = this.listener;
        if (iSliderListener != null) {
            iSliderListener.onSliderNotify(this, this.currentTune);
        }
        this.logUtils.e("ISliderNotifyUp:" + layoutParams.topMargin);
    }

    private void ISliderNotifyDown() {
        int i = this.currentTune;
        if (i == 1) {
            return;
        }
        this.currentTune = i - 1;
        float f = this.nBarHeight / this.maxTune;
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) this.ivThumb.getLayoutParams();
        layoutParams.topMargin = (int) (layoutParams.topMargin + f);
        this.ivThumb.setLayoutParams(layoutParams);
        ISliderListener iSliderListener = this.listener;
        if (iSliderListener != null) {
            iSliderListener.onSliderNotify(this, this.currentTune);
        }
    }

    public void ISliderCheckOut(int i) {
        if (this.defaultTune == i) {
            return;
        }
        float f = this.nBarHeight / this.maxTune;
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) this.ivThumb.getLayoutParams();
        layoutParams.topMargin = (int) (layoutParams.topMargin - (f * (i - this.defaultTune)));
        this.ivThumb.setLayoutParams(layoutParams);
        this.currentTune = i;
        ISliderListener iSliderListener = this.listener;
        if (iSliderListener != null) {
            iSliderListener.onSliderNotify(this, this.defaultTune);
        }
    }

    public void ISliderCheckOut() {
        if (this.defaultTune == this.currentTune) {
            return;
        }
        float f = this.nBarHeight / this.maxTune;
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) this.ivThumb.getLayoutParams();
        layoutParams.topMargin = (int) (layoutParams.topMargin - (f * (this.defaultTune - this.currentTune)));
        this.ivThumb.setLayoutParams(layoutParams);
        int i = this.defaultTune;
        this.currentTune = i;
        ISliderListener iSliderListener = this.listener;
        if (iSliderListener != null) {
            iSliderListener.onSliderNotify(this, i);
        }
    }

    @Override // android.widget.LinearLayout, android.view.View
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }
}
