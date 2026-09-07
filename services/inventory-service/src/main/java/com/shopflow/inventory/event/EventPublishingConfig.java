package com.shopflow.inventory.event;

import com.shopflow.common.kafka.DomainEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventPublishingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
    public InventoryEventPublisher kafkaInventoryEventPublisher(DomainEventPublisher publisher) {
        return new KafkaInventoryEventPublisher(publisher);
    }

    @Bean
    @ConditionalOnMissingBean(InventoryEventPublisher.class)
    public InventoryEventPublisher loggingInventoryEventPublisher() {
        return new LoggingInventoryEventPublisher();
    }
}
