package com.shopflow.inventory.api;

import com.shopflow.common.security.JwtAuthenticationFilter;
import com.shopflow.common.security.JwtPrincipal;
import com.shopflow.common.security.JwtValidator;
import com.shopflow.common.web.GlobalExceptionHandler;
import com.shopflow.common.web.RestAccessDeniedHandler;
import com.shopflow.common.web.RestAuthenticationEntryPoint;
import com.shopflow.common.web.SecurityExceptionAdvice;
import com.shopflow.inventory.api.dto.StockResponse;
import com.shopflow.inventory.security.SecurityConfig;
import com.shopflow.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InventoryController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, SecurityExceptionAdvice.class, GlobalExceptionHandler.class})
class InventoryControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean InventoryService inventoryService;
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
    void setStockRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/inventory/stock").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inventoryIsForbiddenForNonAdmins() throws Exception {
        customerToken("ctok");
        mvc.perform(post("/api/inventory/stock").header("Authorization", "Bearer ctok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantity\":10}".formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanSetStock() throws Exception {
        adminToken("atok");
        UUID productId = UUID.randomUUID();
        when(inventoryService.setStock(any(), anyLong(), anyString()))
                .thenReturn(new StockResponse(productId, 10, 10, 0));

        mvc.perform(post("/api/inventory/stock").header("Authorization", "Bearer atok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"%s\",\"quantity\":10}".formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(10));
    }

    @Test
    void reserveValidatesTheBody() throws Exception {
        adminToken("atok");
        mvc.perform(post("/api/inventory/reservations").header("Authorization", "Bearer atok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":null,\"lines\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
