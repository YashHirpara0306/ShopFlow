package com.shopflow.user.api;

import com.shopflow.common.web.PageResponse;
import com.shopflow.user.api.dto.UpdateProfileRequest;
import com.shopflow.user.api.dto.UserResponse;
import com.shopflow.user.domain.AppUser;
import com.shopflow.user.service.UserMapper;
import com.shopflow.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "Profile and administration. Requires a bearer token.")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's own profile")
    public UserResponse me(@AuthenticationPrincipal String userId) {
        return UserMapper.toResponse(userService.getById(UUID.fromString(userId)));
    }

    @PutMapping("/me")
    @Operation(summary = "Update the authenticated user's own profile")
    public UserResponse updateMe(@AuthenticationPrincipal String userId,
                                 @Valid @RequestBody UpdateProfileRequest request) {
        return UserMapper.toResponse(userService.updateProfile(UUID.fromString(userId), request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or #id.toString() == authentication.name")
    @Operation(summary = "Get a user by id (self or admin)")
    public UserResponse getById(@PathVariable UUID id) {
        return UserMapper.toResponse(userService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List users (admin only)")
    public PageResponse<UserResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        Page<AppUser> page = userService.list(pageable);
        return PageResponse.of(
                page.map(UserMapper::toResponse).getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements());
    }
}
