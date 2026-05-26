# Android Foreground Microphone Service

The microphone stream must run in a foreground service.

Required manifest pieces:

- `android.permission.RECORD_AUDIO`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- `android.permission.POST_NOTIFICATIONS` on Android 13+
- service attribute `android:foregroundServiceType="microphone"`

Operational rules:

- The app requests microphone permission from the foreground activity.
- The user taps the start button before capture begins.
- The service calls `startForeground` immediately with an ongoing notification.
- The service releases `AudioRecord`, WebSocket, and wake lock when stopped.
