package com.superchat.chat.service;

import com.superchat.chat.domain.Conversation;
import com.superchat.chat.repo.ConversationMemberRepository;
import com.superchat.chat.repo.ConversationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Authorization for accessing a conversation, mirroring the visibility rules of
 * ConversationRepository.findAllForUser. Used on JWT-authenticated REST paths
 * (send message, read messages).
 *
 * NOTE: the WebSocket/STOMP SUBSCRIBE path is NOT yet covered — the /ws handshake
 * currently trusts a client-supplied username (no JWT validation), so read-side
 * confidentiality over the broker relay requires authenticating the WS handshake
 * first. Tracked as a follow-up (WS-auth hardening); see team-chats design spec.
 */
@Service
public class ConversationAccessService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;

    public ConversationAccessService(ConversationRepository conversationRepository,
                                     ConversationMemberRepository memberRepository) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public boolean canAccess(Long conversationId, String userId) {
        Conversation c = conversationRepository.findById(conversationId).orElse(null);
        if (c == null || c.isArchived()) {
            return false;
        }
        if ("DIRECT".equals(c.getType())) {
            return userId.equals(c.getDmParticipantA()) || userId.equals(c.getDmParticipantB());
        }
        // GROUP
        if (c.getRoomId() == null) {
            return true; // legacy roomless group (e.g. migrated General)
        }
        if ("PRIVATE".equals(c.getVisibility())) {
            return memberRepository.existsByIdConversationIdAndIdUserId(c.getId(), userId);
        }
        return true; // PUBLIC (org/dept scoping pending Plan C)
    }

    @Transactional(readOnly = true)
    public void assertCanAccess(Long conversationId, String userId) {
        if (!conversationRepository.existsById(conversationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        if (!canAccess(conversationId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not allowed in this conversation");
        }
    }
}
