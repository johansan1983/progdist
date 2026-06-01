<!--
  Documento del Proyecto Final — SuperChat
  Formato base en Markdown. Para la entrega en AVACO con Normas APA, copiar este
  contenido a una plantilla de Word/Google Docs y aplicar: portada APA 7, interlineado
  1.5/doble, fuente Times New Roman 12 o Calibri 11, numeración de páginas, y sangría
  en las referencias. Reemplazar todos los campos entre [corchetes] e insertar las
  capturas indicadas como "[EVIDENCIA: ...]".
-->

# Portada

**[Universidad / Institución]**

**Facultad de [Facultad] — Programa de [Programa]**

**Asignatura:** Sistemas Distribuidos y Programación Distribuida

**Proyecto Final: SuperChat — Plataforma de chat corporativo con arquitectura de microservicios**

**Integrantes:**
- [Integrante 1 — código]
- [Integrante 2 — código]
- [Integrante 3 — código]
- [Integrante 4 — código]

**Docente:** [Nombre del docente]

**Fecha de entrega:** 1 de junio de 2026

**Repositorio:** https://github.com/johansan1983/progdist

---

## Tabla de contenido

1. Introducción
2. Objetivos
3. Descripción del proyecto
4. Arquitectura del sistema
5. Justificación tecnológica
6. Conceptos de sistemas distribuidos aplicados
   - 6.1 Concurrencia
   - 6.2 Sincronización y consistencia
   - 6.3 Redis
   - 6.4 RabbitMQ
   - 6.5 Docker y orquestación
7. Instalación y despliegue paso a paso
8. Flujo funcional del sistema
9. Observabilidad y trazabilidad
10. Integración y despliegue continuo (CI/CD)
11. Simulación de fallos y tolerancia
12. Componente de innovación
13. Evidencias
14. Dificultades y aprendizajes
15. Conclusiones
16. Referencias
17. Anexos

---

## 1. Introducción

SuperChat es una plataforma de **chat corporativo en tiempo real** construida sobre una
**arquitectura de microservicios**. El proyecto se diseñó como un caso real de sistemas
distribuidos: múltiples servicios independientes que se comunican de forma síncrona
(HTTP/REST a través de un *API Gateway*) y asíncrona (eventos sobre RabbitMQ), con
identidad centralizada, configuración centralizada, mensajería en tiempo real mediante
WebSocket/STOMP, almacenamiento políglota (PostgreSQL, Redis, MinIO) y una capa completa
de observabilidad.

Este documento describe la arquitectura, justifica las decisiones tecnológicas, explica
los conceptos de sistemas distribuidos efectivamente implementados (concurrencia,
sincronización, mensajería, caché/estado compartido, contenedores) y detalla cómo
desplegar el sistema desde cero, cómo probarlo y cómo se comporta ante fallos.

> El detalle arquitectónico extendido se encuentra en [docs/ARQUITECTURA_SUPERCHAT.md](ARQUITECTURA_SUPERCHAT.md)
> y las instrucciones operativas completas en el [README.md](../README.md) y [DEPLOY.md](../DEPLOY.md).

## 2. Objetivos

**Objetivo general.** Diseñar, construir y desplegar un sistema distribuido funcional, que
demuestre comunicación entre servicios, procesamiento concurrente de eventos, tolerancia a
fallos y observabilidad, y que pueda desplegarse desde cero en un equipo distinto siguiendo
la documentación.

**Objetivos específicos.**
- Implementar una arquitectura de microservicios con un único punto de entrada (API Gateway)
  y autenticación basada en estándares (OAuth2/OIDC con JWT).
- Garantizar la entrega confiable de eventos entre servicios mediante un patrón de
  *outbox transaccional* sobre RabbitMQ (entrega *at-least-once*).
- Entregar mensajería en tiempo real horizontalmente escalable usando WebSocket/STOMP con
  *broker relay* sobre RabbitMQ y estado de presencia compartido en Redis.
- Centralizar la configuración (Spring Cloud Config) y orquestar todo el stack con Docker
  Compose en redes segmentadas.
