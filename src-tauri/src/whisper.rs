use std::{
    collections::VecDeque,
    ffi::{CStr, CString},
    fs::{self, File},
    io::{Read, Write},
    path::PathBuf,
    ptr::NonNull,
    sync::{Arc, Mutex, RwLock},
    thread,
    time::Duration,
};

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager};
use tracing::{info, warn};
use whisper_rs_sys as whisper;

use crate::relay::RelayState;

const SAMPLE_RATE: usize = 16_000;
const WINDOW_SAMPLES: usize = SAMPLE_RATE * 2;
const MAX_BUFFER_SAMPLES: usize = SAMPLE_RATE * 8;
const TRANSCRIBE_INTERVAL: Duration = Duration::from_millis(900);
const MIN_RMS: f32 = 0.006;
const MODEL_FILE_NAME: &str = "ggml-base.bin";
const MODEL_URL: &str =
    "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin?download=true";
const MIN_MODEL_BYTES: u64 = 100 * 1024 * 1024;

pub struct WhisperUiState {
    status: RwLock<WhisperStatus>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum WhisperStatus {
    Loading,
    DownloadingModel,
    Ready,
    Transcribing,
    Error,
}

impl WhisperStatus {
    pub fn as_str(self) -> &'static str {
        match self {
            Self::Loading => "loading",
            Self::DownloadingModel => "downloading-model",
            Self::Ready => "ready",
            Self::Transcribing => "transcribing",
            Self::Error => "error",
        }
    }

    pub fn is_ready_for_pcm(self) -> bool {
        matches!(self, Self::Ready | Self::Transcribing)
    }
}

impl WhisperUiState {
    pub fn new() -> Self {
        Self {
            status: RwLock::new(WhisperStatus::Loading),
        }
    }

    pub fn status(&self) -> String {
        self.status.read().unwrap().as_str().to_string()
    }

    pub fn current(&self) -> WhisperStatus {
        *self.status.read().unwrap()
    }

