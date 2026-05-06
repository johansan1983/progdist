package com.superchat.chat.web;

import java.util.Map;

import com.superchat.chat.service.PresenceService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class PresenceController {

    private final PresenceService presenceService;

    public PresenceController(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @GetMapping("/presence")
    public Map<String, Object> getPresence(Authentication authentication) {
        PresenceService.PresenceSnapshot snapshot = presenceService.snapshot();
        return Map.of(
                "connectedCount", snapshot.connectedCount(),
                "users", snapshot.users()
        );
    }
}
