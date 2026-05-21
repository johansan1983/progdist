package com.superchat.worker.service;

import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ListenerControlService {

    private final RabbitListenerEndpointRegistry registry;
    private final String listenerId;

    public ListenerControlService(
            RabbitListenerEndpointRegistry registry,
            @Value("${worker.rabbit.listener-id:chatEventConsumerListener}") String listenerId
    ) {
        this.registry = registry;
        this.listenerId = listenerId;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startOnBoot() {
        startListener();
    }

    public boolean stopListener() {
        MessageListenerContainer container = getContainerOrThrow();
        if (container.isRunning()) {
            container.stop();
        }
        return container.isRunning();
    }

    public boolean startListener() {
        MessageListenerContainer container = getContainerOrThrow();
        if (!container.isRunning()) {
            container.start();
        }
        return container.isRunning();
    }

    public boolean isRunning() {
        return getContainerOrThrow().isRunning();
    }

    private MessageListenerContainer getContainerOrThrow() {
        MessageListenerContainer container = registry.getListenerContainer(listenerId);
        if (container == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Listener container not ready");
        }
        return container;
    }
}
