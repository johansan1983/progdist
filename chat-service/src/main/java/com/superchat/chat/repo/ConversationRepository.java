package com.superchat.chat.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.superchat.chat.domain.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {
	Optional<Conversation> findFirstByNameIgnoreCaseOrderByIdAsc(String name);
}
