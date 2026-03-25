package com.superchat.chat.web;

import java.util.Map;

import com.superchat.chat.security.AuthClient;
import com.superchat.chat.service.ChatEventListenerControlService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat/simulation")
@CrossOrigin(origins = "*")
public class SimulationController {

    private final AuthClient authClient;
    private final ChatEventListenerControlService controlService;

    public SimulationController(AuthClient authClient, ChatEventListenerControlService controlService) {
        this.authClient = authClient;
        this.controlService = controlService;
    }

    @PostMapping("/realtime-publisher/fail")
    public ResponseEntity<Map<String, Object>> failRealtimePublisher(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String username = authClient.validateAndGetUsername(authorization);
        boolean running = controlService.stopListener();

        return ResponseEntity.ok(Map.of(
                "component", "realtime-publisher",
                "simulatedFailure", true,
                "running", running,
                "triggeredBy", username,
                "message", "Consumer stopped. RabbitMQ will retain queued events."
        ));
    }

    @PostMapping("/realtime-publisher/restore")
    public ResponseEntity<Map<String, Object>> restoreRealtimePublisher(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        String username = authClient.validateAndGetUsername(authorization);
        boolean running = controlService.startListener();

        return ResponseEntity.ok(Map.of(
                "component", "realtime-publisher",
                "simulatedFailure", false,
                "running", running,
                "triggeredBy", username,
                "message", "Consumer restored. Queued events will be consumed now."
        ));
    }

    @GetMapping("/realtime-publisher/status")
    public ResponseEntity<Map<String, Object>> realtimePublisherStatus(
            @RequestHeader(value = "Authorization", required = false) String authorization
    ) {
        authClient.validateAndGetUsername(authorization);
        boolean running = controlService.isRunning();

        return ResponseEntity.ok(Map.of(
                "component", "realtime-publisher",
                "running", running,
                "simulatedFailure", !running
        ));
    }
}
