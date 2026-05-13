package com.superchat.chat.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatEventPublisherConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChatEventPublisherConsumer.class);

    private final SimpMessagingTemplate messagingTemplate;

    public ChatEventPublisherConsumer(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @RabbitListener(id = "${chat.rabbit.listener-id:chatEventPublisherListener}", queues = "${chat.rabbit.queue}")
    public void consumeAndPublish(Map<String, Object> event,
                                  @Header(value = "X-Request-ID", required = false) String requestId) {
        if (requestId != null) {
            MDC.put("requestId", requestId);
        }
        try {
            Object conversationId = event.get("conversationId");
            if (conversationId == null) {
                return;
            }
            log.info("[Worker] forwarding messageId={} to /topic/conversations.{}", event.get("messageId"), conversationId);
            // RabbitMQ STOMP rejects '/' in routing keys; use '.' as separator
            messagingTemplate.convertAndSend("/topic/conversations." + conversationId, event);
        } finally {
            MDC.remove("requestId");
        }
    }
}
