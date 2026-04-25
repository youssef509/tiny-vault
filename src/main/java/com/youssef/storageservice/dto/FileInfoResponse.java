package com.youssef.storageservice.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO representing a single file in the listing response.
 * Used by GET /api/v1/files.
 */
@Getter
@Builder
public class FileInfoResponse {
    private UUID id;
    private String filename;
    private String originalFilename;
    private Long fileSizeBytes;
    private String mimeType;
    private LocalDateTime uploadedAt;
}
