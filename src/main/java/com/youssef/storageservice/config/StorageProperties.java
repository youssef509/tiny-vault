package com.youssef.storageservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Typed configuration properties for the file storage layer.
 * All values are bound from the app.storage.* prefix in application.yml.
 *
 * Example:
 *   app:
 *     storage:
 *       base-path: /var/storage
 *       max-file-size-bytes: 104857600  # 100MB
 */
@Component
@ConfigurationProperties(prefix = "app.storage")
@Getter
@Setter
public class StorageProperties {

    /**
     * Root directory for all stored files.
     * Files are organized as: {basePath}/{apiKey}/{storedFilename}
     */
    private String basePath = "/var/storage";

    /**
     * Maximum allowed file size in bytes.
     * Default: 100MB. Also enforced at the Spring multipart level.
     */
    private long maxFileSizeBytes = 100L * 1024 * 1024;

    /**
     * Allowed MIME types. Files with any other MIME type are rejected with 400.
     * Phase 4 will add content-based MIME verification (not just header-based).
     */
    private List<String> allowedMimeTypes = List.of(
        // Images
        "image/jpeg", "image/png", "image/gif", "image/webp",
        "image/svg+xml", "image/bmp", "image/tiff",
        // Documents
        "application/pdf",
        // Text
        "text/plain", "text/csv", "text/html", "text/markdown",
        // Archives
        "application/zip", "application/x-zip-compressed",
        "application/x-tar", "application/gzip",
        // Data formats
        "application/json", "application/xml", "text/xml"
    );
}
