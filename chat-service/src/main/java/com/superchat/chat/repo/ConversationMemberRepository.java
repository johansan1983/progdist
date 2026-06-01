package com.superchat.chat.repo;

import com.superchat.chat.domain.ConversationMember;
import com.superchat.chat.domain.ConversationMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationMemberRepository extends JpaRepository<ConversationMember, ConversationMemberId> {
    boolean existsByIdConversationIdAndIdUserId(Long conversationId, String userId);
}
