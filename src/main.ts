import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import {
  Captions,
  Download,
  Eye,
  EyeOff,
  FileText,
  MoveDown,
  QrCode,
  Settings,
  ShieldCheck,
  Trash2,
  createIcons,
} from "lucide";
import QRCode from "qrcode";

type CaptionPayload = {
  text: string;
  isFinal: boolean;
};

type TranscriptLine = {
  text: string;
  time: string;
};

const TRANSCRIPT_KEY = "aiep-subtitulos.transcript";
const SETTINGS_KEY = "aiep-subtitulos.settings";
const FAST_ENGINE = "fast-speech";
const LOCAL_ENGINE = "whisper-local";

let latestWhisperStatus: WhisperStatus = "loading";
let activeCaptureMode: "speech" | "pcm" | null = null;

const app = document.querySelector<HTMLDivElement>("#app");

if (!app) {
  throw new Error("App root not found");
}

if (location.pathname.endsWith("/overlay.html") || location.search.includes("view=overlay")) {
  renderOverlay(app);
} else {
  renderControl(app);
}

async function renderControl(root: HTMLDivElement) {
  const settings = loadSettings();
  document.documentElement.classList.remove("overlay-page");
  document.body.classList.remove("overlay-mode");
  root.innerHTML = `
    <section class="control-shell">
      <header class="app-header">
        <div class="brand-mark">
          <span class="brand-symbol">A</span>
          <div>
            <h1>Subtitulos flotantes</h1>
            <p class="app-subtitle">Aula accesible en tiempo real</p>
          </div>
        </div>
        <div class="header-status">
          <div class="status-row">
            <span class="status-dot" id="status-dot"></span>
            <span id="phone-status">Esperando celular</span>
          </div>
          <div class="privacy-chip"><i data-lucide="shield-check"></i> Sin guardar audio</div>
        </div>
      </header>

      <section class="qr-panel app-card" aria-label="Conexion del celular">
        <div class="section-title">
          <i data-lucide="qr-code"></i>
          <div>
            <p class="section-label">Celular</p>
            <h2>Micrófono</h2>
          </div>
        </div>
        <div class="qr-frame" id="qr-frame">
          <canvas id="qr-code" width="256" height="256"></canvas>
          <div class="qr-placeholder" id="qr-placeholder">
            <i data-lucide="qr-code"></i>
            <span>Preparando QR...</span>
          </div>
        </div>
        <div class="session-code-card" id="session-code-card" aria-live="polite">
          <span>Código</span>
          <strong id="session-code">------</strong>
        </div>
        <p class="qr-url" id="mobile-url">Preparando enlace...</p>
        <p class="qr-status" id="qr-status">Conectando al relay...</p>
      </section>

      <section class="caption-preview app-card">
        <div class="section-header">
          <div class="section-title">
            <i data-lucide="captions"></i>
            <div>
              <p class="section-label">En vivo</p>
              <h2>Subtítulo actual</h2>
            </div>
          </div>
          <div class="overlay-actions" aria-label="Controles del overlay">
            <button id="show-overlay" type="button"><i data-lucide="eye"></i> Mostrar</button>
            <button id="hide-overlay" class="btn-ghost" type="button"><i data-lucide="eye-off"></i> Ocultar</button>
            <button id="reset-overlay" class="btn-ghost" type="button"><i data-lucide="move-down"></i> Abajo</button>
          </div>
        </div>
        <div class="preview-subtitle" id="preview-text">Esperando subtitulos...</div>
        <p class="subtitle-queue" id="subtitle-queue" aria-live="polite"></p>
        <p class="audio-stats" id="audio-stats">Esperando audio del celular...</p>
        <p class="overlay-command-status" id="overlay-command-status">Overlay listo.</p>
      </section>

      <aside class="side-stack">
      <section class="settings-panel app-card">
        <div class="section-title">
          <i data-lucide="settings"></i>
          <div>
            <p class="section-label">Opciones</p>
            <h2>Configuración</h2>
          </div>
        </div>
        <label class="field">
          Motor de transcripcion
          <select id="speech-engine">
            <option value="fast-speech">Rapido: reconocimiento del celular</option>
            <option value="whisper-local">Local: Whisper en este PC</option>
            <option value="openai" disabled>OpenAI speech-to-text (proximamente)</option>
          </select>
        </label>
        <p class="whisper-status" id="whisper-status">Cargando Whisper local...</p>
        <p class="model-path" id="model-path"></p>
        <p class="download-progress" id="download-progress"></p>
        <label class="check-field">
          <input id="save-transcript" type="checkbox" />
          Guardar lo transcrito en esta app
        </label>
      </section>

      <section class="transcript-panel app-card">
        <div class="section-header">
          <div class="section-title">
            <i data-lucide="file-text"></i>
            <div>
              <p class="section-label">Registro</p>
              <h2>Bitácora</h2>
            </div>
          </div>
          <div class="overlay-actions">
            <button id="download-transcript" type="button"><i data-lucide="download"></i> Descargar</button>
            <button id="clear-transcript" class="btn-ghost" type="button"><i data-lucide="trash-2"></i> Limpiar</button>
          </div>
        </div>
        <div class="transcript-log" id="transcript-log"></div>
      </section>
      </aside>
    </section>
  `;
  createIcons({
    icons: {
      Captions,
      Download,
      Eye,
      EyeOff,
      FileText,
      MoveDown,
      QrCode,
      Settings,
      ShieldCheck,
      Trash2,
    },
  });

  const mobileUrl = await invoke<string>("mobile_url");
  await updateMobileUrl(mobileUrl);
  await updateWhisperModelPath();
  wireSettings(settings);
  renderTranscriptLog();

  const runOverlayCommand = async (command: string) => {
    const status = document.querySelector<HTMLParagraphElement>("#overlay-command-status");
    try {
      const message = await invoke<string>(command);
      if (status) status.textContent = message;
    } catch (error) {
      if (status) status.textContent = `No se pudo ejecutar: ${String(error)}`;
    }
  };

  document.querySelector<HTMLButtonElement>("#show-overlay")?.addEventListener("click", () => {
    runOverlayCommand("show_overlay");
  });

  document.querySelector<HTMLButtonElement>("#hide-overlay")?.addEventListener("click", () => {
    runOverlayCommand("hide_overlay");
  });

  document.querySelector<HTMLButtonElement>("#reset-overlay")?.addEventListener("click", () => {
    runOverlayCommand("reset_overlay_position");
  });

  document.querySelector<HTMLButtonElement>("#clear-transcript")?.addEventListener("click", () => {
    localStorage.setItem(TRANSCRIPT_KEY, "[]");
    renderTranscriptLog();
  });

  document.querySelector<HTMLButtonElement>("#download-transcript")?.addEventListener("click", () => {
    downloadTranscript();
  });

  const audioStats = { totalBytes: 0, chunks: 0, lastUpdate: 0 };
  const previewPacer = createPacedSubtitle(
    document.querySelector<HTMLDivElement>("#preview-text"),
    { maxVisibleWords: 34, onPendingChange: updateSubtitleQueue },
  );
  wireCaptionListeners({
    onCaption: (payload) => {
      const text = normalizeCaption(payload.text);
      previewPacer.set(text || "Escuchando...", { instant: !text });
      maybeSaveTranscript({ text, isFinal: payload.isFinal });
    },
    onStatus: updatePhoneStatus,
    onMobileUrl: updateMobileUrl,
    onRelayStatus: updateRelayStatus,
    onWhisperStatus: updateWhisperStatus,
    onWhisperDownloadProgress: updateWhisperDownloadProgress,
    onAudioBytes: (bytes) => {
      audioStats.totalBytes += bytes;
      audioStats.chunks += 1;
      audioStats.lastUpdate = Date.now();
      renderAudioStats(audioStats);
    },
  });
  updateInitialWhisperStatus();
}

