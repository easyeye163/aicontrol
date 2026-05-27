package com.aicontrol.android.aircam.presenter.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import com.aicontrol.android.aircam.presenter.listener.ISensorListener;
import com.aicontrol.android.aircam.utils.Constants;
import com.aicontrol.android.aircam.utils.LogUtils;

/* loaded from: classes5.dex */
public class GSensor implements SensorEventListener {
    LogUtils logEx;
    private Sensor mSensor;
    private ISensorListener sensorChange;
    private SensorManager sensorManager;

    @Override // android.hardware.SensorEventListener
    public void onAccuracyChanged(Sensor sensor, int i) {
    }

    public GSensor(Context context) {
        this.sensorManager = null;
        this.mSensor = null;
        this.sensorChange = null;
        this.logEx = LogUtils.setLogger(GSensor.class);
        SensorManager sensorManager = (SensorManager) context.getSystemService("sensor");
        this.sensorManager = sensorManager;
        Sensor defaultSensor = sensorManager.getDefaultSensor(1);
        this.mSensor = defaultSensor;
        if (defaultSensor == null) {
            this.logEx.e("gravity not support");
        }
    }

    public GSensor(Context context, ISensorListener iSensorListener) {
        this.sensorManager = null;
        this.mSensor = null;
        this.sensorChange = null;
        this.logEx = LogUtils.setLogger(GSensor.class);
        this.sensorChange = iSensorListener;
        SensorManager sensorManager = (SensorManager) context.getSystemService("sensor");
        this.sensorManager = sensorManager;
        Sensor defaultSensor = sensorManager.getDefaultSensor(1);
        this.mSensor = defaultSensor;
        if (defaultSensor == null) {
            this.logEx.e("gravity not support");
        }
    }

    public boolean register(ISensorListener iSensorListener) {
        this.sensorChange = iSensorListener;
        return this.sensorManager.registerListener(this, this.mSensor, 1);
    }

    public void unregister() {
        this.sensorChange = null;
        this.sensorManager.unregisterListener(this, this.mSensor);
    }

    @Override // android.hardware.SensorEventListener
    public void onSensorChanged(SensorEvent sensorEvent) {
        if (sensorEvent.sensor.getType() == 1) {
            int i = (int) ((sensorEvent.values[0] * Constants.MAX_DIR_H_VALUE) / 10.0f);
            int i2 = (int) ((sensorEvent.values[1] * Constants.MAX_DIR_O_VALUE) / 10.0f);
            this.logEx.e("x==" + i + "   y==" + i2);
            ISensorListener iSensorListener = this.sensorChange;
            if (iSensorListener != null) {
                iSensorListener.onGSensorChange(i2, i);
            }
        }
    }
}
