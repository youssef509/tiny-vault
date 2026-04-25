package com.youssef.storageservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body returned after a successful file upload.
 * Only exposes safe, non-sensitive file metadata.
 * The storagePath is intentionally excluded.
 */
@Getter
@Builder
public class FileUploadResponse {
    private UUID id;
    private String filename;           // stored UUID-based filename
    private String originalFilename;   // what the user called it
    private Long fileSizeBytes;
    private String mimeType;
    private LocalDateTime uploadedAt;
}
