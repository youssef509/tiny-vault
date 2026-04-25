package com.youssef.storageservice.exception;

/**
 * Thrown when a disk I/O operation fails (write, read, delete).
 * Maps to HTTP 500 in the global exception handler.
 */
public class StorageException extends RuntimeException {
    public StorageException(String message) {
        super(message);
    }
    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
