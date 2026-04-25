package com.youssef.storageservice.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tracks metadata for every file uploaded to the storage service.
 *
 * The actual file content lives on disk at {@code storagePath}.
 * This record tells us: who owns the file, what it's called, how big it is,
 * what type it is, where to find it on disk, and whether it's publicly accessible.
 */
@Entity
@Table(
    name = "files",
    uniqueConstraints = {
        // A user cannot have two files with the same stored filename
        @UniqueConstraint(name = "uq_files_user_filename", columnNames = {"user_id", "filename"})
    },
    indexes = {
        @Index(name = "idx_files_user_id",    columnList = "user_id"),
        @Index(name = "idx_files_created_at", columnList = "created_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The user who uploaded this file. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_files_user_id"))
    private User user;

    /**
     * The stored filename on disk — always a UUID-based name to avoid collisions
     * and prevent path traversal.
     * Example: "a1b2c3d4-uuid.pdf"
     */
    @Column(name = "filename", nullable = false, length = 255)
    private String filename;

    /**
     * The original filename provided by the uploader.
     * Stored for display purposes only — never used to write to disk.
     * Example: "my-document.pdf"
     */
    @Column(name = "original_filename", length = 255)
    private String originalFilename;

    /** File size in bytes. */
    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    /** MIME type detected from file content (not just extension). e.g. "application/pdf" */
    @Column(name = "mime_type", length = 127)
    private String mimeType;

    /**
     * Absolute path to the file on disk.
     * Pattern: /var/storage/{apiKey}/{filename}
     * Example: /var/storage/myapp-key/a1b2c3d4-uuid.pdf
     */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /**
     * Whether this file is publicly accessible without authentication.
     *
     * When true, the file can be fetched via GET /api/v1/public/{filename}
     * without any X-API-Key / X-API-Secret headers — suitable for embedding
     * images or files in Next.js / Laravel frontends.
     *
     * Default: false (private). Toggle with PATCH /api/v1/files/{filename}/visibility.
     */
    @Builder.Default
    @Column(name = "is_public", nullable = false,
            columnDefinition = "boolean not null default false")
    private Boolean isPublic = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
