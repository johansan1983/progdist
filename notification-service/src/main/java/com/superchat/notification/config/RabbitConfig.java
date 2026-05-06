package com.superchat.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange notificationsExchange(@Value("${notifications.rabbit.exchange:notifications.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    Queue notificationsQueue(@Value("${notifications.rabbit.queue:notifications.queue}") String name) {
        return new Queue(name, true);
    }

    @Bean
    Binding notificationsBinding(
            Queue notificationsQueue,
            TopicExchange notificationsExchange,
            @Value("${notifications.rabbit.routing-key:notifications.message.created}") String routingKey
    ) {
        return BindingBuilder.bind(notificationsQueue).to(notificationsExchange).with(routingKey);
    }

    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
