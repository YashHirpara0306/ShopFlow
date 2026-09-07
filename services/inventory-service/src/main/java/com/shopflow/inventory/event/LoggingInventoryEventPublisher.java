package com.shopflow.inventory.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Used when {@code spring.kafka.bootstrap-servers} is not set. */
public class LoggingInventoryEventPublisher implements InventoryEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingInventoryEventPublisher.class);

    @Override
    public void reserved(UUID orderId, Map<UUID, Long> quantities) {
        log.info("event (log transport) INVENTORY_RESERVED orderId={}", orderId);
    }

    @Override
    public void reservationFailed(UUID orderId, List<String> shortages) {
        log.info("event (log transport) INVENTORY_RESERVATION_FAILED orderId={} shortages={}", orderId, shortages);
    }

    @Override
    public void released(UUID orderId) {
        log.info("event (log transport) INVENTORY_RELEASED orderId={}", orderId);
    }

    @Override
    public void confirmed(UUID orderId) {
        log.info("event (log transport) inventory confirmed orderId={}", orderId);
    }
}
