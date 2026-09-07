package com.shopflow.inventory.service;

import com.shopflow.common.error.ConflictException;
import com.shopflow.common.error.ResourceNotFoundException;
import com.shopflow.inventory.api.dto.ReservationResponse;
import com.shopflow.inventory.api.dto.StockResponse;
import com.shopflow.inventory.domain.InventoryReservation;
import com.shopflow.inventory.domain.ReservationStatus;
import com.shopflow.inventory.domain.Stock;
import com.shopflow.inventory.domain.StockAdjustmentAudit;
import com.shopflow.inventory.repository.InventoryReservationRepository;
import com.shopflow.inventory.repository.StockAdjustmentAuditRepository;
import com.shopflow.inventory.repository.StockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final StockRepository stocks;
    private final InventoryReservationRepository reservations;
    private final StockAdjustmentAuditRepository audits;
    private final TransactionTemplate txTemplate;

    public InventoryService(StockRepository stocks,
                            InventoryReservationRepository reservations,
                            StockAdjustmentAuditRepository audits,
                            PlatformTransactionManager transactionManager) {
        this.stocks = stocks;
        this.reservations = reservations;
        this.audits = audits;
        // REQUIRES_NEW: a shortage rolls back only the reservation attempt, not the caller's
        // transaction (e.g. the Kafka listener's processed_event insert must still commit).
        this.txTemplate = new TransactionTemplate(transactionManager);
        this.txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Outcome of a reservation attempt. A shortage is a normal result, not an exception. */
    public record ReservationOutcome(boolean reserved, ReservationResponse reservation, List<String> shortages) {
        static ReservationOutcome ok(ReservationResponse r) {
            return new ReservationOutcome(true, r, List.of());
        }

        static ReservationOutcome shortage(List<String> shortages) {
            return new ReservationOutcome(false, null, List.copyOf(shortages));
        }
    }

    @Transactional
    public StockResponse setStock(UUID productId, long quantity, String actor) {
        Stock stock = stocks.findById(productId).orElseGet(() -> new Stock(productId));
        long delta = quantity - stock.getTotal();
        stock.setTotalQuantity(quantity);
        stocks.save(stock);
        audits.save(new StockAdjustmentAudit(productId, delta, quantity, actor, "set stock"));
        log.info("stock set productId={} total={} available={}", productId, stock.getTotal(), stock.getAvailable());
        return toResponse(stock);
    }

    @Transactional
    public StockResponse adjust(UUID productId, long delta, String reason, String actor) {
        Stock stock = stocks.findById(productId)
                .orElseThrow(() -> ResourceNotFoundException.of("Stock", productId));
        stock.adjustBy(delta);
        stocks.save(stock);
        audits.save(new StockAdjustmentAudit(productId, delta, stock.getTotal(), actor, reason));
        return toResponse(stock);
    }

    @Transactional(readOnly = true)
    public StockResponse getStock(UUID productId) {
        return stocks.findById(productId).map(InventoryService::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Stock", productId));
    }

    /**
     * All-or-nothing reservation for an order. Idempotent per orderId. A shortage rolls back any
     * partial decrement (via {@link TransactionTemplate#execute} + {@code setRollbackOnly}) and
     * returns {@link ReservationOutcome#shortage}, never throwing.
     */
    public ReservationOutcome reserve(UUID orderId, Map<UUID, Long> quantities) {
        InventoryReservation existing = reservations.findByOrderId(orderId).orElse(null);
        if (existing != null) {
            return ReservationOutcome.ok(toResponse(existing));
        }
        return txTemplate.execute(status -> {
            List<String> shortages = new ArrayList<>();
            for (Map.Entry<UUID, Long> line : quantities.entrySet()) {
                if (stocks.tryReserve(line.getKey(), line.getValue()) == 0) {
                    shortages.add("product " + line.getKey() + " x" + line.getValue());
                }
            }
            if (!shortages.isEmpty()) {
                status.setRollbackOnly(); // undo any lines that did decrement
                return ReservationOutcome.shortage(shortages);
            }
            InventoryReservation reservation = InventoryReservation.reserved(orderId, quantities);
            try {
                reservations.saveAndFlush(reservation);
            } catch (DataIntegrityViolationException race) {
                throw new ConflictException("A reservation already exists for order " + orderId);
            }
            log.info("inventory reserved orderId={} lines={}", orderId, quantities.size());
            return ReservationOutcome.ok(toResponse(reservation));
        });
    }

    @Transactional
    public ReservationResponse release(UUID orderId) {
        InventoryReservation reservation = reservations.findByOrderId(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", orderId));
        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            return toResponse(reservation);
        }
        reservation.markReleased();
        reservation.getItems().forEach(i -> stocks.release(i.getProductId(), i.getQuantity()));
        log.info("inventory released orderId={}", orderId);
        return toResponse(reservation);
    }

    @Transactional
    public ReservationResponse confirm(UUID orderId) {
        InventoryReservation reservation = reservations.findByOrderId(orderId)
                .orElseThrow(() -> ResourceNotFoundException.of("Reservation", orderId));
        if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
            return toResponse(reservation);
        }
        reservation.markConfirmed();
        reservation.getItems().forEach(i -> stocks.confirm(i.getProductId(), i.getQuantity()));
        log.info("inventory confirmed orderId={}", orderId);
        return toResponse(reservation);
    }

    /** {@code true} if a reservation exists for the order (used by the Saga to skip no-op releases). */
    @Transactional(readOnly = true)
    public boolean hasReservation(UUID orderId) {
        return reservations.findByOrderId(orderId).isPresent();
    }

    public static Map<UUID, Long> toQuantityMap(List<com.shopflow.inventory.api.dto.ReserveRequest.Line> lines) {
        Map<UUID, Long> map = new LinkedHashMap<>();
        for (var line : lines) {
            map.merge(line.productId(), line.quantity(), Long::sum);
        }
        return map;
    }

    private static StockResponse toResponse(Stock stock) {
        return new StockResponse(stock.getProductId(), stock.getTotal(), stock.getAvailable(), stock.getReserved());
    }

    private static ReservationResponse toResponse(InventoryReservation r) {
        List<ReservationResponse.Line> lines = r.getItems().stream()
                .map(i -> new ReservationResponse.Line(i.getProductId(), i.getQuantity()))
                .toList();
        return new ReservationResponse(r.getOrderId(), r.getStatus().name(), lines, r.getCreatedAt());
    }
}
