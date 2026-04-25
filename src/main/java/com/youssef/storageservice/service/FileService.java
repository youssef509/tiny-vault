package com.youssef.storageservice.service;

import com.youssef.storageservice.config.StorageProperties;
import com.youssef.storageservice.dto.FileUploadResponse;
import com.youssef.storageservice.exception.FileNotFoundException;
import com.youssef.storageservice.exception.InvalidFileTypeException;
import com.youssef.storageservice.exception.StorageException;
import com.youssef.storageservice.model.FileMetadata;
import com.youssef.storageservice.model.User;
import com.youssef.storageservice.repository.FileMetadataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
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
 *  4. Persist metadata to database
 *  5. Return response DTO
 *
 * Download flow:
 *  1. Look up metadata in DB (by filename + user — prevents cross-user access)
 *  2. Verify file exists on disk
 *  3. Return streamable Resource
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileStorageService fileStorageService;
    private final FileMetadataRepository fileMetadataRepository;
    private final StorageProperties storageProperties;

    /**
     * Handles a file upload request for the given user.
     *
     * @param file the uploaded multipart file
     * @param user the authenticated user (owner)
     * @return a response DTO with file metadata
     */
    @Transactional
    public FileUploadResponse uploadFile(MultipartFile file, User user) {
        // 1. Reject empty files
        if (file.isEmpty()) {
            throw new StorageException("Cannot upload an empty file");
        }

        // 2. Detect and validate MIME type
        // Note: getContentType() is client-provided; Phase 4 adds content-based detection
        String mimeType = resolveMimeType(file);
        if (!storageProperties.getAllowedMimeTypes().contains(mimeType)) {
            throw new InvalidFileTypeException(mimeType);
        }

        // 3. Extract safe file extension from original name
        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extension = extractExtension(originalFilename);

        // 4. Generate a UUID-based stored filename (NEVER use the original name on disk)
        String storedFilename = UUID.randomUUID().toString()
                + (extension.isBlank() ? "" : "." + extension);

        // 5. Write to disk
        String storagePath;
        try {
            storagePath = fileStorageService.store(file, user.getApiKey(), storedFilename);
        } catch (IOException ex) {
            throw new StorageException("Failed to store file: " + originalFilename, ex);
        }

        // 6. Persist metadata to database
        FileMetadata metadata = FileMetadata.builder()
                .user(user)
                .filename(storedFilename)
                .originalFilename(originalFilename)
                .fileSizeBytes(file.getSize())
                .mimeType(mimeType)
                .storagePath(storagePath)
                .build();

        FileMetadata saved = fileMetadataRepository.save(metadata);
        log.info("File uploaded: originalName={}, stored={}, user={}, size={}B",
                originalFilename, storedFilename, user.getEmail(), file.getSize());

        return FileUploadResponse.builder()
                .id(saved.getId())
                .filename(saved.getFilename())
                .originalFilename(saved.getOriginalFilename())
                .fileSizeBytes(saved.getFileSizeBytes())
                .mimeType(saved.getMimeType())
                .uploadedAt(saved.getCreatedAt())
                .build();
    }

    /**
     * Loads a file for download.
     * Validates ownership — a user cannot download another user's file.
     *
     * @param storedFilename the UUID-based stored filename
     * @param user           the authenticated user
     * @return a Spring Resource ready to be streamed to the HTTP response
     */
    @Transactional(readOnly = true)
    public Resource downloadFile(String storedFilename, User user) {
        // Look up by filename + user — enforces ownership
        FileMetadata metadata = fileMetadataRepository
                .findByFilenameAndUser(storedFilename, user)
                .orElseThrow(() -> new FileNotFoundException(storedFilename));

        // Verify physical file still exists on disk
        if (!fileStorageService.exists(user.getApiKey(), storedFilename)) {
            log.error("DB record exists but file missing on disk: {}", metadata.getStoragePath());
            throw new FileNotFoundException(storedFilename);
        }

        log.info("File downloaded: {}, user={}", storedFilename, user.getEmail());
        return fileStorageService.load(user.getApiKey(), storedFilename);
    }

    /**
     * Retrieves the MIME type of the uploaded file.
     * Falls back to "application/octet-stream" if the client doesn't provide one.
     */
    private String resolveMimeType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        // Strip any charset or boundary parameters (e.g. "text/plain; charset=utf-8" → "text/plain")
        return contentType.split(";")[0].trim().toLowerCase();
    }

    /**
     * Sanitizes a client-provided filename by stripping path components.
     * "../../etc/passwd" → "passwd"
     */
    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "upload";
        // Strip any directory separators (path traversal prevention)
        return filename.replaceAll(".*[/\\\\]", "").trim();
    }

    /**
     * Extracts the file extension from a sanitized filename.
     * "document.pdf" → "pdf", "archive.tar.gz" → "gz", "README" → ""
     */
    private String extractExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) return "";
        String ext = filename.substring(dotIndex + 1).toLowerCase();
        // Allow only simple alphanumeric extensions (max 10 chars)
        return ext.matches("[a-z0-9]{1,10}") ? ext : "";
    }
}
