package com.shopflow.gateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.common.error.ErrorCode;
import com.shopflow.common.error.ErrorResponse;
import com.shopflow.common.observability.CorrelationId;
import com.shopflow.common.security.InvalidTokenException;
import com.shopflow.common.security.JwtPrincipal;
import com.shopflow.common.security.JwtValidator;
import com.shopflow.common.web.ShopFlowHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Verifies the bearer token at the edge and forwards identity downstream.
 *
 * <ul>
 *   <li>Always strips client-supplied {@code X-User-*} headers - a service must only ever trust
 *       what the gateway set.</li>
 *   <li>Public routes (config {@code shopflow.gateway.public-paths}) pass through unauthenticated.</li>
 *   <li>Otherwise: no/invalid token -&gt; 401 with the standard error body; valid token -&gt; adds
 *       {@code X-User-Id} and {@code X-User-Roles}.</li>
 * </ul>
 *
 * Services still re-validate the JWT themselves (defence in depth).
 */
@Component
public class GatewayAuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER = "Bearer ";
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private final JwtValidator jwtValidator;
    private final ObjectMapper objectMapper;
    private final List<String> publicPaths;

    public GatewayAuthFilter(JwtValidator jwtValidator, ObjectMapper objectMapper, GatewayProperties properties) {
        this.jwtValidator = jwtValidator;
        this.objectMapper = objectMapper;
        this.publicPaths = properties.getPublicPaths();
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10; // after the correlation-id filter
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // never trust an inbound identity header
        ServerHttpRequest.Builder mutation = request.mutate()
                .headers(h -> {
                    h.remove(ShopFlowHeaders.USER_ID);
                    h.remove(ShopFlowHeaders.USER_ROLES);
                });

        if (isPublic(request.getMethod().name(), request.getPath().value())) {
            return chain.filter(exchange.mutate().request(mutation.build()).build());
        }

        String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER)) {
            return unauthorized(exchange, "A bearer token is required");
        }
        try {
            JwtPrincipal principal = jwtValidator.validate(header.substring(BEARER.length()));
            mutation.headers(h -> {
                h.set(ShopFlowHeaders.USER_ID, principal.userId());
                h.set(ShopFlowHeaders.USER_ROLES, String.join(",", principal.roles()));
            });
            return chain.filter(exchange.mutate().request(mutation.build()).build());
        } catch (InvalidTokenException ex) {
            return unauthorized(exchange, "Invalid or expired token");
        }
    }

    private boolean isPublic(String method, String path) {
        for (String rule : publicPaths) {
            int space = rule.indexOf(' ');
            String ruleMethod = space > 0 ? rule.substring(0, space) : "*";
            String rulePattern = space > 0 ? rule.substring(space + 1) : rule;
            if (("*".equals(ruleMethod) || ruleMethod.equalsIgnoreCase(method))
                    && pathMatcher.match(rulePattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        ErrorResponse body = ErrorResponse.of(HttpStatus.UNAUTHORIZED.value(), ErrorCode.AUTHENTICATION_FAILED,
                message, exchange.getRequest().getPath().value(),
                exchange.getResponse().getHeaders().getFirst(CorrelationId.HEADER));
        try {
            DataBuffer buffer = response.bufferFactory().wrap(objectMapper.writeValueAsBytes(body));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return response.setComplete();
        }
    }
}
