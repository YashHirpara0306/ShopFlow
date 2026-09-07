package com.shopflow.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.security.JwtIssuer;
import com.shopflow.notification.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real Postgres + Flyway + Security. Sender is simulated (fail-rate 0). Requires Docker. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "shopflow.jwt.secret=integration-secret-integration-secret-1234",
        "shopflow.notification.fail-rate=0.0",
        "shopflow.notification.retry-scan-interval=3600s"
})
class NotificationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtIssuer jwtIssuer;
    @Autowired NotificationRepository notifications;

    private String admin() {
        return "Bearer " + jwtIssuer.issueAccessToken("admin-1", Set.of("ROLE_ADMIN")).token();
    }

    @AfterEach
    void wipe() {
        notifications.deleteAllInBatch();
    }

    private JsonNode request(UUID eventId, UUID userId, String template) throws Exception {
        String json = mvc.perform(post("/api/notifications").header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"%s\",\"userId\":\"%s\",\"channel\":\"EMAIL\",\"template\":\"%s\"}"
                                .formatted(eventId, userId, template)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void aRequestedNotificationIsSent() throws Exception {
        JsonNode n = request(UUID.randomUUID(), UUID.randomUUID(), "ORDER_CONFIRMED");
        assertThat(n.get("status").asText()).isEqualTo("SENT");
        assertThat(n.get("attempts").asInt()).isEqualTo(1);
    }

    @Test
    void aRedeliveredEventDoesNotResend() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String first = request(eventId, userId, "ORDER_CONFIRMED").get("id").asText();
        String second = request(eventId, userId, "ORDER_CONFIRMED").get("id").asText();

        assertThat(second).isEqualTo(first);
        assertThat(notifications.count()).isEqualTo(1);
    }

    @Test
    void aFailingTemplateEndsUpFailedThenCanBeRetried() throws Exception {
        JsonNode failed = request(UUID.randomUUID(), UUID.randomUUID(), "FAIL");
        assertThat(failed.get("status").asText()).isEqualTo("FAILED");

        // manual retry still fails (template is permanently bad), attempts increments
        mvc.perform(post("/api/notifications/{id}/retry", failed.get("id").asText()).header("Authorization", admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.attempts").value(2));
    }
}
