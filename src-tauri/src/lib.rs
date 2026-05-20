mod relay;
mod whisper;

use std::{path::PathBuf, sync::Arc};

use tauri::{
    AppHandle, Manager, PhysicalPosition, PhysicalSize, Position, Size, WebviewUrl, WebviewWindow,
    WebviewWindowBuilder,
};
use tracing_subscriber::EnvFilter;

use crate::relay::{relay_config_from_env, spawn_relay_client, RelayState};
use crate::whisper::{model_path, spawn_local_whisper, WhisperUiState};

#[tauri::command]
fn mobile_url(state: tauri::State<'_, Arc<RelayState>>) -> String {
    state.mobile_url()
}

#[tauri::command]
fn whisper_model_path(app: AppHandle) -> Result<String, String> {
    model_path(&app).map(|path| path.display().to_string())
}

#[tauri::command]
fn whisper_status(state: tauri::State<'_, Arc<WhisperUiState>>) -> String {
    state.status()
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

#[tauri::command]
fn nudge_overlay(app: AppHandle, dx: i32, dy: i32) -> Result<&'static str, String> {
    let overlay = ensure_overlay(&app)?;
    let position = overlay.outer_position().map_err(|error| error.to_string())?;
    overlay
        .set_position(Position::Physical(PhysicalPosition {
            x: position.x.saturating_add(dx),
            y: position.y.saturating_add(dy),
        }))
        .map_err(|error| error.to_string())?;
    Ok("Overlay movido")
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let _ = tracing_subscriber::fmt()
        .with_env_filter(
            EnvFilter::try_from_default_env().unwrap_or_else(|_| EnvFilter::new("info")),
        )
        .with_target(false)
        .try_init();

    if rustls::crypto::ring::default_provider()
        .install_default()
        .is_err()
    {
        tracing::debug!("rustls crypto provider already installed");
    }

    tauri::Builder::default()
        .plugin(tauri_plugin_opener::init())
        .setup(|app| {
            let state = Arc::new(RelayState::new());
            let whisper_ui_state = Arc::new(WhisperUiState::new());
            app.manage(state.clone());
            app.manage(whisper_ui_state.clone());

            let cfg = relay_config_from_env();
            let model_path = model_path(app.handle()).unwrap_or_else(|error| {
                tracing::warn!(?error, "whisper: could not resolve model path");
                PathBuf::from("ggml-base.bin")
            });
            tracing::info!(path = %model_path.display(), "whisper model path");
            let whisper = spawn_local_whisper(
                app.handle().clone(),
                state.clone(),
                whisper_ui_state,
                cfg.base_url.clone(),
                model_path,
            );

            tracing::info!(base_url = %cfg.base_url, "starting relay client");
            spawn_relay_client(app.handle().clone(), cfg, state, whisper);

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
            reset_overlay_position,
            nudge_overlay,
            whisper_model_path,
            whisper_status
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
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
    let y =
        work_area.position.y + work_area.size.height.saturating_sub(height + bottom_margin) as i32;

    let _ = overlay.set_size(Size::Physical(PhysicalSize { width, height }));
    let _ = overlay.set_position(Position::Physical(PhysicalPosition { x, y }));
    let _ = overlay.set_always_on_top(true);
}

fn ensure_overlay(app: &AppHandle) -> Result<WebviewWindow, String> {
    if let Some(overlay) = app.get_webview_window("overlay") {
        return Ok(overlay);
    }

    let overlay = WebviewWindowBuilder::new(app, "overlay", WebviewUrl::App("overlay.html".into()))
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
