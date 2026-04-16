package panscience.chatapp.dto;

import java.util.List;

public record TimestampResponse(
        Long assetId,
        String topic,
        List<TimestampItem> timestamps
) {
}

