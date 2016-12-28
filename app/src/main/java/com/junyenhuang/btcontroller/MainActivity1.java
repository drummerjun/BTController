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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.content.res.ResourcesCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.junyenhuang.btcontroller.ble.BluetoothLeService;
import com.junyenhuang.btcontroller.ble.BluetoothSPP;
import com.junyenhuang.btcontroller.ble.GattAttributes;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity1 extends AppCompatActivity {
    private static final String TAG = MainActivity1.class.getSimpleName();
    private RelativeLayout device1, device2, device3;
    private TextView temp1, temp2, temp3;
    private TextView humidity1, humidity2, humidity3;
    private RelativeLayout mButtonLeft;

    //----------- use for BLE ----------------------
    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    private int REQUEST_ENABLE_BT = 1;
    private String mDeviceName;
    private String mDeviceAddress="";
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<>();
    private boolean mConnected = false;
    private BluetoothSPP mBluetooth;
    private BluetoothGatt mGatt;
    private int mQueryID = 1;
    private BTApp btApp;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        invalidateOptionsMenu();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == R.id.action_test) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(R.string.app_name).setMessage(R.string.confirm_exit);
            builder.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    getSharedPreferences("DEVICES", MODE_PRIVATE).edit().clear().apply();
                    //remove("MAC").apply();
                    Intent intent = new Intent(MainActivity1.this, BarcodeActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            });
            builder.setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.show();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();

        btApp = (BTApp)getApplication();

        mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        mDeviceAddress = getSharedPreferences("DEVICES", MODE_PRIVATE).getString("MAC", mDeviceAddress);
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
            intentFilter.addAction("GET_VALUE");
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
        btApp.mBluetoothLeService = null;
        btApp = null;
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
                sendBroadcast(new Intent("GET_VALUE"));
            }
        });

        final RelativeLayout button_right = (RelativeLayout)findViewById(R.id.bottom_button_2);
        button_right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enterSettingActivity();
            }
        });

        final ImageView button_left_image = (ImageView)findViewById(R.id.image_button_1);
        final ImageView button_right_image = (ImageView)findViewById(R.id.image_button_2);

        ImageView device_1_image = (ImageView)findViewById(R.id.device_image_1);
        ImageView device_2_image = (ImageView)findViewById(R.id.device_image_2);
        ImageView device_3_image = (ImageView)findViewById(R.id.device_image_3);
        Picasso.with(this).load(R.drawable.circular).into(device_1_image);
        Picasso.with(this).load(R.drawable.circular).into(device_2_image);
        Picasso.with(this).load(R.drawable.circular).into(device_3_image);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final Drawable toolbarBg = ResourcesCompat.getDrawable(getResources(), R.drawable.head_bar, null);
                final Drawable bg = ResourcesCompat.getDrawable(getResources(), R.drawable.background2, null);
                final Drawable deviceBg = ResourcesCompat.getDrawable(getResources(), R.drawable.status_bg, null);
                final Drawable buttonBg = ResourcesCompat.getDrawable(getResources(), R.drawable.selector_button_bg, null);
                final Drawable buttonBg1 = ResourcesCompat.getDrawable(getResources(), R.drawable.selector_button_bg, null);
                final Drawable refreshButton = ResourcesCompat.getDrawable(getResources(), R.drawable.selector_refresh, null);
                final Drawable settingButton = ResourcesCompat.getDrawable(getResources(), R.drawable.selector_setting, null);
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
                        button_left_image.setImageDrawable(refreshButton);
                        button_right_image.setImageDrawable(settingButton);
                        button_right.setBackground(buttonBg1);
                        mButtonLeft.setBackground(buttonBg);
                    }
                });
            }
        }).start();
    }

    private void refreshStats() {
        bleSend("");
    }

    private void enterSettingActivity() {
        int numOfDevices = getSharedPreferences("DEVICES", MODE_PRIVATE).getInt("BLE", 0);
        if(numOfDevices > 0) {
            Intent intent = new Intent(MainActivity1.this, SettingActivity.class);
            intent.putExtra("DEVICE_NUM", numOfDevices);
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.no_device, Toast.LENGTH_LONG).show();
        }
    }

    private void bleSend(String data){
        Log.d(TAG, "connected=" + mConnected);
        Log.d(TAG, "data=" + data);
        if(mConnected){
            btApp.tx_channel.setValue(data);
            btApp.mBluetoothLeService.writeCharacteristic(btApp.tx_channel, false);
        } else {
            Toast.makeText(this, R.string.reboot, Toast.LENGTH_LONG).show();
            recreate();
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
                //Log.d(TAG, "service UUID=" + gattService.getUuid().toString());
                //Log.d(TAG, "characteristic UUID=" + gattCharacteristic.getUuid().toString());
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<>();
                uuid = gattCharacteristic.getUuid().toString();
                if(uuid.equalsIgnoreCase(GattAttributes.BLE_TX_CHANNEL)){
                    btApp.tx_channel = gattCharacteristic;
                }
                if(uuid.equalsIgnoreCase(GattAttributes.BLE_RX_CHANNEL)){
                    btApp.rx_channel = gattCharacteristic;
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
        if(!data.isEmpty()) {
            try {
                JSONObject json = new JSONObject(data);
                String cmd = json.getString("");
                if (cmd.equals("")) {
                    //int id = json.getInt("ID");
                    JSONArray tempArray = json.getJSONArray("");
                    JSONArray humiArray = json.getJSONArray("");
                    int numOfDevices = json.getInt("");
                    getSharedPreferences("DEVICES", MODE_PRIVATE).edit().putInt("BLE", numOfDevices).apply();
                    for (int i = 0; i < numOfDevices; i++) {
                        String tempString = tempArray.get(i).toString();
                        String humiString = humiArray.get(i).toString();
                        double temp = Double.parseDouble(tempString) / 10;
                        double humi = Double.parseDouble(humiString) / 10;
                        switch (i) {
                            case 0:
                                device1.setVisibility(View.VISIBLE);
                                temp1.setText(getString(R.string.temp) + ": " + String.valueOf(temp) + (char)0x00B0 + "C");
                                humidity1.setText(getString(R.string.humi) + ": " + String.valueOf(humi) + "%");
                                break;
                            case 1:
                                device2.setVisibility(View.VISIBLE);
                                temp2.setText(getString(R.string.temp) + ": " + String.valueOf(temp) + (char)0x00B0 + "C");
                                humidity2.setText(getString(R.string.humi) + ": " + String.valueOf(humi) + "%");
                                break;
                            case 2:
                                device3.setVisibility(View.VISIBLE);
                                temp3.setText(getString(R.string.temp) + ": " + String.valueOf(temp) + (char)0x00B0 + "C");
                                humidity3.setText(getString(R.string.humi) + ": " + String.valueOf(humi) + "%");
                                break;
                        }
                    }
                } else if (cmd.equals("echo")) {
                    Toast.makeText(MainActivity1.this, "Hi 你好 こんにちは", Toast.LENGTH_SHORT).show();
                }
            } catch (JSONException e) {
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    // Code to manage Service lifecycle.
    public final ServiceConnection mServiceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            btApp.mBluetoothLeService = ((BluetoothLeService.LocalBinder)service).getService();
            if (!btApp.mBluetoothLeService.initialize()) {
                Log.d(TAG, "mBluetoothLeService finish");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            boolean result = btApp.mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "service connection result=" + result);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "mBluetoothLeService onServiceDisconnected");
            btApp.mBluetoothLeService.disconnect();
            btApp.mBluetoothLeService = null;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.d(TAG, "ACTION_GATT_CONNECTED");
                mConnected = true;
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)){
                Log.d(TAG, "ACTION_GATT_DISCONNECTED");
                mConnected = false;
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)){
                Log.d(TAG, "ACTION_GATT_SERVICES_DISCOVERED");
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(btApp.mBluetoothLeService.getSupportedGattServices());
                btApp.mBluetoothLeService.setCharacteristicNotification(btApp.rx_channel, true);

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                refreshStats();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)){
                Log.d(TAG, "ACTION_DATA_AVAILABLE");
                String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                processIncomingData(data);
            } else if(action.equals("GET_VALUE")) {
                refreshStats();
            }
         }
    };
}
