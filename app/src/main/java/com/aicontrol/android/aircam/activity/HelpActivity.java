package com.aicontrol.android.aircam.activity;

import com.aicontrol.android.R;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager.widget.ViewPager;
import com.aicontrol.android.aircam.view.ViewPagers;
import com.aicontrol.android.aircam.view.fragment.FirstFragment;
import com.aicontrol.android.aircam.view.fragment.FragmentAdapters;
import com.aicontrol.android.aircam.view.fragment.SecondFragment;
import java.util.ArrayList;
import java.util.List;

/* loaded from: classes5.dex */
public class HelpActivity extends FragmentActivity implements View.OnClickListener, ViewPager.OnPageChangeListener {
    private ImageView btnLeft = null;
    private ImageView btnRight = null;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private ImageView btnReturn = null;
    private int fragmentIdx = 0;
    private View indicator1 = null;
    private View indicator2 = null;
    private ViewPagers viewPagers = null;
    private FirstFragment firstFragment = null;
    private SecondFragment secondFragment = null;
    private List<Fragment> fragments = new ArrayList();
    private FragmentAdapters adapters = null;

    @Override // androidx.viewpager.widget.ViewPager.OnPageChangeListener
    public void onPageScrollStateChanged(int i) {
    }

    @Override // androidx.viewpager.widget.ViewPager.OnPageChangeListener
    public void onPageScrolled(int i, float f, int i2) {
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, androidx.core.app.ComponentActivity, android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_info);
        widget_init();
    }

    private void widget_init() {
        this.btnLeft = (ImageView) findViewById(R.id.btnLeftInfo);
        this.btnRight = (ImageView) findViewById(R.id.btnRightInfo);
        this.btnReturn = (ImageView) findViewById(R.id.btnReturnInfo);
        this.indicator1 = findViewById(R.id.indicator1);
        this.indicator2 = findViewById(R.id.indicator2);
        this.firstFragment = FirstFragment.getInstance();
        this.secondFragment = SecondFragment.getInstance();
        this.viewPagers = (ViewPagers) findViewById(R.id.viewPagersInfo);
        this.fragments.add(this.firstFragment);
        this.fragments.add(this.secondFragment);
        FragmentAdapters fragmentAdapters = new FragmentAdapters(getSupportFragmentManager(), this.fragments);
        this.adapters = fragmentAdapters;
        this.viewPagers.setAdapter(fragmentAdapters);
        this.viewPagers.setOnPageChangeListener(this);
        this.viewPagers.setPageTransformer(false, new SlidePageTransformer());
        this.btnLeft.setVisibility(8);
        updateIndicator(0);
    }

    @Override // androidx.viewpager.widget.ViewPager.OnPageChangeListener
    public void onPageSelected(int i) {
        updateIndicatorWithAnimation(i);
        if (i == 0) {
            this.btnLeft.setVisibility(8);
            this.btnRight.setVisibility(0);
        } else if (i == 1) {
            this.btnLeft.setVisibility(0);
            this.btnRight.setVisibility(8);
        }
    }

    @Override // android.view.View.OnClickListener
    public void onClick(View view) {
        int currentItem;
        int id = view.getId();
        if (id == R.id.btnLeftInfo) {
            int currentItem2 = this.viewPagers.getCurrentItem();
            if (currentItem2 > 0) {
                this.viewPagers.setCurrentItem(currentItem2 - 1);
                return;
            }
            return;
        }
        if (id != R.id.btnReturnInfo) {
            if (id == R.id.btnRightInfo && (currentItem = this.viewPagers.getCurrentItem()) < this.fragments.size() - 1) {
                this.viewPagers.setCurrentItem(currentItem + 1);
                return;
            }
            return;
        }
        finish();
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onStop() {
        super.onStop();
    }

    @Override // androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override // androidx.activity.ComponentActivity, android.app.Activity
    public void onBackPressed() {
        super.onBackPressed();
        finish();
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }

    private void updateIndicator(int i) {
        if (i == 0) {
            this.indicator1.setBackgroundResource(R.drawable.indicator_selected);
            this.indicator2.setBackgroundResource(R.drawable.indicator_unselected);
        } else {
            this.indicator1.setBackgroundResource(R.drawable.indicator_unselected);
            this.indicator2.setBackgroundResource(R.drawable.indicator_selected);
        }
    }

    private void updateIndicatorWithAnimation(int i) {
        View view = i == 0 ? this.indicator1 : this.indicator2;
        View view2 = i == 0 ? this.indicator2 : this.indicator1;
        ObjectAnimator ofFloat = ObjectAnimator.ofFloat(view, "scaleX", 1.0f, 1.3f, 1.0f);
        ObjectAnimator ofFloat2 = ObjectAnimator.ofFloat(view, "scaleY", 1.0f, 1.3f, 1.0f);
        ObjectAnimator ofFloat3 = ObjectAnimator.ofFloat(view2, "scaleX", 1.0f, 0.8f, 1.0f);
        ObjectAnimator ofFloat4 = ObjectAnimator.ofFloat(view2, "scaleY", 1.0f, 0.8f, 1.0f);
        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(ofFloat, ofFloat2, ofFloat3, ofFloat4);
        animatorSet.setDuration(300L);
        updateIndicator(i);
        animatorSet.start();
    }

    private static class SlidePageTransformer implements ViewPager.PageTransformer {
        private SlidePageTransformer() {
        }

        @Override // androidx.viewpager.widget.ViewPager.PageTransformer
        public void transformPage(View view, float f) {
            if (f < -1.0f || f > 1.0f) {
                view.setAlpha(0.0f);
                return;
            }
            view.setAlpha(1.0f);
            if (f <= 0.0f) {
                view.setPivotX(view.getWidth());
                view.setRotationY(Math.abs(f) * 90.0f);
            } else {
                view.setPivotX(0.0f);
                view.setRotationY(Math.abs(f) * (-90.0f));
            }
            view.setCameraDistance(view.getContext().getResources().getDisplayMetrics().densityDpi * 310);
        }
    }
}
