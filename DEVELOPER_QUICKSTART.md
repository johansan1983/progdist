# SuperChat Developer Quick Start Guide

## 🚀 Quick Start (5 minutes)

### Prerequisites
- Docker & Docker Compose
- Git
- Optional: Maven (for local build), PostgreSQL client (for DB inspection)

### Get Running
```bash
cd progdist
docker-compose up -d

# Wait for services to be healthy (~30 seconds)
docker-compose ps

# Open browser
# Frontend: http://localhost:3000
# Chat API: http://localhost:8082
# Auth API: http://localhost:8081
```

### First Login
```bash
# Default credentials (from database init):
Username: demo
Password: demo

# Or create new user via auth-service
```

---

## 📋 Key Architecture Decisions

### Authentication: JWT + Local Validation
**Why**: Reduce auth latency from 50-100ms (remote call) to <1ms (local parse)

```java
// AuthClient.java: Try local JWT parse first
try {
  Claims claims = jwtParser.parseSignedClaims(token).getPayload();
  return claims.get("username", String.class);
} catch {
  // Fallback to remote auth-service
  return remoteValidate(token);
}
```

**Config**:
```bash
export JWT_SECRET="YourSecretAtLeast32CharsLong!!!" # Shared between auth-service and chat-service
```

---

### Message Storage: PostgreSQL + Pagination
**Why**: Efficient pagination without loading entire conversation into memory

```sql
-- Query: Get messages for conversation 1, page 0 (50 items)
SELECT * FROM chat_message 
WHERE conversation_id = 1 
ORDER BY created_at DESC 
LIMIT 50 OFFSET 0;

-- Uses index: idx_chat_message_conversation_created (conversation_id, created_at DESC)
-- Latency: <10ms even with 1M messages
```

**API Response**:
```json
{
  "messages": [ { "id": 1, "sender": "alice", "content": "Hi", "createdAt": "..." }, ... ],
  "page": 0,
  "size": 50,
  "totalPages": 5,
  "totalElements": 250
}
```

---

### Presence: Redis + STOMP Push
**Why**: Real-time presence without polling, scalable to multiple instances

**Flow**:
```
1. User connects → PresenceChannelInterceptor stores in Redis
   Redis: SET presence:{sessionId} "{username}" EX 3600

2. PresenceChannelInterceptor publishes snapshot to STOMP
   STOMP: /topic/presence ← { users: ["alice", "bob"], count: 2 }

3. All connected clients receive update via STOMP subscription
   Frontend: stompClient.subscribe("/topic/presence", callback)
```

**Benefits**:
- Polling eliminated (100%)
- Presence updates in <300ms
- Redis as single source of truth across all instances

---

### Real-Time Messaging: WebSocket + STOMP Relay
**Why**: Multi-instance message coordination via RabbitMQ

**Single Instance (Phase 3)**:
```
Frontend → SockJS/WebSocket → SimpleBroker (in-memory)
                              ↓
                         All clients on this instance
```

**Multi-Instance (Phase 4)**:
```
Frontend A → Instance 1 → ┐
                         ├→ RabbitMQ STOMP (/topic/conversations/{id})
Frontend B → Instance 2 → ┘
                         ↓
                    All instances receive & relay to their clients
```

**Config**:
```yaml
# WebSocketConfig.java
registry.enableStompBrokerRelay("/topic", "/queue")
  .setRelayHost("rabbitmq")
  .setRelayPort(61613);  # STOMP plugin port
```

---

### UI: Optimistic Rendering + Infinite Scroll
**Why**: Instant user feedback + progressive loading for large histories

**Optimistic Flow**:
```javascript
// User sends message
messageForm.submit() {
  // 1. Show immediately (optimistic)
  appendMessage(message, isOptimistic=true);  // Gray, italic "enviando..."
  
  // 2. Send to server
  await api("/chat/messages", { body: JSON.stringify(message) });
  
  // 3. Confirm or show error
  if (success) {
    msgElement.classList.remove("msg-optimistic");  // Remove styling
  } else {
    msgElement.classList.add("msg-error");  // Red border, error icon
  }
}
```

**Infinite Scroll**:
```javascript
// User clicks "Load more"
loadMoreMessages() {
  const data = await api(`/messages?page=${currentPage}&size=50`);
  appendMessages(data.messages);
  currentPage++;
  
  // Hide button if no more pages
  if (currentPage >= totalPages) {
    loadMoreBtn.classList.add("hidden");
  }
}
```

---

### Database: Flyway Migrations + Strategic Indices
**Why**: Schema as code + query optimization without application restarts

**Migration Files**:
```bash
src/main/resources/db/migration/
├── V1__Initial_Schema.sql
│   ├── Creates tables (conversation, chat_message)
│   └── Creates base indices
└── V2__Add_Performance_Indices.sql
    └── Adds performance indices for high-volume scenarios
```

**Automatic Execution**:
```
Application startup:
1. Check if flyway_schema_history table exists
2. Run each V{N}__{description}.sql in order
3. Record version + checksum in flyway_schema_history
4. Skip if already run (idempotent)
```

---

## 🔧 Configuration Reference

### Environment Variables
```bash
# PostgreSQL
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/superchat
SPRING_DATASOURCE_USERNAME=superchat
SPRING_DATASOURCE_PASSWORD=superchat123

# Redis
SPRING_REDIS_HOST=localhost
SPRING_REDIS_PORT=6379

# RabbitMQ
SPRING_RABBITMQ_HOST=localhost
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=superchat
SPRING_RABBITMQ_PASSWORD=superchat123

# JWT
JWT_SECRET=SuperChatJwtSecretKey_MustBeLong_AtLeast32Bytes_2026
JWT_EXPIRATION_MS=3600000  # 1 hour

# Connection Pool (optional)
SPRING_DATASOURCE_HIKARI_MAX_POOL_SIZE=20
SPRING_DATASOURCE_HIKARI_MIN_IDLE=5
```

