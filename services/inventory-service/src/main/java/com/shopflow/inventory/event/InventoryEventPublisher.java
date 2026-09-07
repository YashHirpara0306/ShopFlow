package com.shopflow.inventory.event;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Seam for inventory domain events. Phase 8 logs them; Phase 11 publishes to {@code inventory-events}. */
public interface InventoryEventPublisher {

    void reserved(UUID orderId, Map<UUID, Long> quantities);

    void reservationFailed(UUID orderId, List<String> shortages);

    void released(UUID orderId);

    void confirmed(UUID orderId);
}
