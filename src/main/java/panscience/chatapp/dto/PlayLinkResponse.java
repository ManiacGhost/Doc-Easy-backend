package panscience.chatapp.dto;

public record PlayLinkResponse(
        Long assetId,
        int startSeconds,
        String playableUrl
) {
}

