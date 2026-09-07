package com.shopflow.inventory;

import com.shopflow.common.error.ApiException;
import com.shopflow.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The key Inventory scenario: many concurrent reservations racing for the last unit of stock must
 * NOT oversell. Real Postgres via Testcontainers - the atomic conditional UPDATE is what makes this
 * safe. Requires Docker.
 */
@SpringBootTest
@Testcontainers
class InventoryConcurrencyIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired InventoryService inventoryService;

    @Test
    void concurrentReservationsForTheLastUnitDoNotOversell() throws Exception {
        UUID productId = UUID.randomUUID();
        inventoryService.setStock(productId, 1, "test"); // exactly one unit available

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        List<Callable<Void>> tasks = java.util.stream.IntStream.range(0, threads)
                .<Callable<Void>>mapToObj(i -> () -> {
                    try {
                        inventoryService.reserve(UUID.randomUUID(), Map.of(productId, 1L));
                        succeeded.incrementAndGet();
                    } catch (ApiException expectedForLosers) {
                        rejected.incrementAndGet();
                    }
                    return null;
                })
                .toList();

        for (Future<Void> f : pool.invokeAll(tasks)) {
            f.get();
        }
        pool.shutdown();

        assertThat(succeeded.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(threads - 1);

        var stock = inventoryService.getStock(productId);
        assertThat(stock.available()).isZero();
        assertThat(stock.reserved()).isEqualTo(1);
        assertThat(stock.total()).isEqualTo(1);
    }

    @Test
    void reserveReleaseConfirmMoveStockCorrectly() {
        UUID productId = UUID.randomUUID();
        UUID orderA = UUID.randomUUID();
        UUID orderB = UUID.randomUUID();
        inventoryService.setStock(productId, 10, "test");

        inventoryService.reserve(orderA, Map.of(productId, 3L));
        assertThat(inventoryService.getStock(productId).available()).isEqualTo(7);
        assertThat(inventoryService.getStock(productId).reserved()).isEqualTo(3);

        inventoryService.reserve(orderB, Map.of(productId, 2L));
        assertThat(inventoryService.getStock(productId).available()).isEqualTo(5);

        inventoryService.release(orderA); // compensation
        assertThat(inventoryService.getStock(productId).available()).isEqualTo(8);
        assertThat(inventoryService.getStock(productId).reserved()).isEqualTo(2);

        inventoryService.confirm(orderB);
        var finalStock = inventoryService.getStock(productId);
        assertThat(finalStock.total()).isEqualTo(8);
        assertThat(finalStock.reserved()).isZero();
        assertThat(finalStock.available()).isEqualTo(8);
    }

    @Test
    void reservingForTheSameOrderTwiceIsIdempotent() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        inventoryService.setStock(productId, 5, "test");

        inventoryService.reserve(orderId, Map.of(productId, 2L));
        inventoryService.reserve(orderId, Map.of(productId, 2L)); // no double-decrement

        assertThat(inventoryService.getStock(productId).available()).isEqualTo(3);
    }
}
