package com.aicontrol.android.aircam.adapter;

import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/* loaded from: classes5.dex */
public abstract class BaseMediaAdapter<VH extends RecyclerView.ViewHolder> extends RecyclerView.Adapter<VH> {
    protected OnSelectionChangeListener onSelectionChangeListener;
    protected boolean selectionMode = false;
    protected Set<Integer> selectedPositions = new HashSet();

    public interface OnSelectionChangeListener {
        void onSelectionChanged(int i);
    }

    public void setSelectionMode(boolean z) {
        this.selectionMode = z;
        if (!z) {
            this.selectedPositions.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return this.selectionMode;
    }

    public void toggleSelection(int i) {
        if (this.selectedPositions.contains(Integer.valueOf(i))) {
            this.selectedPositions.remove(Integer.valueOf(i));
        } else {
            this.selectedPositions.add(Integer.valueOf(i));
        }
        notifyItemChanged(i);
        OnSelectionChangeListener onSelectionChangeListener = this.onSelectionChangeListener;
        if (onSelectionChangeListener != null) {
            onSelectionChangeListener.onSelectionChanged(this.selectedPositions.size());
        }
    }

    public void selectAll(int i) {
        this.selectedPositions.clear();
        for (int i2 = 0; i2 < i; i2++) {
            this.selectedPositions.add(Integer.valueOf(i2));
        }
        notifyDataSetChanged();
        OnSelectionChangeListener onSelectionChangeListener = this.onSelectionChangeListener;
        if (onSelectionChangeListener != null) {
            onSelectionChangeListener.onSelectionChanged(this.selectedPositions.size());
        }
    }

    public void clearSelection() {
        this.selectedPositions.clear();
        notifyDataSetChanged();
        OnSelectionChangeListener onSelectionChangeListener = this.onSelectionChangeListener;
        if (onSelectionChangeListener != null) {
            onSelectionChangeListener.onSelectionChanged(0);
        }
    }

    public List<Integer> getSelectedPositions() {
        return new ArrayList(this.selectedPositions);
    }

    public int getSelectedCount() {
        return this.selectedPositions.size();
    }

    public void setOnSelectionChangeListener(OnSelectionChangeListener onSelectionChangeListener) {
        this.onSelectionChangeListener = onSelectionChangeListener;
    }
}
