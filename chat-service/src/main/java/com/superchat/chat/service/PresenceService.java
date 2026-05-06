package com.superchat.chat.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class PresenceService {

    private static final String PRESENCE_KEY_PREFIX = "presence:";
    private final RedisTemplate<String, String> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public PresenceService(RedisTemplate<String, String> redisTemplate,
                           @Lazy SimpMessagingTemplate messagingTemplate) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    public void registerSession(String sessionId, String username, String displayName) {
        if (sessionId == null || username == null || username.isBlank()) {
            return;
        }
        String value = (displayName != null && !displayName.isBlank()) ? displayName.trim() : username.trim();
        String key = getPresenceKey(sessionId);
        redisTemplate.opsForValue().set(key, value);
        redisTemplate.expire(key, java.time.Duration.ofHours(1));
        pushSnapshot();
    }

    public void unregisterSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        redisTemplate.delete(getPresenceKey(sessionId));
        pushSnapshot();
    }

    public PresenceSnapshot snapshot() {
        Set<String> keys = redisTemplate.keys(PRESENCE_KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return new PresenceSnapshot(0, List.of());
        }

        List<String> users = keys.stream()
                .map(key -> redisTemplate.opsForValue().get(key))
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        return new PresenceSnapshot(users.size(), users);
    }

    private void pushSnapshot() {
        PresenceSnapshot snap = snapshot();
        messagingTemplate.convertAndSend("/topic/presence",
                Map.of("connectedCount", snap.connectedCount(), "users", snap.users()));
    }

    private String getPresenceKey(String sessionId) {
        return PRESENCE_KEY_PREFIX + sessionId;
    }

    public record PresenceSnapshot(int connectedCount, List<String> users) {
    }
}
