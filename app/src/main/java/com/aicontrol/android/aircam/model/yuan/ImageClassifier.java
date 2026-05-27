package com.aicontrol.android.aircam.model.yuan;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import androidx.core.view.ViewCompat;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.StringTokenizer;
import org.tensorflow.lite.Interpreter;

/* loaded from: classes5.dex */
public abstract class ImageClassifier {
    private static final int DIM_BATCH_SIZE = 1;
    private static final int DIM_PIXEL_SIZE = 3;
    private static final float FILTER_FACTOR = 0.4f;
    private static final int FILTER_STAGES = 3;
    private static final float GOOD_PROB_THRESHOLD = 0.3f;
    private static final int RESULTS_TO_SHOW = 3;
    private static final int SMALL_COLOR = -2250104;
    private static final String TAG = "TfLiteCameraDemo";
    private float[][] filterLabelProbArray;
    protected ByteBuffer imgData;
    private List<String> labelList;
    protected Interpreter tflite;
    private int[] intValues = new int[getImageSizeX() * getImageSizeY()];
    private PriorityQueue<Map.Entry<String, Float>> sortedLabels = new PriorityQueue<>(3, new Comparator<Map.Entry<String, Float>>() { // from class: com.yuan.ImageClassifier.1
        @Override // java.util.Comparator
        public int compare(Map.Entry<String, Float> entry, Map.Entry<String, Float> entry2) {
            return entry.getValue().compareTo(entry2.getValue());
        }
    });

    protected abstract void addPixelValue(int i);

    protected abstract int getImageSizeX();

    protected abstract int getImageSizeY();

    protected abstract String getLabelPath();

    protected abstract String getModelPath();

    protected abstract float getNormalizedProbability(int i);

    protected abstract int getNumBytesPerChannel();

    protected abstract float getProbability(int i);

    protected abstract void runInference();

    protected abstract void setProbability(int i, Number number);

    ImageClassifier(Activity activity) throws IOException {
        this.imgData = null;
        this.filterLabelProbArray = null;
        this.tflite = new Interpreter(loadModelFile(activity));
        this.labelList = loadLabelList(activity);
        ByteBuffer allocateDirect = ByteBuffer.allocateDirect(getImageSizeX() * 1 * getImageSizeY() * 3 * getNumBytesPerChannel());
        this.imgData = allocateDirect;
        allocateDirect.order(ByteOrder.nativeOrder());
        this.filterLabelProbArray = (float[][]) Array.newInstance((Class<?>) Float.TYPE, 3, getNumLabels());
        Log.d(TAG, "Created a Tensorflow Lite Image Classifier.");
    }

    public void classifyFrame(Bitmap bitmap, SpannableStringBuilder spannableStringBuilder) {
        printTopKLabels(spannableStringBuilder);
        if (this.tflite == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.");
            spannableStringBuilder.append(new SpannableString("Uninitialized Classifier."));
        }
        convertBitmapToByteBuffer(bitmap);
        long uptimeMillis = SystemClock.uptimeMillis();
        runInference();
        long uptimeMillis2 = SystemClock.uptimeMillis();
        StringBuilder sb = new StringBuilder("Timecost to run model inference: ");
        long j = uptimeMillis2 - uptimeMillis;
        sb.append(Long.toString(j));
        Log.d(TAG, sb.toString());
        applyFilter();
        SpannableString spannableString = new SpannableString(j + " ms");
        spannableString.setSpan(new ForegroundColorSpan(-3355444), 0, spannableString.length(), 0);
        spannableStringBuilder.append((CharSequence) spannableString);
    }

    void applyFilter() {
        int numLabels = getNumLabels();
        for (int i = 0; i < numLabels; i++) {
            float[] fArr = this.filterLabelProbArray[0];
            fArr[i] = fArr[i] + ((getProbability(i) - this.filterLabelProbArray[0][i]) * FILTER_FACTOR);
        }
        for (int i2 = 1; i2 < 3; i2++) {
            for (int i3 = 0; i3 < numLabels; i3++) {
                float[][] fArr2 = this.filterLabelProbArray;
                float[] fArr3 = fArr2[i2];
                float f = fArr3[i3];
                fArr3[i3] = f + ((fArr2[i2 - 1][i3] - f) * FILTER_FACTOR);
            }
        }
        for (int i4 = 0; i4 < numLabels; i4++) {
            setProbability(i4, Float.valueOf(this.filterLabelProbArray[2][i4]));
        }
    }

    public void setUseNNAPI(boolean bool) {
        // TFLite 2.13.0 no longer supports setUseNNAPI - no-op
    }

    public void setNumThreads(int i) {
        // TFLite 2.13.0 Interpreter doesn't have setNumThreads - configure via options instead
    }

    public void close() {
        this.tflite.close();
        this.tflite = null;
    }

    private List<String> loadLabelList(Activity activity) throws IOException {
        ArrayList arrayList = new ArrayList();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(activity.getAssets().open(getLabelPath())));
        String readLine = bufferedReader.readLine();
        bufferedReader.close();
        StringTokenizer stringTokenizer = new StringTokenizer(readLine, ",");
        while (stringTokenizer.hasMoreTokens()) {
            arrayList.add(stringTokenizer.nextToken());
        }
        return arrayList;
    }

    private MappedByteBuffer loadModelFile(Activity activity) throws IOException {
        AssetFileDescriptor openFd = activity.getAssets().openFd(getModelPath());
        return new FileInputStream(openFd.getFileDescriptor()).getChannel().map(FileChannel.MapMode.READ_ONLY, openFd.getStartOffset(), openFd.getDeclaredLength());
    }

    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        ByteBuffer byteBuffer = this.imgData;
        if (byteBuffer == null) {
            return;
        }
        byteBuffer.rewind();
        bitmap.getPixels(this.intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        long uptimeMillis = SystemClock.uptimeMillis();
        int i = 0;
        for (int i2 = 0; i2 < getImageSizeX(); i2++) {
            int i3 = 0;
            while (i3 < getImageSizeY()) {
                addPixelValue(this.intValues[i]);
                i3++;
                i++;
            }
        }
        Log.d(TAG, "Timecost to put values into ByteBuffe`r: " + Long.toString(SystemClock.uptimeMillis() - uptimeMillis));
    }

    private void printTopKLabels(SpannableStringBuilder spannableStringBuilder) {
        for (int i = 0; i < getNumLabels(); i++) {
            this.sortedLabels.add(new AbstractMap.SimpleEntry(this.labelList.get(i), Float.valueOf(getNormalizedProbability(i))));
            if (this.sortedLabels.size() > 3) {
                this.sortedLabels.poll();
            }
        }
        int size = this.sortedLabels.size();
        int i2 = 0;
        while (i2 < size) {
            Map.Entry<String, Float> poll = this.sortedLabels.poll();
            SpannableString spannableString = new SpannableString(String.format("%s:  %4.2f\n", poll.getKey(), poll.getValue()));
            int i3 = poll.getValue().floatValue() > GOOD_PROB_THRESHOLD ? ViewCompat.MEASURED_STATE_MASK : SMALL_COLOR;
            int i4 = size - 1;
            if (i2 == i4) {
                spannableString.setSpan(new RelativeSizeSpan(i2 == i4 ? 1.75f : 0.8f), 0, spannableString.length(), 0);
            }
            spannableString.setSpan(new ForegroundColorSpan(i3), 0, spannableString.length(), 0);
            spannableStringBuilder.insert(0, (CharSequence) spannableString);
            i2++;
        }
    }

    protected int getNumLabels() {
        return this.labelList.size();
    }
}
