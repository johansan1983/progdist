# SuperChat

Plataforma de chat corporativo con arquitectura de microservicios para demostración universitaria.

Documento de arquitectura para presentación:

- docs/ARQUITECTURA_SUPERCHAT.md

## Stack

- Java 21 (Temurin)
- Spring Boot 3.5 / Spring Cloud 2025.0.2
- Spring Cloud Gateway (API Gateway + rate limiting)
- Spring Cloud Config Server
- Keycloak 26.6.1 (OAuth2/OIDC, emisión de JWT)
- RabbitMQ 4 (mensajería async + relay STOMP)
- PostgreSQL 16 (persistencia)
- Redis 7 (rate limiting y presencia)
- MinIO (almacenamiento de archivos adjuntos)
- Frontend HTML/CSS/JS con Nginx
- Prometheus + Grafana (observabilidad)
- Dozzle (visor de logs de contenedores en tiempo real)
- Docker Compose

## Arquitectura

| Servicio | Puerto | Rol |
|---|---|---|
| `keycloak` | 8080 | Proveedor de identidad (OAuth2/OIDC, JWT) |
| `config-server` | 8888 | Spring Cloud Config Server |
| `api-gateway` | 8090 | Entrada única: validación JWT, rate limiting, routing |
| `chat-service` | 8082 | Mensajes, WebSocket STOMP, presencia |
| `user-service` | 8083 | Perfiles de usuario, salas |
| `notification-service` | 8084 | Notificaciones asíncronas |
| `worker-service` | 8085 | Consumidor RabbitMQ → relay STOMP/WebSocket, simulación de falla |
| `frontend` | 3000 | Nginx + SPA |
| `rabbitmq` | 5672/61613/15672 | Broker de eventos + relay STOMP |
| `postgres` | 5432 | Bases de datos separadas por servicio |
| `redis` | 6379 | Rate limiting y presencia |
| `minio` | 9000/9001 | Almacenamiento de archivos adjuntos |
| `prometheus` | 9090 | Recolección de métricas |
| `grafana` | 3001 | Dashboards |
| `dozzle` | 9999 | Visor de logs de contenedores |
| `portainer` | 9080 | UI de administración de contenedores Docker |

Flujo principal de mensaje en tiempo real:

1. Usuario inicia sesión en el frontend via Keycloak (ROPC).
2. Frontend llama a `POST /api/chat/messages` con JWT Bearer.
3. API Gateway valida el JWT, aplica rate limiting, enruta a chat-service.
4. chat-service guarda el mensaje en PostgreSQL.
5. chat-service publica evento en RabbitMQ (`chat.exchange` → `chat.messages.queue`).
6. worker-service consume la cola y retransmite al topic WebSocket `/topic/conversations/{id}` via `amq.topic`.
7. Simultáneamente chat-service publica en `notifications.exchange` → notification-service guarda la notificación.
8. Frontend recibe el evento en tiempo real via STOMP/WebSocket.

## Despliegue desde cero con un solo comando

Si tienes una WSL2 (Ubuntu/Debian) o un Linux apt-based recién creado **sin nada instalado**, todo el sistema se levanta con:

```bash
git clone <url-del-repo> && cd progdist
make bootstrap
```

O equivalentemente:

```bash
bash scripts/bootstrap.sh
```

`scripts/bootstrap.sh` hace todo de punta a punta:

1. Detecta el SO y si está corriendo dentro de WSL2.
2. Instala dependencias del sistema (`curl`, `gpg`, `make`, `ca-certificates`).
3. Instala Docker Engine + Docker Compose v2 desde el repo oficial de Docker (si no estaban).
4. Arranca el daemon de Docker (systemd, `service` o `dockerd` directo según lo que aplique).
5. Agrega el usuario actual al grupo `docker`.
6. Crea el volumen externo `rabbitmq_data`.
7. Ejecuta `docker compose up -d --build` (compila los 6 servicios Spring y baja todas las imágenes).
8. Espera a que Keycloak esté listo (~90–120s en el primer arranque).
9. Imprime las URLs, credenciales y usuarios precargados.

