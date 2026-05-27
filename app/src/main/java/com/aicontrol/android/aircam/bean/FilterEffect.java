package com.aicontrol.android.aircam.bean;

public class FilterEffect {
    private String name;
    private int type;
    private int resId;

    public FilterEffect(String name, int type, int resId) {
        this.name = name;
        this.type = type;
        this.resId = resId;
    }

    public String getName() { return name; }
    public int getType() { return type; }
    public int getResId() { return resId; }
}
