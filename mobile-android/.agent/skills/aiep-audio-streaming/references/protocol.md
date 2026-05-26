# Audio Protocol

Current v0.2 behavior:

- Connect to the relay guest WebSocket.
- Send PCM audio as binary WebSocket frames.
- PCM format: `pcm_s16le`, 16000 Hz, mono.
- Frame target: about 100 ms, 3200 bytes.
- Optional text status messages use JSON:

```json
{"kind":"status","text":"android:connected"}
```

Future metadata frame shape:

```json
{
  "type": "audio.frame",
  "sessionId": "ABC123",
  "codec": "pcm_s16le",
  "sampleRate": 16000,
  "channels": 1,
  "sequence": 1024,
  "timestampMs": 18237711
}
```
