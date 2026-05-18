import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
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
            <option value="relay-audio">Audio al PC via relay (MVP)</option>
            <option value="whisper-local" disabled>Whisper local (proximamente)</option>
            <option value="openai" disabled>OpenAI speech-to-text (proximamente)</option>
          </select>
        </label>
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
    onAudioBytes: (bytes) => {
      audioStats.totalBytes += bytes;
      audioStats.chunks += 1;
      audioStats.lastUpdate = Date.now();
      renderAudioStats(audioStats);
    },
  });
}

function renderAudioStats(stats: { totalBytes: number; chunks: number; lastUpdate: number }) {
  const el = document.querySelector<HTMLParagraphElement>("#audio-stats");
  if (!el) return;
  const kb = (stats.totalBytes / 1024).toFixed(1);
  const seconds = Math.round((Date.now() - stats.lastUpdate) / 1000);
  el.textContent = `Audio recibido: ${kb} KB en ${stats.chunks} chunk(s) — ultimo hace ${seconds}s`;
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
function createPacedSubtitle(el: HTMLElement | null) {
  const TICK_MS = 240; // base rate ~= 4 words/sec
  const FAST_BACKLOG = 12; // start taking 2 words/tick once this far behind
  const SKIP_BACKLOG = 28; // collapse harder past this many backlog words
  const SKIP_KEEP_TAIL = 12; // how much of the backlog to leave after a skip
  const MAX_DISPLAYED_WORDS = 80; // hard cap so memory + DOM don't grow forever

  let target = "";
  let displayed = "";
  let displayedIsPlaceholder = false;
  let intervalId: number | null = null;

  const apply = (text: string) => {
    if (!el) return;
    if (el.textContent === text) return;
    el.textContent = text;
  };

  const stop = () => {
    if (intervalId !== null) {
      window.clearInterval(intervalId);
      intervalId = null;
    }
  };

  // Longest suffix of `d` that equals a prefix of `t`. Used to track how much
  // of the latest target the displayed text already covers, even when the
  // mobile dedup sliding-window drops words off the front of target.
  const findOverlap = (d: string[], t: string[]): number => {
    const max = Math.min(d.length, t.length);
    for (let n = max; n > 0; n--) {
      let ok = true;
      for (let i = 0; i < n; i++) {
        if (d[d.length - n + i] !== t[i]) {
          ok = false;
          break;
        }
      }
      if (ok) return n;
    }
    return 0;
  };

  const tick = () => {
    if (displayed === target) {
      stop();
      return;
    }
    const tWords = target.trim().split(/\s+/).filter(Boolean);
    if (tWords.length === 0) {
      displayed = target;
      apply(displayed);
      stop();
      return;
    }
    const dWords = displayed.trim().split(/\s+/).filter(Boolean);
    const overlap = findOverlap(dWords, tWords);
    const remaining = tWords.slice(overlap);
    if (remaining.length === 0) {
      stop();
      return;
    }
    // Si el texto mostrado no comparte nada con el target (ej: viene de un
    // placeholder, hubo silencio largo y el dedup movil tiro la ventana
    // entera), arrancamos limpio en vez de apendear "Escuchando..." + nuevo.
    const baseWords = overlap === 0 && dWords.length > 0 ? [] : dWords;
    let take = 1;
    if (remaining.length > FAST_BACKLOG) take = 2;
    if (remaining.length > SKIP_BACKLOG) take = remaining.length - SKIP_KEEP_TAIL;
    const merged = [...baseWords, ...remaining.slice(0, take)];
    if (merged.length > MAX_DISPLAYED_WORDS) {
      merged.splice(0, merged.length - MAX_DISPLAYED_WORDS);
    }
    displayed = merged.join(" ");
    apply(displayed);
  };

  const start = () => {
    if (intervalId !== null) return;
    intervalId = window.setInterval(tick, TICK_MS);
  };

  return {
    set(text: string, opts: { instant?: boolean } = {}) {
      target = text;
      if (opts.instant) {
        displayed = text;
        displayedIsPlaceholder = true;
        apply(displayed);
        stop();
        return;
      }
      // Si lo ultimo mostrado era un placeholder ("Escuchando...") arrancamos
      // limpio en lugar de apendear las palabras nuevas al placeholder.
      if (displayedIsPlaceholder) {
        displayed = "";
        displayedIsPlaceholder = false;
      }
      if (target === displayed) {
        stop();
        return;
      }
      start();
    },
    reset() {
      target = "";
      displayed = "";
      displayedIsPlaceholder = false;
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

  subtitle?.addEventListener("pointerdown", async (event) => {
    if (event.button !== 0) return;
    try {
      await getCurrentWindow().startDragging();
    } catch (error) {
      console.error("No se pudo mover el overlay", error);
    }
  });

  closeButton?.addEventListener("click", () => {
    invoke("close_overlay");
  });

  const overlayPacer = createPacedSubtitle(subtitle);
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
      normalized === "connected"
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

  if (urlEl) urlEl.textContent = url;
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

type RelayStatus = "connecting" | "online" | "reconnecting";

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

function loadSettings() {
  const fallback = {
    saveTranscript: true,
    speechEngine: "relay-audio",
  };

  try {
    return { ...fallback, ...JSON.parse(localStorage.getItem(SETTINGS_KEY) || "{}") };
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

  saveTranscript?.addEventListener("change", () => {
    settings.saveTranscript = saveTranscript.checked;
    saveSettings(settings);
  });

  speechEngine?.addEventListener("change", () => {
    settings.speechEngine = speechEngine.value;
    saveSettings(settings);
  });
}

function loadTranscript(): TranscriptLine[] {
  try {
    return JSON.parse(localStorage.getItem(TRANSCRIPT_KEY) || "[]");
  } catch {
    return [];
  }
}

function maybeSaveTranscript(payload: CaptionPayload) {
  if (!payload.isFinal || !payload.text.trim()) return;

  const settings = loadSettings();
  if (!settings.saveTranscript) return;

  const lines = loadTranscript();
  const lastLine = lines[lines.length - 1];
  const text = payload.text.trim();
  if (lastLine?.text === text) return;

  lines.push({
    text,
    time: new Date().toISOString(),
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
