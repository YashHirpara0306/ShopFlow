package com.shopflow.inventory.service;

import com.shopflow.common.error.ConflictException;
import com.shopflow.inventory.domain.InventoryReservation;
import com.shopflow.inventory.domain.ReservationStatus;
import com.shopflow.inventory.domain.Stock;
import com.shopflow.inventory.event.InventoryEventPublisher;
import com.shopflow.inventory.repository.InventoryReservationRepository;
import com.shopflow.inventory.repository.StockAdjustmentAuditRepository;
import com.shopflow.inventory.repository.StockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock StockRepository stocks;
    @Mock InventoryReservationRepository reservations;
    @Mock StockAdjustmentAuditRepository audits;
    @Mock InventoryEventPublisher events;

    InventoryService service() {
        return new InventoryService(stocks, reservations, audits, events);
    }

    private final UUID orderId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    @Test
    void setStockCreatesTheRowWhenAbsentAndWritesAnAudit() {
        when(stocks.findById(productId)).thenReturn(Optional.empty());

        var response = service().setStock(productId, 25, "admin");

        assertThat(response.total()).isEqualTo(25);
        assertThat(response.available()).isEqualTo(25);
        verify(stocks).save(any(Stock.class));
        verify(audits).save(any());
    }

    @Test
    void reserveIsIdempotentWhenAReservationAlreadyExists() {
        InventoryReservation existing = InventoryReservation.reserved(orderId, Map.of(productId, 2L));
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.of(existing));

        var response = service().reserve(orderId, Map.of(productId, 2L));

        assertThat(response.status()).isEqualTo("RESERVED");
        verify(stocks, never()).tryReserve(any(), anyLong());
    }

    @Test
    void reserveSucceedsWhenEveryLineHasStock() {
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(stocks.tryReserve(eq(productId), eq(2L))).thenReturn(1);

        var response = service().reserve(orderId, Map.of(productId, 2L));

        assertThat(response.status()).isEqualTo("RESERVED");
        verify(reservations).saveAndFlush(any(InventoryReservation.class));
        verify(events).reserved(eq(orderId), any());
    }

    @Test
    void reserveFailsAllOrNothingWhenALineIsShort() {
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(stocks.tryReserve(eq(productId), eq(5L))).thenReturn(0);

        assertThatThrownBy(() -> service().reserve(orderId, Map.of(productId, 5L)))
                .isInstanceOf(ReservationFailedException.class);
        verify(reservations, never()).saveAndFlush(any());
        verify(events).reservationFailed(eq(orderId), any());
    }

    @Test
    void releaseIsIdempotentAndRefusesAConfirmedReservation() {
        InventoryReservation reserved = InventoryReservation.reserved(orderId, Map.of(productId, 1L));
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.of(reserved));

        service().release(orderId);
        assertThat(reserved.getStatus()).isEqualTo(ReservationStatus.RELEASED);

        // releasing again is a no-op
        service().release(orderId);

        // a confirmed reservation cannot be released
        InventoryReservation confirmed = InventoryReservation.reserved(UUID.randomUUID(), Map.of(productId, 1L));
        confirmed.markConfirmed();
        when(reservations.findByOrderId(confirmed.getOrderId())).thenReturn(Optional.of(confirmed));
        assertThatThrownBy(() -> service().release(confirmed.getOrderId()))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void confirmDeductsFromTotalOnHand() {
        InventoryReservation reserved = InventoryReservation.reserved(orderId, Map.of(productId, 3L));
        when(reservations.findByOrderId(orderId)).thenReturn(Optional.of(reserved));

        var response = service().confirm(orderId);

        assertThat(response.status()).isEqualTo("CONFIRMED");
        verify(stocks).confirm(productId, 3L);
    }
}
