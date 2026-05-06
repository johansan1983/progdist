# SuperChat Features Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add spellcheck, emoji picker, file/image/audio attachments (MinIO presigned upload), view-once messages, and direct messaging to SuperChat.

**Architecture:** Attachments are stored in MinIO (self-hosted S3); `chat-service` issues 15-minute presigned PUT URLs so the browser uploads directly. View-once is enforced server-side: first REST fetch returns content, immediately marks `viewed=true`; subsequent fetches get a tombstone. DMs reuse the existing `conversation` table with a `type` column (`GROUP`/`DIRECT`) and participant columns.

**Tech Stack:** Spring Boot 3.5, Java 21, PostgreSQL (Flyway V4 migration), MinIO Java SDK 8.5.17, vanilla JS/HTML/CSS frontend served by Nginx.

---

## File Map

**New files:**
- `chat-service/src/main/java/com/superchat/chat/config/MinioConfig.java`
- `chat-service/src/main/java/com/superchat/chat/service/AttachmentService.java`
- `chat-service/src/main/java/com/superchat/chat/web/AttachmentController.java`
- `chat-service/src/main/resources/db/migration/V4__Add_Attachments_ViewOnce_DM.sql`
- `chat-service/src/test/java/com/superchat/chat/service/AttachmentServiceTest.java`
- `chat-service/src/test/java/com/superchat/chat/service/ChatServiceViewOnceTest.java`
- `chat-service/src/test/java/com/superchat/chat/service/ConversationDmServiceTest.java`

**Modified files:**
- `docker-compose.yml` — add MinIO service
- `chat-service/pom.xml` — add MinIO SDK dependency
- `chat-service/src/main/java/com/superchat/chat/domain/ChatMessage.java` — add attachmentUrl, attachmentType, viewOnce, viewed fields
- `chat-service/src/main/java/com/superchat/chat/domain/Conversation.java` — add type, dmParticipantA/B, dmParticipantAName/BName fields
- `chat-service/src/main/java/com/superchat/chat/repo/ConversationRepository.java` — add DM lookup + list query
- `chat-service/src/main/java/com/superchat/chat/service/ChatService.java` — attachment + view-once + DM logic
- `chat-service/src/main/java/com/superchat/chat/web/ChatController.java` — new endpoints + updated request/response
- `frontend/index.html` — emoji button, paperclip, flame toggle, DM modal, conversation sidebar
- `frontend/app.js` — emoji picker, file upload, view-once, DM flows
- `frontend/styles.css` — new styles for all features

---

## Task 1: Feature B — Spellcheck

**Files:**
- Modify: `frontend/index.html`

- [ ] **Step 1: Add spellcheck attribute to the message textarea**

In `frontend/index.html`, find the message input line:
```html
<input id="messageInput" type="text" placeholder="Escribe un mensaje..." maxlength="1000" required />
```
Replace it with a textarea:
```html
<textarea id="messageInput" placeholder="Escribe un mensaje..." maxlength="2000" rows="1" spellcheck="true" lang="es" required></textarea>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/index.html
git commit -m "feat: enable native browser spellcheck on message composer"
```

---

## Task 2: Feature A — Emoji Picker

**Files:**
- Modify: `frontend/index.html`
- Modify: `frontend/styles.css`
- Modify: `frontend/app.js`

- [ ] **Step 1: Add emoji-mart CDN and emoji button to index.html**

Add the CDN script before `/app.js`:
```html
<script src="https://cdn.jsdelivr.net/npm/emoji-mart@5/dist/browser.js"></script>
```

Replace the `<form id="messageForm" class="composer">` block:
```html
<form id="messageForm" class="composer">
  <button type="button" id="emojiBtn" class="btn-icon" title="Emojis">😊</button>
  <div id="emojiPicker" class="emoji-picker-container hidden"></div>
  <textarea id="messageInput" placeholder="Escribe un mensaje..." maxlength="2000" rows="1" spellcheck="true" lang="es" required></textarea>
  <button type="submit">Enviar</button>
</form>
```

- [ ] **Step 2: Add CSS for emoji picker**

Append to `frontend/styles.css`:
```css
.composer {
  position: relative;
}

.btn-icon {
  background: none;
  border: none;
  cursor: pointer;
  font-size: 1.3rem;
  padding: 0.25rem;
  line-height: 1;
  border-radius: 6px;
  transition: background 0.15s;
}

.btn-icon:hover {
  background: var(--edge);
}

.emoji-picker-container {
  position: absolute;
  bottom: 3.5rem;
  left: 0;
  z-index: 100;
}

.composer textarea {
  flex: 1;
  resize: none;
  min-height: 2.2rem;
  max-height: 8rem;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--edge);
  border-radius: 8px;
  font-family: inherit;
  font-size: 0.95rem;
  outline: none;
  overflow-y: auto;
}

.composer textarea:focus {
  border-color: var(--accent);
}
```

- [ ] **Step 3: Add emoji picker JS logic to app.js**

Find the `// State` object in `app.js` and add `emojiPickerOpen: false` to it.

Find the `function initUI()` function (or the section where UI listeners are set up) and append:
```js
// Emoji picker
const emojiBtn = document.getElementById('emojiBtn');
const emojiPickerContainer = document.getElementById('emojiPicker');
const picker = new EmojiMart.Picker({
  locale: 'es',
  onEmojiSelect: (emoji) => {
    const input = document.getElementById('messageInput');
    const start = input.selectionStart;
    const end = input.selectionEnd;
    const text = input.value;
    input.value = text.slice(0, start) + emoji.native + text.slice(end);
    input.selectionStart = input.selectionEnd = start + emoji.native.length;
    input.focus();
    emojiPickerContainer.classList.add('hidden');
  }
});
emojiPickerContainer.appendChild(picker);

emojiBtn.addEventListener('click', (e) => {
  e.stopPropagation();
  emojiPickerContainer.classList.toggle('hidden');
});

document.addEventListener('click', () => {
  emojiPickerContainer.classList.add('hidden');
});
```

- [ ] **Step 4: Commit**

```bash
git add frontend/index.html frontend/styles.css frontend/app.js
git commit -m "feat: add emoji picker via emoji-mart CDN"
```

---

## Task 3: Infrastructure — MinIO + Flyway V4

**Files:**
- Modify: `docker-compose.yml`
- Create: `chat-service/src/main/resources/db/migration/V4__Add_Attachments_ViewOnce_DM.sql`

- [ ] **Step 1: Add MinIO service to docker-compose.yml**

Add before the `# ── Spring Cloud Platform` section:
```yaml
  minio:
    image: minio/minio:latest
    container_name: minio
    restart: always
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: superchat
      MINIO_ROOT_PASSWORD: superchat123
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD", "mc", "ready", "local"]
      interval: 10s
      timeout: 5s
      retries: 10
      start_period: 20s
```

Add `minio_data:` under the `volumes:` block at the end.

Add `minio` to `chat-service` depends_on:
```yaml
      minio:
        condition: service_healthy
```

Add MinIO env vars to `chat-service` environment:
```yaml
      MINIO_URL: http://minio:9000
      MINIO_EXTERNAL_URL: http://localhost:9000
      MINIO_ACCESS_KEY: superchat
      MINIO_SECRET_KEY: superchat123
      MINIO_BUCKET: superchat-attachments
```

- [ ] **Step 2: Write Flyway V4 migration**

