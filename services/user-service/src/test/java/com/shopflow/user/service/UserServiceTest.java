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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock AppUserRepository users;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserSecurityProperties securityProperties;
    @Mock UserEventPublisher eventPublisher;

    UserService userService() {
        return new UserService(users, passwordEncoder, securityProperties, eventPublisher);
    }

    private RegisterRequest register(String email) {
        return new RegisterRequest(email, "password1", "Jordan");
    }

    @Test
    void registerHashesThePasswordAssignsCustomerRoleAndPublishesEvent() {
        when(users.existsByEmailIgnoreCase("jordan@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password1")).thenReturn("BCRYPT-HASH");
        when(securityProperties.isBootstrapAdmin(any())).thenReturn(false);

        AppUser result = userService().register(register("jordan@example.com"));

        ArgumentCaptor<AppUser> saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("BCRYPT-HASH");
        assertThat(saved.getValue().getRoles()).containsExactly(Role.CUSTOMER);
        assertThat(result.getEmail()).isEqualTo("jordan@example.com");
        verify(eventPublisher).userRegistered(result);
    }

    @Test
    void registerRejectsADuplicateEmailUpFront() {
        when(users.existsByEmailIgnoreCase("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService().register(register("taken@example.com")))
                .isInstanceOf(ConflictException.class);
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void registerTranslatesAUniqueConstraintRaceIntoConflict() {
        when(users.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("h");
        when(securityProperties.isBootstrapAdmin(any())).thenReturn(false);
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_app_user_email"));

        assertThatThrownBy(() -> userService().register(register("race@example.com")))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void registerGrantsAdminToABootstrapEmail() {
        when(users.existsByEmailIgnoreCase(any())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("h");
        when(securityProperties.isBootstrapAdmin("boss@shopflow.com")).thenReturn(true);

        AppUser result = userService().register(register("boss@shopflow.com"));

        assertThat(result.getRoles()).containsExactlyInAnyOrder(Role.CUSTOMER, Role.ADMIN);
    }

    @Test
    void getByIdThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(users.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService().getById(id))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateProfileMutatesTheLoadedUser() {
        UUID id = UUID.randomUUID();
        AppUser user = AppUser.create("j@example.com", "h", "Old Name", java.util.EnumSet.of(Role.CUSTOMER));
        when(users.findById(id)).thenReturn(Optional.of(user));

        AppUser result = userService().updateProfile(id, new UpdateProfileRequest("New Name", "+91 99999 11111"));

        assertThat(result.getDisplayName()).isEqualTo("New Name");
        assertThat(result.getPhone()).isEqualTo("+91 99999 11111");
    }
}
