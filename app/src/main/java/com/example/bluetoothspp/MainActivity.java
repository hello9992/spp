package com.example.bluetoothspp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;
    private OutputStream outputStream;
    private InputStream inputStream;
    private TextView statusText;
    private EditText sendInput;
    private TextView logText;
    private RadioGroup sendFormatGroup;
    private RadioGroup receiveFormatGroup;
    private ArrayList<String> logList = new ArrayList<>();
    private boolean isConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = findViewById(R.id.statusText);
        sendInput = findViewById(R.id.sendInput);
        logText = findViewById(R.id.logText);
        sendFormatGroup = findViewById(R.id.sendFormatGroup);
        receiveFormatGroup = findViewById(R.id.receiveFormatGroup);
        Button scanBtn = findViewById(R.id.scanBtn);
        Button sendBtn = findViewById(R.id.sendBtn);
        Button saveLogBtn = findViewById(R.id.saveLogBtn);
        ListView deviceList = findViewById(R.id.deviceList);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);

        scanBtn.setOnClickListener(v -> scanDevices(deviceList));
        sendBtn.setOnClickListener(v -> sendData());
        saveLogBtn.setOnClickListener(v -> saveLog());

        deviceList.setOnItemClickListener((parent, view, position, id) -> {
            String item = (String) parent.getItemAtPosition(position);
            String address = item.substring(item.length() - 17);
            connectDevice(address);
        });
    }

    private void scanDevices(ListView listView) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
        Set<BluetoothDevice> pairedDevices = btAdapter.getBondedDevices();
        ArrayList<String> deviceNames = new ArrayList<>();
        for (BluetoothDevice device : pairedDevices) {
            deviceNames.add(device.getName() + "\n" + device.getAddress());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, deviceNames);
        listView.setAdapter(adapter);
        statusText.setText("Found " + deviceNames.size() + " devices");
    }

    private void connectDevice(String address) {
        new Thread(() -> {
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return;
                BluetoothDevice device = btAdapter.getRemoteDevice(address);
                btSocket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                btSocket.connect();
                outputStream = btSocket.getOutputStream();
                inputStream = btSocket.getInputStream();
                isConnected = true;
                runOnUiThread(() -> statusText.setText("Connected to " + address));
                startReceiving();
            } catch (Exception e) {
                runOnUiThread(() -> statusText.setText("Connection failed: " + e.getMessage()));
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
                    int checkedId = receiveFormatGroup.getCheckedRadioButtonId();
                    String displayData = checkedId == R.id.receiveHex ? bytesToHex(data)
                        : checkedId == R.id.receiveDec ? bytesToDec(data) : new String(data);
                    String log = getTimestamp() + " RX: " + displayData;
                    logList.add(log);
                    runOnUiThread(() -> logText.append(log + "\n"));
                } catch (Exception e) {
                    isConnected = false;
                }
            }
        }).start();
    }

    private void sendData() {
        if (!isConnected) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        String input = sendInput.getText().toString();
        new Thread(() -> {
            try {
                byte[] data = sendFormatGroup.getCheckedRadioButtonId() == R.id.sendHex
                    ? hexToBytes(input) : input.getBytes();
                outputStream.write(data);
                String log = getTimestamp() + " TX: " + input;
                logList.add(log);
                runOnUiThread(() -> {
                    logText.append(log + "\n");
                    sendInput.setText("");
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Send failed", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void saveLog() {
        try {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "BluetoothSPP");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "log_" + System.currentTimeMillis() + ".txt");
            FileWriter writer = new FileWriter(file);
            for (String log : logList) {
                writer.write(log + "\n");
            }
            writer.close();
            Toast.makeText(this, "Log saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getTimestamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    private String bytesToDec(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%d ", b & 0xFF));
        }
        return sb.toString().trim();
    }

    private byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("0x|0X", "").replaceAll("\\s+", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isConnected = false;
        try {
            if (btSocket != null) btSocket.close();
        } catch (Exception e) {}
    }
}
