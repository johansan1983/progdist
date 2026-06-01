package com.superchat.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superchat.user.domain.OutboxEvent;
import com.superchat.user.repo.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RoomEventPublisherTest {

    private final OutboxEventRepository repo = mock(OutboxEventRepository.class);
    private final RoomEventPublisher publisher = new RoomEventPublisher(repo, new ObjectMapper());

    @Test
    void memberAdded_persistsOutboxRowWithRoutingKeyAndPayload() {
        UUID userId = UUID.randomUUID();

        publisher.memberAdded(42L, userId);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repo).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getExchange()).isEqualTo("rooms.exchange");
        assertThat(saved.getRoutingKey()).isEqualTo("room.member.added");
        assertThat(saved.getAggregateId()).isEqualTo("42");
        assertThat(saved.getPayload()).contains(userId.toString()).contains("\"roomId\":42");
    }
}
