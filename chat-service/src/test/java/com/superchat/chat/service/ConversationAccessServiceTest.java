package com.superchat.chat.service;

import com.superchat.chat.domain.Conversation;
import com.superchat.chat.repo.ConversationMemberRepository;
import com.superchat.chat.repo.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ConversationAccessServiceTest {

    private final ConversationRepository convRepo = mock(ConversationRepository.class);
    private final ConversationMemberRepository memberRepo = mock(ConversationMemberRepository.class);
    private final ConversationAccessService access = new ConversationAccessService(convRepo, memberRepo);

    private Conversation conv(Long id) {
        Conversation c = new Conversation();
        c.setId(id);
        return c;
    }

    @Test
    void dmParticipant_canAccess_nonParticipant_cannot() {
        Conversation dm = conv(1L);
        dm.setType("DIRECT");
        dm.setDmParticipantA("alice");
        dm.setDmParticipantB("bob");
        when(convRepo.findById(1L)).thenReturn(Optional.of(dm));

        assertThat(access.canAccess(1L, "alice")).isTrue();
        assertThat(access.canAccess(1L, "carol")).isFalse();
    }

    @Test
    void legacyGroupAndPublicChannel_areOpen() {
        Conversation legacy = conv(2L);
        legacy.setType("GROUP"); // roomId null
        when(convRepo.findById(2L)).thenReturn(Optional.of(legacy));
        assertThat(access.canAccess(2L, "anyone")).isTrue();

        Conversation pub = conv(3L);
        pub.setType("GROUP");
        pub.setRoomId(50L);
        pub.setVisibility("PUBLIC");
        when(convRepo.findById(3L)).thenReturn(Optional.of(pub));
        assertThat(access.canAccess(3L, "anyone")).isTrue();
    }

    @Test
    void privateChannel_onlyMembers() {
        Conversation priv = conv(4L);
        priv.setType("GROUP");
        priv.setRoomId(60L);
        priv.setVisibility("PRIVATE");
        when(convRepo.findById(4L)).thenReturn(Optional.of(priv));
        when(memberRepo.existsByIdConversationIdAndIdUserId(4L, "member")).thenReturn(true);
        when(memberRepo.existsByIdConversationIdAndIdUserId(4L, "outsider")).thenReturn(false);

        assertThat(access.canAccess(4L, "member")).isTrue();
        assertThat(access.canAccess(4L, "outsider")).isFalse();
    }

    @Test
    void archivedChannel_isClosed() {
        Conversation arch = conv(5L);
        arch.setType("GROUP");
        arch.setRoomId(70L);
        arch.setVisibility("PUBLIC");
        arch.setArchived(true);
        when(convRepo.findById(5L)).thenReturn(Optional.of(arch));
        assertThat(access.canAccess(5L, "anyone")).isFalse();
    }

    @Test
    void assertCanAccess_missing_throws404_denied_throws403() {
        when(convRepo.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> access.assertCanAccess(9L, "x"))
                .isInstanceOf(ResponseStatusException.class);

        Conversation priv = conv(10L);
        priv.setType("GROUP");
        priv.setRoomId(80L);
        priv.setVisibility("PRIVATE");
        when(convRepo.findById(10L)).thenReturn(Optional.of(priv));
        when(memberRepo.existsByIdConversationIdAndIdUserId(10L, "outsider")).thenReturn(false);
        assertThatThrownBy(() -> access.assertCanAccess(10L, "outsider"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
