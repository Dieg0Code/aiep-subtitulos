# Relay Contract

Default base URL:

```txt
https://aiep-relay-production.up.railway.app
```

Android native app acts as guest:

```txt
wss://aiep-relay-production.up.railway.app/ws/guest?s=<SESSION_ID>
```

The desktop app acts as host:

```txt
wss://aiep-relay-production.up.railway.app/ws/host
```

The Go relay forwards guest binary frames directly to the host. The Tauri host already counts `audio-bytes` and passes bytes to local Whisper.
