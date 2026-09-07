package com.shopflow.order.api;

import com.shopflow.common.error.ValidationException;
import com.shopflow.common.security.JwtAuthenticationFilter;
import com.shopflow.common.security.JwtPrincipal;
import com.shopflow.common.security.JwtValidator;
import com.shopflow.common.web.GlobalExceptionHandler;
import com.shopflow.common.web.RestAccessDeniedHandler;
import com.shopflow.common.web.RestAuthenticationEntryPoint;
import com.shopflow.common.web.SecurityExceptionAdvice;
import com.shopflow.order.api.dto.OrderResponse;
import com.shopflow.order.security.SecurityConfig;
import com.shopflow.order.service.OrderService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, SecurityExceptionAdvice.class, GlobalExceptionHandler.class})
class OrderControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean OrderService orderService;
    @MockitoBean JwtValidator jwtValidator;

    private final UUID userId = UUID.randomUUID();

    private void customerToken(String token) {
        when(jwtValidator.validate(token)).thenReturn(new JwtPrincipal(
                userId.toString(), Set.of("ROLE_CUSTOMER"), Instant.now(), Instant.now().plusSeconds(900)));
    }

    private void adminToken(String token) {
        when(jwtValidator.validate(token)).thenReturn(new JwtPrincipal(
                UUID.randomUUID().toString(), Set.of("ROLE_ADMIN"), Instant.now(), Instant.now().plusSeconds(900)));
    }

    private OrderResponse sample(String statusValue) {
        return new OrderResponse(UUID.randomUUID(), userId, statusValue, "INR", new BigDecimal("10.00"),
                null, List.of(), List.of(), Instant.now(), Instant.now());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/orders").header("Idempotency-Key", "k1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createReturns202() throws Exception {
        customerToken("ctok");
        when(orderService.create(any(), any(), any())).thenReturn(sample("PENDING"));

        mvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer ctok")
                        .header("Idempotency-Key", "k1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void createWithoutIdempotencyKeyIsRejected() throws Exception {
        customerToken("ctok");
        when(orderService.create(any(), any(), any()))
                .thenThrow(new ValidationException("Idempotency-Key header is required"));

        mvc.perform(post("/api/orders").header("Authorization", "Bearer ctok"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void forceStatusIsAdminOnly() throws Exception {
        customerToken("ctok");

        mvc.perform(put("/api/orders/{id}/status", UUID.randomUUID())
                        .header("Authorization", "Bearer ctok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVENTORY_PENDING\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void forceStatusSucceedsForAdmin() throws Exception {
        adminToken("atok");
        when(orderService.updateStatus(any(), any(), any())).thenReturn(sample("INVENTORY_PENDING"));

        mvc.perform(put("/api/orders/{id}/status", UUID.randomUUID())
                        .header("Authorization", "Bearer atok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"INVENTORY_PENDING\",\"reason\":\"saga step\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INVENTORY_PENDING"));
    }
}
