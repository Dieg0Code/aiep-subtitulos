import { invoke } from "@tauri-apps/api/core";
import { listen } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
import QRCode from "qrcode";

type CaptionPayload = {
  text: string;
  isFinal: boolean;
};

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
        <div class="qr-frame">
          <canvas id="qr-code" width="256" height="256"></canvas>
        </div>
        <p class="qr-url" id="mobile-url">Preparando enlace local...</p>
        <div class="steps">
          <span>1. Escanear</span>
          <span>2. Aceptar HTTPS local</span>
          <span>3. Iniciar microfono</span>
        </div>
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
    </section>
  `;

  const mobileUrl = await invoke<string>("mobile_url");
  const urlEl = document.querySelector<HTMLParagraphElement>("#mobile-url");
  const qrCanvas = document.querySelector<HTMLCanvasElement>("#qr-code");

  if (urlEl) urlEl.textContent = mobileUrl;
  if (qrCanvas && mobileUrl.startsWith("https://")) {
    await QRCode.toCanvas(qrCanvas, mobileUrl, {
      width: 256,
      margin: 1,
      color: {
        dark: "#172026",
        light: "#fffaf0",
      },
    });
  }

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

  wireCaptionListeners({
    onCaption: (payload) => {
      const preview = document.querySelector<HTMLDivElement>("#preview-text");
      if (preview) preview.textContent = payload.text || "Escuchando...";
    },
    onStatus: updatePhoneStatus,
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
}) {
  listen<CaptionPayload>("caption-update", (event) => {
    handlers.onCaption?.(event.payload);
  });

  listen<string>("phone-status", (event) => {
    handlers.onStatus?.(event.payload);
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
