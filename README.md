# Bluetooth SPP Communication App

Android 应用，支持通过 SPP（串口协议）与蓝牙设备通信。

## 功能
- 扫描已配对的蓝牙设备
- 连接蓝牙设备（SPP）
- 发送和接收数据
- 实时显示通信日志
- 保存日志到 txt 文件

## 构建
### 本地构建
```bash
./gradlew assembleRelease
```

### GitHub Actions 自动构建
推送代码到 GitHub 后，Actions 会自动构建 APK，可在 Actions 页面下载。

## 使用
1. 打开应用
2. 点击"Scan Devices"扫描已配对设备
3. 点击设备连接
4. 在输入框输入数据，点击"Send"发送
5. 接收的数据会实时显示
6. 点击"Save Log"保存日志到 Documents/BluetoothSPP/

## 权限
需要蓝牙和存储权限，首次运行时会请求授权。