    fn set_status(&self, status: WhisperStatus) {
        *self.status.write().unwrap() = status;
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
    buffer: Arc<Mutex<VecDeque<f32>>>,
}

#[derive(Clone, Debug, Serialize)]
struct CaptionPayload {
    text: String,
    #[serde(rename = "isFinal")]
    is_final: bool,
}

#[derive(Clone, Debug, Serialize)]
struct DownloadProgressPayload {
    downloaded: u64,
    total: Option<u64>,
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
    pub fn push_pcm_bytes(&self, bytes: &[u8]) {
        if bytes.len() < 2 {
            return;
        }

        let mut buffer = self.buffer.lock().unwrap();
        for chunk in bytes.chunks_exact(2) {
            buffer.push_back(i16::from_le_bytes([chunk[0], chunk[1]]) as f32 / 32768.0);
        }
        while buffer.len() > MAX_BUFFER_SAMPLES {
            buffer.pop_front();
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
    emit_status(&app, &ui_state, WhisperStatus::Loading);

    if !model_path.is_file() {
        warn!(path = %model_path.display(), "whisper: model not found, downloading");
    }

    let buffer = Arc::new(Mutex::new(VecDeque::<f32>::new()));
    let worker_buffer = buffer.clone();
    thread::spawn(move || {
        if let Err(error) = run_worker(app.clone(), ui_state.clone(), worker_buffer, model_path) {
            warn!(?error, "whisper: worker stopped");
            emit_status(&app, &ui_state, WhisperStatus::Error);
            if let Some(url) = state.set_capture_mode(CaptureMode::Speech, &base_url) {
                let _ = app.emit("mobile-url-update", url);
            }
        }
    });

    LocalWhisper { buffer }
}

fn emit_status(app: &AppHandle, ui_state: &Arc<WhisperUiState>, status: WhisperStatus) {
    ui_state.set_status(status);
    let _ = app.emit("whisper-status", status.as_str());
}

fn emit_runtime_status(app: &AppHandle, ui_state: &Arc<WhisperUiState>, status: WhisperStatus) {
    ui_state.set_status(status);
    let _ = app.emit("whisper-status", status.as_str());
}

fn run_worker(
    app: AppHandle,
    ui_state: Arc<WhisperUiState>,
    audio_buffer: Arc<Mutex<VecDeque<f32>>>,
    model_path: PathBuf,
) -> Result<(), String> {
    ensure_model_available(&app, &ui_state, &model_path)?;

    info!(path = %model_path.display(), "whisper: loading model");
    let mut engine = WhisperEngine::new(model_path)?;

    emit_status(&app, &ui_state, WhisperStatus::Ready);

    let mut last_text = String::new();

    loop {
        thread::sleep(TRANSCRIBE_INTERVAL);

        let window = {
            let buffer = audio_buffer.lock().unwrap();
            if buffer.len() < WINDOW_SAMPLES {
                continue;
            }
            buffer
                .iter()
                .skip(buffer.len().saturating_sub(WINDOW_SAMPLES))
                .copied()
                .collect::<Vec<_>>()
        };

        if rms(&window) < MIN_RMS {
            continue;
        }

        emit_runtime_status(&app, &ui_state, WhisperStatus::Transcribing);
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
        emit_runtime_status(&app, &ui_state, WhisperStatus::Ready);
    }

    #[allow(unreachable_code)]
    Ok(())
}

fn ensure_model_available(
    app: &AppHandle,
    ui_state: &Arc<WhisperUiState>,
    model_path: &PathBuf,
) -> Result<(), String> {
    if is_valid_model(model_path) {
        return Ok(());
    }

    emit_status(app, ui_state, WhisperStatus::DownloadingModel);

    let parent = model_path
        .parent()
        .ok_or_else(|| "invalid whisper model directory".to_string())?;
    fs::create_dir_all(parent).map_err(|error| format!("failed to create model dir: {error}"))?;

    let temp_path = model_path.with_extension("bin.download");
    if temp_path.exists() {
        let _ = fs::remove_file(&temp_path);
    }

    info!(url = MODEL_URL, path = %model_path.display(), "whisper: downloading model");
    let mut response = reqwest::blocking::get(MODEL_URL)
        .map_err(|error| format!("failed to start model download: {error}"))?
        .error_for_status()
        .map_err(|error| format!("failed to download model: {error}"))?;
    let total = response.content_length();
    let mut file = File::create(&temp_path)
        .map_err(|error| format!("failed to create model file: {error}"))?;
    let mut downloaded = 0_u64;
    let mut buffer = [0_u8; 64 * 1024];

    loop {
        let read = response
            .read(&mut buffer)
            .map_err(|error| format!("failed to read model download: {error}"))?;
        if read == 0 {
            break;
        }
        file.write_all(&buffer[..read])
            .map_err(|error| format!("failed to write model file: {error}"))?;
        downloaded += read as u64;
        let _ = app.emit(
            "whisper-download-progress",
            DownloadProgressPayload { downloaded, total },
        );
    }
    file.flush()
        .map_err(|error| format!("failed to flush model file: {error}"))?;

    let size = fs::metadata(&temp_path)
        .map_err(|error| format!("failed to read downloaded model: {error}"))?
        .len();
    if size < MIN_MODEL_BYTES {
        let _ = fs::remove_file(&temp_path);
        return Err(format!("downloaded model is too small: {size} bytes"));
    }

    if model_path.exists() {
        fs::remove_file(model_path)
            .map_err(|error| format!("failed to replace old model: {error}"))?;
    }
    fs::rename(&temp_path, model_path)
        .map_err(|error| format!("failed to install downloaded model: {error}"))?;

    info!(path = %model_path.display(), bytes = size, "whisper: model downloaded");
    Ok(())
}

fn is_valid_model(model_path: &PathBuf) -> bool {
    fs::metadata(model_path)
        .map(|metadata| metadata.len() >= MIN_MODEL_BYTES)
        .unwrap_or(false)
}

fn rms(samples: &[f32]) -> f32 {
    if samples.is_empty() {
        return 0.0;
    }
    let sum = samples.iter().map(|sample| sample * sample).sum::<f32>();
    (sum / samples.len() as f32).sqrt()
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
    params.no_timestamps = true;
    params.single_segment = true;
    params.print_special = false;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_timestamps = false;
    params.suppress_blank = true;
    params.temperature = 0.0;
    params.max_tokens = 48;

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
