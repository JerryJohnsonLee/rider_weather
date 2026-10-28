# Ride Weather

Ride Weather is a Wear OS app and tile that checks hourly weather at the places on a planned motorcycle round trip. Any non-zero precipitation chance at a planned point on either leg results in **NO RIDE TODAY**.

## Install on a Samsung Galaxy Watch

Download `Ride-Weather-v1.0.apk` from the project’s GitHub **Releases** page. The APK is a debug build for direct installation; it is not a Play Store release.

### 1. Prepare the watch

Connect the watch and computer to the same Wi-Fi network. On the watch:

1. If Developer options are hidden, open **Settings → About watch → Software** and tap **Software version** five times. Samsung’s menu wording can vary slightly by One UI Watch version.
2. Open **Settings → Developer options** and enable **ADB debugging** and **Wireless debugging**.
3. Approve the prompt to allow debugging on this Wi-Fi network.

### 2. Pair the computer once

On the watch, open **Settings → Developer options → Wireless debugging → Pair new device**. Note the displayed IP address, pairing port, and pairing code. Keep this pairing screen open while entering the code.

In PowerShell, set `$adb` to your Android SDK `platform-tools\adb.exe` (the path below is Android Studio’s usual Windows location), then pair:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$pairAddress = "WATCH_IP:PAIRING_PORT"
& $adb pair $pairAddress
```

When prompted, enter the pairing code shown on the watch. Pairing is normally needed only once per computer.

### 3. Connect and install

On the watch’s main **Wireless debugging** screen, note its current IP address and connection port. This connection port is different from the pairing port and can change when wireless debugging restarts.

```powershell
$connectionAddress = "WATCH_IP:CONNECTION_PORT"
& $adb connect $connectionAddress
& $adb devices -l
```

Download `Ride-Weather-v1.0.apk` from Releases. From the folder where it was downloaded, install it:

```powershell
& $adb -s $connectionAddress install -r ".\Ride-Weather-v1.0.apk"
```

If `adb devices -l` shows more than one device, keep the `-s $connectionAddress` selector so the APK goes to the watch. If the watch is listed as `offline`, reconnect using the current connection port shown on the watch.

To add the tile, open the watch’s tile carousel, choose **Add tiles**, and select **Ride Weather**. The exact tile-edit gesture varies by One UI Watch version.

### Troubleshooting

- Confirm the computer and watch are on the same Wi-Fi network. Some guest or managed networks block device-to-device traffic; try another Wi-Fi network or a mobile hotspot if `adb connect` times out.
- Recheck the connection port on the main Wireless debugging screen. Do not use the pairing port with `adb connect`.
- If ADB is stuck, restart it and reconnect:

  ```powershell
  & $adb kill-server
  & $adb start-server
  & $adb connect $connectionAddress
  & $adb devices -l
  ```

## Build from source

Use JDK 17 and install the Android SDK platform/build tools required by the project. From PowerShell in the repository directory:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is written to `app\build\outputs\apk\debug\app-debug.apk`.

## How the forecast works

- Place names are geocoded with the Open-Meteo Geocoding API; hourly forecast data comes from Open-Meteo.
- The app samples the forecast at each stop’s planned outbound and return time. It does not calculate a road route or travel time.
- The recommendation checks both legs. Any sampled precipitation probability above 0% means **NO RIDE TODAY**.
- Temperature uses the selected °C/°F display unit. Wind and gusts are shown in mph.
- Forecasts refresh manually or around 8:00 AM watch-local time when the watch has internet. WorkManager may delay background updates for network or battery conditions.

## References

- [Samsung: Connect Galaxy Watch with Android Studio over Wi-Fi](https://developer.samsung.com/health/sensor/guide/connect-watch.html)
- [Android Developers: Debug Wear OS over Wi-Fi](https://developer.android.com/training/wearables/get-started/debug-wifi)
