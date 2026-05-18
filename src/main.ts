import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
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
      <div class="brand-panel">
        <p class="eyebrow">AIEP inclusivo</p>
        <h1>Subtitulos flotantes para clases presenciales</h1>
        <p class="intro">
          Escanea el QR con el celular docente. La voz se convierte en texto y aparece en una ventana flotante sobre PowerPoint, navegador o cualquier app.
        </p>
        <div class="status-row">
          <span class="status-dot" id="status-dot"></span>
          <span id="phone-status">Esperando celular</span>
        </div>
      </div>

      <section class="qr-panel" aria-label="Conexion del celular">
        <p class="section-label">Conexion del celular</p>
        <div class="qr-frame">
          <canvas id="qr-code" width="256" height="256"></canvas>
        </div>
        <p class="qr-url" id="mobile-url">Preparando enlace local...</p>
        <div class="connection-actions">
          <button id="start-cloudflare" type="button">Activar enlace publico</button>
          <button id="stop-cloudflare" type="button">Usar red local</button>
        </div>
        <p class="connection-status" id="connection-status">Cloudflare Tunnel es el modo recomendado si el celular no esta en la misma red.</p>
        <div class="steps">
          <span>1. Escanear</span>
          <span>2. Aceptar microfono</span>
          <span>3. Iniciar microfono</span>
        </div>
      </section>

      <section class="settings-panel">
        <p class="section-label">Opciones</p>
        <label class="field">
          Modo de conexion
          <select id="connection-mode">
            <option value="public">Enlace publico Cloudflare</option>
            <option value="local">Red local</option>
          </select>
        </label>
        <label class="field">
          Motor de transcripcion
          <select id="speech-engine">
            <option value="web-speech">Web Speech del celular (MVP)</option>
            <option value="whisper-local" disabled>Whisper local (proximamente)</option>
            <option value="openai" disabled>OpenAI speech-to-text (proximamente)</option>
          </select>
        </label>
        <label class="check-field">
          <input id="save-transcript" type="checkbox" />
          Guardar lo transcrito en esta app
        </label>
      </section>

      <section class="caption-preview">
        <div class="section-header">
          <p class="section-label">Vista previa</p>
          <div class="overlay-actions" aria-label="Controles del overlay">
            <button id="show-overlay" type="button">Mostrar overlay</button>
            <button id="hide-overlay" type="button">Ocultar</button>
            <button id="reset-overlay" type="button">Enviar abajo</button>
          </div>
        </div>
        <p class="overlay-command-status" id="overlay-command-status">Overlay listo.</p>
        <div class="preview-subtitle" id="preview-text">Los subtitulos apareceran aqui.</div>
      </section>

      <section class="transcript-panel">
        <div class="section-header">
          <p class="section-label">Registro de la clase</p>
          <div class="overlay-actions">
            <button id="download-transcript" type="button">Descargar TXT</button>
            <button id="clear-transcript" type="button">Limpiar</button>
          </div>
        </div>
        <div class="transcript-log" id="transcript-log"></div>
      </section>
    </section>
  `;

  const mobileUrl = await invoke<string>("mobile_url");
  await updateMobileUrl(mobileUrl);
  wireSettings(settings);
  renderTranscriptLog();
  initializeConnection(settings);

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

  document.querySelector<HTMLButtonElement>("#start-cloudflare")?.addEventListener("click", () => {
    startCloudflareTunnel(true);
  });

  document.querySelector<HTMLButtonElement>("#stop-cloudflare")?.addEventListener("click", () => {
    stopCloudflareTunnel(true);
  });

  document.querySelector<HTMLButtonElement>("#clear-transcript")?.addEventListener("click", () => {
    localStorage.setItem(TRANSCRIPT_KEY, "[]");
    renderTranscriptLog();
  });

  document.querySelector<HTMLButtonElement>("#download-transcript")?.addEventListener("click", () => {
    downloadTranscript();
  });

  wireCaptionListeners({
    onCaption: (payload) => {
      const preview = document.querySelector<HTMLDivElement>("#preview-text");
      if (preview) preview.textContent = payload.text || "Escuchando...";
      maybeSaveTranscript(payload);
    },
    onStatus: updatePhoneStatus,
    onMobileUrl: updateMobileUrl,
    onTunnelStatus: updateConnectionStatus,
  });
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

  wireCaptionListeners({
    onCaption: (payload) => {
      if (paused || !subtitle) return;
      subtitle.textContent = payload.text || "Escuchando...";
      subtitle.classList.toggle("final", payload.isFinal);
    },
  });
}

function wireCaptionListeners(handlers: {
  onCaption?: (payload: CaptionPayload) => void;
  onStatus?: (status: string) => void;
  onMobileUrl?: (url: string) => void;
  onTunnelStatus?: (status: string) => void;
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

  listen<string>("tunnel-status", (event) => {
    handlers.onTunnelStatus?.(event.payload);
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
  }
}

function updateConnectionStatus(status: string) {
  const statusEl = document.querySelector<HTMLParagraphElement>("#connection-status");
  if (statusEl) statusEl.textContent = status;
}

function loadSettings() {
  const fallback = {
    connectionMode: "public",
    saveTranscript: true,
    speechEngine: "web-speech",
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
  const connectionMode = document.querySelector<HTMLSelectElement>("#connection-mode");
  const saveTranscript = document.querySelector<HTMLInputElement>("#save-transcript");
  const speechEngine = document.querySelector<HTMLSelectElement>("#speech-engine");

  if (connectionMode) connectionMode.value = settings.connectionMode;
  if (saveTranscript) saveTranscript.checked = settings.saveTranscript;
  if (speechEngine) speechEngine.value = settings.speechEngine;

  connectionMode?.addEventListener("change", () => {
    settings.connectionMode = connectionMode.value;
    saveSettings(settings);
    if (connectionMode.value === "public") {
      startCloudflareTunnel(false);
    } else {
      stopCloudflareTunnel(false);
    }
  });

  saveTranscript?.addEventListener("change", () => {
    settings.saveTranscript = saveTranscript.checked;
    saveSettings(settings);
  });

  speechEngine?.addEventListener("change", () => {
    settings.speechEngine = speechEngine.value;
    saveSettings(settings);
  });
}

async function initializeConnection(settings: ReturnType<typeof loadSettings>) {
  if (settings.connectionMode === "public") {
    await startCloudflareTunnel(false);
  } else {
    updateConnectionStatus("Modo local activo. El celular debe estar en la misma red.");
  }
}

async function startCloudflareTunnel(persist: boolean) {
  const mode = document.querySelector<HTMLSelectElement>("#connection-mode");
  if (persist && mode) {
    const settings = loadSettings();
    settings.connectionMode = "public";
    mode.value = "public";
    saveSettings(settings);
  }

  updateConnectionStatus("Revisando cloudflared...");
  try {
    const version = await invoke<string>("check_cloudflared");
    updateConnectionStatus(`cloudflared detectado (${version}). Iniciando enlace publico...`);
    const message = await invoke<string>("start_public_tunnel");
    updateConnectionStatus(message);
  } catch (error) {
    updateConnectionStatus(
      `No se pudo activar Cloudflare Tunnel. Se mantiene modo local. Detalle: ${String(error)}`,
    );
    const settings = loadSettings();
    settings.connectionMode = "local";
    saveSettings(settings);
    if (mode) mode.value = "local";
  }
}

async function stopCloudflareTunnel(persist: boolean) {
  const mode = document.querySelector<HTMLSelectElement>("#connection-mode");
  if (persist && mode) {
    const settings = loadSettings();
    settings.connectionMode = "local";
    mode.value = "local";
    saveSettings(settings);
  }

  try {
    const message = await invoke<string>("stop_public_tunnel");
    updateConnectionStatus(message);
  } catch (error) {
    updateConnectionStatus(`No se pudo detener el tunnel: ${String(error)}`);
  }
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