- Proveer observabilidad de extremo a extremo: métricas (Prometheus/Grafana), logs con
  `X-Request-ID` correlacionado y herramientas de inspección (Dozzle, Portainer, RabbitMQ
  Management, Redis Commander).
- Documentar el despliegue de forma que un usuario sin experiencia previa pueda levantarlo.

## 3. Descripción del proyecto

**Nombre del sistema:** SuperChat.

**Problema que resuelve.** Las organizaciones (empresas y universidades) necesitan
comunicación interna en tiempo real con control de identidad, separación por organización y
trazabilidad. SuperChat ofrece chat en tiempo real (mensajes, presencia, “escribiendo…”,
adjuntos, mensajes de voz) sobre una base distribuida, segura y observable.

**Edición.** El núcleo (chat, identidad, mensajería, observabilidad) corresponde a la
edición universitaria. El stack también incluye servicios de la edición *enterprise*
(administración, moderación de contenido y cumplimiento/GDPR) y un panel de administración;
su detalle está en [ENTERPRISE.md](../ENTERPRISE.md).

**Tecnologías principales.** Java 21, Spring Boot 3.5 / Spring Cloud 2025.x, Keycloak 26
(OAuth2/OIDC), RabbitMQ 4, PostgreSQL 16, Redis 7, MinIO, Nginx, Prometheus + Grafana,
Docker y Docker Compose.

## 4. Arquitectura del sistema

SuperChat se compone de **9 servicios Spring** (config-server, api-gateway, chat-service,
user-service, notification-service, worker-service, admin-service, moderation-service,
compliance-service), dos SPA servidas por Nginx (frontend de chat y panel de administración)
e infraestructura de apoyo (Keycloak, RabbitMQ, PostgreSQL, Redis, MinIO, Prometheus,
Grafana, Dozzle, Portainer, Redis Commander).

| Servicio | Puerto | Rol |
|---|---|---|
| keycloak | 8080 | Proveedor de identidad (OAuth2/OIDC, emisión de JWT) |
| config-server | 8888 | Configuración centralizada (Spring Cloud Config) |
| api-gateway | 8090 | Entrada única: validación JWT, *rate limiting*, enrutamiento |
| chat-service | 8082 | Mensajes, WebSocket/STOMP, presencia, *outbox* |
| user-service | 8083 | Perfiles, organizaciones, departamentos, salas |
| notification-service | 8084 | Notificaciones asíncronas |
| worker-service | 8085 | Consumidor RabbitMQ → *relay* a WebSocket; simulación de falla |
| admin-service | 8086 | Reglas de negocio por organización |
| moderation-service | 8087 | Filtro de contenido + incidentes |
| compliance-service | 8088 | Auditoría, consentimiento, GDPR, retención |
| frontend | 3000 | SPA de chat (Nginx) |
| admin-panel | 3002 | SPA de administración (Nginx) |
| rabbitmq | 5672 / 61613 / 15672 | Bus de eventos + *relay* STOMP + consola |
| postgres | 5432 | Bases separadas por servicio |
| redis | 6379 | *Rate limiting* y presencia |
| minio | 9000 / 9001 | Almacenamiento de adjuntos |
| prometheus | 9090 | Recolección de métricas |
| grafana | 3001 | Dashboards |
| dozzle / portainer / redis-commander | 9999 / 9080 / 8181 | Observabilidad y administración |

**Comunicación entre componentes.**
- **Síncrona (HTTP/REST):** el frontend llama al API Gateway (`/api/*`); el gateway valida
  el JWT, aplica *rate limiting* y enruta al microservicio correspondiente (quita el prefijo
  `/api`). Las rutas provienen del Config Server.
- **Asíncrona (eventos):** chat-service publica eventos en RabbitMQ; worker-service y
  notification-service los consumen de forma independiente.
- **Tiempo real (WebSocket/STOMP):** los clientes se conectan a `/ws` (SockJS+STOMP); el
  *broker relay* usa RabbitMQ (puerto 61613), de modo que varias instancias de chat-service
  comparten los mismos *topics*.

