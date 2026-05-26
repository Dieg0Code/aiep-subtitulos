# AIEP Audio Streaming Skill

Use this skill when editing the Android microphone client for AIEP Subtitulos.

Rules:

- Use Kotlin and Jetpack Compose.
- Start microphone capture only after an explicit user action.
- Keep capture inside a `ForegroundService` with `foregroundServiceType="microphone"`.
- Show a persistent notification while the microphone is active.
- Capture PCM with `AudioRecord`, mono, signed 16-bit little-endian, 16 kHz.
- Send audio frames as WebSocket binary messages; never base64 encode audio.
- Use JSON only for metadata, status, and future control messages.
- Preserve compatibility with the current relay contract: Android connects as guest to `/ws/guest?s=<SESSION_ID>`.
- Do not store audio unless the user explicitly consents.
- Prefer robust reconnection, logs, and physical-device validation over complex UI.

References:

- `references/protocol.md`
- `references/foreground-service.md`
- `references/relay-contract.md`
