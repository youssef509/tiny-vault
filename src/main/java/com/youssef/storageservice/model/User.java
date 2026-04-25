package com.youssef.storageservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an API user (you / your apps).
 * Users are created manually via DB insertion or the UserService utility.
 *
 * Security notes:
 *  - apiKey   → stored in plaintext (used for DB lookup)
 *  - apiSecretHash → BCrypt hash of the secret (NEVER store plaintext secret)
 */
@Entity
@Table(
    name = "users",
    indexes = {
        @Index(name = "idx_users_api_key", columnList = "api_key", unique = true),
        @Index(name = "idx_users_email",   columnList = "email",   unique = true)
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "files")   // avoid lazy-loading toString trap
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    /** Plaintext API key — used to look up the user. e.g. "myapp-prod-key-abc123" */
    @Column(name = "api_key", unique = true, nullable = false, length = 64)
    private String apiKey;

    /** BCrypt hash of the API secret. Never store the raw secret. */
    @Column(name = "api_secret_hash", nullable = false, length = 255)
    private String apiSecretHash;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** One user → many files. Cascade delete removes files when user is deleted. */
    @OneToMany(
        mappedBy = "user",
        cascade = CascadeType.ALL,
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    @Builder.Default
    private List<FileMetadata> files = new ArrayList<>();
}
