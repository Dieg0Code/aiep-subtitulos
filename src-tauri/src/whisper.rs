use std::{
    collections::VecDeque,
    ffi::{CStr, CString},
    path::PathBuf,
    ptr::NonNull,
    sync::{mpsc, Arc, RwLock},
    thread,
};

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager};
use tracing::{info, warn};
use whisper_rs_sys as whisper;

use crate::relay::RelayState;

const SAMPLE_RATE: usize = 16_000;
const WINDOW_SAMPLES: usize = SAMPLE_RATE * 5 / 2; // 2.5s
const OVERLAP_SAMPLES: usize = SAMPLE_RATE / 2; // 0.5s
const MAX_BUFFER_SAMPLES: usize = SAMPLE_RATE * 15;
const MODEL_FILE_NAME: &str = "ggml-base.bin";

pub struct WhisperUiState {
    status: RwLock<String>,
}

impl WhisperUiState {
    pub fn new() -> Self {
        Self {
            status: RwLock::new("loading".to_string()),
        }
    }

    pub fn status(&self) -> String {
        self.status.read().unwrap().clone()
    }

    fn set_status(&self, status: &str) {
        *self.status.write().unwrap() = status.to_string();
    }
}

#[derive(Debug, Clone, Copy)]
pub enum CaptureMode {
    Pcm,
    Speech,
}

impl CaptureMode {
    pub fn as_query_value(self) -> &'static str {
        match self {
            Self::Pcm => "pcm",
            Self::Speech => "speech",
        }
    }
}

#[derive(Clone)]
pub struct LocalWhisper {
    tx: Option<mpsc::SyncSender<Vec<f32>>>,
}

#[derive(Clone, Debug, Serialize)]
struct CaptionPayload {
    text: String,
    #[serde(rename = "isFinal")]
    is_final: bool,
}

struct WhisperContext {
    ptr: NonNull<whisper::whisper_context>,
}

unsafe impl Send for WhisperContext {}

impl WhisperContext {
    fn new(model_path: PathBuf) -> Result<Self, String> {
        let model_path = CString::new(model_path.to_string_lossy().as_bytes())
            .map_err(|_| "invalid whisper model path".to_string())?;
        let mut params = unsafe { whisper::whisper_context_default_params() };
        params.use_gpu = false;
        let ptr = unsafe {
            whisper::whisper_init_from_file_with_params_no_state(model_path.as_ptr(), params)
        };

        NonNull::new(ptr)
            .map(|ptr| Self { ptr })
            .ok_or_else(|| "failed to load whisper model".to_string())
    }

    fn as_ptr(&self) -> *mut whisper::whisper_context {
        self.ptr.as_ptr()
    }
}

impl Drop for WhisperContext {
    fn drop(&mut self) {
        unsafe {
            whisper::whisper_free(self.ptr.as_ptr());
        }
    }
}

struct WhisperState {
    ptr: NonNull<whisper::whisper_state>,
}

unsafe impl Send for WhisperState {}

impl WhisperState {
    fn new(ctx: &WhisperContext) -> Result<Self, String> {
        let ptr = unsafe { whisper::whisper_init_state(ctx.as_ptr()) };
        NonNull::new(ptr)
            .map(|ptr| Self { ptr })
            .ok_or_else(|| "failed to create whisper state".to_string())
    }

    fn as_ptr(&self) -> *mut whisper::whisper_state {
        self.ptr.as_ptr()
    }
}

impl Drop for WhisperState {
    fn drop(&mut self) {
        unsafe {
            whisper::whisper_free_state(self.ptr.as_ptr());
        }
    }
}

struct WhisperEngine {
    state: WhisperState,
    ctx: WhisperContext,
    language: CString,
}

impl WhisperEngine {
    fn new(model_path: PathBuf) -> Result<Self, String> {
        let ctx = WhisperContext::new(model_path)?;
        let state = WhisperState::new(&ctx)?;
        let language = CString::new("es").expect("static language has no nul bytes");

        Ok(Self {
            state,
            ctx,
            language,
        })
    }
}

impl LocalWhisper {
    pub fn disabled() -> Self {
        Self { tx: None }
    }

    pub fn push_pcm_bytes(&self, bytes: &[u8]) {
        let Some(tx) = &self.tx else {
            return;
        };

        let samples = bytes
            .chunks_exact(2)
            .map(|chunk| i16::from_le_bytes([chunk[0], chunk[1]]) as f32 / 32768.0)
            .collect::<Vec<_>>();

        if !samples.is_empty() {
            let _ = tx.try_send(samples);
        }
    }
}

pub fn model_path(app: &AppHandle) -> Result<PathBuf, String> {
    app.path()
        .app_data_dir()
        .map(|dir| dir.join("models").join(MODEL_FILE_NAME))
        .map_err(|error| error.to_string())
}

