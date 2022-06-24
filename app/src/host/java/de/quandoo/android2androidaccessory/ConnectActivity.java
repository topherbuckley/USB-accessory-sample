package de.quandoo.android2androidaccessory;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;

import java.util.HashMap;

import butterknife.ButterKnife;

public class ConnectActivity extends AppCompatActivity {

    public static final String DEVICE_EXTRA_KEY = "device";
    private UsbManager usbManager;

    private enum Action_2 {
        INIT,
        CONNECT
    }

    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    Action_2 action_2 = (Action_2) intent.getSerializableExtra("ACTION");

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            takeAction(device, action_2);
                        }
                    }
                    else {
                        Log.d("ChatActivity", "permission denied for device " + device);
//                        finish();
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        ButterKnife.inject(this);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
    }

    @Override
    protected void onStart() {
        super.onStart();

        final HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();

        if (deviceList == null || deviceList.size() == 0) {
            final Intent intent = new Intent(this, InfoActivity.class);
            startActivity(intent);

            finish();
            return;
        }

        if (searchForUsbAccessory(deviceList)) {
            return;
        }

        for (UsbDevice device : deviceList.values()) {
            if (!usbManager.hasPermission(device)){
                getPermission(device, Action_2.INIT);
            }else{
                takeAction(device, Action_2.CONNECT);
            }
        }
    }

    private void getPermission(UsbDevice device, Action_2 action_2){
        Intent usbPermissionIntent = new Intent(ACTION_USB_PERMISSION);
        usbPermissionIntent.putExtra("DEVICE", device);
        usbPermissionIntent.putExtra("ACTION", action_2);

        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, usbPermissionIntent, PendingIntent.FLAG_IMMUTABLE);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);

        usbManager.requestPermission(device, permissionIntent);
    }

    private void takeAction(UsbDevice device, Action_2 action_2){
        if (action_2 == Action_2.INIT){
            initAccessory(device);
        }else if (action_2 == Action_2.CONNECT){
            //call method to set up device communication
            startChat(device);
        }
    }

    private void startChat(UsbDevice device){
        final Intent intent=new Intent(this,ChatActivity.class);
        intent.putExtra(DEVICE_EXTRA_KEY, device);
        startActivity(intent);

        finish();
    }

    private boolean searchForUsbAccessory(final HashMap<String, UsbDevice> deviceList) {
        for (UsbDevice device:deviceList.values()) {
            if (isUsbAccessory(device)) {

                final Intent intent=new Intent(this,ChatActivity.class);
                intent.putExtra(DEVICE_EXTRA_KEY, device);
                startActivity(intent);

                finish();
                return true;
            }
        }

        return false;
    }

    private boolean isUsbAccessory(final UsbDevice device) {
        return (device.getProductId() == 0x2d00) || (device.getProductId() == 0x2d01);
//        return true;
    }

    private boolean initAccessory(final UsbDevice device) {

        final UsbDeviceConnection connection = usbManager.openDevice(device);

        if (connection == null) {
            return false;
        }

        initStringControlTransfer(connection, 0, "quandoo"); // MANUFACTURER
        initStringControlTransfer(connection, 1, "Android2AndroidAccessory"); // MODEL
        initStringControlTransfer(connection, 2, "showcasing android2android USB communication"); // DESCRIPTION
        initStringControlTransfer(connection, 3, "0.1"); // VERSION
        initStringControlTransfer(connection, 4, "http://quandoo.de"); // URI
        initStringControlTransfer(connection, 5, "42"); // SERIAL

        connection.controlTransfer(0x40, 53, 0, 0, new byte[]{}, 0, Constants.USB_TIMEOUT_IN_MS);

        connection.close();

        return true;
    }

    private void initStringControlTransfer(final UsbDeviceConnection deviceConnection,
                                           final int index,
                                           final String string) {
        deviceConnection.controlTransfer(0x40, 52, 0, index, string.getBytes(), string.length(), Constants.USB_TIMEOUT_IN_MS);
    }
}
