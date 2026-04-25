package com.youssef.storageservice.repository;

import com.youssef.storageservice.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for User entities.
 *
 * The key lookup method is {@code findByApiKey} — called on every authenticated
 * request to resolve the API key to a User record before verifying the secret.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Looks up a user by their API key.
     * Used by the authentication filter on every protected request.
     *
     * @param apiKey the raw API key from the X-API-Key header
     * @return the matching user, or empty if not found
     */
    Optional<User> findByApiKey(String apiKey);

    /**
     * Looks up a user by their API key but only if they are active.
     * Useful for quickly rejecting deactivated accounts without loading the full user.
     *
     * @param apiKey   the raw API key
     * @param isActive must be true to get a result
     * @return the matching active user, or empty
     */
    Optional<User> findByApiKeyAndIsActive(String apiKey, Boolean isActive);

    /**
     * Checks if an email address is already registered.
     * Used during user creation to enforce uniqueness before hitting the DB constraint.
     */
    boolean existsByEmail(String email);

    /**
     * Checks if an API key is already in use.
     * Used during user creation to enforce uniqueness.
     */
    boolean existsByApiKey(String apiKey);
}
