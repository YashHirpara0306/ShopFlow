package com.shopflow.product.api;

import com.shopflow.common.security.JwtAuthenticationFilter;
import com.shopflow.common.security.JwtPrincipal;
import com.shopflow.common.security.JwtValidator;
import com.shopflow.common.web.GlobalExceptionHandler;
import com.shopflow.common.web.RestAccessDeniedHandler;
import com.shopflow.common.web.RestAuthenticationEntryPoint;
import com.shopflow.common.web.SecurityExceptionAdvice;
import com.shopflow.product.api.dto.ProductResponse;
import com.shopflow.product.security.SecurityConfig;
import com.shopflow.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, SecurityExceptionAdvice.class, GlobalExceptionHandler.class})
class ProductControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean ProductService productService;
    @MockitoBean JwtValidator jwtValidator;

    private ProductResponse sample() {
        return new ProductResponse(UUID.randomUUID(), "SKU-1", "Widget", "d", new BigDecimal("9.99"),
                "INR", UUID.randomUUID(), "Gadgets", "gadgets", null, true, Instant.now());
    }

    private void adminToken(String token) {
        when(jwtValidator.validate(token)).thenReturn(new JwtPrincipal(
                UUID.randomUUID().toString(), Set.of("ROLE_ADMIN"), Instant.now(), Instant.now().plusSeconds(900)));
    }

    private void customerToken(String token) {
        when(jwtValidator.validate(token)).thenReturn(new JwtPrincipal(
                UUID.randomUUID().toString(), Set.of("ROLE_CUSTOMER"), Instant.now(), Instant.now().plusSeconds(900)));
    }

    @Test
    void listIsPublic() throws Exception {
        Page<ProductResponse> page = new PageImpl<>(List.of(sample()));
        when(productService.listActive(any(), any())).thenReturn(page);

        mvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getByIdIsPublic() throws Exception {
        ProductResponse p = sample();
        when(productService.getActiveById(any())).thenReturn(p);

        mvc.perform(get("/api/products/{id}", p.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Widget"));
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createIsForbiddenForCustomers() throws Exception {
        customerToken("ctok");
        mvc.perform(post("/api/products").header("Authorization", "Bearer ctok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"S1","name":"N","price":1.00,"categoryId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    void createSucceedsForAdmin() throws Exception {
        adminToken("atok");
        when(productService.create(any(), any())).thenReturn(sample());

        mvc.perform(post("/api/products").header("Authorization", "Bearer atok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"SKU-1","name":"Widget","price":9.99,"categoryId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-1"));
    }

    @Test
    void createRejectsInvalidBodyForAdmin() throws Exception {
        adminToken("atok");
        mvc.perform(post("/api/products").header("Authorization", "Bearer atok")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"","name":"","price":-1,"categoryId":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void deactivateReturns204ForAdmin() throws Exception {
        adminToken("atok");
        mvc.perform(delete("/api/products/{id}", UUID.randomUUID()).header("Authorization", "Bearer atok"))
                .andExpect(status().isNoContent());
    }
}