function updateSubtitleQueue(words: number) {
  const el = document.querySelector<HTMLParagraphElement>("#subtitle-queue");
  if (!el) return;
  el.textContent = words > 0 ? `Subtitulos pendientes: ${words} palabra(s)` : "";
}

function renderAudioStats(stats: { totalBytes: number; chunks: number; lastUpdate: number }) {
  const el = document.querySelector<HTMLParagraphElement>("#audio-stats");
  if (!el) return;
  const kb = (stats.totalBytes / 1024).toFixed(1);
  const seconds = Math.round((Date.now() - stats.lastUpdate) / 1000);
  el.textContent = `Audio recibido: ${kb} KB en ${stats.chunks} chunk(s) — ultimo hace ${seconds}s`;
}

function updateAudioModeHint(engine: string) {
  const el = document.querySelector<HTMLParagraphElement>("#audio-stats");
  if (!el) return;
  el.textContent =
    engine === LOCAL_ENGINE
      ? "Modo local: esperando audio PCM del celular..."
      : "Modo rapido: los subtitulos llegan desde el reconocimiento del celular.";
}

// Post-procesamiento de captions del speech engine. Web Speech (y otros)
// suelen escuchar acronimos como palabras separadas o con simbolos. Aqui
// reescribimos a la forma esperada antes de mostrar.
//
// Patrones agrupados (insensibles a mayusculas, con bordes de palabra). El
// orden importa: las variaciones especificas van primero.
const CAPTION_NORMALIZATIONS: Array<[RegExp, string]> = [
  // AIEP (institucion docente). Variantes: "a y e p", "a&e", "ayep", "aip",
  // "a i e p", "a e i p", "ayepe".
  [/\ba\s*[&y]\s*e\s*[&y]?\s*p\b/gi, "AIEP"],
  [/\ba\s*[&y]\s*e\b/gi, "AIEP"],
  [/\ba\s*i\s*e\s*p\b/gi, "AIEP"],
  [/\ba\s*e\s*i\s*p\b/gi, "AIEP"],
  [/\bayepe?\b/gi, "AIEP"],
  [/\baiepe?\b/gi, "AIEP"],
  [/\baip\b/gi, "AIEP"],

  // Otros acronimos comunes en docencia tecnica chilena
  [/\bc\s*f\s*t\b/gi, "CFT"],
  [/\bi\s*p\b/gi, "IP"],
  [/\bduoc\s*u\s*c\b/gi, "Duoc UC"],
  [/\binacap\b/gi, "INACAP"],
];

