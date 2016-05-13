package jp.ac.titech.itpro.sdl.accelgraph;

import android.app.Activity;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements SensorEventListener, View.OnClickListener {

    private final static String TAG = "MainActivity";

    private TextView rateView, accuracyView, modeView;
    private GraphView xView, yView, zView;

    private SensorManager sensorMgr;
    private Sensor accelerometer;

    private final static long GRAPH_REFRESH_WAIT_MS = 20;

    private GraphRefreshThread th = null;
    private Handler handler;

    private float vx, vy, vz;
    private float rate;
    private int accuracy;
    private long prevts;

    private final int Average_num = 5; // 移動平均に使われる数
    private float[] ax = new float[Average_num]; // 移動平均を格納する配列
    private float[] ay = new float[Average_num];
    private float[] az = new float[Average_num];
    private float vx_a, vy_a, vz_a; // 移動平均をした後の数値
    private int idx_average = 0; // 移動平均を格納するための番号

    private final float Alpha_gravity = 0.8f;
    private float vx_g, vy_g, vz_g;

    Button changemode_button;
    private int mode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.activity_main);

        rateView = (TextView) findViewById(R.id.rate_view);
        accuracyView = (TextView) findViewById(R.id.accuracy_view);
        modeView = (TextView) findViewById(R.id.mode_view);
        xView = (GraphView) findViewById(R.id.x_view);
        yView = (GraphView) findViewById(R.id.y_view);
        zView = (GraphView) findViewById(R.id.z_view);

        sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer == null) {
            Toast.makeText(this, getString(R.string.toast_no_accel_error),
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        changemode_button = (Button)findViewById(R.id.button);
        changemode_button.setOnClickListener(this);

        handler = new Handler();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume");
        sensorMgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_FASTEST);
        th = new GraphRefreshThread();
        th.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause");
        th = null;
        sensorMgr.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        // 通常の値
        vx = event.values[0];
        vy = event.values[1];
        vz = event.values[2];
        rate = ((float) (event.timestamp - prevts)) / (1000 * 1000);
        prevts = event.timestamp;
        // 移動平均
        ax[idx_average]=vx;ay[idx_average]=vy;az[idx_average]=vz;
        float sx=0,sy=0,sz=0;
        for(int i=0;i<Average_num;i++){
            sx+=ax[i];sy+=ay[i];sz+=az[i];
        }
        vx_a=sx/Average_num;vy_a=sy/Average_num;vz_a=sz/Average_num;
        idx_average = (idx_average+1) % Average_num;

        // 重みつき平均
        vx_g = Alpha_gravity * vx_g + (1-Alpha_gravity) * vx;
        vy_g = Alpha_gravity * vy_g + (1-Alpha_gravity) * vy;
        vz_g = Alpha_gravity * vz_g + (1-Alpha_gravity) * vz;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.i(TAG, "onAccuracyChanged: ");
        this.accuracy = accuracy;
    }

    @Override
    public void onClick(View v) {
        mode = (mode + 1) % 3;
        int color = (mode == 2) ? Color.MAGENTA : ((mode == 1) ? Color.GREEN : Color.YELLOW);
        xView.changeGraphColor(color);yView.changeGraphColor(color);zView.changeGraphColor(color);
        Log.d(TAG, "MODE CHANGED->"+mode);
        Toast.makeText(this, "Mode Changed!", Toast.LENGTH_SHORT).show();
        modeView.setText((mode == 2) ? R.string.mode_gravity : ((mode == 1) ? R.string.mode_average : R.string.mode_normal));
    }

    private class GraphRefreshThread extends Thread {
        public void run() {
            try {
                while (th != null) {
                    handler.post(new Runnable() {
                        public void run() {
                            rateView.setText(Float.toString(rate));
                            accuracyView.setText(Integer.toString(accuracy));
                            if(mode == 0) {
                                xView.addData(vx, true);
                                yView.addData(vy, true);
                                zView.addData(vz, true);
                            }else if(mode == 1){
                                xView.addData(vx_a, true);
                                yView.addData(vy_a, true);
                                zView.addData(vz_a, true);
                            }else if(mode == 2){
                                xView.addData(vx_g, true);
                                yView.addData(vy_g, true);
                                zView.addData(vz_g, true);
                            }
                        }
                    });
                    Thread.sleep(GRAPH_REFRESH_WAIT_MS);
                }
            }
            catch (InterruptedException e) {
                Log.e(TAG, e.toString());
                th = null;
            }
        }
    }
}
