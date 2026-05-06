package com.superchat.chat.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final PresenceChannelInterceptor presenceChannelInterceptor;
    private final String stompHost;
    private final int stompPort;
    private final String stompUsername;
    private final String stompPassword;

    public WebSocketConfig(
            PresenceChannelInterceptor presenceChannelInterceptor,
            @Value("${chat.rabbit.stomp.host:localhost}") String stompHost,
            @Value("${chat.rabbit.stomp.port:61613}") int stompPort,
            @Value("${chat.rabbit.stomp.username:guest}") String stompUsername,
            @Value("${chat.rabbit.stomp.password:guest}") String stompPassword
    ) {
        this.presenceChannelInterceptor = presenceChannelInterceptor;
        this.stompHost = stompHost;
        this.stompPort = stompPort;
        this.stompUsername = stompUsername;
        this.stompPassword = stompPassword;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // RabbitMQ STOMP relay enables horizontal WebSocket scaling across instances.
        // All messages are routed through RabbitMQ's STOMP plugin (port 61613).
        registry.enableStompBrokerRelay("/topic", "/queue")
                .setRelayHost(stompHost)
                .setRelayPort(stompPort)
                .setClientLogin(stompUsername)
                .setClientPasscode(stompPassword)
                .setSystemLogin(stompUsername)
                .setSystemPasscode(stompPassword);

        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(presenceChannelInterceptor);
    }
}
