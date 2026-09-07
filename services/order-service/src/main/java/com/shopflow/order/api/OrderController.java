package com.shopflow.order.api;

import com.shopflow.common.web.PageResponse;
import com.shopflow.common.web.ShopFlowHeaders;
import com.shopflow.order.api.dto.OrderResponse;
import com.shopflow.order.api.dto.UpdateOrderStatusRequest;
import com.shopflow.order.domain.OrderStatus;
import com.shopflow.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@Tag(name = "Orders")
@SecurityRequirement(name = "bearerAuth")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    private static UUID caller(Authentication auth) {
        return UUID.fromString(auth.getName());
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Create an order from the caller's cart. Returns 202; poll GET for the final status.")
    public OrderResponse create(Authentication auth,
                                @RequestHeader(value = ShopFlowHeaders.IDEMPOTENCY_KEY, required = false) String idemKey,
                                @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return orderService.create(caller(auth), idemKey, authorization);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an order (owner or admin), with status history")
    public OrderResponse get(Authentication auth, @PathVariable UUID id) {
        return orderService.get(id, caller(auth), isAdmin(auth));
    }

    @GetMapping
    @Operation(summary = "List orders. A customer sees only their own; an admin can filter by userId/status.")
    public PageResponse<OrderResponse> list(Authentication auth,
                                            @RequestParam(required = false) OrderStatus status,
                                            @RequestParam(required = false) UUID userId,
                                            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                            Pageable pageable) {
        Page<OrderResponse> page = orderService.list(caller(auth), isAdmin(auth), status, userId, pageable);
        return PageResponse.of(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @PostMapping("/{id}/cancellation")
    @Operation(summary = "Cancel an order (owner or admin); only while PENDING / INVENTORY_PENDING / AWAITING_PAYMENT")
    public OrderResponse cancel(Authentication auth, @PathVariable UUID id) {
        return orderService.cancel(id, caller(auth), isAdmin(auth));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Force a status transition (admin / internal). Phase 11 replaces this with the Saga.")
    public OrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatus(id, request.status(), request.reason());
    }
}
