package com.aicontrol.android.aircam.view.fragment;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.fragment.app.Fragment;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.utils.LocalUtil;

/* loaded from: classes5.dex */
public class SecondFragment extends Fragment {
    private static SecondFragment mInstance;
    private Context context = null;
    private ImageView pageTwoIcon = null;

    private SecondFragment() {
    }

    public static SecondFragment getInstance() {
        if (mInstance == null) {
            mInstance = new SecondFragment();
        }
        return mInstance;
    }

    @Override // androidx.fragment.app.Fragment
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
    }

    @Override // androidx.fragment.app.Fragment
    public View onCreateView(LayoutInflater layoutInflater, ViewGroup viewGroup, Bundle bundle) {
        return layoutInflater.inflate(R.layout.ly_second_fragment, (ViewGroup) null);
    }

    @Override // androidx.fragment.app.Fragment
    public void onViewCreated(View view, Bundle bundle) {
        super.onViewCreated(view, bundle);
        this.pageTwoIcon = (ImageView) view.findViewById(R.id.ivCenterTxtTwo);
        if (LocalUtil.isZh(getContext())) {
            this.pageTwoIcon.setImageResource(R.mipmap.zone_info_page2_cn);
        } else {
            this.pageTwoIcon.setImageResource(R.mipmap.zone_info_page2_en);
        }
    }
}
