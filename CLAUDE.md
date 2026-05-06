# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

SuperChat is a corporate real-time chat platform built as a microservices architecture on Spring Boot 3.5. Identity is managed by Keycloak (OAuth2/OIDC). All services validate JWTs from Keycloak via Spring Security OAuth2 Resource Server. A Spring Cloud Gateway serves as the single entry point. Configuration is centralized in a Spring Cloud Config Server. Docker Compose orchestrates all infrastructure.

## Running the project

**First time only** — create the external RabbitMQ volume and delete any stale Postgres volume:
```bash
docker volume create rabbitmq_data
docker volume rm progdist_postgres_data   # only if upgrading from old MVP
```

Start everything:
```bash
docker compose up -d --build
```

Keycloak takes 90–120 seconds on first boot. Wait for it before logging in.

After startup, create users in Keycloak Admin Console (`http://localhost:8080`, admin/admin → realm `superchat` → Users → Add user). Set a temporary password and disable "Update password" action.

Key URLs:
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

## Building and testing individual services

All services use Maven (no wrapper) and Java 21:

```bash
# Build without tests
cd <service> && mvn package -DskipTests

# Run all tests
cd chat-service && mvn test

# Run a single test class
cd chat-service && mvn test -Dtest=ChatServiceTest
```

Tests in `chat-service` are pure unit tests with Mockito — no running infrastructure needed.

## Architecture

### Service map

| Service | Port | Role |
|---|---|---|
| `keycloak` | 8080 | Identity provider (OAuth2/OIDC, JWT issuance) |
| `config-server` | 8888 | Spring Cloud Config Server (native/filesystem) |
| `api-gateway` | 8090 | Spring Cloud Gateway: JWT validation, rate limiting, routing |
| `chat-service` | 8082 | Messages, WebSocket STOMP relay, RabbitMQ publisher, presence |
| `user-service` | 8083 | User profiles, rooms |
| `notification-service` | 8084 | Async notification storage and delivery |
| `frontend` | 3000 | Nginx reverse proxy + static SPA |
| `rabbitmq` | 5672/61613/15672 | Async events + STOMP broker relay |
| `postgres` | 5432 | Separate databases: `superchat`, `users`, `notifications`, `keycloak` |
| `redis` | 6379 | Rate limiting (API Gateway) and presence |
| `prometheus` | 9090 | Metrics scraping from all 4 Spring services |
| `grafana` | 3001 | Dashboards (auto-provisioned datasource + JVM dashboard) |

Nginx routes:
- `/api/*` → api-gateway:8090 (passes Authorization header)
- `/kc/*` → keycloak:8080 (strips `/kc` prefix — used for ROPC token endpoint)
- `/ws` → chat-service:8082 (WebSocket upgrade, bypasses gateway)

### JWT identity

The canonical user identifier across all services is the Keycloak JWT `sub` claim (a UUID). Each service decodes JWTs using the Keycloak JWKS endpoint (`/realms/superchat/protocol/openid-connect/certs`). The `preferred_username` claim carries the display name.

### Config Server (`com.superchat.config`)

Serves per-service YAML files from `src/main/resources/configs/`. Files: `api-gateway.yml`, `chat-service.yml`, `user-service.yml`, `notification-service.yml`. Each downstream service imports config via `spring.config.import: configserver:...` and a `bootstrap.yml` with `spring.application.name`.

### API Gateway (`com.superchat.gateway`)

Reactive (Netty). `SecurityConfig` enforces JWT validation for all routes except `/actuator/**`. `RateLimiterConfig` defines a `KeyResolver` that extracts JWT `sub` for per-user rate limiting (falls back to IP). Routes come from Config Server (`api-gateway.yml`): StripPrefix=1 removes the `/api` prefix before forwarding.

### chat-service (`com.superchat.chat`)

Four concerns:

1. **REST API** (`web/`) — `ChatController` (conversations + messages), `PresenceController`, `SimulationController` (start/stop RabbitMQ listener), `TypingController`. All controllers inject `Authentication authentication` from Spring Security; `authentication.getName()` returns the JWT `sub`.

2. **WebSocket** — STOMP broker relay over RabbitMQ port 61613 (not in-memory). Configured in `WebSocketConfig`. Enables horizontal scaling — all instances relay through RabbitMQ. `PresenceChannelInterceptor` hooks STOMP CONNECT/DISCONNECT events; reads `username` from STOMP headers (client-supplied, trusted since gateway validates JWT before upgrade).

3. **Async messaging** — `ChatService` saves a message to PostgreSQL, then publishes two RabbitMQ events:
   - `chat.exchange / chat.message.created` → `chat.messages.queue` → `ChatEventPublisherConsumer` → WebSocket topic `/topic/conversations/{id}`
   - `notifications.exchange / notifications.message.created` → `notifications.queue` → notification-service consumer

4. **Simulation feature** — `rabbitmq.listener.simple.auto-startup: false` keeps the listener stopped on startup. `SimulationController` calls `ChatEventListenerControlService` to start/stop it, demonstrating message accumulation in RabbitMQ. This value must NOT be overridden by Config Server.

### user-service (`com.superchat.userservice`)

Manages `UserProfile` (keycloak_id → display_name, avatar, status, bio) and `Room` (PUBLIC/PRIVATE with role-based `RoomMember`). `GET /users/me` auto-creates a profile on first access using the JWT `sub`. Flyway manages the `users` database via `V1__Create_Users_Schema.sql`.

### notification-service (`com.superchat.notification`)

Consumes `notifications.queue` via `@RabbitListener`. Saves `Notification` records (recipientId, type, payload, isRead). REST API: `GET /notifications` (paginated), `GET /notifications/unread-count`, `PUT /notifications/{id}/read`. Ownership is enforced: only the recipient can read/mark their notifications. Flyway manages the `notifications` database.

### Message flow (write → realtime delivery)

```
POST /api/chat/messages (Bearer JWT)
  → API Gateway: validates JWT, rate-limits, strips /api prefix
  → chat-service ChatService: save to PostgreSQL (superchat DB)
  → RabbitTemplate → chat.exchange → chat.messages.queue
  → ChatEventPublisherConsumer (@RabbitListener, manually started)
  → SimpMessagingTemplate → /topic/conversations/{id}
  → WebSocket clients via RabbitMQ STOMP relay

  Also → RabbitTemplate → notifications.exchange → notifications.queue
  → notification-service NotificationConsumer
  → PostgreSQL (notifications DB)
```

### Flyway migrations

Each service manages its own database schema:
- `chat-service`: `V1__Initial_Schema.sql` (tables: `conversation`, `chat_message`), `V2__Add_Performance_Indices.sql`
- `user-service`: `V1__Create_Users_Schema.sql` (tables: `user_profiles`, `rooms`, `room_members`)
- `notification-service`: `V1__Create_Notifications.sql` (table: `notifications`)

All services use `ddl-auto: validate`. JPA entity `@Table` annotations must match Flyway table names exactly.

### Frontend (`frontend/`)

Plain HTML/JS/CSS SPA served by Nginx. `app.js` does ROPC login directly to Keycloak (no server-side auth). Tokens are stored in localStorage. The `api()` helper auto-refreshes the access token using the refresh token before expiry (30s buffer). WebSocket connection uses SockJS + STOMP.js.