Es idempotente: se puede re-ejecutar sin romper nada. Si sólo quieres instalar deps sin levantar el stack:

```bash
bash scripts/bootstrap.sh --no-up
```

> **WSL2 sin systemd:** si el daemon no arranca, habilita systemd creando `/etc/wsl.conf` con
> ```ini
> [boot]
> systemd=true
> ```
> y luego desde Windows: `wsl --shutdown`. El script lo detecta y te avisa si falta.

---

## Instalación de dependencias (Linux / WSL2)

### Docker Engine + Docker Compose V2

Todo el stack corre en contenedores. Docker es el único requisito obligatorio para levantar el proyecto.

```bash
# 1. Instalar dependencias del sistema
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release

# 2. Agregar el repositorio oficial de Docker
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu $(lsb_release -cs) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# 3. Instalar Docker Engine y el plugin Compose
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 4. Permitir ejecutar Docker sin sudo (requiere cerrar y reabrir sesión)
sudo usermod -aG docker $USER

# 5. Verificar instalación
docker --version
docker compose version
```

> En WSL2, después del `usermod` cierra y vuelve a abrir la terminal (o ejecuta `newgrp docker`).

### Java 21 y Maven (solo para desarrollo local)

Solo necesario si quieres compilar o ejecutar tests de un servicio fuera de Docker. El `docker compose up --build` compila todo internamente.

```bash
# Java 21 (Temurin) via SDKMAN — recomendado
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.7-tem
sdk install maven 3.9.9

# Verificar
java -version
mvn -version
```

Alternativa sin SDKMAN:

```bash
sudo apt-get install -y maven
# Nota: el Maven de apt puede traer OpenJDK en lugar de Temurin.
# Para Temurin: https://adoptium.net/installation/linux/
```

---

## Levantar el proyecto

Requisitos previos:

```bash
# Verificar Docker Compose V2
docker compose version

# Crear volumen externo de RabbitMQ (solo la primera vez)
docker volume create rabbitmq_data
```

> **Nota:** Si actualizas desde una versión anterior elimina el volumen de Postgres:
> `docker volume rm progdist_postgres_data`

Levantar todo:

```bash
docker compose up -d --build
```

Keycloak tarda 90–120 segundos en el primer arranque.

### Usuarios de prueba precargados

El realm `superchat` se importa automáticamente al primer arranque con 6 usuarios ya listos para usar (todos con password `demo123`):

| Username | Email | Password |
|---|---|---|
| `alice` | alice@superchat.local | `demo123` |
| `bob` | bob@superchat.local | `demo123` |
| `charlie` | charlie@superchat.local | `demo123` |
| `dave` | dave@superchat.local | `demo123` |
| `eve` | eve@superchat.local | `demo123` |
| `frank` | frank@superchat.local | `demo123` |

Estos usuarios están definidos en `keycloak/superchat-realm.json` y se importan vía el flag `--import-realm` del comando de Keycloak.

> **Nota:** Keycloak sólo importa el realm si todavía no existe en su base de datos. Si ya levantaste el stack antes de que el realm tuviera usuarios, ejecuta `make reseed-keycloak` para reimportar (esto recrea la BD de Keycloak; **no** afecta los datos de chat, perfiles ni notificaciones).

Si necesitas agregar usuarios extra a mano: Keycloak Admin Console (`http://localhost:8080`, admin/admin → realm `superchat` → Users → Add user).

Validar estado:

```bash
docker compose ps
```

## URLs

| URL | Descripción | Credenciales |
|---|---|---|
| http://localhost:3000 | Frontend SPA | (usuarios Keycloak) |
| http://localhost:8080 | Keycloak Admin | admin / admin |
| http://localhost:8090 | API Gateway | — |
| http://localhost:8888 | Config Server | — |
| http://localhost:15672 | RabbitMQ Management | superchat / superchat123 |
| http://localhost:9000 | MinIO API | superchat / superchat123 |
| http://localhost:9001 | MinIO Console | superchat / superchat123 |
| http://localhost:9090 | Prometheus | — |
| http://localhost:3001 | Grafana | admin / admin |
| http://localhost:9999 | Dozzle (logs) | — |
| http://localhost:9080 | Portainer | (crear admin en el primer acceso) |
| http://localhost:8082/docs | Chat Swagger | — |
| http://localhost:8083/docs | User Swagger | — |
| http://localhost:8084/docs | Notification Swagger | — |
| http://localhost:8085/docs | Worker Swagger | — |

