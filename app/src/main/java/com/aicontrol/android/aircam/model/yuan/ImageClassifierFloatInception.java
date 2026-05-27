package com.aicontrol.android.aircam.model.yuan;

import android.app.Activity;
import java.io.IOException;
import java.lang.reflect.Array;

/* loaded from: classes5.dex */
public class ImageClassifierFloatInception extends ImageClassifier {
    private static final float IMAGE_MEAN = 1.0f;
    private static final float IMAGE_STD = 127.0f;
    private float[][] labelProbArray;

    @Override // com.yuan.ImageClassifier
    protected int getImageSizeX() {
        return 224;
    }

    @Override // com.yuan.ImageClassifier
    protected int getImageSizeY() {
        return 224;
    }

    @Override // com.yuan.ImageClassifier
    protected String getLabelPath() {
        return "labels.txt";
    }

    @Override // com.yuan.ImageClassifier
    protected String getModelPath() {
        return "model.tflite";
    }

    @Override // com.yuan.ImageClassifier
    protected int getNumBytesPerChannel() {
        return 4;
    }

    public ImageClassifierFloatInception(Activity activity) throws IOException {
        super(activity);
        this.labelProbArray = null;
        this.labelProbArray = (float[][]) Array.newInstance((Class<?>) Float.TYPE, 1, getNumLabels());
    }

    @Override // com.yuan.ImageClassifier
    protected void addPixelValue(int i) {
        this.imgData.putFloat((((i >> 16) & 255) / IMAGE_STD) - 1.0f);
        this.imgData.putFloat((((i >> 8) & 255) / IMAGE_STD) - 1.0f);
        this.imgData.putFloat(((i & 255) / IMAGE_STD) - 1.0f);
    }

    @Override // com.yuan.ImageClassifier
    protected float getProbability(int i) {
        return this.labelProbArray[0][i];
    }

    @Override // com.yuan.ImageClassifier
    protected void setProbability(int i, Number number) {
        this.labelProbArray[0][i] = number.floatValue();
    }

    @Override // com.yuan.ImageClassifier
    protected float getNormalizedProbability(int i) {
        return getProbability(i);
    }

    @Override // com.yuan.ImageClassifier
    protected void runInference() {
        this.tflite.run(this.imgData, this.labelProbArray);
    }
}
