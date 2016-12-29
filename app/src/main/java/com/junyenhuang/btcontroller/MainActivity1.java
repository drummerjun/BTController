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
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.Pair;
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
import com.junyenhuang.btcontroller.ble.GattAttributes;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

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

    //----------- use for BLE ----------------------
    private BluetoothAdapter mBluetoothAdapter;
    //private Handler mHandler;
    private final String LIST_NAME = "";
    private final String LIST_UUID = "";
    private int REQUEST_ENABLE_BT = 1;
    private String mDeviceAddress="";
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<>();
    private boolean mConnected = false;
    //private BluetoothSPP mBluetooth;
    private BluetoothGatt mGatt;
    private BTApp btApp;
    private Target mToolbarTarget, mDeviceTarget;

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

        //mHandler = new Handler();
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
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction("intent.bt.get_value");
        intentFilter.addAction("intent.bt.bad_mac");
        registerReceiver(mGattUpdateReceiver, intentFilter);

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == Activity.RESULT_CANCELED) {
                finish();
                return;
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
        btApp.setService(null);
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

        RelativeLayout button_left = (RelativeLayout)findViewById(R.id.bottom_button_1);
        button_left.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendBroadcast(new Intent("intent.bt.get_value"));
            }
        });

        RelativeLayout button_right = (RelativeLayout)findViewById(R.id.bottom_button_2);
        button_right.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enterSettingActivity();
            }
        });

        ImageView button_left_image = (ImageView)findViewById(R.id.image_button_1);
        ImageView button_right_image = (ImageView)findViewById(R.id.image_button_2);

        ImageView device_1_image = (ImageView)findViewById(R.id.device_image_1);
        ImageView device_2_image = (ImageView)findViewById(R.id.device_image_2);
        ImageView device_3_image = (ImageView)findViewById(R.id.device_image_3);
        Picasso.with(this).load(R.drawable.circular).into(device_1_image);
        Picasso.with(this).load(R.drawable.circular).into(device_2_image);
        Picasso.with(this).load(R.drawable.circular).into(device_3_image);

        mToolbarTarget = new Target(){
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
        mDeviceTarget = new Target(){
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
        Picasso.with(this).load(R.drawable.head_bar).into(mToolbarTarget);
        Picasso.with(this).load(R.drawable.status_bg).into(mDeviceTarget);

        loadAndSetDrawable(R.drawable.selector_button_bg, button_left);
        loadAndSetDrawable(R.drawable.selector_button_bg, button_right);
        loadAndSetDrawable(R.drawable.selector_refresh, button_left_image);
        loadAndSetDrawable(R.drawable.selector_setting, button_right_image);
    }

    private void loadAndSetDrawable(final int resId, View targetView) {
        new AsyncTask<View, Void, Pair<Drawable, View>>() {
            @Override
            protected Pair<Drawable, View> doInBackground(View... params) {
                Drawable drawable = ContextCompat.getDrawable(MainActivity1.this, resId);
                return new Pair<>(drawable, params[0]);
            }

            @Override
            protected void onPostExecute(Pair<Drawable, View> pair) {
                super.onPostExecute(pair);
                View view = pair.second;
                Drawable drawable = pair.first;
                if(view instanceof RelativeLayout) {
                    view.setBackground(drawable);
                } else if(view instanceof ImageView) {
                    ((ImageView) view).setImageDrawable(drawable);
                }
            }
        }.execute(targetView);
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
            new AsyncTask<String, Void, Boolean>() {
                @Override
                protected Boolean doInBackground(String... params) {
                    try {
                        btApp.getTX().setValue(params[0]);
                        btApp.getService().writeCharacteristic(btApp.getTX(), false);
                    } catch (NullPointerException ex) {
                        ex.printStackTrace();
                        return false;
                    }
                    return true;
                }

                @Override
                protected void onPostExecute(Boolean success) {
                    super.onPostExecute(success);
                    if(!success) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity1.this);
                        builder.setTitle(R.string.app_name).setMessage(R.string.scan_error);
                        builder.setNeutralButton(android.R.string.ok,
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        dialog.dismiss();
                                    }
                                });
                        builder.create().show();
                    }
                }
            }.execute(data);
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
                //Log.w(TAG, "service UUID=" + gattService.getUuid().toString());
                //Log.w(TAG, "characteristic UUID=" + gattCharacteristic.getUuid().toString());
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<>();
                uuid = gattCharacteristic.getUuid().toString();
                if(uuid.equalsIgnoreCase(GattAttributes.BLE_TX_CHANNEL)){
                    btApp.setTX(gattCharacteristic);
                }
                if(uuid.equalsIgnoreCase(GattAttributes.BLE_RX_CHANNEL)){
                    btApp.setRX(gattCharacteristic);
                    btApp.getService().setCharacteristicNotification(btApp.getRX(), true);
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
            btApp.setService(((BluetoothLeService.LocalBinder)service).getService());
            if (!btApp.getService().initialize()) {
                Log.d(TAG, "mBluetoothLeService finish");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            boolean result = btApp.getService().connect(mDeviceAddress);
            Log.d(TAG, "service connection result=" + result);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "mBluetoothLeService onServiceDisconnected");
            btApp.getService().disconnect();
            btApp.setService(null);
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
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... params) {
                        displayGattServices(btApp.getService().getSupportedGattServices());
                        return null;
                    }
                    @Override
                    protected void onPostExecute(Void aVoid) {
                        super.onPostExecute(aVoid);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        //refreshStats();
                        sendBroadcast(new Intent("intent.bt.get_value"));
                    }
                }.execute();
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)){
                String data = intent.getStringExtra(BluetoothLeService.EXTRA_DATA);
                processIncomingData(data);
            } else if(action.equals("intent.bt.get_value")) {
                refreshStats();
            } else if(action.equals("intent.bt.bad_mac")) {
                String badMacString = intent.getStringExtra("BAD_MAC");
                String messageString = badMacString + " " + getString(R.string.bad_mac);
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity1.this);
                builder.setTitle(R.string.app_name).setMessage(messageString);
                builder.setNeutralButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        });
                builder.create().show();
            }
         }
    };
}
