package com.superchat.chat.web;

import java.util.Map;

import com.superchat.chat.security.AuthClient;
import com.superchat.chat.service.PresenceService;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
@CrossOrigin(origins = "*")
public class PresenceController {

    private final AuthClient authClient;
    private final PresenceService presenceService;

    public PresenceController(AuthClient authClient, PresenceService presenceService) {
        this.authClient = authClient;
        this.presenceService = presenceService;
    }

    @GetMapping("/presence")
    public Map<String, Object> getPresence(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        authClient.validateAndGetUsername(authorization);
        PresenceService.PresenceSnapshot snapshot = presenceService.snapshot();

        return Map.of(
                "connectedCount", snapshot.connectedCount(),
                "users", snapshot.users()
        );
    }
}
