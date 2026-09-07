package com.shopflow.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_adjustment_audit")
public class StockAdjustmentAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(nullable = false)
    private long delta;

    @Column(name = "new_total", nullable = false)
    private long newTotal;

    @Column(length = 64)
    private String actor;

    @Column(length = 200)
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockAdjustmentAudit() {
    }

    public StockAdjustmentAudit(UUID productId, long delta, long newTotal, String actor, String reason) {
        this.productId = productId;
        this.delta = delta;
        this.newTotal = newTotal;
        this.actor = actor;
        this.reason = reason;
    }
}
