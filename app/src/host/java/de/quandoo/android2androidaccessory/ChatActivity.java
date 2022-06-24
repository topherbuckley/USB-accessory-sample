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
    private UsbManager usbManager;

    @Override
    protected void sendString(final String string) {
        sendBuffer.add(string);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        UsbDevice device = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(() -> chat(device));
    }

    private void chat(UsbDevice device){
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
