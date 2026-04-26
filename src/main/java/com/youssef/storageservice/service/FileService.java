package com.youssef.storageservice.service;

import com.youssef.storageservice.config.StorageProperties;
import com.youssef.storageservice.dto.FileInfoResponse;
import com.youssef.storageservice.dto.FileUploadResponse;
import com.youssef.storageservice.exception.FileNotFoundException;
import com.youssef.storageservice.exception.InvalidFileTypeException;
import com.youssef.storageservice.exception.StorageException;
import com.youssef.storageservice.model.FileMetadata;
import com.youssef.storageservice.model.User;
import com.youssef.storageservice.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Orchestrates all file business logic.
 *
 * Upload flow:
 *  1. Validate MIME type against allowed list
 *  2. Generate UUID-based stored filename
 *  3. Write bytes to disk via FileStorageService
 *  4. Persist metadata to database (with isPublic flag)
 *  5. Return response DTO including publicUrl (if public)
 *
 * Public file pattern:
 *  - Upload with ?public=true → get back a publicUrl
 *  - Embed publicUrl directly in <img src>, Next.js Image, <a href>, etc.
 *  - No auth needed to fetch /api/v1/public/{filename}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileStorageService fileStorageService;
    private final FileMetadataRepository fileMetadataRepository;
    private final StorageProperties storageProperties;
    private final MimeTypeDetector mimeTypeDetector;

    /**
     * Base URL of this service — used to build public file URLs.
     * Set via app.base-url in application.yml.
     * Example: https://storage.myserver.com
     */
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    /**
     * Handles a file upload request.
     *
     * @param file     the uploaded multipart file
     * @param user     the authenticated user (owner)
     * @param isPublic if true, the file is publicly accessible without auth
     * @return response DTO — includes publicUrl when isPublic=true
     */
    @Transactional
    public FileUploadResponse uploadFile(MultipartFile file, User user, boolean isPublic) {
        if (file.isEmpty()) {
            throw new StorageException("Cannot upload an empty file");
        }

        // 2. Detect MIME type from actual file content (magic bytes), not client header
        String mimeType = mimeTypeDetector.detect(file);
        if (!storageProperties.getAllowedMimeTypes().contains(mimeType)) {
            throw new InvalidFileTypeException(mimeType);
        }

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = extractExtension(originalFilename);
        String storedFilename = UUID.randomUUID() + (extension.isBlank() ? "" : "." + extension);

        String storagePath;
        try {
            storagePath = fileStorageService.store(file, user.getApiKey(), storedFilename);
        } catch (IOException ex) {
            throw new StorageException("Failed to store file: " + originalFilename, ex);
        }

        FileMetadata metadata = FileMetadata.builder()
                .user(user)
                .filename(storedFilename)
                .originalFilename(originalFilename)
                .fileSizeBytes(file.getSize())
                .mimeType(mimeType)
                .storagePath(storagePath)
                .isPublic(isPublic)
                .build();

        FileMetadata saved = fileMetadataRepository.save(metadata);
        log.info("File uploaded: original={}, stored={}, user={}, size={}B, public={}",
                originalFilename, storedFilename, user.getEmail(), file.getSize(), isPublic);

        return FileUploadResponse.builder()
                .id(saved.getId())
                .filename(saved.getFilename())
                .originalFilename(saved.getOriginalFilename())
                .fileSizeBytes(saved.getFileSizeBytes())
                .mimeType(saved.getMimeType())
                .isPublic(saved.getIsPublic())
                .publicUrl(buildPublicUrl(saved))
                .uploadedAt(saved.getCreatedAt())
                .build();
    }

    // -------------------------------------------------------------------------
    // Download (authenticated)
    // -------------------------------------------------------------------------

    /**
     * Loads a file for authenticated download.
     * Validates ownership — a user cannot download another user's file.
     */
    @Transactional(readOnly = true)
    public Resource downloadFile(String storedFilename, User user) {
        FileMetadata metadata = fileMetadataRepository
                .findByFilenameAndUser(storedFilename, user)
                .orElseThrow(() -> new FileNotFoundException(storedFilename));

        if (!fileStorageService.exists(user.getApiKey(), storedFilename)) {
            log.error("DB record exists but file missing on disk: {}", metadata.getStoragePath());
            throw new FileNotFoundException(storedFilename);
        }

        log.info("File downloaded (auth): {}, user={}", storedFilename, user.getEmail());
        return fileStorageService.load(user.getApiKey(), storedFilename);
    }

    // -------------------------------------------------------------------------
    // Public download (no auth — for frontend embedding)
    // -------------------------------------------------------------------------

    /**
     * Serves a public file — no authentication required.
     * Only works if the file's isPublic flag is true.
     *
     * This is the endpoint used by Next.js / Laravel frontends to display files:
     *   <img src="https://storage.myserver.com/api/v1/public/uuid.jpg" />
     *
     * @param storedFilename the UUID-based filename
     * @return the file resource and its metadata (for Content-Type)
     */
    @Transactional(readOnly = true)
    public FileMetadata loadPublicFile(String storedFilename) {
        FileMetadata metadata = fileMetadataRepository
                .findByFilenameAndIsPublicTrue(storedFilename)
                .orElseThrow(() -> new FileNotFoundException(storedFilename));

        if (!fileStorageService.exists(metadata.getUser().getApiKey(), storedFilename)) {
            log.error("Public file missing on disk: {}", metadata.getStoragePath());
            throw new FileNotFoundException(storedFilename);
        }

        log.info("Public file served: {}", storedFilename);
        return metadata;
    }

    public Resource loadPublicFileResource(FileMetadata metadata) {
        return fileStorageService.load(metadata.getUser().getApiKey(), metadata.getFilename());
    }

    // -------------------------------------------------------------------------
    // Visibility toggle
    // -------------------------------------------------------------------------

    /**
     * Makes a file public or private.
     *
     * Use case:
     *   PATCH /api/v1/files/{filename}/visibility?public=true
     *   → file becomes accessible at /api/v1/public/{filename} without auth
     *
     * @param storedFilename the file to update
     * @param user           the authenticated owner
     * @param makePublic     true = public, false = private
     * @return updated file info with the new publicUrl (or null if now private)
     */
    @Transactional
    public FileInfoResponse setVisibility(String storedFilename, User user, boolean makePublic) {
        FileMetadata metadata = fileMetadataRepository
                .findByFilenameAndUser(storedFilename, user)
                .orElseThrow(() -> new FileNotFoundException(storedFilename));

        metadata.setIsPublic(makePublic);
        FileMetadata saved = fileMetadataRepository.save(metadata);

        log.info("Visibility changed: file={}, user={}, public={}",
                storedFilename, user.getEmail(), makePublic);

        return toFileInfoResponse(saved);
    }

    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<FileInfoResponse> listFiles(User user, Pageable pageable) {
        return fileMetadataRepository.findAllByUser(user, pageable)
                .map(this::toFileInfoResponse);
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    @Transactional
    public void deleteFile(String storedFilename, User user) {
        FileMetadata metadata = fileMetadataRepository
                .findByFilenameAndUser(storedFilename, user)
                .orElseThrow(() -> new FileNotFoundException(storedFilename));

        try {
            fileStorageService.delete(user.getApiKey(), storedFilename);
        } catch (IOException ex) {
            log.warn("Could not delete file from disk (cleaning DB record anyway): {}",
                    ex.getMessage());
        }

        fileMetadataRepository.delete(metadata);
        log.info("File deleted: stored={}, original={}, user={}",
                storedFilename, metadata.getOriginalFilename(), user.getEmail());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Builds the full public URL, or null if the file is private. */
    private String buildPublicUrl(FileMetadata metadata) {
        if (!Boolean.TRUE.equals(metadata.getIsPublic())) return null;
        return baseUrl.stripTrailing() + "/api/v1/public/" + metadata.getFilename();
    }

    private FileInfoResponse toFileInfoResponse(FileMetadata metadata) {
        return FileInfoResponse.builder()
                .id(metadata.getId())
                .filename(metadata.getFilename())
                .originalFilename(metadata.getOriginalFilename())
                .fileSizeBytes(metadata.getFileSizeBytes())
                .mimeType(metadata.getMimeType())
                .isPublic(metadata.getIsPublic())
                .publicUrl(buildPublicUrl(metadata))
                .uploadedAt(metadata.getCreatedAt())
                .build();
    }

    /**
     * Sanitizes a client-provided original filename — rejects dangerous patterns.
     *
     * Security checks:
     *   - Null bytes (used to trick some parsers: "evil.php\0.jpg")
     *   - Control characters (non-printable ASCII)
     *   - Path traversal sequences (../ and ..\ in any form)
     *   - Strips any leading path components
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "upload";

        // 1. Null byte check — reject immediately
        if (filename.contains("\0")) {
            throw new StorageException("Filename contains null byte — rejected");
        }

        // 2. Control character check
        for (char c : filename.toCharArray()) {
            if (c < 0x20 && c != 0x09) {   // allow tab
                throw new StorageException("Filename contains control characters — rejected");
            }
        }

        // 3. Explicit path traversal check (before stripping)
        String normalized = filename.replace('\\', '/');
        if (normalized.contains("../") || normalized.contains("./") || normalized.startsWith(".")) {
            log.warn("Path traversal attempt in filename: {}", filename);
            throw new StorageException("Filename contains path traversal sequences — rejected");
        }

        // 4. Strip any directory component (last resort)
        String stripped = normalized.contains("/")
                ? normalized.substring(normalized.lastIndexOf('/') + 1)
                : normalized;

        return stripped.trim().isEmpty() ? "upload" : stripped.trim();
    }

    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) return "";
        String ext = filename.substring(dotIndex + 1).toLowerCase();
        return ext.matches("[a-z0-9]{1,10}") ? ext : "";
    }
}
