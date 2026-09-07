package com.shopflow.common.event;

/**
 * The complete catalogue of business events that flow over Kafka in ShopFlow.
 *
 * <p>Keeping this as a single enum (rather than magic strings scattered across services) means:
 * <ul>
 *   <li>producers and consumers agree on the exact spelling at compile time;</li>
 *   <li>a {@code switch} over this enum is exhaustively checkable;</li>
 *   <li>adding an event is a one-line, reviewable change here.</li>
 * </ul>
 *
 * <p>The event-to-topic mapping and per-event payload schemas are documented in
 * {@code docs/architecture/} and finalised in Phase 3. This enum only names the events.
 */
public enum EventType {

    // --- User context ---
    USER_REGISTERED,

    // --- Catalog context ---
    PRODUCT_CREATED,

    // --- Order context ---
    ORDER_CREATED,
    ORDER_CONFIRMED,
    ORDER_CANCELLED,

    // --- Inventory context ---
    INVENTORY_RESERVED,
    INVENTORY_RESERVATION_FAILED,
    INVENTORY_RELEASED,

    // --- Payment context ---
    PAYMENT_REQUESTED,
    PAYMENT_INITIATED,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    REFUND_INITIATED,
    REFUND_COMPLETED,

    // --- Notification context ---
    NOTIFICATION_REQUESTED
}
