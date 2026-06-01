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

    @Query("""
        SELECT c FROM Conversation c
        WHERE c.type = 'GROUP'
           OR c.dmParticipantA = :userId
           OR c.dmParticipantB = :userId
        ORDER BY c.createdAt DESC
        """)
    List<Conversation> findAllForUser(@Param("userId") String userId);
}
