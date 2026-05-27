package com.aicontrol.android.aircam.view.fragment;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.fragment.app.Fragment;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.utils.LocalUtil;

/* loaded from: classes5.dex */
public class FirstFragment extends Fragment {
    private static FirstFragment mInstance;
    private ImageView ivCenter = null;
    private int screenWidth = 0;
    private int screenHeight = 0;

    private FirstFragment() {
    }

    public static FirstFragment getInstance() {
        if (mInstance == null) {
            mInstance = new FirstFragment();
        }
        return mInstance;
    }

    @Override // androidx.fragment.app.Fragment
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
    }

    @Override // androidx.fragment.app.Fragment
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        View inflate = layoutInflater.inflate(R.layout.ly_first_fragment, (ViewGroup) null);
        DisplayMetrics displayMetrics = inflate.getResources().getDisplayMetrics();
        this.screenWidth = displayMetrics.widthPixels;
        this.screenHeight = displayMetrics.heightPixels;
        return inflate;
    }

    @Override // androidx.fragment.app.Fragment
    public void onViewCreated(View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        widget_init(view);
    }

    private void widget_init(View view) {
        this.ivCenter = (ImageView) view.findViewById(R.id.ivCenterTxtOne);
        if (LocalUtil.isZh(getContext())) {
            this.ivCenter.setImageResource(R.mipmap.zone_info_page1_cn);
        } else {
            this.ivCenter.setImageResource(R.mipmap.zone_info_page1_en);
        }
    }
}
