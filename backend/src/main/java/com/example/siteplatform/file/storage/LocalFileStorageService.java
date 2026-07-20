package com.example.siteplatform.file.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
public class LocalFileStorageService implements FileStorageService {
    private final Path root;

    public LocalFileStorageService(@Value("${file.upload.path:./uploads}") String uploadPath) {
        this.root = Paths.get(uploadPath).toAbsolutePath().normalize();
    }

    @Override
    public String provider() {
        return "local";
    }

    @Override
    public StoredFile store(String storageKey, MultipartFile file) throws Exception {
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        String sha256;
        try (InputStream digestStream = file.getInputStream()) {
            sha256 = StorageSupport.sha256(digestStream);
        }
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredFile(provider(), storageKey, file.getOriginalFilename(), file.getContentType(),
                StorageSupport.extension(file.getOriginalFilename()), file.getSize(), sha256);
    }

    @Override
    public Resource load(String storageKey) {
        return new FileSystemResource(resolve(storageKey));
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) throws Exception {
        Files.deleteIfExists(resolve(storageKey));
    }

    private Path resolve(String storageKey) {
        Path raw = Paths.get(storageKey);
        if (raw.isAbsolute()) return raw.normalize();
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("非法文件存储路径");
        return resolved;
    }
}
