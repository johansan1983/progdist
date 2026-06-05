package com.superchat.chat.service;

import com.superchat.chat.domain.Conversation;
import com.superchat.chat.domain.ConversationMember;
import com.superchat.chat.domain.ConversationMemberId;
import com.superchat.chat.repo.ConversationMemberRepository;
import com.superchat.chat.repo.ConversationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Maintains the chat-service read model (Conversation + ConversationMember) from
 * rooms.exchange events emitted by user-service. All operations are idempotent so that
 * RabbitMQ's at-least-once redelivery never creates duplicates.
 */
@Service
public class RoomProjectionService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;

    public RoomProjectionService(ConversationRepository conversationRepository,
                                 ConversationMemberRepository memberRepository) {
        this.conversationRepository = conversationRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public void applyRoomUpsert(Map<String, Object> payload) {
        Long roomId = asLong(payload.get("roomId"));
        if (roomId == null) return;
        Conversation conv = conversationRepository.findByRoomId(roomId).orElseGet(Conversation::new);
        conv.setRoomId(roomId);
        conv.setType("GROUP");
        conv.setName(asString(payload.get("name")));
        conv.setVisibility(asString(payload.get("visibility")));
        conv.setChannelType(payload.get("channelType") != null ? asString(payload.get("channelType")) : "GENERAL");
        conv.setOrgId(asString(payload.get("orgId")));
        conv.setDeptId(asString(payload.get("deptId")));
        conv.setCreatedBy(asString(payload.get("createdBy")));
        if (Boolean.TRUE.equals(payload.get("archived"))) {
            conv.setArchived(true);
        }
        conversationRepository.save(conv);
    }

    @Transactional
    public void applyRoomArchived(Map<String, Object> payload) {
        Long roomId = asLong(payload.get("roomId"));
        if (roomId == null) return;
        conversationRepository.findByRoomId(roomId).ifPresent(conv -> {
            conv.setArchived(true);
            conversationRepository.save(conv);
        });
    }

    @Transactional
    public void applyMemberAdded(Map<String, Object> payload) {
        Long roomId = asLong(payload.get("roomId"));
        String userId = asString(payload.get("userId"));
        if (roomId == null || userId == null) return;
        conversationRepository.findByRoomId(roomId).ifPresent(conv -> {
            if (!memberRepository.existsByIdConversationIdAndIdUserId(conv.getId(), userId)) {
                memberRepository.save(new ConversationMember(new ConversationMemberId(conv.getId(), userId)));
            }
        });
    }

    @Transactional
    public void applyMemberRemoved(Map<String, Object> payload) {
        Long roomId = asLong(payload.get("roomId"));
        String userId = asString(payload.get("userId"));
        if (roomId == null || userId == null) return;
        conversationRepository.findByRoomId(roomId).ifPresent(conv -> {
            ConversationMemberId id = new ConversationMemberId(conv.getId(), userId);
            if (memberRepository.existsById(id)) {
                memberRepository.deleteById(id);
            }
        });
    }

    private static Long asLong(Object o) {
        return (o instanceof Number n) ? n.longValue() : null;
    }

    private static String asString(Object o) {
        return o != null ? o.toString() : null;
    }
}
