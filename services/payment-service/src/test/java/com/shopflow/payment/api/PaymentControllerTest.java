package com.shopflow.payment.api;

import com.shopflow.common.security.JwtAuthenticationFilter;
import com.shopflow.common.security.JwtPrincipal;
import com.shopflow.common.security.JwtValidator;
import com.shopflow.common.web.GlobalExceptionHandler;
import com.shopflow.common.web.RestAccessDeniedHandler;
import com.shopflow.common.web.RestAuthenticationEntryPoint;
import com.shopflow.common.web.SecurityExceptionAdvice;
import com.shopflow.payment.api.dto.PaymentResponse;
import com.shopflow.payment.security.SecurityConfig;
import com.shopflow.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, SecurityExceptionAdvice.class, GlobalExceptionHandler.class})
class PaymentControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean PaymentService paymentService;
    @MockitoBean JwtValidator jwtValidator;

    private void adminToken(String token) {
        when(jwtValidator.validate(token)).thenReturn(new JwtPrincipal(
                UUID.randomUUID().toString(), Set.of("ROLE_ADMIN"), Instant.now(), Instant.now().plusSeconds(900)));
    }

    private void customerToken(String token) {
        when(jwtValidator.validate(token)).thenReturn(new JwtPrincipal(
                UUID.randomUUID().toString(), Set.of("ROLE_CUSTOMER"), Instant.now(), Instant.now().plusSeconds(900)));
    }

    @Test
    void processRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/payments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void processIsForbiddenForCustomers() throws Exception {
        customerToken("ctok");
        mvc.perform(post("/api/payments").header("Authorization", "Bearer ctok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"amount\":10.00}".formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanProcessAPayment() throws Exception {
        adminToken("atok");
        UUID orderId = UUID.randomUUID();
        when(paymentService.process(any(), any(), any())).thenReturn(new PaymentResponse(
                UUID.randomUUID(), orderId, new BigDecimal("10.00"), "INR", "CAPTURED", null, Instant.now()));

        mvc.perform(post("/api/payments").header("Authorization", "Bearer atok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"amount\":10.00,\"currency\":\"INR\"}".formatted(orderId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CAPTURED"));
    }

    @Test
    void processRejectsANonPositiveAmount() throws Exception {
        adminToken("atok");
        mvc.perform(post("/api/payments").header("Authorization", "Bearer atok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":\"%s\",\"amount\":0}".formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
