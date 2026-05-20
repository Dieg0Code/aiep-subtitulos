use std::{
    sync::{Arc, RwLock},
    time::Duration,
};

use futures_util::{SinkExt, StreamExt};
use serde::Serialize;
use tauri::{AppHandle, Emitter};
use tokio::{select, time};
use tokio_tungstenite::{
    connect_async,
    tungstenite::{client::IntoClientRequest, protocol::Message},
};
use tracing::{info, warn};

use crate::whisper::{CaptureMode, LocalWhisper};

const DEFAULT_RELAY_URL: &str = "https://aiep-relay-production.up.railway.app";
const RELAY_URL_ENV: &str = "AIEP_RELAY_URL";
const HEARTBEAT: Duration = Duration::from_secs(30);
const BACKOFF_MIN: Duration = Duration::from_secs(1);
const BACKOFF_MAX: Duration = Duration::from_secs(30);

#[derive(Clone)]
pub struct RelayConfig {
    pub base_url: String,
    pub ws_url: String,
}

pub fn relay_config_from_env() -> RelayConfig {
    let base = std::env::var(RELAY_URL_ENV).unwrap_or_else(|_| DEFAULT_RELAY_URL.to_string());
    let ws = base
        .replacen("https://", "wss://", 1)
        .replacen("http://", "ws://", 1)
        + "/ws/host";
    RelayConfig {
        base_url: base,
        ws_url: ws,
    }
}

pub struct RelayState {
    mobile_url: RwLock<String>,
    session_id: RwLock<Option<String>>,
    capture_mode: RwLock<CaptureMode>,
}

impl RelayState {
    pub fn new() -> Self {
        Self {
            mobile_url: RwLock::new(String::new()),
            session_id: RwLock::new(None),
            capture_mode: RwLock::new(CaptureMode::Speech),
        }
    }

    pub fn mobile_url(&self) -> String {
        self.mobile_url.read().unwrap().clone()
    }

    fn session_id(&self) -> Option<String> {
        self.session_id.read().unwrap().clone()
    }

    fn set_session(&self, id: String, base_url: &str) -> String {
        let mode = *self.capture_mode.read().unwrap();
        let url = mobile_url(base_url, &id, mode);
        *self.session_id.write().unwrap() = Some(id);
        *self.mobile_url.write().unwrap() = url.clone();
        url
    }

    pub fn set_capture_mode(&self, mode: CaptureMode, base_url: &str) -> Option<String> {
        *self.capture_mode.write().unwrap() = mode;
        let id = self.session_id()?;
        let url = mobile_url(base_url, &id, mode);
        *self.mobile_url.write().unwrap() = url.clone();
        Some(url)
    }
}

#[derive(Debug, Serialize, Clone)]
struct CaptionPayload {
    text: String,
    #[serde(rename = "isFinal")]
    is_final: bool,
}

pub fn spawn_relay_client(
    app: AppHandle,
    cfg: RelayConfig,
    state: Arc<RelayState>,
    whisper: LocalWhisper,
) {
    tauri::async_runtime::spawn(async move {
        let mut backoff = BACKOFF_MIN;
        loop {
            let _ = app.emit("relay-status", "connecting");

            // Resume the previous session id (if any) so the phone QR stays
            // valid across reconnects. The relay accepts `?cid=<id>` and will
            // either reattach to or recreate that session.
            let ws_url = match state.session_id() {
                Some(id) => format!("{}?cid={}", cfg.ws_url, id),
                None => cfg.ws_url.clone(),
            };
            info!(url = %ws_url, "relay: connecting");

            let request = match ws_url.as_str().into_client_request() {
                Ok(req) => req,
                Err(err) => {
                    warn!(?err, "relay: invalid url, stopping client");
                    let _ = app.emit("relay-status", "reconnecting");
                    return;
                }
            };

            match connect_async(request).await {
                Ok((stream, _)) => {
                    info!("relay: connected");
                    let _ = app.emit("relay-status", "online");
                    backoff = BACKOFF_MIN;
                    run_session(&app, &state, &cfg, &whisper, stream).await;
                }
                Err(err) => {
                    warn!(?err, "relay: connect failed");
                }
            }

            let _ = app.emit("relay-status", "reconnecting");
            info!(?backoff, "relay: backing off before reconnect");
            time::sleep(backoff).await;
            backoff = (backoff * 2).min(BACKOFF_MAX);
        }
    });
}

