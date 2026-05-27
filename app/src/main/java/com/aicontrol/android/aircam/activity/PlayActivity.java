package com.aicontrol.android.aircam.activity;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.aicontrol.android.R;
import com.google.common.base.Ascii;
import com.aicontrol.android.aircam.model.gesture.FaceTask;
// TODO: Kongzue DialogX disabled - library not available
// import com.kongzue.dialog.interfaces.OnDialogButtonClickListener;
// import com.kongzue.dialog.interfaces.OnMenuItemClickListener;
// import com.kongzue.dialog.util.BaseDialog;
// import com.kongzue.dialog.v3.BottomMenu;
import com.aicontrol.android.aircam.utils.Camera;
import com.aicontrol.android.aircam.base.BaseActivity;
import com.aicontrol.android.aircam.bean.MessageWrap;
import com.aicontrol.android.aircam.model.speech.SpeechRecognizerUtil;
import com.aicontrol.android.aircam.presenter.WiFiPresenter;
import com.aicontrol.android.aircam.presenter.listener.ISensorListener;
import com.aicontrol.android.aircam.presenter.listener.ITargetBitmapFixListener;
import com.aicontrol.android.aircam.utils.BmpUtils;
import com.aicontrol.android.aircam.utils.ConfigUtils;
import com.aicontrol.android.aircam.utils.Constants;
import com.aicontrol.android.aircam.utils.FileChooseUtil;
import com.aicontrol.android.aircam.utils.LocalUtil;
import com.aicontrol.android.aircam.utils.LogUtils;
import com.aicontrol.android.aircam.utils.PathUtils;
import com.aicontrol.android.aircam.utils.StringUtils;
// DisplayImage now using ImageView directly in XML to avoid ClassCastException
import com.aicontrol.android.aircam.view.ZoomView;
import com.aicontrol.android.aircam.view.base.ICaptureView;
import com.aicontrol.android.aircam.view.listener.IRudderListener;
import com.aicontrol.android.aircam.view.listener.IScaleListener;
import com.aicontrol.android.aircam.view.pathdraw.PathEvaluator;
import com.aicontrol.android.aircam.view.pathdraw.PathPoint;
import com.aicontrol.android.aircam.view.rudder.Rudder;
import com.aicontrol.android.aircam.view.slider.SliderHor;
import com.aicontrol.android.aircam.view.slider.SliderVer;
import com.aicontrol.android.aircam.view.slider.listener.ISliderListener;
import com.aicontrol.android.aircam.model.yuan.ImageClassifier;
// TODO: PocketSphinx voice control disabled - library not available
// import edu.cmu.pocketsphinx.Hypothesis;
// import edu.cmu.pocketsphinx.RecognitionListener;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.Collection;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/* loaded from: classes5.dex */
public class PlayActivity extends BaseActivity implements View.OnClickListener, ICaptureView, ISliderListener, ISensorListener, IRudderListener, ITargetBitmapFixListener, IScaleListener {
    private static final int MSG_BITMAP_PROCESSED = 2;
    public static String audio_str = null;
    public static ImageClassifier classifier = null;
    public static boolean disposeFlagInit = false;
    private static long nStartFrameTime;
    public static String path1;
    public static String path2;
    private PathStartRunnable PathStartRunnable;
    private ScaleRunnable ScaleRunnable;
    private ImageView btnChangeOrientation;
    ConnectivityManager connectivityManager;
    private FaceDectorRunnable faceDectorRunnable;
    private HandRecordDectorRunnable handRecordDectorRunnable;
    private HandSnapDectorRunnable handSnapDectorRunnable;
    private BmpUtils mBmpUtils;
    private CloseVoiceControlRunnable mCloseVoiceControl;
    FaceTask mFaceTask;
    private SafeHandler mHandler;
    private RecRunnable mRecRunnable;
    private PathResetRunnable pathResetRunnable;
    private PathRunnable pathRunnable;
    private ResetRunnable resetRunnable;
    private SpeechRecognizerUtil speechRecognizerUtil;
    private Vibrator vibrator;
    private ZoomView zoomView;
    private ImageView ivLeftImage = null;
    private ImageView ivRightImage = null;
    private ImageView btnVrPlay = null;
    private ImageView btnRecord = null;
    private ImageView btnPhotoSnap = null;
    private ImageView btnSpeed = null;
    private ImageView btnAutoPhoto = null;
    private ImageView btnReverse = null;
    private ImageView btnLock = null;
    private ImageView btnButton = null;
    private ImageView btnMp3Switch = null;
    private ImageView img_filter_play = null;
    private ImageView img_voice_control = null;
    private ImageView btnAutoPath = null;
    private ImageView btnRotate = null;
    private ImageView btnNoHead = null;
    private ImageView btnGensor = null;
    private ImageView btnStayHigh = null;
    private ImageView btnOneKeyFly = null;
    private ImageView btnOneKeyLand = null;
    private ImageView btnOneKeyStop = null;
    private ImageView btnCheckout = null;
    private ImageView ivRoundMove = null;
    private TextView tvScaleValue = null;
    private TextView tvVoiceWord = null;
    private boolean bVrPlay = false;
    private DisplayMetrics displayMetrics = null;
    private Rudder mRudder = null;
    private int screenWidth = 0;
    private int screenHeight = 0;
    private SliderHor leftSlider = null;
    private SliderHor rightSlider = null;
    private SliderVer centerSlider = null;
    private boolean bSensorClick = false;
    private boolean bStayHighClick = false;
    private boolean bOneKeyFlyClick = false;
    private boolean bOneKeyLandClick = false;
    private boolean bMengencyStopClick = false;
    private boolean bAutoPath = false;
    public boolean bAutoPhoto = false;
    private boolean bNoHeadMode = false;
    private boolean bVideoRecord = false;
    private boolean bPlayRotate = false;
    private LinearLayout lyHeadSecond = null;
    private LinearLayout lySliderBottom = null;
    private RelativeLayout lySliderCenter = null;
    private RelativeLayout lyMengencyStop = null;
    private ObjectAnimator objectAnimator = null;
    private LinearLayout lyRecordTime = null;
    private ImageView ivRecordIcon = null;
    private TextView tvRecordTime = null;
    private TextView tvAutoPhoto = null;
    private ImageView ivLoading = null;
    private ImageView imusic = null;
    LogUtils logUtils = LogUtils.setLogger(PlayActivity.class);
    private int recTime = 0;
    private boolean bWiFiConnect = false;
    private int nAutoPhoto = 3;
    private boolean bRotateActive = false;
    private RelativeLayout lyRudderMap = null;
    private long tStart = 0;
    public final int MSG_SPEECH_RECOGNIZER = 1;
    private volatile boolean isBitmapProcessing = false;
    private boolean bSpeechStart = false;
    private boolean isWait = false;
    private boolean isMove = false;
    private int cameraType = 0;
    private boolean is_portrait = false;

    @Override // android.view.Window.Callback
    public void onPointerCaptureChanged(boolean z) {
    }

    public void writeConfig() {
    }

    static /* synthetic */ int access$108(PlayActivity playActivity) {
        int i = playActivity.recTime;
        playActivity.recTime = i + 1;
        return i;
    }

    static /* synthetic */ int access$610(PlayActivity playActivity) {
        int i = playActivity.nAutoPhoto;
        playActivity.nAutoPhoto = i - 1;
        return i;
    }

    private static class SafeHandler extends Handler {
        private final WeakReference<PlayActivity> activityRef;

        SafeHandler(PlayActivity playActivity) {
            this.activityRef = new WeakReference<>(playActivity);
        }

        @Override // android.os.Handler
        public void handleMessage(Message message) {
            PlayActivity playActivity = this.activityRef.get();
            if (playActivity == null) {
                return;
            }
            playActivity.handleMessageInternal(message);
        }
    }

    private static final String TAG = "PlayActivity";

