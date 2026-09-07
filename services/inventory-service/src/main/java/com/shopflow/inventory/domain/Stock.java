package com.shopflow.inventory.domain;

import com.shopflow.common.error.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Stock for one product. Invariant: {@code total = available + reserved}, all non-negative.
 *
 * <p>Reserve / release / confirm are done with atomic conditional SQL UPDATEs in
 * {@code StockRepository} (the oversell guard). This entity + {@link Version} is used only for the
 * admin "set / adjust stock" path.
 */
@Entity
@Table(name = "stock")
public class Stock {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(nullable = false)
    private long total;

    @Column(nullable = false)
    private long available;

    @Column(nullable = false)
    private long reserved;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Stock() {
    }

    public Stock(UUID productId) {
        this.productId = productId;
        this.total = 0;
        this.available = 0;
        this.reserved = 0;
    }

    /** Set absolute on-hand quantity. {@code available} becomes {@code total - reserved}. */
    public void setTotalQuantity(long newTotal) {
        if (newTotal < reserved) {
            throw new ValidationException("Cannot set total (" + newTotal + ") below already-reserved (" + reserved + ")");
        }
        this.total = newTotal;
        this.available = newTotal - reserved;
    }

    public void adjustBy(long delta) {
        setTotalQuantity(this.total + delta);
    }

    public UUID getProductId() {
        return productId;
    }

    public long getTotal() {
        return total;
    }

    public long getAvailable() {
        return available;
    }

    public long getReserved() {
        return reserved;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
