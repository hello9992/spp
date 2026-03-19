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
import android.os.Environment;
import android.os.IBinder;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
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
    private ArrayList<String> logList = new ArrayList<>();
    private String connectedAddress = "";

    public interface DataListener {
        void onDataReceived(byte[] data);
        void onStatusChanged(String status);
        void onLogSaved(String path);
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
        this.connectedAddress = address;
        this.logList.clear();
        addLog("连接到 " + address);
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
                addLog("连接成功");
                if (dataListener != null) dataListener.onStatusChanged("已连接");
                startReceiving();
            } catch (Exception e) {
                addLog("连接失败: " + e.getMessage());
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
                    addLog("连接断开");
                    updateNotification("连接断开");
                    if (dataListener != null) dataListener.onStatusChanged("连接断开");
                    saveLog();
                }
            }
        }).start();
    }

    public void send(byte[] data, String displayText) {
        if (!isConnected) return;
        addLog("TX: " + displayText);
        new Thread(() -> {
            try {
                outputStream.write(data);
            } catch (Exception e) {
                addLog("发送失败");
            }
        }).start();
    }

    public void addLog(String log) {
        String timestamp = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
        logList.add(timestamp + " " + log);
    }

    public ArrayList<String> getLogList() {
        return logList;
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

    private void saveLog() {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BluetoothSPP");
            if (!dir.exists()) dir.mkdirs();
            String fileName = "log_" + connectedAddress.replaceAll(":", "_") + "_" + System.currentTimeMillis() + ".txt";
            File file = new File(dir, fileName);
            FileWriter writer = new FileWriter(file);
            for (String log : logList) {
                writer.write(log + "\n");
            }
            writer.close();
            if (dataListener != null) dataListener.onLogSaved(file.getAbsolutePath());
        } catch (Exception e) {
            addLog("保存日志失败: " + e.getMessage());
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isConnected = false;
        if (logList.size() > 0 && !isConnected) {
            saveLog();
        }
        try {
            if (btSocket != null) btSocket.close();
        } catch (Exception e) {}
    }
}
