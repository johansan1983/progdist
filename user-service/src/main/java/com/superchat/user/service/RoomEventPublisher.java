package com.superchat.user.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superchat.user.config.RabbitConfig;
import com.superchat.user.domain.OutboxEvent;
import com.superchat.user.domain.Room;
import com.superchat.user.repo.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists room/membership domain events to the outbox in the SAME transaction as the
 * domain change (called from within RoomService's @Transactional methods). The OutboxRelay
 * publishes them to RabbitMQ later — this never touches the broker directly.
 */
@Service
public class RoomEventPublisher {

    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public RoomEventPublisher(OutboxEventRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void roomCreated(Room room)  { emit("room.created", roomPayload(room), room.getId()); }
    public void roomUpdated(Room room)  { emit("room.updated", roomPayload(room), room.getId()); }
    public void roomArchived(Room room) { emit("room.archived", roomPayload(room), room.getId()); }

    /**
     * @param memberKeycloakId the member's Keycloak subject (NOT the UserProfile UUID) — chat-service
     *                         authorizes by the JWT 'sub', so membership must be projected under that id.
     */
    public void memberAdded(Long roomId, String memberKeycloakId) {
        emit("room.member.added", memberPayload(roomId, memberKeycloakId), roomId);
    }

    public void memberRemoved(Long roomId, String memberKeycloakId) {
        emit("room.member.removed", memberPayload(roomId, memberKeycloakId), roomId);
    }

    private Map<String, Object> roomPayload(Room room) {
        var m = new LinkedHashMap<String, Object>();
        m.put("roomId", room.getId());
        m.put("name", room.getName());
        m.put("description", room.getDescription());
        m.put("visibility", room.getType().name());
        m.put("channelType", room.getChannelType().name());
        m.put("orgId", room.getOrganization() != null ? room.getOrganization().getId().toString() : null);
        m.put("deptId", room.getDepartment() != null ? room.getDepartment().getId().toString() : null);
        m.put("createdBy", room.getCreatedBy());
        m.put("archived", room.isArchived());
        return m;
    }

    private Map<String, Object> memberPayload(Long roomId, String memberKeycloakId) {
        var m = new LinkedHashMap<String, Object>();
        m.put("roomId", roomId);
        m.put("userId", memberKeycloakId);
        return m;
    }

    private void emit(String routingKey, Map<String, Object> payload, Long aggregateId) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            outboxRepository.save(new OutboxEvent(
                    aggregateId != null ? aggregateId.toString() : null,
                    RabbitConfig.ROOMS_EXCHANGE, routingKey, json));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize room event payload", e);
        }
    }
}
