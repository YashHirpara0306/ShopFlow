package com.shopflow.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_key")
public class IdempotencyKey {

    @Id
    private UUID id;

    @Column(name = "idem_key", nullable = false, unique = true, length = 200)
    private String idemKey;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyKey() {
    }

    public IdempotencyKey(String idemKey, UUID userId, String requestHash, UUID orderId) {
        this.id = UUID.randomUUID();
        this.idemKey = idemKey;
        this.userId = userId;
        this.requestHash = requestHash;
        this.orderId = orderId;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public UUID getOrderId() {
        return orderId;
    }
}
