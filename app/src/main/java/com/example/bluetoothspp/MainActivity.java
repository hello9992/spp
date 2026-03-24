package com.example.bluetoothspp;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
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
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends AppCompatActivity {
    private BluetoothAdapter btAdapter;
    private BluetoothService btService;
    private boolean serviceBound = false;
    private TextView statusText;
    private EditText sendInput;
    private TextView logText;
    private RadioGroup sendFormatGroup;
    private RadioGroup receiveFormatGroup;
    private ArrayList<String> logList = new ArrayList<>();

    // 电量分析相关数据结构
    private static class BatteryDataPoint {
        long timestamp;  // 毫秒时间戳
        int voltage;
        int percentage;
        int level;

        BatteryDataPoint(long timestamp, int voltage, int percentage, int level) {
            this.timestamp = timestamp;
            this.voltage = voltage;
            this.percentage = percentage;
            this.level = level;
        }
    }
    private List<BatteryDataPoint> batteryDataList = new ArrayList<>();
    private long batteryRecordStartTime = 0;

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
            Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.FOREGROUND_SERVICE}, 1);

        Intent intent = new Intent(this, BluetoothService.class);
        startForegroundService(intent);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        scanBtn.setOnClickListener(v -> scanDevices(deviceList));
        sendBtn.setOnClickListener(v -> sendData());
        saveLogBtn.setOnClickListener(v -> saveLog());

        deviceList.setOnItemClickListener((parent, view, position, id) -> {
            String item = (String) parent.getItemAtPosition(position);
            String address = item.substring(item.length() - 17);
            if (serviceBound) {
                // 清空电量分析数据
                batteryDataList.clear();
                batteryRecordStartTime = 0;
                btService.connect(address);
            }
        });
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BluetoothService.LocalBinder binder = (BluetoothService.LocalBinder) service;
            btService = binder.getService();
            serviceBound = true;
            btService.setDataListener(new BluetoothService.DataListener() {
                @Override
                public void onDataReceived(byte[] data) {
                    int checkedId = receiveFormatGroup.getCheckedRadioButtonId();
                    String displayData;
                    if (checkedId == R.id.receiveHex) {
                        displayData = bytesToHex(data);
                    } else if (checkedId == R.id.receiveDec) {
                        displayData = bytesToDec(data);
                    } else if (checkedId == R.id.receiveBattery) {
                        displayData = bytesToBattery(data);
                    } else {
                        displayData = new String(data);
                    }
                    String log = "RX: " + displayData;
                    btService.addLog(log);
                    logList.add(getTimestamp() + " " + log);
                    runOnUiThread(() -> logText.append(getTimestamp() + " " + log + "\n"));
                }

                @Override
                public void onStatusChanged(String status) {
                    runOnUiThread(() -> statusText.setText(status));
                }

                @Override
                public void onLogSaved(String path) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "日志已保存: " + path, Toast.LENGTH_LONG).show());
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };

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

    private void sendData() {
        if (!serviceBound || !btService.isConnected()) {
            Toast.makeText(this, "Not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        String input = sendInput.getText().toString();
        byte[] data = sendFormatGroup.getCheckedRadioButtonId() == R.id.sendHex
            ? hexToBytes(input) : input.getBytes();
        btService.send(data, input);
        String log = getTimestamp() + " TX: " + input;
        logList.add(log);
        logText.append(log + "\n");
        sendInput.setText("");
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

            // 如果当前选择的是电量分析模式，添加电量分析统计
            if (receiveFormatGroup.getCheckedRadioButtonId() == R.id.receiveBattery && !batteryDataList.isEmpty()) {
                writer.write("\n========== 电量分析统计 ==========\n");
                String analysis = generateBatteryAnalysis();
                writer.write(analysis);

                // 生成并保存电量图表
                File chartFile = new File(dir, "battery_chart_" + System.currentTimeMillis() + ".png");
                if (generateBatteryChart(chartFile)) {
                    writer.write("\n图表已保存: " + chartFile.getName() + "\n");
                }
            }

            writer.close();
            Toast.makeText(this, "Log saved: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 生成电量分析统计信息
     * 按连续段统计每个电量等级的持续时间、电压范围和时间范围
     */
    private String generateBatteryAnalysis() {
        if (batteryDataList.isEmpty()) {
            return "无电量数据";
        }

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        SimpleDateFormat fullFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

        sb.append("记录开始时间: ").append(fullFormat.format(new Date(batteryRecordStartTime))).append("\n");
        sb.append("记录结束时间: ").append(fullFormat.format(new Date(batteryDataList.get(batteryDataList.size() - 1).timestamp))).append("\n");
        sb.append("总数据点数: ").append(batteryDataList.size()).append("\n\n");

        sb.append("电量等级连续段统计:\n");
        sb.append("================================================\n");

        int segmentIndex = 0;
        int currentLevel = -1;
        int segmentStartIdx = 0;
        int minVoltage = Integer.MAX_VALUE;
        int maxVoltage = Integer.MIN_VALUE;

        for (int i = 0; i <= batteryDataList.size(); i++) {
            int level = (i < batteryDataList.size()) ? batteryDataList.get(i).level : -1;

            if (level != currentLevel) {
                // 输出上一段统计
                if (currentLevel != -1 && i > segmentStartIdx) {
                    BatteryDataPoint startPoint = batteryDataList.get(segmentStartIdx);
                    BatteryDataPoint endPoint = batteryDataList.get(i - 1);
                    long duration = endPoint.timestamp - startPoint.timestamp;
                    long durationMinutes = duration / 60000;
                    long durationSeconds = (duration % 60000) / 1000;

                    segmentIndex++;
                    sb.append(String.format("\n[段 %d] 电量等级 %d:\n", segmentIndex, currentLevel));
                    sb.append(String.format("  持续时间: %d分%d秒\n", durationMinutes, durationSeconds));
                    sb.append(String.format("  电压范围: %dmv - %dmv\n", minVoltage, maxVoltage));
                    sb.append(String.format("  时间范围: %s - %s\n",
                            timeFormat.format(new Date(startPoint.timestamp)),
                            timeFormat.format(new Date(endPoint.timestamp))));
                    sb.append(String.format("  数据点数: %d\n", i - segmentStartIdx));
                }

                // 开始新段
                currentLevel = level;
                segmentStartIdx = i;
                minVoltage = Integer.MAX_VALUE;
                maxVoltage = Integer.MIN_VALUE;
            }

            // 更新电压范围
            if (i < batteryDataList.size()) {
                int voltage = batteryDataList.get(i).voltage;
                if (voltage < minVoltage) minVoltage = voltage;
                if (voltage > maxVoltage) maxVoltage = voltage;
            }
        }

        return sb.toString();
    }

    /**
     * 生成电量图表 (x轴: 分钟, y轴: 电量等级)
     */
    private boolean generateBatteryChart(File outputFile) {
        if (batteryDataList.isEmpty()) {
            return false;
        }

        try {
            int width = 1200;
            int height = 600;
            int padding = 80;

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.drawColor(Color.WHITE);

            Paint axisPaint = new Paint();
            axisPaint.setColor(Color.BLACK);
            axisPaint.setStrokeWidth(2);
            axisPaint.setTextSize(24);

            Paint gridPaint = new Paint();
            gridPaint.setColor(Color.LTGRAY);
            gridPaint.setStrokeWidth(1);

            Paint linePaint = new Paint();
            linePaint.setColor(Color.BLUE);
            linePaint.setStrokeWidth(3);
            linePaint.setAntiAlias(true);

            Paint pointPaint = new Paint();
            pointPaint.setColor(Color.RED);
            pointPaint.setAntiAlias(true);

            Paint titlePaint = new Paint();
            titlePaint.setColor(Color.BLACK);
            titlePaint.setTextSize(28);
            titlePaint.setFakeBoldText(true);

            // 绘制标题
            canvas.drawText("电量等级变化图", width / 2 - 100, 40, titlePaint);

            // 计算数据范围
            long minTime = batteryRecordStartTime;
            long maxTime = batteryDataList.get(batteryDataList.size() - 1).timestamp;
            long totalDuration = maxTime - minTime;
            double maxMinutes = totalDuration / 60000.0;

            int maxLevel = 0;
            for (BatteryDataPoint p : batteryDataList) {
                if (p.level > maxLevel) maxLevel = p.level;
            }
            maxLevel = Math.max(maxLevel + 1, 5); // 至少显示到等级5

            int chartWidth = width - 2 * padding;
            int chartHeight = height - 2 * padding;

            // 绘制坐标轴
            canvas.drawLine(padding, height - padding, width - padding, height - padding, axisPaint); // X轴
            canvas.drawLine(padding, height - padding, padding, padding, axisPaint); // Y轴

            // 绘制X轴标签 (分钟)
            canvas.drawText("时间 (分钟)", width / 2 - 40, height - 20, axisPaint);
            int xLabels = Math.min((int) Math.ceil(maxMinutes), 10);
            for (int i = 0; i <= xLabels; i++) {
                float x = padding + (float) i / xLabels * chartWidth;
                double minute = i * maxMinutes / xLabels;
                canvas.drawLine(x, height - padding, x, height - padding + 10, axisPaint);
                canvas.drawText(String.format("%.1f", minute), x - 15, height - padding + 35, axisPaint);
            }

            // 绘制Y轴标签 (电量等级)
            canvas.save();
            canvas.rotate(-90, 30, height / 2);
            canvas.drawText("电量等级", 0, height / 2, axisPaint);
            canvas.restore();

            for (int i = 0; i <= maxLevel; i++) {
                float y = height - padding - (float) i / maxLevel * chartHeight;
                canvas.drawLine(padding - 10, y, padding, y, axisPaint);
                canvas.drawText(String.valueOf(i), padding - 35, y + 8, axisPaint);
                // 绘制网格线
                if (i > 0) {
                    canvas.drawLine(padding, y, width - padding, y, gridPaint);
                }
            }

            // 绘制数据点和连线
            if (batteryDataList.size() >= 2) {
                for (int i = 0; i < batteryDataList.size() - 1; i++) {
                    BatteryDataPoint p1 = batteryDataList.get(i);
                    BatteryDataPoint p2 = batteryDataList.get(i + 1);

                    float x1 = padding + (float) ((p1.timestamp - minTime) / (double) totalDuration * chartWidth);
                    float y1 = height - padding - (float) p1.level / maxLevel * chartHeight;
                    float x2 = padding + (float) ((p2.timestamp - minTime) / (double) totalDuration * chartWidth);
                    float y2 = height - padding - (float) p2.level / maxLevel * chartHeight;

                    canvas.drawLine(x1, y1, x2, y2, linePaint);
                    canvas.drawCircle(x1, y1, 5, pointPaint);
                }
                // 绘制最后一个点
                BatteryDataPoint last = batteryDataList.get(batteryDataList.size() - 1);
                float lastX = padding + (float) ((last.timestamp - minTime) / (double) totalDuration * chartWidth);
                float lastY = height - padding - (float) last.level / maxLevel * chartHeight;
                canvas.drawCircle(lastX, lastY, 5, pointPaint);
            } else if (batteryDataList.size() == 1) {
                BatteryDataPoint p = batteryDataList.get(0);
                float x = padding + chartWidth / 2f;
                float y = height - padding - (float) p.level / maxLevel * chartHeight;
                canvas.drawCircle(x, y, 8, pointPaint);
            }

            // 保存图片
            FileOutputStream fos = new FileOutputStream(outputFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();
            bitmap.recycle();

            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
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

    private String bytesToBattery(byte[] bytes) {
        if (bytes.length < 4) {
            return "数据长度不足（需要4字节）";
        }
        // 数据帧：电压高八位 电压低八位 电量百分比 电量等级
        int voltageHigh = bytes[0] & 0xFF;
        int voltageLow = bytes[1] & 0xFF;
        int voltage = (voltageHigh << 8) | voltageLow;
        int percentage = bytes[2] & 0xFF;
        int level = bytes[3] & 0xFF;

        // 记录电量数据用于分析
        long currentTime = System.currentTimeMillis();
        if (batteryRecordStartTime == 0) {
            batteryRecordStartTime = currentTime;
        }
        batteryDataList.add(new BatteryDataPoint(currentTime, voltage, percentage, level));

        return String.format("%dmv %d%% 电量等级%d", voltage, percentage, level);
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
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }
}
