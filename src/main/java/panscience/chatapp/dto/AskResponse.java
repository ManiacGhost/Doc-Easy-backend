package panscience.chatapp.dto;

import java.util.List;

public record AskResponse(
        Long assetId,
        String question,
        String answer,
        List<TimestampItem> timestamps
) {
}

