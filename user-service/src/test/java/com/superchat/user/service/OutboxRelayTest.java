package com.superchat.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superchat.user.domain.OutboxEvent;
import com.superchat.user.repo.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    private final OutboxEventRepository repo = mock(OutboxEventRepository.class);
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final OutboxRelay relay = new OutboxRelay(repo, rabbit, new ObjectMapper());

    @Test
    void relay_publishesUnsentEventsAndMarksThemPublished() {
        OutboxEvent e = new OutboxEvent("7", "rooms.exchange", "room.member.added", "{\"roomId\":7}");
        when(repo.findTop100ByPublishedFalseOrderByIdAsc()).thenReturn(List.of(e));

        relay.relay();

        verify(rabbit).convertAndSend(eq("rooms.exchange"), eq("room.member.added"), any(Object.class));
        assertThat(e.isPublished()).isTrue();
    }

    @Test
    void relay_publishFailure_leavesEventUnpublished() {
        OutboxEvent e = new OutboxEvent("7", "rooms.exchange", "room.created", "{\"roomId\":7}");
        when(repo.findTop100ByPublishedFalseOrderByIdAsc()).thenReturn(List.of(e));
        doThrow(new RuntimeException("broker down"))
                .when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class));

        relay.relay();

        assertThat(e.isPublished()).isFalse();
    }
}
