package com.junyenhuang.btcontroller;

import android.app.Application;
import android.bluetooth.BluetoothGattCharacteristic;

import com.junyenhuang.btcontroller.ble.BluetoothLeService;

public class BTApp extends Application {
    private BluetoothLeService mBluetoothLeService;
    private BluetoothGattCharacteristic tx_channel;
    private BluetoothGattCharacteristic rx_channel;

    public void setService(BluetoothLeService service) {
        mBluetoothLeService = service;
    }

    public BluetoothLeService getService() {
        return mBluetoothLeService;
    }

    public void setTX(BluetoothGattCharacteristic tx) {
        tx_channel = tx;
    }

    public BluetoothGattCharacteristic getTX() {
        return tx_channel;
    }

    public void setRX(BluetoothGattCharacteristic rx) {
        rx_channel = rx;
    }

    public BluetoothGattCharacteristic getRX() {
        return rx_channel;
    }
}
