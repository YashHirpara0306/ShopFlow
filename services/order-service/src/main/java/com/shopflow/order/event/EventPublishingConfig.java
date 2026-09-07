package com.shopflow.order.event;

import com.shopflow.order.kafka.OutboxWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the event transport: the Kafka/outbox publisher when {@code spring.kafka.bootstrap-servers}
 * is set, otherwise a logging no-op. Keeps every other integration test able to boot without a broker.
 */
@Configuration
public class EventPublishingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
    public OrderEventPublisher kafkaOrderEventPublisher(OutboxWriter outboxWriter) {
        return new KafkaOrderEventPublisher(outboxWriter);
    }

    @Bean
    @ConditionalOnMissingBean(OrderEventPublisher.class)
    public OrderEventPublisher loggingOrderEventPublisher() {
        return new LoggingOrderEventPublisher();
    }
}
