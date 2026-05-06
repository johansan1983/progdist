package com.superchat.chat.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.superchat.chat.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByConversationId(Long conversationId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("UPDATE ChatMessage m SET m.viewed = true WHERE m.id = :id AND m.viewed = false")
    int markViewed(@Param("id") Long id);
}
