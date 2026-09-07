package com.shopflow.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shopflow.product.api.dto.ProductResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Cache-aside for a single product's detail representation.
 *
 * <ul>
 *   <li>Read: {@link #get} - miss returns empty and the caller loads from the DB then calls {@link #put}.</li>
 *   <li>Write: {@link #evict} on every update/deactivate - delete, don't update, to avoid stale races.</li>
 *   <li>Every key has a TTL ({@code shopflow.cache.product-ttl}) so a missed eviction self-heals.</li>
 * </ul>
 *
 * Hit/miss are exposed as {@code shopflow.product.cache} counters. Redis being down degrades to
 * DB-only reads (logged), never an error.
 */
@Component
public class ProductCache {

    private static final Logger log = LoggerFactory.getLogger(ProductCache.class);
    private static final String KEY_PREFIX = "product:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;
    private final Counter hits;
    private final Counter misses;

    public ProductCache(StringRedisTemplate redis,
                        ObjectMapper objectMapper,
                        MeterRegistry meterRegistry,
                        @Value("${shopflow.cache.product-ttl:10m}") Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = ttl;
        this.hits = Counter.builder("shopflow.product.cache").tag("result", "hit").register(meterRegistry);
        this.misses = Counter.builder("shopflow.product.cache").tag("result", "miss").register(meterRegistry);
    }

    public Optional<ProductResponse> get(UUID id) {
        try {
            String json = redis.opsForValue().get(KEY_PREFIX + id);
            if (json == null) {
                misses.increment();
                return Optional.empty();
            }
            hits.increment();
            return Optional.of(objectMapper.readValue(json, ProductResponse.class));
        } catch (Exception e) {
            log.warn("product cache read failed for {}: {}", id, e.toString());
            return Optional.empty();
        }
    }

    public void put(ProductResponse product) {
        try {
            redis.opsForValue().set(KEY_PREFIX + product.id(), objectMapper.writeValueAsString(product), jitteredTtl());
        } catch (Exception e) {
            log.warn("product cache write failed for {}: {}", product.id(), e.toString());
        }
    }

    /** +/- 10% so a burst of populates for the same key does not all expire on the same tick (stampede). */
    private Duration jitteredTtl() {
        long base = ttl.toMillis();
        long delta = (long) (base * 0.1);
        return Duration.ofMillis(base + ThreadLocalRandom.current().nextLong(-delta, delta + 1));
    }

    public void evict(UUID id) {
        try {
            redis.delete(KEY_PREFIX + id);
        } catch (Exception e) {
            log.warn("product cache evict failed for {}: {}", id, e.toString());
        }
    }
}
