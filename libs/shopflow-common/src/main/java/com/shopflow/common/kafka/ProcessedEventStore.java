package com.shopflow.common.kafka;

import java.util.UUID;

/**
 * Consumer-side idempotency. Each service backs this with its own {@code processed_event} table
 * (a row per {@code (eventId)} in that service's database), written in the same local transaction
 * as the handler's side effects.
 */
public interface ProcessedEventStore {

    /**
     * Record that {@code eventId} is being processed by {@code consumer}.
     *
     * @return {@code true} if this is the first time (the caller should proceed), {@code false} if
     * the event was already processed (the caller should skip - it is a duplicate delivery).
     */
    boolean markProcessed(UUID eventId, String consumer);
}
