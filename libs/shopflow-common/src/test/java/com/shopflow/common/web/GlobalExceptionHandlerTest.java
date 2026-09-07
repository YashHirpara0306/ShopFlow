package com.shopflow.common.web;

import com.shopflow.common.error.ConflictException;
import com.shopflow.common.error.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Drives {@link GlobalExceptionHandler} through a throwaway controller with standalone MockMvc -
 * no Spring context, no Boot application needed in this library module.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void mapsResourceNotFoundTo404WithStandardBody() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/test/not-found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void mapsConflictTo409() throws Exception {
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void mapsBeanValidationTo400WithFieldDetail() throws Exception {
        mvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("name")));
    }

    @Test
    void mapsUnexpectedExceptionTo500AndHidesInternals() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"));
    }

    @RestController
    @RequestMapping("/test")
    static class TestController {

        @org.springframework.web.bind.annotation.GetMapping("/not-found")
        void notFound() {
            throw ResourceNotFoundException.of("Product", 42);
        }

        @org.springframework.web.bind.annotation.GetMapping("/conflict")
        void conflict() {
            throw new ConflictException("SKU already exists");
        }

        @org.springframework.web.bind.annotation.GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("secret internal detail that must not leak");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody Payload body) {
            // reached only when valid
        }
    }

    record Payload(@NotBlank String name) {
    }
}
