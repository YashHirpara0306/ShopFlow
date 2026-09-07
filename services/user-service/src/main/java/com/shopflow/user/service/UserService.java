package com.shopflow.user.service;

import com.shopflow.common.error.ConflictException;
import com.shopflow.common.error.ResourceNotFoundException;
import com.shopflow.user.api.dto.RegisterRequest;
import com.shopflow.user.api.dto.UpdateProfileRequest;
import com.shopflow.user.config.UserSecurityProperties;
import com.shopflow.user.domain.AppUser;
import com.shopflow.user.domain.Role;
import com.shopflow.user.event.UserEventPublisher;
import com.shopflow.user.repository.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final UserSecurityProperties securityProperties;
    private final UserEventPublisher eventPublisher;

    public UserService(AppUserRepository users,
                       PasswordEncoder passwordEncoder,
                       UserSecurityProperties securityProperties,
                       UserEventPublisher eventPublisher) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AppUser register(RegisterRequest request) {
        String email = request.email().trim();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Email is already registered");
        }

        Set<Role> roles = securityProperties.isBootstrapAdmin(email)
                ? EnumSet.of(Role.CUSTOMER, Role.ADMIN)
                : EnumSet.of(Role.CUSTOMER);

        AppUser user = AppUser.create(
                email,
                passwordEncoder.encode(request.password()),
                request.displayName().trim(),
                roles);

        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException race) {
            // Lost a race against a concurrent registration - the UNIQUE constraint is the real guard.
            throw new ConflictException("Email is already registered");
        }

        eventPublisher.userRegistered(user);
        log.info("user registered userId={} roles={}", user.getId(), user.getRoles());
        return user;
    }

    @Transactional(readOnly = true)
    public AppUser getById(UUID id) {
        return users.findById(id).orElseThrow(() -> ResourceNotFoundException.of("User", id));
    }

    @Transactional(readOnly = true)
    public Page<AppUser> list(Pageable pageable) {
        return users.findAll(pageable);
    }

    @Transactional
    public AppUser updateProfile(UUID id, UpdateProfileRequest request) {
        AppUser user = getById(id);
        String phone = (request.phone() == null || request.phone().isBlank()) ? null : request.phone().trim();
        user.updateProfile(request.displayName(), phone);
        return user; // flushed on commit via dirty checking
    }
}
