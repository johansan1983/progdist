# SuperChat — New Features Design

**Date:** 2026-05-06
**Scope:** Spellchecking, Emoji Picker, File/Image/Audio Attachments, View-Once Messages, Direct Messaging

---

## 1. Overview

Five features added to the existing SuperChat platform (Spring Boot 3.5 microservices, vanilla JS frontend, RabbitMQ, PostgreSQL, Redis). No new microservices. File storage handled by a self-hosted MinIO container. Build order follows dependencies: B → A → C → D → E.

---

## 2. New Infrastructure

### MinIO (docker-compose.yml)
- New `minio` service, image `minio/minio:latest`
- Ports: `9000` (S3 API), `9001` (web console)
- Single bucket: `superchat-attachments`, public-read policy so files are served directly by URL
- Credentials via env vars: `MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`
- `chat-service` gets env vars: `MINIO_URL` (internal: `http://minio:9000`), `MINIO_EXTERNAL_URL` (browser-reachable: `http://localhost:9000`), `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET`
- MinIO must have CORS configured to allow `PUT` from `http://localhost:3000` (set via MinIO client on startup or bucket policy)
- Presigned PUT URLs use `MINIO_EXTERNAL_URL` so the browser can reach MinIO directly; SDK calls use `MINIO_URL`

---

## 3. Database Changes

**Flyway migration V4** on `chat-service` (`superchat` DB):

```sql
-- Attachments and view-once on messages
ALTER TABLE chat_message ADD COLUMN view_once       BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_message ADD COLUMN viewed          BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_message ADD COLUMN attachment_url  VARCHAR(512);
ALTER TABLE chat_message ADD COLUMN attachment_type VARCHAR(20); -- IMAGE, AUDIO, FILE

-- DM support on conversations
ALTER TABLE conversation ADD COLUMN type              VARCHAR(10) NOT NULL DEFAULT 'GROUP';
ALTER TABLE conversation ADD COLUMN dm_participant_a  VARCHAR(36); -- Keycloak sub UUID
ALTER TABLE conversation ADD COLUMN dm_participant_b  VARCHAR(36); -- Keycloak sub UUID
```

JPA entities (`ChatMessage`, `Conversation`) updated to match. `ddl-auto: validate` stays in place.

---

## 4. Feature Designs

### B — Spellchecking
- Add `spellcheck="true"` (and optionally `lang="es"`) to the message `<textarea>` in `frontend/index.html`
- Browser handles underlines and correction menu natively
- No backend changes

### A — Emoji Picker
- Load `emoji-mart` via CDN `<script>` tag (no build tooling needed)
- Add a 😊 button next to the send button in the composer
- Clicking the button toggles a floating picker panel; selecting an emoji inserts it at the cursor position in the textarea
- No backend changes — emojis are Unicode text in message content

### C — File/Image/Audio Attachments

**Backend:**
- New `MinioConfig` `@Configuration` bean: `MinioClient` constructed from env vars
- New endpoint: `POST /chat/attachments/presign`
  - Auth required (JWT)
  - Request body: `{ filename: string, contentType: string, conversationId: long }`
  - Calls MinIO SDK `presignedPutObject()` with 15-minute TTL
  - Returns: `{ uploadUrl: string, objectKey: string, publicUrl: string }`
- `ChatMessage` entity gains `attachmentUrl` and `attachmentType` fields
- `POST /chat/messages` body gains optional `attachmentUrl` and `attachmentType` fields
- WebSocket event payload includes `attachmentUrl` and `attachmentType`

**Frontend flow:**
1. User clicks paperclip icon → file input (`accept="image/*,audio/*,*"`) opens
2. On file select: `POST /api/chat/attachments/presign` → get `uploadUrl` and `publicUrl`
3. `PUT uploadUrl` with file bytes directly to MinIO (browser → MinIO, bypasses chat-service)
4. On PUT success: send message with `attachmentUrl` and `attachmentType` populated
5. Rendering:
   - `IMAGE` → `<img>` inline
   - `AUDIO` → `<audio controls>`
   - `FILE` → download `<a>` link with filename

**Accepted types and size limit:** enforced client-side on the file input. Max file size: 50 MB (MinIO presign request validates via content-length on upload).

### D — View-Once Messages

