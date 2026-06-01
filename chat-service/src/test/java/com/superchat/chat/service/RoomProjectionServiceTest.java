package com.superchat.chat.service;

import com.superchat.chat.domain.Conversation;
import com.superchat.chat.domain.ConversationMember;
import com.superchat.chat.repo.ConversationMemberRepository;
import com.superchat.chat.repo.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoomProjectionServiceTest {

    private final ConversationRepository convRepo = mock(ConversationRepository.class);
    private final ConversationMemberRepository memberRepo = mock(ConversationMemberRepository.class);
    private final RoomProjectionService service = new RoomProjectionService(convRepo, memberRepo);

    private Map<String, Object> roomPayload() {
        return Map.of(
                "roomId", 5,
                "name", "ingenieria",
                "visibility", "PRIVATE",
                "channelType", "TEAM",
                "orgId", "org-1",
                "createdBy", "kc-admin"
        );
    }

    @Test
    void roomCreated_whenNew_createsProjectedConversation() {
        when(convRepo.findByRoomId(5L)).thenReturn(Optional.empty());
        when(convRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.applyRoomUpsert(roomPayload());

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(convRepo).save(captor.capture());
        Conversation saved = captor.getValue();
        assertThat(saved.getRoomId()).isEqualTo(5L);
        assertThat(saved.getName()).isEqualTo("ingenieria");
        assertThat(saved.getType()).isEqualTo("GROUP");
        assertThat(saved.getVisibility()).isEqualTo("PRIVATE");
        assertThat(saved.getChannelType()).isEqualTo("TEAM");
        assertThat(saved.getOrgId()).isEqualTo("org-1");
    }

    @Test
    void roomCreated_whenExisting_updatesInPlace_noDuplicate() {
        Conversation existing = new Conversation();
        existing.setRoomId(5L);
        existing.setName("old");
        when(convRepo.findByRoomId(5L)).thenReturn(Optional.of(existing));
        when(convRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.applyRoomUpsert(roomPayload());

        verify(convRepo, times(1)).save(existing);
        assertThat(existing.getName()).isEqualTo("ingenieria");
    }

    @Test
    void memberAdded_whenNotPresent_savesMember() {
        Conversation conv = new Conversation();
        conv.setRoomId(5L);
        conv.setId(99L);
        when(convRepo.findByRoomId(5L)).thenReturn(Optional.of(conv));
        when(memberRepo.existsByIdConversationIdAndIdUserId(99L, "kc-user")).thenReturn(false);

        service.applyMemberAdded(Map.of("roomId", 5, "userId", "kc-user"));

        verify(memberRepo).save(any(ConversationMember.class));
    }

    @Test
    void memberAdded_whenAlreadyPresent_isIdempotent() {
        Conversation conv = new Conversation();
        conv.setRoomId(5L);
        conv.setId(99L);
        when(convRepo.findByRoomId(5L)).thenReturn(Optional.of(conv));
        when(memberRepo.existsByIdConversationIdAndIdUserId(99L, "kc-user")).thenReturn(true);

        service.applyMemberAdded(Map.of("roomId", 5, "userId", "kc-user"));

        verify(memberRepo, never()).save(any());
    }
}
