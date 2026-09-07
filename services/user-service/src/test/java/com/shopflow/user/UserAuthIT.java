package com.shopflow.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.observability.CorrelationId;
import com.shopflow.user.domain.RefreshToken;
import com.shopflow.user.repository.AppUserRepository;
import com.shopflow.user.repository.RefreshTokenRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end: real Postgres (Testcontainers) + real Flyway migration + real Spring Security filter
 * chain. Covers the "important test scenarios" for User Service: successful registration/login,
 * duplicate email, invalid input, unauthorized access, admin-only access, refresh-token rotation
 * with reuse detection, and logout.
 *
 * <p>Requires Docker. Runs under Failsafe (class name ends with {@code IT}) and in CI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = {
        "shopflow.jwt.secret=integration-secret-integration-secret-1234",
        "shopflow.jwt.access-token-ttl=15m",
        "shopflow.security.bootstrap-admin-emails=admin@shopflow.test"
})
class UserAuthIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AppUserRepository users;
    @Autowired RefreshTokenRepository refreshTokens;

    @AfterEach
    void wipe() {
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    // --- helpers ---------------------------------------------------------------

    private void register(String email, String password, String name) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", email, "password", password, "displayName", name)))
                .andExpect(status().isCreated());
    }

    private JsonNode login(String email, String password) throws Exception {
        String json = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private static String body(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(kv[i]).append("\":\"").append(kv[i + 1]).append('"');
        }
        return sb.append('}').toString();
    }

    // --- tests ---------------------------------------------------------------

    @Test
    void registerThenLoginThenAccessOwnProfile() throws Exception {
        register("jordan@example.com", "password1", "Jordan");

        // password is stored hashed, never in the clear
        String storedHash = users.findByEmailIgnoreCase("jordan@example.com").orElseThrow().getPasswordHash();
        assertThat(storedHash).startsWith("$2").isNotEqualTo("password1");

        JsonNode tokens = login("jordan@example.com", "password1");
        String accessToken = tokens.get("accessToken").asText();
        assertThat(tokens.get("refreshToken").asText()).isNotBlank();

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jordan@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(header().exists(CorrelationId.HEADER));
    }

    @Test
    void registeringTheSameEmailTwiceReturns409() throws Exception {
        register("dupe@example.com", "password1", "Dupe");

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", "dupe@example.com", "password", "password2", "displayName", "Other")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void invalidRegistrationBodyReturns400() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("email", "not-an-email", "password", "x", "displayName", "A")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void profileEndpointRejectsAnUnauthenticatedRequest() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void refreshRotatesTokensAndReusingTheOldOneRevokesTheFamily() throws Exception {
        register("rot@example.com", "password1", "Rot");
        JsonNode first = login("rot@example.com", "password1");
        String oldRefresh = first.get("refreshToken").asText();

        JsonNode rotated = objectMapper.readTree(mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("refreshToken", oldRefresh)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        String newRefresh = rotated.get("refreshToken").asText();
        assertThat(newRefresh).isNotEqualTo(oldRefresh);

        // reusing the old (now rotated) token is treated as theft
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("refreshToken", oldRefresh)))
                .andExpect(status().isUnauthorized());

        // ... and the whole family, including the fresh token, is revoked
        assertThat(refreshTokens.findAll()).allMatch(RefreshToken::isRevoked);
        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("refreshToken", newRefresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshToken() throws Exception {
        register("out@example.com", "password1", "Out");
        String refresh = login("out@example.com", "password1").get("refreshToken").asText();

        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("refreshToken", refresh)))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("refreshToken", refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listingUsersIsAdminOnly() throws Exception {
        register("cust@example.com", "password1", "Cust");
        String customerToken = login("cust@example.com", "password1").get("accessToken").asText();

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));

        register("admin@shopflow.test", "password1", "Boss"); // bootstrapped to ADMIN
        String adminToken = login("admin@shopflow.test", "password1").get("accessToken").asText();

        mvc.perform(get("/api/users").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(2));
    }
}
