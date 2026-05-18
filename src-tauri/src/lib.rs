use axum::{
    extract::{
        ws::{Message, WebSocket, WebSocketUpgrade},
        State,
    },
    response::{Html, IntoResponse},
    routing::get,
    Router,
};
use axum_server::tls_rustls::RustlsConfig;
use futures_util::StreamExt;
use local_ip_address::local_ip;
use rcgen::{CertificateParams, DistinguishedName, DnType, KeyPair, SanType};
use serde::{Deserialize, Serialize};
use std::{
    net::{IpAddr, SocketAddr},
    sync::{Arc, Mutex},
};
use tauri::{
    AppHandle, Emitter, Manager, PhysicalPosition, PhysicalSize, Position, Size, WebviewUrl,
    WebviewWindow, WebviewWindowBuilder,
};

#[derive(Clone)]
struct WebState {
    app: AppHandle,
}

#[derive(Clone)]
struct MobileUrl(Arc<Mutex<String>>);

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct CaptionPayload {
    text: String,
    is_final: bool,
}

#[derive(Debug, Deserialize)]
#[serde(rename_all = "camelCase")]
struct PhoneMessage {
    kind: String,
    text: Option<String>,
    is_final: Option<bool>,
}

#[tauri::command]
fn mobile_url(state: tauri::State<'_, MobileUrl>) -> String {
    state.0.lock().map(|url| url.clone()).unwrap_or_default()
}

#[tauri::command]
fn close_overlay(app: AppHandle) -> Result<&'static str, String> {
    hide_overlay(app)
}

#[tauri::command]
fn show_overlay(app: AppHandle) -> Result<&'static str, String> {
    let overlay = ensure_overlay(&app)?;
    overlay.show().map_err(|error| error.to_string())?;
    overlay
        .set_always_on_top(true)
        .map_err(|error| error.to_string())?;
    Ok("Overlay visible")
}

#[tauri::command]
fn hide_overlay(app: AppHandle) -> Result<&'static str, String> {
    let overlay = ensure_overlay(&app)?;
    overlay.hide().map_err(|error| error.to_string())?;
    Ok("Overlay oculto")
}

#[tauri::command]
fn reset_overlay_position(app: AppHandle) -> Result<&'static str, String> {
    ensure_overlay(&app)?;
    position_overlay(&app);
    show_overlay(app)?;
    Ok("Overlay reposicionado")
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let url = start_mobile_server(app.handle().clone())
                .unwrap_or_else(|error| format!("No se pudo iniciar servidor: {error}"));
            app.manage(MobileUrl(Arc::new(Mutex::new(url))));
            position_overlay(app.handle());
            Ok(())
        })
        .on_window_event(|window, event| {
            if window.label() == "main" {
                if let tauri::WindowEvent::CloseRequested { .. } = event {
                    window.app_handle().exit(0);
                }
            }
        })
        .invoke_handler(tauri::generate_handler![
            mobile_url,
            close_overlay,
            show_overlay,
            hide_overlay,
            reset_overlay_position
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}

fn start_mobile_server(app: AppHandle) -> Result<String, String> {
    let ip = local_ip().unwrap_or(IpAddr::from([127, 0, 0, 1]));
    let addr = SocketAddr::from(([0, 0, 0, 0], 8787));
    let cert = tauri::async_runtime::block_on(tls_config_for(ip))?;
    let router = Router::new()
        .route("/", get(mobile_page))
        .route("/ws", get(ws_handler))
        .with_state(WebState { app });

    tauri::async_runtime::spawn(async move {
        if let Err(error) = axum_server::bind_rustls(addr, cert)
            .serve(router.into_make_service())
            .await
        {
            eprintln!("mobile server failed: {error}");
        }
    });

    Ok(format!("https://{ip}:8787"))
}

fn position_overlay(app: &AppHandle) {
    let Some(overlay) = app.get_webview_window("overlay") else {
        return;
    };

    let Ok(Some(monitor)) = app.primary_monitor() else {
        return;
    };

    let work_area = monitor.work_area();
    let width = (work_area.size.width as f64 * 0.86).round() as u32;
    let height = 132_u32;
    let bottom_margin = 28_u32;
    let x = work_area.position.x + ((work_area.size.width.saturating_sub(width)) / 2) as i32;
    let y = work_area.position.y
        + work_area
            .size
            .height
            .saturating_sub(height + bottom_margin) as i32;

    let _ = overlay.set_size(Size::Physical(PhysicalSize { width, height }));
    let _ = overlay.set_position(Position::Physical(PhysicalPosition { x, y }));
    let _ = overlay.set_always_on_top(true);
}

fn ensure_overlay(app: &AppHandle) -> Result<WebviewWindow, String> {
    if let Some(overlay) = app.get_webview_window("overlay") {
        return Ok(overlay);
    }

    let overlay = WebviewWindowBuilder::new(
        app,
        "overlay",
        WebviewUrl::App("overlay.html".into()),
    )
    .title("Subtitulos")
    .inner_size(1180.0, 132.0)
    .decorations(false)
    .transparent(true)
    .always_on_top(true)
    .skip_taskbar(true)
    .resizable(true)
    .shadow(false)
    .build()
    .map_err(|error| error.to_string())?;

    position_overlay(app);
    Ok(overlay)
}

async fn tls_config_for(ip: IpAddr) -> Result<RustlsConfig, String> {
    let mut params = CertificateParams::new(vec!["localhost".to_string()])
        .map_err(|error| error.to_string())?;
    params.distinguished_name = DistinguishedName::new();
    params
        .distinguished_name
        .push(DnType::CommonName, "AIEP Subtitulos Local");
    params.subject_alt_names.push(SanType::IpAddress(ip));
    let key_pair = KeyPair::generate().map_err(|error| error.to_string())?;
    let cert = params
        .self_signed(&key_pair)
        .map_err(|error| error.to_string())?;
    RustlsConfig::from_pem(
        cert.pem().into_bytes(),
        key_pair.serialize_pem().into_bytes(),
    )
    .await
    .map_err(|error| error.to_string())
}

async fn mobile_page() -> Html<&'static str> {
    Html(MOBILE_HTML)
}

