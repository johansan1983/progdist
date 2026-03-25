package com.superchat.chat.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.superchat.chat.domain.ChatMessage;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
