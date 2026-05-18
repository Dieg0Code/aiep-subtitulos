# AIEP Subtitulos

App de escritorio inclusiva para docentes: muestra subtitulos flotantes sobre cualquier presentacion, navegador o aplicacion. El celular funciona como microfono y el PC muestra el texto como overlay siempre visible.

El audio del celular viaja al PC a traves de un relay WebSocket publico en Railway, asi no importa si el celular esta en otra red o usando datos moviles. No requiere instalar `cloudflared`, no muestra advertencias de certificado y no depende de la red local.

Repositorio con dos componentes: **app de escritorio Tauri** (frontend + Rust) y **relay Go** desplegado en Railway.

## Que hace

- Abre la app de escritorio en el PC.
- Se conecta al relay y obtiene un ID de sesion.
- Muestra un QR con la URL del celular: `https://aiep-relay-production.up.railway.app/m?s=<id>`.
- El celular abre esa URL (HTTPS valido), pide permiso de microfono y empieza a enviar audio.
- El PC recibe el audio en vivo y mantiene un contador para verificar el flujo.
- Muestra el overlay flotante always-on-top con los subtitulos (cuando este Whisper integrado en F2).
- Permite mostrar, ocultar, cerrar, mover y reposicionar el overlay.
- Guarda localmente un registro de lo transcrito y permite descargarlo como TXT.

## Estado del MVP

Esta version logra el flujo completo de captura y transporte de audio:

- F1 cerrado: celular captura audio con `MediaRecorder(opus)`, lo envia en chunks binarios al relay, el relay lo forwardea al PC. Se observa con el contador `Audio recibido: X KB en N chunks`.
- F2 pendiente: integrar `whisper-rs` en el PC para convertir esos chunks en subtitulos. Hasta entonces el preview muestra "Esperando subtitulos..." pero el audio si esta llegando.
- F3 pendiente: ventana deslizante / interim captions.

Importante:

- No se guarda audio en ningun lado (ni PC, ni relay).
- El audio pasa por el relay en transito (opus en WebSocket). No se almacena.
- El overlay es una ventana Tauri transparente, flotante y movible.

## Requisitos

- Windows 10/11.
- Node.js 20 o superior, npm 10 o superior.
- Rust y Cargo instalados.
- WebView2 Runtime (suele venir con Windows moderno).
- Conexion a internet (el relay vive en Railway).
- Un celular con Chrome en Android (o Safari iOS) para escanear el QR.

```powershell
node --version
npm --version
rustc --version
cargo --version
```

## Instalacion

```powershell
git clone https://github.com/Dieg0Code/aiep-subtitulos.git
cd aiep-subtitulos
npm install
```

El relay Go se instala aparte solo si quieres modificarlo localmente; ver `server/README.md`.

## Ejecutar en desarrollo

```powershell
npm run tauri dev
```

Levanta:

- Vite en `http://localhost:1420` (UI Tauri).
- La app de escritorio Tauri.
- Cliente WebSocket conectado a `wss://aiep-relay-production.up.railway.app/ws/host`.

La primera compilacion de Rust toma varios minutos.

## Uso

1. Abre la app con `npm run tauri dev`.
2. Espera el QR (1-2 segundos despues de conectar al relay).
3. Escanea el QR con el celular.
4. En el celular acepta permisos de microfono.
5. Presiona "Iniciar captura".
6. Habla cerca del celular.
7. En la app del PC el contador "Audio recibido" sube cada ~1.5s y el chip de estado dice "Celular conectado".

Si pierdes la conexion, el cliente Tauri reintenta con backoff exponencial y, al reconectar, obtiene un nuevo session ID; el QR se regenera y debes volver a escanearlo en el celular.

## Configuracion

| Variable | Default | Notas |
|---|---|---|
| `AIEP_RELAY_URL` | `https://aiep-relay-production.up.railway.app` | URL base del relay. La app deriva `wss://.../ws/host` y construye la URL movil `<base>/m?s=<id>`. Util para apuntar a un relay staging o local. |
| `RUST_LOG` | `info` | Nivel de log Rust (`tracing_subscriber`). Usa `debug` o `trace` para diagnosticar problemas del cliente WS. |

Ejemplo Powershell:

```powershell
$env:AIEP_RELAY_URL = "http://127.0.0.1:8080"
$env:RUST_LOG = "debug"
npm run tauri dev
```

## Controles del overlay

Desde la ventana principal:

- "Mostrar": muestra o recrea el overlay.
- "Ocultar": oculta el overlay.
- "Abajo": reposiciona el overlay en la parte inferior del monitor primario.

Desde el overlay:

- Arrastra el bloque negro para moverlo.
- Pasa el mouse sobre el overlay para ver controles.
- "Pausar": congela los subtitulos.
- "Tamano": cambia el tamano del texto.
- "X": oculta el overlay.

