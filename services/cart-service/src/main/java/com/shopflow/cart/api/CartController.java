package com.shopflow.cart.api;

import com.shopflow.cart.api.dto.AddItemRequest;
import com.shopflow.cart.api.dto.CartResponse;
import com.shopflow.cart.api.dto.UpdateQuantityRequest;
import com.shopflow.cart.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
@RequestMapping("/api/cart")
@Tag(name = "Cart")
@SecurityRequirement(name = "bearerAuth")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    private static UUID user(String principal) {
        return UUID.fromString(principal);
    }

    @GetMapping
    @Operation(summary = "Get the caller's cart (priced)")
    public CartResponse getCart(@AuthenticationPrincipal String userId) {
        return cartService.getCart(user(userId));
    }

    @PostMapping("/items")
    @Operation(summary = "Add an item (or increment its quantity)")
    public CartResponse addItem(@AuthenticationPrincipal String userId, @Valid @RequestBody AddItemRequest request) {
        return cartService.addItem(user(userId), request);
    }

    @PutMapping("/items/{productId}")
    @Operation(summary = "Set an item's quantity (0 removes it)")
    public CartResponse updateItem(@AuthenticationPrincipal String userId,
                                   @PathVariable UUID productId,
                                   @Valid @RequestBody UpdateQuantityRequest request) {
        return cartService.updateQuantity(user(userId), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove an item")
    public CartResponse removeItem(@AuthenticationPrincipal String userId, @PathVariable UUID productId) {
        return cartService.removeItem(user(userId), productId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Clear the cart")
    public void clear(@AuthenticationPrincipal String userId) {
        cartService.clear(user(userId));
    }
}
