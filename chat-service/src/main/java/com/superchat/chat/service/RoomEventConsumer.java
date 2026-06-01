package com.superchat.chat.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes room/membership events from rooms.exchange (published by user-service via its
 * transactional outbox) and applies them to the local projection. Handlers are idempotent,
 * matching RabbitMQ's at-least-once delivery.
 */
@Component
public class RoomEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RoomEventConsumer.class);

    private final RoomProjectionService projection;

    public RoomEventConsumer(RoomProjectionService projection) {
        this.projection = projection;
    }

    @RabbitListener(queues = "${rooms.projection.queue:chat.rooms.projection.queue}")
    public void onRoomEvent(Map<String, Object> payload,
                            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey) {
        switch (routingKey) {
            case "room.created", "room.updated" -> projection.applyRoomUpsert(payload);
            case "room.archived" -> projection.applyRoomArchived(payload);
            case "room.member.added" -> projection.applyMemberAdded(payload);
            case "room.member.removed" -> projection.applyMemberRemoved(payload);
            default -> log.warn("[RoomProjection] ignoring unknown routing key: {}", routingKey);
        }
    }
}
