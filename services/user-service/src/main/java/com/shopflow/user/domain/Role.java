package com.shopflow.user.domain;

/**
 * Application roles. Stored by {@code name()} in {@code user_role.role}. The Spring Security
 * authority string is {@code "ROLE_" + name()} - see {@link #authority()}.
 */
public enum Role {

    CUSTOMER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
