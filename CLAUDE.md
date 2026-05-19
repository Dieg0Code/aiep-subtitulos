# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```powershell
npm install                            # install JS deps
npm run tauri dev                      # run desktop app + Vite dev server + relay WS client
npm run build                          # tsc + vite build (typecheck + frontend bundle)
npm run tauri build                    # produce installer in src-tauri/target/release/bundle
cd src-tauri; cargo check              # verify Rust compiles without a full build
cmake --version                        # required by whisper.cpp build
rustup target list --installed         # Windows Whisper builds should prefer x86_64-pc-windows-msvc

cd server; go test -count=1 ./...      # Go relay tests
cd server; go run .                    # run relay locally on :8080 (override with PORT=)
cd server; go vet ./...                # static analysis

railway up --service aiep-relay        # manual relay deploy (auto-deploy on push to main is also wired)

git tag app-v0.X.Y; git push origin app-v0.X.Y   # trigger Windows installer release (draft) via GitHub Actions
```

No test runner is configured for the Tauri side. `npm run build` is the only frontend typecheck — no separate lint script.

Dev ports started by `npm run tauri dev`:
- `1420` — Vite dev server (Tauri webview source). The Rust side no longer hosts an HTTPS server.

## Architecture

Two components:
1. **Tauri desktop app** in `src/` (TS/Vite) and `src-tauri/` (Rust). The Rust side acts purely as a WebSocket **client** of a remote relay, plus owns the overlay window.
2. **Go WebSocket relay** in `server/`, deployed on Railway. It accepts host (PC) and guest (phone) connections, hands out short session IDs, and forwards all frames (text JSON + binary audio) between them.

```
phone Chrome ─wss audio opus──> Railway Go relay ─wss───> Tauri Rust (client)
                                  /ws/host emits             routes frames to events:
                                  {"type":"session",         caption-update / phone-status
                                   "id":"ABCDEF"}            mobile-url-update / audio-bytes
                                                             relay-status
                                                                |
                                                                v
                                                          Tauri webview (src/main.ts)
                                                          QR + preview + overlay
