package panscience.chatapp.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import panscience.chatapp.exception.BadRequestException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class StorageService {

    private final Path storageRoot;

    public StorageService(@Value("${app.storage.path:uploads}") String storagePath) {
        this.storageRoot = Paths.get(storagePath).toAbsolutePath().normalize();
        try {
            Files.createDirectories(storageRoot);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not initialize storage directory", ex);
        }
    }

    public String store(MultipartFile file) {
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = original.replaceAll("[^a-zA-Z0-9._-]", "_");
        String stored = UUID.randomUUID() + "_" + safeName;

        try {
            Files.copy(file.getInputStream(), storageRoot.resolve(stored), StandardCopyOption.REPLACE_EXISTING);
            return stored;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not store file", ex);
        }
    }

    public Resource loadAsResource(String storedFilename) {
        Path path = storageRoot.resolve(storedFilename).normalize();
        if (!path.startsWith(storageRoot)) {
            throw new BadRequestException("Invalid file path");
        }

        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists()) {
                throw new BadRequestException("File not found on storage");
            }
            return resource;
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Could not load file", ex);
        }
    }
}

