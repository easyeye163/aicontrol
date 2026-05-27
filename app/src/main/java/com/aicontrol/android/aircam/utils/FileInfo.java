package com.aicontrol.android.aircam.utils;

import android.text.TextUtils;
import java.io.Serializable;

/* loaded from: classes5.dex */
public class FileInfo implements Serializable {
    private String createDate;
    private String dateMes;
    private int fileType;
    private String filename;
    private boolean isDirectory;
    private String mPath;
    private long mSize;
    private long height = 0;
    private boolean isAVI = false;
    private boolean isSelected = false;
    private long totalTime = 0;
    private long width = 0;

    public String getFilename() {
        return this.filename;
    }

    public void setFilename(String str) {
        this.filename = str;
    }

    public long getSize() {
        return this.mSize;
    }

    public void setSize(long j) {
        this.mSize = j;
    }

    public boolean isDirectory() {
        return this.isDirectory;
    }

    public void setDirectory(boolean z) {
        this.isDirectory = z;
    }

    public void setPath(String str) {
        this.mPath = str;
    }

    public String getPath() {
        return this.mPath;
    }

    public void setIsAVI(boolean z) {
        this.isAVI = z;
    }

    public boolean getIsAVI() {
        return this.isAVI;
    }

    public void setTotalTime(long j) {
        this.totalTime = j;
    }

    public long getTotalTime() {
        return this.totalTime;
    }

    public void setWidth(long j) {
        this.width = j;
    }

    public long getWidth() {
        return this.width;
    }

    public void setHeight(long j) {
        this.height = j;
    }

    public long getHeight() {
        return this.height;
    }

    public void setCreateDate(String str) {
        this.createDate = str;
    }

    public String getCreateDate() {
        return this.createDate;
    }

    public void setDateMes(String str) {
        this.dateMes = str;
    }

    public String getDateMes() {
        return this.dateMes;
    }

    public boolean isSelected() {
        return this.isSelected;
    }

    public void setSelected(boolean z) {
        this.isSelected = z;
    }

    public int getFileType() {
        return this.fileType;
    }

    public void setFileType(int i) {
        this.fileType = i;
    }

    public String toString() {
        String str = "{\"isDirectory\":" + this.isDirectory + ",\n\"mSize\":" + this.mSize + ",\n\"isAVI\":" + this.isAVI + ",\n\"totalTime\":" + this.totalTime + ",\n\"width\":" + this.width + ",\n\"height\":" + this.height + ",\n\"isSelected\":" + this.isSelected + ",\n";
        if (!TextUtils.isEmpty(this.filename)) {
            str = str + "\"filename\":\"" + this.filename + "\",\n";
        }
        if (!TextUtils.isEmpty(this.mPath)) {
            str = str + "\"mPath\":\"" + this.mPath + "\",\n";
        }
        if (!TextUtils.isEmpty(this.createDate)) {
            str = str + "\"createDate\":\"" + this.createDate + "\",\n";
        }
        if (!TextUtils.isEmpty(this.dateMes)) {
            str = str + "\"dateMes\":\"" + this.dateMes + "\",\n";
        }
        int lastIndexOf = str.lastIndexOf(",");
        if (lastIndexOf > -1) {
            str = str.substring(0, lastIndexOf);
        }
        return str + "}";
    }
}
