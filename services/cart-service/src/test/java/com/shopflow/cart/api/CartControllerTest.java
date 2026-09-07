package com.shopflow.cart.api;

import com.shopflow.cart.api.dto.CartResponse;
import com.shopflow.cart.security.SecurityConfig;
import com.shopflow.cart.service.CartService;
import com.shopflow.common.security.JwtAuthenticationFilter;
import com.shopflow.common.security.JwtPrincipal;
import com.shopflow.common.security.JwtValidator;
import com.shopflow.common.web.GlobalExceptionHandler;
import com.shopflow.common.web.RestAccessDeniedHandler;
import com.shopflow.common.web.RestAuthenticationEntryPoint;
import com.shopflow.common.web.SecurityExceptionAdvice;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CartController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, SecurityExceptionAdvice.class, GlobalExceptionHandler.class})
class CartControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean CartService cartService;
    @MockitoBean JwtValidator jwtValidator;

    private final UUID userId = UUID.randomUUID();

    private void customerToken(String token) {
        when(jwtValidator.validate(token)).thenReturn(new JwtPrincipal(
                userId.toString(), Set.of("ROLE_CUSTOMER"), Instant.now(), Instant.now().plusSeconds(900)));
    }

    private CartResponse emptyCart() {
        return new CartResponse(userId, List.of(), 0, BigDecimal.ZERO, "INR");
    }

    @Test
    void getCartRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void getCartReturnsTheCallersCart() throws Exception {
        customerToken("ctok");
        when(cartService.getCart(userId)).thenReturn(emptyCart());

        mvc.perform(get("/api/cart").header("Authorization", "Bearer ctok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.totalQuantity").value(0));
    }

    @Test
    void addItemValidatesTheBody() throws Exception {
        customerToken("ctok");

        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer ctok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":null,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void addItemSucceeds() throws Exception {
        customerToken("ctok");
        when(cartService.addItem(any(), any())).thenReturn(emptyCart());

        mvc.perform(post("/api/cart/items").header("Authorization", "Bearer ctok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantity\":2}".formatted(UUID.randomUUID())))
                .andExpect(status().isOk());
    }
}
