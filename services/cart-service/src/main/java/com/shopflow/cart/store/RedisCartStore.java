package com.shopflow.cart.store;

import com.shopflow.common.error.DownstreamUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Redis-hash-per-user cart. Key {@code cart:{userId}}, field = productId, value = quantity.
 * Every mutation refreshes a sliding TTL. Redis being unreachable surfaces as a 503 - the cart
 * cannot fall back to a database because there isn't one (ADR-0004).
 */
@Component
public class RedisCartStore implements CartStore {

    private static final String KEY_PREFIX = "cart:";

    private final StringRedisTemplate redis;
    private final HashOperations<String, String, String> hash;
    private final Duration ttl;

    public RedisCartStore(StringRedisTemplate redis,
                          @Value("${shopflow.cart.ttl:14d}") Duration ttl) {
        this.redis = redis;
        this.hash = redis.opsForHash();
        this.ttl = ttl;
    }

    private static String key(UUID userId) {
        return KEY_PREFIX + userId;
    }

    @Override
    public int addQuantity(UUID userId, UUID productId, int delta, int max) {
        try {
            long updated = hash.increment(key(userId), productId.toString(), delta);
            int clamped = (int) Math.max(0, Math.min(max, updated));
            if (clamped != updated) {
                hash.put(key(userId), productId.toString(), Integer.toString(clamped));
            }
            if (clamped == 0) {
                hash.delete(key(userId), productId.toString());
            }
            redis.expire(key(userId), ttl);
            return clamped;
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void setQuantity(UUID userId, UUID productId, int quantity) {
        try {
            if (quantity <= 0) {
                hash.delete(key(userId), productId.toString());
            } else {
                hash.put(key(userId), productId.toString(), Integer.toString(quantity));
            }
            redis.expire(key(userId), ttl);
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void remove(UUID userId, UUID productId) {
        try {
            hash.delete(key(userId), productId.toString());
            redis.expire(key(userId), ttl);
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public Map<UUID, Integer> items(UUID userId) {
        try {
            Map<String, String> raw = hash.entries(key(userId));
            Map<UUID, Integer> result = new LinkedHashMap<>();
            raw.forEach((productId, qty) -> result.put(UUID.fromString(productId), Integer.parseInt(qty)));
            return result;
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    @Override
    public void clear(UUID userId) {
        try {
            redis.delete(key(userId));
        } catch (DataAccessException e) {
            throw unavailable(e);
        }
    }

    private static DownstreamUnavailableException unavailable(Exception cause) {
        return new DownstreamUnavailableException("Cart storage is temporarily unavailable", cause);
    }
}
