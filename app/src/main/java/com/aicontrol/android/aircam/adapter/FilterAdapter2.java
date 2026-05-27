package com.aicontrol.android.aircam.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import com.aicontrol.android.R;
import com.aicontrol.android.aircam.bean.FilterEffect;
import com.aicontrol.android.aircam.utils.DataHandler;
import java.util.List;

/* loaded from: classes5.dex */
public class FilterAdapter2 extends BaseAdapter {
    private List<Bitmap> filterList;
    Context mContext;
    private int selectFilter = 0;
    private int selectedPosition;

    @Override // android.widget.Adapter
    public long getItemId(int i) {
        return i;
    }

    public void setSelectFilter(int i) {
        this.selectFilter = i;
    }

    public int getSelectFilter() {
        return this.selectFilter;
    }

    public FilterAdapter2(Context context, List<Bitmap> list) {
        this.mContext = context;
        this.filterList = list;
    }

    @Override // android.widget.Adapter
    public int getCount() {
        return this.filterList.size();
    }

    @Override // android.widget.Adapter
    public Object getItem(int i) {
        return this.filterList.get(i);
    }

    @Override // android.widget.Adapter
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder viewHolder;
        if (view == null) {
            view = LayoutInflater.from(this.mContext).inflate(R.layout.item_bottom_filter, (ViewGroup) null);
            viewHolder = new ViewHolder(view);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }
        bindData(viewHolder, i);
        return view;
    }

    private void bindData(ViewHolder viewHolder, int i) {
        FilterEffect filterEffect = DataHandler.filters.get(i);
        viewHolder.smallFilter.setImageBitmap(this.filterList.get(i));
        viewHolder.filterName.setText(filterEffect.getName());
        if (i == this.selectedPosition) {
            viewHolder.frameLayout.setBackgroundColor(this.mContext.getResources().getColor(R.color.frame_filter));
        } else {
            viewHolder.frameLayout.setBackgroundColor(this.mContext.getResources().getColor(R.color.filter_bg));
        }
    }

    class ViewHolder {
        TextView filterName;
        FrameLayout frameLayout;
        ImageView smallFilter;

        ViewHolder(View view) {
            this.smallFilter = (ImageView) view.findViewById(R.id.small_filter);
            this.frameLayout = (FrameLayout) view.findViewById(R.id.frame_layout);
            this.filterName = (TextView) view.findViewById(R.id.filter_name);
            view.setTag(this);
        }
    }

    public void setSelected(int i) {
        this.selectedPosition = i;
        notifyDataSetChanged();
    }

    public List<Bitmap> getList() {
        return this.filterList;
    }
}
