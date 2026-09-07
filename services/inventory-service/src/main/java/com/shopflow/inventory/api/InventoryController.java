package com.shopflow.inventory.api;

import com.shopflow.inventory.api.dto.AdjustStockRequest;
import com.shopflow.inventory.api.dto.ReservationResponse;
import com.shopflow.inventory.api.dto.ReserveRequest;
import com.shopflow.inventory.api.dto.SetStockRequest;
import com.shopflow.inventory.api.dto.StockResponse;
import com.shopflow.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "Admin / internal. From Phase 11 the Saga drives reservations over Kafka.")
@SecurityRequirement(name = "bearerAuth")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    private static String actor(Authentication auth) {
        return auth != null ? auth.getName() : "system";
    }

    @PostMapping("/stock")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create or set a product's on-hand quantity (Product Service calls this on product creation)")
    public StockResponse setStock(Authentication auth, @Valid @RequestBody SetStockRequest request) {
        return inventoryService.setStock(request.productId(), request.quantity(), actor(auth));
    }

    @PutMapping("/{productId}/adjustment")
    @Operation(summary = "Adjust a product's on-hand quantity by a signed delta (audited)")
    public StockResponse adjust(Authentication auth, @PathVariable UUID productId,
                                @Valid @RequestBody AdjustStockRequest request) {
        return inventoryService.adjust(productId, request.delta(), request.reason(), actor(auth));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get a product's stock")
    public StockResponse getStock(@PathVariable UUID productId) {
        return inventoryService.getStock(productId);
    }

    @PostMapping("/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Reserve stock for an order (all-or-nothing, idempotent per orderId)")
    public ReservationResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return inventoryService.reserve(request.orderId(), InventoryService.toQuantityMap(request.lines()));
    }

    @PostMapping("/reservations/{orderId}/release")
    @Operation(summary = "Release an order's reservation (compensation)")
    public ReservationResponse release(@PathVariable UUID orderId) {
        return inventoryService.release(orderId);
    }

    @PostMapping("/reservations/{orderId}/confirm")
    @Operation(summary = "Confirm an order's reservation (deduct from total on hand)")
    public ReservationResponse confirm(@PathVariable UUID orderId) {
        return inventoryService.confirm(orderId);
    }
}
