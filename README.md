# SuperChat MVP

Aplicacion web de chat con arquitectura de microservicios para demostracion universitaria.

Documento de arquitectura para presentacion:

- docs/ARQUITECTURA_SUPERCHAT.md

## Stack

- Java 25 (Temurin)
- Spring Boot 3.5.0
- Springdoc OpenAPI 2.8.5 (Swagger UI)
- RabbitMQ 4 (mensajeria)
- PostgreSQL 16 (persistencia)
- Redis 7 (listo para presencia/cache)
- Frontend HTML/CSS/JS con Nginx
- Docker Compose

## Arquitectura

Servicios:

- auth-service (puerto 8081): login y validacion JWT firmado
- chat-service (puerto 8082): conversaciones, mensajes, WebSocket, presencia y simulacion de falla
- rabbitmq (puertos 5672 y 15672): broker de eventos
- postgres (puerto 5432): base de datos principal
- redis (puerto 6379): infraestructura lista para extensiones de presencia/cache
- frontend (puerto 3000): interfaz web y proxy inverso para auth/chat/ws

Flujo principal de mensaje en tiempo real:

1. Usuario inicia sesion en frontend.
2. frontend llama a auth-service para obtener JWT firmado con expiracion.
3. frontend crea conversacion en chat-service (con token Bearer).
4. frontend envia mensaje a chat-service.
5. chat-service guarda el mensaje en PostgreSQL.
6. chat-service publica evento en RabbitMQ.
7. Un consumidor Rabbit en chat-service toma el evento de la cola.
8. El consumidor publica el evento a WebSocket en /topic/conversations/{id}.
9. frontend recibe el evento en tiempo real y lo renderiza.

Flujo de presencia:

1. El cliente abre conexion STOMP y envia su username en el handshake.
2. chat-service registra/desregistra sesiones conectadas.
3. frontend consulta /chat/presence periodicamente y muestra cantidad + nombres conectados en la barra lateral derecha.

Flujo de simulacion de falla del publicador en tiempo real:

1. Desde frontend o API se ejecuta fail del publicador (se detiene consumidor Rabbit).
2. Los mensajes nuevos quedan en la cola de RabbitMQ y no salen por WebSocket.
3. Se ejecuta restore del publicador.
4. El consumidor se reanuda y procesa los eventos pendientes.

## Levantar proyecto

Prerequisitos:

- Docker y Docker Compose
- Volumen externo para RabbitMQ (si no existe):

Verifica que usas Compose V2 (comando docker compose) y no docker-compose V1:

```bash
docker compose version
```

Si no tienes Compose V2 instalado, instala el plugin oficial de Docker Compose para tu distribucion.

```bash
docker volume create rabbitmq_data
```

Ejecutar:

```bash
docker compose up -d --build
```

Validar estado:

```bash
docker compose ps
```

## Troubleshooting rapido

Error al levantar con docker-compose V1:

```text
KeyError: 'ContainerConfig'
```

Causa probable: incompatibilidad de docker-compose 1.29.x con versiones recientes de Docker Engine.

Solucion recomendada:

```bash
docker compose down --remove-orphans
docker rm -f rabbitmq postgres redis auth-service chat-service frontend 2>/dev/null || true
docker compose up -d --build
```

Si aparece 502 Bad Gateway en /chat o /ws:

```bash
docker compose ps
docker compose logs --tail=200 chat-service
curl -sS http://localhost:8082/chat/ping
```

Notas:

