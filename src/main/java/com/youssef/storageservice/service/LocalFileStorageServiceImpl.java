package com.youssef.storageservice.service;

import com.youssef.storageservice.config.StorageProperties;
import com.youssef.storageservice.exception.FileNotFoundException;
import com.youssef.storageservice.exception.StorageException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;

/**
 * Local filesystem implementation of FileStorageService.
 *
 * Files are stored at: {basePath}/{apiKey}/{storedFilename}
 *
 * Security measures:
 *  - Files are always stored using a UUID-based name (never the original filename)
 *  - Path traversal is prevented by normalizing and validating the resolved path
 *  - The base directory is created on startup if it doesn't exist
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalFileStorageServiceImpl implements FileStorageService {

    private final StorageProperties storageProperties;

    /** Root storage directory (resolved once at startup). */
    private Path rootLocation;

    /**
     * Initializes the root storage directory on startup.
     * Creates it if it doesn't exist.
     */
    @PostConstruct
    public void init() {
        rootLocation = Path.of(storageProperties.getBasePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(rootLocation);
            log.info("Storage root initialized: {}", rootLocation);
        } catch (IOException ex) {
            throw new StorageException(
                "Could not create storage root directory: " + rootLocation, ex);
        }
    }

    @Override
    public String store(MultipartFile file, String apiKey, String storedFilename) throws IOException {
        Path userDir = resolveUserDir(apiKey);
        Files.createDirectories(userDir);

        Path destination = resolveAndValidate(apiKey, storedFilename);

        // Copy file bytes to disk — REPLACE_EXISTING in case of a retry
        Files.copy(file.getInputStream(), destination, StandardCopyOption.REPLACE_EXISTING);

        log.debug("Stored file: {}", destination);
        return destination.toString();
    }

    @Override
    public Resource load(String apiKey, String storedFilename) {
        Path filePath = resolveAndValidate(apiKey, storedFilename);
        try {
            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists() && resource.isReadable()) {
                return resource;
            }
            throw new FileNotFoundException(storedFilename);
        } catch (MalformedURLException ex) {
            throw new StorageException("Could not resolve file path: " + storedFilename, ex);
        }
    }

    @Override
    public void delete(String apiKey, String storedFilename) throws IOException {
        Path filePath = resolveAndValidate(apiKey, storedFilename);
        boolean deleted = Files.deleteIfExists(filePath);
        if (deleted) {
            log.debug("Deleted file: {}", filePath);
        } else {
            log.warn("Delete called but file not found on disk: {}", filePath);
        }
    }

    @Override
    public boolean exists(String apiKey, String storedFilename) {
        return Files.exists(resolveAndValidate(apiKey, storedFilename));
    }

    @Override
    public Path resolvePath(String apiKey, String storedFilename) {
        return resolveAndValidate(apiKey, storedFilename);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Path resolveUserDir(String apiKey) {
        // Sanitize apiKey: only allow alphanumeric, hyphen, underscore
        String safeApiKey = apiKey.replaceAll("[^a-zA-Z0-9\\-_]", "_");
        return rootLocation.resolve(safeApiKey).normalize();
    }

    /**
     * Resolves the full path for a file and validates it is within the root.
     * This is the core path traversal prevention — any attempt to use "../"
     * sequences in the filename will be caught here.
     */
    private Path resolveAndValidate(String apiKey, String storedFilename) {
        Path userDir = resolveUserDir(apiKey);
        Path resolved = userDir.resolve(storedFilename).normalize();

        // Security check: ensure the resolved path is still inside the root
        if (!resolved.startsWith(rootLocation)) {
            throw new StorageException(
                "Path traversal attempt detected! Filename: " + storedFilename);
        }
        return resolved;
    }
}
