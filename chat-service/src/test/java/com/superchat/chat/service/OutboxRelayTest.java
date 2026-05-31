package com.superchat.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superchat.chat.domain.OutboxEvent;
import com.superchat.chat.repo.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    private OutboxEventRepository repo;
    private RabbitTemplate rabbitTemplate;
    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        repo = mock(OutboxEventRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        relay = new OutboxRelay(repo, rabbitTemplate, new ObjectMapper());
    }

    @Test
    void publishes_unpublished_event_and_marks_it_published() {
        OutboxEvent e = new OutboxEvent("42", "chat.exchange", "chat.key", "{\"eventType\":\"CHAT_MESSAGE_CREATED\"}");
        when(repo.findTop100ByPublishedFalseOrderByIdAsc()).thenReturn(List.of(e));

        relay.relay();

        verify(rabbitTemplate).convertAndSend(eq("chat.exchange"), eq("chat.key"), (Object) any());
        assertTrue(e.isPublished());
        assertNotNull(e.getPublishedAt());
    }

    @Test
    void leaves_event_unpublished_when_broker_publish_fails() {
        OutboxEvent e = new OutboxEvent("7", "chat.exchange", "chat.key", "{\"eventType\":\"X\"}");
        when(repo.findTop100ByPublishedFalseOrderByIdAsc()).thenReturn(List.of(e));
        doThrow(new RuntimeException("broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), (Object) any());

        relay.relay();   // must not throw — bad row is skipped, not fatal

        assertFalse(e.isPublished());
        assertNull(e.getPublishedAt());
    }

    @Test
    void one_bad_event_does_not_block_the_others() {
        OutboxEvent bad  = new OutboxEvent("1", "x", "k", "{not valid json");
        OutboxEvent good = new OutboxEvent("2", "chat.exchange", "chat.key", "{\"eventType\":\"OK\"}");
        when(repo.findTop100ByPublishedFalseOrderByIdAsc()).thenReturn(List.of(bad, good));

        relay.relay();

        assertFalse(bad.isPublished());   // unparseable payload skipped
        assertTrue(good.isPublished());   // valid one still delivered
    }

    @Test
    void empty_batch_makes_no_broker_calls() {
        when(repo.findTop100ByPublishedFalseOrderByIdAsc()).thenReturn(List.of());
        relay.relay();
        verifyNoInteractions(rabbitTemplate);
    }
}
