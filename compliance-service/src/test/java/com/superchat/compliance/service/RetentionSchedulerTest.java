package com.superchat.compliance.service;

import com.superchat.compliance.domain.ErasureRequest;
import com.superchat.compliance.repo.ErasureRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RetentionSchedulerTest {

    private ErasureRequestRepository erasureRepo;
    private RabbitTemplate rabbitTemplate;
    private DataExportClient exportClient;
    private RetentionScheduler scheduler;

    @BeforeEach
    void setUp() {
        erasureRepo    = mock(ErasureRequestRepository.class);
        rabbitTemplate = mock(RabbitTemplate.class);
        exportClient   = mock(DataExportClient.class);
        scheduler = new RetentionScheduler(erasureRepo, rabbitTemplate, exportClient);
        when(erasureRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void processErasure_marks_completed_and_publishes_event_on_success() {
        ErasureRequest req = pending();
        when(erasureRepo.findByStatusIn(java.util.List.of("PENDING", "FAILED"))).thenReturn(List.of(req));
        when(exportClient.eraseUserData(req.getUserId())).thenReturn(true);

        scheduler.processErasureRequests();

        assertEquals("COMPLETED", req.getStatus());
        assertNotNull(req.getCompletedAt());
        verify(exportClient).eraseUserData(req.getUserId());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), (Object) argThat(e ->
                e instanceof java.util.Map<?, ?> m && "USER_ERASURE_COMPLETED".equals(m.get("eventType"))));
    }

    @Test
    void processErasure_marks_failed_when_anonymization_fails() {
        ErasureRequest req = pending();
        when(erasureRepo.findByStatusIn(java.util.List.of("PENDING", "FAILED"))).thenReturn(List.of(req));
        when(exportClient.eraseUserData(req.getUserId())).thenReturn(false);

        scheduler.processErasureRequests();

        assertEquals("FAILED", req.getStatus());
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), (Object) argThat(e ->
                e instanceof java.util.Map<?, ?> m && "USER_ERASURE_FAILED".equals(m.get("eventType"))));
    }

    @Test
    void processErasure_does_nothing_when_no_pending_requests() {
        when(erasureRepo.findByStatusIn(java.util.List.of("PENDING", "FAILED"))).thenReturn(List.of());
        scheduler.processErasureRequests();
        verifyNoInteractions(exportClient);
    }

    private ErasureRequest pending() {
        ErasureRequest r = new ErasureRequest();
        r.setUserId(UUID.randomUUID().toString());
        r.setStatus("PENDING");
        return r;
    }
}
