package com.aicontrol.android.aircam.view.fragment;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import java.util.ArrayList;
import java.util.List;

/* loaded from: classes5.dex */
public class FragmentAdapters extends FragmentPagerAdapter {
    private List<Fragment> fragments;

    public FragmentAdapters(FragmentManager fragmentManager, List<Fragment> list) {
        super(fragmentManager);
        new ArrayList();
        this.fragments = list;
    }

    public FragmentAdapters(FragmentManager fragmentManager) {
        super(fragmentManager);
        this.fragments = new ArrayList();
    }

    @Override // androidx.fragment.app.FragmentPagerAdapter
    public Fragment getItem(int i) {
        return this.fragments.get(i);
    }

    @Override // androidx.viewpager.widget.PagerAdapter
    public int getCount() {
        return this.fragments.size();
    }
}