function normalizeCaption(text: string): string {
  if (!text) return text;
  let out = text;
  for (const [re, replacement] of CAPTION_NORMALIZATIONS) {
    out = out.replace(re, replacement);
  }
  // Colapsa espacios duplicados que los reemplazos puedan haber dejado
  return out.replace(/\s+/g, " ").trim();
}

// Paced subtitle: drip-feed the displayed text at a comfortable reading rate
// (~4 words/sec base, up to ~8 wps briefly to catch up). Matches how live
// caption tools (Otter, Live Transcribe) keep subtitles readable when the
// speaker is faster than the reader.
function createPacedSubtitle(
  el: HTMLElement | null,
  opts: { maxVisibleWords?: number; onPendingChange?: (words: number) => void } = {},
) {
  const TICK_MS = 300; // fixed tempo ~= 3.3 words/sec
  const MAX_VISIBLE_WORDS = opts.maxVisibleWords ?? 28;
  const MAX_HISTORY_WORDS = 420;
  const ANCHOR_SEARCH_WORDS = 140;
  const DISTINCTIVE_KEY_LENGTH = 5;

  let visibleWords: string[] = [];
  let pendingWords: string[] = [];
  let acceptedHistory: string[] = [];
  let displayedIsPlaceholder = false;
  let enteringIndex: number | null = null;
  let intervalId: number | null = null;

  const applyText = (text: string) => {
    if (!el) return;
    if (el.textContent === text) return;
    el.textContent = text;
  };

  const applyWords = () => {
    if (!el) return;
    el.innerHTML = visibleWords
      .map((word, index) => {
        const classes = ["subtitle-word"];
        if (index === enteringIndex) classes.push("entering");
        else classes.push("settled");
        if (index === visibleWords.length - 1) classes.push("current-word");
        return `<span class="${classes.join(" ")}">${escapeHtml(word)}</span>`;
      })
      .join(" ");
  };

  const stop = () => {
    if (intervalId !== null) {
      window.clearInterval(intervalId);
      intervalId = null;
    }
  };

  const toWords = (text: string) => text.trim().split(/\s+/).filter(Boolean);

  const wordKey = (word: string) =>
    word
      .toLowerCase()
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .replace(/[^\p{L}\p{N}]/gu, "");

  const sameWordForQueue = (a: string, b: string) => wordKey(a) === wordKey(b);

  const isEmptyWord = (word: string) => wordKey(word).length === 0;

  // Longest suffix of `known` that equals a prefix of `incoming`.
  const findOverlap = (known: string[], incoming: string[]): number => {
    const max = Math.min(known.length, incoming.length);
    for (let n = max; n > 0; n--) {
      let ok = true;
      for (let i = 0; i < n; i++) {
        if (!sameWordForQueue(known[known.length - n + i], incoming[i])) {
          ok = false;
          break;
        }
      }
      if (ok) return n;
    }
    return 0;
  };

  // Web Speech corrige palabras anteriores mientras habla. Cuando una ventana
  // nueva reescribe parte de la frase, buscamos el ultimo ancla ya aceptada y
  // solo encolamos lo que viene despues, en vez de repetir la ventana completa.
  const findNovelTailAfterKnownAnchor = (known: string[], incoming: string[]) => {
    const recentKnown = known.slice(-ANCHOR_SEARCH_WORDS);
    for (let incomingIndex = incoming.length - 1; incomingIndex >= 0; incomingIndex--) {
      const incomingKey = wordKey(incoming[incomingIndex]);
      if (!incomingKey) continue;

      for (let knownIndex = recentKnown.length - 1; knownIndex >= 0; knownIndex--) {
        if (wordKey(recentKnown[knownIndex]) !== incomingKey) continue;

        let matchLength = 0;
        while (
          incomingIndex - matchLength >= 0 &&
          knownIndex - matchLength >= 0 &&
          sameWordForQueue(
            recentKnown[knownIndex - matchLength],
            incoming[incomingIndex - matchLength],
          )
        ) {
          matchLength++;
        }

        const hasContext = matchLength >= 2;
        const hasDistinctiveAnchor = incomingKey.length >= DISTINCTIVE_KEY_LENGTH;
        if (hasContext || hasDistinctiveAnchor) {
          return incoming.slice(incomingIndex + 1);
        }
      }
    }
    return incoming;
  };

  const containsSequence = (haystack: string[], needle: string[]) => {
    if (needle.length === 0 || needle.length > haystack.length) return false;
    for (let start = 0; start <= haystack.length - needle.length; start++) {
      let ok = true;
      for (let offset = 0; offset < needle.length; offset++) {
        if (!sameWordForQueue(haystack[start + offset], needle[offset])) {
          ok = false;
          break;
        }
      }
      if (ok) return true;
    }
    return false;
  };

  const notifyPending = () => {
    opts.onPendingChange?.(pendingWords.length);
  };

  const applyVisible = () => {
    applyWords();
  };

  const tick = () => {
    if (pendingWords.length === 0) {
      stop();
      return;
    }

    if (visibleWords.length >= MAX_VISIBLE_WORDS) {
      visibleWords = [];
    }
    visibleWords.push(pendingWords.shift()!);
    enteringIndex = visibleWords.length - 1;
    applyVisible();
    notifyPending();
  };

  const start = () => {
    if (intervalId !== null) return;
    intervalId = window.setInterval(tick, TICK_MS);
  };

  return {
    set(text: string, opts: { instant?: boolean } = {}) {
      if (opts.instant) {
        visibleWords = toWords(text);
        pendingWords = [];
        acceptedHistory = visibleWords.filter((word) => !isEmptyWord(word)).slice(-MAX_HISTORY_WORDS);
        displayedIsPlaceholder = true;
        enteringIndex = null;
        applyText(text);
        notifyPending();
        stop();
        return;
      }
      // Si lo ultimo mostrado era un placeholder ("Escuchando...") arrancamos
      // limpio en lugar de apendear las palabras nuevas al placeholder.
      if (displayedIsPlaceholder) {
        visibleWords = [];
        pendingWords = [];
        acceptedHistory = [];
        enteringIndex = null;
        displayedIsPlaceholder = false;
      }

      const incoming = toWords(text).filter((word) => !isEmptyWord(word));
      if (incoming.length === 0) {
        return;
      }

      if (containsSequence(acceptedHistory, incoming)) {
        return;
      }

      const overlap = findOverlap(acceptedHistory, incoming);
      const novel = overlap > 0
        ? incoming.slice(overlap)
        : findNovelTailAfterKnownAnchor(acceptedHistory, incoming);
      if (novel.length === 0) return;

      pendingWords.push(...novel);
      acceptedHistory.push(...novel);
      if (acceptedHistory.length > MAX_HISTORY_WORDS) {
        acceptedHistory = acceptedHistory.slice(-MAX_HISTORY_WORDS);
      }
      notifyPending();
      start();
    },
    reset() {
      visibleWords = [];
      pendingWords = [];
      acceptedHistory = [];
      enteringIndex = null;
      displayedIsPlaceholder = false;
      notifyPending();
      stop();
    },
  };
}

