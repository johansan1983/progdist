package com.superchat.chat.service;

import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PresenceService {

    private static final String PRESENCE_KEY_PREFIX = "presence:";
    private final RedisTemplate<String, String> redisTemplate;

    public PresenceService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void registerSession(String sessionId, String username, String alias) {
        if (sessionId == null || username == null || username.isBlank()) {
            return;
        }
        String trimmedUsername = username.trim();
        String value;
        if (alias != null && !alias.isBlank()) {
            // store as username||alias to preserve backward compatibility
            value = trimmedUsername + "||" + alias.trim();
        } else {
            value = trimmedUsername;
        }
        String key = getPresenceKey(sessionId);
        redisTemplate.opsForValue().set(key, value);
        redisTemplate.expire(key, java.time.Duration.ofHours(1));
    }

    public void unregisterSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        redisTemplate.delete(getPresenceKey(sessionId));
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
                .map(raw -> {
                    // support stored formats: "username" or "username||alias"
                    if (raw.contains("||")) {
                        String[] parts = raw.split("\\|\\|", 2);
                        String username = parts[0].trim();
                        String alias = parts.length > 1 ? parts[1].trim() : "";
                        if (!alias.isBlank()) {
                            return alias + " (" + username + ")";
                        }
                        return username;
                    }
                    return raw;
                })
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        return new PresenceSnapshot(users.size(), users);
    }

    private String getPresenceKey(String sessionId) {
        return PRESENCE_KEY_PREFIX + sessionId;
    }

    public record PresenceSnapshot(int connectedCount, List<String> users) {
    }
}