async fn ws_handler(ws: WebSocketUpgrade, State(state): State<WebState>) -> impl IntoResponse {
    ws.on_upgrade(move |socket| handle_phone(socket, state))
}

async fn handle_phone(mut socket: WebSocket, state: WebState) {
    let _ = state.app.emit("phone-status", "connected");

    while let Some(Ok(message)) = socket.next().await {
        if let Message::Text(raw) = message {
            if let Ok(phone_message) = serde_json::from_str::<PhoneMessage>(&raw) {
                match phone_message.kind.as_str() {
                    "caption" => {
                        let payload = CaptionPayload {
                            text: phone_message.text.unwrap_or_default(),
                            is_final: phone_message.is_final.unwrap_or(false),
                        };
                        let _ = state.app.emit("caption-update", payload);
                    }
                    "status" => {
                        let _ = state
                            .app
                            .emit("phone-status", phone_message.text.unwrap_or_default());
                    }
                    _ => {}
                }
            }
        }
    }

    let _ = state.app.emit("phone-status", "disconnected");
}

const MOBILE_HTML: &str = r##"<!doctype html>
<html lang="es">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Micrófono AIEP</title>
    <style>
      :root {
        color: #172026;
        background: #f4efe6;
        font-family: Aptos, "Segoe UI", sans-serif;
      }
      body {
        margin: 0;
        min-height: 100vh;
        display: grid;
        place-items: center;
      }
      main {
        width: min(92vw, 460px);
        padding: 28px;
      }
      h1 {
        font-size: clamp(2rem, 10vw, 3.4rem);
        line-height: 0.95;
        margin: 0 0 18px;
      }
      p {
        color: #42515a;
        font-size: 1.05rem;
      }
      button {
        width: 100%;
        min-height: 64px;
        margin-top: 20px;
        border: 0;
        border-radius: 8px;
        background: #c8102e;
        color: white;
        font-size: 1.1rem;
        font-weight: 800;
      }
      button.listening {
        background: #12262f;
      }
      .transcript {
        min-height: 112px;
        margin-top: 22px;
        padding: 18px;
        border: 1px solid #d5cbbc;
        border-radius: 8px;
        background: rgba(255, 255, 255, 0.62);
        font-size: 1.25rem;
        line-height: 1.35;
      }
      .status {
        margin-top: 16px;
        color: #6a5a47;
        font-size: 0.95rem;
      }
      textarea {
        width: calc(100% - 28px);
        min-height: 80px;
        margin-top: 16px;
        padding: 14px;
        border-radius: 8px;
        border: 1px solid #d5cbbc;
        font: inherit;
      }
    </style>
  </head>
  <body>
    <main>
      <h1>Micrófono docente</h1>
      <p>Activa el micrófono y habla cerca del celular. Los subtítulos aparecerán en el PC.</p>
      <button id="toggle">Iniciar subtítulos</button>
      <div id="transcript" class="transcript">Esperando voz...</div>
      <textarea id="manual" placeholder="Respaldo: escribe aquí si el navegador no permite reconocimiento de voz."></textarea>
      <div id="status" class="status">Conectando con el PC...</div>
    </main>

    <script>
      const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
      const socket = new WebSocket(`wss://${location.host}/ws`);
      const button = document.querySelector("#toggle");
      const transcript = document.querySelector("#transcript");
      const status = document.querySelector("#status");
      const manual = document.querySelector("#manual");
      let recognition;
      let listening = false;
      let lastText = "";

      const send = (payload) => {
        if (socket.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify(payload));
        }
      };

      socket.addEventListener("open", () => {
        status.textContent = "Conectado al PC.";
        send({ kind: "status", text: "connected" });
      });

      socket.addEventListener("close", () => {
        status.textContent = "Se perdió la conexión con el PC.";
      });

      manual.addEventListener("input", () => {
        const text = manual.value.trim();
        transcript.textContent = text || "Esperando voz...";
        send({ kind: "caption", text, isFinal: false });
      });

      if (!SpeechRecognition) {
        button.disabled = true;
        button.textContent = "Reconocimiento no disponible";
        status.textContent = "Este navegador no expone reconocimiento de voz. Usa Chrome en Android o el respaldo escrito.";
      } else {
        recognition = new SpeechRecognition();
        recognition.lang = "es-CL";
        recognition.continuous = true;
        recognition.interimResults = true;

        recognition.onresult = (event) => {
          const parts = [];
          let hasInterim = false;

          for (let index = event.resultIndex; index < event.results.length; index++) {
            const result = event.results[index];
            const phrase = result[0].transcript.trim();
            if (!phrase) continue;
            parts.push(phrase);
            hasInterim = hasInterim || !result.isFinal;
          }

          if (parts.length === 0 && event.results.length > 0) {
            const lastResult = event.results[event.results.length - 1];
            parts.push(lastResult[0].transcript.trim());
            hasInterim = !lastResult.isFinal;
          }

          const text = tailWords(cleanSubtitle(parts.join(" ")), 18);
          if (text === lastText) return;
          lastText = text;
          transcript.textContent = text || "Escuchando...";
          send({ kind: "caption", text, isFinal: !hasInterim });
        };

        const cleanSubtitle = (raw) => {
          const words = raw.replace(/\s+/g, " ").trim().split(" ").filter(Boolean);
          return collapseGrowingRepeats(collapseAdjacentRepeats(words)).join(" ");
        };

        const sameWord = (left, right) => normalizeWord(left) === normalizeWord(right);

        const normalizeWord = (word) =>
          word.toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^\p{L}\p{N}]/gu, "");

        const sameSequence = (words, leftStart, rightStart, length) => {
          for (let offset = 0; offset < length; offset++) {
            if (!sameWord(words[leftStart + offset], words[rightStart + offset])) return false;
          }
          return true;
        };

        const collapseAdjacentRepeats = (input) => {
          const words = [...input];
          let changed = true;

          while (changed) {
            changed = false;
            for (let size = Math.min(8, Math.floor(words.length / 2)); size >= 1; size--) {
              for (let index = 0; index + size * 2 <= words.length; index++) {
                if (sameSequence(words, index, index + size, size)) {
                  words.splice(index, size);
                  changed = true;
                  break;
                }
              }
              if (changed) break;
            }
          }

          return words;
        };

        const collapseGrowingRepeats = (input) => {
          let words = [...input];

          for (let size = Math.min(10, Math.floor(words.length / 2)); size >= 2; size--) {
            for (let index = 0; index + size < words.length; index++) {
              const nextStart = index + size;
              let overlap = 0;

              while (
                overlap < size &&
                nextStart + overlap < words.length &&
                sameWord(words[index + overlap], words[nextStart + overlap])
              ) {
                overlap++;
              }

              if (overlap >= Math.min(size, 3)) {
                words.splice(index, overlap);
                return collapseGrowingRepeats(collapseAdjacentRepeats(words));
              }
            }
          }

          return words;
        };

        const tailWords = (raw, maxWords) => {
          const words = raw.split(" ").filter(Boolean);
          return words.slice(Math.max(0, words.length - maxWords)).join(" ");
        };

        recognition.onerror = (event) => {
          status.textContent = `Error de micrófono: ${event.error}. Revisa permisos o certificado HTTPS.`;
          listening = false;
          button.classList.remove("listening");
          button.textContent = "Reintentar subtítulos";
        };

        recognition.onend = () => {
          if (listening) recognition.start();
        };
      }

      button.addEventListener("click", () => {
        if (!recognition) return;
        listening = !listening;
        if (listening) {
          lastText = "";
          transcript.textContent = "Escuchando...";
          button.classList.add("listening");
          button.textContent = "Pausar subtítulos";
          recognition.start();
        } else {
          recognition.stop();
          button.classList.remove("listening");
          button.textContent = "Reanudar subtítulos";
        }
      });
    </script>
  </body>
</html>"##;
