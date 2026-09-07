package com.shopflow.user;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the Spring context starts with no external infrastructure.
 * As the service gains a database / Kafka / Redis in later phases, this becomes a
 * Testcontainers-backed integration test.
 */
@SpringBootTest
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
        // Fails the build if wiring, config, or the classpath is broken.
    }
}
