package com.youssef.storageservice.controller;

import com.youssef.storageservice.model.User;
import com.youssef.storageservice.repository.FileMetadataRepository;
import com.youssef.storageservice.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Storage metrics endpoint — returns per-user file statistics.
 *
 * GET /api/v1/storage/stats (requires API key auth)
 *
 * Example response:
 * {
 *   "totalFiles": 12,
 *   "publicFiles": 4,
 *   "privateFiles": 8,
 *   "generatedAt": "2026-04-26T..."
 * }
 */
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
@Tag(name = "Storage Metrics", description = "Endpoints for retrieving storage usage statistics")
public class StorageMetricsController {

    private final FileMetadataRepository fileMetadataRepository;

    @Operation(summary = "Get storage stats", description = "Returns total, public, and private file counts for the authenticated user.")
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        User currentUser = SecurityUtils.getCurrentUser();

        long totalFiles   = fileMetadataRepository.countByUser(currentUser);
        long publicFiles  = fileMetadataRepository.countByUserAndIsPublicTrue(currentUser);
        long privateFiles = totalFiles - publicFiles;

        return ResponseEntity.ok(Map.of(
            "totalFiles",   totalFiles,
            "publicFiles",  publicFiles,
            "privateFiles", privateFiles,
            "generatedAt",  Instant.now().toString()
        ));
    }
}