## Troubleshooting rápido

Si `chat-service`, `worker-service` o `notification-service` están en `Restarting`:

```bash
docker compose logs --tail=50 chat-service
docker compose logs --tail=50 notification-service
```

También puedes ver los logs en tiempo real desde Dozzle en http://localhost:9999.

Causas frecuentes:
- RabbitMQ sin el usuario `superchat` (ocurre si el volumen ya existía con otro usuario). Solución:
  ```bash
  curl -u guest:guest -X PUT http://localhost:15672/api/users/superchat \
    -H "Content-Type: application/json" \
    -d '{"password":"superchat123","tags":"administrator"}'
  curl -u guest:guest -X PUT "http://localhost:15672/api/permissions/%2F/superchat" \
    -H "Content-Type: application/json" \
    -d '{"configure":".*","write":".*","read":".*"}'
  docker compose restart chat-service worker-service notification-service
  ```
- chat-service o worker-service en 502 via gateway: esperar a que estén `healthy` o revisar migración Flyway.

## Endpoints

Todos los endpoints de negocio pasan por el API Gateway en `http://localhost:8090/api/`.

**Chat:**

- `GET  /api/chat/conversations/{id}/messages`
- `POST /api/chat/conversations`
- `POST /api/chat/messages`
- `POST /api/chat/attachments/presign` — genera URL prefirmada para subir archivos a MinIO
- `GET  /api/chat/presence`

**Worker:**

- `GET  /api/worker/simulation/realtime-publisher/status`
- `POST /api/worker/simulation/realtime-publisher/fail`
- `POST /api/worker/simulation/realtime-publisher/restore`

**Usuarios:**

- `GET  /api/users/me` — devuelve (y auto-crea) el perfil del usuario autenticado
- `PUT  /api/users/me` — actualiza el perfil
- `GET  /api/users/{id}` — perfil de otro usuario
- `GET  /api/users/search` — búsqueda de usuarios

**Salas:**

- `POST /api/rooms` — crear sala
- `GET  /api/rooms` — listar salas
- `GET  /api/rooms/{id}/members` — listar miembros
- `POST /api/rooms/{id}/members` — agregar miembro

**Notificaciones:**

- `GET /api/notifications`
- `GET /api/notifications/unread-count`
- `PUT /api/notifications/{id}/read`

**WebSocket/STOMP:**

- Endpoint SockJS: `/ws` (va directo a chat-service, bypass del gateway)
- Topic mensajes: `/topic/conversations/{conversationId}`
- Topic typing: `/topic/conversations/{conversationId}/typing`
- App destination typing: `/app/typing`

## Ejemplo rápido con curl

Obtener token desde Keycloak (requiere usuario creado en el realm `superchat`):

```bash
TOKEN=$(curl -sS -X POST http://localhost:8080/realms/superchat/protocol/openid-connect/token \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  -d 'client_id=superchat-frontend&username=alice&password=demo123&grant_type=password' \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])")
```

Crear conversación:

```bash
curl -sS -X POST http://localhost:8090/api/chat/conversations \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"General"}'
```

Enviar mensaje:

```bash
curl -sS -X POST http://localhost:8090/api/chat/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":1,"content":"Hola mundo"}'
```

Listar mensajes:

```bash
curl -sS http://localhost:8090/api/chat/conversations/1/messages \
  -H "Authorization: Bearer $TOKEN"
```

Perfil de usuario (se auto-crea en el primer acceso):

```bash
curl -sS http://localhost:8090/api/users/me \
  -H "Authorization: Bearer $TOKEN"
```

Consultar presencia:

```bash
curl -sS http://localhost:8090/api/chat/presence \
  -H "Authorization: Bearer $TOKEN"
```

Notificaciones pendientes:

