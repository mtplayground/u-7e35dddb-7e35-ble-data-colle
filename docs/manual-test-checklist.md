# Manual Test Checklist

Issue #18 verification covers the hardware-dependent BLE flow plus release build output.

## Required Device Pass

Use an Android device on API 29 or newer with Bluetooth enabled and a BLE peripheral advertising a name that starts with `CM-` or `x_skiing`.

1. Install a release APK on the Android device.
2. Launch the app.
3. Deny the requested Bluetooth/location permissions once and verify the app blocks scanning with clear permission text.
4. Grant the requested permissions and verify the Scan screen starts scanning.
5. Turn Bluetooth off and verify scanning is blocked by the Bluetooth prompt.
6. Turn Bluetooth on and verify the Scan screen resumes readiness.
7. Verify only devices whose names start with `CM-` or `x_skiing` appear in the list.
8. Tap a filtered device and verify the Live screen opens.
9. Verify the app connects, requests MTU `512`, discovers services, and subscribes to the first notifiable characteristic.
10. Verify incoming packets appear in the Live list as two-line records:
   ```text
   [15:44:22.726] 数据 -- x_skiing=3F:89:E5:1E:2A:EF
   HEX: BE BB 42 AD BA FF 9B 07 D8 FF FE FF 05 00 FF FF 9B
   ```
11. Verify recording starts automatically after connection and displays the active filename.
12. Tap Stop and verify recording finalizes without crashing.
13. Open Android Downloads and verify a file named like `x_skiing_20260707_142530.txt` exists.
14. Open the file and verify every saved record exactly matches the two-line screen format, including `数据 --`, `deviceName=MAC`, and uppercase space-separated `HEX:` bytes.
15. Turn Bluetooth off during a live session and verify the Live screen shows a clear disconnect/Bluetooth-off message.

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

Issue #18 local release build output:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
size: 19M
sha256: f5a8cc7371b8d16a6f6f101684f05ce12a39735b605f120027255910c8f2469f
```
