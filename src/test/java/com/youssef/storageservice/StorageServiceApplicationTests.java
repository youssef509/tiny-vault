package com.youssef.storageservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: verifies the Spring application context loads correctly
 * with all beans, configs, and DB connection.
 */
@SpringBootTest
@ActiveProfiles("dev")
class StorageServiceApplicationTests {

    @Test
    void contextLoads() {
        // If this test passes, the application context started successfully:
        // - All @Bean definitions are valid
        // - DB connection was established (Neon PostgreSQL)
        // - All @Autowired dependencies resolved
    }
}
