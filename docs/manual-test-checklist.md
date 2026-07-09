# Manual Test Checklist

Hardware-dependent checks require an Android device on API 29 or newer, Bluetooth enabled, and two BLE peripherals advertising names that start with `CM-` or `x_skiing`.

## Permissions and Scan

1. Install a release APK on the Android device.
2. Launch the app.
3. Deny the requested Bluetooth/location permissions once and verify the app blocks scanning with clear permission text.
4. Grant the requested permissions and verify the Scan screen starts scanning.
5. Turn Bluetooth off and verify scanning is blocked by the Bluetooth prompt.
6. Turn Bluetooth on and verify the Scan screen resumes readiness.
7. Verify only devices whose names start with `CM-` or `x_skiing` appear in the list.

## Multi-device BLE Flow

1. Keep scanning active and connect two filtered devices from the Scan screen.
2. Verify each scan-list row shows an independent state: `连接中` while connecting, then `已连接` after service setup.
3. Navigate to the global Live screen and verify both connected devices are listed with their name and MAC address.
4. For each device, tap `初始化传感器` and verify the UI reports all three sensors initialized: 六轴, 地磁, and 气压计.
5. Set one device to `左脚` and the other to `右脚`; verify each device shows its selected data source independently.
6. Tap `开始采集` for both devices. Verify Start is disabled while collecting and Stop remains available per device.
7. Verify incoming packets from both devices appear in one time-ordered Live list as two-line records:
   ```text
   [15:44:22.726] 数据 -- x_skiing=3F:89:E5:1E:2A:EF
   HEX: BE BB 42 AD BA FF 9B 07 D8 FF FE FF 05 00 FF FF 9B
   ```
8. Tap Start recording and verify the Live screen displays one active aggregate filename such as `ble-session_20260707_142530.txt`.
9. Keep both devices collecting and verify the record count increases for packets from either device.
10. Tap `停止采集` for each device and verify each device leaves collecting state independently.
11. Tap Stop recording and verify recording finalizes without crashing.
12. Open Android Downloads and verify one `ble-session_*.txt` file exists for the session.
13. Open the file and verify records from both devices are saved in receive order in the same file, preserving the exact two-line screen format with `数据 --`, `deviceName=MAC`, and uppercase space-separated `HEX:` bytes.
14. Disconnect one device and verify the other remains connected and visible.
15. Tap Disconnect All and verify all device rows leave connected/collecting state.

## Command/Response Spot Checks

Use BLE peripheral logs or a BLE sniffer when available.

1. Initialization sends exactly `BE BB 02 00 01`, `BE BB 02 00 02`, and `BE BB 02 00 03`.
2. Successful initialization responses are exactly `BB BE 02 00 11`, `BB BE 02 00 21`, and `BB BE 02 00 31`.
3. Left data source sends `BE BB 02 01 01` and accepts `BB BE 06 05 41`.
4. Right data source sends `BE BB 02 01 02` and accepts `BB BE 06 05 42`.
5. Start collection sends `BE BB 02 03 01`; if `BB BE 02 00 00` is returned, verify the UI reports `有传感器未初始化成功`.
6. Stop collection sends `BE BB 02 03 02`.
7. Incoming collection data frames are not parsed into sensor fields; they are displayed and saved unchanged as HEX records.

## Automated Verification

Run these from the repository root:

```bash
ANDROID_HOME=/tmp/android-sdk ./gradlew :app:testDebugUnitTest
ANDROID_HOME=/tmp/android-sdk ./gradlew :app:assembleDebug :app:assembleRelease
```

Expected release APK output:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```
