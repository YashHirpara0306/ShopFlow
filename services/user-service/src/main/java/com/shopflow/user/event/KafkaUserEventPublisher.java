package com.shopflow.user.event;

import com.shopflow.common.event.EventType;
import com.shopflow.common.kafka.DomainEventPublisher;
import com.shopflow.common.kafka.Topics;
import com.shopflow.user.domain.AppUser;

import java.time.Instant;
import java.util.Map;

/** Publishes {@code USER_REGISTERED} to {@code notification-events} (key = userId). */
public class KafkaUserEventPublisher implements UserEventPublisher {

    private final DomainEventPublisher publisher;

    public KafkaUserEventPublisher(DomainEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void userRegistered(AppUser user) {
        Map<String, Object> payload = Map.of(
                "userId", user.getId().toString(),
                "email", user.getEmail(),
                "displayName", user.getDisplayName(),
                "registeredAt", Instant.now().toString());
        publisher.publish(Topics.NOTIFICATION_EVENTS, user.getId().toString(),
                EventType.USER_REGISTERED, 1, "user-service", payload);
    }
}
