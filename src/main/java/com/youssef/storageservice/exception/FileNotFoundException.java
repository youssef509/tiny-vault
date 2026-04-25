package com.youssef.storageservice.exception;

/**
 * Thrown when a requested file is not found in the database
 * OR is not found on disk.
 * Maps to HTTP 404 in the global exception handler.
 */
public class FileNotFoundException extends RuntimeException {
    public FileNotFoundException(String filename) {
        super("File not found: " + filename);
    }
}
