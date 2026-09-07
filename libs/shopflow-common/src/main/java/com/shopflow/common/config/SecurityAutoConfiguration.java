package com.shopflow.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.security.JwtAuthenticationFilter;
import com.shopflow.common.security.JwtValidator;
import com.shopflow.common.web.RestAccessDeniedHandler;
import com.shopflow.common.web.RestAuthenticationEntryPoint;
import com.shopflow.common.web.SecurityExceptionAdvice;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Shared JWT-security building blocks for servlet services: the bearer-token filter, the JSON 401/403
 * responders, the method-security advice, and a BCrypt {@link PasswordEncoder}.
 *
 * <p>Each service still declares its own {@code SecurityFilterChain} bean (its public paths differ)
 * and injects {@link JwtAuthenticationFilter}, {@link RestAuthenticationEntryPoint} and
 * {@link RestAccessDeniedHandler} from here.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({SecurityFilterChain.class, JwtValidator.class})
public class SecurityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JwtValidator.class)
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtValidator jwtValidator) {
        return new JwtAuthenticationFilter(jwtValidator);
    }

    @Bean
    @ConditionalOnMissingBean
    public RestAuthenticationEntryPoint restAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return new RestAuthenticationEntryPoint(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RestAccessDeniedHandler restAccessDeniedHandler(ObjectMapper objectMapper) {
        return new RestAccessDeniedHandler(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityExceptionAdvice securityExceptionAdvice(RestAccessDeniedHandler accessDeniedHandler,
                                                          RestAuthenticationEntryPoint entryPoint) {
        return new SecurityExceptionAdvice(accessDeniedHandler, entryPoint);
    }

    @Bean
    @ConditionalOnMissingBean(PasswordEncoder.class)
    public PasswordEncoder passwordEncoder(@Value("${shopflow.bcrypt-strength:12}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }
}
