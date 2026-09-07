package com.shopflow.payment.event;

import com.shopflow.common.kafka.DomainEventPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EventPublishingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
    public PaymentEventPublisher kafkaPaymentEventPublisher(DomainEventPublisher publisher) {
        return new KafkaPaymentEventPublisher(publisher);
    }

    @Bean
    @ConditionalOnMissingBean(PaymentEventPublisher.class)
    public PaymentEventPublisher loggingPaymentEventPublisher() {
        return new LoggingPaymentEventPublisher();
    }
}
