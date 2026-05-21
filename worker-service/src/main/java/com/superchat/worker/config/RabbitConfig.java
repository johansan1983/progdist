package com.superchat.worker.config;

import org.slf4j.MDC;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    @Bean
    Queue chatQueue(@Value("${worker.rabbit.queue}") String queueName) {
        return new Queue(queueName, true);
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
