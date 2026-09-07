package com.shopflow.order.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Polls the outbox and publishes unpublished rows to Kafka. Idempotent: a row is re-sent if the
 *  relay crashes between {@code send} and {@code markPublished} (consumers de-duplicate on eventId). */
@Component
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outbox;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxRepository outbox, KafkaTemplate<String, String> kafkaTemplate) {
        this.outbox = outbox;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${shopflow.outbox.relay-interval:500ms}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outbox.findByPublishedAtIsNullOrderByCreatedAtAsc(Limit.of(100));
        for (OutboxEvent event : pending) {
            kafkaTemplate.send(event.getTopic(), event.getPartitionKey(), event.getPayload());
            event.markPublished();
        }
        if (!pending.isEmpty()) {
            log.info("outbox relay published {} event(s)", pending.size());
        }
    }
}
