package com.shopflow.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "cancel_reason", length = 40)
    private String cancelReason;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Order() {
    }

    private Order(UUID id, UUID userId, BigDecimal totalAmount) {
        this.id = id;
        this.userId = userId;
        this.totalAmount = totalAmount;
        this.status = OrderStatus.PENDING;
    }

    public static Order create(UUID userId, List<NewLine> lines, BigDecimal totalAmount) {
        return create(UUID.randomUUID(), userId, lines, totalAmount);
    }

    /** Overload used when the id was reserved earlier (e.g. written into the idempotency-key row first). */
    public static Order create(UUID id, UUID userId, List<NewLine> lines, BigDecimal totalAmount) {
        Order order = new Order(id, userId, totalAmount);
        for (NewLine line : lines) {
            order.items.add(new OrderItem(order, line.productId(), line.productName(), line.unitPrice(), line.quantity()));
        }
        return order;
    }

    /** Guard the transition here so the aggregate can never be in an illegal state. */
    public OrderStatusHistory transitionTo(OrderStatus target, String reason, String correlationId) {
        OrderStateMachine.validate(this.status, target);
        OrderStatus previous = this.status;
        this.status = target;
        if (target == OrderStatus.CANCELLED && reason != null) {
            this.cancelReason = reason.length() > 40 ? reason.substring(0, 40) : reason;
        }
        return new OrderStatusHistory(this.id, previous, target, reason, correlationId);
    }

    public OrderStatusHistory initialHistory(String correlationId) {
        return new OrderStatusHistory(this.id, null, OrderStatus.PENDING, "order created", correlationId);
    }

    public boolean isOwnedBy(UUID candidate) {
        return userId.equals(candidate);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public List<OrderItem> getItems() {
        return List.copyOf(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** A cart line about to be snapshotted onto a new order. */
    public record NewLine(UUID productId, String productName, BigDecimal unitPrice, int quantity) {
    }
}
