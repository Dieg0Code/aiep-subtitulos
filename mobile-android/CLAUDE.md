# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

This file scopes the **`mobile-android/`** module. The repo-root `../CLAUDE.md` describes the relay + Tauri desktop side and the wire contract — read it for anything that crosses the WS boundary.

## Commands

```powershell
.\gradlew.bat assembleDebug                       # build debug APK -> app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat installDebug                        # install on connected device
.\gradlew.bat test                                # JVM unit tests (src/test) — no instrumentation tests configured
.\gradlew.bat test --tests "*CaptionProcessorTest*"   # single test class
.\gradlew.bat :app:lint                           # AGP lint (lint check `Instantiatable` is disabled in app/build.gradle.kts)

& "$env:ANDROID_HOME\platform-tools\adb.exe" logcat -s AiepSubtitulos     # filtered runtime logs (LOG_TAG)
```

Physical device only — emulators can't validate the locked-screen / battery-optimization scenarios this module exists for. Validate every behaviour-touching change with the phone screen locked for 2+ minutes.

## Architecture

This module is an **additive** native guest for the same relay the browser `mobile.html` uses. The browser QR flow must keep working; this app is not a replacement.

```
MainActivity (Compose NavHost)
  ├── Sessions list / detail / Settings screens (ui/)
  ├── SessionsRepository (DataStore-backed)  ── sessions/
  ├── QR scan via Play Services code-scanner ── qr/QrParser
  └── startForegroundService → MicrophoneStreamingService
                                 │
                                 ├── CaptureEngine (interface)        ── audio/
                                 │     ├── PcmCaptureEngine     (AudioRecord 16 kHz mono i16)
                                 │     └── SpeechCaptureEngine  (Android SpeechRecognizer; emits caption JSON)
                                 │
                                 └── AudioTransport (interface)       ── transport/
                                       ├── CloudRelayTransport  (OkHttp WS → wss://…/ws/guest?s=<id>)
                                       └── LocalOnlyTransport   (no network; on-device storage path)
```

Key wiring rules:

- The service is started via `MicrophoneStreamingService.startIntent(...)`; it requires `RECORD_AUDIO` already granted (MainActivity handles the permission dance, including `POST_NOTIFICATIONS` on Tiramisu+). The service self-stops if RECORD_AUDIO is missing or `sessionId` is blank in non-local-only mode.
- **`foregroundServiceType="microphone"`** + a wake lock are mandatory for locked-screen capture. Don't remove either.
- `CaptureMode` (in `StreamingConfig.kt`) is `Speech` (default) or `Pcm`. PCM mode streams raw binary i16 frames; Speech mode streams `{kind:"caption", text, isFinal}` JSON. This mirrors the browser `mobile.html` contract — keep `kind` (camelCase) on outgoing JSON; relay/PC metadata uses `type`.
- `LocalOnlyTransport` bypasses the relay entirely (sessions captured + stored on-device, exported via `export/` PDF/AI flow). When `localOnly=true` the service forces `CaptureMode.Speech` and uses `LOCAL_ONLY_SESSION_ID`.
- `ActiveSessionTracker` + `SessionsRepository.updateSessionMode(...)` tie a streaming run to a row in the on-device sessions list. The "session ID" the user types/scans is the **relay** session (6 chars from `RELAY_ID_ALPHABET`); the local row has its own UUID (`localSessionId`).
- Caption pacing/preview goes through `CaptionPacer` + `CaptionPreviewBus` + `CaptionProcessor` — these are the only pieces under unit test (`src/test/java/.../audio/CaptionProcessorTest.kt`, `.../export/StudyExportTest.kt`). Pure-Kotlin logic belongs here so it stays JVM-testable.
- **Export (`export/`)**: "Descargar material" opens `ExportOptionsSheet` (in `SessionDetailScreen.kt`) to pick contenido (crudo / mejorado con IA) × formato (PDF / Word). The same study Markdown source (`StudyMarkdown.raw(...)` or `AiProviderClient.createStudyMarkdown(...)`) feeds both formats. **PDF** is rendered from a single HTML/CSS template (`assets/study_template.html` → `StudyHtmlTemplate` → `MarkdownToHtml` → `HtmlPdfRenderer`, an offscreen WebView printed to file via the `android.print.WebViewPdfPrinter` Java shim — the callbacks' ctors are package-private so the shim *must* stay in package `android.print`). **Word** is a hand-built OOXML zip (`StudyDocxGenerator` + pure `DocxXml`), no dependency. Both embed the AIEP brand (`assets/logo_aiep.png`, Ubuntu in `assets/fonts/`) and share via `PdfShare.share(..., mimeType)`. New cache dirs need a `<cache-path>` in `res/xml/file_paths.xml`. Pure pieces (`MarkdownToHtml`, `DocxXml`, `StudyPdfFileName`) are unit-tested; WebView/zip are device-verified.
- Prefs live in `prefs/AppPreferences.kt` (DataStore Preferences). Expose as a `Flow`; collect with `collectAsStateWithLifecycle`.

## Conventions

- Package root: `cl.aiep.subtitulos`. `applicationId` and `namespace` match. `minSdk=23`, `targetSdk=34`, `compileSdk=35`, Kotlin JVM toolchain 17.
- Release uses the debug signing config on purpose (internal distribution only — no Play Store). Don't add a real signing config without asking.
- Logs: `Log.x(LOG_TAG, ...)` with `LOG_TAG = "AiepSubtitulos"` (single tag for the whole app — easy to filter via logcat).
- UI copy is Spanish (Chilean). Strings live in `app/src/main/res/values/strings.xml`; never hardcode user-facing text in Kotlin.
- Don't introduce a second mic capture path. If you need a new transport or capture mode, add an implementation of `AudioTransport` / `CaptureEngine` and select it in `MicrophoneStreamingService.onStartCommand`.
- The native app is additive: never change behaviour that would break the existing browser QR + `mobile.html` flow served by the relay (see [[feedback_no_regressions_qr_flow]]).