Create `chat-service/src/main/resources/db/migration/V4__Add_Attachments_ViewOnce_DM.sql`:
```sql
-- Allow content-less messages (attachment-only)
ALTER TABLE chat_message ALTER COLUMN content DROP NOT NULL;

-- Attachment support
ALTER TABLE chat_message ADD COLUMN attachment_url  VARCHAR(512);
ALTER TABLE chat_message ADD COLUMN attachment_type VARCHAR(20);

-- View-once support
ALTER TABLE chat_message ADD COLUMN view_once BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE chat_message ADD COLUMN viewed    BOOLEAN NOT NULL DEFAULT FALSE;

-- Direct messaging support
ALTER TABLE conversation ADD COLUMN type                 VARCHAR(10) NOT NULL DEFAULT 'GROUP';
ALTER TABLE conversation ADD COLUMN dm_participant_a      VARCHAR(36);
ALTER TABLE conversation ADD COLUMN dm_participant_b      VARCHAR(36);
ALTER TABLE conversation ADD COLUMN dm_participant_a_name VARCHAR(120);
ALTER TABLE conversation ADD COLUMN dm_participant_b_name VARCHAR(120);
```

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml chat-service/src/main/resources/db/migration/V4__Add_Attachments_ViewOnce_DM.sql
git commit -m "feat: add MinIO service and V4 Flyway migration for attachments, view-once, and DM"
```

---

## Task 4: MinIO SDK Dependency + Config Bean

**Files:**
- Modify: `chat-service/pom.xml`
- Create: `chat-service/src/main/java/com/superchat/chat/config/MinioConfig.java`

- [ ] **Step 1: Add MinIO SDK to pom.xml**

Add inside `<dependencies>`:
```xml
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.17</version>
</dependency>
```

- [ ] **Step 2: Create MinioConfig.java**

Create `chat-service/src/main/java/com/superchat/chat/config/MinioConfig.java`:
```java
package com.superchat.chat.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.messages.CorsConfiguration;
import io.minio.messages.CorsRule;
import io.minio.SetBucketCorsArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.List;