function renderOverlay(root: HTMLDivElement) {
  document.documentElement.classList.add("overlay-page");
  document.body.classList.add("overlay-mode");
  root.innerHTML = `
    <section class="overlay-shell">
      <div class="overlay-tools">
        <button id="pause-toggle" type="button">Pausar</button>
        <label>
          Tamano
          <input id="font-size" type="range" min="26" max="58" value="38" />
        </label>
        <button id="close-overlay" class="icon-button" type="button" title="Cerrar overlay">X</button>
      </div>
      <button class="drag-handle" type="button" title="Arrastra para mover" data-tauri-drag-region>Mover</button>
      <div class="subtitle-box" id="subtitle-box" data-tauri-drag-region>Esperando subtitulos...</div>
    </section>
  `;

  let paused = false;
  const subtitle = document.querySelector<HTMLDivElement>("#subtitle-box");
  const pauseButton = document.querySelector<HTMLButtonElement>("#pause-toggle");
  const closeButton = document.querySelector<HTMLButtonElement>("#close-overlay");
  const fontSize = document.querySelector<HTMLInputElement>("#font-size");

  pauseButton?.addEventListener("click", () => {
    paused = !paused;
    pauseButton.textContent = paused ? "Reanudar" : "Pausar";
    document.body.classList.toggle("paused", paused);
  });

  fontSize?.addEventListener("input", () => {
    if (subtitle) subtitle.style.fontSize = `${fontSize.value}px`;
  });

  closeButton?.addEventListener("click", () => {
    invoke("close_overlay");
  });

  const overlayPacer = createPacedSubtitle(subtitle, { maxVisibleWords: 18 });
  wireCaptionListeners({
    onCaption: (payload) => {
      if (paused || !subtitle) return;
      const text = normalizeCaption(payload.text);
      overlayPacer.set(text || "Escuchando...", { instant: !text });
      subtitle.classList.toggle("final", payload.isFinal);
    },
  });
}