    @Override
    protected void onCreate(Bundle bundle) {
        try {
            Log.e(TAG, "=== onCreate START ===");
            super.onCreate(bundle);
            Log.e(TAG, "=== super.onCreate DONE ===");
            this.connectivityManager = (ConnectivityManager) getSystemService("connectivity");
            this.connectivityManager.bindProcessToNetwork(null);
            Log.e(TAG, "=== connectivityManager DONE ===");
            initLanguage();
            Log.e(TAG, "=== initLanguage DONE ===");
            new PathUtils(this);
            Log.e(TAG, "=== PathUtils DONE ===");
            getWindow().addFlags(128);
            setContentView(R.layout.activity_play);
            Log.e(TAG, "=== setContentView DONE ===");
            DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
            this.displayMetrics = displayMetrics;
            this.screenWidth = displayMetrics.widthPixels;
            this.screenHeight = this.displayMetrics.heightPixels;
            Log.e(TAG, "=== displayMetrics DONE ===");
            this.vibrator = (Vibrator) getSystemService("vibrator");
            Log.e(TAG, "=== vibrator DONE ===");
            this.speechRecognizerUtil = new SpeechRecognizerUtil(this, this);
            Log.e(TAG, "=== speechRecognizer DONE ===");
            this.mHandler = new SafeHandler(this);
            this.mRecRunnable = new RecRunnable(this);
            this.faceDectorRunnable = new FaceDectorRunnable(this);
            this.handSnapDectorRunnable = new HandSnapDectorRunnable(this);
            this.handRecordDectorRunnable = new HandRecordDectorRunnable(this);
            this.resetRunnable = new ResetRunnable(this);
            this.pathResetRunnable = new PathResetRunnable(this);
            this.pathRunnable = new PathRunnable(this);
            this.PathStartRunnable = new PathStartRunnable(this);
            this.ScaleRunnable = new ScaleRunnable(this);
            this.mCloseVoiceControl = new CloseVoiceControlRunnable(this);
            Log.e(TAG, "=== runnables DONE ===");
            if (path1 == null) {
                path1 = PathUtils.getInstance(this).copyFilesFromAssets("hand.param");
            }
            if (path2 == null) {
                path2 = PathUtils.getInstance(this).copyFilesFromAssets("hand.bin");
            }
            Log.e(TAG, "=== assets copy DONE ===");
            widget_init();
            Log.e(TAG, "=== widget_init DONE ===");
            readConfig();
            Log.e(TAG, "=== readConfig DONE ===");
            getWindow().getDecorView().post(new Runnable() {
                @Override
                public void run() {
                    if (getApp().bLockClick) {
                        WiFiPresenter.getInstance(PlayActivity.this).ICmd_Start();
                    }
                }
            });
            Log.e(TAG, "=== onCreate SUCCESS ===");
        } catch (Throwable t) {
            Log.e(TAG, "=== onCreate CRASH ===", t);
            t.printStackTrace();
            Toast.makeText(this, "PlayActivity init error: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initLanguage() {
        if (LocalUtil.isZh(this)) {
            StringUtils.getInstance(this).changeLan(0);
        } else {
            StringUtils.getInstance(this).changeLan(1);
        }
    }

    public void readConfig() {
        this.bStayHighClick = true;
        play_high_click_down();
    }

    private void widget_init() {
        Log.e(TAG, "=== widget_init START ===");
        try {
            this.mBmpUtils = new BmpUtils(this, this);
            Log.e(TAG, "=== BmpUtils DONE ===");
            this.ivLeftImage = (ImageView) findViewById(R.id.ivLeftImage);
            Log.e(TAG, "=== ivLeftImage DONE: " + this.ivLeftImage);
            this.ivRightImage = (ImageView) findViewById(R.id.ivRightImage);
            this.btnVrPlay = (ImageView) findViewById(R.id.btnVrPlay);
            this.mRudder = (Rudder) findViewById(R.id.playRudder);
            Log.e(TAG, "=== mRudder DONE: " + this.mRudder);
            this.tvScaleValue = (TextView) findViewById(R.id.tvScaleValue);
            this.tvVoiceWord = (TextView) findViewById(R.id.tvVoiceWord);
            this.leftSlider = (SliderHor) findViewById(R.id.playLeftSlider);
            Log.e(TAG, "=== leftSlider DONE: " + this.leftSlider);
            this.rightSlider = (SliderHor) findViewById(R.id.playRightSlider);
            this.centerSlider = (SliderVer) findViewById(R.id.playCenterSlider);
            Log.e(TAG, "=== centerSlider DONE: " + this.centerSlider);
            ZoomView zoomView = (ZoomView) findViewById(R.id.lyZoomView);
            Log.e(TAG, "=== zoomView DONE: " + zoomView);
            this.zoomView = zoomView;
            zoomView.setDelegate(this);
            this.btnRecord = (ImageView) findViewById(R.id.btnPlayRecord);
            this.btnPhotoSnap = (ImageView) findViewById(R.id.btnPlaySnap);
            this.btnSpeed = (ImageView) findViewById(R.id.btnPlaySpeed);
            this.btnVrPlay = (ImageView) findViewById(R.id.btnVrPlay);
            this.btnAutoPhoto = (ImageView) findViewById(R.id.btnPlayAutoPhoto);
            this.btnLock = (ImageView) findViewById(R.id.btnPlayLock);
            this.btnButton = (ImageView) findViewById(R.id.btnPlayButton);
            this.btnMp3Switch = (ImageView) findViewById(R.id.btnMp3Switch);
            this.img_filter_play = (ImageView) findViewById(R.id.img_filter_play);
            this.img_voice_control = (ImageView) findViewById(R.id.img_voice_control);
            ImageView imageView = (ImageView) findViewById(R.id.btnPlayPath);
            this.btnAutoPath = imageView;
            imageView.setVisibility(8);
            this.btnRotate = (ImageView) findViewById(R.id.btnPlayRoate);
            this.btnGensor = (ImageView) findViewById(R.id.btnPlayGensor);
            this.btnStayHigh = (ImageView) findViewById(R.id.btnPlayStayHigh);
            this.btnOneKeyFly = (ImageView) findViewById(R.id.btnPlayOneKeyFly);
            this.btnOneKeyLand = (ImageView) findViewById(R.id.btnPlayOneKeyLand);
            this.btnOneKeyStop = (ImageView) findViewById(R.id.btnMengencyStop);
            ImageView ivPlayBack = (ImageView) findViewById(R.id.btnPlayBack);
            ImageView ivCameraSwitch = (ImageView) findViewById(R.id.btnCameraSwitch);
            ImageView ivPlayReturn = (ImageView) findViewById(R.id.btnPlayReturn);
            this.lyHeadSecond = (LinearLayout) findViewById(R.id.ly_head_second);
            this.lySliderBottom = (LinearLayout) findViewById(R.id.lySliderBottom);
            this.lySliderCenter = (RelativeLayout) findViewById(R.id.lySliderCenter);
            this.lyMengencyStop = (RelativeLayout) findViewById(R.id.lyMengencyStop);
            this.lyRecordTime = (LinearLayout) findViewById(R.id.ly_record_time);
            this.ivRecordIcon = (ImageView) findViewById(R.id.ivRecordIcon);
            this.tvRecordTime = (TextView) findViewById(R.id.tvRecordTime);
            this.tvAutoPhoto = (TextView) findViewById(R.id.tvAutoPhoto);
            this.lyRecordTime.setVisibility(4);
            this.ivLoading = (ImageView) findViewById(R.id.ivLoading);
            this.ivRoundMove = (ImageView) findViewById(R.id.ivRoundMove);
            this.btnReverse = (ImageView) findViewById(R.id.btnPlayRev);
            this.imusic = (ImageView) findViewById(R.id.img_video_mp3);
            this.btnNoHead = (ImageView) findViewById(R.id.btnPlayNoHead);
            this.btnChangeOrientation = (ImageView) findViewById(R.id.btnChangeOrientation);
            Log.e(TAG, "=== all findViewById DONE ===");
            this.leftSlider.addSliderListener(this, ConfigUtils.getLeftTune(this));
            this.rightSlider.addSliderListener(this, ConfigUtils.getRightTune(this));
            this.centerSlider.addSliderListener(this, ConfigUtils.getCenterTune(this));
            Log.e(TAG, "=== slider listeners DONE ===");
            this.mRudder.registerListener(this);
            this.mRudder.invalidateValue();
            Log.e(TAG, "=== rudder DONE ===");
            // Set initial icons for ALL buttons
            this.btnRecord.setImageResource(R.mipmap.play_record_icon);
            this.btnPhotoSnap.setImageResource(R.mipmap.play_auto_photo);
            this.btnAutoPhoto.setImageResource(R.mipmap.play_auto_photo);
            this.btnRotate.setImageResource(R.mipmap.play_rotate_icon);
            this.btnGensor.setImageResource(R.mipmap.play_gsensor_icon);
            this.btnStayHigh.setImageResource(R.mipmap.play_high_icon);
            this.btnOneKeyFly.setImageResource(R.mipmap.zone_info_page1_cn);
            this.btnOneKeyLand.setImageResource(R.mipmap.zone_info_page2_cn);
            this.btnOneKeyStop.setImageResource(R.mipmap.zone_rotate_180);
            this.btnReverse.setImageResource(R.mipmap.zone_rotate_180);
            this.btnNoHead.setImageResource(R.mipmap.play_no_head_icon);
            this.img_filter_play.setImageResource(R.mipmap.play_button_icon);
            this.img_voice_control.setImageResource(R.mipmap.voice_nor);
            this.btnCheckout = (ImageView) findViewById(R.id.btnPlayCheckout);
            Log.e(TAG, "=== initial icons DONE ===");
            // Register click listeners for ALL buttons (was lost during decompilation)
            if (ivPlayBack != null) ivPlayBack.setOnClickListener(this);
            if (this.btnVrPlay != null) this.btnVrPlay.setOnClickListener(this);
            if (ivCameraSwitch != null) ivCameraSwitch.setOnClickListener(this);
            if (this.btnRotate != null) this.btnRotate.setOnClickListener(this);
            if (this.btnChangeOrientation != null) this.btnChangeOrientation.setOnClickListener(this);
            if (this.imusic != null) this.imusic.setOnClickListener(this);
            if (this.btnMp3Switch != null) this.btnMp3Switch.setOnClickListener(this);
            if (ivPlayReturn != null) ivPlayReturn.setOnClickListener(this);
            if (this.btnRecord != null) this.btnRecord.setOnClickListener(this);
            if (this.btnPhotoSnap != null) this.btnPhotoSnap.setOnClickListener(this);
            if (this.btnSpeed != null) this.btnSpeed.setOnClickListener(this);
            if (this.btnAutoPhoto != null) this.btnAutoPhoto.setOnClickListener(this);
            if (this.btnLock != null) this.btnLock.setOnClickListener(this);
            if (this.btnButton != null) this.btnButton.setOnClickListener(this);
            if (this.img_filter_play != null) this.img_filter_play.setOnClickListener(this);
            if (this.img_voice_control != null) this.img_voice_control.setOnClickListener(this);
            if (this.btnReverse != null) this.btnReverse.setOnClickListener(this);
            if (this.btnNoHead != null) this.btnNoHead.setOnClickListener(this);
            if (this.btnAutoPath != null) this.btnAutoPath.setOnClickListener(this);
            if (this.btnGensor != null) this.btnGensor.setOnClickListener(this);
            if (this.btnStayHigh != null) this.btnStayHigh.setOnClickListener(this);
            if (this.btnOneKeyFly != null) this.btnOneKeyFly.setOnClickListener(this);
            if (this.btnOneKeyLand != null) this.btnOneKeyLand.setOnClickListener(this);
            if (this.btnOneKeyStop != null) this.btnOneKeyStop.setOnClickListener(this);
            if (this.btnCheckout != null) this.btnCheckout.setOnClickListener(this);
            // Drone config button
            TextView btnDroneConfig = (TextView) findViewById(R.id.btnDroneConfig);
            if (btnDroneConfig != null) {
                btnDroneConfig.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        startActivity(new Intent(PlayActivity.this, DroneConfigActivity.class));
                    }
                });
            }
            Log.e(TAG, "=== setOnClickListener DONE ===");
            // Default lock to ON so UI is fully visible
            if (!getApp().bLockClick) {
                getApp().bLockClick = true;
            }
            play_lock_click_down();
            this.btnLock.setImageResource(R.mipmap.play_lock_icon_down);
            if (getApp().bButtonClick) {
                this.btnButton.setImageResource(R.mipmap.play_button_icon_down);
                play_button_click_down();
            } else {
                this.btnButton.setImageResource(R.mipmap.play_button_icon);
                play_button_click_up();
            }
            if (getApp().nSpeed == 0) {
                this.btnSpeed.setImageResource(R.mipmap.play_speed_1_icon);
            } else if (getApp().nSpeed == 1) {
                this.btnSpeed.setImageResource(R.mipmap.play_speed_2_icon);
            } else if (getApp().nSpeed == 2) {
                this.btnSpeed.setImageResource(R.mipmap.play_speed_3_icon);
            }
            if (getApp().bfilter_playClick) {
                play_filter_click_down();
            } else {
                play_filter_click_up();
            }
            Log.e(TAG, "=== widget_init SUCCESS ===");
        } catch (Throwable t) {
            Log.e(TAG, "=== widget_init CRASH ===", t);
            t.printStackTrace();
        }
    }

    private void performVibrate(long j) {
        VibrationEffect createOneShot;
        Vibrator vibrator = this.vibrator;
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            Vibrator vibrator2 = this.vibrator;
            createOneShot = VibrationEffect.createOneShot(j, -1);
            vibrator2.vibrate(createOneShot);
            return;
        }
        this.vibrator.vibrate(j);
    }

    private void checkAndStopVoiceControl(int i) {
        if ((i == R.id.btnPlayPath || i == R.id.btnPlayRoate || i == R.id.btnPlayNoHead || i == R.id.btnPlayCheckout || i == R.id.btnPlayGensor || i == R.id.btnPlayStayHigh || i == R.id.btnPlayOneKeyFly || i == R.id.btnPlayOneKeyLand || i == R.id.btnMengencyStop) && this.bSpeechStart) {
            stopVoiceControl();
        }
    }

    private void stopVoiceControl() {
        this.bSpeechStart = false;
        ImageView imageView = this.img_voice_control;
        if (imageView != null) {
            imageView.setImageResource(R.mipmap.voice_nor);
        }
        TextView textView = this.tvVoiceWord;
        if (textView != null) {
            textView.setVisibility(4);
        }
        SpeechRecognizerUtil speechRecognizerUtil = this.speechRecognizerUtil;
        if (speechRecognizerUtil == null || !speechRecognizerUtil.isRunning()) {
            return;
        }
        this.speechRecognizerUtil.stop();
    }

    private void handleVoiceControlClick() {
        if (!this.bSpeechStart) {
            if (this.bAutoPath) {
                this.btnAutoPath.performClick();
            }
            if (this.bPlayRotate) {
                this.btnRotate.performClick();
            }
            if (this.bNoHeadMode) {
                this.btnNoHead.performClick();
            }
            if (this.bSensorClick) {
                this.btnGensor.performClick();
            }
            this.bSpeechStart = true;
            ImageView imageView = this.img_voice_control;
            if (imageView != null) {
                imageView.setImageResource(R.mipmap.voice_sel);
            }
            TextView textView = this.tvVoiceWord;
            if (textView != null) {
                textView.setVisibility(0);
            }
            speechRecognizerStart();
            return;
        }
        stopVoiceControl();
    }

    private boolean checkWiFiConnection() {
        if (this.bWiFiConnect) {
            return true;
        }
        Toast.makeText(this, StringUtils.getInstance(this).strNoConnect, 0).show();
        return false;
    }

    @Override // com.tzh.wifi.wificam.base.BaseActivity, android.view.View.OnClickListener
    public void onClick(View view) {
        this.logUtils.e("###onClick");
        checkAndStopVoiceControl(view.getId());
        int id = view.getId();
        if (id == R.id.btnCameraSwitch) {
            if (checkWiFiConnection()) {
                performVibrate(40L);
                if (this.cameraType == 1) {
                    Toast.makeText(this, StringUtils.getInstance(this).strNoSupport, 1).show();
                } else {
                    long currentTimeMillis = System.currentTimeMillis();
                    if (currentTimeMillis - this.tStart >= 2000) {
                        Camera.iCameraSwitch();
                        this.tStart = currentTimeMillis;
                        this.logUtils.e("### camera switch!");
                    }
                }
            }
        } else if (id == R.id.btnChangeOrientation) {
            performVibrate(40L);
            if (getResources().getConfiguration().orientation == 1) {
                setRequestedOrientation(0);
                this.is_portrait = false;
            } else if (getResources().getConfiguration().orientation == 2) {
                setRequestedOrientation(1);
                this.is_portrait = true;
            }
            this.mBmpUtils.setIs_portrait(this.is_portrait);
        } else if (id == R.id.btnVrPlay) {
            performVibrate(30L);
            if (this.bVrPlay) {
                this.bVrPlay = false;
                play_vr_click_up();
            } else {
                this.bVrPlay = true;
                play_vr_click_down();
            }
        } else if (id == R.id.img_filter_play) {
            performVibrate(30L);
            if (getApp().bfilter_playClick) {
                getApp().bfilter_playClick = false;
                play_filter_click_up();
            } else {
                getApp().bfilter_playClick = true;
                play_filter_click_down();
            }
        } else if (id == R.id.btnMengencyStop) {
            performVibrate(100L);
            WiFiPresenter.getInstance(this).ICmd_OneKeyMergency();
        } else if (id == R.id.btnMp3Switch) {
            performVibrate(40L);
            EventBus.getDefault().postSticky(MessageWrap.getInstance("music_off"));
            Camera.stop_music();
        } else if (id == R.id.btnPlayAutoPhoto) {
            if (checkWiFiConnection()) {
                performVibrate(40L);
                if (this.bAutoPhoto) {
                    this.bAutoPhoto = false;
                    play_auto_photo_up();
                    this.btnAutoPhoto.setImageResource(R.mipmap.play_auto_photo);
                } else {
                    this.bAutoPhoto = true;
                    this.isWait = false;
                    play_auto_photo_down();
                    this.btnAutoPhoto.setImageResource(R.mipmap.play_auto_photo_down);
                    nStartFrameTime = System.currentTimeMillis();
                }
            }
        } else if (id == R.id.btnPlayBack) {
            performVibrate(30L);
            // PlayBackActivity disabled (ad SDK dependency removed)
        } else if (id == R.id.btnPlayButton) {
            performVibrate(30L);
            if (getApp().bButtonClick) {
                this.btnButton.setImageResource(R.mipmap.play_button_icon);
                getApp().bButtonClick = false;
                play_button_click_up();
            } else {
                this.btnButton.setImageResource(R.mipmap.play_button_icon_down);
                getApp().bButtonClick = true;
                play_button_click_down();
            }
        } else if (id == R.id.btnPlayCheckout) {
            performVibrate(40L);
            WiFiPresenter.getInstance(this).playSound(1);
            this.centerSlider.ISliderCheckOut();
            this.leftSlider.ISliderCheckOut();
            this.rightSlider.ISliderCheckOut();
            WiFiPresenter.getInstance(this).ICmd_CheckOutFlg();
        } else if (id == R.id.btnPlayGensor) {
            if (!getApp().bLockClick) {
                Toast.makeText(this, StringUtils.getInstance(this).strLockDown, 0).show();
            } else if (!this.bAutoPath) {
                performVibrate(40L);
                if (this.bSensorClick) {
                    this.bSensorClick = false;
                    this.btnGensor.setImageResource(R.mipmap.play_gsensor_icon);
                    play_sensor_click_up();
                } else {
                    this.bSensorClick = true;
                    play_sensor_click_down();
                    this.btnGensor.setImageResource(R.mipmap.play_gsensor_icon_down);
                }
            }
        } else if (id == R.id.btnPlayLock) {
            if (this.bSensorClick) {
                Toast.makeText(this, StringUtils.getInstance(this).strGsensorEnable, 1).show();
            } else {
                performVibrate(40L);
                if (getApp().bLockClick) {
                    getApp().bLockClick = false;
                    this.btnLock.setImageResource(R.mipmap.play_lock_icon);
                    play_lock_click_up();
                    WiFiPresenter.getInstance(this).ICmd_Resume();
                } else {
                    getApp().bLockClick = true;
                    play_lock_click_down();
                    this.mRudder.invalidateValue();
                    WiFiPresenter.getInstance(this).ICmd_Start();
                    this.btnLock.setImageResource(R.mipmap.play_lock_icon_down);
                }
            }
        } else if (id == R.id.btnPlayNoHead) {
            performVibrate(40L);
            if (this.bNoHeadMode) {
                this.bNoHeadMode = false;
                this.btnNoHead.setImageResource(R.mipmap.play_no_head_icon);
            } else {
                this.bNoHeadMode = true;
                this.btnNoHead.setImageResource(R.mipmap.play_no_head_icon_down);
            }
            WiFiPresenter.getInstance(this).ICmd_NoHeadModle(this.bNoHeadMode);
        } else if (id == R.id.btnPlayOneKeyFly) {
            performVibrate(60L);
            WiFiPresenter.getInstance(this).ICmd_OneKeyFly();
        } else if (id == R.id.btnPlayOneKeyLand) {
            performVibrate(60L);
            WiFiPresenter.getInstance(this).ICmd_OneKeyLand();
        } else if (id == R.id.btnPlayPath) {
            performVibrate(40L);
            if (this.bSensorClick) {
                Toast.makeText(this, StringUtils.getInstance(this).strGsensorEnable, 1).show();
            } else if (this.bAutoPath) {
                this.bAutoPath = false;
                this.btnAutoPath.setImageResource(R.mipmap.play_light_icon);
                play_path_click_up();
            } else {
                this.bAutoPath = true;
                this.btnAutoPath.setImageResource(R.mipmap.play_light_icon_down);
                play_path_click_down();
            }
        } else if (id == R.id.btnPlayRecord) {
            if (checkWiFiConnection()) {
                performVibrate(80L);
                if (this.bVideoRecord) {
                    play_record_click_up();
                } else {
                    play_record_click_down();
                }
            }
        } else if (id == R.id.btnPlayReturn) {
            performVibrate(30L);
            onBackPressed();
        } else if (id == R.id.btnPlayRev) {
            if (checkWiFiConnection()) {
                performVibrate(40L);
                Camera.iCameraRoate();
            }
        } else if (id == R.id.btnPlayRoate) {
            performVibrate(40L);
            if (this.bPlayRotate) {
                play_rotate_click_up();
                this.mRudder.onUnregisterRotate();
            } else {
                this.mRudder.onRegisterRotate();
                play_rotate_click_down();
            }
        } else if (id == R.id.btnPlaySnap) {
            WiFiPresenter.getInstance(this).playSound(0);
            if (checkWiFiConnection()) {
                performVibrate(50L);
                if (getApp().bRotate == 1) {
                    WiFiPresenter.getInstance(this).prepareSnap(true);
                } else {
                    WiFiPresenter.getInstance(this).prepareSnap(false);
                }
            }
        } else if (id == R.id.btnPlaySpeed) {
            performVibrate(30L);
            play_speed_click_down();
        } else if (id == R.id.btnPlayStayHigh) {
            performVibrate(40L);
            if (this.bStayHighClick) {
                this.bStayHighClick = false;
                this.btnStayHigh.setImageResource(R.mipmap.play_high_icon);
                play_high_click_up();
            } else {
                this.bStayHighClick = true;
                this.btnStayHigh.setImageResource(R.mipmap.play_high_icon_down);
                play_high_click_down();
            }
        } else if (id == R.id.img_video_mp3) {
            performVibrate(30L);
            startActivity(MusicActivity.class);
        } else if (id == R.id.img_voice_control) {
            performVibrate(40L);
            handleVoiceControlClick();
        }
    }

    private static class RecRunnable implements Runnable {
        private final WeakReference<PlayActivity> activityRef;

        RecRunnable(PlayActivity playActivity) {
            this.activityRef = new WeakReference<>(playActivity);
        }

        @Override // java.lang.Runnable
        public void run() {
            PlayActivity playActivity = this.activityRef.get();
            if (playActivity == null) {
                return;
            }
            PlayActivity.access$108(playActivity);
            int i = playActivity.recTime / 2;
            if (playActivity.recTime % 2 == 0) {
                playActivity.tvRecordTime.setText(String.format("%02d:%02d", Integer.valueOf(i / 60), Integer.valueOf(i % 60)));
                playActivity.ivRecordIcon.setVisibility(4);
            } else {
                playActivity.ivRecordIcon.setVisibility(0);
            }
            playActivity.logUtils.e("recTime:" + playActivity.recTime);
            playActivity.mHandler.postDelayed(playActivity.mRecRunnable, 500L);
        }
    }

    void play_high_click_down() {
        this.btnOneKeyFly.setEnabled(true);
        this.btnOneKeyLand.setEnabled(true);
        this.btnOneKeyStop.setEnabled(true);
        this.btnOneKeyFly.setAlpha(1.0f);
        this.btnOneKeyLand.setAlpha(1.0f);
        this.btnOneKeyStop.setAlpha(1.0f);
        this.mRudder.onRegisterStayHighMode();
    }

    void play_high_click_up() {
        this.btnOneKeyFly.setEnabled(false);
        this.btnOneKeyLand.setEnabled(false);
        this.btnOneKeyStop.setEnabled(false);
        this.btnOneKeyFly.setAlpha(0.5f);
        this.btnOneKeyLand.setAlpha(0.5f);
        this.btnOneKeyStop.setAlpha(0.5f);
        this.mRudder.onUnregisterStayHighMode();
    }

    void play_vr_click_down() {
        this.ivRightImage.setVisibility(0);
    }

    void play_vr_click_up() {
        this.ivRightImage.setVisibility(8);
    }

    void play_lock_click_down() {
        this.lyHeadSecond.setVisibility(0);
        this.lySliderBottom.setVisibility(0);
        this.lySliderCenter.setVisibility(0);
        this.lyMengencyStop.setVisibility(0);
        this.mRudder.setVisibility(0);
        this.img_voice_control.setEnabled(true);
        this.img_voice_control.setAlpha(1.0f);
        this.bStayHighClick = true;
        this.btnStayHigh.setImageResource(R.mipmap.play_high_icon_down);
        play_high_click_down();
    }

    void play_lock_click_up() {
        this.isMove = false;
        this.bStayHighClick = false;
        this.btnStayHigh.setImageResource(R.mipmap.play_high_icon);
        play_high_click_up();
        this.lyHeadSecond.setVisibility(4);
        this.lySliderBottom.setVisibility(4);
        this.lySliderCenter.setVisibility(4);
        this.lyMengencyStop.setVisibility(4);
        this.mRudder.setVisibility(4);
        this.img_voice_control.setEnabled(false);
        this.img_voice_control.setAlpha(0.5f);
        this.bSpeechStart = false;
        this.img_voice_control.setImageResource(R.mipmap.voice_nor);
        this.tvVoiceWord.setVisibility(4);
        if (this.speechRecognizerUtil.isRunning()) {
            this.speechRecognizerUtil.stop();
        }
        ObjectAnimator objectAnimator = this.objectAnimator;
        if (objectAnimator == null || !objectAnimator.isRunning()) {
            return;
        }
        this.objectAnimator.cancel();
        this.ivRoundMove.setVisibility(4);
    }

    void play_button_click_down() {
        this.btnSpeed.setVisibility(0);
        this.imusic.setVisibility(0);
        this.img_filter_play.setVisibility(0);
        this.btnChangeOrientation.setVisibility(0);
    }

    void play_button_click_up() {
        this.btnSpeed.setVisibility(4);
        this.imusic.setVisibility(4);
        this.img_filter_play.setVisibility(4);
        this.btnChangeOrientation.setVisibility(4);
    }

    void play_filter_click_down() {
        this.zoomView.setVisibility(0);
    }

    void play_filter_click_up() {
        this.zoomView.setVisibility(4);
        this.zoomView.dealWithrest();
    }

    void play_path_click_down() {
        this.mRudder.onPathFollowRegister();
    }

    void play_path_click_up() {
        ObjectAnimator objectAnimator = this.objectAnimator;
        if (objectAnimator != null) {
            objectAnimator.cancel();
        }
        this.mRudder.onPathFollowUnregister();
        this.ivRoundMove.setVisibility(4);
    }

    void play_auto_photo_down() {
        this.tvAutoPhoto.setVisibility(0);
    }

    void play_auto_photo_up() {
        this.tvAutoPhoto.setVisibility(4);
    }

    void play_speed_click_down() {
        if (getApp().nSpeed == 0) {
            getApp().nSpeed = 1;
            this.btnSpeed.setImageResource(R.mipmap.play_speed_2_icon);
            Constants.MAX_DIR_H_VALUE = 60;
            Constants.MAX_DIR_O_VALUE = 60;
            Constants.CUR_OFFSET_VALUE = (byte) 10;
        } else if (getApp().nSpeed == 1) {
            getApp().nSpeed = 2;
            Constants.MAX_DIR_H_VALUE = 128;
            Constants.MAX_DIR_O_VALUE = 128;
            Constants.CUR_OFFSET_VALUE = Ascii.SI;
            this.btnSpeed.setImageResource(R.mipmap.play_speed_3_icon);
        } else if (getApp().nSpeed == 2) {
            getApp().nSpeed = 0;
            Constants.MAX_DIR_H_VALUE = 40;
            Constants.MAX_DIR_O_VALUE = 40;
            Constants.CUR_OFFSET_VALUE = (byte) 6;
            this.btnSpeed.setImageResource(R.mipmap.play_speed_1_icon);
        }
        this.mRudder.onNotifySpeed();
    }

    private void play_sensor_click_down() {
        this.mRudder.sensorRegister();
        WiFiPresenter.getInstance(this).onRegisterSensor(this);
    }

    private void play_sensor_click_up() {
        this.mRudder.sensorUnregister();
        WiFiPresenter.getInstance(this).onUnregisterSensor();
    }

    public void play_rotate_click_down() {
        this.mRudder.onRegisterRotate();
        this.btnRotate.setImageResource(R.mipmap.play_rotate_icon_down);
        this.bPlayRotate = true;
    }

    public void play_rotate_click_up() {
        this.mRudder.onUnregisterRotate();
        this.bPlayRotate = false;
        this.bRotateActive = false;
        this.btnRotate.setImageResource(R.mipmap.play_rotate_icon);
        WiFiPresenter.getInstance(this).ICmd_SetRotate(false);
    }

    // TODO: Kongzue DialogX disabled - choose_mp3_music stubbed out
    private void choose_mp3_music() {
        // TODO: PocketSphinx voice control disabled
    }

    @Override // androidx.fragment.app.FragmentActivity, androidx.activity.ComponentActivity, android.app.Activity
    protected void onActivityResult(int i, int i2, Intent intent) {
        Cursor query;
        super.onActivityResult(i, i2, intent);
        if (intent != null && i2 == -1) {
            Uri data = intent.getData();
            String chooseFileResultPath = FileChooseUtil.getInstance(this).getChooseFileResultPath(data);
            Log.d("PlayActivity", "picked audio path:" + chooseFileResultPath);
            try {
                String str = null;
                if ("content".equalsIgnoreCase(data.getScheme()) && (query = getContentResolver().query(data, null, null, null, null)) != null) {
                    int columnIndex = query.getColumnIndex("_display_name");
                    if (columnIndex >= 0 && query.moveToFirst()) {
                        str = query.getString(columnIndex);
                    }
                    query.close();
                }
                if (str == null && chooseFileResultPath != null) {
                    int lastIndexOf = chooseFileResultPath.lastIndexOf(47);
                    str = lastIndexOf >= 0 ? chooseFileResultPath.substring(lastIndexOf + 1) : chooseFileResultPath;
                }
                if (str == null || str.trim().isEmpty()) {
                    str = "picked_audio.m4a";
                }
                File file = new File(getFilesDir(), str);
                InputStream openInputStream = getContentResolver().openInputStream(data);
                if (openInputStream != null) {
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    byte[] bArr = new byte[8192];
                    while (true) {
                        int read = openInputStream.read(bArr);
                        if (read == -1) {
                            break;
                        } else {
                            fileOutputStream.write(bArr, 0, read);
                        }
                    }
                    openInputStream.close();
                    fileOutputStream.close();
                    audio_str = file.getAbsolutePath();
                    Log.d("PlayActivity", "copied audio to:" + audio_str);
                } else {
                    audio_str = chooseFileResultPath;
                    Log.w("PlayActivity", "fallback to picked path:" + audio_str);
                }
            } catch (Exception e) {
                Log.e("PlayActivity", "copy picked audio failed:" + e);
                audio_str = chooseFileResultPath;
            }
            Toast.makeText(this, "选择的音乐路径是：" + audio_str, 1).show();
        }
    }

    public void play_record_click_down() {
        this.bVideoRecord = true;
        this.tvRecordTime.setText("00:00");
        this.ivRecordIcon.setVisibility(4);
        this.lyRecordTime.setVisibility(0);
        this.btnRecord.setImageResource(R.mipmap.play_record_icon_down);
        Log.d("PlayActivity", "record start audio_str:" + audio_str);
        File file = audio_str != null ? new File(audio_str) : null;
        StringBuilder sb = new StringBuilder("audio file exists:");
        sb.append(file != null && file.exists());
        sb.append(" len:");
        sb.append(file != null ? file.length() : -1L);
        Log.d("PlayActivity", sb.toString());
        if (getApp().bRotate == 1) {
            WiFiPresenter.getInstance(this).prepareRecord(true);
        } else {
            WiFiPresenter.getInstance(this).prepareRecord(false);
        }
        this.mHandler.postDelayed(this.mRecRunnable, 500L);
    }

    public void play_record_click_up() {
        this.bVideoRecord = false;
        this.lyRecordTime.setVisibility(4);
        this.tvRecordTime.setText("00:00");
        this.recTime = 0;
        this.ivRecordIcon.setVisibility(4);
        this.btnRecord.setImageResource(R.mipmap.play_record_icon);
        WiFiPresenter.getInstance(this).stopRecord();
        this.mHandler.removeCallbacks(this.mRecRunnable);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleMessageInternal(Message message) {
        int i = message.what;
        if (i == 1) {
            this.speechRecognizerUtil.switchSearch();
            return;
        }
        if (i == 5) {
            WiFiPresenter.getInstance(this).onAutoPhotoClick(false, getApp().bRotate * 1);
            this.nAutoPhoto = 3;
            this.ivLoading.setVisibility(0);
            this.mHandler.postDelayed(this.faceDectorRunnable, 1000L);
            this.logUtils.e("Constance recive a message!");
            return;
        }
        if (i == 6) {
            play_rotate_click_up();
            return;
        }
        if (i != 7) {
            return;
        }
        int i2 = message.arg1;
        this.nAutoPhoto = 3;
        this.ivLoading.setVisibility(0);
        if (i2 == 0) {
            this.mHandler.postDelayed(this.handRecordDectorRunnable, 1000L);
        } else if (i2 == 1) {
            this.mHandler.postDelayed(this.handSnapDectorRunnable, 1000L);
        }
    }

    private static class FaceDectorRunnable implements Runnable {
        private final WeakReference<PlayActivity> activityRef;

        FaceDectorRunnable(PlayActivity playActivity) {
            this.activityRef = new WeakReference<>(playActivity);
        }

        @Override // java.lang.Runnable
        public void run() {
            PlayActivity playActivity = this.activityRef.get();
            if (playActivity == null) {
                return;
            }
            if (playActivity.nAutoPhoto == 3) {
                playActivity.ivLoading.setImageResource(R.mipmap.loading2);
                playActivity.mHandler.postDelayed(playActivity.faceDectorRunnable, 1000L);
                playActivity.logUtils.e("Constance recive a message!");
            } else if (playActivity.nAutoPhoto == 2) {
                playActivity.ivLoading.setImageResource(R.mipmap.loading1);
                playActivity.mHandler.postDelayed(playActivity.faceDectorRunnable, 1000L);
                playActivity.logUtils.e("Constance recive a message!");
            } else if (playActivity.nAutoPhoto == 1) {
                playActivity.ivLoading.setVisibility(4);
                playActivity.ivLoading.setImageResource(R.mipmap.loading3);
                playActivity.btnPhotoSnap.performClick();
                WiFiPresenter.getInstance(playActivity).onAutoPhotoClick(playActivity.bAutoPhoto, playActivity.getApp().bRotate * 1);
            }
            PlayActivity.access$610(playActivity);
        }
    }

    private static class HandSnapDectorRunnable implements Runnable {
        private final WeakReference<PlayActivity> activityRef;

        HandSnapDectorRunnable(PlayActivity playActivity) {
            this.activityRef = new WeakReference<>(playActivity);
        }

        @Override // java.lang.Runnable
        public void run() {
            PlayActivity playActivity = this.activityRef.get();
            if (playActivity == null) {
                return;
            }
            if (playActivity.nAutoPhoto == 3) {
                playActivity.ivLoading.setImageResource(R.mipmap.loading2);
                playActivity.mHandler.postDelayed(playActivity.handSnapDectorRunnable, 1000L);
            } else if (playActivity.nAutoPhoto == 2) {
                playActivity.ivLoading.setImageResource(R.mipmap.loading1);
                playActivity.mHandler.postDelayed(playActivity.handSnapDectorRunnable, 1000L);
            } else if (playActivity.nAutoPhoto == 1) {
                playActivity.ivLoading.setVisibility(4);
                playActivity.ivLoading.setImageResource(R.mipmap.loading3);
                playActivity.btnPhotoSnap.performClick();
                long unused = PlayActivity.nStartFrameTime = System.currentTimeMillis();
                playActivity.isWait = false;
            }
            PlayActivity.access$610(playActivity);
        }
    }

    private static class HandRecordDectorRunnable implements Runnable {
        private final WeakReference<PlayActivity> activityRef;

        HandRecordDectorRunnable(PlayActivity playActivity) {
            this.activityRef = new WeakReference<>(playActivity);
        }

        @Override // java.lang.Runnable
        public void run() {
            PlayActivity playActivity = this.activityRef.get();
            if (playActivity == null) {
                return;
            }
            if (playActivity.nAutoPhoto == 3) {
                playActivity.ivLoading.setImageResource(R.mipmap.loading2);
                playActivity.mHandler.postDelayed(playActivity.handRecordDectorRunnable, 1000L);
            } else if (playActivity.nAutoPhoto == 2) {
                playActivity.ivLoading.setImageResource(R.mipmap.loading1);
                playActivity.mHandler.postDelayed(playActivity.handRecordDectorRunnable, 1000L);
            } else if (playActivity.nAutoPhoto == 1) {
                playActivity.ivLoading.setVisibility(4);
                playActivity.ivLoading.setImageResource(R.mipmap.loading3);
                playActivity.btnRecord.performClick();
                long unused = PlayActivity.nStartFrameTime = System.currentTimeMillis();
                playActivity.isWait = false;
            }
            PlayActivity.access$610(playActivity);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void play_start_follow(View view, String str, Collection<PathPoint> collection, long j) {
        ObjectAnimator ofObject = ObjectAnimator.ofObject(this, str, new PathEvaluator(), collection.toArray());
        this.objectAnimator = ofObject;
        ofObject.setInterpolator(new LinearInterpolator());
        this.objectAnimator.setDuration(j);
        this.objectAnimator.start();
    }

    @Override // com.tzh.wifi.wificam.view.slider.listener.ISliderListener
    public void onSliderNotify(View view, int i) {
        if ((view.getId() == R.id.playLeftSlider || view.getId() == R.id.playRightSlider || view.getId() == R.id.playCenterSlider) && this.bSpeechStart) {
            this.bSpeechStart = false;
            this.img_voice_control.setImageResource(R.mipmap.voice_nor);
            this.tvVoiceWord.setVisibility(4);
            if (this.speechRecognizerUtil.isRunning()) {
                this.speechRecognizerUtil.stop();
            }
        }
        int sliderId = view.getId();
        if (sliderId == R.id.playCenterSlider) {
            this.logUtils.e("center slider:" + i);
            WiFiPresenter.getInstance(this).ICmd_SetTune((byte) -1, (byte) -1, (byte) i);
            ConfigUtils.writeCenterTune(this, i);
        } else if (sliderId == R.id.playLeftSlider) {
            this.logUtils.e("left slider:" + i);
            WiFiPresenter.getInstance(this).ICmd_SetTune((byte) i, (byte) -1, (byte) -1);
            ConfigUtils.writeLeftTune(this, i);
        } else if (sliderId == R.id.playRightSlider) {
            this.logUtils.e("right slider:" + i);
            WiFiPresenter.getInstance(this).ICmd_SetTune((byte) -1, (byte) i, (byte) -1);
            ConfigUtils.writeRightTune(this, i);
        }
        this.mRudder.invalidateValue();
    }

    @Override // com.tzh.wifi.wificam.presenter.listener.ISensorListener
    public void onGSensorChange(int i, int i2) {
        this.mRudder.onSensorNotify(i, i2);
    }

    @Override // com.tzh.wifi.wificam.view.base.ICaptureView
    public void onFaceDector() {
        this.logUtils.e("you have dector a face");
    }

    @Override // com.tzh.wifi.wificam.view.base.ICaptureView
    public void connected() {
        this.bWiFiConnect = true;
    }

    @Override // com.tzh.wifi.wificam.view.base.ICaptureView
    public void disconnected() {
        this.bWiFiConnect = false;
        play_record_click_up();
    }

    @Override // com.tzh.wifi.wificam.view.base.ICaptureView
    public void reciveBitmap(int i, int i2, Bitmap bitmap) {
        ImageView displayImage;
        this.bWiFiConnect = true;
        // Hide WiFi status overlay when camera connects
        final View wifiStatus = findViewById(R.id.lyWifiStatus);
        if (wifiStatus != null && wifiStatus.getVisibility() != View.GONE) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    wifiStatus.setVisibility(View.GONE);
                }
            });
        }
        if (this.isBitmapProcessing) {
            return;
        }
        WiFiPresenter.getInstance(this).takeSnap(bitmap);
        WiFiPresenter.getInstance(this).videoTakeSnap(bitmap);
        if (this.mBmpUtils == null || (displayImage = this.ivLeftImage) == null || displayImage.getWidth() == 0 || this.ivLeftImage.getHeight() == 0) {
            return;
        }
        this.isBitmapProcessing = true;
        this.mBmpUtils.setImageParam(this.ivLeftImage.getWidth(), this.ivLeftImage.getHeight());
        this.mBmpUtils.push(bitmap);
        this.isBitmapProcessing = false;
    }

    @Override // com.tzh.wifi.wificam.view.base.ICaptureView
    public void snapState(int i) {
        if (i == 0) {
            this.btnPhotoSnap.performClick();
        } else {
            if (i != 1) {
                return;
            }
            this.btnRecord.performClick();
        }
    }

    @Override // com.tzh.wifi.wificam.view.base.ICaptureView
    public void cameraType(int i) {
        this.cameraType = i;
    }

    @Override // com.tzh.wifi.wificam.presenter.listener.ITargetBitmapFixListener
    public void OnBitmapFixed(final Bitmap bitmap) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        runOnUiThread(new Runnable() { // from class: com.tzh.wifi.wificam.activity.PlayActivity.2
            @Override // java.lang.Runnable
            public void run() {
                if (PlayActivity.this.ivLeftImage == null || PlayActivity.this.ivRightImage == null) {
                    return;
                }
                PlayActivity.this.ivLeftImage.setImageBitmap(bitmap);
                PlayActivity.this.ivRightImage.setImageBitmap(bitmap);
                long currentTimeMillis = System.currentTimeMillis();
                if (!PlayActivity.this.bAutoPhoto || currentTimeMillis - PlayActivity.nStartFrameTime < 100 || PlayActivity.this.isWait) {
                    return;
                }
                if (PlayActivity.this.mFaceTask != null) {
                    int i = AnonymousClass4.$SwitchMap$android$os$AsyncTask$Status[PlayActivity.this.mFaceTask.getStatus().ordinal()];
                    if (i == 1) {
                        return;
                    }
                    if (i == 2) {
                        PlayActivity.this.mFaceTask.cancel(false);
                    }
                }
                PlayActivity.this.isWait = true;
                PlayActivity.this.mFaceTask = new FaceTask(PlayActivity.this, bitmap);
                PlayActivity.this.mFaceTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, (Void) null);
            }
        });
    }

    /* renamed from: com.tzh.wifi.wificam.activity.PlayActivity$4, reason: invalid class name */
    static /* synthetic */ class AnonymousClass4 {
        static final /* synthetic */ int[] $SwitchMap$android$os$AsyncTask$Status;

        static {
            int[] iArr = new int[AsyncTask.Status.values().length];
            $SwitchMap$android$os$AsyncTask$Status = iArr;
            try {
                iArr[AsyncTask.Status.RUNNING.ordinal()] = 1;
            } catch (NoSuchFieldError unused) {
            }
            try {
                $SwitchMap$android$os$AsyncTask$Status[AsyncTask.Status.PENDING.ordinal()] = 2;
            } catch (NoSuchFieldError unused2) {
            }
        }
    }

    @Override // com.tzh.wifi.wificam.view.listener.IRudderListener
    public void onAccNotify(int i, int i2) {
        if (i2 == 102 || i2 == 153) {
            i2++;
        }
        WiFiPresenter.getInstance(this).ICmd_AccNotify((byte) i2, (byte) i);
    }

    @Override // com.tzh.wifi.wificam.view.listener.IRudderListener
    public void onDirNotify(int i, int i2, int i3) {
        // Swap left/right: rotateAction 1 (originally left) → send right, 2 (originally right) → send left
        if (i3 == 1) {
            WiFiPresenter.getInstance(this).ICmd_DirNotify((byte) 0, (byte) i2);
            if (this.bRotateActive) {
                return;
            }
            this.bRotateActive = true;
            WiFiPresenter.getInstance(this).ICmd_SetRotate(true);
            this.mHandler.sendEmptyMessageDelayed(6, 500L);
            return;
        }
        if (i3 == 2) {
            WiFiPresenter.getInstance(this).ICmd_DirNotify((byte) -1, (byte) i2);
            if (this.bRotateActive) {
                return;
            }
            this.bRotateActive = true;
            WiFiPresenter.getInstance(this).ICmd_SetRotate(true);
            this.mHandler.sendEmptyMessageDelayed(6, 500L);
            return;
        }
        if (i3 == 3) {
            WiFiPresenter.getInstance(this).ICmd_DirNotify((byte) i, (byte) 0);
            if (this.bRotateActive) {
                return;
            }
            this.bRotateActive = true;
            WiFiPresenter.getInstance(this).ICmd_SetRotate(true);
            this.mHandler.sendEmptyMessageDelayed(6, 500L);
            return;
        }
        if (i3 == 4) {
            WiFiPresenter.getInstance(this).ICmd_DirNotify((byte) i, (byte) -1);
            if (this.bRotateActive) {
                return;
            }
            this.bRotateActive = true;
            WiFiPresenter.getInstance(this).ICmd_SetRotate(true);
            this.mHandler.sendEmptyMessageDelayed(6, 500L);
            return;
        }
        WiFiPresenter.getInstance(this).ICmd_DirNotify((byte) i, (byte) i2);
    }

    private static class ResetRunnable implements Runnable {
        private final WeakReference<PlayActivity> activityRef;

        ResetRunnable(PlayActivity playActivity) {
            this.activityRef = new WeakReference<>(playActivity);
        }

        @Override // java.lang.Runnable
        public void run() {
            PlayActivity playActivity = this.activityRef.get();
            if (playActivity == null) {
                return;
            }
            playActivity.isMove = false;
            playActivity.mRudder.dealWithAccRudder(playActivity.mRudder.getAccDefaultPosition().x, playActivity.mRudder.getAccCenterY());
            playActivity.mRudder.dealWidhDirRudder(playActivity.mRudder.getDirDefaultPosition().x, playActivity.mRudder.getDirDefaultPosition().y, false);
        }
    }

    /* JADX WARN: Type inference failed for: r0v1, types: [com.tzh.wifi.wificam.activity.PlayActivity$3] */
    private void speechRecognizerStart() {
        this.logUtils.e("speechRecognizerStart");
        new Thread() { // from class: com.tzh.wifi.wificam.activity.PlayActivity.3
            @Override // java.lang.Thread, java.lang.Runnable
            public void run() {
                super.run();
                try {
                    PlayActivity.this.speechRecognizerUtil.setupRecognizer(false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                PlayActivity.this.mHandler.sendEmptyMessage(1);
            }
        }.start();
    }

    // TODO: PocketSphinx voice control disabled
    public void onBeginningOfSpeech() {
        // TODO: PocketSphinx voice control disabled
    }

    // TODO: PocketSphinx voice control disabled
    public void onEndOfSpeech() {
        // TODO: PocketSphinx voice control disabled
    }

    // TODO: PocketSphinx voice control disabled
    public void onPartialResult(String hypothesis) {
        // TODO: PocketSphinx voice control disabled
    }

    // TODO: PocketSphinx voice control disabled
    public void onResult(String hypothesis) {
        // TODO: PocketSphinx voice control disabled
    }

    // TODO: PocketSphinx voice control disabled
    public void onError(Exception exc) {
        // TODO: PocketSphinx voice control disabled
    }

    // TODO: PocketSphinx voice control disabled
    public void onTimeout() {
        // TODO: PocketSphinx voice control disabled
    }

    public void setFab(PathPoint pathPoint) {
        this.ivRoundMove.setTranslationX(pathPoint.pointX - (this.ivRoundMove.getWidth() / 2));
        this.ivRoundMove.setTranslationY(pathPoint.pointY - (this.ivRoundMove.getWidth() / 2));
        this.mRudder.dealWidhDirRudder(pathPoint.pointX, pathPoint.pointY, false);
        PathPoint endPathPoint = this.mRudder.getEndPathPoint();
        if (endPathPoint.pointX == pathPoint.pointX && endPathPoint.pointY == pathPoint.pointY) {
            this.mHandler.postDelayed(this.pathResetRunnable, 300L);
        }
    }

    private static class PathResetRunnable implements Runnable {
        private final WeakReference<PlayActivity> activityRef;

        PathResetRunnable(PlayActivity playActivity) {
            this.activityRef = new WeakReference<>(playActivity);
        }

        @Override // java.lang.Runnable
        public void run() {
            PlayActivity playActivity = this.activityRef.get();
            if (playActivity == null) {
                return;
            }
            playActivity.mRudder.dealWidhDirRudder(playActivity.mRudder.getDirDefaultPosition().x, playActivity.mRudder.getDirDefaultPosition().y, false);
        }
    }

    private static class PathRunnable implements Runnable {
        private final WeakReference<PlayActivity> activityRef;

        PathRunnable(PlayActivity playActivity) {
            this.activityRef = new WeakReference<>(playActivity);
        }

        @Override // java.lang.Runnable
        public void run() {
            PlayActivity playActivity = this.activityRef.get();
            if (playActivity == null) {
                return;
            }
            playActivity.ivRoundMove.setVisibility(4);
        }
    }

    private static class PathStartRunnable implements Runnable {
        private final WeakReference<PlayActivity> activityRef;

        PathStartRunnable(PlayActivity playActivity) {
            this.activityRef = new WeakReference<>(playActivity);
        }

        @Override // java.lang.Runnable
        public void run() {
            PlayActivity playActivity = this.activityRef.get();
            if (playActivity == null) {
                return;
            }
            playActivity.mHandler.removeCallbacks(playActivity.pathRunnable);
            if (playActivity.mRudder.getPathPoint().size() > 1) {
                playActivity.ivRoundMove.setVisibility(0);
                long pathLen = ((long) playActivity.mRudder.getPathLen()) / 8;
                playActivity.logUtils.e("animation time " + pathLen);
                playActivity.play_start_follow(playActivity.ivRoundMove, "fab", playActivity.mRudder.getPathPoint(), pathLen);
                playActivity.mHandler.postDelayed(playActivity.pathRunnable, pathLen);
            }
        }
    }

    @Override // com.tzh.wifi.wificam.view.listener.IRudderListener
    public void onPathFollowNotify(boolean z, Collection<PathPoint> collection) {
        if (z) {
            ObjectAnimator objectAnimator = this.objectAnimator;
            if (objectAnimator != null && objectAnimator.isRunning()) {
                this.objectAnimator.cancel();
            }
            this.mHandler.removeCallbacks(this.pathRunnable);
            this.ivRoundMove.setVisibility(4);
            return;
        }
        this.mHandler.post(this.PathStartRunnable);
    }

    @Override // com.tzh.wifi.wificam.base.BaseActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onResume() {
        super.onResume();
        if (getApp().bLockClick) {
            WiFiPresenter.getInstance(this).ICmd_Start();
        } else {
            WiFiPresenter.getInstance(this).ICmd_Resume();
        }
        WiFiPresenter.getInstance(this).attachView(this);
        this.bWiFiConnect = false;
        BmpUtils bmpUtils = this.mBmpUtils;
        if (bmpUtils != null) {
            bmpUtils.start();
        }
        Rudder rudder = this.mRudder;
        rudder.dealWidhDirRudder(rudder.getDirDefaultPosition().x, this.mRudder.getDirDefaultPosition().y, false);
        if (getResources().getConfiguration().orientation == 1) {
            this.is_portrait = true;
        } else if (getResources().getConfiguration().orientation == 2) {
            this.is_portrait = false;
        }
        this.mBmpUtils.setIs_portrait(this.is_portrait);
    }

    @Override // com.tzh.wifi.wificam.base.BaseActivity, androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    public void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
        this.cameraType = 0;
        ObjectAnimator objectAnimator = this.objectAnimator;
        if (objectAnimator != null && objectAnimator.isRunning()) {
            this.objectAnimator.cancel();
            ImageView imageView = this.ivRoundMove;
            if (imageView != null) {
                imageView.setVisibility(4);
            }
        }
        if (this.bVideoRecord) {
            play_record_click_up();
        }
        SafeHandler safeHandler = this.mHandler;
        if (safeHandler != null) {
            safeHandler.removeCallbacks(this.ScaleRunnable);
            this.mHandler.removeCallbacks(this.handSnapDectorRunnable);
            this.mHandler.removeCallbacks(this.handRecordDectorRunnable);
            this.mHandler.removeCallbacks(this.mCloseVoiceControl);
            this.mHandler.removeCallbacks(this.pathResetRunnable);
            this.mHandler.removeCallbacks(this.mRecRunnable);
            this.mHandler.removeCallbacks(this.faceDectorRunnable);
            this.mHandler.removeCallbacks(this.resetRunnable);
            this.mHandler.removeCallbacks(this.pathRunnable);
            this.mHandler.removeCallbacks(this.PathStartRunnable);
        }
        ImageView imageView2 = this.ivLoading;
        if (imageView2 != null) {
            imageView2.setVisibility(4);
            this.ivLoading.setImageResource(R.mipmap.loading3);
        }
        this.isWait = false;
        ZoomView zoomView = this.zoomView;
        if (zoomView != null) {
            zoomView.dealWithrest();
        }
        BmpUtils bmpUtils = this.mBmpUtils;
        if (bmpUtils != null) {
            bmpUtils.setScale(0);
        }
        this.bSpeechStart = false;
        ImageView imageView3 = this.img_voice_control;
        if (imageView3 != null) {
            imageView3.setImageResource(R.mipmap.voice_nor);
        }
        TextView textView = this.tvVoiceWord;
        if (textView != null) {
            textView.setVisibility(4);
        }
        SpeechRecognizerUtil speechRecognizerUtil = this.speechRecognizerUtil;
        if (speechRecognizerUtil != null && speechRecognizerUtil.isRunning()) {
            this.speechRecognizerUtil.stop();
        }
        FaceTask faceTask = this.mFaceTask;
        if (faceTask == null || faceTask.getStatus() != AsyncTask.Status.RUNNING) {
            return;
        }
        this.mFaceTask.cancel(true);
    }

    @Override // com.tzh.wifi.wificam.base.BaseActivity, androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    protected void onDestroy() {
        super.onDestroy();
        writeConfig();
        SafeHandler safeHandler = this.mHandler;
        if (safeHandler != null) {
            safeHandler.removeCallbacksAndMessages(null);
        }
        Rudder rudder = this.mRudder;
        if (rudder != null) {
            rudder.onDestroy();
        }
        play_auto_photo_up();
        SpeechRecognizerUtil speechRecognizerUtil = this.speechRecognizerUtil;
        if (speechRecognizerUtil != null && speechRecognizerUtil.isRunning()) {
            this.speechRecognizerUtil.stop();
        }
        FaceTask faceTask = this.mFaceTask;
        if (faceTask != null) {
            faceTask.cancel(true);
            this.mFaceTask = null;
        }
        ObjectAnimator objectAnimator = this.objectAnimator;
        if (objectAnimator != null && objectAnimator.isRunning()) {
            this.objectAnimator.cancel();
            this.objectAnimator = null;
        }
        WiFiPresenter.getInstance(this).disattachView();
        if (getApp().bLockClick) {
            WiFiPresenter.getInstance(this).ICmd_Stop();
        }
        BmpUtils bmpUtils = this.mBmpUtils;
        if (bmpUtils != null) {
            bmpUtils.stop();
            this.mBmpUtils = null;
        }
        Vibrator vibrator = this.vibrator;
        if (vibrator != null) {
            vibrator.cancel();
            this.vibrator = null;
        }
        this.ivLeftImage = null;
        this.ivRightImage = null;
    }

    @Override // androidx.activity.ComponentActivity, android.app.Activity
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }

    @Override // androidx.appcompat.app.AppCompatActivity, androidx.fragment.app.FragmentActivity, android.app.Activity
    public void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onGetMessage(MessageWrap messageWrap) {
        FileInputStream fileInputStream = null;
        if (messageWrap.message.equals("music_on")) {
            this.btnMp3Switch.setVisibility(0);
            Log.d("PlayActivity", "music_on received audio_str:" + audio_str);
            try {
                if (audio_str == null) {
                    return;
                }
                File file = new File(audio_str);
                if (audio_str.startsWith(getFilesDir().getAbsolutePath())) {
                    return;
                }
                String name = file.getName();
                if (name == null || name.isEmpty()) {
                    name = "picked_audio.m4a";
                }
                File file2 = new File(getFilesDir(), name);
                try {
                    fileInputStream = new FileInputStream(file);
                } catch (Exception unused) {
                }
                if (fileInputStream == null) {
                    return;
                }
                FileOutputStream fileOutputStream = new FileOutputStream(file2);
                byte[] bArr = new byte[8192];
                while (true) {
                    int read = fileInputStream.read(bArr);
                    if (read != -1) {
                        fileOutputStream.write(bArr, 0, read);
                    } else {
                        fileInputStream.close();
                        fileOutputStream.close();
                        audio_str = file2.getAbsolutePath();
                        Log.d("PlayActivity", "normalized external audio to:" + audio_str);
                        return;
                    }
                }
            } catch (Exception e) {
                Log.e("PlayActivity", "normalize audio failed:" + e);
            }
        } else if (messageWrap.message.equals("music_off")) {
            audio_str = null;
            this.btnMp3Switch.setVisibility(4);
            Log.d("PlayActivity", "music_off received, audio_str cleared");
        }
    }

    @Override // com.tzh.wifi.wificam.view.listener.IScaleListener
    public void OnZoomStart() {
        this.tvScaleValue.setVisibility(0);
    }

    @Override // com.tzh.wifi.wificam.view.listener.IScaleListener
    public void OnZoomSet(int i) {
        this.tvScaleValue.setText(String.format("%d X", Integer.valueOf(i + 1)));
        BmpUtils bmpUtils = this.mBmpUtils;
        if (bmpUtils != null) {
            bmpUtils.setScale(i);
        }
    }

    @Override // com.tzh.wifi.wificam.view.listener.IScaleListener
    public void OnZoomEnd() {
        this.mHandler.postDelayed(this.ScaleRunnable, 1000L);
    }

    private static class ScaleRunnable implements Runnable {
        private final WeakReference<PlayActivity> activityRef;

        ScaleRunnable(PlayActivity playActivity) {
            this.activityRef = new WeakReference<>(playActivity);
        }

        @Override // java.lang.Runnable
        public void run() {
            PlayActivity playActivity = this.activityRef.get();
            if (playActivity == null) {
                return;
            }
            playActivity.tvScaleValue.setVisibility(4);
        }
    }

    public void setHandFist() {
        Log.e("PlayActivity", "手势：拳！");
        if (this.bVideoRecord) {
            Toast.makeText(this, StringUtils.getInstance(this).strRecordEnd, 0).show();
        } else {
            Toast.makeText(this, StringUtils.getInstance(this).strRecordStart, 0).show();
        }
        Message obtain = Message.obtain();
        obtain.what = 7;
        obtain.arg1 = 0;
        this.mHandler.sendMessage(obtain);
    }

    public void setHandPalm() {
        if (this.bVideoRecord) {
            this.isWait = false;
            nStartFrameTime = System.currentTimeMillis();
            return;
        }
        Log.e("PlayActivity", "手势：掌！");
        Toast.makeText(this, StringUtils.getInstance(this).strSnap, 0).show();
        Message obtain = Message.obtain();
        obtain.what = 7;
        obtain.arg1 = 1;
        this.mHandler.sendMessage(obtain);
    }

    public void resetHandRect() {
        if (this.bVideoRecord) {
            this.isWait = false;
            nStartFrameTime = System.currentTimeMillis();
        } else {
            Log.e("PlayActivity", "手势：无！");
            this.isWait = false;
            nStartFrameTime = System.currentTimeMillis();
        }
    }

    public void setWaitFalse() {
        this.isWait = false;
    }

    private static class CloseVoiceControlRunnable implements Runnable {
        private final WeakReference<PlayActivity> activityRef;

        CloseVoiceControlRunnable(PlayActivity playActivity) {
            this.activityRef = new WeakReference<>(playActivity);
        }

        @Override // java.lang.Runnable
        public void run() {
            PlayActivity playActivity = this.activityRef.get();
            if (playActivity == null) {
                return;
            }
            if (playActivity.speechRecognizerUtil.isRunning()) {
                playActivity.speechRecognizerUtil.stop();
            }
            playActivity.mHandler.removeCallbacks(playActivity.mCloseVoiceControl);
        }
    }

    @Override // com.tzh.wifi.wificam.view.listener.IRudderListener
    public void closeVoiceControl() {
        if (this.bSpeechStart) {
            this.bSpeechStart = false;
            this.img_voice_control.setImageResource(R.mipmap.voice_nor);
            this.tvVoiceWord.setVisibility(4);
            this.mHandler.postDelayed(this.mCloseVoiceControl, 500L);
        }
    }
}