pub fn spawn_local_whisper(
    app: AppHandle,
    state: Arc<RelayState>,
    ui_state: Arc<WhisperUiState>,
    base_url: String,
    model_path: PathBuf,
) -> LocalWhisper {
    emit_status(
        &app,
        &state,
        &ui_state,
        &base_url,
        "loading",
        CaptureMode::Speech,
    );

    if !model_path.is_file() {
        warn!(path = %model_path.display(), "whisper: model not found");
        emit_status(
            &app,
            &state,
            &ui_state,
            &base_url,
            "missing-model",
            CaptureMode::Speech,
        );
        return LocalWhisper::disabled();
    }

    let (tx, rx) = mpsc::sync_channel::<Vec<f32>>(1);
    thread::spawn(move || {
        if let Err(error) = run_worker(
            app.clone(),
            state.clone(),
            ui_state.clone(),
            base_url.clone(),
            rx,
            model_path,
        ) {
            warn!(?error, "whisper: worker stopped");
            emit_status(
                &app,
                &state,
                &ui_state,
                &base_url,
                "error",
                CaptureMode::Speech,
            );
        }
    });

    LocalWhisper { tx: Some(tx) }
}

fn emit_status(
    app: &AppHandle,
    state: &Arc<RelayState>,
    ui_state: &Arc<WhisperUiState>,
    base_url: &str,
    status: &str,
    mode: CaptureMode,
) {
    ui_state.set_status(status);
    let _ = app.emit("whisper-status", status);
    if let Some(url) = state.set_capture_mode(mode, base_url) {
        let _ = app.emit("mobile-url-update", url);
    }
}

fn emit_runtime_status(app: &AppHandle, ui_state: &Arc<WhisperUiState>, status: &str) {
    ui_state.set_status(status);
    let _ = app.emit("whisper-status", status);
}

fn run_worker(
    app: AppHandle,
    state_for_mode: Arc<RelayState>,
    ui_state: Arc<WhisperUiState>,
    base_url: String,
    rx: mpsc::Receiver<Vec<f32>>,
    model_path: PathBuf,
) -> Result<(), String> {
    info!(path = %model_path.display(), "whisper: loading model");
    let mut engine = WhisperEngine::new(model_path)?;

    emit_status(
        &app,
        &state_for_mode,
        &ui_state,
        &base_url,
        "ready",
        CaptureMode::Pcm,
    );

    let mut buffer = VecDeque::<f32>::new();
    let mut last_text = String::new();

    while let Ok(samples) = rx.recv() {
        buffer.extend(samples);
        while buffer.len() > MAX_BUFFER_SAMPLES {
            buffer.pop_front();
        }

        if buffer.len() < WINDOW_SAMPLES {
            continue;
        }

        let window = buffer
            .iter()
            .copied()
            .take(WINDOW_SAMPLES)
            .collect::<Vec<_>>();
        let drain = WINDOW_SAMPLES.saturating_sub(OVERLAP_SAMPLES);
        for _ in 0..drain.min(buffer.len()) {
            buffer.pop_front();
        }

        emit_runtime_status(&app, &ui_state, "transcribing");
        let text = transcribe_window(&mut engine, &window)?;
        let clean = text.split_whitespace().collect::<Vec<_>>().join(" ");
        if !clean.is_empty() && clean != last_text {
            last_text = clean.clone();
            let _ = app.emit(
                "caption-update",
                CaptionPayload {
                    text: clean,
                    is_final: true,
                },
            );
        }
        emit_runtime_status(&app, &ui_state, "ready");
    }

    Ok(())
}

fn transcribe_window(engine: &mut WhisperEngine, samples: &[f32]) -> Result<String, String> {
    let mut params = unsafe {
        whisper::whisper_full_default_params(
            whisper::whisper_sampling_strategy_WHISPER_SAMPLING_GREEDY,
        )
    };
    params.greedy.best_of = 1;
    params.language = engine.language.as_ptr();
    params.translate = false;
    params.n_threads = 2;
    params.no_context = true;
    params.single_segment = true;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;

    let result = unsafe {
        whisper::whisper_full_with_state(
            engine.ctx.as_ptr(),
            engine.state.as_ptr(),
            params,
            samples.as_ptr(),
            samples.len() as i32,
        )
    };
    if result != 0 {
        return Err(format!("failed to run whisper: {result}"));
    }

    let segment_count =
        unsafe { whisper::whisper_full_n_segments_from_state(engine.state.as_ptr()) };
    let mut segments = Vec::new();
    for index in 0..segment_count {
        let text = unsafe {
            whisper::whisper_full_get_segment_text_from_state(engine.state.as_ptr(), index)
        };
        if !text.is_null() {
            segments.push(
                unsafe { CStr::from_ptr(text) }
                    .to_string_lossy()
                    .into_owned(),
            );
        }
    }

    Ok(segments.join(" "))
}
