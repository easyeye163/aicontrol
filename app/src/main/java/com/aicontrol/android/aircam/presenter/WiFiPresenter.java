package com.aicontrol.android.aircam.presenter;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.text.SpannableStringBuilder;
import android.util.Log;
import com.aicontrol.android.aircam.utils.Camera;
import com.aicontrol.android.aircam.activity.PlayActivity;
import com.aicontrol.android.aircam.media.Media;
import com.aicontrol.android.aircam.media.OggPlayer;
import com.aicontrol.android.aircam.model.WiFiModelImpl;
import com.aicontrol.android.aircam.model.listener.IModelCallBack;
import com.aicontrol.android.aircam.presenter.listener.IPresenter;
import com.aicontrol.android.aircam.presenter.listener.ISensorListener;
import com.aicontrol.android.aircam.presenter.sensor.GSensor;
import com.aicontrol.android.aircam.utils.LogUtils;
import com.aicontrol.android.aircam.utils.PathUtils;
import com.aicontrol.android.aircam.view.base.ICaptureView;
import com.aicontrol.android.aircam.model.yuan.IData;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;

/* loaded from: classes5.dex */
public class WiFiPresenter implements IPresenter, IModelCallBack, IData {
    private static final int VIDEO_REC_FRAME_RATE = 25;
    private static WiFiPresenter mInstance;
    private static PaintFlagsDrawFilter pfd = new PaintFlagsDrawFilter(0, 3);
    protected ICaptureView captureView;
    private GSensor gSensor;
    private int iretain;
    private Context mContext;
    private Observable mObsv;
    private PathUtils mPathUtils;
    private Snap mPhoto;
    private byte[] mdat;
    private Media media;
    private Thread mthread;
    private OggPlayer oggPlayer;
    private int resolution;
    private int tFrame;
    private WiFiModelImpl wiFiModel;
    LogUtils logUtils = LogUtils.setLogger(WiFiPresenter.class);
    private Thread mThread = null;
    private int srcWidth = 0;
    private int srcHeight = 0;
    private String videoPath = null;
    private long tStart = 0;
    private LinkedBlockingQueue<byte[]> mList = new LinkedBlockingQueue<>();
    private int modehand = -1;
    private float val = 0.0f;
    private boolean mRun = false;
    private String mode = null;
    private Observer<ArrayList<String>> mobserver = new Observer<ArrayList<String>>() { // from class: com.tzh.wifi.wificam.presenter.WiFiPresenter.2
        @Override // io.reactivex.Observer
        public void onSubscribe(Disposable disposable) {
        }

        @Override // io.reactivex.Observer
        public void onNext(ArrayList<String> arrayList) {
            WiFiPresenter.this.mDetectProc = false;
        }

        @Override // io.reactivex.Observer
        public void onError(Throwable th) {
            WiFiPresenter.this.mDetectProc = false;
        }

        @Override // io.reactivex.Observer
        public void onComplete() {
            WiFiPresenter.this.mDetectProc = false;
        }
    };
    private boolean mDetectProc = false;
    private long cnt = 0;
    private Canvas mCanvas = new Canvas();
    private Paint mPaint = new Paint();

    static /* synthetic */ long access$608(WiFiPresenter wiFiPresenter) {
        long j = wiFiPresenter.cnt;
        wiFiPresenter.cnt = 1 + j;
        return j;
    }

    private WiFiPresenter(Context context) {
        this.wiFiModel = null;
        this.mContext = null;
        this.media = null;
        this.oggPlayer = null;
        this.gSensor = null;
        this.mPhoto = null;
        this.mPathUtils = null;
        this.tFrame = 0;
        this.wiFiModel = new WiFiModelImpl(context, this);
        this.media = new Media(context);
        this.oggPlayer = new OggPlayer(context);
        this.gSensor = new GSensor(context);
        this.mPhoto = new Snap(context);
        this.mPathUtils = new PathUtils(context);
        this.tFrame = 38;
        this.mContext = context;
    }

