package com.shopflow.inventory.repository;

import com.shopflow.inventory.domain.StockAdjustmentAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockAdjustmentAuditRepository extends JpaRepository<StockAdjustmentAudit, Long> {
}
