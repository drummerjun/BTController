package com.junyenhuang.btcontroller;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.junyenhuang.btcontroller.ble.BluetoothLeService;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import io.apptik.widget.MultiSlider;

public class SettingActivity1 extends AppCompatActivity {
    private static final String TAG = SettingActivity1.class.getSimpleName();
    private RelativeLayout device1, device2, device3;
    private TextView hi1, hi2, hi3;
    private TextView lo1, lo2, lo3;
    private MultiSlider slider1, slider2, slider3;

    //----------- use for BLE ----------------------
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private String mDeviceAddress="";
    private int numOfDevices = 1;
    private BTApp btApp;
    private int oldLo1, oldLo2, oldLo3;
    private int oldHi1, oldHi2, oldHi3;
    private Target toolbarTarget, deviceBgTarget;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home){
            onBackPressed();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);
        initViews();

        btApp = (BTApp)getApplication();

        //mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        mDeviceAddress = getSharedPreferences("DEVICES", MODE_PRIVATE).getString("MAC", mDeviceAddress);
        numOfDevices = getIntent().getIntExtra("DEVICE_NUM", 1);
        //mBluetoothAdapter = ((BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction("intent.bt.limits_set");
        registerReceiver(mGattUpdateReceiver, intentFilter);

        getLimits();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(mGattUpdateReceiver);
        } catch(IllegalArgumentException ex) {
            ex.printStackTrace();
        }
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.humi_title);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        device1 = (RelativeLayout)findViewById(R.id.device_1);
        device2 = (RelativeLayout)findViewById(R.id.device_2);
        device3 = (RelativeLayout)findViewById(R.id.device_3);
        device1.setVisibility(View.INVISIBLE);
        device2.setVisibility(View.INVISIBLE);
        device3.setVisibility(View.INVISIBLE);

        TextView tvSensor1 = (TextView)findViewById(R.id.device_name_1);
        TextView tvSensor2 = (TextView)findViewById(R.id.device_name_2);
        TextView tvSensor3 = (TextView)findViewById(R.id.device_name_3);
        String name = getString(R.string.device_) + " 1";
        tvSensor1.setText(name);
        name = getString(R.string.device_) + " 2";
        tvSensor2.setText(name);
        name = getString(R.string.device_) + " 3";
        tvSensor3.setText(name);

        slider1 = (MultiSlider)findViewById(R.id.range1);
        slider2 = (MultiSlider)findViewById(R.id.range2);
        slider3 = (MultiSlider)findViewById(R.id.range3);
        hi1 = (TextView)findViewById(R.id.hi1);
        hi2 = (TextView)findViewById(R.id.hi2);
        hi3 = (TextView)findViewById(R.id.hi3);
        lo1 = (TextView)findViewById(R.id.lo1);
        lo2 = (TextView)findViewById(R.id.lo2);
        lo3 = (TextView)findViewById(R.id.lo3);

        slider1.setOnThumbValueChangeListener(new MultiSlider.OnThumbValueChangeListener() {
            @Override
            public void onValueChanged(MultiSlider multiSlider, MultiSlider.Thumb thumb, int thumbIndex, int value) {
                if(thumbIndex == 0) {
                    lo1.setText(String.valueOf(value));
                } else if(thumbIndex == 1){
                    hi1.setText(String.valueOf(value));
                }
            }
        });
        slider2.setOnThumbValueChangeListener(new MultiSlider.OnThumbValueChangeListener() {
            @Override
            public void onValueChanged(MultiSlider multiSlider, MultiSlider.Thumb thumb, int thumbIndex, int value) {
                if(thumbIndex == 0) {
                    lo2.setText(String.valueOf(value));
                } else if(thumbIndex == 1){
                    hi2.setText(String.valueOf(value));
                }
            }
        });
        slider3.setOnThumbValueChangeListener(new MultiSlider.OnThumbValueChangeListener() {
            @Override
            public void onValueChanged(MultiSlider multiSlider, MultiSlider.Thumb thumb, int thumbIndex, int value) {
                if(thumbIndex == 0) {
                    lo3.setText(String.valueOf(value));
                } else if(thumbIndex == 1){
                    hi3.setText(String.valueOf(value));
                }
            }
        });

        RelativeLayout button_left = (RelativeLayout)findViewById(R.id.bottom_button_1);
        button_left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                revertLimits();
            }
        });

        RelativeLayout button_right = (RelativeLayout)findViewById(R.id.bottom_button_2);
        button_right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                uploadLimit();
            }
        });

        ImageView button_left_image = (ImageView)findViewById(R.id.image_button_1);
        ImageView button_right_image = (ImageView)findViewById(R.id.image_button_2);

        ImageView device_1_image = (ImageView)findViewById(R.id.static_icon_1);
        ImageView device_2_image = (ImageView)findViewById(R.id.static_icon_2);
        ImageView device_3_image = (ImageView)findViewById(R.id.static_icon_3);
        Picasso.with(this).load(R.drawable.plug2).into(device_1_image);
        Picasso.with(this).load(R.drawable.plug2).into(device_2_image);
        Picasso.with(this).load(R.drawable.plug2).into(device_3_image);

        toolbarTarget = new Target(){
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                getSupportActionBar().setBackgroundDrawable(new BitmapDrawable(getResources(), bitmap));
            }

            @Override
            public void onBitmapFailed(Drawable errorDrawable) {
                Log.e(TAG, "Picasso windowsBg failed");
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {

            }
        };
        deviceBgTarget = new Target(){
            @Override
            public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                device1.setBackground(new BitmapDrawable(getResources(), bitmap));
                device2.setBackground(new BitmapDrawable(getResources(), bitmap));
                device3.setBackground(new BitmapDrawable(getResources(), bitmap));
            }

            @Override
            public void onBitmapFailed(Drawable errorDrawable) {
                Log.e(TAG, "Picasso deviceBG failed");
            }

            @Override
            public void onPrepareLoad(Drawable placeHolderDrawable) {

            }
        };
        Picasso.with(this).load(R.drawable.head_bar).into(toolbarTarget);
        Picasso.with(this).load(R.drawable.setting_bg).into(deviceBgTarget);

        loadAndSetDrawable(R.drawable.selector_button_bg, button_left);
        loadAndSetDrawable(R.drawable.selector_button_bg, button_right);
        loadAndSetDrawable(R.drawable.selector_default, button_left_image);
        loadAndSetDrawable(R.drawable.selector_apply, button_right_image);
    }

    private void loadAndSetDrawable(final int resId, final View targetView) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Drawable drawable = ContextCompat.getDrawable(SettingActivity1.this, resId);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(targetView instanceof RelativeLayout) {
                            targetView.setBackground(drawable);
                        } else if(targetView instanceof ImageView) {
                            ((ImageView) targetView).setImageDrawable(drawable);
                        }
                    }
                });
            }
        }).start();
    }

    private void getLimits() {
        bleSend("", false);
    }

    private void uploadLimit() {
        JSONObject uploadObj = new JSONObject();
        try {
            uploadObj.put("", "");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        bleSend(uploadObj.toString(), true);
    }

    private void revertLimits() {
        for (int i = 0; i < 2; i++) {
            lo1.setText(String.valueOf(oldLo1/10));
            hi1.setText(String.valueOf(oldHi1/10));
            slider1.getThumb(0).setValue(oldLo1/10);

            lo2.setText(String.valueOf(oldLo2/10));
            hi2.setText(String.valueOf(oldHi2/10));
            slider2.getThumb(0).setValue(oldLo2/10);

            lo3.setText(String.valueOf(oldLo3/10));
            hi3.setText(String.valueOf(oldHi3/10));
            slider3.getThumb(0).setValue(oldLo3/10);

            slider1.getThumb(1).setValue(oldHi1/10);
            slider2.getThumb(1).setValue(oldHi2/10);
            slider3.getThumb(1).setValue(oldHi3/10);
        }
    }

    private void bleSend(String data, boolean needToReport){
        Log.d(TAG, "data=" + data);
        new AsyncTask<Object, Void, Void>() {
            @Override
            protected Void doInBackground(Object... params) {
                btApp.getTX().setValue(params[0].toString());
                btApp.getService().writeCharacteristic(btApp.getTX(), (boolean)params[1]);
                return null;
            }
        }.execute(data, needToReport);
    }

    private void processIncomingData(String data) {
        Log.d(TAG, data);
        if(!data.isEmpty()) {
            try {
                JSONObject json = new JSONObject(data);
                String cmd = json.getString("");
                if (cmd.equals("")) {
                    JSONArray loArray = json.getJSONArray("");
                    JSONArray hiArray = json.getJSONArray("");
                    numOfDevices = json.getInt("");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)){
                String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                processIncomingData(data);
            } else if(action.equals("intent.bt.limits_set")) {
                AlertDialog.Builder builder = new AlertDialog.Builder(SettingActivity1.this);
                builder.setTitle(R.string.app_name).setMessage(R.string.set_ok);
                builder.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                });
                builder.show();
            }
         }
    };
}
