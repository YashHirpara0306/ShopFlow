package com.shopflow.product;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.security.JwtIssuer;
import com.shopflow.product.client.InventoryClient;
import com.shopflow.product.repository.CategoryRepository;
import com.shopflow.product.repository.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end: real Postgres + real Redis + Flyway + Spring Security. Covers admin CRUD, public
 * read, filtering, the cache-aside populate/evict cycle, and authorization. Requires Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@TestPropertySource(properties = "shopflow.jwt.secret=integration-secret-integration-secret-1234")
class ProductCatalogIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @TestConfiguration
    static class StubInventory {
        @Bean
        InventoryClient inventoryClient() {
            return (productId, sku, bearerToken) -> { /* no-op: Inventory Service has its own tests */ };
        }
    }

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtIssuer jwtIssuer;
    @Autowired StringRedisTemplate redisTemplate;
    @Autowired ProductRepository productRepository;
    @Autowired CategoryRepository categoryRepository;

    @AfterEach
    void wipe() {
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        java.util.Set<String> keys = redisTemplate.keys("product:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String adminAuth() {
        return "Bearer " + jwtIssuer.issueAccessToken("admin-1", Set.of("ROLE_ADMIN")).token();
    }

    private String customerAuth() {
        return "Bearer " + jwtIssuer.issueAccessToken("cust-1", Set.of("ROLE_CUSTOMER")).token();
    }

    private String createCategory(String name) throws Exception {
        String json = mvc.perform(post("/api/categories").header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("id").asText();
    }

    private JsonNode createProduct(String sku, String name, String price, String categoryId) throws Exception {
        String json = mvc.perform(post("/api/products").header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"%s","price":%s,"categoryId":"%s"}
                                """.formatted(sku, name, price, categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void adminCreatesCategoryAndProductThenPublicCanListAndFilter() throws Exception {
        String books = createCategory("Books");
        String gadgets = createCategory("Gadgets");
        createProduct("BOOK-1", "Clean Code", "499.00", books);
        createProduct("GAD-1", "USB Cable", "199.00", gadgets);

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));

        mvc.perform(get("/api/products").param("category", "books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("BOOK-1"));

        mvc.perform(get("/api/products").param("minPrice", "300"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].sku").value("BOOK-1"));

        mvc.perform(get("/api/products").param("q", "usb"))
                .andExpect(jsonPath("$.content[0].sku").value("GAD-1"));
    }

    @Test
    void readingAProductPopulatesTheCacheAndUpdatingEvictsIt() throws Exception {
        String cat = createCategory("Cache");
        String id = createProduct("CACHE-1", "Cached", "10.00", cat).get("id").asText();
        String key = "product:" + id;

        assertThat(redisTemplate.hasKey(key)).isFalse();

        mvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk());
        assertThat(redisTemplate.hasKey(key)).isTrue();

        // second read is a cache hit and still correct
        mvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cached"));

        mvc.perform(put("/api/products/{id}", id).header("Authorization", adminAuth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Renamed\"}"))
                .andExpect(status().isOk());
        assertThat(redisTemplate.hasKey(key)).isFalse();

        mvc.perform(get("/api/products/{id}", id))
                .andExpect(jsonPath("$.name").value("Renamed"));
    }

    @Test
    void deactivatedProductsAreHiddenFromThePublicApi() throws Exception {
        String cat = createCategory("Hidden");
        String id = createProduct("HID-1", "Soon Gone", "10.00", cat).get("id").asText();

        mvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/products/{id}", id).header("Authorization", adminAuth()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/products/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(get("/api/products")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void writeEndpointsRequireAdmin() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/categories").header("Authorization", customerAuth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nope\"}"))
                .andExpect(status().isForbidden());
    }
}
