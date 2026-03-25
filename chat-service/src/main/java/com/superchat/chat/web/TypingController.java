package com.superchat.chat.web;

import java.time.Instant;
import java.util.Map;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
public class TypingController {

    private final SimpMessagingTemplate messagingTemplate;

    public TypingController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/typing")
    public void handleTyping(@Payload TypingEvent event) {
        if (event == null || event.conversationId() == null || event.username() == null || event.username().isBlank()) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "eventType", "TYPING",
                "conversationId", event.conversationId(),
                "username", event.username().trim(),
                "typing", event.typing(),
                "createdAt", Instant.now().toString()
        );

        messagingTemplate.convertAndSend("/topic/conversations/" + event.conversationId() + "/typing", payload);
    }

    public record TypingEvent(Long conversationId, String username, boolean typing) {
    }
}
