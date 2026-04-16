package panscience.chatapp.service;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import panscience.chatapp.entity.AssetType;

@Component
public class AssetTypeResolver {

    public AssetType resolve(MultipartFile file) {
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();

        if (contentType.startsWith("audio/") || filename.endsWith(".mp3") || filename.endsWith(".wav") || filename.endsWith(".m4a")) {
            return AssetType.AUDIO;
        }
        if (contentType.startsWith("video/") || filename.endsWith(".mp4") || filename.endsWith(".mkv") || filename.endsWith(".mov")) {
            return AssetType.VIDEO;
        }
        if (contentType.startsWith("image/") || filename.endsWith(".jpg") || filename.endsWith(".jpeg") || 
            filename.endsWith(".png") || filename.endsWith(".gif") || filename.endsWith(".webp")) {
            return AssetType.IMAGE;
        }
        return AssetType.DOCUMENT;
    }
}
