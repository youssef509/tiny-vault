package com.youssef.storageservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Content-based MIME type detector using magic bytes (file signatures).
 *
 * Why not trust client Content-Type?
 *   A malicious client can upload a .php script with Content-Type: image/jpeg.
 *   Reading the first bytes of the file reveals the actual format.
 *
 * This implementation checks the most common file signatures without
 * adding heavyweight dependencies (no Apache Tika needed for personal use).
 *
 * Phase 4 Task 4.3 implementation.
 */
@Slf4j
@Component
public class MimeTypeDetector {

    /** Number of bytes to read for signature detection. */
    private static final int HEADER_BYTES = 16;

    /**
     * Detects the true MIME type from file content (magic bytes).
     *
     * Falls back to the client-provided Content-Type only if detection is
     * inconclusive (e.g. text files have no distinct magic bytes).
     *
     * @param file the uploaded multipart file
     * @return detected MIME type string (e.g. "image/jpeg")
     */
    public String detect(MultipartFile file) {
        byte[] header = readHeader(file);
        String detected = detectFromMagicBytes(header);

        if (detected != null) {
            String clientType = normalizeContentType(file.getContentType());
            if (!detected.equals(clientType)) {
                log.warn("MIME mismatch: client claimed '{}' but magic bytes say '{}' for file '{}'",
                        clientType, detected, file.getOriginalFilename());
            }
            return detected;
        }

        // For text files, check if bytes are valid UTF-8/ASCII printable
        if (isProbablyText(header)) {
            return "text/plain";
        }

        // Fall back to client-provided type (stripped of parameters)
        String fallback = normalizeContentType(file.getContentType());
        log.debug("No magic bytes match for '{}', using client Content-Type: {}",
                file.getOriginalFilename(), fallback);
        return fallback;
    }

    // -------------------------------------------------------------------------
    // Magic bytes signatures
    // -------------------------------------------------------------------------

    private String detectFromMagicBytes(byte[] h) {
        if (h.length < 4) return null;

        // ── Images ──────────────────────────────────────────────────────────
        // JPEG: FF D8 FF
        if (h[0] == (byte)0xFF && h[1] == (byte)0xD8 && h[2] == (byte)0xFF)
            return "image/jpeg";

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (h[0] == (byte)0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47)
            return "image/png";

        // GIF: GIF87a or GIF89a
        if (h[0] == 0x47 && h[1] == 0x49 && h[2] == 0x46 && h[3] == 0x38)
            return "image/gif";

        // WebP: RIFF????WEBP
        if (h.length >= 12 &&
                h[0] == 0x52 && h[1] == 0x49 && h[2] == 0x46 && h[3] == 0x46 &&
                h[8] == 0x57 && h[9] == 0x45 && h[10] == 0x42 && h[11] == 0x50)
            return "image/webp";

        // BMP: BM
        if (h[0] == 0x42 && h[1] == 0x4D)
            return "image/bmp";

        // ── Documents ───────────────────────────────────────────────────────
        // PDF: %PDF
        if (h[0] == 0x25 && h[1] == 0x50 && h[2] == 0x44 && h[3] == 0x46)
            return "application/pdf";

        // ── Archives ────────────────────────────────────────────────────────
        // ZIP (and derived formats like .docx, .xlsx, .jar): PK\x03\x04
        if (h[0] == 0x50 && h[1] == 0x4B && h[2] == 0x03 && h[3] == 0x04)
            return "application/zip";

        // GZIP: 1F 8B
        if (h[0] == (byte)0x1F && h[1] == (byte)0x8B)
            return "application/gzip";

        // ── Structured text (starts with recognizable chars) ─────────────
        // JSON: starts with { or [
        if ((h[0] == '{' || h[0] == '[') && isProbablyText(h))
            return "application/json";

        // XML / SVG: starts with <?xml or <svg
        if (h[0] == '<' && isProbablyText(h))
            return "application/xml";

        return null;
    }

    /**
     * Returns true if all sampled bytes are printable ASCII or common UTF-8.
     * Used to detect plain text / CSV / markdown files.
     */
    private boolean isProbablyText(byte[] bytes) {
        for (byte b : bytes) {
            // Allow: printable ASCII (0x20-0x7E), tab, newline, carriage return
            if (b != 0x09 && b != 0x0A && b != 0x0D
                    && (b < 0x20 || b == 0x7F)
                    && (b & 0xFF) < 0xC0) {   // not a UTF-8 multi-byte lead
                return false;
            }
        }
        return true;
    }

    private byte[] readHeader(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            byte[] buf = new byte[HEADER_BYTES];
            int read = in.read(buf);
            if (read <= 0) return new byte[0];
            if (read < HEADER_BYTES) {
                byte[] trimmed = new byte[read];
                System.arraycopy(buf, 0, trimmed, 0, read);
                return trimmed;
            }
            return buf;
        } catch (IOException ex) {
            log.warn("Could not read file header for MIME detection: {}", ex.getMessage());
            return new byte[0];
        }
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) return "application/octet-stream";
        return contentType.split(";")[0].trim().toLowerCase();
    }
}
