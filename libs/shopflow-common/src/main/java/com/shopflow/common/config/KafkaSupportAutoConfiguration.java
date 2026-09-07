package com.shopflow.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.kafka.DomainEventPublisher;
import com.shopflow.common.kafka.EventJson;
import com.shopflow.common.kafka.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka wiring shared by every service that sets {@code spring.kafka.bootstrap-servers}:
 * String key/value serdes, an idempotent {@code acks=all} producer, a {@link DomainEventPublisher},
 * a listener factory that commits per record and, on a handler failure, retries with exponential
 * backoff then routes the record to {@code <topic>.DLT}. Also declares the four topics + DLTs.
 *
 * <p>When {@code spring.kafka.bootstrap-servers} is absent (local dev, non-Saga integration tests)
 * none of this is created, so nothing tries to reach a broker.
 */
@AutoConfiguration
@ConditionalOnClass(KafkaTemplate.class)
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
@EnableConfigurationProperties(KafkaProperties.class)
public class KafkaSupportAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public EventJson eventJson(ObjectMapper objectMapper) {
        return new EventJson(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(name = "shopflowProducerFactory")
    public ProducerFactory<String, String> shopflowProducerFactory(KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties(null));
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    @ConditionalOnMissingBean(KafkaTemplate.class)
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> shopflowProducerFactory) {
        return new KafkaTemplate<>(shopflowProducerFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public DomainEventPublisher domainEventPublisher(KafkaTemplate<String, String> kafkaTemplate, EventJson eventJson) {
        return new DomainEventPublisher(kafkaTemplate, eventJson);
    }

    @Bean
    @ConditionalOnMissingBean(name = "shopflowConsumerFactory")
    public ConsumerFactory<String, String> shopflowConsumerFactory(KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>(properties.buildConsumerProperties(null));
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.putIfAbsent(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 50);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean(name = "kafkaListenerContainerFactory")
    @ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> shopflowConsumerFactory,
            KafkaTemplate<String, String> kafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(shopflowConsumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        // retries: 1s, 2s, 4s (max 3), then -> <topic>.DLT on the same partition
        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxElapsedTime(15_000L);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> new TopicPartition(Topics.dlt(record.topic()), record.partition()));
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class); // malformed envelope -> straight to DLT
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    @Bean
    public KafkaAdmin.NewTopics shopflowTopics() {
        return new KafkaAdmin.NewTopics(
                topic(Topics.ORDER_EVENTS), topic(Topics.dlt(Topics.ORDER_EVENTS)),
                topic(Topics.INVENTORY_EVENTS), topic(Topics.dlt(Topics.INVENTORY_EVENTS)),
                topic(Topics.PAYMENT_EVENTS), topic(Topics.dlt(Topics.PAYMENT_EVENTS)),
                topic(Topics.NOTIFICATION_EVENTS), topic(Topics.dlt(Topics.NOTIFICATION_EVENTS)));
    }

    private static NewTopic topic(String name) {
        return new NewTopic(name, Topics.PARTITIONS, (short) 1);
    }
}
