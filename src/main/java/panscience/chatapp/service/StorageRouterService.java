package panscience.chatapp.service;

import org.springframework.stereotype.Service;
import panscience.chatapp.entity.AssetType;

@Service
public class StorageRouterService {

    private final AwsS3StorageService awsS3StorageService;
    private final CloudinaryStorageService cloudinaryStorageService;

    public StorageRouterService(
            AwsS3StorageService awsS3StorageService,
            CloudinaryStorageService cloudinaryStorageService
    ) {
        this.awsS3StorageService = awsS3StorageService;
        this.cloudinaryStorageService = cloudinaryStorageService;
    }

    /**
     * Get the appropriate cloud storage service based on asset type.
     */
    public CloudStorageService getStorageService(AssetType assetType) {
        return switch (assetType) {
            case AUDIO, VIDEO, IMAGE -> cloudinaryStorageService;
            case DOCUMENT -> awsS3StorageService;
        };
    }
}

