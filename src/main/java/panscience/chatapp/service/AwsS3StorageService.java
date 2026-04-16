package panscience.chatapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.InputStream;
import java.net.URL;
import java.util.UUID;

@Service
public class AwsS3StorageService implements CloudStorageService {

    private final S3Client s3Client;
    private final String bucketName;
    private final String region;

    public AwsS3StorageService(
            @Value("${app.aws.access-key:}") String accessKey,
            @Value("${app.aws.secret-key:}") String secretKey,
            @Value("${app.aws.region:us-east-1}") String region,
            @Value("${app.aws.bucket-name:}") String bucketName
    ) {
        this.bucketName = bucketName;
        this.region = region;
        try {
            if (accessKey == null || accessKey.isBlank()) {
                throw new IllegalStateException("AWS credentials not configured. Set app.aws.access-key in application.properties");
            }
            AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
            this.s3Client = S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .build();
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to initialize AWS S3 client: " + ex.getMessage(), ex);
        }
    }

    @Override
    public String upload(String filename, InputStream inputStream, String contentType) {
        try {
            String key = "uploads/" + UUID.randomUUID() + "_" + sanitizeFilename(filename);

            byte[] bytes = inputStream.readAllBytes();
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(bytes)
            );

            return "s3://" + bucketName + "/" + key;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to upload to AWS S3: " + ex.getMessage(), ex);
        }
    }

    @Override
    public void delete(String cloudUrl) {
        try {
            if (!cloudUrl.startsWith("s3://")) {
                return;
            }
            String key = cloudUrl.replace("s3://" + bucketName + "/", "");
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucketName)
                            .key(key)
                            .build()
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to delete from AWS S3", ex);
        }
    }

    @Override
    public String getUrl(String cloudUrl) {
        if (cloudUrl.startsWith("s3://")) {
            // Generate presigned URL valid for 1 hour
            try {
                String key = cloudUrl.replace("s3://" + bucketName + "/", "");

                S3Utilities s3Utilities = S3Utilities.builder()
                        .region(Region.of(region))
                        .build();

                URL presignedUrl = s3Utilities.getUrl(builder ->
                    builder.bucket(bucketName)
                           .key(key)
                );

                return presignedUrl.toString();
            } catch (Exception ex) {
                // Fallback to regular HTTPS URL if presigned URL generation fails
                String key = cloudUrl.replace("s3://" + bucketName + "/", "");
                return "https://" + bucketName + ".s3." + region + ".amazonaws.com/" + key;
            }
        }
        return cloudUrl;
    }

    private String sanitizeFilename(String filename) {
        return filename == null ? "file" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