## Registro de transcripcion

- El registro queda en `localStorage` del webview Tauri.
- Solo se llena cuando el celular envia texto via "Modo respaldo escrito" (textarea manual). El audio capturado se almacena en ninguna parte.
- Puedes desactivar "Guardar lo transcrito en esta app" en Opciones.
- Puedes descargar el registro como `.txt` o limpiarlo desde la app.

## Build

```powershell
npm run build              # frontend Vite + tsc
cd src-tauri; cargo check  # validar Rust
npm run tauri build        # instalador/app de escritorio
```

Artefactos en `src-tauri/target/release/bundle/`.

## Arquitectura

```mermaid
flowchart LR
  Phone["Celular / Chrome Android"] -- WSS audio opus --> Relay["Railway Go relay"]
  Relay -- WSS audio + status --> Tauri["PC Tauri WS client"]
  Tauri --> Overlay["Overlay flotante"]
  Tauri --> Whisper["Whisper local (F2)"]
  Whisper --> Overlay
```

Protocolo del relay (`server/main.go`, `server/hub.go`):

- **Host** (Tauri PC) conecta a `wss://<relay>/ws/host`. El relay responde con `{"type":"session","id":"ABC123"}` y forwardea todos los frames que llegan del guest.
- **Guest** (celular) conecta a `wss://<relay>/ws/guest?s=<id>`. El relay forwardea binario (audio opus) y JSON al host. Al parear / despareceer, el relay inyecta al host `{"type":"status","state":"phone-connected|phone-disconnected"}`.

Ver `server/README.md` para el detalle del relay.

## Estructura

- `src/main.ts`: UI principal y overlay, listeners de eventos Tauri.
- `src/styles.css`: estilos.
- `src-tauri/src/lib.rs`: bootstrap Tauri, comandos del overlay, monta el cliente del relay.
- `src-tauri/src/relay.rs`: cliente WebSocket, reconexion exponencial, heartbeat 30s, ruteo de frames a eventos Tauri.
- `src-tauri/tauri.conf.json`: ventanas Tauri (`main` y `overlay`).
- `server/`: codigo Go del relay (deploy Railway, tests, Dockerfile).
- `.github/workflows/relay-ci.yml`: CI del relay (vet + test -race + build).

## Privacidad

- No se guarda audio en disco ni en PC ni en el relay.
- El audio pasa por el relay (Railway, region `us-east`) en transito como WebSocket binario opus. No se persiste.
- La transcripcion del modo respaldo escrito se guarda localmente solo si la opcion esta activa.
- Los logs del relay registran metadata (timestamps, IPs en `RemoteAddr`, byte counts no se loguean), no contenido.

Si necesitas un modelo 100% offline, queda en el roadmap: integracion de Whisper local en F2 + opcion de modo aula offline (relay en LAN, fuera de scope MVP).

## Solucion de problemas

### El QR no aparece

- Revisa `RUST_LOG=debug npm run tauri dev` y busca `relay: connecting` / `relay: connect failed`.
- Verifica que tienes internet: `curl https://aiep-relay-production.up.railway.app/healthz` debe devolver `ok`.
- Si el relay esta caido, el cliente reintenta cada 1-30s; verifica desde el dashboard de Railway.

### El celular no abre el QR

- Asegurate de tener conexion a internet en el celular (red o datos moviles, no importa cual).
- El QR codifica una URL `https://aiep-relay-production.up.railway.app/m?s=...` — debe abrirse sin warning de certificado en Chrome.

### Audio no llega al PC

- Verifica que el chip de estado del celular dice "Conectado al PC" (verde).
- En el celular, asegurate de haber dado permiso de microfono.
- El contador "Audio recibido" en el PC debe subir cada ~1.5s mientras hables.
- Si el celular se bloquea, el navegador puede suspender la captura tras unos minutos; vuelve a desbloquear y la captura se reanuda automaticamente.

### El overlay no aparece

- Usa "Mostrar overlay". Si aparece fuera de lugar, usa "Abajo".

### El overlay no se mueve

- Arrastra el bloque negro del subtitulo, no los botones. Usa la API `startDragging` de Tauri.

## Roadmap

- **F2**: integrar `whisper-rs` (whisper.cpp embebido) en el PC para transcribir los chunks de audio en tiempo real.
- **F3**: ventana deslizante con interim captions para feel de tiempo real.
- **F4**: descarga / setup del modelo Whisper al primer uso.
- Opcion de proveedor cloud configurable (OpenAI / Deepgram) como alternativa a Whisper local.
- Guardar preferencias locales de posicion, tamano y opacidad del overlay.
- Empaquetado instalable para docentes no tecnicos.

## Licencia

MIT.
