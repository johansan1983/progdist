package com.superchat.chat.config;

import org.slf4j.MDC;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RabbitConfig {

    // ── Chat messaging ────────────────────────────────────────────────────────

    @Bean
    TopicExchange chatExchange(@Value("${chat.rabbit.exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    Queue chatQueue(@Value("${chat.rabbit.queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    Binding chatBinding(
            Queue chatQueue,
            TopicExchange chatExchange,
            @Value("${chat.rabbit.routing-key}") String routingKey
    ) {
        return BindingBuilder.bind(chatQueue).to(chatExchange).with(routingKey);
    }

    // ── Audit — declare exchange so compliance-service consumer connects cleanly ──

    @Bean
    TopicExchange auditExchange() {
        return new TopicExchange("audit.exchange", true, false);
    }

    // ── Notifications — declared here so the exchange/queue exist before
    //    notification-service starts (declarations are idempotent in RabbitMQ) ──

    @Bean
    TopicExchange notificationsExchange(@Value("${chat.notifications.exchange:notifications.exchange}") String name) {
        return new TopicExchange(name, true, false);
    }

    @Bean
    Queue notificationsQueue() {
        return new Queue("notifications.queue", true);
    }

    @Bean
    Binding notificationsBinding(
            Queue notificationsQueue,
            TopicExchange notificationsExchange,
            @Value("${chat.notifications.routing-key:notifications.message.created}") String routingKey
    ) {
        return BindingBuilder.bind(notificationsQueue).to(notificationsExchange).with(routingKey);
    }

    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

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