### Application Configuration
```yaml
# application.yml

spring:
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema
      
  flyway:
    enabled: true
    locations: classpath:db/migration
    validate-on-migrate: true  # Prevent silent failures
    
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
```

---

## 📊 Performance Tuning Checklist

- [ ] **JWT_SECRET set identically** in auth-service and chat-service
- [ ] **STOMP plugin enabled** on RabbitMQ (rabbitmq-enabled-plugins file)
- [ ] **STOMP relay configured** in WebSocketConfig.java (not SimpleBroker)
- [ ] **Database indices created** via Flyway migrations (V1 + V2)
- [ ] **Connection pool sized appropriately** (20-30 per instance recommended)
- [ ] **Redis TTL set to 3600** seconds (presence auto-cleanup)
- [ ] **RabbitMQ memory limit** set (2GB recommended for 5k messages/sec)

---

## 🐛 Common Issues & Fixes

### WebSocket Connection Fails
```
Error: Failed to connect /ws
Reason: Auth token invalid
Fix: Re-login to get fresh token
```

### Messages Not Crossing Instances
```
Error: Message sent on instance 8082, not visible on 8083
Reason: STOMP relay not enabled (still using SimpleBroker)
Fix: Verify WebSocketConfig.java has enableStompBrokerRelay()
     Restart chat-service
```

### Presence Updates Every 5+ Seconds
```
Error: Presence updating slowly
Reason: Still using polling instead of STOMP push
Fix: Verify app.js subscribes to /topic/presence
     Check RabbitMQ STOMP plugin active: docker exec rabbitmq rabbitmq-plugins list
```

### Database Query Timeout
```
Error: "HikariPool-1 - Connection is not available"
Reason: Connection pool exhausted
Fix: Increase SPRING_DATASOURCE_HIKARI_MAX_POOL_SIZE=30
     Or identify slow queries: docker logs chat-service | grep slow
```

### Pagination Returns Wrong Results
```
Error: page parameter ignored, gets full history
Reason: Endpoint using old ChatMessageRepository method
Fix: Verify ChatController calls: chatService.listMessages(conversationId, page, size)
     Check ChatService.listMessages() creates PageRequest correctly
```

---

## 📚 Deep Dives

For detailed implementation information, see:

- **[IMPLEMENTATION_SUMMARY.md](IMPLEMENTATION_SUMMARY.md)** - Complete 5-phase overview
- **[PHASE1_TESTING_GUIDE.md](PHASE1_TESTING_GUIDE.md)** - JWT & pagination testing
- **[PHASE2_TESTING_GUIDE.md](PHASE2_TESTING_GUIDE.md)** - Redis presence testing
- **[PHASE4_TESTING_GUIDE.md](PHASE4_TESTING_GUIDE.md)** - Multi-instance STOMP testing
- **[PHASE5_OPTIMIZATION_GUIDE.md](PHASE5_OPTIMIZATION_GUIDE.md)** - Database optimization testing

---

## 🚢 Deployment Checklist

### Before Going to Production
- [ ] All 5 phases tested and validated
- [ ] Load tested with expected user count
- [ ] Database indices verified with `EXPLAIN ANALYZE`
- [ ] RabbitMQ clustering configured (if multi-region)
- [ ] Redis persistence enabled
- [ ] PostgreSQL backups configured
- [ ] Environment variables set for production credentials
- [ ] SSL/TLS certificates configured
- [ ] Rate limiting implemented (Phase 6)
- [ ] Monitoring/alerting configured

### Scaling to Multiple Instances
```bash
# Create docker-compose.override.yml:
services:
  chat-service-2:
    build: ./chat-service
    ports:
      - "8083:8082"
    environment:
      # Same as chat-service but different port
  
  chat-service-3:
    # Similar...
```

Then: `docker-compose up -d`

All instances will:
- Share PostgreSQL (persistence)
- Share Redis (presence, cache)
- Share RabbitMQ (message relay via STOMP)
- Coordinate via STOMP broker relay

---

## 🎓 Learning Resources

### Core Concepts
- **Spring WebSocket**: https://spring.io/guides/gs/messaging-stomp-websocket/
- **STOMP Protocol**: https://stomp.github.io/
- **JWT Best Practices**: https://jwt.io/introduction
- **Redis Pub/Sub**: https://redis.io/docs/interact/pubsub/
- **PostgreSQL Indices**: https://www.postgresql.org/docs/current/indexes.html

### Tools & Debugging
- **RabbitMQ Admin**: http://localhost:15672 (user: superchat / pass: superchat123)
- **PostgreSQL CLI**: `docker exec -it postgres psql -U superchat -d superchat`
- **Redis CLI**: `docker exec -it redis redis-cli`
- **Check Logs**: `docker logs -f chat-service`

---

## 🤝 Contributing

When adding features:
1. Follow existing patterns (e.g., service → repository → controller)
2. Add unit tests (AuthClientTest, ChatServiceTest pattern)
3. Test multi-instance behavior (if affecting messaging/presence)
4. Update relevant testing guide (PHASE{N}_TESTING_GUIDE.md)
5. Verify performance metrics maintained

---

**Ready to deploy? Let's go! 🚀**
