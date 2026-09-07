package com.shopflow.notification.kafka;

import com.shopflow.common.kafka.EventJson;
import com.shopflow.common.kafka.InboundEvent;
import com.shopflow.common.kafka.ProcessedEventStore;
import com.shopflow.common.kafka.Topics;
import com.shopflow.common.observability.CorrelationId;
import com.shopflow.notification.domain.Channel;
import com.shopflow.notification.service.NotificationService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Turns business events into notifications. Consumes order / payment / notification topics.
 * {@code NotificationService.request} is idempotent per {@code (eventId, channel)}, and the
 * {@code processed_event} guard skips duplicate deliveries.
 */
@Component
@ConditionalOnProperty(prefix = "spring.kafka", name = "bootstrap-servers")
public class NotificationEventsListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventsListener.class);
    private static final String CONSUMER = "notification-svc";

    private final EventJson eventJson;
    private final ProcessedEventStore processedEvents;
    private final NotificationService notifications;

    public NotificationEventsListener(EventJson eventJson, ProcessedEventStore processedEvents,
                                      NotificationService notifications) {
        this.eventJson = eventJson;
        this.processedEvents = processedEvents;
        this.notifications = notifications;
    }

    @KafkaListener(topics = {Topics.ORDER_EVENTS, Topics.PAYMENT_EVENTS, Topics.NOTIFICATION_EVENTS},
            groupId = CONSUMER)
    @Transactional
    public void onEvent(ConsumerRecord<String, String> record) {
        InboundEvent event = eventJson.parse(record.value());
        CorrelationId.set(event.correlationId().toString());
        try {
            if (!processedEvents.markProcessed(event.eventId(), CONSUMER)) {
                return;
            }
            UUID userId = event.uuid("userId");
            switch (event.eventType()) {
                case USER_REGISTERED -> send(event, userId, "WELCOME", null);
                case ORDER_CONFIRMED -> send(event, userId, "ORDER_CONFIRMED", event.text("orderId"));
                case ORDER_CANCELLED -> send(event, userId, "ORDER_CANCELLED", event.text("reason"));
                case PAYMENT_FAILED -> send(event, userId, "PAYMENT_FAILED", event.text("reason"));
                case REFUND_COMPLETED -> send(event, userId, "REFUND_COMPLETED", event.text("orderId"));
                case NOTIFICATION_REQUESTED -> {
                    Channel channel = Channel.valueOf(
                            event.text("channel") == null ? "EMAIL" : event.text("channel"));
                    notifications.request(event.eventId(), userId, channel, event.text("template"), event.text("data"));
                }
                default -> log.debug("notification-svc ignoring {}", event.eventType());
            }
        } finally {
            CorrelationId.clear();
        }
    }

    private void send(InboundEvent event, UUID userId, String template, String data) {
        if (userId == null) {
            log.warn("event {} has no userId; cannot notify", event.eventType());
            return;
        }
        notifications.request(event.eventId(), userId, Channel.EMAIL, template, data);
    }
}
