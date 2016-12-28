package com.junyenhuang.btcontroller;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@TargetApi(21)
public class Sample extends AppCompatActivity {
    private static final String TAG = Sample.class.getSimpleName();
    private RelativeLayout device1, device2, device3;
    private TextView temp1, temp2, temp3;
    private TextView humidity1, humidity2, humidity3;

    private BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private BluetoothDevice mDevice;
    private String mDeviceAddress = "";
    private static final long SCAN_PERIOD = 10000;
    private int REQUEST_ENABLE_BT = 1;
    private static int mDUT = 0;

    private final UUID UUID_READ = UUID.fromString("");
    private final UUID UUID_NOTIFY = UUID.fromString("");
    private final UUID UUID_WRITE = UUID.fromString("");
    private final UUID UUID_TX = UUID.fromString("");

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

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, 2);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 2: { // REQUEST_LOCATION_PERMISSION
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted
                } else {
                    //finish(); // permission denied
                }
                return;
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        } else {
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();
                filters = new ArrayList<>();
            }
            scanLeDevice(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mGatt == null) {
            return;
        }
        mGatt.disconnect();
        mGatt.close();
        mGatt = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                //Bluetooth not enabled.
                finish();
                return;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void initViews() {
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        toolbar.setTitle(getResources().getString(R.string.app_name));
        setSupportActionBar(toolbar);

        device1 = (RelativeLayout)findViewById(R.id.device_1);
        device2 = (RelativeLayout)findViewById(R.id.device_2);
        device3 = (RelativeLayout)findViewById(R.id.device_3);

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

        RelativeLayout button_left = (RelativeLayout)findViewById(R.id.bottom_button_1);
        button_left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshStats();
            }
        });

        RelativeLayout button_right = (RelativeLayout)findViewById(R.id.bottom_button_2);
        button_right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enterSettingActivity();
            }
        });

        ImageView device_1_image = (ImageView)findViewById(R.id.device_image_1);
        ImageView device_2_image = (ImageView)findViewById(R.id.device_image_2);
        ImageView device_3_image = (ImageView)findViewById(R.id.device_image_3);
        ImageView button_left_image = (ImageView)findViewById(R.id.image_button_1);
        ImageView button_right_image = (ImageView)findViewById(R.id.image_button_2);
        // TODO: Picasso library to load image resource
    }

    private void refreshStats() {
        BluetoothGattService writeService = mGatt.getService(UUID_WRITE);
        BluetoothGattCharacteristic tx_channel = writeService.getCharacteristic(UUID_TX);
        String leQueryString = "";
        tx_channel.setValue(leQueryString);
        mGatt.writeCharacteristic(tx_channel);
    }

    private void enterSettingActivity() {

    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (Build.VERSION.SDK_INT < 21) {
                        mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    } else {
                        mLEScanner.stopScan(mScanCallback);

                    }
                }
            }, SCAN_PERIOD);
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.startLeScan(mLeScanCallback);
            } else {
                mLEScanner.startScan(filters, settings, mScanCallback);
            }
        } else {
            if (Build.VERSION.SDK_INT < 21) {
                mBluetoothAdapter.stopLeScan(mLeScanCallback);
            } else {
                mLEScanner.stopScan(mScanCallback);
            }
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i("result", result.toString());
            BluetoothDevice btDevice = result.getDevice();
            if (btDevice.getAddress().equals(mDeviceAddress)) {
                connectToDevice(btDevice);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.i("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e("Scan Failed", "Error Code: " + errorCode);
        }
    };

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Log.i("onLeScan", device.toString());
                    if (device.getAddress().equals(mDeviceAddress)) {
                        connectToDevice(device);
                    }
                }
            });
        }
    };

    public void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mDevice = device;
            mGatt = device.connectGatt(this, true, gattCallback);
            scanLeDevice(false);// will stop after first device detection
        }
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.i("gattCallback", "STATE_CONNECTED");
                    gatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.e("gattCallback", "STATE_DISCONNECTED. Reconnecting...");
                    BluetoothDevice mDevice = gatt.getDevice();
                    mGatt = null;
                    connectToDevice(mDevice);
                    break;
                default:
                    Log.e("gattCallback", "STATE_OTHER");
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            mGatt = gatt;
            List<BluetoothGattService> services = gatt.getServices();
            for (BluetoothGattService service : services) {
                Log.d(TAG, "Service UUID=" + service.getUuid().toString()
                        + " Characteristics=" + service.getCharacteristics()
                );
            }
            BluetoothGattService readService = gatt.getService(UUID_READ);
            BluetoothGattCharacteristic characteristic = readService.getCharacteristic(UUID_NOTIFY);
            for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
                mGatt.writeDescriptor(descriptor);
            }
            gatt.setCharacteristicNotification(characteristic, true);
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic, int status) {
            Log.i("onCharacteristicRead", characteristic.toString());
            byte[] value = characteristic.getValue();
            String v = new String(value);
            Log.i("onCharacteristicRead", "Value: " + v);
            //gatt.disconnect();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            float char_float_value = characteristic.getFloatValue(BluetoothGattCharacteristic.FORMAT_FLOAT, 1);
            Log.i("onCharacteristicChanged", Float.toString(char_float_value));
            //gatt.disconnect();
        }
    };
}