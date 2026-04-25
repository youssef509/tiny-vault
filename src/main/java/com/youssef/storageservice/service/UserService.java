package com.youssef.storageservice.service;

import com.youssef.storageservice.model.User;
import com.youssef.storageservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Business logic for managing API users.
 *
 * User creation is intentionally manual — no self-registration endpoint.
 * Users are created either via this service (CommandLineRunner on startup)
 * or directly by inserting a row into the database.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Creates a new API user with a BCrypt-hashed secret.
     *
     * @param email          the user's email address (must be unique)
     * @param apiKey         the plaintext API key (used for DB lookup — must be unique)
     * @param plainTextSecret the raw API secret to be hashed with BCrypt (never stored as-is)
     * @return the saved User entity
     * @throws IllegalArgumentException if email or API key already exists
     */
    @Transactional
    public User createUser(String email, String apiKey, String plainTextSecret) {
        // Guard: enforce uniqueness before hitting the DB constraint
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                "A user with email '" + email + "' already exists");
        }
        if (userRepository.existsByApiKey(apiKey)) {
            throw new IllegalArgumentException(
                "A user with API key '" + apiKey + "' already exists");
        }

        // Hash the secret — this is the only place the raw secret is handled
        String hashedSecret = passwordEncoder.encode(plainTextSecret);

        User user = User.builder()
                .email(email)
                .apiKey(apiKey)
                .apiSecretHash(hashedSecret)
                .isActive(true)
                .build();

        User saved = userRepository.save(user);
        log.info("Created user: email={}, apiKey={}", email, maskKey(apiKey));
        return saved;
    }

    /**
     * Finds a user by their API key.
     * Returns empty Optional if not found or if account is inactive.
     */
    @Transactional(readOnly = true)
    public Optional<User> findActiveUserByApiKey(String apiKey) {
        return userRepository.findByApiKeyAndIsActive(apiKey, true);
    }

    /**
     * Deactivates a user account without deleting them.
     * Their files remain in the database but they can no longer authenticate.
     */
    @Transactional
    public void deactivateUser(String apiKey) {
        userRepository.findByApiKey(apiKey).ifPresent(user -> {
            user.setIsActive(false);
            userRepository.save(user);
            log.info("Deactivated user with apiKey={}", maskKey(apiKey));
        });
    }

    /** Masks an API key for safe logging. "abc123xyz" → "abc***xyz" */
    private String maskKey(String key) {
        if (key == null || key.length() < 6) return "***";
        return key.substring(0, 3) + "***" + key.substring(key.length() - 3);
    }
}
