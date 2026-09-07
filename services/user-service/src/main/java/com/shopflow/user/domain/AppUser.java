package com.shopflow.user.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * A registered user. The aggregate root of this service.
 *
 * <p>Construction goes through {@link #create}: the id is assigned up front (so it can appear in the
 * {@code USER_REGISTERED} event before the row is flushed), the password is stored already hashed,
 * and every user gets at least {@link Role#CUSTOMER}.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected AppUser() {
        // for JPA
    }

    private AppUser(UUID id, String email, String passwordHash, String displayName, Set<Role> roles) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.roles = EnumSet.copyOf(roles);
        this.status = UserStatus.ACTIVE;
    }

    public static AppUser create(String email, String passwordHash, String displayName, Set<Role> roles) {
        Set<Role> effective = roles.isEmpty() ? EnumSet.of(Role.CUSTOMER) : EnumSet.copyOf(roles);
        effective.add(Role.CUSTOMER);
        return new AppUser(UUID.randomUUID(), email, passwordHash, displayName, effective);
    }

    public void updateProfile(String newDisplayName, String newPhone) {
        if (newDisplayName != null && !newDisplayName.isBlank()) {
            this.displayName = newDisplayName;
        }
        this.phone = newPhone;
    }

    public void grantRole(Role role) {
        this.roles.add(role);
    }

    public void disable() {
        this.status = UserStatus.DISABLED;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhone() {
        return phone;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
