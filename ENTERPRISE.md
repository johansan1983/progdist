# SuperChat — Enterprise / University Edition

Operations and architecture reference for the corporate chat platform. Covers the
multi-tenant organization model, RBAC, business-rule enforcement, content moderation,
message encryption, GDPR compliance, and observability.

> For local setup and the base architecture, see [README.md](README.md). This document
> describes the enterprise layer built on top of it.

---

## 1. Service map

| Service | Port | Role |
|---|---|---|
| keycloak | 8080 | Identity provider (OAuth2/OIDC, JWT, realm roles) |
| chat-service | 8082 | Messages, WebSocket relay, encryption, moderation + business-rule enforcement |
| user-service | 8083 | Profiles, rooms, **organizations, departments, role assignment** |
| notification-service | 8084 | Async notification storage |
| worker-service | 8085 | RabbitMQ event consumer / simulation |
| **admin-service** | 8086 | Business rules per organization |
| **moderation-service** | 8087 | Language/content filter engine + incident log |
| **compliance-service** | 8088 | Audit log, consent, GDPR export & erasure, retention |
| api-gateway | 8090 | JWT validation, rate limiting, routing |
| config-server | 8888 | Centralized configuration |
| frontend | 3000 | Chat SPA |
| **admin-panel** | 3002 | Administration SPA |
| prometheus | 9090 | Metrics scraping (all 8 Spring services) |
| grafana | 3001 | Dashboards (SuperChat Enterprise) |

Databases (PostgreSQL): `superchat`, `users`, `notifications`, `admin`, `moderation`, `compliance`, `keycloak`.

---

## 2. Multi-tenant model

```
Organization (BASIC | ENTERPRISE | UNIVERSITY)
  └── Department (self-referencing hierarchy)
        └── UserProfile (org_id, dept_id, system_role)
Room / Conversation → channel_type
```

**System roles** (`UserProfile.systemRole`, mirrored as Keycloak realm roles):

| Role | Capability |
|---|---|
| `PLATFORM_ADMIN` | Manage all organizations; cross-org admin |
| `ORG_ADMIN` | Manage users, rules, moderation, compliance within own org |
| `DEPT_ADMIN` | Department-scoped administration |
| `USER` | Standard chat user |

**Channel types** (`channel_type`): `GENERAL`, `ANNOUNCEMENT`, `TEAM`, `CLASS`, `SUPPORT`.
`ANNOUNCEMENT` channels are **read-only for non-admins** — only `ORG_ADMIN`/`PLATFORM_ADMIN` may post.

### Assigning roles in Keycloak
Admin Console → realm `superchat` → Users → *select user* → Role Mappings → assign
`org_admin` or `platform_admin`. The `KeycloakRoleConverter` in each service maps
`realm_access.roles` → Spring `ROLE_*` authorities for `@PreAuthorize`.

---

## 3. Business rules

Stored per-organization in admin-service (`business_rules` table). Fetched by chat-service
via `BusinessRuleClient` (5-minute TTL cache, fails open).

| Key | Default | Enforced where |
|---|---|---|
| `message_retention_days` | 365 | compliance retention scheduler |
| `max_file_size_mb` | 25 | attachment presign |
| `allowed_file_types` | image/,video/,application/pdf,application/zip | attachment presign |
| `working_hours_only` | false | message send (403 outside window) |
| `working_hours_start` / `_end` | 08:00 / 18:00 | message send |
| `working_hours_timezone` | UTC | message send |
| `require_consent_on_join` | true | frontend consent gate |
| `consent_version` | 1 | bump to force re-consent |
| `dm_enabled` | true | message send (403 on DM if false) |
| `guest_access_enabled` | false | reserved |

Seed defaults: `POST /api/admin/organizations/{orgId}/rules/seed-defaults`.

---

## 4. Content moderation

moderation-service holds per-org `word_lists` (literal or regex, case-insensitive).
chat-service calls `POST /moderation/check` before persisting each message.

| Action | Effect |
|---|---|
| `BLOCK` | Message rejected (400), incident logged |
| `REPLACE` | Matched text substituted, message stored, incident logged |
| `WARN` | Message stored unchanged, incident logged |

Rules are cached in-memory per org and evicted on add/delete. Every match writes a
`moderation_incidents` row (org, user, conversation, pattern, action).

---

## 5. Message encryption at rest

