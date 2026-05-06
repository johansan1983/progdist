# SuperChat

Plataforma de chat corporativo con arquitectura de microservicios para demostración universitaria.

Documento de arquitectura para presentación:

- docs/ARQUITECTURA_SUPERCHAT.md

## Stack

- Java 21 (Temurin)
- Spring Boot 3.5
- Spring Cloud Gateway (API Gateway + rate limiting)
- Spring Cloud Config Server
- Keycloak 26 (OAuth2/OIDC, emisión de JWT)
- RabbitMQ 4 (mensajería async + relay STOMP)
- PostgreSQL 16 (persistencia)
- Redis 7 (rate limiting y presencia)
- Frontend HTML/CSS/JS con Nginx
- Prometheus + Grafana (observabilidad)
- Docker Compose

## Arquitectura

| Servicio | Puerto | Rol |
|---|---|---|
| `keycloak` | 8080 | Proveedor de identidad (OAuth2/OIDC, JWT) |
| `config-server` | 8888 | Spring Cloud Config Server |
| `api-gateway` | 8090 | Entrada única: validación JWT, rate limiting, routing |
| `chat-service` | 8082 | Mensajes, WebSocket STOMP, RabbitMQ, presencia |
| `user-service` | 8083 | Perfiles de usuario, salas |
| `notification-service` | 8084 | Notificaciones asíncronas |
| `frontend` | 3000 | Nginx + SPA |
| `rabbitmq` | 5672/61613/15672 | Broker de eventos + relay STOMP |
| `postgres` | 5432 | Bases de datos separadas por servicio |
| `redis` | 6379 | Rate limiting y presencia |
| `prometheus` | 9090 | Recolección de métricas |
| `grafana` | 3001 | Dashboards |

Flujo principal de mensaje en tiempo real:

1. Usuario inicia sesión en el frontend via Keycloak (ROPC).
2. Frontend llama a `POST /api/chat/messages` con JWT Bearer.
3. API Gateway valida el JWT, aplica rate limiting, enruta a chat-service.
4. chat-service guarda el mensaje en PostgreSQL.
5. chat-service publica evento en RabbitMQ (`chat.exchange`).
6. Consumidor RabbitMQ en chat-service retransmite al topic WebSocket `/topic/conversations/{id}`.
7. Simultáneamente publica en `notifications.exchange` → notification-service guarda la notificación.
8. Frontend recibe el evento en tiempo real via STOMP/WebSocket.

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

Keycloak tarda 90–120 segundos en el primer arranque. Espera antes de crear usuarios.

Crear usuarios de prueba en Keycloak Admin Console (`http://localhost:8080`, admin/admin → realm `superchat` → Users → Add user). Completa: username, email, first name, last name. Asigna contraseña y desactiva "Update password".

Validar estado:

```bash
docker compose ps
```

## URLs

- Frontend: http://localhost:3000
- Keycloak Admin: http://localhost:8080
- API Gateway: http://localhost:8090
- Config Server: http://localhost:8888
- RabbitMQ Management: http://localhost:15672 (superchat/superchat123)
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3001 (admin/admin)
- Chat Swagger: http://localhost:8082/docs
- User Swagger: http://localhost:8083/docs
- Notification Swagger: http://localhost:8084/docs

## Troubleshooting rápido

Si `chat-service` o `notification-service` están en `Restarting`:

```bash
docker compose logs --tail=50 chat-service
docker compose logs --tail=50 notification-service
```

Causas frecuentes:
- RabbitMQ sin el usuario `superchat` (ocurre si el volumen ya existía con otro usuario). Solución:
  ```bash
  # Crear el usuario via management API
  curl -u guest:guest -X PUT http://localhost:15672/api/users/superchat \
    -H "Content-Type: application/json" \
    -d '{"password":"superchat123","tags":"administrator"}'
  curl -u guest:guest -X PUT "http://localhost:15672/api/permissions/%2F/superchat" \
    -H "Content-Type: application/json" \
    -d '{"configure":".*","write":".*","read":".*"}'
  docker compose restart chat-service notification-service
  ```
- chat-service en 502 via gateway: esperar a que esté `healthy` o revisar migración Flyway.

## Endpoints

Todos los endpoints de negocio pasan por el API Gateway en `http://localhost:8090/api/`.

**Chat:**

- `GET  /api/chat/conversations/{id}/messages`
- `POST /api/chat/conversations`
- `POST /api/chat/messages`
- `GET  /api/chat/presence`
- `GET  /api/chat/simulation/realtime-publisher/status`
- `POST /api/chat/simulation/realtime-publisher/fail`
- `POST /api/chat/simulation/realtime-publisher/restore`

**Usuarios:**

- `GET /api/users/me` (crea perfil automáticamente en el primer acceso)

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
curl -sS -X POST http://localhost:8090/api/chat/simulation/realtime-publisher/fail \
  -H "Authorization: Bearer $TOKEN"
```

Restablecer publicador:

```bash
curl -sS -X POST http://localhost:8090/api/chat/simulation/realtime-publisher/restore \
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
- Panel de simulación (fallar/restablecer publicador) en la vista de chat.
- Sidebar derecho con cantidad de usuarios conectados y sus nombres.

## Identidad JWT

El identificador canónico de usuario en todos los servicios es el claim `sub` del JWT de Keycloak (UUID). El claim `preferred_username` lleva el nombre visible. Cada servicio valida JWTs contra el endpoint JWKS de Keycloak (`/realms/superchat/protocol/openid-connect/certs`).
