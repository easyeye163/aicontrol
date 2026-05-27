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
public class SliderHor extends LinearLayout implements View.OnClickListener {
    private int barWidth;
    private int currentTune;
    private int defaultTune;
    private ImageView ivNext;
    private ImageView ivPrev;
    private ImageView ivThumb;
    private ISliderListener listener;
    LogUtils logUtils;
    private int maxTune;
    private View view;

    public SliderHor(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.view = null;
        this.ivPrev = null;
        this.ivNext = null;
        this.ivThumb = null;
        this.defaultTune = 16;
        this.maxTune = 31;
        this.currentTune = 16;
        this.barWidth = 0;
        this.logUtils = LogUtils.setLogger(SliderHor.class);
        this.listener = null;
        View inflate = LayoutInflater.from(context).inflate(R.layout.ly_hslider, (ViewGroup) null);
        this.view = inflate;
        addView(inflate);
        widget_init();
    }

    private void widget_init() {
        this.ivPrev = (ImageView) findViewById(R.id.ivSliderPrev);
        this.ivNext = (ImageView) findViewById(R.id.ivSliderNext);
        this.ivThumb = (ImageView) findViewById(R.id.ivSliderHThumb);
        this.ivPrev.setOnClickListener(this);
        this.ivNext.setOnClickListener(this);
        this.barWidth = (int) getResources().getDimension(R.dimen.y168);
    }

    public void addSliderListener(ISliderListener iSliderListener, int i) {
        this.listener = iSliderListener;
        ISliderWrite(i);
    }

    @Override // android.view.View.OnClickListener
    public void onClick(View view) {
        int viewId = view.getId();
        if (viewId == R.id.ivSliderNext) {
            ISliderNotifyNext();
        } else if (viewId == R.id.ivSliderPrev) {
            ISliderNotifyPrev();
        }
    }

    private void ISliderNotifyPrev() {
        int i = this.currentTune;
        if (i == 1) {
            return;
        }
        this.currentTune = i - 1;
        float f = this.barWidth / this.maxTune;
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) this.ivThumb.getLayoutParams();
        layoutParams.leftMargin = (int) (layoutParams.leftMargin - f);
        this.ivThumb.setLayoutParams(layoutParams);
        ISliderListener iSliderListener = this.listener;
        if (iSliderListener != null) {
            iSliderListener.onSliderNotify(this, this.currentTune);
        }
        this.logUtils.e("ISliderNotifyPrev:" + this.currentTune);
    }

    private void ISliderNotifyNext() {
        int i = this.currentTune;
        if (i == 31) {
            return;
        }
        this.currentTune = i + 1;
        float f = this.barWidth / this.maxTune;
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) this.ivThumb.getLayoutParams();
        layoutParams.leftMargin = (int) (layoutParams.leftMargin + f);
        this.ivThumb.setLayoutParams(layoutParams);
        ISliderListener iSliderListener = this.listener;
        if (iSliderListener != null) {
            iSliderListener.onSliderNotify(this, this.currentTune);
        }
        this.logUtils.e("ISliderNotifyNext:" + this.currentTune);
    }

    public void ISliderWrite(int i) {
        if (this.defaultTune == i) {
            return;
        }
        float f = this.barWidth / this.maxTune;
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) this.ivThumb.getLayoutParams();
        layoutParams.leftMargin = (int) (layoutParams.leftMargin + (f * (i - this.defaultTune)));
        this.ivThumb.setLayoutParams(layoutParams);
        this.currentTune = i;
        ISliderListener iSliderListener = this.listener;
        if (iSliderListener != null) {
            iSliderListener.onSliderNotify(this, this.defaultTune);
        }
        this.logUtils.e("ISliderNotifyNext:" + this.currentTune);
    }

    public void ISliderCheckOut() {
        if (this.defaultTune == this.currentTune) {
            return;
        }
        float f = this.barWidth / this.maxTune;
        RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) this.ivThumb.getLayoutParams();
        layoutParams.leftMargin = (int) (layoutParams.leftMargin + (f * (this.defaultTune - this.currentTune)));
        this.ivThumb.setLayoutParams(layoutParams);
        int i = this.defaultTune;
        this.currentTune = i;
        ISliderListener iSliderListener = this.listener;
        if (iSliderListener != null) {
            iSliderListener.onSliderNotify(this, i);
        }
        this.logUtils.e("ISliderNotifyNext:" + this.currentTune);
    }

    @Override // android.widget.LinearLayout, android.view.View
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }
}
