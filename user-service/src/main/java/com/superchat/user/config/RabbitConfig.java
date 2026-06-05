package com.superchat.user.config;

import org.slf4j.MDC;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String ROOMS_EXCHANGE = "rooms.exchange";

    @Bean
    TopicExchange roomsExchange() {
        return new TopicExchange(ROOMS_EXCHANGE, true, false);
    }

    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Publish room/membership events as JSON so chat-service's Jackson-based listener can
     * deserialize them. Without this, the auto-configured RabbitTemplate uses Java
     * serialization (content-type application/x-java-serialized-object) and the consumer fails.
     * Also propagates X-Request-ID for end-to-end trace continuity, matching chat-service.
     */
    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.addBeforePublishPostProcessors(msg -> {
            String rid = MDC.get("requestId");
            if (rid != null) {
                msg.getMessageProperties().setHeader("X-Request-ID", rid);
            }
            return msg;
        });
        return template;
    }
}
