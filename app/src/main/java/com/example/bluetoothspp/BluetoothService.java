package com.example.bluetoothspp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class BluetoothService extends Service {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String CHANNEL_ID = "BluetoothSPP";
    private final IBinder binder = new LocalBinder();
    private BluetoothSocket btSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private boolean isConnected = false;
    private DataListener dataListener;

    public interface DataListener {
        void onDataReceived(byte[] data);
        void onStatusChanged(String status);
    }

    public class LocalBinder extends Binder {
        BluetoothService getService() {
            return BluetoothService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, createNotification("等待连接"));
    }

    public void setDataListener(DataListener listener) {
        this.dataListener = listener;
    }

    public void connect(String address) {
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
                BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
                BluetoothDevice device = btAdapter.getRemoteDevice(address);
                btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                btSocket.connect();
                outputStream = btSocket.getOutputStream();
                inputStream = btSocket.getInputStream();
                isConnected = true;
                updateNotification("已连接");
                if (dataListener != null) dataListener.onStatusChanged("已连接");
                startReceiving();
            } catch (Exception e) {
                if (dataListener != null) dataListener.onStatusChanged("连接失败");
            }
        }).start();
    }

    private void startReceiving() {
        new Thread(() -> {
            byte[] buffer = new byte[1024];
            while (isConnected) {
                try {
                    int bytes = inputStream.read(buffer);
                    byte[] data = new byte[bytes];
                    System.arraycopy(buffer, 0, data, 0, bytes);
                    if (dataListener != null) dataListener.onDataReceived(data);
                } catch (Exception e) {
                    isConnected = false;
                    if (dataListener != null) dataListener.onStatusChanged("连接断开");
                }
            }
        }).start();
    }

    public void send(byte[] data) {
        if (!isConnected) return;
        new Thread(() -> {
            try {
                outputStream.write(data);
            } catch (Exception e) {}
        }).start();
    }

    public boolean isConnected() {
        return isConnected;
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "蓝牙SPP服务", NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private Notification createNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("蓝牙SPP")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(pendingIntent)
            .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(1, createNotification(text));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isConnected = false;
        try {
            if (btSocket != null) btSocket.close();
        } catch (Exception e) {}
    }
}
