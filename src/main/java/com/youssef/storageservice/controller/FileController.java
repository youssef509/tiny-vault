package com.youssef.storageservice.controller;

import com.youssef.storageservice.dto.FileUploadResponse;
import com.youssef.storageservice.model.FileMetadata;
import com.youssef.storageservice.model.User;
import com.youssef.storageservice.repository.FileMetadataRepository;
import com.youssef.storageservice.security.SecurityUtils;
import com.youssef.storageservice.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for file operations.
 *
 * All endpoints require X-API-Key + X-API-Secret headers (enforced by ApiKeyAuthFilter).
 *
 * Endpoints:
 *   POST   /api/v1/upload                → upload a file
 *   GET    /api/v1/download/{filename}   → download a file by stored name
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final FileMetadataRepository fileMetadataRepository;

    // -------------------------------------------------------------------------
    // Task 2.1: File Upload
    // -------------------------------------------------------------------------

    /**
     * Uploads a file to the storage service.
     *
     * Request: POST /api/v1/upload
     *   Content-Type: multipart/form-data
     *   Body param:  file=<binary>
     *   Headers:     X-API-Key, X-API-Secret
     *
     * Response 201 Created:
     *   { "id": "...", "filename": "uuid.ext", "originalFilename": "doc.pdf",
     *     "fileSizeBytes": 12345, "mimeType": "application/pdf", "uploadedAt": "..." }
     *
     * Error responses:
     *   400 — empty file, invalid MIME type, file too large
     *   401 — missing or invalid credentials
     */
    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file) {

        User currentUser = SecurityUtils.getCurrentUser();
        log.info("Upload request: user={}, originalName={}, size={}B",
                currentUser.getEmail(), file.getOriginalFilename(), file.getSize());

        FileUploadResponse response = fileService.uploadFile(file, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // -------------------------------------------------------------------------
    // Task 2.2: File Download
    // -------------------------------------------------------------------------

    /**
     * Downloads a file by its stored (UUID-based) filename.
     *
     * Request: GET /api/v1/download/{filename}
     *   Headers: X-API-Key, X-API-Secret
     *
     * Response 200 OK:
     *   Body: raw file bytes
     *   Headers: Content-Type, Content-Disposition (attachment)
     *
     * Error responses:
     *   401 — missing or invalid credentials
     *   404 — file not found or belongs to another user
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        User currentUser = SecurityUtils.getCurrentUser();
        log.info("Download request: user={}, filename={}", currentUser.getEmail(), filename);

        Resource resource = fileService.downloadFile(filename, currentUser);

        // Look up the MIME type from the database for accurate Content-Type
        String contentType = fileMetadataRepository
                .findByFilenameAndUser(filename, currentUser)
                .map(FileMetadata::getMimeType)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
