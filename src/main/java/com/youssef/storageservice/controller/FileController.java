package com.youssef.storageservice.controller;

import com.youssef.storageservice.dto.FileInfoResponse;
import com.youssef.storageservice.dto.FileUploadResponse;
import com.youssef.storageservice.dto.PagedResponse;
import com.youssef.storageservice.model.FileMetadata;
import com.youssef.storageservice.model.User;
import com.youssef.storageservice.security.SecurityUtils;
import com.youssef.storageservice.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST controller for all file operations.
 *
 * AUTHENTICATED endpoints (require X-API-Key + X-API-Secret):
 *   POST   /api/v1/upload                          → upload a file
 *   GET    /api/v1/download/{filename}             → download a file
 *   GET    /api/v1/files                           → list files (paginated)
 *   DELETE /api/v1/files/{filename}                → delete a file
 *   PATCH  /api/v1/files/{filename}/visibility     → toggle public/private
 *
 * PUBLIC endpoint (no auth — for frontend embedding):
 *   GET    /api/v1/public/{filename}               → serve a public file
 *
 * HOW TO USE FROM NEXT.JS / LARAVEL:
 *   1. From your backend (API route / controller), call POST /api/v1/upload?public=true
 *      with your X-API-Key + X-API-Secret headers.
 *   2. Save the returned `publicUrl` in your database.
 *   3. In your frontend: <img src={file.publicUrl} /> — no auth needed.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    // -------------------------------------------------------------------------
    // Upload — POST /api/v1/upload
    // -------------------------------------------------------------------------

    /**
     * Uploads a file.
     *
     * Query params:
     *   public=false (default) → private file, download requires API key
     *   public=true            → public file, returns a publicUrl for frontend embedding
     *
     * Example response (public=true):
     * {
     *   "filename": "uuid.jpg",
     *   "publicUrl": "https://storage.example.com/api/v1/public/uuid.jpg",
     *   "isPublic": true,
     *   ...
     * }
     *
     * In Next.js: store publicUrl in DB, then <Image src={file.publicUrl} />
     * In Laravel:  store publicUrl, then <img src="{{ $file->public_url }}" />
     */
    @PostMapping("/upload")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "public", defaultValue = "false") boolean isPublic) {

        User currentUser = SecurityUtils.getCurrentUser();
        log.info("Upload: user={}, file={}, size={}B, public={}",
                currentUser.getEmail(), file.getOriginalFilename(), file.getSize(), isPublic);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(fileService.uploadFile(file, currentUser, isPublic));
    }

    // -------------------------------------------------------------------------
    // Authenticated download — GET /api/v1/download/{filename}
    // -------------------------------------------------------------------------

    /**
     * Downloads a private file (requires API key).
     * For public files, prefer using the publicUrl directly.
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        User currentUser = SecurityUtils.getCurrentUser();
        log.info("Download (auth): user={}, file={}", currentUser.getEmail(), filename);

        Resource resource = fileService.downloadFile(filename, currentUser);

        // Re-fetch mime type for the Content-Type header
        String contentType = fileService.listFiles(currentUser,
                        PageRequest.of(0, 1, Sort.by("createdAt")))
                .stream()
                .filter(f -> f.getFilename().equals(filename))
                .map(FileInfoResponse::getMimeType)
                .findFirst()
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    // -------------------------------------------------------------------------
    // PUBLIC file serving — GET /api/v1/public/{filename}  (NO AUTH REQUIRED)
    // -------------------------------------------------------------------------

    /**
     * Serves a public file — no authentication needed.
     *
     * This is what your Next.js / Laravel frontend hits directly:
     *   <img src="https://your-server/api/v1/public/uuid.jpg" />
     *
     * Returns 404 if the file doesn't exist or is private (isPublic=false).
     * This means setting isPublic=false effectively "unpublishes" the file
     * from the public endpoint immediately.
     */
    @GetMapping("/public/{filename}")
    public ResponseEntity<Resource> servePublicFile(@PathVariable String filename) {
        log.debug("Public file request: {}", filename);

        FileMetadata metadata = fileService.loadPublicFile(filename);
        Resource resource = fileService.loadPublicFileResource(metadata);

        String contentType = metadata.getMimeType() != null
                ? metadata.getMimeType()
                : MediaType.APPLICATION_OCTET_STREAM_VALUE;

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                // inline (not attachment) — browser renders images/PDFs in-page
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + metadata.getOriginalFilename() + "\"")
                // Allow browsers and CDNs to cache public files
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .body(resource);
    }

    // -------------------------------------------------------------------------
    // List — GET /api/v1/files
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of all files for the authenticated user.
     *
     * Query params (all optional):
     *   page=0, size=20, sortBy=createdAt, direction=desc
     */
    @GetMapping("/files")
    public ResponseEntity<PagedResponse<FileInfoResponse>> listFiles(
            @RequestParam(defaultValue = "0")        int page,
            @RequestParam(defaultValue = "20")       int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc")     String direction) {

        User currentUser = SecurityUtils.getCurrentUser();
        int cappedSize = Math.min(size, 100);

        String sortField = switch (sortBy) {
            case "originalFilename", "fileSizeBytes", "mimeType" -> sortBy;
            default -> "createdAt";
        };

        Sort sort = "asc".equalsIgnoreCase(direction)
                ? Sort.by(sortField).ascending()
                : Sort.by(sortField).descending();

        Pageable pageable = PageRequest.of(page, cappedSize, sort);
        Page<FileInfoResponse> filePage = fileService.listFiles(currentUser, pageable);

        return ResponseEntity.ok(PagedResponse.of(filePage));
    }

    // -------------------------------------------------------------------------
    // Delete — DELETE /api/v1/files/{filename}
    // -------------------------------------------------------------------------

    @DeleteMapping("/files/{filename}")
    public ResponseEntity<Void> deleteFile(@PathVariable String filename) {
        User currentUser = SecurityUtils.getCurrentUser();
        log.info("Delete: user={}, file={}", currentUser.getEmail(), filename);
        fileService.deleteFile(filename, currentUser);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Visibility toggle — PATCH /api/v1/files/{filename}/visibility
    // -------------------------------------------------------------------------

    /**
     * Makes a file public or private.
     *
     * PATCH /api/v1/files/{filename}/visibility?public=true
     *   → isPublic becomes true, publicUrl is returned
     *
     * PATCH /api/v1/files/{filename}/visibility?public=false
     *   → isPublic becomes false, file is no longer accessible at /public/{filename}
     *
     * Use case: upload privately first (safe), then make public once validated.
     */
    @PatchMapping("/files/{filename}/visibility")
    public ResponseEntity<FileInfoResponse> setVisibility(
            @PathVariable String filename,
            @RequestParam("public") boolean makePublic) {

        User currentUser = SecurityUtils.getCurrentUser();
        log.info("Visibility change: user={}, file={}, public={}",
                currentUser.getEmail(), filename, makePublic);

        FileInfoResponse updated = fileService.setVisibility(filename, currentUser, makePublic);
        return ResponseEntity.ok(updated);
    }
}