```

### Tauri side (`src-tauri/`)

- **Whisper F2 simple**: `src/whisper.rs` looks for `ggml-base.bin` at `app_data_dir()/models/ggml-base.bin` (Windows: `%APPDATA%\cl.aiep.subtitulos\models\ggml-base.bin`). If missing, it downloads the model from Hugging Face, emits `whisper-download-progress`, keeps mobile mode on `speech`, then loads `whisper-rs-sys` CPU-only and transcribes PCM windows of 2.5s with 0.5s overlap, `language=es`, `n_threads=2`, `single_segment=true`. It emits `caption-update` and `whisper-status`. If download or load fails, mobile mode falls back to `speech`.
- `mobile_url` now includes `mode=pcm|speech`; `pcm` is used only after local Whisper is ready.

- **`src/lib.rs`** — bootstrap. `setup()` initializes `tracing_subscriber`, creates `Arc<RelayState>`, calls `spawn_relay_client(...)`, and positions the overlay. Exposes Tauri commands: `mobile_url` (read cached URL), `show_overlay` / `hide_overlay` / `close_overlay` / `reset_overlay_position`. Window-close on `main` exits the app.
- **`src/relay.rs`** — owns the WS client. `relay_config_from_env()` reads `AIEP_RELAY_URL` (default `https://aiep-relay-production.up.railway.app`) and derives the `wss://.../ws/host` URL. `spawn_relay_client(...)` runs a `tauri::async_runtime::spawn` loop with exponential backoff (1s→30s) and a 30s heartbeat ping. Frame routing inside `run_session`:
  - Text `{type:"session", id}` → store ID, build `<base>/m?s=<id>`, emit `mobile-url-update`.
  - Text `{type:"status", state}` → emit `phone-status` with `"connected"`/`"disconnected"`.
  - Text `{kind:"status", text}` (pass-through from guest) → emit `phone-status` with the raw text (used for `chunks:N` updates).
  - Text `{kind:"caption", text, isFinal}` → emit `caption-update` (used by guest's manual-text backup textarea).
  - Binary → emit `audio-bytes` with `data.len() as u64`.
- The Rust crate **no longer hosts a local HTTPS server**: `axum`, `axum-server`, `local-ip-address`, `rcgen` are removed. `cloudflared` subprocess management is gone. The old `MOBILE_HTML` constant lives only in `server/mobile.html` now.

### Frontend (`src/`)

- **`src/main.ts`** — single entry, branches on `location.pathname` between `renderControl` (main window) and `renderOverlay` (overlay window).
  - `wireCaptionListeners` subscribes to: `caption-update`, `phone-status`, `mobile-url-update`, `relay-status`, `whisper-status`, `whisper-download-progress`, `audio-bytes`.
  - `updateRelayStatus(status)` (`"connecting"`/`"online"`/`"reconnecting"`) updates `#qr-status` with proper colors via `data-state` attribute.
  - `updateWhisperStatus(status)` (`"missing-model"`/`"loading"`/`"downloading-model"`/`"ready"`/`"transcribing"`/`"error"`) updates the engine UI and fallback messaging.
  - `mobile_url` invoke at startup is just a one-shot read; the source of truth is the `mobile-url-update` event which fires on each new session.
  - Transcript log and settings persist only in `localStorage` (`aiep-subtitulos.transcript`, `aiep-subtitulos.settings`); transcript array capped at 500 entries.

### Go relay (`server/`)

- **`server/mobile.html` capture modes**: reads `mode=pcm|speech` from the QR URL. PCM mode uses `AudioWorklet` to send 16 kHz i16 mono chunks over binary WS for local Whisper. Speech mode uses Web Speech and sends `{kind:"caption"}` JSON as fallback.

- **`server/main.go`** — HTTP routing (`/healthz`, `/m`, `/ws/host`, `/ws/guest`), embeds `mobile.html` via `//go:embed`, graceful shutdown.
- **`server/hub.go`** — session store. Generates 6-char IDs from a 30-char alphabet (no `0/O/1/I/L` confusion). Pairing model: host creates session on connect, guest joins via `?s=<id>`. Concurrent writes to the host conn (initial `session` msg + guest's status/relay) are serialized via `Session.WriteHost` (mutex-guarded). Binary and text frames pass through untouched. Max frame size 1 MiB.
- **`server/mobile.html`** — the embedded phone UI. PCM mode captures 16 kHz i16 mono chunks with `AudioWorklet` and sends binary WS to `/ws/guest?s=<id>`; speech mode uses Web Speech and sends caption JSON as fallback. Includes silent-audio AudioContext loop + wake lock + visibility-restart to survive screen-off as best as a browser can.
- **`server/hub_test.go`** — 10 unit tests covering ID generation, pairing, relay direction, second-guest rejection (1008), 404/400 routing, and host-disconnect cleanup. CI runs these with `-race` on Linux.

### CI/CD

- **`.github/workflows/relay-ci.yml`** runs `go vet`, `go test -race`, `go build` on PRs and pushes touching `server/**`.
- **`.github/workflows/release.yml`** builds the NSIS Windows installer on `windows-latest`. Triggers: PRs touching `src/**`, `src-tauri/**`, `package*.json`, `vite.config.*`, `tsconfig*.json` (build → `.exe` uploaded as a downloadable run artifact, no release) and `push` of tag `app-v*` (build → GitHub Release draft with the `.exe` attached). Version is driven by the tag: a PowerShell step rewrites `version` in `src-tauri/tauri.conf.json` and `package.json` inside the runner before `tauri-action@v0` runs, so the repo files stay untouched. NSIS uses `webviewInstallMode: embedBootstrapper` — installer adds ~1.8 MB and downloads WebView2 at install time if missing. No code signing — SmartScreen warning is expected and documented in the release body.
- **Railway auto-deploy**: dashboard → service `aiep-relay` → Settings → Source connected to repo, Root Directory `server/`. Each push to `main` rebuilds the Dockerfile and redeploys.

## Event/command contract

| Command (`#[tauri::command]`) | Returns | Notes |
|---|---|---|
| `mobile_url` | `String` | Cached URL or `""` until first session; includes `mode=pcm|speech` |
| `whisper_model_path` | `Result<String, String>` | Expected `ggml-base.bin` path |
| `whisper_status` | `String` | Cached local Whisper state |
| `show_overlay` / `hide_overlay` / `close_overlay` / `reset_overlay_position` | `Result<&'static str, String>` | Overlay window controls |

| Event (Rust → TS) | Payload | Trigger |
|---|---|---|
| `mobile-url-update` | `string` | New session ID arrived; `""` means we lost the connection |
| `phone-status` | `string` | `"connected"`, `"disconnected"`, or guest pass-through like `"chunks:42"` |
| `caption-update` | `{ text, isFinal }` (camelCase) | Guest manual-text backup |
| `audio-bytes` | `number` | Bytes in the latest binary chunk |
| `relay-status` | `"connecting" \| "online" \| "reconnecting"` | WS client state transitions |
| `whisper-status` | `"missing-model" \| "loading" \| "downloading-model" \| "ready" \| "transcribing" \| "error"` | Local Whisper lifecycle |
| `whisper-download-progress` | `{ downloaded, total? }` | First-run model download progress in bytes |

## Conventions worth knowing

- The phone-side JS in `mobile.html` sends `{kind: ...}` (camelCase); the relay metadata uses `{type: ...}`. Rust's `relay.rs` discriminates on which field is present.
- Connection settings live in `localStorage` (`saveTranscript`, `speechEngine`). The old `connectionMode` field is silently ignored — no migration needed.
- All Rust logs use `tracing` (`info!`, `warn!`). Filter via `RUST_LOG`.
- Local Whisper builds through `whisper-rs-sys`, so Windows development needs CMake, MSVC Build Tools, and a visible `libclang` (for example MSYS2 clang with `LIBCLANG_PATH=C:\msys64\mingw64\bin`).
- The mobile URL is always relay-hosted now. There is no LAN path, no self-signed cert, no `cloudflared`. If the user reports "AP isolation" or "self-signed warning" they're reading old docs.
- UI copy is Spanish (Chilean: `es-CL`).
- The relay's protocol is intentionally minimal: no auth, no rate limiting, no message inspection. Pairing happens purely via the 6-char session ID, which is ~729M combinations. Acceptable for MVP / teacher use; not for adversarial environments.