    public static WiFiPresenter getInstance(Context context) {
        synchronized (WiFiPresenter.class) {
            if (mInstance == null) {
                mInstance = new WiFiPresenter(context);
            }
        }
        return mInstance;
    }

    public void playSound(int i) {
        if (i == 0) {
            this.media.play();
        } else {
            if (i != 1) {
                return;
            }
            this.oggPlayer.play();
        }
    }

    public void prepareSnap(boolean z) {
        Snap snap = this.mPhoto;
        if (snap != null) {
            snap.prepareSnap(this.resolution, this.iretain, z);
        }
    }

    public void takeSnap(Bitmap bitmap) {
        Snap snap = this.mPhoto;
        if (snap != null) {
            snap.takeSnap(bitmap);
        }
    }

    public void videoTakeSnap(Bitmap bitmap) {
        Snap snap = this.mPhoto;
        if (snap != null) {
            snap.videoTakeSnap(bitmap);
        }
    }

    public void prepareRecord(boolean newFile) {
        try {
            String dateStr = getDateStr("yyyy-MM-dd-HH-mm-ss");
            File dir = this.mPathUtils.vrFile;
            if (dir != null && !dir.exists()) {
                dir.mkdirs();
            }
            this.videoPath = dir.getAbsolutePath() + "/" + dateStr + ".mp4";
            startYuanRecord(this.resolution);
        } catch (Exception e) {
            this.logUtils.e("prepareRecord error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stopRecord() {
        this.mRun = false;
        Thread thread = this.mthread;
        if (thread != null) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            this.mthread = null;
        }
        this.mList.clear();
        Camera.iYuanRelease();
        try {
            invalidate(new File(this.videoPath));
        } catch (NullPointerException unused) {
        }
        this.videoPath = null;
    }

    public ContentValues getVideoContentValues(Context context, File file, long j) {
        ContentValues contentValues = new ContentValues();
        contentValues.put("title", this.mPathUtils.vrFile.getParent());
        contentValues.put("_display_name", new File(this.videoPath).getParentFile().getName());
        contentValues.put("mime_type", "video/mp4");
        contentValues.put("datetaken", Long.valueOf(j));
        contentValues.put("date_modified", Long.valueOf(j));
        contentValues.put("date_added", Long.valueOf(j));
        contentValues.put("_data", file.getAbsolutePath());
        contentValues.put("_size", Long.valueOf(file.length()));
        contentValues.put("duration", Integer.valueOf(getLocalVideoDuration(this.videoPath)));
        return contentValues;
    }

    public void invalidate(File file) {
        MediaScannerConnection.scanFile(this.mContext, new String[]{file.toString()}, null, null);
    }

    public static int getLocalVideoDuration(String str) {
        try {
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
            mediaMetadataRetriever.setDataSource(str);
            return Integer.parseInt(mediaMetadataRetriever.extractMetadata(9));
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public void onRegisterSensor(ISensorListener iSensorListener) {
        this.gSensor.register(iSensorListener);
    }

    public void onUnregisterSensor() {
        this.gSensor.unregister();
    }

    public ICaptureView getView() {
        return this.captureView;
    }

    public void attachView(ICaptureView iCaptureView) {
        this.captureView = iCaptureView;
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.setOnDataListener(this);
            this.wiFiModel.iCameraStart();
        }
    }

    public void disattachView() {
        this.captureView = null;
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.setOnDataListener(null);
            this.mRun = false;
            this.wiFiModel.iCameraStop();
        }
    }

    public void onAutoPhotoClick(boolean z, int i) {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.onAutoPhotoClick(z, i);
        }
    }

    @Override // com.tzh.wifi.wificam.model.listener.IModelCallBack
    public void iFaceDector() {
        ICaptureView iCaptureView = this.captureView;
        if (iCaptureView != null) {
            iCaptureView.onFaceDector();
        }
    }

