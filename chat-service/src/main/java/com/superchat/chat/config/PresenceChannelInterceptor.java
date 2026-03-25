package com.superchat.chat.config;

import com.superchat.chat.service.PresenceService;

import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class PresenceChannelInterceptor implements ChannelInterceptor {

    private final PresenceService presenceService;

    public PresenceChannelInterceptor(PresenceService presenceService) {
        this.presenceService = presenceService;
    }

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String username = accessor.getFirstNativeHeader("username");
            presenceService.registerSession(accessor.getSessionId(), username);
        }

        if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            presenceService.unregisterSession(accessor.getSessionId());
        }

        return message;
    }
}