**Backend:**
- `POST /chat/messages` accepts `viewOnce: boolean` (default `false`); stored as `view_once`
- `GET /chat/conversations/{id}/messages`:
  - For each message where `view_once=true` AND `viewed=false` AND `sender ≠ current user`:
    - Include full `content` and `attachment_url` in the response
    - Immediately execute `UPDATE chat_message SET viewed=true WHERE id=?` in the same request
  - For messages where `view_once=true` AND `viewed=true` AND `sender ≠ current user`:
    - Return tombstone: `content=null`, `attachmentUrl=null`, `viewOnceExpired=true`
  - The sender always sees their own view-once message in full (they sent it; they know what it said)
- WebSocket delivery: standard single publish — no special handling needed; the consumer marks viewed on first REST fetch

**Frontend:**
- Flame 🔥 toggle in the composer sets `viewOnce: true` on send
- View-once messages render with a 🔥 badge in the message bubble
- Tombstoned messages render: *"🔥 Message was viewed"* in muted style
- No re-delivery on WebSocket reconnect (server state is authoritative)

### E — Direct Messaging

**Backend:**
- `conversation.type` column: `GROUP` (default) or `DIRECT`
- New endpoint: `POST /chat/conversations/dm`
  - Body: `{ participantId: string }` (Keycloak sub of the other user)
  - Idempotent: checks for existing DM between the two users before creating
    ```sql
    WHERE type = 'DIRECT'
      AND ((dm_participant_a = :me AND dm_participant_b = :them)
        OR (dm_participant_a = :them AND dm_participant_b = :me))
    ```
  - Returns the conversation (existing or newly created)
- `GET /chat/conversations`:
  - Returns GROUP conversations (all) + DIRECT conversations where current user is participant A or B
  - For DIRECT conversations, response includes `otherParticipantName` (resolved by looking up the most recent message's `sender_name` from the other participant, falling back to their Keycloak sub if no messages yet)
- Access control on DM conversations: `POST /chat/messages` and `GET /chat/conversations/{id}/messages` verify the requesting user is a participant when `type = 'DIRECT'`; returns 403 otherwise

**Frontend:**
- Sidebar conversation list shows DMs with the other participant's `senderName` as the title (e.g. "💬 alice")
- "New DM" button opens a modal:
  - Fetches `GET /api/users/profiles` from user-service (returns all registered users)
  - Shows a searchable list; clicking a user calls `POST /api/chat/conversations/dm`
  - Opens the resulting conversation in the chat panel
- Presence panel: each username becomes a clickable link that triggers the same `POST /api/chat/conversations/dm` flow
- Conversation type badge: GROUP conversations show a 👥 prefix, DM conversations show a 💬 prefix

---

## 5. API Gateway Routing

- `POST /api/chat/attachments/presign` → chat-service (already covered by `/api/chat/**` route, no new route needed)
- `POST /api/chat/conversations/dm` → chat-service (same)
- `GET /api/users/profiles` → user-service — **new route needed** in `api-gateway.yml` if not already present

---

## 6. Error Handling

| Scenario | Behaviour |
|---|---|
| MinIO unreachable on presign | 503 from `/chat/attachments/presign` with message |
| MinIO PUT fails (browser-side) | Frontend shows error toast, message not sent |
| DM with self | 400 Bad Request |
| DM with unknown user | 404 Not Found |
| Non-participant access to DM | 403 Forbidden |
| Attachment > 50 MB | Client-side rejection before presign request |
| View-once message fetched twice | Second fetch returns tombstone (content null) |

---

## 7. Testing

- Unit tests (Mockito, no infrastructure) in `chat-service`:
  - `AttachmentPresignServiceTest`: mock `MinioClient`, verify presigned URL generation
  - `ChatServiceViewOnceTest`: verify `viewed` flag flipped on first fetch, tombstone on second
  - `ConversationDmServiceTest`: verify idempotent DM creation, 403 on non-participant access
- Existing `RedisPresenceServiceTest` and other unit tests unaffected
- Manual integration test checklist (Docker Compose up):
  - Upload an image → appears inline in chat
  - Upload audio → `<audio controls>` renders
  - Send view-once text → second client sees tombstone after first read
  - Create DM from presence panel → conversation opens, messages exchange privately
  - Create DM from "New DM" modal → same result for offline users

---

## 8. Build Order

| Step | Feature | Dependencies |
|---|---|---|
| 1 | B — Spellcheck | None |
| 2 | A — Emoji Picker | None |
| 3 | C — Attachments | MinIO container added |
| 4 | D — View-Once | C (attachment_url column exists) |
| 5 | E — Direct Messaging | Conversation type column from step 3 |
