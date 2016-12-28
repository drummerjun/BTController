package com.junyenhuang.btcontroller;

import android.app.Application;
import android.bluetooth.BluetoothGattCharacteristic;

import com.junyenhuang.btcontroller.ble.BluetoothLeService;

public class BTApp extends Application {
    public BluetoothLeService mBluetoothLeService;
    public BluetoothGattCharacteristic tx_channel;
    public BluetoothGattCharacteristic rx_channel;
}