`EncryptionConverter` (AES-256-GCM) transparently encrypts `chat_message.content` and
`attachment_url` via a JPA `AttributeConverter`. Stored format: `Base64(IV[12] ‖ ciphertext ‖ tag[16])`.
A random IV per write means identical plaintext yields distinct ciphertext; the GCM tag
makes tampering fail closed.

Key: `AES_ENCRYPTION_KEY` (64 hex chars / 32 bytes). **Generate with `openssl rand -hex 32`
and set in `.env` for production** — the compose default is intentionally weak.

---

## 6. GDPR / data-protection compliance

compliance-service is the compliance surface.

| Right | Endpoint | Mechanism |
|---|---|---|
| Access / portability | `GET /api/compliance/export/{userId}` | Aggregates profile + messages + consent + erasure history |
| Erasure (Art. 17) | `DELETE /api/compliance/erasure/{orgId}` → scheduler | Anonymizes messages & profile across services |
| Consent | `POST/DELETE /api/compliance/consent` | Versioned, gated in frontend on login |
| Audit trail | `GET /api/compliance/audit` | Append-only `audit_log`, consumed from `audit.exchange` |
| Retention (Art. 5(1)(e)) | nightly scheduler | Honors `message_retention_days` |

**Erasure execution loop**: a request is `PENDING` → scheduler marks `IN_PROGRESS` →
`DataExportClient.eraseUserData()` calls internal token-protected endpoints on chat-service
(`anonymizeBySender`: nulls encrypted columns, sender→`[deleted]`) and user-service
(profile→`[deleted]`) → marks `COMPLETED`/`FAILED` → publishes outcome to audit log.

Internal endpoints (`/internal/**`) are network-isolated to `backend-net` and protected by
the `INTERNAL_API_TOKEN` shared secret — never exposed through the gateway.

---

## 7. Security model summary

- **Edge**: api-gateway validates every JWT, rate-limits per-user (20 req/s, burst 40).
- **Per-service**: each validates JWTs against Keycloak JWKS; `@EnableMethodSecurity` +
  `@PreAuthorize` guards on all admin/org/moderation/compliance write endpoints.
- **Open paths**: `/actuator/**`, swagger, `/moderation/check` and `/internal/**`
  (backend-net only; the latter also token-gated).
- **Frontend** injects `X-Org-Id` on every call so server-side rule enforcement has org context.

---

## 8. Observability

Custom Prometheus counters (scraped from `/actuator/prometheus`):

| Metric | Tags | Service |
|---|---|---|
| `superchat_messages_sent_total` | channel_type, conv_type | chat-service |
| `superchat_moderation_incidents_total` | action | moderation-service |
| `superchat_notifications_delivered_total` | — | notification-service |

Grafana → **SuperChat Enterprise** dashboard (auto-provisioned): message throughput,
moderation incidents, notification delivery, API request/error rates, JVM heap, p99 latency.
Prometheus scrapes all 8 Spring services including admin/moderation/compliance.

---

## 9. Test coverage

Pure Mockito unit tests, no running infrastructure required.

| Service | Tests | Focus |
|---|---|---|
| chat-service | 41 | encryption, view-once, DM, announcement guard, moderation/rule wiring |
| moderation-service | 21 | PASS/BLOCK/REPLACE/WARN, regex vs literal, incident recording |
| compliance-service | 16 | consent, erasure lifecycle, retention orchestration |
| admin-service | 9 | business-rule upsert/delete/defaults |
| notification-service | 6 | pagination, ownership enforcement |
| **Total** | **93** | |

Run all for a service: `cd <service> && mvn test`.

---

## 10. Operational runbook

**First boot**: `docker volume create rabbitmq_data` then `docker compose up -d --build`.
Wait ~90–120s for Keycloak. Create users in the Keycloak admin console and assign roles.

**Bootstrap an organization** (as `platform_admin`):
1. `POST /api/organizations` — create org
2. `POST /api/organizations/{id}/departments` — add departments
3. `PUT /api/organizations/{orgId}/users/{userId}` — assign users + roles
4. `POST /api/admin/organizations/{orgId}/rules/seed-defaults` — seed business rules
5. Use the **admin-panel** (http://localhost:3002) for all of the above via UI.

**Required production secrets** (`.env`): `AES_ENCRYPTION_KEY`, `INTERNAL_API_TOKEN`
(both `openssl rand -hex 32`), Keycloak/DB/RabbitMQ credentials.

**Key dashboards**: Grafana http://localhost:3001 · Prometheus http://localhost:9090 ·
RabbitMQ http://localhost:15672 · per-service Swagger at `/docs`.