```bash
curl -sS http://localhost:8090/api/notifications/unread-count \
  -H "Authorization: Bearer $TOKEN"
```

Simular falla del publicador en tiempo real:

```bash
curl -sS -X POST http://localhost:8090/api/worker/simulation/realtime-publisher/fail \
  -H "Authorization: Bearer $TOKEN"
```

Restablecer publicador:

```bash
curl -sS -X POST http://localhost:8090/api/worker/simulation/realtime-publisher/restore \
  -H "Authorization: Bearer $TOKEN"
```

## Verificación de eventos RabbitMQ

```bash
curl -u superchat:superchat123 http://localhost:15672/api/queues/%2F/chat.messages.queue
```

Revisa los campos `messages` (pendientes) y `message_stats.publish` (total publicados).

## Funcionalidades UI implementadas

- Sesión persistente en navegador (auto-refresh de token Keycloak).
- Botón de cerrar sesión.
- Indicador de "escribiendo..." en tiempo real.
- Scroll fijo del chat con posición inicial en el último mensaje.
- Sidebar derecho con cantidad de usuarios conectados y sus nombres.
- Envío de archivos adjuntos (imágenes, audio, documentos) via MinIO presigned URLs.
- Mensajes de voz grabados directamente desde el navegador.
- Mensajes de un solo uso (view-once): se auto-eliminan 5 segundos después de ser leídos.
- Lightbox de imágenes al hacer clic.
- Envío de mensaje con Enter.
- Selector de emojis.

## Identidad JWT

El identificador canónico de usuario en todos los servicios es el claim `sub` del JWT de Keycloak (UUID). El claim `preferred_username` lleva el nombre visible. Cada servicio valida JWTs contra el endpoint JWKS de Keycloak (`/realms/superchat/protocol/openid-connect/certs`).

## Observabilidad

- **Prometheus** (`:9090`) raspa métricas JVM y de negocio de los 5 servicios Spring via `/actuator/prometheus`.
- **Grafana** (`:3001`) provee dashboards auto-provisionados (datasource Prometheus + dashboard JVM).
- **Dozzle** (`:9999`) permite ver y filtrar logs de todos los contenedores en tiempo real desde el navegador, sin necesidad de acceso a la terminal.
- **Portainer** (`:9080`) UI web para administrar contenedores: ver estado, reiniciar, ver logs, consola, inspeccionar redes y volúmenes. En el primer acceso pide crear usuario admin.
- Cada servicio incluye un `CorrelationIdFilter` que propaga el header `X-Request-ID` en todos los logs (también a través de RabbitMQ, ver sección siguiente).

## Trazabilidad: X-Request-ID end-to-end

Todos los servicios propagan un mismo `X-Request-ID` a lo largo de toda la cadena para poder correlacionar logs entre contenedores en Dozzle.

```
Cliente HTTP ──► API Gateway ──► chat-service ──► RabbitMQ ──► worker-service / notification-service
                  (genera ID)     (lee header)    (header AMQP)   (lee header → MDC)
```

| Componente | Mecanismo |
|---|---|
| `api-gateway` | `CorrelationIdFilter` reactive: si la petición no trae `X-Request-ID`, genera uno; lo añade al request mutado (forward downstream) y al response. `MdcContextLifterConfig` mantiene el ID en MDC a través de cambios de hilo reactivos |
| `chat-service`, `user-service`, `notification-service`, `worker-service` | `CorrelationIdFilter` servlet: lee `X-Request-ID` del request y lo guarda en `MDC` durante el ciclo |
| `chat-service` → RabbitMQ | `RabbitTemplate` con `addBeforePublishPostProcessors` lee `MDC.get("requestId")` y lo escribe como header `X-Request-ID` en el mensaje AMQP |
| RabbitMQ → `worker-service` / `notification-service` | Los `@RabbitListener` reciben `@Header("X-Request-ID")` y lo restauran en `MDC` |
| Logs | Patrón Logback `%X{requestId:----}` imprime el ID en cada línea → visible y filtrable desde Dozzle |

Probar en vivo:

