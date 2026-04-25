package com.youssef.storageservice.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Abstraction for file storage operations on disk.
 *
 * Keeping this as an interface means we can swap LocalFileStorageServiceImpl
 * for an S3 or Contabo Object Storage implementation later without touching
 * any business logic or controllers.
 */
public interface FileStorageService {

    /**
     * Saves a file to disk under the user's directory.
     *
     * @param file           the uploaded multipart file
     * @param apiKey         the user's API key (used as the storage directory name)
     * @param storedFilename the UUID-based filename to use on disk (never the original name)
     * @return the absolute path where the file was saved
     * @throws IOException if the write fails
     */
    String store(MultipartFile file, String apiKey, String storedFilename) throws IOException;

    /**
     * Loads a file from disk as a Spring Resource (streamable to the HTTP response).
     *
     * @param apiKey         the owner's API key
     * @param storedFilename the UUID-based stored filename
     * @return a readable Resource
     */
    Resource load(String apiKey, String storedFilename);

    /**
     * Deletes a file from disk.
     *
     * @param apiKey         the owner's API key
     * @param storedFilename the UUID-based stored filename
     * @throws IOException if the delete fails
     */
    void delete(String apiKey, String storedFilename) throws IOException;

    /**
     * Checks whether a file exists on disk.
     */
    boolean exists(String apiKey, String storedFilename);

    /**
     * Resolves the full disk path for a given apiKey + filename.
     * Used internally and for logging.
     */
    Path resolvePath(String apiKey, String storedFilename);
}