function wireCaptionListeners(handlers: {
  onCaption?: (payload: CaptionPayload) => void;
  onStatus?: (status: string) => void;
  onMobileUrl?: (url: string) => void;
  onRelayStatus?: (status: RelayStatus) => void;
  onWhisperStatus?: (status: WhisperStatus) => void;
  onWhisperDownloadProgress?: (progress: DownloadProgressPayload) => void;
  onAudioBytes?: (bytes: number) => void;
}) {
  listen<CaptionPayload>("caption-update", (event) => {
    handlers.onCaption?.(event.payload);
  });

  listen<string>("phone-status", (event) => {
    handlers.onStatus?.(event.payload);
  });

  listen<string>("mobile-url-update", (event) => {
    handlers.onMobileUrl?.(event.payload);
  });

  listen<RelayStatus>("relay-status", (event) => {
    handlers.onRelayStatus?.(event.payload);
  });

  listen<WhisperStatus>("whisper-status", (event) => {
    handlers.onWhisperStatus?.(event.payload);
  });

  listen<DownloadProgressPayload>("whisper-download-progress", (event) => {
    handlers.onWhisperDownloadProgress?.(event.payload);
  });

  listen<number>("audio-bytes", (event) => {
    handlers.onAudioBytes?.(event.payload);
  });
}

function updatePhoneStatus(status: string) {
  const label = document.querySelector<HTMLSpanElement>("#phone-status");
  const dot = document.querySelector<HTMLSpanElement>("#status-dot");
  const normalized = status.toLowerCase();

  if (label) {
    label.textContent =
      normalized === "connected" ||
      normalized.startsWith("speech:") ||
      normalized.startsWith("chunks:") ||
      normalized.startsWith("samplerate:")
        ? "Celular conectado"
        : normalized === "disconnected"
          ? "Celular desconectado"
          : status || "Esperando celular";
  }

  dot?.classList.toggle("online", normalized === "connected");
}

