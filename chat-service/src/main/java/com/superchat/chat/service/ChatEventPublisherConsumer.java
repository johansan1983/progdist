package com.superchat.chat.service;

import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatEventPublisherConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    public ChatEventPublisherConsumer(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @RabbitListener(id = "${chat.rabbit.listener-id:chatEventPublisherListener}", queues = "${chat.rabbit.queue}")
    public void consumeAndPublish(Map<String, Object> event) {
        Object conversationId = event.get("conversationId");
        if (conversationId == null) {
            return;
        }

        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, event);
    }
}
