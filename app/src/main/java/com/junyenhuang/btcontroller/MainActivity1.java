package com.junyenhuang.btcontroller;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.junyenhuang.btcontroller.ble.BluetoothLeService;
import com.junyenhuang.btcontroller.ble.BluetoothSPP;
import com.junyenhuang.btcontroller.ble.GattAttributes;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MainActivity1 extends AppCompatActivity {
    private static final String TAG = MainActivity1.class.getSimpleName();
    private RelativeLayout device1, device2, device3;
    private TextView temp1, temp2, temp3;
    private TextView humidity1, humidity2, humidity3;
    private RelativeLayout mButtonLeft;

    //----------- use for BLE ----------------------
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private final String LIST_NAME = "";
    private final String LIST_UUID = "";
    private int REQUEST_ENABLE_BT = 1;
    private String mDeviceName;
    private String mDeviceAddress="";
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic tx_channel;
    private BluetoothGattCharacteristic rx_channel;
    private BluetoothSPP mBluetooth;
    private BluetoothGatt mGatt;
    private int mQueryID = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();

        mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
            intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
            intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
            intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
            registerReceiver(mGattUpdateReceiver, intentFilter);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                finish();
                return;
            } else {
                IntentFilter intentFilter = new IntentFilter();
                intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
                intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
                intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
                intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
                registerReceiver(mGattUpdateReceiver, intentFilter);
              }
        }
        super.onActivityResult(requestCode, resultCode, data);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
        if (mGatt == null) {
            return;
        }
        mGatt.close();
        mGatt = null;
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        toolbar.setTitle(getResources().getString(R.string.app_name));
        setSupportActionBar(toolbar);

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

        temp1 = (TextView)findViewById(R.id.device_temp_1);
        temp2 = (TextView)findViewById(R.id.device_temp_2);
        temp3 = (TextView)findViewById(R.id.device_temp_3);
        humidity1 = (TextView)findViewById(R.id.device_humid_1);
        humidity2 = (TextView)findViewById(R.id.device_humid_2);
        humidity3 = (TextView)findViewById(R.id.device_humid_3);

        mButtonLeft = (RelativeLayout)findViewById(R.id.bottom_button_1);
        mButtonLeft.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SharedPreferences pref = getSharedPreferences("DEVICES", MODE_PRIVATE);
                if(pref.getBoolean("BLE1", true)) {
                    mQueryID = 1;
                    refreshStats(mQueryID);
                } else if(pref.getBoolean("BLE2", true)) {
                    mQueryID = 2;
                    refreshStats(mQueryID);
                } else if(pref.getBoolean("BLE3", true)) {
                    mQueryID = 3;
                    refreshStats(mQueryID);
                }
            }
        });

        final RelativeLayout button_right = (RelativeLayout)findViewById(R.id.bottom_button_2);
        button_right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enterSettingActivity();
            }
        });

        new Thread(new Runnable() {
            @Override
            public void run() {
                final Drawable toolbarBg = ResourcesCompat.getDrawable(getResources(), R.drawable.head_bar, null);
                final Drawable bg = ResourcesCompat.getDrawable(getResources(), R.drawable.background2, null);
                final Drawable deviceBg = ResourcesCompat.getDrawable(getResources(), R.drawable.status_bg, null);
                final Drawable buttonBg = ResourcesCompat.getDrawable(getResources(), R.drawable.selector_button, null);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            getSupportActionBar().setBackgroundDrawable(toolbarBg);
                        } catch (NullPointerException e) {
                            e.printStackTrace();
                        }
                        getWindow().setBackgroundDrawable(bg);
                        device1.setBackground(deviceBg);
                        device2.setBackground(deviceBg);
                        device3.setBackground(deviceBg);
                        mButtonLeft.setBackground(buttonBg);
                        button_right.setBackground(buttonBg);
                    }
                });
            }
        }).start();

        ImageView device_1_image = (ImageView)findViewById(R.id.device_image_1);
        ImageView device_2_image = (ImageView)findViewById(R.id.device_image_2);
        ImageView device_3_image = (ImageView)findViewById(R.id.device_image_3);
        ImageView button_left_image = (ImageView)findViewById(R.id.image_button_1);
        ImageView button_right_image = (ImageView)findViewById(R.id.image_button_2);
        // TODO: Picasso library to load image resource
    }

    private void refreshStats(int queryID) {
        mButtonLeft.setEnabled(false);
        String leQueryString = "";
        bleSend(leQueryString);
    }

    private void enterSettingActivity() {

    }

    private void bleSend(String data){
        if(mConnected){
            tx_channel.setValue(data);
            mBluetoothLeService.writeCharacteristic(tx_channel);
        }
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<>();
        mGattCharacteristics = new ArrayList<>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(LIST_NAME, GattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData = new ArrayList<>();
            List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas = new ArrayList<>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                Log.d(TAG, "service UUID=" + gattService.getUuid().toString());
                Log.d(TAG, "characteristic UUID=" + gattCharacteristic.getUuid().toString());
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<>();
                uuid = gattCharacteristic.getUuid().toString();
                if(uuid.equalsIgnoreCase(GattAttributes.BLE_TX_CHANNEL)){
                    tx_channel = gattCharacteristic;
                }
                if(uuid.equalsIgnoreCase(GattAttributes.BLE_RX_CHANNEL)){
                    rx_channel = gattCharacteristic;
                }
                currentCharaData.put(LIST_NAME, GattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }
    }

    private void processIncomingData(String data) {
        Log.d(TAG, data);
        if(data.equals("{}")) {
            getSharedPreferences("DEVICES", MODE_PRIVATE).edit()
                    .putBoolean("BLE" + String.valueOf(mQueryID), false).apply();
        } else if(!data.isEmpty()) {
            try {
                JSONObject json = new JSONObject(data);
                String cmd = json.getString("");
                if (cmd.equals("")) {
                    int id = json.getInt("");
                    String temp = json.getString("");
                    String humidity = json.getString("");
                    SharedPreferences prefs = getSharedPreferences("DEVICES", MODE_PRIVATE);
                    prefs.edit().putBoolean("BLE" + String.valueOf(id), true).apply();
                    switch (id) {
                        case 1:
                            device1.setVisibility(View.VISIBLE);
                            temp1.setText(temp);
                            humidity1.setText(humidity);
                            break;
                        case 2:
                            device2.setVisibility(View.VISIBLE);
                            temp2.setText(temp);
                            humidity2.setText(humidity);
                            break;
                        case 3:
                            device3.setVisibility(View.VISIBLE);
                            temp3.setText(temp);
                            humidity3.setText(humidity);
                            break;
                    }
                } else if (cmd.equals("echo")) {
                    Toast.makeText(MainActivity1.this, "Hi 你好 こんにちは", Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            getSharedPreferences("DEVICES", MODE_PRIVATE).edit()
                    .putBoolean("BLE" + String.valueOf(mQueryID), false).apply();
        }
    }

    // Code to manage Service lifecycle.
    public final ServiceConnection mServiceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            mBluetoothLeService = ((BluetoothLeService.LocalBinder)service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.d(TAG, "mBluetoothLeService finish");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "service connection result=" + result);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "mBluetoothLeService onServiceDisconnected");
            mBluetoothLeService.disconnect();
            mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)){
                mConnected = false;
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)){
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
                mBluetoothLeService.setCharacteristicNotification(rx_channel, true);

                SharedPreferences pref = getSharedPreferences("DEVICES", MODE_PRIVATE);
                if(pref.getBoolean("BLE1", true)) {
                    mQueryID = 1;
                    refreshStats(mQueryID);
                } else if(pref.getBoolean("BLE2", true)) {
                    mQueryID = 2;
                    refreshStats(mQueryID);
                } else if(pref.getBoolean("BLE3", true)) {
                    mQueryID = 3;
                    refreshStats(mQueryID);
                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)){
                String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                processIncomingData(data);

                SharedPreferences pref = getSharedPreferences("DEVICES", MODE_PRIVATE);
                switch (mQueryID) {
                    case 1:
                        if(pref.getBoolean("BLE2", true)) {
                            mQueryID = 2;
                            refreshStats(mQueryID);
                        } else if(pref.getBoolean("BLE3", true)) {
                            mQueryID = 3;
                            refreshStats(mQueryID);
                        } else {
                            mQueryID = 1;
                            mButtonLeft.setEnabled(true);
                        }
                        break;
                    case 2:
                        if(pref.getBoolean("BLE3", true)) {
                            mQueryID = 3;
                            refreshStats(mQueryID);
                        } else {
                            mQueryID = 1;
                            mButtonLeft.setEnabled(true);
                        }
                        break;
                    case 3:
                        mQueryID = 1;
                        mButtonLeft.setEnabled(true);
                        break;
                }
            }
        }
    };
}