async function updateMobileUrl(url: string) {
  const urlEl = document.querySelector<HTMLParagraphElement>("#mobile-url");
  const qrCanvas = document.querySelector<HTMLCanvasElement>("#qr-code");
  const qrFrame = document.querySelector<HTMLDivElement>("#qr-frame");
  const statusEl = document.querySelector<HTMLParagraphElement>("#qr-status");
  const sessionCodeEl = document.querySelector<HTMLElement>("#session-code");
  const sessionCodeCard = document.querySelector<HTMLDivElement>("#session-code-card");
  const sessionCode = extractSessionCode(url);

  if (urlEl) urlEl.textContent = url;
  if (sessionCodeEl) sessionCodeEl.textContent = sessionCode ?? "------";
  sessionCodeCard?.classList.toggle("has-code", sessionCode != null);
  if (qrCanvas && url.startsWith("https://")) {
    await QRCode.toCanvas(qrCanvas, url, {
      width: 256,
      margin: 1,
      color: {
        dark: "#172026",
        light: "#fffaf0",
      },
    });
    qrFrame?.classList.add("has-qr");
    if (statusEl && statusEl.dataset.state !== "reconnecting") {
      statusEl.textContent = "Listo. Escanea el QR con el celular.";
      statusEl.dataset.state = "online";
    }
  } else {
    qrFrame?.classList.remove("has-qr");
  }
}

async function setBackendCaptureMode(mode: "speech" | "pcm") {
  if (activeCaptureMode === mode) return true;
  try {
    const nextUrl = await invoke<string>("set_capture_mode", { mode });
    activeCaptureMode = mode;
    if (nextUrl) await updateMobileUrl(nextUrl);
    return true;
  } catch (error) {
    console.warn("No se pudo cambiar el modo de captura", error);
    return false;
  }
}

function extractSessionCode(url: string): string | null {
  try {
    const parsed = new URL(url);
    const candidates = [
      parsed.searchParams.get("session"),
      parsed.searchParams.get("sessionId"),
      parsed.searchParams.get("s"),
      parsed.searchParams.get("sid"),
      parsed.searchParams.get("cid"),
    ];
    const fromQuery = candidates.find((value) => value && /^[a-z0-9]{6}$/i.test(value));
    if (fromQuery) return fromQuery.toUpperCase();

    const fromPath = parsed.pathname
      .split("/")
      .reverse()
      .find((part) => /^[a-z0-9]{6}$/i.test(part));
    return fromPath ? fromPath.toUpperCase() : null;
  } catch {
    return null;
  }
}

type RelayStatus = "connecting" | "online" | "reconnecting";
type WhisperStatus =
  | "missing-model"
  | "loading"
  | "downloading-model"
  | "ready"
  | "transcribing"
  | "error";

type DownloadProgressPayload = {
  downloaded: number;
  total?: number;
};

function updateRelayStatus(status: RelayStatus) {
  const el = document.querySelector<HTMLParagraphElement>("#qr-status");
  if (!el) return;
  const hasUrl = !!document
    .querySelector<HTMLDivElement>("#qr-frame")
    ?.classList.contains("has-qr");
  el.textContent =
    status === "online"
      ? hasUrl
        ? "Listo. Escanea el QR con el celular."
        : "Conectado al relay. Esperando sesion..."
      : status === "reconnecting"
        ? "Sin conexion al relay. Reintentando..."
        : "Conectando al relay...";
  el.dataset.state = status;
}

async function updateWhisperModelPath() {
  const el = document.querySelector<HTMLParagraphElement>("#model-path");
  if (!el) return;
  try {
    const path = await invoke<string>("whisper_model_path");
    el.textContent = `Modelo: ${path}`;
  } catch (error) {
    el.textContent = `No se pudo resolver la ruta del modelo: ${String(error)}`;
  }
}

async function updateInitialWhisperStatus() {
  try {
    updateWhisperStatus(await invoke<WhisperStatus>("whisper_status"));
  } catch (error) {
    console.error("No se pudo leer el estado de Whisper", error);
  }
}

