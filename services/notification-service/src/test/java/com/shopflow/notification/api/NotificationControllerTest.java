package com.shopflow.notification.api;

import com.shopflow.common.security.JwtAuthenticationFilter;
import com.shopflow.common.security.JwtPrincipal;
import com.shopflow.common.security.JwtValidator;
import com.shopflow.common.web.GlobalExceptionHandler;
import com.shopflow.common.web.RestAccessDeniedHandler;
import com.shopflow.common.web.RestAuthenticationEntryPoint;
import com.shopflow.common.web.SecurityExceptionAdvice;
import com.shopflow.notification.api.dto.NotificationResponse;
import com.shopflow.notification.security.SecurityConfig;
import com.shopflow.notification.service.NotificationService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, SecurityExceptionAdvice.class, GlobalExceptionHandler.class})
class NotificationControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean NotificationService notificationService;
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
    void requestRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/notifications").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requestIsForbiddenForCustomers() throws Exception {
        customerToken("ctok");
        mvc.perform(post("/api/notifications").header("Authorization", "Bearer ctok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"%s\",\"userId\":\"%s\",\"channel\":\"EMAIL\",\"template\":\"T\"}"
                                .formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanRequestANotification() throws Exception {
        adminToken("atok");
        when(notificationService.request(any(), any(), any(), any(), any())).thenReturn(new NotificationResponse(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "EMAIL", "T", "SENT", 1, null,
                Instant.now(), Instant.now()));

        mvc.perform(post("/api/notifications").header("Authorization", "Bearer atok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"%s\",\"userId\":\"%s\",\"channel\":\"EMAIL\",\"template\":\"ORDER_CONFIRMED\"}"
                                .formatted(UUID.randomUUID(), UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"));
    }

    @Test
    void requestValidatesTheBody() throws Exception {
        adminToken("atok");
        mvc.perform(post("/api/notifications").header("Authorization", "Bearer atok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":null,\"userId\":null,\"channel\":\"EMAIL\",\"template\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
