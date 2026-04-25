package com.youssef.storageservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body returned after a successful file upload.
 *
 * publicUrl:
 *   - Non-null when the file was uploaded with ?public=true
 *   - Format: https://your-server.com/api/v1/public/{filename}
 *   - This URL can be embedded directly in <img>, Next.js Image, or <a> tags
 *     without any authentication headers.
 *   - Null for private files (requires API key to download).
 */
@Getter
@Builder
public class FileUploadResponse {
    private UUID id;
    private String filename;           // stored UUID-based filename (use this for delete/download by key)
    private String originalFilename;   // what the user called it
    private Long fileSizeBytes;
    private String mimeType;
    private Boolean isPublic;
    private String publicUrl;          // non-null if public=true was set on upload
    private LocalDateTime uploadedAt;
}
