package com.shopflow.inventory.repository;

import com.shopflow.inventory.domain.InventoryReservation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, UUID> {

    @EntityGraph(attributePaths = "items")
    Optional<InventoryReservation> findByOrderId(UUID orderId);
}
