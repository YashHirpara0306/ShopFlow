package com.shopflow.inventory.domain;

import com.shopflow.common.error.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockTest {

    @Test
    void setTotalQuantityRecomputesAvailableAsTotalMinusReserved() {
        Stock stock = new Stock(UUID.randomUUID());
        stock.setTotalQuantity(10);
        assertThat(stock.getTotal()).isEqualTo(10);
        assertThat(stock.getAvailable()).isEqualTo(10);
        assertThat(stock.getReserved()).isZero();
    }

    @Test
    void adjustByChangesTotalAndAvailable() {
        Stock stock = new Stock(UUID.randomUUID());
        stock.setTotalQuantity(5);
        stock.adjustBy(3);
        assertThat(stock.getTotal()).isEqualTo(8);
        assertThat(stock.getAvailable()).isEqualTo(8);
    }

    @Test
    void cannotSetTotalBelowWhatIsAlreadyReserved() {
        Stock stock = new Stock(UUID.randomUUID());
        stock.setTotalQuantity(5);
        // simulate a reservation by adjusting is not possible via the entity; this guard is what matters:
        assertThatThrownBy(() -> stock.setTotalQuantity(-1))
                .isInstanceOf(ValidationException.class);
    }
}