- Si auth/login responde 200 pero rutas /chat/* y /ws fallan con 502, normalmente chat-service no esta levantado o no esta listo aun.
- Espera a que rabbitmq, postgres y redis esten healthy antes de reintentar pruebas funcionales.
- Levanta siempre con docker compose (V2), evita docker-compose (V1).

URLs:

- Frontend: http://localhost:3000
- Auth health: http://localhost:8081/actuator/health
- Chat health: http://localhost:8082/actuator/health
- Auth Swagger UI: http://localhost:3000/auth/docs
- Chat Swagger UI: http://localhost:3000/chat/docs
- Auth OpenAPI JSON: http://localhost:3000/auth/v3/api-docs
- Chat OpenAPI JSON: http://localhost:3000/chat/v3/api-docs
- RabbitMQ Management: http://localhost:15672

Credenciales RabbitMQ por defecto:

- user: superchat
- pass: superchat123

Credenciales de acceso MVP para probar:

- Usuario: cualquier valor (por ejemplo, alice)
- Password: cualquier valor no vacio (por ejemplo, demo)

## Endpoints MVP

Auth:

- POST /auth/login
- GET /auth/validate

Chat:

- GET /chat/ping
- POST /chat/conversations
- POST /chat/messages
- GET /chat/conversations/{conversationId}/messages
- GET /chat/presence
- GET /chat/simulation/realtime-publisher/status
- POST /chat/simulation/realtime-publisher/fail
- POST /chat/simulation/realtime-publisher/restore

WebSocket/STOMP:

- Endpoint SockJS: /ws
- Topic mensajes por conversacion: /topic/conversations/{conversationId}
- Topic typing por conversacion: /topic/conversations/{conversationId}/typing
- App destination typing: /app/typing

## Ejemplo rapido con curl

Login:

```bash
TOKEN=$(curl -sS -X POST http://localhost:3000/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"demo"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
```

Crear conversacion:

```bash
curl -sS -X POST http://localhost:3000/chat/conversations \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"General"}'
```

Enviar mensaje:

```bash
curl -sS -X POST http://localhost:3000/chat/messages \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"conversationId":1,"content":"Hola mundo"}'
```

Listar mensajes:

```bash
curl -sS -X GET http://localhost:3000/chat/conversations/1/messages \
  -H "Authorization: Bearer $TOKEN"
```

Consultar presencia:

```bash
curl -sS -X GET http://localhost:3000/chat/presence \
  -H "Authorization: Bearer $TOKEN"
```

Simular falla del publicador en tiempo real:

```bash
curl -sS -X POST http://localhost:3000/chat/simulation/realtime-publisher/fail \
  -H "Authorization: Bearer $TOKEN"
```

Restablecer publicador:

```bash
curl -sS -X POST http://localhost:3000/chat/simulation/realtime-publisher/restore \
  -H "Authorization: Bearer $TOKEN"
```

## Verificacion de eventos RabbitMQ

```bash
curl -u superchat:superchat123 http://localhost:15672/api/queues/%2F/chat.messages.queue
```

Revisa el campo messages o message_stats.publish.

## Funcionalidades UI implementadas

- Sesion persistente en navegador (no vuelve a login al refrescar si token valido).
- Boton de cerrar sesion.
- Indicador de "escribiendo..." en tiempo real.
- Scroll fijo del chat con posicion inicial en el ultimo mensaje.
- Panel de simulacion (fallar/restablecer publicador) en la vista de chat.
- Sidebar derecho con cantidad de usuarios conectados y sus nombres.

## Limites actuales del MVP

- Auth usa login simple (sin registro ni hash de password en BD).
- No hay API Gateway dedicado aun.
- El consumidor actual de Rabbit publica a WebSocket, pero no existe pipeline adicional de negocio (notificaciones, auditoria, etc.).
- No hay control de permisos por sala.
- La presencia se mantiene en memoria del chat-service (no distribuida entre replicas).
- No hay pruebas de carga formales en este repositorio.

## Mejoras sugeridas post-entrega

- Agregar API Gateway (rate limit, centralizacion de auth).
- Persistir usuarios/credenciales de forma segura (hash + sal).
- Agregar consumidor de eventos para notificaciones.
- Migrar presencia a Redis para escalamiento horizontal.
- Agregar pruebas de carga con k6 y metricas p95/p99.
