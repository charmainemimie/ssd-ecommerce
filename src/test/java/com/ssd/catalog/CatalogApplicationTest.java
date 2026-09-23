package com.ssd.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * T-01 "Done when": the app builds and starts. Fails fast if any bean or
 * configuration is broken, before any endpoint-level test runs.
 */
@SpringBootTest
@ActiveProfiles("test")
class CatalogApplicationTest {

    @Test
    void T01_contextLoads() {
        // Intentionally empty: the assertion is that the context started.
    }
}
