package com.superchat.user.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String ROOMS_EXCHANGE = "rooms.exchange";

    @Bean
    TopicExchange roomsExchange() {
        return new TopicExchange(ROOMS_EXCHANGE, true, false);
    }
}
