package com.shopflow.inventory.domain;

import com.shopflow.common.error.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "inventory_reservation")
public class InventoryReservation {

    @Id
    private UUID id;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReservationItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected InventoryReservation() {
    }

    public static InventoryReservation reserved(UUID orderId, Map<UUID, Long> quantities) {
        InventoryReservation reservation = new InventoryReservation();
        reservation.id = UUID.randomUUID();
        reservation.orderId = orderId;
        reservation.status = ReservationStatus.RESERVED;
        quantities.forEach((productId, qty) -> reservation.items.add(new ReservationItem(reservation, productId, qty)));
        return reservation;
    }

    public void markReleased() {
        if (status == ReservationStatus.CONFIRMED) {
            throw new ConflictException("Reservation for order " + orderId + " is already confirmed and cannot be released");
        }
        this.status = ReservationStatus.RELEASED;
    }

    public void markConfirmed() {
        if (status == ReservationStatus.RELEASED) {
            throw new ConflictException("Reservation for order " + orderId + " was released and cannot be confirmed");
        }
        this.status = ReservationStatus.CONFIRMED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public List<ReservationItem> getItems() {
        return List.copyOf(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
