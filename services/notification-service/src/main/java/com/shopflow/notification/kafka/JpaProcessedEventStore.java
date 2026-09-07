package com.shopflow.notification.kafka;

import com.shopflow.common.kafka.ProcessedEventStore;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Runs inside the consumer handler's transaction: the {@code processed_event} INSERT and the
 * handler's side effects commit together, so a duplicate delivery is a clean no-op and a crash
 * mid-handler leaves no "processed" marker.
 */
@Component
public class JpaProcessedEventStore implements ProcessedEventStore {

    private final ProcessedEventRepository repository;

    public JpaProcessedEventStore(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean markProcessed(UUID eventId, String consumer) {
        if (repository.existsById(eventId)) {
            return false;
        }
        repository.save(new ProcessedEvent(eventId, consumer)); // flushed with the handler tx
        return true;
    }
}
