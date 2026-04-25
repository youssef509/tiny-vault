package com.youssef.storageservice.repository;

import com.youssef.storageservice.model.FileMetadata;
import com.youssef.storageservice.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for FileMetadata entities.
 *
 * All queries are scoped to a specific user — a user can never
 * access another user's files through these methods.
 */
@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, UUID> {

    /**
     * Finds a file by its stored filename AND its owner.
     * Used for download and delete operations — ensures users can only access
     * files they own (not other users' files with the same filename).
     *
     * @param filename the UUID-based stored filename (not the original name)
     * @param user     the authenticated user (owner)
     * @return the file metadata, or empty if not found / wrong owner
     */
    Optional<FileMetadata> findByFilenameAndUser(String filename, User user);

    /**
     * Returns a paginated list of all files belonging to a user.
     * Used by the GET /api/v1/files endpoint.
     * Pagination prevents memory issues if a user has thousands of files.
     *
     * @param user     the authenticated user
     * @param pageable pagination + sorting parameters
     * @return a page of file metadata
     */
    Page<FileMetadata> findAllByUser(User user, Pageable pageable);

    /**
     * Counts how many files a user has uploaded.
     * Useful for metrics and storage quota checks.
     */
    long countByUser(User user);

    /**
     * Checks if a file with the given stored filename already exists for this user.
     * Prevents duplicate entries before saving to disk.
     */
    boolean existsByFilenameAndUser(String filename, User user);
}
