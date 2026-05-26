# AIEP Subtitulos Android

Android native microphone client for AIEP Subtitulos v0.2.

This replaces the mobile browser when the teacher needs robust microphone capture with the phone locked.

## Stack

- Kotlin
- Jetpack Compose
- ForegroundService with `foregroundServiceType="microphone"`
- `AudioRecord` PCM mono 16 kHz
- OkHttp WebSocket
- Physical Android device via ADB

## Build

From this folder:

```powershell
.\gradlew.bat assembleDebug
```

With Android CLI:

```powershell
android describe --project_dir .
```

If `adb` is not in PATH on this machine, use the installed SDK path:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
.\gradlew.bat installDebug
```

After `assembleDebug`, Android CLI can locate the APK:

```powershell
android run --apks app\build\outputs\apk\debug\app-debug.apk
```

## Run

1. Open the desktop app and copy the 6-character session ID from the QR/mobile URL.
2. Open the Android app.
3. Enter the session ID.
4. Keep the default relay unless testing a local relay.
5. Tap `Iniciar microfono`.
6. Accept microphone and notification permissions.

## Physical Device Validation

Use a real phone, not an emulator:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" logcat | Select-String AiepSubtitulos
```

Test:

1. Start the microphone in the Android app.
2. Lock the phone.
3. Speak for 2 minutes.
4. Verify the desktop app continues receiving audio bytes/captions.
5. Check logcat for connection or `AudioRecord` errors.

## Current Relay Contract

The app connects as guest:

```txt
wss://aiep-relay-production.up.railway.app/ws/guest?s=<SESSION_ID>
```

It sends raw binary PCM frames. No base64 is used.