```bash
curl -i http://localhost:8090/api/chat/presence \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Request-ID: demo-1234"
# La respuesta incluye X-Request-ID: demo-1234
# En Dozzle (http://localhost:9999) filtra por "demo-1234" y verás los logs
# de api-gateway, chat-service y, al enviar un mensaje, también de
# worker-service / notification-service con el mismo ID.
```

## Manejo del stack con Makefile

El `Makefile` envuelve los comandos de `docker compose` para facilitar el día a día:

```bash
make help          # listar todos los targets
make up            # levantar todo (compila si hace falta)
make down          # detener y eliminar contenedores
make ps            # estado de los contenedores
make logs          # logs en vivo de todo el stack
make logs-chat-service   # logs en vivo de un servicio específico
make restart-worker-service
make health        # GET /actuator/health de los servicios Spring
make urls          # imprimir las URLs útiles
make clean         # bajar y borrar volúmenes locales
```

## ¿Qué pasa si un contenedor falla?

| Cae | Efecto inmediato | Recuperación |
|---|---|---|
| `worker-service` | Los mensajes se acumulan en `chat.messages.queue` (RabbitMQ los persiste). El frontend deja de recibir el mensaje en tiempo real vía WebSocket, pero el mensaje ya fue persistido en Postgres por chat-service. Al reiniciar el worker, consume el backlog y se entrega el mensaje al topic STOMP | `make restart-worker-service` o reiniciar desde Portainer |
| `notification-service` | Los eventos se acumulan en `notifications.queue`. No se generan notificaciones nuevas, pero los mensajes de chat siguen funcionando. Al reiniciar consume el backlog | `make restart-notification-service` |
| `chat-service` | El gateway responde 502/503 en `/api/chat/**`. WebSocket directos a `/ws` se caen. Frontend muestra error al enviar mensaje | Auto-restart por `restart: always` |
| `rabbitmq` | chat-service no puede publicar (los envíos fallan). worker/notification se quedan sin consumir. WebSocket STOMP relay se desconecta | Auto-restart; cola y mensajes durables se conservan en el volumen `rabbitmq_data` |
| `postgres` | Spring services entran en estado de error/restart. Datos persistidos en volumen `postgres_data` | Auto-restart; al volver, los servicios reconectan |
| `redis` | Rate limiting del gateway falla (se pierde estado). Presencia se reinicia | Auto-restart |
| `keycloak` | Login deja de funcionar; sesiones con JWT vigente siguen operando hasta que caduque el token | Auto-restart (90–120s) |
| `config-server` | Servicios ya levantados siguen con su config cacheada. Servicios que aún no arrancan no podrán hacerlo | Auto-restart |
| `api-gateway` | Frontend pierde el único punto de entrada `/api/*`. WebSocket `/ws` (que va directo a chat-service) sigue funcionando | Auto-restart |
| `frontend` (nginx) | La SPA deja de servirse. Backend sigue OK; APIs accesibles directamente vía 8090 | Auto-restart |
| `prometheus` / `grafana` / `dozzle` / `portainer` | Solo afecta observabilidad/administración. Negocio intacto | Auto-restart |

Patrón general: todos los servicios tienen `restart: always` + healthchecks; las dependencias usan `depends_on: condition: service_healthy` para arrancar en orden; las colas RabbitMQ son `durable` y el volumen `rabbitmq_data` es externo para sobrevivir a `docker compose down`.

## Redes Docker

El `docker-compose.yml` separa el tráfico en tres redes bridge:

- **`frontend-net`** — sólo `frontend`, `api-gateway`, `chat-service` (para WebSocket directo) y `keycloak` (para token endpoint vía `/kc/*`).
- **`backend-net`** — bus de comunicación entre microservicios e infraestructura (RabbitMQ, Redis, Config Server, MinIO, Prometheus, Grafana, Dozzle, Portainer).
- **`db-net`** — sólo `postgres` y los servicios que consultan base de datos (`chat-service`, `user-service`, `notification-service`, `keycloak`).

El `worker-service` y `notification-service` no necesitan acceso a internet ni a la red de frontend: sólo escuchan en `backend-net`. Esto reduce la superficie de ataque y deja claro qué servicios pueden hablarse.