El diagrama de arquitectura (Mermaid) y el diagrama de secuencia del flujo de mensaje están
en el [README.md](../README.md#arquitectura). Se recomienda insertarlos aquí como imagen.

> [EVIDENCIA: insertar captura del diagrama de arquitectura renderizado]

## 5. Justificación tecnológica

| Tecnología | Por qué se eligió |
|---|---|
| **Spring Boot / Spring Cloud** | Ecosistema maduro para microservicios: Gateway reactivo, Config Server, Security OAuth2 Resource Server, soporte AMQP y WebSocket de primera clase. |
| **Keycloak (OAuth2/OIDC)** | Identidad estándar y centralizada; emite JWT firmados validados por todos los servicios vía JWKS. Evita implementar autenticación propia (riesgosa). |
| **API Gateway** | Punto único de entrada: un solo lugar para validar JWT, limitar tasa y enrutar; reduce superficie de exposición. |
| **RabbitMQ** | Desacopla productores de consumidores, persiste eventos en colas *durables* y habilita el *broker relay* STOMP para escalar el tiempo real horizontalmente. |
| **PostgreSQL (una base por servicio)** | Persistencia transaccional fiable; *database-per-service* preserva la independencia de cada microservicio. |
| **Redis** | Estado compartido de baja latencia: contador de *rate limiting* por usuario y presencia con expiración por TTL, compartido entre instancias. |
| **MinIO** | Almacenamiento de objetos compatible con S3 para adjuntos, con URLs prefirmadas (el binario no atraviesa los servicios). |
| **Prometheus + Grafana** | Métricas JVM y de negocio vía `/actuator/prometheus` y dashboards auto-aprovisionados. |
| **Docker + Docker Compose** | Despliegue reproducible “desde cero”, redes segmentadas y orden de arranque por *healthchecks*. |

## 6. Conceptos de sistemas distribuidos aplicados

### 6.1 Concurrencia

- **Consumo concurrente de eventos.** worker-service y notification-service procesan los
  mensajes de sus colas mediante `@RabbitListener`. RabbitMQ entrega los mensajes y permite
  escalar consumidores; la concurrencia de consumo se ajusta por configuración del *listener
  container*. Los productores (chat-service) no se bloquean esperando a los consumidores.
- **Transacciones cortas y sin IO remoto.** La escritura del mensaje vive en un *bean*
  transaccional dedicado (`MessagePersistenceService`) para que el límite `@Transactional`
  se aplique a través del *proxy* de Spring y no se pierda por auto-invocación. Las
  validaciones remotas (moderación, reglas de negocio) ocurren **antes** de abrir la
  transacción, de modo que ninguna conexión a la base queda retenida durante una llamada de
  red.
- **Tiempo real multi-instancia.** Al usar *broker relay* sobre RabbitMQ en lugar de un
  *broker* en memoria, varias instancias de chat-service pueden atender WebSockets en
  paralelo compartiendo los *topics*.

### 6.2 Sincronización y consistencia

El reto clásico de “doble escritura” (guardar en base **y** publicar un evento de forma
atómica) se resuelve con el **patrón Outbox transaccional**:

1. `MessagePersistenceService.persist(...)` escribe, en **una sola transacción**, la fila del
   mensaje (`chat_message`) y las filas de eventos en la tabla *outbox*. O ambas se confirman,
   o ninguna.
2. `OutboxRelay` se ejecuta periódicamente (`@Scheduled`, cada ~2 s), toma un lote de eventos
   no publicados (`findTop100ByPublishedFalseOrderByIdAsc`) y los publica en RabbitMQ. Solo
   marca `published = true` **después** de un envío exitoso; si falla, la fila queda pendiente
   y se reintenta en el siguiente ciclo, sin abortar el lote por una fila defectuosa.

Esto entrega una semántica **at-least-once**: un evento nunca se pierde, pero puede
re-publicarse si el *relay* cae tras publicar y antes de confirmar. Por eso los **consumidores
son idempotentes**. Otros mecanismos de sincronización: expiración por **TTL** de las claves
de presencia en Redis (limpieza automática de sesiones colgadas) y colas **durables** en
RabbitMQ que conservan los eventos ante reinicios.

### 6.3 Redis

Redis se usa como **estado compartido** de baja latencia en dos funciones:

- **Rate limiting (API Gateway).** Un `KeyResolver` extrae el *claim* `sub` del JWT y arma la
  clave `user:<sub>` (con *fallback* a `ip:<dirección>` para peticiones sin autenticar). El
  `RequestRateLimiter` de Spring Cloud Gateway mantiene el contador en Redis, de modo que el
  límite es **por usuario** y consistente aunque haya varias instancias del gateway.
- **Presencia (chat-service).** `PresenceService` guarda una clave `presence:<sessionId>` por
  cada sesión WebSocket conectada, con **TTL de 1 hora**. Un *snapshot* agrega los usuarios
  conectados y se difunde al *topic* `/topic/presence`. Como el estado vive en Redis (no en
  memoria del proceso), la presencia es correcta aunque los WebSockets se repartan entre
  varias instancias.

### 6.4 RabbitMQ

RabbitMQ cumple tres papeles:

1. **Bus de eventos de dominio.** chat-service publica a `chat.exchange` (rutea a
   `chat.messages.queue`, consumida por worker-service) y a `notifications.exchange` (rutea a
   `notifications.queue`, consumida por notification-service). Productores y consumidores
   quedan desacoplados.
2. **Entrega confiable.** Las colas son **durables** y el *outbox relay* publica con
   semántica *at-least-once*; si un consumidor está caído, los eventos se acumulan en la cola
   y se procesan al reiniciar (ver §11).
3. **Broker relay STOMP.** El puerto 61613 permite que el *broker* de mensajes en tiempo real
   sea el propio RabbitMQ; así el WebSocket escala horizontalmente.

### 6.5 Docker y orquestación

Todo el stack se define en `docker-compose.yml`. Puntos clave:

- **Imágenes:** cada servicio Spring tiene su `Dockerfile` (compilación multi-etapa con Maven
  y Java 21); las SPA se sirven con Nginx.
- **Redes segmentadas (3 *bridges*):** `frontend-net` (frontend, gateway, chat-service para
  WebSocket directo y Keycloak), `backend-net` (bus de servicios e infraestructura) y
  `db-net` (PostgreSQL y los servicios que consultan base). Segmentar reduce la superficie de
  ataque y deja explícito qué servicios pueden comunicarse.
- **Orden de arranque:** `depends_on: condition: service_healthy` + *healthchecks* arrancan
  los servicios en el orden correcto.
- **Persistencia:** volúmenes para PostgreSQL, Redis, RabbitMQ (volumen externo
  `rabbitmq_data`), Prometheus y Grafana.

## 7. Instalación y despliegue paso a paso

> Procedimiento resumido. La guía completa y multiplataforma está en el
> [README.md](../README.md) y en [DEPLOY.md](../DEPLOY.md).

**Requisitos previos:** Git, Docker Engine y Docker Compose v2. En Windows/WSL2 ver las
notas de RAM y DNS del README. Java 21 + Maven solo si se compila fuera de Docker.

**Despliegue de una sola orden (multiplataforma):**
```bash
# Windows (PowerShell)
powershell -ExecutionPolicy Bypass -File .\deploy.ps1
# Linux / macOS
./deploy.sh
# o, con Makefile:
make deploy
```

**Despliegue manual:**
```bash
git clone https://github.com/johansan1983/progdist.git && cd progdist
docker volume create rabbitmq_data      # solo la primera vez
docker compose up -d --build
```
Keycloak tarda **90–120 s** en el primer arranque. El realm `superchat` se importa
automáticamente con 6 usuarios de prueba (alice…frank, contraseña `demo123`).

**Variables de entorno** (acceso desde otra máquina): copiar `.env.example` a `.env` y
definir `PUBLIC_HOST`; el script `render-realm` regenera el realm de Keycloak con los
`redirectUris` correctos. Para producción con HTTPS, `PUBLIC_DOMAIN` + `ACME_EMAIL` y
`make up-prod` (Caddy + Let’s Encrypt).

**Verificación:** `docker compose ps` y `make health`.

## 8. Flujo funcional del sistema

**Cómo probar el sistema.** Entrar a http://localhost:3000, iniciar sesión con `alice` /
`demo123`, abrir una segunda sesión (otro usuario/navegador) y enviar mensajes para ver la
entrega en tiempo real, la presencia y el indicador de “escribiendo…”.

**Ejemplos de endpoints** (todos vía gateway `http://localhost:8090/api/`, con
`Authorization: Bearer <token>`):
```bash
# Token (ROPC contra Keycloak)
TOKEN=$(curl -s -X POST http://localhost:8080/realms/superchat/protocol/openid-connect/token \
  -d 'client_id=superchat-frontend&username=alice&password=demo123&grant_type=password' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['access_token'])")

curl -X POST http://localhost:8090/api/chat/conversations -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"name":"General"}'
curl -X POST http://localhost:8090/api/chat/messages -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"conversationId":1,"content":"Hola mundo"}'
```
La documentación OpenAPI/Swagger de cada servicio está en `/docs` (8082, 8083, 8084, 8085,
8086, 8087, 8088).

**Flujo de eventos (write → entrega en tiempo real):**
1. `POST /api/chat/messages` → Gateway valida JWT y limita tasa → chat-service.
2. chat-service persiste el mensaje **y** los eventos *outbox* en una transacción.
3. `OutboxRelay` publica a `chat.exchange` y `notifications.exchange`.
4. worker-service consume `chat.messages.queue` y reenvía al *topic*
   `/topic/conversations/{id}` (STOMP relay) → el frontend lo recibe en vivo.
5. notification-service consume `notifications.queue` y persiste la notificación.

## 9. Observabilidad y trazabilidad

- **Logs claros por servicio** y visor en tiempo real con **Dozzle** (http://localhost:9999),
  sin necesidad de terminal.
- **`X-Request-ID` de extremo a extremo.** El gateway genera el ID si no viene; cada servicio
  lo lee y lo guarda en el MDC; chat-service lo propaga como *header* AMQP al publicar, y los
  `@RabbitListener` lo restauran en el MDC. El patrón de Logback `%X{requestId}` lo imprime en
  cada línea, de modo que un mismo ID se sigue por gateway → chat-service → worker/notification
  en Dozzle.
- **Métricas.** Prometheus (http://localhost:9090) raspa `/actuator/prometheus` de los 8
  servicios Spring; Grafana (http://localhost:3001) muestra el dashboard “SuperChat
  Enterprise” auto-aprovisionado.
- **Administración:** Portainer (contenedores), RabbitMQ Management (colas/eventos) y Redis
  Commander (claves).

> [EVIDENCIA: capturas de Dozzle filtrando por un X-Request-ID, del dashboard de Grafana con
> datos, de RabbitMQ Management mostrando las colas y de `docker compose ps`]

## 10. Integración y despliegue continuo (CI/CD)

El repositorio incluye dos *workflows* de **GitHub Actions** (`.github/workflows/`):

- **`ci.yml`** — en cada *push*/PR a `main`: compila y prueba (`mvn -B verify`) los 9
  servicios Spring en paralelo (*matrix*) y valida que `docker-compose.yml` y
  `docker-compose.prod.yml` sean correctos.
- **`docker-publish.yml`** — en *push* a `main` y *tags* `v*`: construye las 11 imágenes
  (9 Spring + frontend + admin-panel) con Buildx y las publica en **GitHub Container Registry**
  (`ghcr.io/johansan1983/progdist-<servicio>`), con caché de capas.

Imágenes consumibles sin compilar localmente:
```bash
docker pull ghcr.io/johansan1983/progdist-chat-service:latest
```

> [EVIDENCIA: captura del pipeline en verde en GitHub Actions y de los paquetes publicados en GHCR]

## 11. Simulación de fallos y tolerancia

El sistema fue diseñado con `restart: always`, *healthchecks*, colas durables y volúmenes
persistentes. Comportamiento ante caídas:

| Cae | Efecto inmediato | Recuperación |
|---|---|---|
| **worker-service** | Los mensajes se **acumulan** en `chat.messages.queue` (persistidos). El mensaje ya quedó en PostgreSQL; solo se retrasa la entrega en vivo. | Al reiniciar, consume el *backlog* y entrega al *topic*. `make restart-worker-service`. |
| **notification-service** | Eventos acumulados en `notifications.queue`; el chat sigue funcionando. | Consume el *backlog* al reiniciar. |
| **rabbitmq** | El *outbox relay* no publica (los eventos quedan pendientes en la base). | Auto-restart; el *relay* reintenta; colas y mensajes durables se conservan. |
| **postgres** | Los servicios entran en error/reintento. | Auto-restart; reconexión; datos en volumen. |
| **redis** | Se pierde el estado de *rate limiting*/presencia. | Auto-restart; se reconstruye. |
| **keycloak** | No hay nuevos *logins*; los JWT vigentes siguen valiendo hasta expirar. | Auto-restart (90–120 s). |
| **api-gateway** | Se pierde la entrada `/api/*`; `/ws` directo a chat-service sigue. | Auto-restart. |

**Simulación dirigida (demo).** worker-service expone un interruptor para simular la caída
del publicador en tiempo real, demostrando la acumulación de mensajes en RabbitMQ y su
posterior recuperación:
```bash
curl -X POST http://localhost:8090/api/worker/simulation/realtime-publisher/fail    -H "Authorization: Bearer $TOKEN"
curl -X POST http://localhost:8090/api/worker/simulation/realtime-publisher/restore -H "Authorization: Bearer $TOKEN"
# Verificar acumulación:
curl -u superchat:superchat123 http://localhost:15672/api/queues/%2F/chat.messages.queue
```

> [EVIDENCIA: captura de la cola con mensajes acumulados durante el fallo y vacía tras restaurar]

## 12. Componente de innovación

Más allá de lo mínimo, el proyecto incorpora:

- **Patrón Outbox transaccional** para entrega confiable de eventos (resuelve la doble
  escritura) — un concepto de sistemas distribuidos de nivel profesional.
- **Tiempo real escalable** con WebSocket/STOMP sobre *broker relay* de RabbitMQ.
- **Identidad estándar** con Keycloak (OAuth2/OIDC, JWT, JWKS) en lugar de autenticación
  artesanal.
- **Trazabilidad `X-Request-ID`** correlacionada incluso a través de RabbitMQ.
- **Observabilidad completa:** Prometheus + Grafana, Dozzle, Portainer, Redis Commander.
- **Despliegue de una sola orden** multiplataforma y **perfil productivo con HTTPS** (Caddy +
  Let’s Encrypt).
- **CI/CD** con publicación automática de imágenes en GHCR.
- **Funcionalidades de chat avanzadas:** adjuntos vía MinIO con URLs prefirmadas, mensajes de
  voz, mensajes de un solo uso (*view-once*), presencia, “escribiendo…”.
- **Edición enterprise:** multi-tenant (organizaciones/departamentos), moderación,
  cumplimiento/GDPR y panel de administración (ver [ENTERPRISE.md](../ENTERPRISE.md)).

## 13. Evidencias

Insertar las capturas y registros siguientes (la rúbrica exige evidencias):

- [EVIDENCIA: `docker compose ps` con todos los contenedores *Up/healthy*]
- [EVIDENCIA: frontend con dos sesiones intercambiando mensajes en tiempo real]
- [EVIDENCIA: Dozzle filtrando por un `X-Request-ID` a través de varios servicios]
- [EVIDENCIA: Grafana mostrando métricas; Prometheus *Targets* en *up*]
- [EVIDENCIA: RabbitMQ Management con exchanges y colas; acumulación durante el fallo]
- [EVIDENCIA: pipeline de GitHub Actions en verde y paquetes en GHCR]

## 14. Dificultades y aprendizajes

> Completar con la experiencia real del equipo. Ejemplos observados durante el desarrollo:

- **Doble escritura base+broker.** Publicar el evento directamente tras guardar arriesgaba
  perder eventos si el *broker* fallaba. **Solución:** patrón *outbox* + *relay* idempotente.
- **Límite transaccional perdido por auto-invocación.** Llamar un método `@Transactional`
  desde la misma clase no pasa por el *proxy* de Spring. **Solución:** mover la persistencia a
  un *bean* dedicado.
- **Presencia en despliegue multi-instancia.** Mantener presencia en memoria no escala.
  **Solución:** Redis con TTL + *broker relay* STOMP.
- **Codificación de archivos en Windows.** Editores reescribían el realm de Keycloak con BOM y
  *mojibake*. **Aprendizaje:** fijar UTF-8/LF y revisar *diffs* antes de confirmar.
- **Configuración de Grafana.** El *datasource* aprovisionado debe fijar su `uid` para que el
  dashboard lo resuelva. **Aprendizaje:** el aprovisionamiento es declarativo y sensible a IDs.
- **Arranque ordenado.** Sin *healthchecks*, los servicios arrancaban antes que sus
  dependencias. **Solución:** `depends_on: service_healthy`.
- **Trabajo colaborativo:** [describir reparto de tareas, uso de ramas y *pull requests*].

## 15. Conclusiones

SuperChat demuestra un sistema distribuido **real, desplegable, observable y entendible**.
Integra comunicación síncrona y asíncrona, procesamiento concurrente de eventos, entrega
confiable mediante *outbox*, estado compartido en Redis, tiempo real escalable, identidad
estándar y observabilidad de extremo a extremo, todo orquestado con Docker Compose y
desplegable desde cero con una sola orden. Las decisiones de arquitectura priorizaron la
fiabilidad (entrega *at-least-once*, colas durables, *healthchecks*, reinicios automáticos) y
la claridad operativa (un punto de entrada, configuración centralizada, trazabilidad por
`X-Request-ID`). El resultado cumple los objetivos planteados y constituye una base sólida
para evolucionar hacia funcionalidades de colaboración, voz/vídeo y gobernanza.

## 16. Referencias

> Formato APA 7. Ajustar fechas de consulta. Aplicar sangría francesa en Word.

- Docker, Inc. (2025). *Docker documentation*. https://docs.docker.com/
- Pivotal/VMware. (2025). *RabbitMQ documentation*. https://www.rabbitmq.com/documentation.html
- Redis Ltd. (2025). *Redis documentation*. https://redis.io/docs/latest/
- Red Hat. (2025). *Keycloak documentation* (v26). https://www.keycloak.org/documentation
- VMware. (2025). *Spring Boot reference documentation* (v3.5). https://docs.spring.io/spring-boot/
- VMware. (2025). *Spring Cloud Gateway documentation*. https://docs.spring.io/spring-cloud-gateway/
- Richardson, C. (2018). *Microservices patterns*. Manning. (Patrón *Transactional Outbox*.)
- The PostgreSQL Global Development Group. (2025). *PostgreSQL 16 documentation*. https://www.postgresql.org/docs/16/
- Prometheus Authors. (2025). *Prometheus documentation*. https://prometheus.io/docs/
- Grafana Labs. (2025). *Grafana documentation*. https://grafana.com/docs/

## 17. Anexos

**A. URLs del stack (demo).**

| URL | Servicio |
|---|---|
| http://localhost:3000 | Frontend (chat) |
| http://localhost:3002 | Panel de administración |
| http://localhost:8080 | Keycloak (admin/admin) |
| http://localhost:8090 | API Gateway |
| http://localhost:15672 | RabbitMQ Management (superchat/superchat123) |
| http://localhost:9090 / 3001 | Prometheus / Grafana (admin/admin) |
| http://localhost:9999 / 9080 / 8181 | Dozzle / Portainer / Redis Commander |

**B. Usuarios de prueba.** alice, bob, charlie, dave, eve, frank — contraseña `demo123`.

**C. Comandos Make útiles.** `make up`, `make health`, `make ps`, `make logs`,
`make restart-worker-service`, `make urls`, `make clean`.
