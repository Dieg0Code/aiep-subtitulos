# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```powershell
npm install                            # install JS deps
npm run tauri dev                      # run desktop app + Vite dev server + relay WS client
npm run build                          # tsc + vite build (typecheck + frontend bundle)
npm run tauri build                    # produce installer in src-tauri/target/release/bundle
cd src-tauri; cargo check              # verify Rust compiles without a full build

cd server; go test -count=1 ./...      # Go relay tests
cd server; go run .                    # run relay locally on :8080 (override with PORT=)
cd server; go vet ./...                # static analysis

railway up --service aiep-relay        # manual relay deploy (auto-deploy on push to main is also wired)
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
  - `wireCaptionListeners` subscribes to: `caption-update`, `phone-status`, `mobile-url-update`, `relay-status`, `audio-bytes`.
  - `updateRelayStatus(status)` (`"connecting"`/`"online"`/`"reconnecting"`) updates `#qr-status` with proper colors via `data-state` attribute.
  - `mobile_url` invoke at startup is just a one-shot read; the source of truth is the `mobile-url-update` event which fires on each new session.
  - Transcript log and settings persist only in `localStorage` (`aiep-subtitulos.transcript`, `aiep-subtitulos.settings`); transcript array capped at 500 entries.

### Go relay (`server/`)

- **`server/main.go`** — HTTP routing (`/healthz`, `/m`, `/ws/host`, `/ws/guest`), embeds `mobile.html` via `//go:embed`, graceful shutdown.
- **`server/hub.go`** — session store. Generates 6-char IDs from a 30-char alphabet (no `0/O/1/I/L` confusion). Pairing model: host creates session on connect, guest joins via `?s=<id>`. Concurrent writes to the host conn (initial `session` msg + guest's status/relay) are serialized via `Session.WriteHost` (mutex-guarded). Binary and text frames pass through untouched. Max frame size 1 MiB.
- **`server/mobile.html`** — the embedded phone UI. `MediaRecorder(opus)` 1.5s chunks → binary WS to `/ws/guest?s=<id>`. Includes silent-audio AudioContext loop + wake lock + visibility-restart to survive screen-off as best as a browser can.
- **`server/hub_test.go`** — 10 unit tests covering ID generation, pairing, relay direction, second-guest rejection (1008), 404/400 routing, and host-disconnect cleanup. CI runs these with `-race` on Linux.

### CI/CD

- **`.github/workflows/relay-ci.yml`** runs `go vet`, `go test -race`, `go build` on PRs and pushes touching `server/**`.
- **Railway auto-deploy**: dashboard → service `aiep-relay` → Settings → Source connected to repo, Root Directory `server/`. Each push to `main` rebuilds the Dockerfile and redeploys.

## Event/command contract

| Command (`#[tauri::command]`) | Returns | Notes |
|---|---|---|
| `mobile_url` | `String` | Cached URL or `""` until first session |
| `show_overlay` / `hide_overlay` / `close_overlay` / `reset_overlay_position` | `Result<&'static str, String>` | Overlay window controls |

| Event (Rust → TS) | Payload | Trigger |
|---|---|---|
| `mobile-url-update` | `string` | New session ID arrived; `""` means we lost the connection |
| `phone-status` | `string` | `"connected"`, `"disconnected"`, or guest pass-through like `"chunks:42"` |
| `caption-update` | `{ text, isFinal }` (camelCase) | Guest manual-text backup |
| `audio-bytes` | `number` | Bytes in the latest binary chunk |
| `relay-status` | `"connecting" \| "online" \| "reconnecting"` | WS client state transitions |

## Conventions worth knowing

- The phone-side JS in `mobile.html` sends `{kind: ...}` (camelCase); the relay metadata uses `{type: ...}`. Rust's `relay.rs` discriminates on which field is present.
- Connection settings live in `localStorage` (`saveTranscript`, `speechEngine`). The old `connectionMode` field is silently ignored — no migration needed.
- All Rust logs use `tracing` (`info!`, `warn!`). Filter via `RUST_LOG`.
- The mobile URL is always relay-hosted now. There is no LAN path, no self-signed cert, no `cloudflared`. If the user reports "AP isolation" or "self-signed warning" they're reading old docs.
- UI copy is Spanish (Chilean: `es-CL`).
- The relay's protocol is intentionally minimal: no auth, no rate limiting, no message inspection. Pairing happens purely via the 6-char session ID, which is ~729M combinations. Acceptable for MVP / teacher use; not for adversarial environments.
