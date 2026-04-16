package panscience.chatapp.service;

import java.io.InputStream;

public interface CloudStorageService {
    /**
     * Upload file to cloud storage and return the cloud URL.
     */
    String upload(String filename, InputStream inputStream, String contentType);

    /**
     * Delete file from cloud storage.
     */
    void delete(String cloudUrl);

    /**
     * Get a direct URL to the file (may require authorization headers).
     */
    String getUrl(String cloudUrl);
}

