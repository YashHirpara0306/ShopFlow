package com.shopflow.gateway.observability;

import com.shopflow.common.observability.CorrelationId;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Ensures every request carries {@code X-Correlation-Id}: reads the client's (validated), or mints
 * one, forwards it downstream, and echoes it on the response. Runs first.
 */
@Component
public class CorrelationIdGatewayFilter implements GlobalFilter, Ordered {

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = CorrelationId.sanitizeOrCreate(
                exchange.getRequest().getHeaders().getFirst(CorrelationId.HEADER));

        ServerHttpRequest mutated = exchange.getRequest().mutate()
                .headers(h -> h.set(CorrelationId.HEADER, correlationId))
                .build();
        exchange.getResponse().getHeaders().set(CorrelationId.HEADER, correlationId);

        return chain.filter(exchange.mutate().request(mutated).build());
    }
}
