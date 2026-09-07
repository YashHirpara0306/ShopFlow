package com.shopflow.user.event;

import com.shopflow.common.kafka.DomainEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventPublishingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
    public UserEventPublisher kafkaUserEventPublisher(DomainEventPublisher publisher) {
        return new KafkaUserEventPublisher(publisher);
    }

    @Bean
    @ConditionalOnMissingBean(UserEventPublisher.class)
    public UserEventPublisher loggingUserEventPublisher() {
        return new LoggingUserEventPublisher();
    }
}
