package com.shopflow.user.api;

import com.shopflow.user.api.dto.LoginRequest;
import com.shopflow.user.api.dto.LogoutRequest;
import com.shopflow.user.api.dto.RefreshRequest;
import com.shopflow.user.api.dto.RegisterRequest;
import com.shopflow.user.api.dto.TokenResponse;
import com.shopflow.user.api.dto.UserResponse;
import com.shopflow.user.service.AuthService;
import com.shopflow.user.service.UserMapper;
import com.shopflow.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration and token lifecycle. All endpoints are public.")
public class AuthController {

    private final UserService userService;
    private final AuthService authService;

    public AuthController(UserService userService, AuthService authService) {
        this.userService = userService;
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new customer account")
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserMapper.toResponse(userService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange email + password for an access token and a refresh token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a valid refresh token for a new access + refresh token (rotation)")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Revoke a refresh token")
    public void logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
    }
}
