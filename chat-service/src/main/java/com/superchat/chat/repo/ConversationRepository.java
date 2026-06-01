package com.superchat.chat.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.superchat.chat.domain.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findFirstByNameIgnoreCaseOrderByIdAsc(String name);

    Optional<Conversation> findByRoomId(Long roomId);

    @Query("""
        SELECT c FROM Conversation c
        WHERE c.type = 'DIRECT'
          AND ((c.dmParticipantA = :a AND c.dmParticipantB = :b)
            OR (c.dmParticipantA = :b AND c.dmParticipantB = :a))
        """)
    Optional<Conversation> findDmBetween(@Param("a") String userA, @Param("b") String userB);

    /**
     * Conversations visible to a user. Closes the previous cross-tenant leak where every
     * GROUP was returned to everyone:
     *   - DMs the user participates in;
     *   - legacy groups with no source room (room_id IS NULL) — e.g. the migrated "General";
     *   - PUBLIC channels (org/dept scoping is applied in a later milestone once the org claim exists);
     *   - PRIVATE channels only when the user is a projected member.
     * Archived channels are excluded.
     */
    @Query("""
        SELECT c FROM Conversation c
        WHERE c.archived = false AND (
              (c.type = 'DIRECT' AND (c.dmParticipantA = :userId OR c.dmParticipantB = :userId))
           OR (c.type = 'GROUP' AND c.roomId IS NULL)
           OR (c.type = 'GROUP' AND c.roomId IS NOT NULL AND c.visibility = 'PUBLIC')
           OR (c.type = 'GROUP' AND c.roomId IS NOT NULL AND c.visibility = 'PRIVATE'
               AND EXISTS (SELECT 1 FROM ConversationMember m
                           WHERE m.id.conversationId = c.id AND m.id.userId = :userId))
        )
        ORDER BY c.createdAt DESC
        """)
    List<Conversation> findAllForUser(@Param("userId") String userId);
}
