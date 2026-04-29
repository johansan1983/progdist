package com.superchat.chat.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisPresenceServiceTest {

    private PresenceService presenceService;
    private RedisTemplate<String, String> redisTemplateMock;
    private ValueOperations<String, String> valueOpsMock;

    @BeforeEach
    void setUp() {
        redisTemplateMock = mock(RedisTemplate.class);
        valueOpsMock = mock(ValueOperations.class);
        when(redisTemplateMock.opsForValue()).thenReturn(valueOpsMock);

        presenceService = new PresenceService(redisTemplateMock);
    }

    @Test
    void testRegisterSession_ValidInput_StoresInRedis() {
        presenceService.registerSession("session-123", "alice", null);

        verify(valueOpsMock).set("presence:session-123", "alice");
        verify(redisTemplateMock).expire("presence:session-123", Duration.ofHours(1));
    }

    @Test
    void testRegisterSession_NullSessionId_DoesNothing() {
        presenceService.registerSession(null, "alice", null);

        verify(valueOpsMock, never()).set(anyString(), anyString());
    }

    @Test
    void testRegisterSession_NullUsername_DoesNothing() {
        presenceService.registerSession("session-123", null, null);

        verify(valueOpsMock, never()).set(anyString(), anyString());
    }

    @Test
    void testRegisterSession_BlankUsername_DoesNothing() {
        presenceService.registerSession("session-123", "   ", null);

        verify(valueOpsMock, never()).set(anyString(), anyString());
    }

    @Test
    void testUnregisterSession_ValidInput_DeletesFromRedis() {
        presenceService.unregisterSession("session-123");

        verify(redisTemplateMock).delete("presence:session-123");
    }

    @Test
    void testUnregisterSession_NullSessionId_DoesNothing() {
        presenceService.unregisterSession(null);

        verify(redisTemplateMock, never()).delete(anyString());
    }

    @Test
    void testSnapshot_MultipleUsers_ReturnsDistinctSorted() {
        Set<String> keys = Set.of("presence:sess-1", "presence:sess-2", "presence:sess-3");
        when(redisTemplateMock.keys("presence:*")).thenReturn(keys);
        when(valueOpsMock.get("presence:sess-1")).thenReturn("charlie");
        when(valueOpsMock.get("presence:sess-2")).thenReturn("alice");
        when(valueOpsMock.get("presence:sess-3")).thenReturn("alice");

        PresenceService.PresenceSnapshot snapshot = presenceService.snapshot();

        assertEquals(2, snapshot.connectedCount());
        assertEquals(List.of("alice", "charlie"), snapshot.users());
    }

    @Test
    void testSnapshot_NoUsers_ReturnsEmpty() {
        when(redisTemplateMock.keys("presence:*")).thenReturn(Set.of());

        PresenceService.PresenceSnapshot snapshot = presenceService.snapshot();

        assertEquals(0, snapshot.connectedCount());
        assertEquals(List.of(), snapshot.users());
    }

    @Test
    void testSnapshot_NullKeys_ReturnsEmpty() {
        when(redisTemplateMock.keys("presence:*")).thenReturn(null);

        PresenceService.PresenceSnapshot snapshot = presenceService.snapshot();

        assertEquals(0, snapshot.connectedCount());
        assertEquals(List.of(), snapshot.users());
    }

    @Test
    void testSnapshot_BlankUsernames_Filtered() {
        Set<String> keys = Set.of("presence:sess-1", "presence:sess-2");
        when(redisTemplateMock.keys("presence:*")).thenReturn(keys);
        when(valueOpsMock.get("presence:sess-1")).thenReturn("   ");
        when(valueOpsMock.get("presence:sess-2")).thenReturn("bob");

        PresenceService.PresenceSnapshot snapshot = presenceService.snapshot();

        assertEquals(1, snapshot.connectedCount());
        assertEquals(List.of("bob"), snapshot.users());
    }
}
