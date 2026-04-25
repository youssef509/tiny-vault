package com.youssef.storageservice.config;

import com.youssef.storageservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Runs on application startup to create an initial API user.
 *
 * How to use:
 *  1. Set app.init.create-user=true in application-dev.yml (or via env var)
 *  2. Set your desired email, api-key, and api-secret
 *  3. Start the application — the user will be created and logged
 *  4. Set create-user=false (or remove the property) to prevent re-running
 *
 * This is intentionally a one-shot utility, not a production endpoint.
 * The runner is safe to re-run: it checks for duplicates before inserting.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UserInitializationRunner {

    @Value("${app.init.create-user:false}")
    private boolean createUser;

    @Value("${app.init.user-email:}")
    private String userEmail;

    @Value("${app.init.user-api-key:}")
    private String userApiKey;

    @Value("${app.init.user-api-secret:}")
    private String userApiSecret;

    private final UserService userService;

    @Bean
    public CommandLineRunner initializeFirstUser() {
        return args -> {
            if (!createUser) {
                log.debug("User initialization skipped (app.init.create-user=false)");
                return;
            }

            // Validate all required fields are present
            if (userEmail.isBlank() || userApiKey.isBlank() || userApiSecret.isBlank()) {
                log.error("Cannot create user: app.init.user-email, " +
                          "app.init.user-api-key, and app.init.user-api-secret are all required");
                return;
            }

            try {
                userService.createUser(userEmail, userApiKey, userApiSecret);
                log.info("============================================================");
                log.info("  Initial user created successfully!");
                log.info("  Email:   {}", userEmail);
                log.info("  API Key: {}", userApiKey);
                log.info("  Set app.init.create-user=false to skip on next startup.");
                log.info("============================================================");
            } catch (IllegalArgumentException e) {
                // User already exists — not a fatal error, just log and continue
                log.warn("User initialization skipped: {}", e.getMessage());
            }
        };
    }
}
