package com.superchat.chat.service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class PresenceService {

    private final Map<String, String> sessionUsers = new ConcurrentHashMap<>();

    public PresenceService() {
    }

    public void registerSession(String sessionId, String username) {
        if (sessionId == null || username == null || username.isBlank()) {
            return;
        }
        sessionUsers.put(sessionId, username.trim());
    }

    public void unregisterSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        sessionUsers.remove(sessionId);
    }

    public PresenceSnapshot snapshot() {
        List<String> users = sessionUsers.values().stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        return new PresenceSnapshot(users.size(), users);
    }

    public record PresenceSnapshot(int connectedCount, List<String> users) {
    }
}
