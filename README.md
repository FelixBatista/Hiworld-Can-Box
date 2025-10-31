# TEYES Phase B - CAN Listener (Flutter + Android native)

Read-only listener for TEYES/HiWorld CAN broadcasts exposed as Android Intents. Shows a live list of messages and a header with RPM and Speed when found.

## Features

- Dynamic Android BroadcastReceiver subscribes to a list of candidate TEYES/HiWorld CAN Intent action strings
- Forwards full Intent extras to Flutter via an EventChannel as a `Map<String, Object>`
- Normalizes common keys to `rpm` and `speed` when found
- Flutter UI shows most-recent-first log (capped to 200) and a header with RPM/Speed

## Building/Running

1. Connect the TEYES CC2 (or test Android device) with USB debugging enabled
2. From project root run:
   - `flutter pub get`
   - `flutter run`

Android min API 28+, Flutter 3.x.

## Emulator/Device test mode (no TEYES connected)

You can simulate CAN updates on any Android device/emulator:

1. Launch the app: `flutter run`
2. Tap the Play icon in the top-right (Start Test) to begin emitting synthetic broadcasts every 500 ms
3. The header will show changing RPM/Speed and the list will populate with synthetic messages
4. Tap the Stop icon to stop the generator

Implementation notes:
- The Flutter UI toggles a `MethodChannel('teyes_can_control')` calling `startTest`/`stopTest`.
- Native side sends an `Intent` using the first entry in `candidateActions`, with extras like `rpm`, `speed`, `gear`, etc. The same dynamic `BroadcastReceiver` receives it, exercising the full pipeline.
- If broadcast sending fails, it falls back to emitting directly to the `EventChannel`.

## How to discover the correct broadcast action names (adb logcat)

Action names vary by firmware. Use `adb logcat` to discover which broadcasts your unit emits and then add them to the Kotlin `candidateActions` list.

1. On the device, reproduce CAN activity (e.g., start/drive, change speed/RPM)
2. On your computer, run one or more of the following:

```bash
adb logcat | grep -iE "teyes|hiworld|can|canbus|rzc|roadrover|syu"

# Alternatively, dump broadcasts of interest while driving
adb shell "dumpsys activity broadcasts" | sed -n '1,200p'

# If you suspect a package, list its broadcasts
adb shell cmd package resolve-activity --brief com.teyes.*
```

Look for lines mentioning an `Intent { act=... }`. Copy the `act=` value(s) and add them to `candidateActions` in `android/app/src/main/kotlin/.../TeyesBroadcastService.kt`.

## Where to extend candidate actions

Open:

`android/app/src/main/kotlin/com/example/hiworld_can_box/TeyesBroadcastService.kt`

Update the `candidateActions` list. Example placeholders:

```text
com.teyes.canbus.DATA
com.hiworld.canbus.DATA
com.hiworld.teyes.CAN_DATA
ru.teyes.can.ACTION_CAN_DATA
com.teyes.canbus.CAN_INFO
com.hiworld.teyes.intent.action.CAN_INFO
com.android.teyes.canbus.ACTION
com.roadrover.canbus.ACTION_DATA
com.syu.ms.action.CANBUS
com.hiworld.can.CAN_INFO
```

Keep only the ones you confirm in logcat; add new ones as discovered.

## Permissions

`AndroidManifest.xml` includes `INTERNET` and `BLUETOOTH` for future extensibility. No special permission is required to receive exported broadcasts. If your head unit requires modern Bluetooth permissions (API 31+), you may later add `BLUETOOTH_CONNECT/SCAN` as needed.

## Notes

- This is Phase B: read-only. No write-to-CAN functionality is implemented.
- Event channel name: `teyes_can_stream`
- The native bridge copies all extras and adds `action` and `timestamp`. It also tries to expose `rpm` and `speed`.