    @Override // com.tzh.wifi.wificam.model.listener.IModelCallBack
    public void connected() {
        ICaptureView iCaptureView = this.captureView;
        if (iCaptureView != null) {
            iCaptureView.connected();
        }
    }

    @Override // com.tzh.wifi.wificam.model.listener.IModelCallBack
    public void snapState(int i) {
        ICaptureView iCaptureView = this.captureView;
        if (iCaptureView != null) {
            iCaptureView.snapState(i);
        }
    }

    @Override // com.tzh.wifi.wificam.model.listener.IModelCallBack
    public void cameraType(int i) {
        ICaptureView iCaptureView = this.captureView;
        if (iCaptureView != null) {
            iCaptureView.cameraType(i);
        }
    }

    @Override // com.tzh.wifi.wificam.model.listener.IModelCallBack
    public void disconnected() {
        ICaptureView iCaptureView = this.captureView;
        if (iCaptureView != null) {
            iCaptureView.disconnected();
        }
    }

    @Override // com.tzh.wifi.wificam.model.listener.IModelCallBack
    public void recvFrame(int i, int i2, Bitmap bitmap) {
        if (this.captureView != null) {
            this.resolution = i;
            this.iretain = i2;
            this.srcWidth = bitmap.getWidth();
            this.srcHeight = bitmap.getHeight();
            this.captureView.reciveBitmap(i, i2, bitmap);
        }
    }