function updateWhisperStatus(status: WhisperStatus) {
  latestWhisperStatus = status;
  const el = document.querySelector<HTMLParagraphElement>("#whisper-status");
  const speechEngine = document.querySelector<HTMLSelectElement>("#speech-engine");
  const progress = document.querySelector<HTMLParagraphElement>("#download-progress");
  if (!el) return;

  el.dataset.state = status;
  const localSelected = speechEngine?.value === LOCAL_ENGINE;
  el.textContent = whisperStatusCopy(status, localSelected);

  if (progress && status !== "downloading-model") {
    progress.textContent = "";
  }

  if (speechEngine && localSelected && !isWhisperReadyForPcm(status)) {
    speechEngine.value = FAST_ENGINE;
    const settings = loadSettings();
    settings.speechEngine = FAST_ENGINE;
    saveSettings(settings);
    updateAudioModeHint(FAST_ENGINE);
    setBackendCaptureMode("speech");
  } else if (speechEngine && localSelected && isWhisperReadyForPcm(status)) {
    setBackendCaptureMode("pcm");
  }
}

function whisperStatusCopy(status: WhisperStatus, localSelected: boolean) {
  if (localSelected) {
    return status === "ready"
      ? "Whisper local listo. El celular enviara audio PCM al PC; puede tardar mas que el modo rapido."
      : status === "transcribing"
        ? "Whisper local esta transcribiendo. Si se atrasa, cambia a modo rapido."
        : status === "downloading-model"
          ? "Descargando modelo Whisper local. Mientras tanto se usara el modo rapido del celular."
          : status === "error"
            ? "Whisper tuvo un error. Se volvio al modo rapido del celular."
            : "Preparando Whisper local. Se usara modo rapido hasta que quede listo.";
  }

  return status === "ready" || status === "transcribing"
    ? "Modo rapido activo. Whisper local esta disponible si prefieres privacidad local."
    : status === "downloading-model"
      ? "Modo rapido activo. Descargando Whisper local en segundo plano."
      : status === "error"
        ? "Modo rapido activo. Whisper local tuvo un error."
        : "Modo rapido activo. Preparando Whisper local en segundo plano.";
}

function isWhisperReadyForPcm(status: WhisperStatus) {
  return status === "ready" || status === "transcribing";
}

function updateWhisperDownloadProgress(progress: DownloadProgressPayload) {
  const el = document.querySelector<HTMLParagraphElement>("#download-progress");
  if (!el) return;

  const downloadedMb = progress.downloaded / 1024 / 1024;
  if (progress.total) {
    const totalMb = progress.total / 1024 / 1024;
    const percent = Math.min(100, (progress.downloaded / progress.total) * 100);
    el.textContent = `Descarga del modelo: ${percent.toFixed(0)}% (${downloadedMb.toFixed(1)} de ${totalMb.toFixed(1)} MB)`;
  } else {
    el.textContent = `Descarga del modelo: ${downloadedMb.toFixed(1)} MB`;
  }
}

function loadSettings() {
  const fallback = {
    saveTranscript: true,
    speechEngine: FAST_ENGINE,
  };

  try {
    const parsed = { ...fallback, ...JSON.parse(localStorage.getItem(SETTINGS_KEY) || "{}") };
    if (parsed.speechEngine === "relay-audio") parsed.speechEngine = FAST_ENGINE;
    if (parsed.speechEngine !== FAST_ENGINE && parsed.speechEngine !== LOCAL_ENGINE) {
      parsed.speechEngine = FAST_ENGINE;
    }
    return parsed;
  } catch {
    return fallback;
  }
}

function saveSettings(settings: ReturnType<typeof loadSettings>) {
  localStorage.setItem(SETTINGS_KEY, JSON.stringify(settings));
}

