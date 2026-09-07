package com.shopflow.payment.repository;

import com.shopflow.payment.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    Optional<Refund> findByOrderId(UUID orderId);
}