    public void ICmd_OneKeyFly() {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.ICmd_OneKeyFly();
        }
    }

    public void ICmd_OneKeyLand() {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.ICmd_OneKeyLand();
        }
    }

    public void ICmd_OneKeyMergency() {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.ICmd_OneKeyMergency();
        }
    }

    public void ICmd_CheckOutFlg() {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.ICmd_CheckOutFlg();
        }
    }

    public void ICmd_NoHeadModle(boolean z) {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.ICmd_NoHeadModle(z);
        }
    }

    public void ICmd_StayHighModle(boolean z) {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.ICmd_StayHighModle(z);
        }
    }

    public void ICmd_SetRotate(boolean z) {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.ICmd_SetRotate(z);
        }
    }

    public void ICmd_Start() {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.ICmd_Start();
        }
    }

    public void ICmd_Resume() {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.ICmd_Resume();
        }
    }

    public void ICmd_Stop() {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.ICmd_Stop();
        }
    }

    public void ICmd_SetTune(byte b, byte b2, byte b3) {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.ICmd_SetTune(b, b2, b3);
        }
    }

    public void ICmd_AccNotify(byte b, byte b2) {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.ICmd_AccNotify(b, b2);
        }
    }

    public void ICmd_DirNotify(byte b, byte b2) {
        WiFiModelImpl wiFiModelImpl = this.wiFiModel;
        if (wiFiModelImpl != null) {
            wiFiModelImpl.ICmd_DirNotify(b, b2);
        }
    }

    @Override // com.yuan.IData
    public void onData(byte[] bArr, int i) {
        if (i != bArr.length) {
            Log.e("MJPEG", "---------------------------->" + i + "---->" + bArr.length);
        }
        if (!this.mDetectProc) {
            detect(bArr, i);
        }
        if (this.mRun) {
            this.mList.offer(bArr);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public boolean isFrameCome(long j) {
        if (System.currentTimeMillis() - j < this.tFrame) {
            return false;
        }
        this.tStart = System.currentTimeMillis();
        return true;
    }

    private void startYuanRecord(final int i) {
        this.mRun = true;
        if (this.mThread == null || !this.mthread.isAlive()) {
            Thread thread = new Thread(new Runnable() { // from class: com.tzh.wifi.wificam.presenter.WiFiPresenter.1
                @Override // java.lang.Runnable
                public void run() {
                    WiFiPresenter.this.tStart = System.currentTimeMillis();
                    byte[] bArr = null;
                    while (WiFiPresenter.this.mRun) {
                        WiFiPresenter wiFiPresenter = WiFiPresenter.this;
                        if (!wiFiPresenter.isFrameCome(wiFiPresenter.tStart)) {
                            try {
                                Thread.sleep(5L);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        } else {
                            WiFiPresenter.this.tStart = System.currentTimeMillis();
                            if (!WiFiPresenter.this.mList.isEmpty()) {
                                bArr = (byte[]) WiFiPresenter.this.mList.poll();
                                if (bArr != null) {
                                    Camera.iYuanProc(bArr, bArr.length, i);
                                }
                            } else if (bArr != null) {
                                WiFiPresenter.this.logUtils.e("### lose frame insert!\n");
                                Camera.iYuanProc(bArr, bArr.length, i);
                            }
                        }
                    }
                }
            });
            this.mthread = thread;
            thread.start();
        }
    }

    private void detect(byte[] bArr, int i) {
        if (PlayActivity.classifier == null || this.mDetectProc) {
            return;
        }
        this.mDetectProc = true;
        this.mdat = (byte[]) bArr.clone();
        if (this.mObsv == null) {
            this.mObsv = Observable.create(new ObservableOnSubscribe<ArrayList<String>>() { // from class: com.tzh.wifi.wificam.presenter.WiFiPresenter.3
                @Override // io.reactivex.ObservableOnSubscribe
                public void subscribe(ObservableEmitter<ArrayList<String>> observableEmitter) throws Exception {
                    Bitmap decodeByteArray = BitmapFactory.decodeByteArray(WiFiPresenter.this.mdat, 0, WiFiPresenter.this.mdat.length);
                    if (decodeByteArray == null) {
                        WiFiPresenter.this.mDetectProc = false;
                        return;
                    }
                    WiFiPresenter.access$608(WiFiPresenter.this);
                    SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder();
                    Bitmap extractThumbnail = ThumbnailUtils.extractThumbnail(decodeByteArray, 224, 224);
                    PlayActivity.classifier.classifyFrame(extractThumbnail, spannableStringBuilder);
                    extractThumbnail.recycle();
                    decodeByteArray.recycle();
                    Log.e("amlan", spannableStringBuilder.toString());
                    if (spannableStringBuilder.toString().indexOf(":") != -1) {
                        Log.e("token", spannableStringBuilder.toString().substring(0, spannableStringBuilder.toString().indexOf(":")));
                    }
                    ArrayList<String> arrayList = new ArrayList<>();
                    arrayList.add(spannableStringBuilder.toString());
                    observableEmitter.onNext(arrayList);
                }
            }).subscribeOn(Schedulers.computation()).subscribeOn(AndroidSchedulers.mainThread());
        }
        this.mObsv.subscribe(this.mobserver);
    }

    private Bitmap scaleImageCavans(Bitmap bitmap, int i, int i2) {
        if (bitmap == null) {
            return null;
        }
        Bitmap createBitmap = Bitmap.createBitmap(i, i, Bitmap.Config.ARGB_8888);
        this.mCanvas.setBitmap(createBitmap);
        this.mPaint.setXfermode(null);
        this.mPaint.setAntiAlias(true);
        this.mCanvas.save();
        this.mCanvas.scale(i / bitmap.getWidth(), i2 / bitmap.getHeight());
        this.mCanvas.setDrawFilter(pfd);
        this.mCanvas.drawBitmap(bitmap, 0.0f, 0.0f, (Paint) null);
        this.mCanvas.restore();
        return createBitmap;
    }

    private void saveToLocal(Bitmap bitmap, String str) throws IOException {
        File file = new File("/sdcard/" + str + ".jpg");
        if (file.exists()) {
            file.delete();
        }
        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)) {
                fileOutputStream.flush();
                fileOutputStream.close();
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e2) {
            e2.printStackTrace();
        }
    }

    public static String getDateStr(String str) {
        if (str == null || str.isEmpty()) {
            str = "yyyy-MM-dd-HH-mm-ss";
        }
        return new SimpleDateFormat(str).format(new Date());
    }
}
