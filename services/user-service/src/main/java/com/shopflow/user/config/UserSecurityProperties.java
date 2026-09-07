package com.shopflow.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Locale;

/**
 * {@code shopflow.security.*} - User Service specifics not covered by the shared {@code shopflow.jwt.*}.
 */
@ConfigurationProperties(prefix = "shopflow.security")
public class UserSecurityProperties {

    /**
     * Emails that are granted {@link com.shopflow.user.domain.Role#ADMIN} at registration.
     * Lets you create the first admin without a manual DB edit. Empty by default.
     */
    private List<String> bootstrapAdminEmails = List.of();

    public List<String> getBootstrapAdminEmails() {
        return bootstrapAdminEmails;
    }

    public void setBootstrapAdminEmails(List<String> bootstrapAdminEmails) {
        this.bootstrapAdminEmails = bootstrapAdminEmails;
    }

    public boolean isBootstrapAdmin(String email) {
        String normalized = email.toLowerCase(Locale.ROOT);
        return bootstrapAdminEmails.stream()
                .map(e -> e.toLowerCase(Locale.ROOT).trim())
                .anyMatch(normalized::equals);
    }
}
