# aiep-subtitulos-relay

Go WebSocket relay que empareja el celular del docente con la app de escritorio Tauri. Se despliega en Railway. El cliente Tauri actúa como **host** y el celular como **guest**; el relay sólo reenvía frames entre ambos.

## Endpoints

| Path | Quien lo usa | Qué hace |
|---|---|---|
| `GET /healthz` | Railway healthcheck | Devuelve `ok`. |
| `GET /m?s=<id>` | Celular | Sirve `mobile.html` (UI de captura de audio). |
| `GET /ws/host` | Tauri PC | Upgrade a WS; relay responde con `{"type":"session","id":"ABCDEF"}` y forwardea cualquier frame del guest pareado. |
| `GET /ws/guest?s=<id>` | Celular | Upgrade a WS; relay forwardea todo lo que envíe (binario o texto JSON) al host. |

Protocolo en detalle: ver `hub.go`.

## Desarrollo local

```bash
cd server
go test ./...        # unit tests del hub
go run .             # arranca en :8080 (override con PORT=9000)
```

Smoke manual: `curl http://127.0.0.1:8080/healthz` → `ok`.

## Tests

```bash
go test -race -count=1 ./...
```

Cobertura en `hub_test.go`:

- Generación de IDs (longitud/alphabet/unicidad).
- Pairing host↔guest con relay bidireccional de binario y texto.
- Eventos `phone-connected` / `phone-disconnected` al host.
- Rechazo de segundo guest (close 1008).
- 404 / 400 cuando falta o no existe el `s=`.
- Cierre del host arrastra al guest (close 1001) y limpia la sesión.

CI corre los tests con `-race` en GitHub Actions (`.github/workflows/relay-ci.yml`).

## Deploy a Railway

### Setup inicial (una sola vez)

```powershell
cd server
railway login
railway init --name aiep-subtitulos-relay
railway add --service aiep-relay
railway up --service aiep-relay
railway domain --service aiep-relay
```

URL pública actual: `https://aiep-relay-production.up.railway.app`.

Despues de cada despliegue, valida que la vista movil responda con la UI actual:

```powershell
Invoke-WebRequest -UseBasicParsing "https://aiep-relay-production.up.railway.app/m?s=TEST&mode=pcm"
```

La seccion "Motor de captura" debe mostrar que el modo lo define el PC docente, sin selector manual en el celular.

### Auto-deploy con GitHub (recomendado)

1. Railway dashboard → proyecto `aiep-subtitulos-relay` → service `aiep-relay` → **Settings → Source**.
2. Conectar repo `Dieg0Code/aiep-subtitulos`.
3. **Root Directory**: `server`.
4. **Branch**: `main`.
5. Watch paths: dejar default (todo el root directory).

Con esto cada push a `main` que toque `server/**` redespliega automáticamente. El workflow `relay-ci` valida tests antes del merge.

### Deploy manual (fallback)

```powershell
cd server
railway up --service aiep-relay --detach
```

## Configuración

| Variable | Default | Notas |
|---|---|---|
| `PORT` | `8080` | Railway lo inyecta automáticamente, no hace falta setear. |

## Observabilidad

Logs estructurados en JSON a stdout (slog). Visibles desde Railway dashboard → service → Deployments → último → Logs. Eventos relevantes: `host connected`, `host disconnected`, `guest connected`, `guest disconnected`, `req` (cada request HTTP con método/path/duración).

## Privacidad

El audio del celular pasa por este relay en tránsito (binario opus en WebSocket). **No se almacena**: los frames se reenvían y se descartan. No hay logs del contenido, sólo metadata (timestamps, byte counts no se guardan).
