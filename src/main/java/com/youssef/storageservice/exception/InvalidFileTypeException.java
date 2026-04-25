package com.youssef.storageservice.exception;

/**
 * Thrown when an uploaded file's MIME type is not on the allowed list.
 * Maps to HTTP 400 in the global exception handler.
 */
public class InvalidFileTypeException extends RuntimeException {
    public InvalidFileTypeException(String mimeType) {
        super("File type not allowed: " + mimeType +
              ". Allowed types: images, PDF, text, ZIP, JSON, XML");
    }
}
