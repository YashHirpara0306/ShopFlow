package com.shopflow.user.api;

import com.shopflow.common.security.JwtAuthenticationFilter;
import com.shopflow.common.security.JwtPrincipal;
import com.shopflow.common.security.JwtValidator;
import com.shopflow.common.web.GlobalExceptionHandler;
import com.shopflow.common.web.RestAccessDeniedHandler;
import com.shopflow.common.web.RestAuthenticationEntryPoint;
import com.shopflow.common.web.SecurityExceptionAdvice;
import com.shopflow.user.domain.AppUser;
import com.shopflow.user.domain.Role;
import com.shopflow.user.security.SecurityConfig;
import com.shopflow.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, SecurityExceptionAdvice.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean UserService userService;
    @MockitoBean JwtValidator jwtValidator;

    private final UUID customerId = UUID.randomUUID();

    private void bearerIsCustomer(String token) {
        when(jwtValidator.validate(token)).thenReturn(new JwtPrincipal(
                customerId.toString(), Set.of("ROLE_CUSTOMER"), Instant.now(), Instant.now().plusSeconds(900)));
    }

    private void bearerIsAdmin(String token) {
        when(jwtValidator.validate(token)).thenReturn(new JwtPrincipal(
                UUID.randomUUID().toString(), Set.of("ROLE_ADMIN"), Instant.now(), Instant.now().plusSeconds(900)));
    }

    private AppUser sampleUser() {
        return AppUser.create("jordan@example.com", "hash", "Jordan", EnumSet.of(Role.CUSTOMER));
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void meReturnsTheCallersProfile() throws Exception {
        bearerIsCustomer("ctok");
        when(userService.getById(customerId)).thenReturn(sampleUser());

        mvc.perform(get("/api/users/me").header("Authorization", "Bearer ctok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jordan@example.com"));
    }

    @Test
    void listIsForbiddenForACustomer() throws Exception {
        bearerIsCustomer("ctok");

        mvc.perform(get("/api/users").header("Authorization", "Bearer ctok"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("ACCESS_DENIED"));
    }

    @Test
    void listIsAllowedForAnAdmin() throws Exception {
        bearerIsAdmin("atok");
        Page<AppUser> page = new PageImpl<>(List.of(sampleUser()));
        when(userService.list(any())).thenReturn(page);

        mvc.perform(get("/api/users").header("Authorization", "Bearer atok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("jordan@example.com"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getByIdIsAllowedForSelf() throws Exception {
        bearerIsCustomer("ctok");
        when(userService.getById(customerId)).thenReturn(sampleUser());

        mvc.perform(get("/api/users/{id}", customerId).header("Authorization", "Bearer ctok"))
                .andExpect(status().isOk());
    }

    @Test
    void getByIdIsForbiddenForADifferentCustomer() throws Exception {
        bearerIsCustomer("ctok");

        mvc.perform(get("/api/users/{id}", UUID.randomUUID()).header("Authorization", "Bearer ctok"))
                .andExpect(status().isForbidden());
    }
}