function wireSettings(settings: ReturnType<typeof loadSettings>) {
  const saveTranscript = document.querySelector<HTMLInputElement>("#save-transcript");
  const speechEngine = document.querySelector<HTMLSelectElement>("#speech-engine");

  if (saveTranscript) saveTranscript.checked = settings.saveTranscript;
  if (speechEngine) speechEngine.value = settings.speechEngine;
  updateAudioModeHint(settings.speechEngine);
  setBackendCaptureMode(settings.speechEngine === LOCAL_ENGINE && isWhisperReadyForPcm(latestWhisperStatus) ? "pcm" : "speech");

  saveTranscript?.addEventListener("change", () => {
    settings.saveTranscript = saveTranscript.checked;
    saveSettings(settings);
  });

  speechEngine?.addEventListener("change", () => {
    settings.speechEngine = speechEngine.value;
    if (settings.speechEngine === LOCAL_ENGINE && !isWhisperReadyForPcm(latestWhisperStatus)) {
      settings.speechEngine = FAST_ENGINE;
      speechEngine.value = FAST_ENGINE;
    }
    saveSettings(settings);
    updateAudioModeHint(settings.speechEngine);
    setBackendCaptureMode(settings.speechEngine === LOCAL_ENGINE ? "pcm" : "speech");
    updateWhisperStatus(latestWhisperStatus);
  });
}

function loadTranscript(): TranscriptLine[] {
  try {
    return JSON.parse(localStorage.getItem(TRANSCRIPT_KEY) || "[]");
  } catch {
    return [];
  }
}

// Longest suffix of `a` that equals a prefix of `b`. Mirrors the pacer's
// findOverlap: the phone sends a sliding window of the last ~18 words, so
// consecutive finals heavily overlap with the previous saved line.
function findWordOverlap(a: string[], b: string[]): number {
  const max = Math.min(a.length, b.length);
  for (let n = max; n > 0; n--) {
    let ok = true;
    for (let i = 0; i < n; i++) {
      if (a[a.length - n + i] !== b[i]) {
        ok = false;
        break;
      }
    }
    if (ok) return n;
  }
  return 0;
}

const TRANSCRIPT_MERGE_GAP_MS = 4000;

function maybeSaveTranscript(payload: CaptionPayload) {
  if (!payload.isFinal || !payload.text.trim()) return;

  const settings = loadSettings();
  if (!settings.saveTranscript) return;

  const text = payload.text.trim();
  const lines = loadTranscript();
  const last = lines[lines.length - 1];
  const now = Date.now();

  if (last) {
    const lastWords = last.text.split(/\s+/).filter(Boolean);
    const newWords = text.split(/\s+/).filter(Boolean);
    const overlap = findWordOverlap(lastWords, newWords);

    // El final nuevo está contenido al final del anterior: nada que sumar.
    if (overlap === newWords.length) return;

    const lastTime = new Date(last.time).getTime();
    const withinGap = Number.isFinite(lastTime) && now - lastTime < TRANSCRIPT_MERGE_GAP_MS;

    // Si hay overlap o la pausa fue corta, extendemos la línea existente con
    // sólo el sufijo nuevo en vez de empujar una entrada nueva con repetición.
    if (overlap > 0 || withinGap) {
      const tail = newWords.slice(overlap).join(" ").trim();
      if (!tail) return;
      last.text = `${last.text} ${tail}`.replace(/\s+/g, " ").trim();
      localStorage.setItem(TRANSCRIPT_KEY, JSON.stringify(lines.slice(-500)));
      renderTranscriptLog();
      return;
    }
  }

  lines.push({
    text,
    time: new Date(now).toISOString(),
  });

  localStorage.setItem(TRANSCRIPT_KEY, JSON.stringify(lines.slice(-500)));
  renderTranscriptLog();
}

function renderTranscriptLog() {
  const log = document.querySelector<HTMLDivElement>("#transcript-log");
  if (!log) return;

  const lines = loadTranscript();
  if (lines.length === 0) {
    log.innerHTML = `<p class="empty-log">Todavia no hay transcripcion guardada.</p>`;
    return;
  }

  log.innerHTML = lines
    .slice(-80)
    .map((line) => {
      const time = new Date(line.time).toLocaleTimeString("es-CL", {
        hour: "2-digit",
        minute: "2-digit",
      });
      return `<p><time>${time}</time><span>${escapeHtml(line.text)}</span></p>`;
    })
    .join("");
  log.scrollTop = log.scrollHeight;
}

function downloadTranscript() {
  const lines = loadTranscript();
  const content = lines
    .map((line) => `[${new Date(line.time).toLocaleString("es-CL")}] ${line.text}`)
    .join("\n");
  const blob = new Blob([content || "Sin transcripcion guardada."], { type: "text/plain" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `aiep-subtitulos-${new Date().toISOString().slice(0, 10)}.txt`;
  link.click();
  URL.revokeObjectURL(url);
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}
