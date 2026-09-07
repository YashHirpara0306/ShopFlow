package com.shopflow.user.api;

import com.shopflow.common.error.ConflictException;
import com.shopflow.common.security.JwtValidator;
import com.shopflow.common.web.GlobalExceptionHandler;
import com.shopflow.user.api.dto.LoginRequest;
import com.shopflow.user.api.dto.TokenResponse;
import com.shopflow.user.domain.AppUser;
import com.shopflow.user.domain.Role;
import com.shopflow.user.security.RestAccessDeniedHandler;
import com.shopflow.user.security.RestAuthenticationEntryPoint;
import com.shopflow.user.security.SecurityConfig;
import com.shopflow.user.service.AuthService;
import com.shopflow.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.EnumSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class,
        GlobalExceptionHandler.class})
class AuthControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean UserService userService;
    @MockitoBean AuthService authService;
    @MockitoBean JwtValidator jwtValidator;

    @Test
    void registerReturns201ForAValidBody() throws Exception {
        when(userService.register(any())).thenReturn(
                AppUser.create("jordan@example.com", "hash", "Jordan", EnumSet.of(Role.CUSTOMER)));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jordan@example.com","password":"password1","displayName":"Jordan"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jordan@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void registerReturns400ForAnInvalidBody() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","displayName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }

    @Test
    void registerReturns409WhenTheEmailIsTaken() throws Exception {
        when(userService.register(any())).thenThrow(new ConflictException("Email is already registered"));

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"taken@example.com","password":"password1","displayName":"Sam"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    void loginReturnsTokens() throws Exception {
        when(authService.login(any())).thenReturn(TokenResponse.bearer("access.jwt", "refresh-raw", 900));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jordan@example.com","password":"secret"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("access.jwt"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-raw"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void loginReturns401ForBadCredentials() throws Exception {
        when(authService.login(any()))
                .thenThrow(new com.shopflow.common.error.AuthenticationFailedException("Invalid credentials"));

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"jordan@example.com","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void logoutReturns204WithoutRequiringAToken() throws Exception {
        mvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"some-refresh"}
                                """))
                .andExpect(status().isNoContent());
    }
}