async fn run_session<S>(
    app: &AppHandle,
    state: &Arc<RelayState>,
    cfg: &RelayConfig,
    whisper: &LocalWhisper,
    stream: tokio_tungstenite::WebSocketStream<S>,
) where
    S: tokio::io::AsyncRead + tokio::io::AsyncWrite + Unpin,
{
    let (mut sink, mut source) = stream.split();
    let mut heartbeat = time::interval(HEARTBEAT);
    heartbeat.tick().await; // consume immediate tick

    loop {
        select! {
            msg = source.next() => {
                let Some(item) = msg else {
                    info!("relay: stream ended");
                    return;
                };
                let frame = match item {
                    Ok(f) => f,
                    Err(err) => {
                        warn!(?err, "relay: stream error");
                        return;
                    }
                };
                match frame {
                    Message::Text(raw) => handle_text(app, state, cfg, raw.as_str()),
                    Message::Binary(data) => {
                        let _ = app.emit("audio-bytes", data.len() as u64);
                        whisper.push_pcm_bytes(&data);
                    }
                    Message::Close(frame) => {
                        info!(?frame, "relay: close frame");
                        return;
                    }
                    Message::Ping(_) | Message::Pong(_) | Message::Frame(_) => {}
                }
            }
            _ = heartbeat.tick() => {
                if let Err(err) = sink.send(Message::Ping(Vec::new().into())).await {
                    warn!(?err, "relay: heartbeat send failed");
                    return;
                }
            }
        }
    }
}

fn mobile_url(base_url: &str, id: &str, mode: CaptureMode) -> String {
    format!(
        "{}/m?s={}&mode={}",
        base_url.trim_end_matches('/'),
        id,
        mode.as_query_value()
    )
}

fn handle_text(app: &AppHandle, state: &Arc<RelayState>, cfg: &RelayConfig, raw: &str) {
    let parsed: serde_json::Value = match serde_json::from_str(raw) {
        Ok(v) => v,
        Err(err) => {
            warn!(?err, raw, "relay: bad json from relay");
            return;
        }
    };
    if let Some(type_field) = parsed.get("type").and_then(|v| v.as_str()) {
        match type_field {
            "session" => {
                if let Some(id) = parsed.get("id").and_then(|v| v.as_str()) {
                    let url = state.set_session(id.to_string(), &cfg.base_url);
                    info!(session = id, "relay: session assigned");
                    let _ = app.emit("mobile-url-update", url);
                }
            }
            "status" => {
                if let Some(s) = parsed.get("state").and_then(|v| v.as_str()) {
                    let payload = match s {
                        "phone-connected" => "connected",
                        "phone-disconnected" => "disconnected",
                        other => other,
                    };
                    let _ = app.emit("phone-status", payload);
                }
            }
            other => warn!(?other, "relay: unknown type"),
        }
        return;
    }

    if let Some(kind) = parsed.get("kind").and_then(|v| v.as_str()) {
        match kind {
            "status" => {
                let text = parsed
                    .get("text")
                    .and_then(|v| v.as_str())
                    .unwrap_or("")
                    .to_string();
                let _ = app.emit("phone-status", text);
            }
            "caption" => {
                let payload = CaptionPayload {
                    text: parsed
                        .get("text")
                        .and_then(|v| v.as_str())
                        .unwrap_or("")
                        .to_string(),
                    is_final: parsed
                        .get("isFinal")
                        .and_then(|v| v.as_bool())
                        .unwrap_or(false),
                };
                let _ = app.emit("caption-update", payload);
            }
            other => warn!(?other, "relay: unknown guest kind"),
        }
        return;
    }

    warn!(raw, "relay: text frame without type/kind");
}
