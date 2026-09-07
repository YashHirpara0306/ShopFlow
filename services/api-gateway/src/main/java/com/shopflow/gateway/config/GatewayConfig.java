package com.shopflow.gateway.config;

import com.shopflow.common.web.ShopFlowHeaders;
import com.shopflow.gateway.security.GatewayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
@EnableConfigurationProperties(GatewayProperties.class)
public class GatewayConfig {

    /**
     * Rate-limit bucket key: the authenticated user id (set by {@code GatewayAuthFilter}) if present,
     * otherwise the client IP. Referenced from {@code application.yml} as {@code #{@clientKeyResolver}}.
     */
    @Bean
    public KeyResolver clientKeyResolver() {
        return exchange -> {
            String userId = exchange.getRequest().getHeaders().getFirst(ShopFlowHeaders.USER_ID);
            if (userId != null && !userId.isBlank()) {
                return Mono.just("user:" + userId);
            }
            String ip = exchange.getRequest().getRemoteAddress() != null
                    ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                    : "unknown";
            return Mono.just("ip:" + ip);
        };
    }
}
