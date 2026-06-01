package com.superchat.user.service;

import com.superchat.user.domain.*;
import com.superchat.user.repo.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RoomServiceTest {

    private final RoomRepository roomRepo = mock(RoomRepository.class);
    private final RoomMemberRepository memberRepo = mock(RoomMemberRepository.class);
    private final UserProfileRepository profileRepo = mock(UserProfileRepository.class);
    private final OrganizationRepository orgRepo = mock(OrganizationRepository.class);
    private final DepartmentRepository deptRepo = mock(DepartmentRepository.class);
    private final RoomEventPublisher events = mock(RoomEventPublisher.class);

    private final RoomService service =
            new RoomService(roomRepo, memberRepo, profileRepo, orgRepo, deptRepo, events);

    @Test
    void addMember_emitsMemberAddedEvent() {
        Long roomId = 7L;
        UUID userId = UUID.randomUUID();
        when(roomRepo.findById(roomId)).thenReturn(Optional.of(new Room()));
        when(memberRepo.existsById(any())).thenReturn(false);
        when(memberRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.addMember(roomId, userId);

        verify(events).memberAdded(roomId, userId);
    }

    @Test
    void removeMember_existing_deletesAndEmitsEvent() {
        Long roomId = 7L;
        UUID userId = UUID.randomUUID();
        when(roomRepo.findById(roomId)).thenReturn(Optional.of(new Room()));
        when(memberRepo.existsById(any())).thenReturn(true);

        service.removeMember(roomId, userId);

        verify(memberRepo).deleteById(new RoomMemberId(roomId, userId));
        verify(events).memberRemoved(roomId, userId);
    }

    @Test
    void removeMember_missing_throwsNotFound() {
        Long roomId = 7L;
        UUID userId = UUID.randomUUID();
        when(roomRepo.findById(roomId)).thenReturn(Optional.of(new Room()));
        when(memberRepo.existsById(any())).thenReturn(false);

        assertThatThrownBy(() -> service.removeMember(roomId, userId))
                .isInstanceOf(ResponseStatusException.class);
        verify(events, never()).memberRemoved(any(), any());
    }

    @Test
    void archiveRoom_setsArchivedAndEmitsEvent() {
        Long roomId = 7L;
        Room room = new Room();
        when(roomRepo.findById(roomId)).thenReturn(Optional.of(room));

        service.archiveRoom(roomId);

        assertThatRoomArchived(room);
        verify(events).roomArchived(room);
    }

    private void assertThatRoomArchived(Room room) {
        org.assertj.core.api.Assertions.assertThat(room.isArchived()).isTrue();
    }
}
