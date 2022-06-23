package de.quandoo.android2androidaccessory;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;

public class ChatActivity extends BaseChatActivity {

    private final AtomicBoolean keepThreadAlive = new AtomicBoolean(true);
    private final List<String> sendBuffer = new ArrayList<>();

    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
                            ExecutorService executorService = Executors.newSingleThreadExecutor();
                            executorService.execute(() -> connect(device));
                        }
                    }
                    else {
                        Log.d("ChatActivity", "permission denied for device " + device);
                    }
                }
            }
        }
    };

    private PendingIntent permissionIntent;
    private UsbManager usbManager;
    private Intent usbPermissionIntent;

    @Override
    protected void sendString(final String string) {
        sendBuffer.add(string);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        usbPermissionIntent = new Intent(ACTION_USB_PERMISSION);

        permissionIntent = PendingIntent.getBroadcast(this, 0, usbPermissionIntent, 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);

        final UsbDevice device=getIntent().getParcelableExtra(ConnectActivity.DEVICE_EXTRA_KEY);
        usbPermissionIntent.putExtra("DEVICE", device);
        usbManager.requestPermission(device, permissionIntent);
    }

    private void connect(UsbDevice device){
        UsbEndpoint endpointIn = null;
        UsbEndpoint endpointOut = null;

        final UsbInterface usbInterface = device.getInterface(0);

        for (int i = 0; i < device.getInterface(0).getEndpointCount(); i++) {

            final UsbEndpoint endpoint = device.getInterface(0).getEndpoint(i);
            if (endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                endpointIn = endpoint;
            }
            if (endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                endpointOut = endpoint;
            }

        }

        if (endpointIn == null) {
            printLineToUI("Input Endpoint not found");
            return;
        }

        if (endpointOut == null) {
            printLineToUI("Output Endpoint not found");
            return;
        }

        final UsbDeviceConnection connection = usbManager.openDevice(device);

        if (connection == null) {
            printLineToUI("Could not open device");
            return;
        }

        final boolean claimResult = connection.claimInterface(usbInterface, true);

        if (!claimResult) {
            printLineToUI("Could not claim device");
        } else {
            final byte buff[] = new byte[Constants.BUFFER_SIZE_IN_BYTES];
            printLineToUI("Claimed interface - ready to communicate");

            while (keepThreadAlive.get()) {
                final int bytesTransferred = connection.bulkTransfer(endpointIn, buff, buff.length, Constants.USB_TIMEOUT_IN_MS);
                if (bytesTransferred > 0) {
                    printLineToUI("device> "+new String(buff, 0, bytesTransferred));
                }

                synchronized (sendBuffer) {
                    if (sendBuffer.size()>0) {
                        final byte[] sendBuff=sendBuffer.get(0).toString().getBytes();
                        connection.bulkTransfer(endpointOut, sendBuff, sendBuff.length, Constants.USB_TIMEOUT_IN_MS);
                        sendBuffer.remove(0);
                    }
                }
            }
        }

        connection.releaseInterface(usbInterface);
        connection.close();
    }

    @Override
    protected void onStop() {
        super.onStop();
        keepThreadAlive.set(false);
    }
}
