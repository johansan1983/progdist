package com.superchat.chat.web;

import java.util.Map;

import com.superchat.chat.service.ChatEventListenerControlService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat/simulation")
public class SimulationController {

    private final ChatEventListenerControlService controlService;

    public SimulationController(ChatEventListenerControlService controlService) {
        this.controlService = controlService;
    }

    @PostMapping("/realtime-publisher/fail")
    public ResponseEntity<Map<String, Object>> failRealtimePublisher(Authentication authentication) {
        boolean running = controlService.stopListener();
        return ResponseEntity.ok(Map.of(
                "component", "realtime-publisher",
                "simulatedFailure", true,
                "running", running,
                "triggeredBy", authentication.getName(),
                "message", "Consumer stopped. RabbitMQ will retain queued events."
        ));
    }

    @PostMapping("/realtime-publisher/restore")
    public ResponseEntity<Map<String, Object>> restoreRealtimePublisher(Authentication authentication) {
        boolean running = controlService.startListener();
        return ResponseEntity.ok(Map.of(
                "component", "realtime-publisher",
                "simulatedFailure", false,
                "running", running,
                "triggeredBy", authentication.getName(),
                "message", "Consumer restored. Queued events will be consumed now."
        ));
    }

    @GetMapping("/realtime-publisher/status")
    public ResponseEntity<Map<String, Object>> realtimePublisherStatus(Authentication authentication) {
        boolean running = controlService.isRunning();
        return ResponseEntity.ok(Map.of(
                "component", "realtime-publisher",
                "running", running,
                "simulatedFailure", !running
        ));
    }
}
