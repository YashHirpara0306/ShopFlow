package com.shopflow.common.config;

import com.shopflow.common.security.JwtIssuer;
import com.shopflow.common.security.JwtProperties;
import com.shopflow.common.security.JwtValidator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Exposes a {@link JwtValidator} bean whenever {@code shopflow.jwt.secret} is configured.
 *
 * <p>Applies to servlet services and the reactive gateway alike (no web-type condition): both need
 * to verify tokens. When no secret is set (e.g. a service that has not been wired for auth yet) no
 * bean is created, so the 32-byte-secret guard never trips at startup.
 */
@AutoConfiguration
@ConditionalOnClass(JwtValidator.class)
@EnableConfigurationProperties(JwtProperties.class)
@ConditionalOnProperty(prefix = "shopflow.jwt", name = "secret")
public class JwtAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public JwtValidator jwtValidator(JwtProperties properties) {
        return new JwtValidator(properties);
    }

    /**
     * Only the service that issues tokens (User Service) actually injects this, but exposing it
     * here keeps issuing and validating configured from the same properties.
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtIssuer jwtIssuer(JwtProperties properties) {
        return new JwtIssuer(properties);
    }
}
