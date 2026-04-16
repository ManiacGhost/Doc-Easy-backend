package panscience.chatapp.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.InputStream;
import java.util.UUID;

@Service
public class CloudinaryStorageService implements CloudStorageService {
    private static final Logger logger = LoggerFactory.getLogger(CloudinaryStorageService.class);

    private final String cloudName;
    private final String apiKey;
    private final String apiSecret;
    private final String uploadPreset;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CloudinaryStorageService(
            @Value("${app.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.cloudinary.api-key:}") String apiKey,
            @Value("${app.cloudinary.api-secret:}") String apiSecret,
            @Value("${app.cloudinary.upload-preset:}") String uploadPreset,
            ObjectMapper objectMapper
    ) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.uploadPreset = uploadPreset;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.cloudinary.com/v1_1/" + cloudName)
                .build();
        logger.info("Cloudinary initialized with cloud-name: {}, upload-preset: {}", cloudName, uploadPreset);
    }

    @Override
    public String upload(String filename, InputStream inputStream, String contentType) {
        try {
            byte[] fileBytes = inputStream.readAllBytes();
            String publicId = "uploads/" + UUID.randomUUID() + "_" + sanitizeFilename(filename);

            logger.info("Uploading to Cloudinary: {} (size: {} bytes)", filename, fileBytes.length);
            logger.info("Cloud name: {}, Upload preset: {}, API Key present: {}", cloudName, uploadPreset, apiKey != null && !apiKey.isEmpty());

            String boundary = "----WebKitFormBoundary7MA4YWxkTrZu0gW";
            byte[] body = createMultipartBody(fileBytes, publicId, boundary);
            logger.info("Multipart body size: {} bytes", body.length);

            // Upload to Cloudinary using multipart form data
            String response = restClient.post()
                    .uri("/upload")
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            logger.info("Cloudinary response: {}", response);
            JsonNode responseJson = objectMapper.readTree(response);

            // Check for error in response
            if (responseJson.has("error")) {
                String errorMsg = responseJson.path("error").path("message").asText();
                logger.error("Cloudinary API error: {}", errorMsg);
                throw new IllegalStateException("Cloudinary error: " + errorMsg);
            }

            String secureUrl = responseJson.path("secure_url").asText();
            
            if (secureUrl == null || secureUrl.isEmpty()) {
                logger.error("No secure_url in Cloudinary response: {}", response);
                throw new IllegalStateException("No secure_url in Cloudinary response");
            }
            logger.info("Successfully uploaded to Cloudinary: {}", secureUrl);
            return secureUrl;
        } catch (Exception ex) {
            logger.error("Failed to upload to Cloudinary: {} | Exception: {}", ex.getMessage(), ex.getClass().getName(), ex);
            throw new IllegalStateException("Failed to upload to Cloudinary: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void delete(String cloudUrl) {
        // Cloudinary deletion can be implemented if needed
    }

    @Override
    public String getUrl(String cloudUrl) {
        return cloudUrl;
    }

    private String sanitizeFilename(String filename) {
        return filename == null ? "file" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private byte[] createMultipartBody(byte[] file, String publicId, String boundary) {
        String header = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"file\"; filename=\"upload\"\r\n" +
                "Content-Type: application/octet-stream\r\n\r\n";

        String after_file = "\r\n--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"public_id\"\r\n\r\n" +
                publicId + "\r\n";
        
        String after_public_id = "--" + boundary + "\r\n" +
                "Content-Disposition: form-data; name=\"upload_preset\"\r\n\r\n" +
                uploadPreset + "\r\n";

        String footer = "--" + boundary + "--\r\n";

        byte[] headerBytes = header.getBytes();
        byte[] after_fileBytes = after_file.getBytes();
        byte[] after_public_idBytes = after_public_id.getBytes();
        byte[] footerBytes = footer.getBytes();
        
        int totalLength = headerBytes.length + file.length + after_fileBytes.length + 
                          after_public_idBytes.length + footerBytes.length;
        byte[] result = new byte[totalLength];
        
        int offset = 0;
        System.arraycopy(headerBytes, 0, result, offset, headerBytes.length);
        offset += headerBytes.length;
        
        System.arraycopy(file, 0, result, offset, file.length);
        offset += file.length;
        
        System.arraycopy(after_fileBytes, 0, result, offset, after_fileBytes.length);
        offset += after_fileBytes.length;
        
        System.arraycopy(after_public_idBytes, 0, result, offset, after_public_idBytes.length);
        offset += after_public_idBytes.length;
        
        System.arraycopy(footerBytes, 0, result, offset, footerBytes.length);
        
        return result;
    }
}

