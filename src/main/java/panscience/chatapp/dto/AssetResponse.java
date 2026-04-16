package panscience.chatapp.dto;

import panscience.chatapp.entity.AssetType;

import java.time.OffsetDateTime;

public record AssetResponse(
        Long id,
        String originalFilename,
        String contentType,
        AssetType assetType,
        Long sizeBytes,
        OffsetDateTime uploadedAt
) {
}

