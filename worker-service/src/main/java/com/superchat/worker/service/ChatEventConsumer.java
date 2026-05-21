package com.superchat.worker.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Service
public class ChatEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChatEventConsumer.class);

    // RabbitMQ STOMP plugin maps /topic/<key> to amq.topic exchange with routing key <key>.
    // Publishing here delivers to all WebSocket clients subscribed to /topic/conversations.<id>.
    private static final String STOMP_EXCHANGE = "amq.topic";

    private final RabbitTemplate rabbitTemplate;
    private final String routingKeyPrefix;

    public ChatEventConsumer(
            RabbitTemplate rabbitTemplate,
            @Value("${worker.stomp.routing-key-prefix:conversations.}") String routingKeyPrefix
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.routingKeyPrefix = routingKeyPrefix;
    }

    @RabbitListener(id = "${worker.rabbit.listener-id:chatEventConsumerListener}", queues = "${worker.rabbit.queue}")
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
            String routingKey = routingKeyPrefix + conversationId;
            log.info("[Worker] forwarding messageId={} to {}", event.get("messageId"), routingKey);
            rabbitTemplate.convertAndSend(STOMP_EXCHANGE, routingKey, event);
        } finally {
            MDC.remove("requestId");
        }
    }
}