@Configuration
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Value("${minio.url}") private String url;
    @Value("${minio.external-url}") private String externalUrl;
    @Value("${minio.access-key}") private String accessKey;
    @Value("${minio.secret-key}") private String secretKey;
    @Value("${minio.bucket}") private String bucket;

    @Bean("minioClient")
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean("externalMinioClient")
    public MinioClient externalMinioClient() {
        return MinioClient.builder()
                .endpoint(externalUrl)
                .credentials(accessKey, secretKey)
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initBucket() {
        try {
            MinioClient client = minioClient();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("Created MinIO bucket: {}", bucket);
            }

            String policy = """
                    {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":"*",
                    "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
                    """.formatted(bucket);
            client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());

            CorsRule rule = new CorsRule(List.of("*"), List.of("GET", "PUT", "HEAD"), List.of("*"), null, null);
            client.setBucketCors(SetBucketCorsArgs.builder()
                    .bucket(bucket)
                    .config(new CorsConfiguration(List.of(rule)))
                    .build());
            log.info("MinIO bucket configured with public-read policy and CORS");
        } catch (Exception e) {
            log.warn("MinIO bucket init failed (will retry on next start): {}", e.getMessage());
        }
    }
}
```

- [ ] **Step 3: Verify compile**

```bash
cd chat-service && mvn compile -q
```

Expected: BUILD SUCCESS (no errors)

- [ ] **Step 4: Commit**

```bash
git add chat-service/pom.xml chat-service/src/main/java/com/superchat/chat/config/MinioConfig.java
git commit -m "feat: add MinIO SDK dependency and MinioConfig bean with bucket init"
```

---

## Task 5: AttachmentService (TDD)

**Files:**
- Create: `chat-service/src/main/java/com/superchat/chat/service/AttachmentService.java`
- Create: `chat-service/src/test/java/com/superchat/chat/service/AttachmentServiceTest.java`

- [ ] **Step 1: Write the failing test**

Create `chat-service/src/test/java/com/superchat/chat/service/AttachmentServiceTest.java`:
```java
package com.superchat.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AttachmentServiceTest {

    private AttachmentService attachmentService;
    private MinioClient internalClient;
    private MinioClient externalClient;

    @BeforeEach
    void setUp() {
        internalClient = mock(MinioClient.class);
        externalClient = mock(MinioClient.class);
        attachmentService = new AttachmentService(internalClient, externalClient, "superchat-attachments", "http://localhost:9000");
    }

    @Test
    void testPresign_ValidInput_ReturnsPresignResult() throws Exception {
        when(externalClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/superchat-attachments/abc123?X-Amz-Signature=sig");

        AttachmentService.PresignResult result = attachmentService.presign("photo.jpg", "image/jpeg", 1L);

        assertNotNull(result.uploadUrl());
        assertNotNull(result.objectKey());
        assertNotNull(result.publicUrl());
        assertTrue(result.uploadUrl().contains("localhost:9000"));
        assertTrue(result.objectKey().endsWith("photo.jpg"));
        assertEquals("IMAGE", result.attachmentType());
    }

    @Test
    void testPresign_AudioFile_ReturnsAudioType() throws Exception {
        when(externalClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/superchat-attachments/abc123?X-Amz-Signature=sig");

        AttachmentService.PresignResult result = attachmentService.presign("voice.mp3", "audio/mpeg", 1L);

        assertEquals("AUDIO", result.attachmentType());
    }

    @Test
    void testPresign_GenericFile_ReturnsFileType() throws Exception {
        when(externalClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/superchat-attachments/abc123?X-Amz-Signature=sig");

        AttachmentService.PresignResult result = attachmentService.presign("report.pdf", "application/pdf", 1L);

        assertEquals("FILE", result.attachmentType());
    }

    @Test
    void testPresign_PublicUrlUsesExternalHost() throws Exception {
        when(externalClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://localhost:9000/superchat-attachments/abc123?X-Amz-Signature=sig");

        AttachmentService.PresignResult result = attachmentService.presign("img.png", "image/png", 1L);

        assertTrue(result.publicUrl().startsWith("http://localhost:9000/superchat-attachments/"));
        assertFalse(result.publicUrl().contains("X-Amz-Signature"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd chat-service && mvn test -Dtest=AttachmentServiceTest -q 2>&1 | tail -10
```

Expected: COMPILATION ERROR — `AttachmentService` does not exist yet.

- [ ] **Step 3: Implement AttachmentService**

Create `chat-service/src/main/java/com/superchat/chat/service/AttachmentService.java`:
```java
package com.superchat.chat.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AttachmentService {

    private final MinioClient internalClient;
    private final MinioClient externalClient;
    private final String bucket;
    private final String externalUrl;

    public AttachmentService(
            @Qualifier("minioClient") MinioClient internalClient,
            @Qualifier("externalMinioClient") MinioClient externalClient,
            @Value("${minio.bucket}") String bucket,
            @Value("${minio.external-url}") String externalUrl
    ) {
        this.internalClient = internalClient;
        this.externalClient = externalClient;
        this.bucket = bucket;
        this.externalUrl = externalUrl;
    }

    public PresignResult presign(String filename, String contentType, Long conversationId) throws Exception {
        String objectKey = conversationId + "/" + UUID.randomUUID() + "/" + filename;

        String uploadUrl = externalClient.getPresignedObjectUrl(
                GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(bucket)
                        .object(objectKey)
                        .expiry(15, TimeUnit.MINUTES)
                        .build()
        );

        String publicUrl = externalUrl + "/" + bucket + "/" + objectKey;
        String attachmentType = resolveType(contentType);

        return new PresignResult(uploadUrl, objectKey, publicUrl, attachmentType);
    }

    private String resolveType(String contentType) {
        if (contentType == null) return "FILE";
        if (contentType.startsWith("image/")) return "IMAGE";
        if (contentType.startsWith("audio/")) return "AUDIO";
        return "FILE";
    }

    public record PresignResult(String uploadUrl, String objectKey, String publicUrl, String attachmentType) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
cd chat-service && mvn test -Dtest=AttachmentServiceTest -q 2>&1 | tail -5
```

Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 5: Commit**

```bash
git add chat-service/src/main/java/com/superchat/chat/service/AttachmentService.java \
        chat-service/src/test/java/com/superchat/chat/service/AttachmentServiceTest.java
git commit -m "feat: add AttachmentService with MinIO presigned URL generation"
```

---

## Task 6: AttachmentController

**Files:**
- Create: `chat-service/src/main/java/com/superchat/chat/web/AttachmentController.java`

- [ ] **Step 1: Create AttachmentController.java**

```java
package com.superchat.chat.web;

import com.superchat.chat.service.AttachmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/chat/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping("/presign")
    public ResponseEntity<Map<String, Object>> presign(@RequestBody PresignRequest request) {
        if (request.filename() == null || request.filename().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "filename is required");
        }
        if (request.conversationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId is required");
        }
        try {
            AttachmentService.PresignResult result = attachmentService.presign(
                    request.filename(), request.contentType(), request.conversationId());
            return ResponseEntity.ok(Map.of(
                    "uploadUrl", result.uploadUrl(),
                    "objectKey", result.objectKey(),
                    "publicUrl", result.publicUrl(),
                    "attachmentType", result.attachmentType()
            ));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "MinIO unavailable: " + e.getMessage());
        }
    }

    public record PresignRequest(String filename, String contentType, Long conversationId) {}
}
```

- [ ] **Step 2: Compile**

```bash
cd chat-service && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add chat-service/src/main/java/com/superchat/chat/web/AttachmentController.java
git commit -m "feat: add POST /chat/attachments/presign endpoint"
```

---

## Task 7: ChatMessage Entity + ChatService + ChatController — Attachment Support

**Files:**
- Modify: `chat-service/src/main/java/com/superchat/chat/domain/ChatMessage.java`
- Modify: `chat-service/src/main/java/com/superchat/chat/service/ChatService.java`
- Modify: `chat-service/src/main/java/com/superchat/chat/web/ChatController.java`

- [ ] **Step 1: Add attachment fields to ChatMessage entity**

In `ChatMessage.java`, change the `content` field annotation (make nullable for attachment-only messages) and add new fields after `senderName`:

```java
// Change:
@Column(nullable = false, length = 2000)
private String content;
// To:
@Column(length = 2000)
private String content;
```

Add after the `senderName` field and its getter/setter:
```java
@Column(length = 512)
private String attachmentUrl;

@Column(length = 20)
private String attachmentType;
```

Add getters and setters for both:
```java
public String getAttachmentUrl() { return attachmentUrl; }
public void setAttachmentUrl(String attachmentUrl) { this.attachmentUrl = attachmentUrl; }

public String getAttachmentType() { return attachmentType; }
public void setAttachmentType(String attachmentType) { this.attachmentType = attachmentType; }
```

- [ ] **Step 2: Update ChatService.sendMessage to accept attachment fields**

Change the `sendMessage` signature to:
```java
public ChatMessage sendMessage(Long conversationId, String content, String sender, String senderName,
                               String attachmentUrl, String attachmentType) {
```

Update validation (content OR attachment required):
```java
if ((content == null || content.isBlank()) && (attachmentUrl == null || attachmentUrl.isBlank())) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "content or attachment is required");
}
```

In the message builder block, add:
```java
if (attachmentUrl != null && !attachmentUrl.isBlank()) {
    message.setAttachmentUrl(attachmentUrl.trim());
    message.setAttachmentType(attachmentType);
}
if (content != null && !content.isBlank()) {
    message.setContent(content.trim());
}
```

Remove the previous `message.setContent(content.trim());` line.

In the `chatEvent` Map, add (use `Map.ofEntries` instead of `Map.of` since Map.of is limited to 10 entries):
```java
Map<String, Object> chatEvent = new java.util.HashMap<>();
chatEvent.put("eventType", "CHAT_MESSAGE_CREATED");
chatEvent.put("messageId", saved.getId());
chatEvent.put("conversationId", conversationId);
chatEvent.put("sender", sender);
chatEvent.put("senderName", senderName != null ? senderName : sender);
chatEvent.put("content", saved.getContent() != null ? saved.getContent() : "");
chatEvent.put("createdAt", saved.getCreatedAt().toString());
chatEvent.put("publishedAt", Instant.now().toString());
chatEvent.put("attachmentUrl", saved.getAttachmentUrl() != null ? saved.getAttachmentUrl() : "");
chatEvent.put("attachmentType", saved.getAttachmentType() != null ? saved.getAttachmentType() : "");
```

Do the same replacement for `notificationEvent` (replace `Map.of` with `HashMap`).

- [ ] **Step 3: Update ChatController MessageRequest + sendMessage call**

Change `MessageRequest` record to:
```java
public record MessageRequest(Long conversationId, String content, String attachmentUrl, String attachmentType, boolean viewOnce) {}
```

Update the `sendMessage` method call:
```java
ChatMessage saved = chatService.sendMessage(
        request.conversationId(), request.content(), sender, senderName,
        request.attachmentUrl(), request.attachmentType());
```

Update the response map in `sendMessage` to include attachment fields:
```java
Map<String, Object> resp = new HashMap<>();
resp.put("id", saved.getId());
resp.put("conversationId", saved.getConversation().getId());
resp.put("sender", saved.getSender());
resp.put("senderName", saved.getSenderName() != null ? saved.getSenderName() : saved.getSender());
resp.put("content", saved.getContent() != null ? saved.getContent() : "");
resp.put("attachmentUrl", saved.getAttachmentUrl() != null ? saved.getAttachmentUrl() : "");
resp.put("attachmentType", saved.getAttachmentType() != null ? saved.getAttachmentType() : "");
resp.put("createdAt", saved.getCreatedAt().toString());
resp.put("status", "persisted_and_published");
return ResponseEntity.ok(resp);
```

Update `listMessages` to include attachment fields in the per-message map:
```java
m.put("attachmentUrl", message.getAttachmentUrl() != null ? message.getAttachmentUrl() : "");
m.put("attachmentType", message.getAttachmentType() != null ? message.getAttachmentType() : "");
```

- [ ] **Step 4: Fix ChatServiceTest (sendMessage now takes 6 args)**

In `ChatServiceTest.java`, the test does not call `sendMessage` directly, so it should still compile. But if any test does, update the call to pass two extra `null` args. Run:

```bash
cd chat-service && mvn test -q 2>&1 | tail -5
```

Expected: all existing tests pass.

- [ ] **Step 5: Commit**

```bash
git add chat-service/src/main/java/com/superchat/chat/domain/ChatMessage.java \
        chat-service/src/main/java/com/superchat/chat/service/ChatService.java \
        chat-service/src/main/java/com/superchat/chat/web/ChatController.java \
        chat-service/src/test/java/com/superchat/chat/service/ChatServiceTest.java
git commit -m "feat: add attachment fields to ChatMessage, ChatService, and ChatController"
```

---

## Task 8: Frontend — Attachment UI

**Files:**
- Modify: `frontend/index.html`
- Modify: `frontend/styles.css`
- Modify: `frontend/app.js`

- [ ] **Step 1: Add paperclip button to composer in index.html**

In the `<form id="messageForm" class="composer">`, add the file input and paperclip button before the emoji button:
```html
<form id="messageForm" class="composer">
  <input type="file" id="fileInput" class="hidden" accept="image/*,audio/*,*/*" />
  <button type="button" id="attachBtn" class="btn-icon" title="Adjuntar archivo">📎</button>
  <button type="button" id="emojiBtn" class="btn-icon" title="Emojis">😊</button>
  <div id="emojiPicker" class="emoji-picker-container hidden"></div>
  <div id="attachmentPreview" class="attachment-preview hidden"></div>
  <textarea id="messageInput" placeholder="Escribe un mensaje..." maxlength="2000" rows="1" spellcheck="true" lang="es"></textarea>
  <button type="submit" id="sendBtn">Enviar</button>
</form>
```

- [ ] **Step 2: Add CSS for attachment preview**

Append to `frontend/styles.css`:
```css
.attachment-preview {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.4rem 0.6rem;
  background: var(--edge);
  border-radius: 8px;
  font-size: 0.85rem;
  color: var(--muted);
}

.attachment-preview img {
  max-height: 60px;
  max-width: 80px;
  object-fit: cover;
  border-radius: 4px;
}

.attachment-preview .remove-attachment {
  cursor: pointer;
  color: var(--danger);
  font-size: 1rem;
  line-height: 1;
  background: none;
  border: none;
  padding: 0 0.2rem;
}

.msg-image {
  max-width: 280px;
  max-height: 200px;
  border-radius: 8px;
  display: block;
  margin-top: 0.4rem;
}

.msg-audio {
  display: block;
  margin-top: 0.4rem;
  max-width: 280px;
}

.msg-file {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  margin-top: 0.4rem;
  color: var(--accent);
  text-decoration: none;
  font-size: 0.9rem;
}
```

- [ ] **Step 3: Add attachment JS to app.js**

In the state object, add:
```js
pendingAttachment: null, // { file, uploadUrl, publicUrl, attachmentType }
```

Add attachment handling in the UI setup section:
```js
const attachBtn = document.getElementById('attachBtn');
const fileInput = document.getElementById('fileInput');
const attachmentPreview = document.getElementById('attachmentPreview');

attachBtn.addEventListener('click', () => fileInput.click());

fileInput.addEventListener('change', async () => {
  const file = fileInput.files[0];
  if (!file) return;
  if (file.size > 50 * 1024 * 1024) {
    alert('El archivo no puede superar 50 MB.');
    fileInput.value = '';
    return;
  }

  const contentType = file.type || 'application/octet-stream';
  try {
    const resp = await api('/chat/attachments/presign', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ filename: file.name, contentType, conversationId: state.activeConversationId })
    });
    const data = await resp.json();

    state.pendingAttachment = { file, uploadUrl: data.uploadUrl, publicUrl: data.publicUrl, attachmentType: data.attachmentType };

    attachmentPreview.classList.remove('hidden');
    if (data.attachmentType === 'IMAGE') {
      const img = document.createElement('img');
      img.src = URL.createObjectURL(file);
      attachmentPreview.innerHTML = '';
      attachmentPreview.appendChild(img);
    } else {
      attachmentPreview.textContent = `📎 ${file.name}`;
    }
    const removeBtn = document.createElement('button');
    removeBtn.textContent = '✕';
    removeBtn.className = 'remove-attachment';
    removeBtn.onclick = () => {
      state.pendingAttachment = null;
      attachmentPreview.classList.add('hidden');
      attachmentPreview.innerHTML = '';
      fileInput.value = '';
    };
    attachmentPreview.appendChild(removeBtn);
  } catch (e) {
    alert('Error al preparar el adjunto: ' + e.message);
    fileInput.value = '';
  }
});
```

In the message send handler (where `POST /chat/messages` is called), before the API call, upload the file to MinIO if there's a pending attachment:
```js
let attachmentUrl = '';
let attachmentType = '';
if (state.pendingAttachment) {
  const { file, uploadUrl, publicUrl, attachmentType: aType } = state.pendingAttachment;
  await fetch(uploadUrl, {
    method: 'PUT',
    headers: { 'Content-Type': file.type || 'application/octet-stream' },
    body: file
  });
  attachmentUrl = publicUrl;
  attachmentType = aType;
  state.pendingAttachment = null;
  attachmentPreview.classList.add('hidden');
  attachmentPreview.innerHTML = '';
  fileInput.value = '';
}
```

Add `attachmentUrl` and `attachmentType` to the POST body:
```js
body: JSON.stringify({ conversationId: state.activeConversationId, content, attachmentUrl, attachmentType })
```

In the `appendMessage` function (where message HTML is built), add attachment rendering after the message text:
```js
if (msg.attachmentType === 'IMAGE' && msg.attachmentUrl) {
  const img = document.createElement('img');
  img.src = msg.attachmentUrl;
  img.className = 'msg-image';
  bubble.appendChild(img);
} else if (msg.attachmentType === 'AUDIO' && msg.attachmentUrl) {
  const audio = document.createElement('audio');
  audio.src = msg.attachmentUrl;
  audio.controls = true;
  audio.className = 'msg-audio';
  bubble.appendChild(audio);
} else if (msg.attachmentType === 'FILE' && msg.attachmentUrl) {
  const link = document.createElement('a');
  link.href = msg.attachmentUrl;
  link.textContent = '📎 Descargar archivo';
  link.className = 'msg-file';
  link.target = '_blank';
  link.rel = 'noopener';
  bubble.appendChild(link);
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/index.html frontend/styles.css frontend/app.js
git commit -m "feat: add file/image/audio attachment upload UI with MinIO presigned PUT"
```

---

## Task 9: View-Once Backend (TDD)

**Files:**
- Modify: `chat-service/src/main/java/com/superchat/chat/domain/ChatMessage.java`
- Modify: `chat-service/src/main/java/com/superchat/chat/service/ChatService.java`
- Modify: `chat-service/src/main/java/com/superchat/chat/web/ChatController.java`
- Modify: `chat-service/src/main/java/com/superchat/chat/repo/ChatMessageRepository.java`
- Create: `chat-service/src/test/java/com/superchat/chat/service/ChatServiceViewOnceTest.java`

- [ ] **Step 1: Add viewOnce and viewed fields to ChatMessage**

Add to `ChatMessage.java` after `attachmentType` field:
```java
@Column(nullable = false)
private boolean viewOnce = false;

@Column(nullable = false)
private boolean viewed = false;
```

Add getters and setters:
```java
public boolean isViewOnce() { return viewOnce; }
public void setViewOnce(boolean viewOnce) { this.viewOnce = viewOnce; }
public boolean isViewed() { return viewed; }
public void setViewed(boolean viewed) { this.viewed = viewed; }
```

- [ ] **Step 2: Add updateViewedById to ChatMessageRepository**

In `ChatMessageRepository.java`, add:
```java
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Modifying
@Transactional
@Query("UPDATE ChatMessage m SET m.viewed = true WHERE m.id = :id")
void markViewed(@Param("id") Long id);
```

- [ ] **Step 3: Write the failing test**

Create `chat-service/src/test/java/com/superchat/chat/service/ChatServiceViewOnceTest.java`:
```java
package com.superchat.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import com.superchat.chat.domain.ChatMessage;
import com.superchat.chat.domain.Conversation;
import com.superchat.chat.repo.ChatMessageRepository;
import com.superchat.chat.repo.ConversationRepository;

class ChatServiceViewOnceTest {

    private ChatService chatService;
    private ChatMessageRepository messageRepository;
    private ConversationRepository conversationRepository;
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void setUp() {
        messageRepository = mock(ChatMessageRepository.class);
        conversationRepository = mock(ConversationRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        chatService = new ChatService(
                conversationRepository, messageRepository, rabbitTemplate,
                "chat.exchange", "chat.routing.key",
                "notifications.exchange", "notifications.message.created"
        );
    }

    @Test
    void testListMessages_ViewOnceNotViewed_OtherUser_IncludesContentAndMarksViewed() {
        ChatMessage msg = viewOnceMessage(1L, "secret-sender-id", "Hello secret", false);
        Page<ChatMessage> page = new PageImpl<>(List.of(msg));
        when(messageRepository.findByConversationId(eq(1L), any(Pageable.class))).thenReturn(page);

        List<Map<String, Object>> result = chatService.listMessages(1L, 0, 50, "other-user-id");

        assertEquals(1, result.size());
        assertEquals("Hello secret", result.get(0).get("content"));
        assertFalse((Boolean) result.get(0).get("viewOnceExpired"));
        verify(messageRepository).markViewed(1L);
    }

    @Test
    void testListMessages_ViewOnceAlreadyViewed_OtherUser_ReturnsTombstone() {
        ChatMessage msg = viewOnceMessage(2L, "secret-sender-id", "Hello secret", true);
        Page<ChatMessage> page = new PageImpl<>(List.of(msg));
        when(messageRepository.findByConversationId(eq(1L), any(Pageable.class))).thenReturn(page);

        List<Map<String, Object>> result = chatService.listMessages(1L, 0, 50, "other-user-id");

        assertEquals(1, result.size());
        assertNull(result.get(0).get("content"));
        assertTrue((Boolean) result.get(0).get("viewOnceExpired"));
        verify(messageRepository, never()).markViewed(anyLong());
    }

    @Test
    void testListMessages_ViewOnce_SenderAlwaysSeesFull() {
        ChatMessage msg = viewOnceMessage(3L, "alice-id", "My own message", false);
        Page<ChatMessage> page = new PageImpl<>(List.of(msg));
        when(messageRepository.findByConversationId(eq(1L), any(Pageable.class))).thenReturn(page);

        List<Map<String, Object>> result = chatService.listMessages(1L, 0, 50, "alice-id");

        assertEquals("My own message", result.get(0).get("content"));
        assertFalse((Boolean) result.get(0).get("viewOnceExpired"));
        verify(messageRepository, never()).markViewed(anyLong());
    }

    @Test
    void testListMessages_NormalMessage_NeverMarkedViewed() {
        ChatMessage msg = new ChatMessage();
        msg.setId(4L);
        msg.setSender("user1");
        msg.setContent("normal");
        msg.setCreatedAt(Instant.now());
        msg.setViewOnce(false);

        Conversation conv = new Conversation();
        conv.setId(1L);
        conv.setName("test");
        msg.setConversation(conv);

        Page<ChatMessage> page = new PageImpl<>(List.of(msg));
        when(messageRepository.findByConversationId(eq(1L), any(Pageable.class))).thenReturn(page);

        List<Map<String, Object>> result = chatService.listMessages(1L, 0, 50, "any-user");

        assertEquals("normal", result.get(0).get("content"));
        verify(messageRepository, never()).markViewed(anyLong());
    }

    private ChatMessage viewOnceMessage(Long id, String sender, String content, boolean viewed) {
        Conversation conv = new Conversation();
        conv.setId(1L);
        conv.setName("test");

        ChatMessage msg = new ChatMessage();
        msg.setId(id);
        msg.setSender(sender);
        msg.setContent(content);
        msg.setCreatedAt(Instant.now());
        msg.setViewOnce(true);
        msg.setViewed(viewed);
        msg.setConversation(conv);
        return msg;
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

```bash
cd chat-service && mvn test -Dtest=ChatServiceViewOnceTest -q 2>&1 | tail -10
```

Expected: COMPILATION ERROR or test failures — `listMessages` doesn't take a `currentUserId` arg yet.

- [ ] **Step 5: Update ChatService.listMessages to return view-once-aware list**

Change the `listMessages` signature and return type:
```java
@Transactional
public List<Map<String, Object>> listMessages(Long conversationId, int page, int size, String currentUserId) {
    int safePage = Math.max(0, page);
    int safeSize = Math.max(1, Math.min(size, 200));
    PageRequest pr = PageRequest.of(safePage, safeSize, Sort.by("createdAt").ascending());
    Page<ChatMessage> paged = chatMessageRepository.findByConversationId(conversationId, pr);

    return paged.getContent().stream().map(msg -> {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", msg.getId());
        m.put("conversationId", msg.getConversation().getId());
        m.put("sender", msg.getSender());
        m.put("senderName", msg.getSenderName() != null ? msg.getSenderName() : msg.getSender());
        m.put("attachmentUrl", msg.getAttachmentUrl() != null ? msg.getAttachmentUrl() : "");
        m.put("attachmentType", msg.getAttachmentType() != null ? msg.getAttachmentType() : "");
        m.put("createdAt", msg.getCreatedAt().toString());
        m.put("viewOnce", msg.isViewOnce());

        boolean isSender = msg.getSender().equals(currentUserId);

        if (msg.isViewOnce() && !isSender) {
            if (!msg.isViewed()) {
                m.put("content", msg.getContent() != null ? msg.getContent() : "");
                m.put("viewOnceExpired", false);
                chatMessageRepository.markViewed(msg.getId());
            } else {
                m.put("content", null);
                m.put("attachmentUrl", null);
                m.put("viewOnceExpired", true);
            }
        } else {
            m.put("content", msg.getContent() != null ? msg.getContent() : "");
            m.put("viewOnceExpired", false);
        }
        return m;
    }).toList();
}
```

Add `import java.util.List; import java.util.Map;` if not already present.

Also update `sendMessage` to accept and store `viewOnce` flag. Change signature to:
```java
public ChatMessage sendMessage(Long conversationId, String content, String sender, String senderName,
                               String attachmentUrl, String attachmentType, boolean viewOnce) {
```

Add inside the method, after `message.setAttachmentType`:
```java
message.setViewOnce(viewOnce);
```

- [ ] **Step 6: Run test to verify it passes**

```bash
cd chat-service && mvn test -Dtest=ChatServiceViewOnceTest -q 2>&1 | tail -5
```

Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 7: Update ChatController for viewOnce**

The `MessageRequest` already has `viewOnce` field (added in Task 7). Update the call:
```java
ChatMessage saved = chatService.sendMessage(
        request.conversationId(), request.content(), sender, senderName,
        request.attachmentUrl(), request.attachmentType(), request.viewOnce());
```

Add `viewOnce` to the send response:
```java
resp.put("viewOnce", saved.isViewOnce());
```

Update `listMessages` in ChatController to call the new signature:
```java
String currentUserId = authentication.getName();
List<Map<String, Object>> messages = chatService.listMessages(conversationId, page, size, currentUserId);

return ResponseEntity.ok(Map.of(
        "messages", messages,
        "page", page,
        "size", size
));
```

Remove the old `paged` variable and stream mapping (the service now returns the mapped list).

- [ ] **Step 8: Run all tests**

```bash
cd chat-service && mvn test -q 2>&1 | tail -10
```

Expected: all tests pass (update `ChatServiceTest.listMessages` tests — they now call the 4-arg version. Update their calls to pass a `currentUserId` string like `"any-user"`).

Fix `ChatServiceTest` — change all `chatService.listMessages(conversationId, 0, 50)` calls to `chatService.listMessages(conversationId, 0, 50, "any-user")` and update the return type check from `Page<ChatMessage>` to `List<Map<String, Object>>`.

- [ ] **Step 9: Commit**

```bash
git add chat-service/src/main/java/com/superchat/chat/domain/ChatMessage.java \
        chat-service/src/main/java/com/superchat/chat/repo/ChatMessageRepository.java \
        chat-service/src/main/java/com/superchat/chat/service/ChatService.java \
        chat-service/src/main/java/com/superchat/chat/web/ChatController.java \
        chat-service/src/test/java/com/superchat/chat/service/ChatServiceViewOnceTest.java \
        chat-service/src/test/java/com/superchat/chat/service/ChatServiceTest.java
git commit -m "feat: add view-once message support with server-side tombstone on second read"
```

---

## Task 10: View-Once Frontend

**Files:**
- Modify: `frontend/index.html`
- Modify: `frontend/styles.css`
- Modify: `frontend/app.js`

- [ ] **Step 1: Add flame toggle button to composer**

In `index.html`, add the flame toggle after the emoji button:
```html
<button type="button" id="viewOnceBtn" class="btn-icon btn-view-once" title="Mensaje de una vez">🔥</button>
```

- [ ] **Step 2: Add CSS for view-once**

Append to `styles.css`:
```css
.btn-view-once.active {
  background: #fef3c7;
  border-radius: 6px;
}

.badge-view-once {
  font-size: 0.7rem;
  margin-left: 0.25rem;
  opacity: 0.8;
}

.msg-tombstone {
  font-style: italic;
  color: var(--muted);
  font-size: 0.85rem;
}
```

- [ ] **Step 3: Add view-once JS**

In the state object, add:
```js
viewOnceActive: false,
```

In the UI setup:
```js
const viewOnceBtn = document.getElementById('viewOnceBtn');
viewOnceBtn.addEventListener('click', () => {
  state.viewOnceActive = !state.viewOnceActive;
  viewOnceBtn.classList.toggle('active', state.viewOnceActive);
  viewOnceBtn.title = state.viewOnceActive ? 'Mensaje de una vez (activo)' : 'Mensaje de una vez';
});
```

Add `viewOnce: state.viewOnceActive` to the POST body when sending a message, and after sending reset:
```js
state.viewOnceActive = false;
viewOnceBtn.classList.remove('active');
```

In the `appendMessage` function, handle view-once rendering:
```js
if (msg.viewOnceExpired) {
  const tombstone = document.createElement('span');
  tombstone.className = 'msg-tombstone';
  tombstone.textContent = '🔥 Mensaje visto';
  bubble.appendChild(tombstone);
} else {
  if (msg.content) {
    const text = document.createElement('span');
    text.textContent = msg.content;
    bubble.appendChild(text);
  }
  if (msg.viewOnce) {
    const badge = document.createElement('span');
    badge.className = 'badge-view-once';
    badge.textContent = '🔥';
    badge.title = 'Mensaje de una vez';
    bubble.appendChild(badge);
  }
  // attachment rendering (from Task 8) goes here
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/index.html frontend/styles.css frontend/app.js
git commit -m "feat: add view-once toggle in composer and tombstone rendering"
```

---

## Task 11: DM Backend (TDD)

**Files:**
- Modify: `chat-service/src/main/java/com/superchat/chat/domain/Conversation.java`
- Modify: `chat-service/src/main/java/com/superchat/chat/repo/ConversationRepository.java`
- Modify: `chat-service/src/main/java/com/superchat/chat/service/ChatService.java`
- Create: `chat-service/src/test/java/com/superchat/chat/service/ConversationDmServiceTest.java`

- [ ] **Step 1: Update Conversation entity**

Full replacement of `Conversation.java`:
```java
package com.superchat.chat.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversation")
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 10)
    private String type = "GROUP";

    @Column(length = 36)
    private String dmParticipantA;

    @Column(length = 36)
    private String dmParticipantB;

    @Column(length = 120)
    private String dmParticipantAName;

    @Column(length = 120)
    private String dmParticipantBName;

    @PrePersist
    void onCreate() { this.createdAt = Instant.now(); }

    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Instant getCreatedAt() { return createdAt; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getDmParticipantA() { return dmParticipantA; }
    public void setDmParticipantA(String dmParticipantA) { this.dmParticipantA = dmParticipantA; }
    public String getDmParticipantB() { return dmParticipantB; }
    public void setDmParticipantB(String dmParticipantB) { this.dmParticipantB = dmParticipantB; }
    public String getDmParticipantAName() { return dmParticipantAName; }
    public void setDmParticipantAName(String dmParticipantAName) { this.dmParticipantAName = dmParticipantAName; }
    public String getDmParticipantBName() { return dmParticipantBName; }
    public void setDmParticipantBName(String dmParticipantBName) { this.dmParticipantBName = dmParticipantBName; }
}
```

- [ ] **Step 2: Update ConversationRepository**

Full replacement of `ConversationRepository.java`:
```java
package com.superchat.chat.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.superchat.chat.domain.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findFirstByNameIgnoreCaseOrderByIdAsc(String name);

    @Query("""
        SELECT c FROM Conversation c
        WHERE c.type = 'DIRECT'
          AND ((c.dmParticipantA = :a AND c.dmParticipantB = :b)
            OR (c.dmParticipantA = :b AND c.dmParticipantB = :a))
        """)
    Optional<Conversation> findDmBetween(@Param("a") String userA, @Param("b") String userB);

    @Query("""
        SELECT c FROM Conversation c
        WHERE c.type = 'GROUP'
           OR c.dmParticipantA = :userId
           OR c.dmParticipantB = :userId
        ORDER BY c.createdAt DESC
        """)
    List<Conversation> findAllForUser(@Param("userId") String userId);
}
```

- [ ] **Step 3: Write the failing DM tests**

Create `chat-service/src/test/java/com/superchat/chat/service/ConversationDmServiceTest.java`:
```java
package com.superchat.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import com.superchat.chat.domain.Conversation;
import com.superchat.chat.repo.ChatMessageRepository;
import com.superchat.chat.repo.ConversationRepository;

class ConversationDmServiceTest {

    private ChatService chatService;
    private ConversationRepository conversationRepository;
    private ChatMessageRepository messageRepository;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        messageRepository = mock(ChatMessageRepository.class);
        chatService = new ChatService(
                conversationRepository, messageRepository, mock(RabbitTemplate.class),
                "chat.exchange", "chat.routing.key",
                "notifications.exchange", "notifications.message.created"
        );
    }

    @Test
    void testCreateDm_NewDm_SavesConversation() {
        when(conversationRepository.findDmBetween("alice-id", "bob-id")).thenReturn(Optional.empty());
        Conversation saved = dmConversation(1L, "alice-id", "alice", "bob-id", "bob");
        when(conversationRepository.save(any(Conversation.class))).thenReturn(saved);

        Conversation result = chatService.createDm("alice-id", "alice", "bob-id", "bob");

        assertEquals(1L, result.getId());
        assertEquals("DIRECT", result.getType());
        verify(conversationRepository).save(any(Conversation.class));
    }

    @Test
    void testCreateDm_ExistingDm_ReturnsExisting() {
        Conversation existing = dmConversation(5L, "alice-id", "alice", "bob-id", "bob");
        when(conversationRepository.findDmBetween("alice-id", "bob-id")).thenReturn(Optional.of(existing));

        Conversation result = chatService.createDm("alice-id", "alice", "bob-id", "bob");

        assertEquals(5L, result.getId());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void testCreateDm_WithSelf_ThrowsBadRequest() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> chatService.createDm("alice-id", "alice", "alice-id", "alice"));
    }

    @Test
    void testListConversationsForUser_ReturnsGroupAndDm() {
        Conversation group = new Conversation();
        group.setId(1L); group.setName("General"); group.setType("GROUP");
        Conversation dm = dmConversation(2L, "alice-id", "alice", "bob-id", "bob");

        when(conversationRepository.findAllForUser("alice-id")).thenReturn(List.of(group, dm));

        List<Map<String, Object>> result = chatService.listConversationsForUser("alice-id");

        assertEquals(2, result.size());
        assertEquals("GROUP", result.get(0).get("type"));
        assertEquals("DIRECT", result.get(1).get("type"));
        assertEquals("bob", result.get(1).get("otherParticipantName"));
    }

    private Conversation dmConversation(Long id, String aId, String aName, String bId, String bName) {
        Conversation c = new Conversation();
        c.setId(id);
        c.setName("DM:" + aId + ":" + bId);
        c.setType("DIRECT");
        c.setDmParticipantA(aId);
        c.setDmParticipantAName(aName);
        c.setDmParticipantB(bId);
        c.setDmParticipantBName(bName);
        return c;
    }
}
```

- [ ] **Step 4: Run to verify it fails**

```bash
cd chat-service && mvn test -Dtest=ConversationDmServiceTest -q 2>&1 | tail -10
```

Expected: COMPILATION ERROR — `createDm` and `listConversationsForUser` don't exist yet.

- [ ] **Step 5: Implement createDm and listConversationsForUser in ChatService**

Add to `ChatService.java`:
```java
@Transactional
public Conversation createDm(String myId, String myName, String participantId, String participantName) {
    if (myId.equals(participantId)) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot create DM with yourself");
    }
    return conversationRepository.findDmBetween(myId, participantId)
            .orElseGet(() -> {
                Conversation dm = new Conversation();
                dm.setName("DM:" + myId + ":" + participantId);
                dm.setType("DIRECT");
                dm.setDmParticipantA(myId);
                dm.setDmParticipantAName(myName);
                dm.setDmParticipantB(participantId);
                dm.setDmParticipantBName(participantName);
                return conversationRepository.save(dm);
            });
}

@Transactional(readOnly = true)
public List<Map<String, Object>> listConversationsForUser(String userId) {
    return conversationRepository.findAllForUser(userId).stream().map(c -> {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("type", c.getType());
        m.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
        if ("DIRECT".equals(c.getType())) {
            boolean iAmA = userId.equals(c.getDmParticipantA());
            m.put("otherParticipantName", iAmA ? c.getDmParticipantBName() : c.getDmParticipantAName());
            m.put("otherParticipantId", iAmA ? c.getDmParticipantB() : c.getDmParticipantA());
        }
        return m;
    }).toList();
}
```

Add `import java.util.List; import java.util.Map;` at the top if not present.

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd chat-service && mvn test -Dtest=ConversationDmServiceTest -q 2>&1 | tail -5
```

Expected: `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 7: Run all tests**

```bash
cd chat-service && mvn test -q 2>&1 | tail -10
```

Expected: all test classes pass.

- [ ] **Step 8: Commit**

```bash
git add chat-service/src/main/java/com/superchat/chat/domain/Conversation.java \
        chat-service/src/main/java/com/superchat/chat/repo/ConversationRepository.java \
        chat-service/src/main/java/com/superchat/chat/service/ChatService.java \
        chat-service/src/test/java/com/superchat/chat/service/ConversationDmServiceTest.java
git commit -m "feat: add DM conversation creation and listing to ChatService"
```

---

## Task 12: DM Controller + DM Access Control

**Files:**
- Modify: `chat-service/src/main/java/com/superchat/chat/web/ChatController.java`

- [ ] **Step 1: Add DM access control helper to ChatService**

Add to `ChatService.java`:
```java
@Transactional(readOnly = true)
public void assertDmAccess(Long conversationId, String userId) {
    Conversation conv = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    if ("DIRECT".equals(conv.getType())) {
        boolean isParticipant = userId.equals(conv.getDmParticipantA()) || userId.equals(conv.getDmParticipantB());
        if (!isParticipant) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a participant of this conversation");
        }
    }
}
```

- [ ] **Step 2: Add new endpoints to ChatController**

Add these endpoints to `ChatController.java`:

```java
@GetMapping("/conversations")
public ResponseEntity<List<Map<String, Object>>> listConversations(Authentication authentication) {
    String userId = authentication.getName();
    return ResponseEntity.ok(chatService.listConversationsForUser(userId));
}

@PostMapping("/conversations/dm")
public ResponseEntity<Map<String, Object>> createDm(
        Authentication authentication,
        @RequestBody DmRequest request
) {
    if (request.participantId() == null || request.participantId().isBlank()) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "participantId is required");
    }
    String myId = authentication.getName();
    String myName = preferredUsername(authentication);
    Conversation dm = chatService.createDm(myId, myName, request.participantId(),
            request.participantName() != null ? request.participantName() : request.participantId());
    boolean iAmA = myId.equals(dm.getDmParticipantA());
    return ResponseEntity.ok(Map.of(
            "id", dm.getId(),
            "type", dm.getType(),
            "otherParticipantName", iAmA ? dm.getDmParticipantBName() : dm.getDmParticipantAName(),
            "otherParticipantId", iAmA ? dm.getDmParticipantB() : dm.getDmParticipantA(),
            "createdAt", dm.getCreatedAt().toString()
    ));
}
```

Add new record:
```java
public record DmRequest(String participantId, String participantName) {}
```

Update `sendMessage` endpoint to call `assertDmAccess` before sending:
```java
chatService.assertDmAccess(request.conversationId(), authentication.getName());
ChatMessage saved = chatService.sendMessage(...);
```

Update `listMessages` endpoint to call `assertDmAccess`:
```java
chatService.assertDmAccess(conversationId, authentication.getName());
String currentUserId = authentication.getName();
List<Map<String, Object>> messages = chatService.listMessages(conversationId, page, size, currentUserId);
```

Add `import java.util.List;` at the top.

- [ ] **Step 3: Compile**

```bash
cd chat-service && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add chat-service/src/main/java/com/superchat/chat/web/ChatController.java \
        chat-service/src/main/java/com/superchat/chat/service/ChatService.java
git commit -m "feat: add GET /chat/conversations and POST /chat/conversations/dm endpoints"
```

---

## Task 13: DM Frontend

**Files:**
- Modify: `frontend/index.html`
- Modify: `frontend/styles.css`
- Modify: `frontend/app.js`

- [ ] **Step 1: Add conversation sidebar + DM modal to index.html**

Replace the `<div class="chat-layout">` block with:
```html
<div class="chat-layout">
  <aside class="conv-sidebar" aria-label="Conversaciones">
    <div class="conv-sidebar-header">
      <h3>Conversaciones</h3>
      <button id="newDmBtn" type="button" class="btn-ghost btn-sm">+ DM</button>
    </div>
    <ul id="convList" class="conv-list"></ul>
  </aside>

  <section class="chat-main">
    <!-- existing chat-main content unchanged -->
    ...
  </section>

  <aside class="chat-side" aria-label="Usuarios conectados">
    <!-- existing presence panel unchanged -->
    ...
  </aside>
</div>

<!-- DM modal -->
<div id="dmModal" class="modal-overlay hidden" role="dialog" aria-modal="true">
  <div class="modal-box">
    <h3>Nuevo mensaje directo</h3>
    <input id="dmSearch" type="text" placeholder="Buscar usuario..." class="dm-search-input" />
    <ul id="dmUserList" class="dm-user-list"></ul>
    <button id="closeDmModal" type="button" class="btn-ghost">Cancelar</button>
  </div>
</div>
```

- [ ] **Step 2: Add CSS for conversation sidebar and DM modal**

Append to `styles.css`:
```css
.chat-layout {
  display: grid;
  grid-template-columns: 200px 1fr 180px;
  gap: 0.75rem;
  height: calc(100vh - 10rem);
}

.conv-sidebar {
  display: flex;
  flex-direction: column;
  background: var(--panel);
  border: 1px solid var(--edge);
  border-radius: 14px;
  overflow: hidden;
}

.conv-sidebar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem;
  border-bottom: 1px solid var(--edge);
}

.conv-sidebar-header h3 {
  margin: 0;
  font-size: 0.85rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--muted);
}

.btn-sm {
  font-size: 0.8rem;
  padding: 0.2rem 0.5rem;
}

.conv-list {
  list-style: none;
  margin: 0;
  padding: 0.4rem;
  overflow-y: auto;
  flex: 1;
}

.conv-list li {
  padding: 0.5rem 0.6rem;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.88rem;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: background 0.12s;
}

.conv-list li:hover {
  background: var(--edge);
}

.conv-list li.active {
  background: color-mix(in srgb, var(--accent) 12%, transparent);
  color: var(--accent);
  font-weight: 600;
}

.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0,0,0,0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 200;
}

.modal-box {
  background: var(--panel);
  border-radius: 16px;
  padding: 1.5rem;
  min-width: 320px;
  max-width: 90vw;
  box-shadow: 0 8px 40px rgba(0,0,0,0.18);
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.dm-search-input {
  width: 100%;
  padding: 0.5rem 0.75rem;
  border: 1px solid var(--edge);
  border-radius: 8px;
  font-size: 0.95rem;
  outline: none;
}

.dm-search-input:focus {
  border-color: var(--accent);
}

.dm-user-list {
  list-style: none;
  margin: 0;
  padding: 0;
  max-height: 240px;
  overflow-y: auto;
}

.dm-user-list li {
  padding: 0.5rem 0.75rem;
  cursor: pointer;
  border-radius: 8px;
  font-size: 0.9rem;
}

.dm-user-list li:hover {
  background: var(--edge);
}

.presence-list li {
  cursor: pointer;
  text-decoration: underline;
  text-decoration-color: transparent;
  transition: text-decoration-color 0.15s;
}

.presence-list li:hover {
  text-decoration-color: var(--accent);
  color: var(--accent);
}
```

- [ ] **Step 3: Add DM JS logic to app.js**

In the state object, add:
```js
activeConversationId: null,
conversations: [],
```

Add a `loadConversations` function:
```js
async function loadConversations() {
  try {
    const resp = await api('/chat/conversations');
    const list = await resp.json();
    state.conversations = list;
    renderConvList();
  } catch (e) {
    console.warn('Could not load conversations', e);
  }
}

function renderConvList() {
  const ul = document.getElementById('convList');
  ul.innerHTML = '';
  state.conversations.forEach(conv => {
    const li = document.createElement('li');
    const label = conv.type === 'DIRECT'
      ? `💬 ${conv.otherParticipantName || conv.id}`
      : `👥 ${conv.name}`;
    li.textContent = label;
    li.title = label;
    li.dataset.id = conv.id;
    if (conv.id === state.activeConversationId) li.classList.add('active');
    li.addEventListener('click', () => switchConversation(conv.id, label));
    ul.appendChild(li);
  });
}

async function switchConversation(id, label) {
  state.activeConversationId = id;
  document.querySelector('.chat-header h2').textContent = label || `Conversación #${id}`;
  document.getElementById('messages').innerHTML = '';
  renderConvList();
  await loadMessages();
}
```

Add `loadConversations()` call inside the post-login / post-connect flow (after WebSocket connects).

Wire up the "New DM" modal:
```js
const newDmBtn = document.getElementById('newDmBtn');
const dmModal = document.getElementById('dmModal');
const closeDmModal = document.getElementById('closeDmModal');
const dmSearch = document.getElementById('dmSearch');
const dmUserList = document.getElementById('dmUserList');

newDmBtn.addEventListener('click', () => {
  dmModal.classList.remove('hidden');
  dmSearch.value = '';
  dmUserList.innerHTML = '';
  dmSearch.focus();
});

closeDmModal.addEventListener('click', () => dmModal.classList.add('hidden'));
dmModal.addEventListener('click', (e) => { if (e.target === dmModal) dmModal.classList.add('hidden'); });

let dmSearchTimer;
dmSearch.addEventListener('input', () => {
  clearTimeout(dmSearchTimer);
  dmSearchTimer = setTimeout(async () => {
    const q = dmSearch.value.trim();
    if (!q) { dmUserList.innerHTML = ''; return; }
    const resp = await api('/users/search?q=' + encodeURIComponent(q));
    const users = await resp.json();
    dmUserList.innerHTML = '';
    users.forEach(u => {
      const li = document.createElement('li');
      li.textContent = u.displayName || u.keycloakId;
      li.addEventListener('click', () => startDm(u.keycloakId, u.displayName));
      dmUserList.appendChild(li);
    });
  }, 300);
});

async function startDm(participantId, participantName) {
  dmModal.classList.add('hidden');
  try {
    const resp = await api('/chat/conversations/dm', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ participantId, participantName })
    });
    const dm = await resp.json();
    await loadConversations();
    await switchConversation(dm.id, `💬 ${dm.otherParticipantName}`);
  } catch (e) {
    alert('Error al crear DM: ' + e.message);
  }
}
```

Make presence panel usernames clickable — in `renderPresence()` (or wherever presence list items are built), change plain `li.textContent = username` to:
```js
li.style.cursor = 'pointer';
li.addEventListener('click', () => startDm(username, username));
```

Note: presence currently stores display names, not Keycloak subs. The `startDm` call using the display name as `participantId` won't work with Keycloak sub-based DMs. To properly support clicking presence names, the DM search flow (modal) is the reliable path. Make the presence list items open the DM modal pre-filled with the username instead:
```js
li.addEventListener('click', () => {
  dmModal.classList.remove('hidden');
  dmSearch.value = username;
  dmSearch.dispatchEvent(new Event('input'));
});
```

- [ ] **Step 4: Commit**

```bash
git add frontend/index.html frontend/styles.css frontend/app.js
git commit -m "feat: add conversation sidebar, DM modal, and user search for direct messaging"
```

---

## Self-Review

**Spec coverage check:**

| Spec requirement | Task |
|---|---|
| Spellcheck on composer | Task 1 |
| Emoji picker via CDN | Task 2 |
| MinIO in docker-compose | Task 3 |
| V4 Flyway migration | Task 3 |
| MinIO SDK + config bean | Task 4 |
| AttachmentService + test | Task 5 |
| POST /chat/attachments/presign | Task 6 |
| Attachment fields on ChatMessage | Task 7 |
| Attachment rendering (image/audio/file) | Task 8 |
| viewOnce + viewed on ChatMessage | Task 9 |
| View-once server logic + tombstone | Task 9 |
| View-once frontend toggle + rendering | Task 10 |
| DM fields on Conversation | Task 11 |
| createDm idempotent + access control | Task 11, 12 |
| GET /chat/conversations | Task 12 |
| POST /chat/conversations/dm | Task 12 |
| Conversation sidebar UI | Task 13 |
| DM modal with user search | Task 13 |
| Presence panel → DM shortcut | Task 13 |

**Type consistency:** `listMessages` returns `List<Map<String,Object>>` throughout Tasks 9 and 12. `sendMessage` takes 7 args from Task 9 onward. `assertDmAccess` is added in Task 12 Step 1. All consistent.

**Placeholder scan:** No TBD/TODO in any step. All commands have expected output.
